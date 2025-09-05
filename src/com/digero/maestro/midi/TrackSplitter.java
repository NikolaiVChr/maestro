package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Logger;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiFactory;
import com.digero.common.midi.MidiInstrument;
import com.digero.common.midi.MidiStandard;
import com.digero.common.midi.MidiUtils;

/**
 * Takes a midi input and expands each instrument to its own track. Works with GM2, XG, GS, GM and GM+
 * 
 */
public class TrackSplitter {
	private static final Logger log = Logger.getLogger("export.midi");
	
	private SequenceDataCache sequenceCache = null;
	private boolean isGM = true;

	public Sequence split(Sequence sequence, SequenceDataCache sequenceCache, MidiStandard standard,
			boolean[] rolandDrumChannels, List<TreeMap<Long, Boolean>> yamahaDrumSwitches, boolean[] yamahaDrumChannels,
			List<TreeMap<Long, Boolean>> mmaDrumSwitches, SortedMap<Integer, Integer> portMap)
			throws InvalidMidiDataException {

		this.sequenceCache = sequenceCache;

		int resolution = sequence.getResolution();
		float divisionType = sequence.getDivisionType();
		Sequence expandedSequence = new Sequence(divisionType, resolution);

		isGM = standard == MidiStandard.GM;
		boolean hasPorts = sequenceCache.hasPorts;

		Track[] oldTracks = sequence.getTracks();
		Track newMetaTrack = expandedSequence.createTrack();
		newMetaTrack.add(MidiFactory.createTrackNameEvent("META"));
		long lastEOTTick = 0L;
		for (int oldTrackNumber = 0; oldTrackNumber < oldTracks.length; oldTrackNumber++) {
			Track oldTrack = oldTracks[oldTrackNumber];

			// Find the old name and end of track for the track we want to expand
			String oldTrackName = "";
			MidiEvent oldEndOfTrack = null;
			for (int i = 0; i < oldTrack.size(); i++) {
				MidiEvent evt = oldTrack.get(i);
				MidiMessage msg = evt.getMessage();
				if (msg instanceof MetaMessage meta) {
                    int type = meta.getType();
					if (type == MidiConstants.META_TRACK_NAME) {
						byte[] data = meta.getData();
						String tmp = MidiUtils.decodeMidiText(data).trim();
						if (!tmp.isEmpty()) {
							oldTrackName = tmp;
							break;
						}
					} else if (type == MidiConstants.META_END_OF_TRACK) {
						oldEndOfTrack = evt;
						if (evt.getTick() > lastEOTTick) {
							lastEOTTick = evt.getTick();
						}
					}
				}
			}
			if (oldTrackName.isEmpty()) {
				oldTrackName = "Track " + oldTrackNumber;
			}

			// This hash map contains a map from instrument name into new track.
			// This instrument name is prepended with the channel if its a GM+ format midi.
			HashMap<String, Track> newTracks = new HashMap<>();

			// Making a list of which notes are playing, the note will map into an
			// instrument, so that the Midi OFF event gets put on same track as its midi ON
			// event.
			List<HashMap<Integer, String>> notesOn = new ArrayList<>();
			for (int i = 0; i < MidiConstants.CHANNEL_COUNT; i++) {
				notesOn.add(new HashMap<>());
			}

			// GM+ stuff
			int port = portMap.get(oldTrackNumber);
			List<MidiEvent> portPrograms = new ArrayList<>();// Program changes within specific port, they will later be
																// put into 'firstTrackUsingPorts'
			Track firstTrackUsingPorts = null;// The first of the new expanded tracks, this will come to contain GM+
												// port program changes.

			// Iterate over all midi events in old track
			int trackCounter = 1;
			evtIter: for (int i = 0; i < oldTrack.size(); i++) {

				String instr = "";
				MidiEvent evt = oldTrack.get(i);
				long tick = evt.getTick();
				MidiMessage msg = evt.getMessage();
				if (msg instanceof ShortMessage shortMsg) {
                    int cmd = shortMsg.getCommand();
					int channel = shortMsg.getChannel();

					if (cmd == ShortMessage.NOTE_OFF || cmd == ShortMessage.NOTE_ON) {
						instr = handleEvent(oldTrackNumber, notesOn, port, tick, channel, cmd, shortMsg);
						// if (instr == null) System.out.println("instr==null "+on);
						// if ("".equals(instr)) System.out.println("instr=='' "+on);
					} else if (hasPorts && cmd == ShortMessage.PROGRAM_CHANGE) {
						portPrograms.add(evt);
						continue evtIter;
					}

					// Lets put the midi event in its new track. If we determined its tied to an
					// instrument,
					// then we place it in one of the new tracks, which each represent an
					// instrument.
					// If not associated with an instrument, then it is put in track 0, where we
					// keep all the meta, sysex, bank changes and normal program changes..
					if (instr != null && !instr.isEmpty()) {
						String trackID = hasPorts ? (channel + instr) : instr;// If its not a Cakewalk midi then we
																				// lumps all of same instr together,
																				// regardless of channel.
						Track newTrack = newTracks.get(trackID);
						if (newTrack == null) {
							newTrack = expandedSequence.createTrack();
							if (firstTrackUsingPorts == null)
								firstTrackUsingPorts = newTrack;
							newTrack.add(MidiFactory.createTrackNameEvent(oldTrackName + " : " + trackCounter));
                            //if (oldEndOfTrack != null) newTrack.add(MidiFactory.createEndOfTrackEvent(oldEndOfTrack.getTick()));
							if (hasPorts) {
								// We put the GM+ port change in every one of the new tracks if the old had it.
								MidiEvent evtPort = MidiFactory.createPortEvent(port);
								if (evtPort != null) {
									newTrack.add(evtPort);
								} else {
									log.severe("Failed to create GM+ port event when expanding midi");
									return null;
								}
							}
							trackCounter += 1;
							newTracks.put(trackID, newTrack);
						}
						newTrack.add(evt);
					} else {
						newMetaTrack.add(evt);
					}
				} else {
                    if (msg instanceof MetaMessage metaMsg) {
                        int type = metaMsg.getType();
                        if (oldTrackNumber > 0 && (type == MidiConstants.META_TEXT || type == MidiConstants.META_LYRIC || type == MidiConstants.META_MARKER || type == MidiConstants.META_CUE_POINT)) {
                            // Its lyrics related
                            String trackID = "Lyrics " + oldTrackNumber;
                            Track newTrack = newTracks.get(trackID);
                            if (newTrack == null) {
                                newTrack = expandedSequence.createTrack();
                                newTrack.add(MidiFactory.createTrackNameEvent(oldTrackName + " : Lyrics"));
                                //if (oldEndOfTrack != null) newTrack.add(MidiFactory.createEndOfTrackEvent(oldEndOfTrack.getTick()));

                                trackCounter += 1;
                                newTracks.put(trackID, newTrack);
                            }
                            newTrack.add(evt);

                        } else if (oldTrackNumber > 0 && type == MidiConstants.META_TEMPO) {
                            // Its tempo but not in first track
                            String trackID = "Tempos " + oldTrackNumber;
                            Track newTrack = newTracks.get(trackID);
                            if (newTrack == null) {
                                newTrack = expandedSequence.createTrack();
                                newTrack.add(MidiFactory.createTrackNameEvent(oldTrackName + " : Tempos"));
                                //if (oldEndOfTrack != null) newTrack.add(MidiFactory.createEndOfTrackEvent(oldEndOfTrack.getTick()));

                                trackCounter += 1;
                                newTracks.put(trackID, newTrack);
                            }
                            newTrack.add(evt);
                        } else {
                            newMetaTrack.add(evt);
                        }
                    } else {
                        newMetaTrack.add(evt);
                    }
				}
			}
			
			addPortChangesToTrack(newMetaTrack, portPrograms, firstTrackUsingPorts);
			
			for (Track track : newTracks.values()) {
				long last = track.get(track.size()-1).getTick();
				track.add(MidiFactory.createEndOfTrackEvent(last+1L));
			}
		}
		
		if (lastEOTTick > 0L) {
			newMetaTrack.add(MidiFactory.createEndOfTrackEvent(lastEOTTick));
		}
		return expandedSequence;
	}

	private String handleEvent(int oldTrackNumber, List<HashMap<Integer, String>> notesOn, int port, long tick, int channel, int cmd,
			ShortMessage shortMsg) {
		boolean on = cmd == ShortMessage.NOTE_ON;
		int note = shortMsg.getData1();
		int velocity = shortMsg.getData2();
		if (on && velocity > 0) {
			return treatAsMidiOn(oldTrackNumber, notesOn, port, tick, channel, note);
		} else if (!on) {
			// This is a genuine midi OFF event
			return notesOn.get(channel).remove(note);
		} else {
			// This is a midi ON event that might act as a midi OFF
			String instr = notesOn.get(channel).remove(note);
			if (instr == null) {
				instr = treatAsMidiOn(oldTrackNumber, notesOn, port, tick, channel, note);
			}
			return instr;
		}
	}

	/**
	 * Its a silent MIDI ON not preceded by a midi ON, so we treat is as a midi ON although, it's silent.
	 * <p>
	 * TODO: Consider to remove it, cause Maestro will assign +pppp+ to it,
     * TODO: and it will become audible which is probably
	 * TODO: not what the midi maker intended.
	 *
     */
	private String treatAsMidiOn(int index, List<HashMap<Integer, String>> notesOn, int port, long tick, int channel,
			int note) {
		String instr;
		instr = fetchInstrName(tick, channel, port, index);
		if (instr != null && !instr.isEmpty()) {
			notesOn.get(channel).put(note, instr);
		}
		return instr;
	}

	/**
	 * We add all program changes for this GM+ port to the first expanded track that uses this port.
	 *
     */
	private void addPortChangesToTrack(Track newMetaTrack, List<MidiEvent> portPrograms, Track firstTrackUsingPorts) {
		if (firstTrackUsingPorts != null) {
			for (MidiEvent event : portPrograms) {
				firstTrackUsingPorts.add(event);
			}
		} else {
			// This should not be needed, something went wrong if this is executed with actual port programs.
			if (!portPrograms.isEmpty()) log.severe("No events added to new tracks. portPrograms="+portPrograms.size());
			for (MidiEvent event : portPrograms) {
				newMetaTrack.add(event);
			}
		}
	}

	private String fetchInstrName(long tick, int channel, int port, int track) {
		if (isGM) {
			if (channel == MidiConstants.DRUM_CHANNEL)
				return MidiInstrument.STANDARD_DRUM_KIT;
			int instrumentNumber = sequenceCache.getInstrument(port, channel, tick);
			return MidiInstrument.fromId(instrumentNumber).toString();
		} else {
            return sequenceCache.getInstrumentExt(channel, tick, isDrumsTrack(track));
		}
	}

	private boolean isDrumsTrack(int track) {
		return sequenceCache.isXGDrumsTrack(track) || sequenceCache.isGSDrumsTrack(track)
				|| sequenceCache.isGM2DrumsTrack(track) || sequenceCache.isDrumsTrack(track);
	}
}