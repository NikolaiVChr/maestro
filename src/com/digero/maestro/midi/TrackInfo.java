package com.digero.maestro.midi;

import java.nio.charset.StandardCharsets;
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
import com.digero.common.midi.Note;
import com.digero.common.midi.TimeSignature;
import com.digero.maestro.view.MiscSettings;

/**
 * Create NoteEvents from MIDI note ON/OFF messages
 */
public class TrackInfo implements MidiConstants {
	private SequenceInfo sequenceInfo;

	private int trackNumber;
	private String name;
	private TimeSignature timeSignature = null;// The first one in this track
	private KeySignature keySignature = null;
	private Set<Integer> instruments;
	private Set<String> instrumentExtensions;
	private List<NoteEvent> noteEvents;
	private SortedSet<Integer> notesInUse;// Used for knowing which drum sounds to display in DrumPanel
	private boolean isDrumTrack;
	private final int minVelocity;
	private final int maxVelocity;

	@SuppressWarnings("unchecked") //
	TrackInfo(SequenceInfo parent, Track track, int trackNumber, SequenceDataCache sequenceCache, boolean isXGDrumTrack,
			boolean isGSDrumTrack, boolean wasType0, boolean isDrumsTrack, boolean isGM2DrumTrack,
			TreeMap<Integer, Integer> portMap, MiscSettings miscSettings, boolean oldVelocities)
			throws InvalidMidiDataException {
		this.sequenceInfo = parent;
		// TempoCache tempoCache = new TempoCache(parent.getSequence());
		this.trackNumber = trackNumber;


		if (isXGDrumTrack || isGSDrumTrack || isDrumsTrack || isGM2DrumTrack) {
			isDrumTrack = true;

			// No need? Separated drum tracks already have their name. Type 0 channel tracks can keep their 'Track x',
			// or?
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
		List<NoteEvent>[] notesOn = new List[CHANNEL_COUNT_ABC];
		int zeroNotesRemoved = 0;

		int minVelocity = Integer.MAX_VALUE;
		int maxVelocity = Integer.MIN_VALUE;

		int[] pitchBend = new int[CHANNEL_COUNT_ABC];

		List<BentNoteEvent> allBentNotes = new ArrayList<>();

		long tick = -10000000;
		for (int j = 0, sz = track.size(); j < sz; j++) {
			MidiEvent evt = track.get(j);
			MidiMessage msg = evt.getMessage();

			if (evt.getTick() != tick && !isDrumTrack) {
				// Moving to new tick, lets process bends since the last tick
				for (int ch = 0; ch < CHANNEL_COUNT_ABC; ch++) {
					// Lets get all bends that happened since last tick, excluding the current tick
					Set<Entry<Long, Integer>> entries = sequenceCache.getBendMap().getEntries(ch, tick, evt.getTick());
					for (Entry<Long, Integer> entry : entries) {
						int bend = entry.getValue();
						long bendTick = entry.getKey();
						if (bend != pitchBend[ch]) {
							List<NoteEvent> bentNotes = new ArrayList<>();
							if (notesOn[ch] != null) {
								for (NoteEvent ne : notesOn[ch]) {
									if (!(ne instanceof BentNoteEvent) && bend != 0) {
										// This note is playing while this bend happens
										// Lets convert it to a BentNoteEvent
										BentNoteEvent be = new BentNoteEvent(ne.note, ne.velocity, ne.getStartTick(),
												ne.getEndTick(), ne.getTempoCache());
										allBentNotes.add(be);
										be.setMidiPan(ne.midiPan);
										be.addBend(ne.getStartTick(), 0);// we need this initial bend in NoteGraph class
										noteEvents.remove(ne);
										ne = be;
										noteEvents.add(ne);
									}
									if (ne instanceof BentNoteEvent && ((BentNoteEvent) ne).getBend(bendTick) != bend) {
										// The if statement prevents double bend commands,
										// which will make an extra split.
										((BentNoteEvent) ne).addBend(bendTick, bend);
									}
									bentNotes.add(ne);
								}
								notesOn[ch] = bentNotes;
							}
							pitchBend[ch] = bend;
						}
					}
				}
			}
			tick = evt.getTick();

			if (msg instanceof ShortMessage) {
				ShortMessage m = (ShortMessage) msg;
				int cmd = m.getCommand();
				int c = m.getChannel();

				/*
				 * if (isXGDrumTrack || isGSDrumTrack) { // } else if (noteEvents.isEmpty() && cmd ==
				 * ShortMessage.NOTE_ON) isDrumTrack = (c == DRUM_CHANNEL); else if (isDrumTrack != (c == DRUM_CHANNEL)
				 * && cmd == ShortMessage.NOTE_ON)
				 * System.err.println("Track "+trackNumber+" contains both notes and drums.."+(name!=null?name:""));
				 */
				if (notesOn[c] == null)
					notesOn[c] = new ArrayList<>();

				if (cmd == ShortMessage.NOTE_ON || cmd == ShortMessage.NOTE_OFF) {
					int noteId = m.getData1();
					int velocity = m.getData2();
					if (oldVelocities) {
						// Order of math expression here is important, so I added parenthesizes:
						velocity = (velocity * sequenceCache.getChannelVolume(c, tick)) / DEFAULT_CHANNEL_VOLUME;
						if (velocity > 127)
							velocity = 127;
					} else {
						int ch_vol = sequenceCache.getChannelVolume(c, tick);
						int expr = sequenceCache.getExpression(c, tick);
						velocity *= (ch_vol / (double) MAX_VOLUME) * (expr / (double) MAX_EXPRESSION);
						if (velocity == 0 && m.getData2() > 0 && ch_vol > 0 && expr > 0) {
							// Do not allow very low expression and volume to reduce velocity to zero.
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

					// If this is a Note ON and was preceded by a similar Note ON without a Note OFF, lets turn it the preceding note off
					// If this is a Note OFF lets do same.
					Iterator<NoteEvent> iter = notesOn[c].iterator();
					while (iter.hasNext()) {
						NoteEvent ne = iter.next();
						if (ne.note.id == noteId) {
							iter.remove();
							ne.setEndTick(tick);
							if (tick == ne.getStartTick()) {
								// Illegal zero duration note terminated, so Maestro don't have to process it and discard it in the abc export anyway.
								// If we were wanting to keep it, we would need to give it a duration.
								// If the current message is a note ON with velocity (which is what I observe most) then
								// it would not be possible to keep it anyway, as next note will start with this pitch immediately.
								noteEvents.remove(ne);
								//System.out.println(name+" tick:"+tick+" file:"+sequenceInfo.getFileName()+" track:"+trackNumber+" mins:"+((sequenceCache.tickToMicros(tick)/1000000.0)/60));
								// notesInUse.remove(ne.note.id); might not be the first of this pitch to be used, so we cannot do this call. Not a big deal.
								zeroNotesRemoved++;
							}
							break;
						}
					}

					if (cmd == ShortMessage.NOTE_ON && velocity > 0) {
						Note note = Note.fromId(noteId);
						if (note == null) {
							continue; // Note was probably bent out of range. Not great, but not a reason to fail.
						}

						NoteEvent ne = new NoteEvent(note, velocity, tick, tick, sequenceCache);
						if (!isDrumTrack && sequenceCache.getBendMap().get(c, tick) != 0) {
							// pitch bend active in channel already when note starts
							BentNoteEvent be = new BentNoteEvent(note, velocity, tick, tick, sequenceCache);
							allBentNotes.add(be);
							be.addBend(tick, sequenceCache.getBendMap().get(c, tick));
							ne = be;
						}
						ne.setMidiPan(sequenceCache.getPanMap().get(c, tick));// We don't set this in NoteEvent
																				// constructor as only MIDI notes will
																				// get this set, abc notes not.


						if (velocity > maxVelocity)
							maxVelocity = velocity;
						if (velocity < minVelocity)
							minVelocity = velocity;

						instrumentExtensions.add(sequenceCache.getInstrumentExt(c, tick, isDrumTrack));
						if (!isDrumTrack) {
							instruments.add(sequenceCache.getInstrument(portMap.get(trackNumber), c, tick));
						}
						noteEvents.add(ne);
						notesInUse.add(ne.note.id);
						notesOn[c].add(ne);
					}
				}
			} else if (msg instanceof MetaMessage) {
				MetaMessage m = (MetaMessage) msg;
				int type = m.getType();

				if (type == META_TRACK_NAME && name == null) {
					byte[] data = m.getData();// Text that starts with any of these indicate charset: "@LATIN", "@JP",
												// "@UTF-16LE", or "@UTF-16BE"
					String tmp = new String(data, 0, data.length, StandardCharsets.US_ASCII).trim();// "UTF-8"
					if (tmp.length() > 0 && !tmp.equalsIgnoreCase("untitled")
							&& !tmp.equalsIgnoreCase("WinJammer Demo")) {
						// System.out.println("Starts with @ "+data[0]+" "+(data[0] & 0xFF));

						/*
						 * String pattern = "\u000B";// Vertical tab in unicode Pattern r = Pattern.compile(pattern);
						 * Matcher match = r.matcher(tmp); tmp = match.replaceAll(" ");
						 */

						name = tmp;
					}
				} else if (type == META_KEY_SIGNATURE && keySignature == null) {
					keySignature = new KeySignature(m);
				} else if (type == META_TIME_SIGNATURE && timeSignature == null) {
					try {
						timeSignature = new TimeSignature(m);
					} catch (InvalidMidiDataException e) {
						// Ignore the illegal message
					}
				}
			}
		}

		for (BentNoteEvent be : allBentNotes) {
			// All bent notes that span more than an octave will
			// already here be split into small pieces.
			if (Math.abs(be.getMaxBend() - be.getMinBend()) > miscSettings.maxRangeForNewBendMethod) {
				List<NoteEvent> prematureSplit = be.split();
				noteEvents.addAll(prematureSplit);
				noteEvents.remove(be);
			} else {
				// System.err.println(trackNumber+": Delay split on "+be.getMinBend()+"<>"+be.getMaxBend()+"
				// ("+Math.abs(be.getMaxBend() -
				// be.getMinBend())+")");
			}
		}

		// Turn off notes that are on at the end of the song. This shouldn't happen...
		int ctNotesOn = 0;
		for (List<NoteEvent> notesOnChannel : notesOn) {
			if (notesOnChannel != null)
				ctNotesOn += notesOnChannel.size();
		}
		if (ctNotesOn > 0) {
			System.err.println((ctNotesOn) + " note(s) not turned off at the end of the track.");

			for (List<NoteEvent> notesOnChannel : notesOn) {
				if (notesOnChannel != null)
					noteEvents.removeAll(notesOnChannel);
			}
		}
		
		if (zeroNotesRemoved > 0) {
			// Its mostly drum tracks that have zero duration notes as far as I have observed. ~Aifel
			System.err.println(zeroNotesRemoved + " note(s) removed due to being zero duration in midi file "+sequenceInfo.getFileName()+" track:"+trackNumber);
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
	public List<NoteEvent> getEvents() {
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
			if (names.length() == 0)
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
		if (names.length() == 0) {
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
		return sequenceInfo.getDataCache().isGM();
	}

	public int getInstrumentCount() {
		return instruments.size();
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