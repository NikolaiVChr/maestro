package com.digero.maestro.midi;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.sound.midi.*;

import com.digero.common.midi.*;
import com.digero.common.util.FileParseException;
import com.digero.maestro.view.MiscSettings;

/**
 * Takes a midi input and expands each instrument to its own track. Works with GM2, XG, GS, GM
 * 
 */
public class TrackSplitter {
	private static final Logger log = Logger.getLogger("export.midi");
	
	private SequenceDataCache sequenceCache = null;
	private Sequence oldSequence = null;
	private boolean isGM = true;

	public Sequence split(File file)
            throws InvalidMidiDataException, FileParseException, IOException {

		// "temp_midi_expander" is super important, else it will wipe all maestro settings as it's tied to package, not class inside the package.
		Preferences prefs = Preferences.userNodeForPackage(this.getClass()).node("temp_midi_expander");
        try {
            prefs.clear();
        } catch (BackingStoreException e) {
            throw new RuntimeException(e);
        }
        SequenceInfo sequenceInfo = SequenceInfo.fromMidi(file, new MiscSettings(prefs, false), false, false, false, false, 1);

		this.sequenceCache = sequenceInfo.getDataCache();
		this.oldSequence = sequenceInfo.getSequence();
		SortedMap<Integer, Integer> portMap = sequenceCache.getPortMap();
		MidiStandard standard = sequenceInfo.standard;

		int resolution = oldSequence.getResolution();
		float divisionType = oldSequence.getDivisionType();
		Sequence expandedSequence = new Sequence(divisionType, resolution);

		isGM = standard == MidiStandard.GM;

		Track[] oldTracks = oldSequence.getTracks();
		Track newMetaTrack = expandedSequence.createTrack();

		newMetaTrack.add(MidiFactory.createTrackNameEvent("META"));
		if (standard == MidiStandard.GM) {
			newMetaTrack.add(new MidiEvent(MidiFactory.createGMReset(),0L));
		} else if (standard == MidiStandard.GS) {
			newMetaTrack.add(new MidiEvent(MidiFactory.createGSReset(),0L));
		} else if (standard == MidiStandard.XG) {
			newMetaTrack.add(new MidiEvent(MidiFactory.createXGReset(),0L));
		} else if (standard == MidiStandard.GM2) {
			newMetaTrack.add(new MidiEvent(MidiFactory.createGM2Reset(),0L));
		}

		Map<Integer, Track> initTracksByPort = new HashMap<>();
		Set<Integer> activePorts = new HashSet<>(portMap.values());
		activePorts.add(0);

		Map<String, Integer> extensionDrumPorts = new HashMap<>();
		int freePort = 0;
		while (activePorts.contains(freePort)) freePort++;

		for (int oldTrackNumber = 0; oldTrackNumber < oldTracks.length; oldTrackNumber++) {
			//if (oldTrackNumber != 0 && oldTrackNumber != 4) continue;
			if (isDrumsTrack(oldTrackNumber)) {
				Track t = oldTracks[oldTrackNumber];
				int p = portMap.get(oldTrackNumber);
				for (int i = 0; i < t.size(); i++) {
					MidiMessage msg = t.get(i).getMessage();
					if (msg instanceof ShortMessage shortMsg) {
						int ch = shortMsg.getChannel();
						if (isDrumsTrack(oldTrackNumber)) {
							String pKey = p + ":" + ch+ ":" + oldTrackNumber;
							if (!extensionDrumPorts.containsKey(pKey)) {
								extensionDrumPorts.put(pKey, freePort);
								activePorts.add(freePort);
								while (activePorts.contains(freePort)) freePort++;
							}
						}
						break; // We found the channel for this track, move to the next track
					}
				}
			}
		}


		for (int p : activePorts) {
			Track initTrack = expandedSequence.createTrack();
			initTrack.add(MidiFactory.createTrackNameEvent("NON-META " + p));

			// Lock this initialization track to the correct hardware port
			MidiEvent evtPort = MidiFactory.createPortEvent(p);
			if (evtPort != null) {
				initTrack.add(evtPort);
			} else {
				log.severe("Failed to create port event when expanding midi");
				return null;
			}
			initTracksByPort.put(p, initTrack);
		}
		long lastEOTTick = 0L;
		for (int oldTrackNumber = 0; oldTrackNumber < oldTracks.length; oldTrackNumber++) {
			//if (oldTrackNumber != 0 && oldTrackNumber != 4) continue;
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

				int targetPort = port;
				int targetChannel = -1;

				if (msg instanceof ShortMessage shortMsg) {
                    int cmd = shortMsg.getCommand();
					int channel = shortMsg.getChannel();
					targetChannel = channel;

					String pKey = port + ":" + channel+":"+oldTrackNumber;
					boolean hasExtDrum = extensionDrumPorts.containsKey(pKey);

					if (hasExtDrum && channel != MidiConstants.DRUM_CHANNEL) {
						targetPort = extensionDrumPorts.get(pKey);
						targetChannel = MidiConstants.DRUM_CHANNEL; // Force to Ch 10

						// Rewrite the binary message for Channel 10
						try {
							ShortMessage newMsg = new ShortMessage();
							newMsg.setMessage(cmd, targetChannel, shortMsg.getData1(), shortMsg.getData2());
							evt = new MidiEvent(newMsg, tick);
							shortMsg = newMsg; // Update local msg for downstream routing
						} catch (InvalidMidiDataException e) {
							log.warning("Failed to rewrite drum channel.");
						}
					}

					if (pendingChannelPrefix != null) {
						pendingChannelPrefix = null;
					}

					if (cmd == ShortMessage.NOTE_OFF || cmd == ShortMessage.NOTE_ON) {
						instr = handleEvent(oldTrackNumber, notesOn, port, tick, channel, cmd, shortMsg);
						// if (instr == null) System.out.println("instr==null "+on);
						// if ("".equals(instr)) System.out.println("instr=='' "+on);
					} else if (cmd == ShortMessage.PROGRAM_CHANGE ||
							(cmd == ShortMessage.CONTROL_CHANGE && (shortMsg.getData1() == MidiConstants.BANK_SELECT_MSB || shortMsg.getData1() == MidiConstants.BANK_SELECT_LSB))) {
						// If this event was shifted to Channel 10, vaporize all Bank Selects!
						//if (targetChannel == MidiConstants.DRUM_CHANNEL && cmd == ShortMessage.CONTROL_CHANGE) {
						//	continue evtIter; // Drop the MSB/LSB completely!
						//}
						initTracksByPort.get(targetPort).add(evt);
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
						String trackID = targetPort+":"+targetChannel+":"+instr;
						Track newTrack = newTracks.get(trackID);
						if (newTrack == null) {
							newTrack = expandedSequence.createTrack();
							newTrack.add(MidiFactory.createTrackNameEvent(oldTrackName + " : " + trackCounter));
                            //if (oldEndOfTrack != null) newTrack.add(MidiFactory.createEndOfTrackEvent(oldEndOfTrack.getTick()));

							// We put the port change in every one of the new tracks if the old had it.
							MidiEvent evtPort = MidiFactory.createPortEvent(targetPort);
							if (evtPort != null) {
								newTrack.add(evtPort);
							} else {
								log.severe("Failed to create port event when expanding midi");
								return null;
							}

							trackCounter += 1;
							newTracks.put(trackID, newTrack);
						}
						newTrack.add(evt);
					} else {
						initTracksByPort.get(targetPort).add(evt);
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
						boolean isGSPartModeSwitch = false;

						if (message.length == 9 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x43
								&& (message[3] & 0xFF) == 0x4C && (message[4] & 0xFF) == 0x08 && (message[6] & 0xFF) == 0x07
								&& (message[8] & 0xFF) == 0xF7 && (message[5] & 0xFF) < 16) {
							isXGPartModeSwitch = true;
						}

						// Intercept GS Drum Switch
						isGSPartModeSwitch = message.length == 11 && (message[0] & 0xFF) == 0xF0
								&& (message[1] & 0xFF) == 0x41 && (message[3] & 0xFF) == 0x42
								&& (message[4] & 0xFF) == 0x12 && (message[5] & 0xFF) == 0x40
								&& (message[7] & 0xFF) == 0x15 && (message[10] & 0xFF) == 0xF7;

						if (isXGPartModeSwitch || isGSPartModeSwitch) {
							log.fine("TrackSplitter: Deleting obsolete drum SysEx at tick " + tick);
							if (pendingChannelPrefix != null) {
								pendingChannelPrefix = null;
							}
							continue evtIter;
						}

						if (MidiUtils.isResetGM(message) || MidiUtils.isResetXG(message) || MidiUtils.isResetGS(message) || MidiUtils.isResetGM2(message)) {
							if (pendingChannelPrefix != null) {
								pendingChannelPrefix = null;
							}
							continue evtIter;
						}

						// XG SysEx Patch Change format: F0 43 10 4C 08 nn 03 pp F7
						boolean isXGSysexPatch = message.length == 9 && (message[0] & 0xFF) == 0xF0
								&& (message[1] & 0xFF) == 0x43 && (message[3] & 0xFF) == 0x4C
								&& (message[4] & 0xFF) == 0x08 && (message[6] & 0xFF) < 4 && (message[6] & 0xFF) > 0
								&& (message[8] & 0xFF) == 0xF7 && (message[5] & 0xFF) < 16;

						if (isXGSysexPatch) {
							int ch = message[5] & 0xFF;
							int param = message[6] & 0xFF;
							int value = message[7] & 0xFF;

							int transPort = port;
							int transChannel = ch;

							// Check if this channel was funneled into a new port by loop 1
							String hKey = port + ":" + ch+":"+oldTrackNumber;
							if (extensionDrumPorts.containsKey(hKey)) {
								transPort = extensionDrumPorts.get(hKey);
								transChannel = MidiConstants.DRUM_CHANNEL;
							}

							try {
								ShortMessage translatedMsg = null;

								if (param == 1) { // 0x01 = Bank Select MSB
									// Do not send MSB 127 to the drum channel
									//if (!(transChannel == MidiConstants.DRUM_CHANNEL && value == 127)) {
										translatedMsg = new ShortMessage(ShortMessage.CONTROL_CHANGE, transChannel, MidiConstants.BANK_SELECT_MSB, value);
									//}
								} else if (param == 2) { // 0x02 = Bank Select LSB
									// do not use LSB on drums
									//if (transChannel != MidiConstants.DRUM_CHANNEL) {
										translatedMsg = new ShortMessage(ShortMessage.CONTROL_CHANGE, transChannel, MidiConstants.BANK_SELECT_LSB, value);
									//}
								} else if (param == 3) { // 0x03 = Program Change
									translatedMsg = new ShortMessage(ShortMessage.PROGRAM_CHANGE, transChannel, value, 0);
								}

								if (translatedMsg != null) {
									if (pendingChannelPrefix != null) {
										pendingChannelPrefix = null;
									}
									initTracksByPort.get(transPort).add(new MidiEvent(translatedMsg, tick));
								}

							} catch (InvalidMidiDataException e) {
								log.warning("Failed to translate XG SysEx to ShortMessage.");
							}
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