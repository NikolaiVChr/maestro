package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.digero.common.midi.ExtensionMidiInstrument;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiInstrument;
import com.digero.common.midi.MidiStandard;
import com.digero.common.midi.MidiUtils;
import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.midi.TimeSignature;
import com.digero.common.util.Util;
import com.digero.maestro.view.MiscSettings;

/**
 * Create NoteEvents from MIDI note ON/OFF messages
 */
public class TrackInfo implements MidiConstants {
	private static final Logger log = Logger.getLogger("import.midi.track");
	
	private SequenceInfo sequenceInfo;

	private int trackNumber;
	private String name;
	private TimeSignature timeSignature = null;// The first one in this track
	private KeySignature keySignature = null;
	private Set<Integer> instruments;
	private Set<String> instrumentExtensions;
	private List<MidiNoteEvent> noteEvents;
	private SortedSet<Integer> notesInUse;// Used for knowing which drum sounds to display in DrumPanel
	private boolean isDrumTrack;
	private final int minVelocity;
	private final int maxVelocity;

	@SuppressWarnings("unchecked") //
	TrackInfo(SequenceInfo parent, Track track, int trackNumber, SequenceDataCache sequenceCache, boolean isXGDrumTrack,
			boolean isGSDrumTrack, boolean wasType0, boolean isDrumsTrack, boolean isGM2DrumTrack,
			TreeMap<Integer, Integer> portMap, MiscSettings miscSettings, boolean oldVelocities, boolean ignoreMidiText)
			throws InvalidMidiDataException {
		this.sequenceInfo = parent;
		// TempoCache tempoCache = new TempoCache(parent.getSequence());
		this.trackNumber = trackNumber;


		if (isXGDrumTrack || isGSDrumTrack || isDrumsTrack || isGM2DrumTrack) {
			isDrumTrack = true;

			// No need? Separated drum tracks already have their name. Type 0 channel tracks can keep their 'Track x',
			// or? Keeping this for backward compat, since track names are stored in MSX projects.
			if (wasType0) {
				if (isXGDrumTrack) {
					name = ExtensionMidiInstrument.TRACK_NAME_DRUM_XG;
				} else if (isGSDrumTrack) {
					name = ExtensionMidiInstrument.TRACK_NAME_DRUM_GS;
				} else if (isGM2DrumTrack) {
					name = ExtensionMidiInstrument.TRACK_NAME_DRUM_GM2;
				} else {
					name = ExtensionMidiInstrument.TRACK_NAME_DRUM_GM;
				}
			}
		}

		instruments = new HashSet<>();
		instrumentExtensions = new HashSet<>();
		noteEvents = new ArrayList<>();
		notesInUse = new TreeSet<>();
        MidiNoteEvent[][] activeNotes = new MidiNoteEvent[CHANNEL_COUNT_ABC][128];
		int zeroNotesRemoved = 0;

		int minVelocity = Integer.MAX_VALUE;
		int maxVelocity = Integer.MIN_VALUE;

		int[] pitchBend = new int[CHANNEL_COUNT_ABC];

		List<BentMidiNoteEvent> allBentNotes = new ArrayList<>();
		
		List<MidiEvent> danglingNoteOffs = new ArrayList<>();
		long EOT = Long.MAX_VALUE;
		MidiEvent EOTevt = null;
		long lastValidEvent = 0L;

		long tick = -10000000;
		for (int j = 0, sz = track.size(); j < sz; j++) {
			MidiEvent evt = track.get(j);
			MidiMessage msg = evt.getMessage();
			
			if (evt.getTick() < 0) {
				log.warning("Negative tick: "+evt.getTick());
				continue;
			}

			if (evt.getTick() != tick && !isDrumTrack) {
				// Moving to new tick, lets process bends since the last tick
				for (int ch = 0; ch < CHANNEL_COUNT_ABC; ch++) {
					// Lets get all bends that happened since last tick, excluding the current tick
					Set<Entry<Long, Integer>> entries = sequenceCache.getBendMap().getEntries(ch, tick, evt.getTick());
					for (Entry<Long, Integer> entry : entries) {
						int bend = entry.getValue();
						long bendTick = entry.getKey();
						if (bend != pitchBend[ch]) {
                            for (MidiNoteEvent ne : activeNotes[ch]) {
                                if (ne != null) {
                                    if (!(ne instanceof BentMidiNoteEvent) && bend != 0) {
                                        // This note is playing while this bend happens
                                        // Lets convert it to a BentNoteEvent
                                        BentMidiNoteEvent be = new BentMidiNoteEvent(ne.note, ne.velocity, ne.getStartTick(),
                                                ne.getEndTick(), ne.getTempoCache(), ne.midiPan);
                                        allBentNotes.add(be);
                                        be.addBend(ne.getStartTick(), 0);// we need this initial bend in NoteGraph class
                                        noteEvents.remove(ne);
                                        noteEvents.add(be);
                                        activeNotes[ch][ne.note.id] = be;
                                        ne = be;
                                    }
                                    if (ne instanceof BentMidiNoteEvent be && be.getBend(bendTick) != bend) {
                                        // The if statement prevents double bend commands,
                                        // which will make an extra split.
                                        be.addBend(bendTick, bend);
                                    }
                                }
                            }
							pitchBend[ch] = bend;
						}
					}
				}
			}
			tick = evt.getTick();
			
			if (msg instanceof ShortMessage m) {
				if (tick > EOT) {
					danglingNoteOffs.add(evt);
					continue;
				}
                int cmd = m.getCommand();
				int ch = m.getChannel();

				/*
				 * if (isXGDrumTrack || isGSDrumTrack) { // } else if (noteEvents.isEmpty() && cmd ==
				 * ShortMessage.NOTE_ON) isDrumTrack = (c == DRUM_CHANNEL); else if (isDrumTrack != (c == DRUM_CHANNEL)
				 * && cmd == ShortMessage.NOTE_ON)
				 * System.err.println("Track "+trackNumber+" contains both notes and drums.."+(name!=null?name:""));
				 */

				if (cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF) {
					int noteId = m.getData1();
					int velocity = m.getData2();
					if (oldVelocities) {
						// The order of math expression here is important, so I added some parentheses:
						velocity = (velocity * sequenceCache.getChannelVolume(ch, tick)) / DEFAULT_CHANNEL_VOLUME;
						if (velocity > 127)
							velocity = 127;
					} else {
						int ch_vol = sequenceCache.getChannelVolume(ch, tick);
						int expr = sequenceCache.getExpression(ch, tick);
						double volume_modifier = (ch_vol / (double) MAX_VOLUME) * (expr / (double) MAX_EXPRESSION);
						velocity = (int) Math.clamp(volume_modifier * velocity, 0.0d, 127.0d);
						if (velocity == 0 && m.getData2() > 0 && ch_vol > 0 && expr > 0) {
							// Do not allow very low expression and channel volume to reduce velocity to zero.
							velocity = 1;
						}
					}

					/*
					 * long time = MidiUtils.tick2microsecond(parent.getSequence(), tick, tempoCache); if (trackNumber
					 * == 2 && time > 360000000L && velocity == 0) { System.err.println();
					 * System.err.println("Tick: "+evt.getTick());
					 * System.err.println(cmd==ShortMessage.NOTE_ON?"NOTE ON":(cmd==ShortMessage.NOTE_OFF?"NOTE OFF":cmd
					 * )); System.err.println("Channel: "+c); System.err.println("Velocity: "+m.getData2());
					 * System.err.println("CH Volume: "+sequenceCache.getVolume(c, tick));
					 * System.err.println("Pitch: "+noteId); System.err.println("Bytes: "+m.getLength());
					 * System.err.println("Time: "+Util.formatDuration(time)); }
					 */

					// If this is a Note ON and was preceded by a similar Note ON without a Note OFF, lets turn the preceding note off
					// If this is a Note OFF, let's do same, but also delete the preceding note if it has zero duration.
                    MidiNoteEvent active = activeNotes[ch][noteId];
                    if (active != null) {
                        active.setEndTick(tick);
                        activeNotes[ch][noteId] = null;
                        if (tick == active.getStartTick() && (cmd == ShortMessage.NOTE_ON && velocity > 0)) {
                            // Illegal zero duration note terminated, so Maestro don't have to process it and discard it in the abc export anyway.
                            //
                            // If the current message is a note ON with velocity (which is what I observe most), then
                            // it would not be possible to keep it anyway, as the next note will start with this pitch immediately.
                            // If the current message is note OFF, we will keep it though, and give it a small duration in AbcExporter.
                            //   (even though that's against MIDI standard, but some MIDI files are meant for them to be played)

                            log.fine(name+" Removing zero note (OFF), tick:"+tick+" file:"+sequenceInfo.getFileName()+" track:"+trackNumber+" time:"+Util.formatDurationM(sequenceCache.tickToMicros(tick)));
                            noteEvents.remove(active);
                            zeroNotesRemoved++;
                        }
                    }

					if ((cmd == ShortMessage.NOTE_OFF || (cmd == ShortMessage.NOTE_ON && velocity == 0)) && active == null) {
						// note OFF event, but no notes to turn off.
						// events like this can make a midi appear longer than they are,
						// so we remove the event.
						danglingNoteOffs.add(evt);
					} else {
						lastValidEvent = tick;
					}
					
					if (cmd == ShortMessage.NOTE_ON && velocity > 0) {
						Note note = Note.fromId(noteId);
						if (note == null) {
							continue; // Note was probably bent out of range. Not great, but not a reason to fail.
						}

						MidiNoteEvent ne = new MidiNoteEvent(note, velocity, tick, tick, sequenceCache, sequenceCache.getPanMap().get(ch, tick));
						if (!isDrumTrack && sequenceCache.getBendMap().get(ch, tick) != 0) {
							// pitch bend active in channel already when note starts
							BentMidiNoteEvent be = new BentMidiNoteEvent(note, velocity, tick, tick, sequenceCache, sequenceCache.getPanMap().get(ch, tick));
							allBentNotes.add(be);
							be.addBend(tick, sequenceCache.getBendMap().get(ch, tick));
							ne = be;
						}
						


						if (velocity > maxVelocity)
							maxVelocity = velocity;
						if (velocity < minVelocity)
							minVelocity = velocity;

						instrumentExtensions.add(sequenceCache.getInstrumentExt(ch, tick, isDrumTrack));
						if (!isDrumTrack) {
							instruments.add(sequenceCache.getInstrument(portMap.get(trackNumber), ch, tick));
							log.finest("Track 0 uses instrument "+MidiInstrument.fromId(sequenceCache.getInstrument(portMap.get(trackNumber), ch, tick))+", channel "+ch);
						}
						noteEvents.add(ne);
						//notesInUse.add(ne.note.id);
                        activeNotes[ch][noteId] = ne;
					}
				}
			} else if (msg instanceof MetaMessage m) {
                int type = m.getType();

				if (type == META_TRACK_NAME && name == null && m.getData() != null) {
					byte[] data = m.getData();
					//System.out.println("Track "+trackNumber+":\n "+MidiUtils.formatBytes(data));				
					String tmp = "";
					if (!ignoreMidiText) tmp = MidiUtils.decodeMidiText(data).trim();
					
					if (!tmp.isEmpty() && !tmp.equalsIgnoreCase("untitled")) {
						name = tmp;
					}
				} else if (type == META_KEY_SIGNATURE && keySignature == null) {
					keySignature = new KeySignature(m);
				} else if (type == META_TIME_SIGNATURE) {
					try {
						if (timeSignature == null) timeSignature = new TimeSignature(m);
					} catch (InvalidMidiDataException e) {
						// Ignore the illegal message
					}
				} else if (type == META_END_OF_TRACK) {
					if (tick < EOT) {
						EOT = tick;
						EOTevt = evt;
						boolean assertEnabled = false;
						assert assertEnabled = true;
						if (assertEnabled) {
							// heavy operation so we skip if assert is disabled
							log.fine(trackNumber+": EOT at "+Util.formatDurationM(MidiUtils.tick2microsecond(sequenceInfo.getSequence(), tick, new SequencerWrapper.TempoCacheSlow(sequenceInfo.getSequence()))));
						}
					}
					// turn off all notes, but keep them instead of discarding them like old days.
					for (int ch = 0; ch < MidiConstants.CHANNEL_COUNT; ch++) {
						for (int pitch = 0 ; pitch < 128 ; pitch++) {
                            MidiNoteEvent ne = activeNotes[ch][pitch];
                            if (ne != null) {
                                activeNotes[ch][pitch] = null;
                                ne.setEndTick(tick);
                                if (tick == ne.getStartTick()) {
                                    // Illegal zero duration note terminated, so Maestro don't have to process it and discard it in the abc export anyway.
                                    //
                                    noteEvents.remove(ne);
                                    zeroNotesRemoved++;

                                    log.fine(name + " Removing zero note (EOT), tick:" + tick + " file:" + sequenceInfo.getFileName() + " track:" + trackNumber + " time:" + Util.formatDurationM(sequenceCache.tickToMicros(tick)));
                                } else {
                                    log.info(sequenceInfo.getFileName() + ": Keeping note ending by EOT instead of Note OFF. Tick " + tick + ", track " + trackNumber);
                                }
                            }
                        }
					}
					// We keep iterating, perhaps there is track-name after EOT
				} else {
					lastValidEvent = tick;
				}
			} else {
				//SysEx
			}
		}
		
		// this compliments SequenceInfo.fixupTrackLength(),
		// here we have better knowledge of note OFFs that
		// does nothing.
		List<MidiEvent> danglingEvents = new ArrayList<>();
		if (EOT > lastValidEvent+1 && EOT != Long.MAX_VALUE) {
			/*
			track.remove(EOTevt);
			EOTevt.setTick((lastValidEvent+1));
			track.add(EOTevt);// this dont work, as java24 auto adjust the tick to be after last event
			log.fine("Moved EOT to "+EOTevt.getTick()+", that should have been "+(lastValidEvent+1));
			*/
			for (int j = track.size()-1; j >= 0; j--) {
				MidiEvent evt = track.get(j);
				if (evt.getTick() > lastValidEvent+1) {
					if (evt.getMessage() instanceof ShortMessage) {
						log.fine(trackNumber+": removing "+MidiUtils.midiEventToShortString(evt));
						danglingNoteOffs.add(evt);
					} else {
						log.fine(trackNumber+": moving "+MidiUtils.midiEventToShortString(evt)+" to "+(lastValidEvent+1));
						danglingEvents.add(evt);
					}
				} else {
					break;
				}
			}
		}
		
		for (MidiEvent off : danglingNoteOffs) {
			track.remove(off);
		}
		
		for (MidiEvent evt : danglingEvents.reversed()) {
			track.remove(evt);
			MidiEvent nw = new MidiEvent(evt.getMessage(), lastValidEvent);
			track.add(nw);
		}
		
		for (BentMidiNoteEvent be : allBentNotes) {
			// All bent notes that span more than an octave (or whatever the option is set to)
            // will already here be split into small pieces.
			if (Math.abs(be.getMaxBend() - be.getMinBend()) > miscSettings.maxRangeForNewBendMethod) {
				List<MidiNoteEvent> prematureSplit = be.split();
				noteEvents.addAll(prematureSplit);
				noteEvents.remove(be);
			} else {
				// System.err.println(trackNumber+": Delay split on "+be.getMinBend()+"<>"+be.getMaxBend()+"
				// ("+Math.abs(be.getMaxBend() -
				// be.getMinBend())+")");
			}
		}
		
		for (MidiNoteEvent ne : noteEvents) {
			// We do it here due to the above split might have removed or added notes
			notesInUse.add(ne.note.id);
		}

		// Turn off notes that are on at the end of the song. This shouldn't happen...
        List<MidiNoteEvent> dangling = new ArrayList<>();
        for (int ch = 0; ch < CHANNEL_COUNT_ABC; ch++) {
            for (int pitch = 0; pitch < 128; pitch++) {
                MidiNoteEvent ne = activeNotes[ch][pitch];
                if (ne != null) {
                    dangling.add(ne);
                }
            }
        }
        if (!dangling.isEmpty()) {
            log.info("Deleting " + dangling.size() + " note(s) not turned off at the end of the track.");
            noteEvents.removeAll(dangling);
        }
		
		if (zeroNotesRemoved > 0) {
			//System.err.println(zeroNotesRemoved + " note(s) removed due to being zero duration in midi file "+sequenceInfo.getFileName()+" track:"+trackNumber);
		}

		if (minVelocity == Integer.MAX_VALUE)
			minVelocity = 0;
		if (maxVelocity == Integer.MIN_VALUE)
			maxVelocity = MidiConstants.MAX_VOLUME;

		this.minVelocity = minVelocity;
		this.maxVelocity = maxVelocity;

		noteEvents = Collections.unmodifiableList(noteEvents);
		notesInUse = Collections.unmodifiableSortedSet(notesInUse);
		instruments = Collections.unmodifiableSet(instruments);
	}

	public SequenceInfo getSequenceInfo() {
		return sequenceInfo;
	}

	public int getTrackNumber() {
		return trackNumber;
	}

	public boolean hasName() {
		return name != null;
	}

	public String getName() {
		if (name == null)
			return "Track " + trackNumber;
		return name;
	}

	public KeySignature getKeySignature() {
		return keySignature;
	}

	public TimeSignature getTimeSignature() {
		return timeSignature;
	}

	@Override
	public String toString() {
		return getName();
	}

	public boolean isDrumTrack() {
		return isDrumTrack;
	}

	/** Gets an unmodifiable list of the note events in this track. */
	public List<MidiNoteEvent> getEvents() {
		return noteEvents;
	}

	public boolean hasEvents() {
		return !noteEvents.isEmpty();
	}

	public SortedSet<Integer> getNotesInUse() {
		return notesInUse;
	}

	public int getEventCount() {
		return noteEvents.size();
	}

	public String getEventCountString() {
		if (getEventCount() == 1) {
			return "1 note";
		}
		return getEventCount() + " notes";
	}

	public String getInstrumentNames() {
		if (isDrumTrack) {

			StringBuilder names = new StringBuilder();
			boolean first = true;

			for (String i : instrumentExtensions) {
				if (i == null)
					break;
				if (!first)
					names.append(", ");
				else
					first = false;

				names.append(i);
			}
			if (names.isEmpty())
				return MidiInstrument.STANDARD_DRUM_KIT;

			return names.toString();
		}

		if (instruments.isEmpty()) {
			if (hasEvents())
				return MidiInstrument.PIANO.name;
			else
				return "<None>";
		}

		StringBuilder names = new StringBuilder();
		boolean first = true;

		if (!isGM()) {// Due to Maestro only supporting port assignments for GM, we make sure to use the GM instr. names
						// for GM.
			for (String i : instrumentExtensions) {
				if (i == null)
					break;
				if (!first)
					names.append(", ");
				else
					first = false;

				names.append(i);
			}
		}
		if (names.isEmpty()) {
			first = true;
			for (int i : instruments) {
				if (!first)
					names.append(", ");
				else
					first = false;

				names.append(MidiInstrument.fromId(i).name);
			}
		}

		return names.toString();
	}

	private boolean isGM() {
		return sequenceInfo.standard == MidiStandard.GM;
	}

	public int getInstrumentCount() {
		return instruments.size();
	}
	
	public int getInstrumentExCount() {
		return instrumentExtensions.size();
	}

	public Set<Integer> getInstruments() {
		return instruments;
	}

	public int getMinVelocity() {
		return minVelocity;
	}

	public int getMaxVelocity() {
		return maxVelocity;
	}
}