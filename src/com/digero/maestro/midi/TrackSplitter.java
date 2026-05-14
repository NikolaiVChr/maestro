package com.digero.maestro.midi;

import java.util.*;
import java.util.logging.Logger;

import javax.sound.midi.*;

import com.digero.common.midi.*;

/**
 * Takes a midi input and expands each instrument to its own track. Works with GM2, XG, GS, GM
 * 
 */
public class TrackSplitter {
	private static final Logger log = Logger.getLogger("export.midi");
	
	private SequenceDataCache sequenceCache = null;
	private Sequence oldSequence = null;
	private boolean isGM = true;

	public Sequence split(Sequence sequence, SequenceDataCache sequenceCache, MidiStandard standard,
			SortedMap<Integer, Integer> portMap)
			throws InvalidMidiDataException {

		this.sequenceCache = sequenceCache;
		this.oldSequence = sequence;

		int resolution = sequence.getResolution();
		float divisionType = sequence.getDivisionType();
		Sequence expandedSequence = new Sequence(divisionType, resolution);

		isGM = standard == MidiStandard.GM;
		boolean hasPorts = sequenceCache.hasPorts;

		Track[] oldTracks = sequence.getTracks();
		Track newMetaTrack = expandedSequence.createTrack();

		newMetaTrack.add(MidiFactory.createTrackNameEvent("META"));

		Map<Integer, Track> initTracksByPort = new HashMap<>();
		Set<Integer> activePorts = new HashSet<>(portMap.values());
		activePorts.add(0);
		for (int p : activePorts) {
			Track initTrack = expandedSequence.createTrack();
			initTrack.add(MidiFactory.createTrackNameEvent("NON-META " + p));

			// Lock this initialization track to the correct hardware port!
			if (hasPorts) {
				MidiEvent evtPort = MidiFactory.createPortEvent(p);
				if (evtPort != null) {
					initTrack.add(evtPort);
				} else {
					log.severe("Failed to create port event when expanding midi");
					return null;
				}
			}
			initTracksByPort.put(p, initTrack);
		}
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

			int port = portMap.get(oldTrackNumber);

			// Iterate over all midi events in old track
			int trackCounter = 1;
			MidiEvent pendingChannelPrefix = null;
			evtIter: for (int i = 0; i < oldTrack.size(); i++) {

				String instr = "";
				MidiEvent evt = oldTrack.get(i);
				long tick = evt.getTick();
				MidiMessage msg = evt.getMessage();
				if (msg instanceof ShortMessage shortMsg) {
                    int cmd = shortMsg.getCommand();
					int channel = shortMsg.getChannel();

					// If we have prefix pending, it was an orphan.
					if (pendingChannelPrefix != null) {
						log.fine("Discarding orphaned MIDI Channel Prefix at tick " + tick);
						pendingChannelPrefix = null;
					}

					if (cmd == ShortMessage.NOTE_OFF || cmd == ShortMessage.NOTE_ON) {
						instr = handleEvent(oldTrackNumber, notesOn, port, tick, channel, cmd, shortMsg);
						// if (instr == null) System.out.println("instr==null "+on);
						// if ("".equals(instr)) System.out.println("instr=='' "+on);
					} else if (cmd == ShortMessage.PROGRAM_CHANGE ||
							(cmd == ShortMessage.CONTROL_CHANGE && (shortMsg.getData1() == MidiConstants.BANK_SELECT_MSB || shortMsg.getData1() == MidiConstants.BANK_SELECT_LSB))) {
						initTracksByPort.get(port).add(evt);
						continue evtIter;
					} else {
						// Identify instrument for Control Change, Pitch Bend, and standard Program Changes
						// So they go into the Instrument Track instead of the NON-META track.
						instr = fetchInstrName(tick, channel, port, oldTrackNumber);
					}

					// Lets put the midi event in its new track. If we determined its tied to an
					// instrument,
					// then we place it in one of the new tracks, which each represent an
					// instrument.
					// If not associated with an instrument, then it is put in track 0, where we
					// keep all the meta, sysex, bank changes and normal program changes..
					if (instr != null && !instr.isEmpty()) {
						String trackID = port+":"+channel+":"+instr;
						Track newTrack = newTracks.get(trackID);
						if (newTrack == null) {
							newTrack = expandedSequence.createTrack();
							newTrack.add(MidiFactory.createTrackNameEvent(oldTrackName + " : " + trackCounter));
                            //if (oldEndOfTrack != null) newTrack.add(MidiFactory.createEndOfTrackEvent(oldEndOfTrack.getTick()));
							if (hasPorts) {
								// We put the port change in every one of the new tracks if the old had it.
								MidiEvent evtPort = MidiFactory.createPortEvent(port);
								if (evtPort != null) {
									newTrack.add(evtPort);
								} else {
									log.severe("Failed to create port event when expanding midi");
									return null;
								}
							}
							trackCounter += 1;
							newTracks.put(trackID, newTrack);
						}
						newTrack.add(evt);
					} else {
						initTracksByPort.get(port).add(evt);
					}
				} else {
                    if (msg instanceof MetaMessage metaMsg) {
						int type = metaMsg.getType();

						if (type == MidiConstants.META_PORT_CHANGE) {
							// Ignore old port events!
						} else if (type == MidiConstants.META_CHANNEL_PREFIX) {
							// store the prefix
							pendingChannelPrefix = evt;
						} else if (oldTrackNumber > 0 && (type == MidiConstants.META_TEXT || type == MidiConstants.META_LYRIC || type == MidiConstants.META_MARKER || type == MidiConstants.META_CUE_POINT)) {
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
							if (pendingChannelPrefix != null) {
								newTrack.add(pendingChannelPrefix);
								pendingChannelPrefix = null;
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
							if (pendingChannelPrefix != null) {
								pendingChannelPrefix = null;
							}
							newTrack.add(evt);
						} else {
							if (pendingChannelPrefix != null) {
								newMetaTrack.add(pendingChannelPrefix);
								pendingChannelPrefix = null;
							}
							newMetaTrack.add(evt);
						}
					} else if (msg instanceof SysexMessage sysMsg) {
						byte[] message = sysMsg.getMessage();
						boolean isXGPartModeSwitch = false;

						if (message.length == 9 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x43
								&& (message[3] & 0xFF) == 0x4C && (message[4] & 0xFF) == 0x08 && (message[6] & 0xFF) == 0x07
								&& (message[8] & 0xFF) == 0xF7 && (message[5] & 0xFF) < 16) {
							isXGPartModeSwitch = true;
						}

						if (isXGPartModeSwitch) {
							log.fine("Translating XG Part Mode SysEx to standard msb/patch during split.");
							int ch = message[5];
							boolean isDrum = (message[7] > 0);
							int msb = isDrum ? 127 : 0;

							if (pendingChannelPrefix != null) {
								pendingChannelPrefix = null;
							}

							// Inject the MSB Bank Select where the SysEx used to be
							initTracksByPort.get(port).add(MidiFactory.createControllerEvent((byte)MidiConstants.BANK_SELECT_MSB, msb, ch, tick));

							// A real XG synth resets the patch when receiving this SysEx.
							// PC 0 acts as Standard Kit (if MSB 127) or Grand Piano (if MSB 0).
							initTracksByPort.get(port).add(MidiFactory.createProgramChangeEvent(0, ch, tick));
						} else {
							if (pendingChannelPrefix != null) {
								initTracksByPort.get(port).add(pendingChannelPrefix);
								pendingChannelPrefix = null;
							}
							initTracksByPort.get(port).add(evt);
						}
					} else {
						assert false: "Unexpected MIDI message type: " + msg.getClass().getSimpleName();
                    }
				}
			}

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
            return sequenceCache.getInstrumentExt(port, channel, tick, isDrumsTrack(track));
		}
	}

	private boolean isDrumsTrack(int track) {
		return sequenceCache.isXGDrumsTrack(track) || sequenceCache.isGSDrumsTrack(track)
				|| sequenceCache.isGM2DrumsTrack(track) || sequenceCache.isDrumsTrack(track);
	}
}