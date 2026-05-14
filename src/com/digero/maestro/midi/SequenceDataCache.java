package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;

import com.digero.common.midi.ExtensionMidiInstrument;
import com.digero.common.midi.IBarNumberCache;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiInstrument;
import com.digero.common.midi.MidiStandard;
import com.digero.common.midi.MidiUtils;
import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.TimeSignature;
import com.digero.common.util.LyricLine;
import com.digero.common.util.Quad;
import com.digero.common.util.Util;
import com.digero.maestro.abc.TimingInfo;

public class SequenceDataCache implements MidiConstants, ITempoCache, IBarNumberCache {
	private static final Logger log = Logger.getLogger("import.midi");
	private final int tickResolution;
	private final float divisionType;
	private final int primaryTempoMPQ;
	private final TimeSignature timeSignature;
	private final NavigableMap<Long, TempoEvent> tempo = new TreeMap<>();
    private final NavigableMap<Long, TempoEvent> tempoByMicros = new TreeMap<>();

	private final long songLengthTicks;
	private static final int NO_RESULT = -250;

	private final MapByChannelPort instruments = new MapByChannelPort(DEFAULT_INSTRUMENT);
	private final MapByChannel channelVolume = new MapByChannel(DEFAULT_CHANNEL_VOLUME);//TODO: this should be port aware too, but for backward compat, it is not
	private final MapByChannel expression = new MapByChannel(DEFAULT_EXPRESSION);//TODO: this should be port aware too, but for backward compat, it is not
	private final MapByChannelPort pitchBendRangeCoarse = new MapByChannelPort(DEFAULT_PITCH_BEND_RANGE_SEMITONES);
	private final MapByChannelPort pitchBendRangeFine = new MapByChannelPort(DEFAULT_PITCH_BEND_RANGE_CENTS);
	private final MapByChannelPort bendMap;
	private final MapByChannelPort rpnLSBMap = new MapByChannelPort(DEFAULT_RPN_NULL);
	private final MapByChannelPort rpnMSBMap = new MapByChannelPort(DEFAULT_RPN_NULL);
	private final MapByChannelPort panMap;
	private final MapByChannelPort mapMSB = new MapByChannelPort(0);
	private final MapByChannelPort mapLSB = new MapByChannelPort(0);
	private final MapByChannelPort mapPatch = new MapByChannelPort(0);
	private final DrumBankType[] brandDrumBanks;
	private final MidiStandard standard;
	private final boolean[] rolandDrumChannels;
	private final boolean[] yamahaDrumChannels;
	public boolean hasPorts = false;
	private String copyright = "";
	private final boolean ignoreZeroChannelVolume;
	private final MidiText midiText;
    private boolean tempoInHigherTracks = false;
	private String fileName = "";
	private final int usingNewMidiLayout;

	public SequenceDataCache(Sequence song, MidiStandard standard, boolean[] rolandDrumChannels,
							 Map<Integer, ArrayList<TreeMap<Long, Boolean>>> yamahaDrumSwitches, boolean[] yamahaDrumChannels,
			                 Map<Integer, ArrayList<TreeMap<Long, Boolean>>> mmaDrumSwitches, SortedMap<Integer, Integer> portMap,
			boolean onlyFirstTrackTempos, boolean ignoreZeroChannelVolume, boolean ignoreMidiText,
			String fileName, int usingNewMidiLayout) {

		this.fileName = fileName;
		this.usingNewMidiLayout = usingNewMidiLayout;

        // This is total accumulated duration in micros of each tempo used in the song
		Map<Integer, Long> tempoLengths = new HashMap<>();// MPQ -> micros

		this.standard = standard;
		this.rolandDrumChannels = rolandDrumChannels;
		this.yamahaDrumChannels = yamahaDrumChannels;
		
		this.ignoreZeroChannelVolume = ignoreZeroChannelVolume;

		brandDrumBanks = new DrumBankType[song.getTracks().length];

		tempo.put(0L, TempoEvent.DEFAULT_TEMPO);
		TimeSignature foundTimeSignature = null;

		divisionType = song.getDivisionType();
		tickResolution = song.getResolution();

		midiText = new MidiText(this);
		
		/*
		 * We need to be able to know which tracks have drum notes. We also need to know what instrument voices are used
		 * in each track, so we build maps of voice changes that TrackInfo later can use to build strings of instruments
		 * for each track.
		 * 
		 * This among other things we will find out by iterating through all MidiEvents.
		 * 
		 */
		List<Quad<Integer, Integer, Long, Double>> pitchWheelMap = new ArrayList<>();
		panMap = new MapByChannelPort(PAN_CENTER);
		Track[] tracks = song.getTracks();
		hasPorts = false;
		long lastTick = 0L;
		final boolean specCompliant = false;
		if (standard != MidiStandard.PREVIEW) {
			for (int iiTrack = 0; iiTrack < tracks.length; iiTrack++) {
				// Build a map of ports, this is done before main iteration
				// due to that patch changes need to know this.
				Track track = tracks[iiTrack];
				int port = 0;
				portMap.put(iiTrack, port);

				for (int jj = 0, sz1 = track.size(); jj < sz1; jj++) {
					MidiEvent evt = track.get(jj);
					long tick = evt.getTick();
					if (tick > 0L) break;
					MidiMessage msg = evt.getMessage();
					if (msg instanceof MetaMessage meta) {
						if (meta.getType() == META_PORT_CHANGE) {
							byte[] portChange = meta.getData();
							if (portChange.length == 1) {
								// Support for (non-midi-standard) port assignments used by Cakewalk and
								// Musescore.
								// We only support this for GM, and only super well-formed (tick == 0).
								port = (int) portChange[0] & 0xFF;
								log.fine("Port change on track " + iiTrack + ", tick " + tick + ", port " + MidiUtils.formatBytes(portChange));
								portMap.put(iiTrack, port);
								boolean isGM = MidiStandard.GM == standard;
								if (!isGM) {
									log.info(fileName + ": "+standard+" with ports");
								}
								hasPorts = true;// = isGM
								break;
							}
						} else if (meta.getType() == META_PORT_NAME) {
							byte[] data = meta.getData();
							log.warning("Named port: " + (new String(data)));
						}
					}
				}
			}
			for (int iiTrack = 0; iiTrack < tracks.length; iiTrack++) {
				Track track = tracks[iiTrack];
				int port = portMap.get(iiTrack);
				for (int jj = 0, sz1 = track.size(); jj < sz1; jj++) {
					MidiEvent evt = track.get(jj);
					long tick = evt.getTick();
					MidiMessage msg = evt.getMessage();
					if (msg instanceof ShortMessage shortMsg) {
						int cmd = shortMsg.getCommand();
						int ch = shortMsg.getChannel();
						if (cmd == ShortMessage.CONTROL_CHANGE) {
							switch (shortMsg.getData1()) {
								case REGISTERED_PARAMETER_NUMBER_MSB:
									rpnMSBMap.put(port, ch, tick, shortMsg.getData2());
									break;
								case REGISTERED_PARAMETER_NUMBER_LSB:
									rpnLSBMap.put(port, ch, tick, shortMsg.getData2());
									break;
								case RESET_ALL_CONTROLLERS:
									if (tick > 0L) {
										String str = "";
										int rl = rpnLSBMap.get(port, ch, tick);
										int rm = rpnMSBMap.get(port, ch, tick);
										int ex = expression.get(ch, tick);
										boolean changingStuff = rl != 127 || rm != 127 || (specCompliant && ex != 127);// too much hassle to detect if bend wheel was active.
										if (changingStuff) {
											str += "\n rpn lsb " + rl + " -> " + DEFAULT_RPN_NULL;
											str += "\n rpn msb " + rm + " -> " + DEFAULT_RPN_NULL;
											if (specCompliant) str += "\n expr " + ex + " -> " + DEFAULT_EXPRESSION;
											str += "\n pitch wheel -> 0%";
										}

										//this will remove some pitch bending changes from some songs, but it's the right thing to do as per specs:
										rpnLSBMap.putIfAbsent(port, ch, tick, DEFAULT_RPN_NULL);
										rpnMSBMap.putIfAbsent(port, ch, tick, DEFAULT_RPN_NULL);
										pitchWheelMap.add(new Quad<>(port, ch, tick, 0.0d));

										// TODO: Spec dictates this, but it will break backwards compatibility with projects volume.
										//       Plan is to make an option or toggle inside projects like when introduced expressions.
										//expression.put(ch, tick, DEFAULT_EXPRESSION);

										if (changingStuff) log.warning(fileName + ": Resetting all controllers on channel " + ch + ", tick " + tick + str);
									}
									break;
							}
						}
					} else if (specCompliant && tick > 0L && msg instanceof SysexMessage sysex) {
						byte[] smsg = sysex.getMessage();
						boolean isResetGM = false;
						boolean isResetGM2 = false;
						boolean isResetXG = false;
						boolean isResetGS = false;
						if ((isResetGM = MidiUtils.isResetGM(smsg)) || (isResetGS = MidiUtils.isResetGS(smsg))
								|| (isResetXG = MidiUtils.isResetXG(smsg)) || (isResetGM2 = MidiUtils.isResetGM2(smsg))) {

							// TODO: This will break backwards compatibility for sure :(
							//       Therefore the specCompliant bool stops it from happening.
							//       In future versions, this behavior may be configurable.
							for (int ch = 0; ch < CHANNEL_COUNT_ABC; ch++) {
								rpnLSBMap.put(port, ch, tick, DEFAULT_RPN_NULL);
								rpnMSBMap.put(port, ch, tick, DEFAULT_RPN_NULL);
								expression.put(ch, tick, DEFAULT_EXPRESSION);
								channelVolume.put(ch, tick, DEFAULT_CHANNEL_VOLUME);
								panMap.put(port, ch, tick, PAN_CENTER);
								pitchWheelMap.add(new Quad<>(port, ch, tick, 0.0d));
								pitchBendRangeCoarse.put(port, ch, tick, DEFAULT_PITCH_BEND_RANGE_SEMITONES);
								pitchBendRangeFine.put(port, ch, tick, DEFAULT_PITCH_BEND_RANGE_CENTS);
								if (MidiStandard.GM == standard && isResetGM) {
									// This has issues. What if there is a GM reset in the middle
									// of a GS file? This will reset the patch, which is not ideal.
									// This code should probably never be run, even though its spec compliant.
									mapMSB.put(port, ch, tick, 0);
									mapLSB.put(port, ch, tick, 0);
									mapPatch.put(port, ch, tick, 0);
								}
							}
							log.warning(fileName+": Resetting everything, tick "+tick+" GM="+isResetGM+" XG="+isResetXG+" GS="+isResetGS+" GM2="+isResetGM2);
						}
					}
				}
			}
			TimeSignature backupTimeSignature = null;
			for (int iTrack = 0; iTrack < tracks.length; iTrack++) {
				Track track = tracks[iTrack];
				int port = portMap.get(iTrack);
				for (int j = 0, sz = track.size(); j < sz; j++) {
					MidiEvent evt = track.get(j);
					MidiMessage msg = evt.getMessage();
					long tick = evt.getTick();
					if (tick > lastTick)// && msg instanceof ShortMessage
						lastTick = tick;

					if (msg instanceof ShortMessage shortMsg) {
                        int cmd = shortMsg.getCommand();
						int ch = shortMsg.getChannel();

						if (cmd == ShortMessage.NOTE_ON) {
							if (rolandDrumChannels != null && rolandDrumChannels[ch] && MidiStandard.GS == standard) {
								brandDrumBanks[iTrack] = DrumBankType.GS_DRUM;
							} else if (brandDrumBanks[iTrack] != DrumBankType.XG_DRUM && MidiStandard.XG == standard
									&& yamahaDrumSwitches != null && yamahaDrumSwitches.get(port).get(ch).floorEntry(tick) != null
									&& yamahaDrumSwitches.get(port).get(ch).floorEntry(tick).getValue()) {
								brandDrumBanks[iTrack] = DrumBankType.XG_DRUM;// XG drums
							} else if (brandDrumBanks[iTrack] != DrumBankType.GM2_DRUM && MidiStandard.GM2 == standard
									&& mmaDrumSwitches != null && mmaDrumSwitches.get(port).get(ch).floorEntry(tick) != null
									&& mmaDrumSwitches.get(port).get(ch).floorEntry(tick).getValue()) {
								brandDrumBanks[iTrack] = DrumBankType.GM2_DRUM;// GM2 drums
							} else if (ch == DRUM_CHANNEL
									&& (MidiStandard.GM == standard || MidiStandard.ABC == standard)) {
								brandDrumBanks[iTrack] = DrumBankType.STANDARD_DRUM;// GM drums on channel #10
							}
						} else if (cmd == ShortMessage.PROGRAM_CHANGE) {
							if (shortMsg.getData1() > 127) {
								log.warning(fileName+"; Channel "+ch+": Ignoring program change out of range: "+shortMsg.getData1());
								continue;
							}
							if (((ch != DRUM_CHANNEL && rolandDrumChannels == null && yamahaDrumChannels == null)
									|| ((rolandDrumChannels == null || MidiStandard.GS != standard
											|| !rolandDrumChannels[ch])
											&& (yamahaDrumChannels == null || MidiStandard.XG != standard
													|| !yamahaDrumChannels[ch])))
									&& (MidiStandard.XG != standard || yamahaDrumSwitches == null
											|| yamahaDrumSwitches.get(port).get(ch).floorEntry(tick) == null
											|| !yamahaDrumSwitches.get(port).get(ch).floorEntry(tick).getValue())
									&& (MidiStandard.GM2 != standard || mmaDrumSwitches == null
											|| mmaDrumSwitches.get(port).get(ch).floorEntry(tick) == null
											|| !mmaDrumSwitches.get(port).get(ch).floorEntry(tick).getValue())) {
								instruments.put(portMap.get(iTrack), ch, tick, shortMsg.getData1());
								log.fine("Instrument change on track "+iTrack+", tick "+tick+", instrument "+MidiInstrument.fromId(shortMsg.getData1())+ ", port "+portMap.get(iTrack)+", channel "+ch);
							}
							mapPatch.put(port, ch, tick, shortMsg.getData1());
						} else if (cmd == ShortMessage.CONTROL_CHANGE) {
							switch (shortMsg.getData1()) {
							case CHANNEL_VOLUME_CONTROLLER_COARSE:
								if (shortMsg.getData2() != 0 || !ignoreZeroChannelVolume)
									channelVolume.put(ch, tick, Math.clamp(shortMsg.getData2(), 0, 127));
								break;
							case CHANNEL_EXPRESSION_CONTROLLER:
								expression.put(ch, tick, Math.clamp(shortMsg.getData2(), 0, 127));
								break;
								/*
							case REGISTERED_PARAMETER_NUMBER_MSB:
								// TODO: channel, not tracks!!
								if (rpn[ch] == REGISTERED_PARAM_NONE) rpn[ch] = 0;
								rpn[ch] = (rpn[ch] & 0x7F) | ((shortMsg.getData2() & 0x7F) << 7);
								break;
							case REGISTERED_PARAMETER_NUMBER_LSB:
								// there is no guard to zero it here on purpose
								rpn[ch] = (rpn[ch] & (0x7F << 7)) | (shortMsg.getData2() & 0x7F);
								break;
								*/
							case DATA_ENTRY_COARSE:
								if (getRPN(port, ch, tick) == REGISTERED_PARAM_PITCH_BEND_RANGE) {
									if (shortMsg.getData2() > 127) {
										log.warning(fileName + "; Channel " + ch + " Port " + port + ": Clamping coarse pitch bend wheel range out of bounds: " + shortMsg.getData2()+" semitones");
									}
									pitchBendRangeCoarse.put(port, ch, tick, Math.clamp(shortMsg.getData2(), 0, 127));
								}
								break;
							case DATA_ENTRY_FINE:
								if (getRPN(port, ch, tick) == REGISTERED_PARAM_PITCH_BEND_RANGE) {
									if (shortMsg.getData2() > 99) {
										log.info(fileName + "; Channel " + ch + " Port " + port + ": Clamping fine pitch bend wheel range out of bounds: " + shortMsg.getData2()+" cents.");
									}
									pitchBendRangeFine.put(port, ch, tick, Math.clamp(shortMsg.getData2(), 0, 99));
								}
								break;
							case DATA_BUTTON_INCREMENT:
								if (getRPN(port, ch, tick) == REGISTERED_PARAM_PITCH_BEND_RANGE) {
									int currentFine = pitchBendRangeFine.get(port, ch, tick);
									int currentCoarse = pitchBendRangeCoarse.get(port, ch, tick);

									currentFine++;

									// RP-018: Wrap to next semitone when reaching 100 cents
									if (currentFine >= 100) {
										if (currentCoarse < 127) {
											currentFine = 0;
											currentCoarse++;
											pitchBendRangeCoarse.put(port, ch, tick, currentCoarse);
										} else {
											// Hard clamp at the absolute maximum (127 semitones, 99 cents)
											currentFine = 99;
										}
									}
									log.fine("DATA_BUTTON_INCREMENT for pitch bend detected.");
									pitchBendRangeFine.put(port, ch, tick, currentFine);
								}
								break;
							case DATA_BUTTON_DECREMENT:
								if (getRPN(port, ch, tick) == REGISTERED_PARAM_PITCH_BEND_RANGE) {
									int currentFine2 = pitchBendRangeFine.get(port, ch, tick);
									int currentCoarse2 = pitchBendRangeCoarse.get(port, ch, tick);

									currentFine2--;

									// RP-018: Wrap to previous semitone when dropping below 0 cents
									if (currentFine2 < 0) {
										if (currentCoarse2 > 0) {
											currentFine2 = 99;
											currentCoarse2--;
											pitchBendRangeCoarse.put(port, ch, tick, currentCoarse2);
										} else {
											// Hard clamp at absolute minimum (0 semitones, 0 cents)
											currentFine2 = 0;
										}
									}

									pitchBendRangeFine.put(port, ch, tick, currentFine2);
									log.fine("DATA_BUTTON_DECREMENT for pitch bend detected.");
								}
								break;
							case PAN_CONTROL:
								panMap.put(port, ch, tick, Math.clamp(shortMsg.getData2(),0,127));
								break;
							case BANK_SELECT_MSB:
								if (shortMsg.getData2() > 127) {
									log.warning(fileName+"; Channel "+ch+": Ignoring MSB bank address out of range: "+shortMsg.getData2());
									continue;
								}

								if (usingNewMidiLayout == 0) {
									if (ch != DRUM_CHANNEL || MidiStandard.XG != standard || shortMsg.getData2() == 126
											|| shortMsg.getData2() == 127) {
										// Due to XG drum part protect mode being ON, drum channel 9 only can switch
										// between MSB 126 & 127.
										mapMSB.put(port, ch, tick, shortMsg.getData2());
									} else if (ch == DRUM_CHANNEL && MidiStandard.XG == standard && shortMsg.getData2() != 126
											&& shortMsg.getData2() != 127) {
										log.finer("XG Drum Part Protect Mode prevented bank select MSB.");
									}
									// if(ch==DRUM_CHANNEL) System.err.println("Bank select MSB "+m.getData2()+" "+tick);
								} else {
									boolean isXGDrumChannel = false;
									if (MidiStandard.XG == standard && yamahaDrumSwitches != null) {
										Map.Entry<Long, Boolean> entry = yamahaDrumSwitches.get(port).get(ch).floorEntry(tick);
										if (entry != null && entry.getValue()) {
											isXGDrumChannel = true;
										}
									}

									if (isXGDrumChannel && shortMsg.getData2() != 126 && shortMsg.getData2() != 127) {
										log.finer("XG Drum Part Protect Mode prevented bank select MSB on port " + port + " channel " + ch);
									} else {
										mapMSB.put(port, ch, tick, shortMsg.getData2());
									}
								}
								break;
							case BANK_SELECT_LSB:
								if (shortMsg.getData2() > 127) {
									log.warning(fileName+"; Channel "+ch+": Ignoring LSB bank address out of range: "+shortMsg.getData2());
									continue;
								}
								mapLSB.put(port, ch, tick, shortMsg.getData2());
								// if(ch==DRUM_CHANNEL) System.err.println("Bank select LSB "+m.getData2()+" "+tick);
								break;
							}
						} else if (cmd == ShortMessage.PITCH_BEND) {
							int value1 = shortMsg.getData1();
							int value2 = shortMsg.getData2();
							if (value1 > 127 || value2 > 127) {
								log.warning(fileName+"; Channel "+ch+": Clamping pitch bend out of range: "+value1+","+value2);
							}
							value1 = Math.clamp(value1,0,127);
							value2 = Math.clamp(value2,0,127);
							double pct = 2.0d * (((value1 | (value2 << 7)) / (double) (1 << 14)) - 0.5d);
							pitchWheelMap.add(new Quad<>(port, ch, tick, pct));
							// Notice we put in the bend even if its a repeat of same bend.
							// Reason is that later on another track there might get put some
							// bends in between them.
						}
					} else if (msg instanceof SysexMessage sysex) {
                        byte[] message = sysex.getMessage();
						if (message.length == 9 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x43
								&& (message[3] & 0xFF) == 0x4C // this check can change the listed instr, but that does not break back compat
								&& (message[4] & 0xFF) == 0x08 && (message[8] & 0xFF) == 0xF7) {

							if (MidiStandard.XG == standard) {
								String bank = message[6] == 1 ? "MSB"
										: (message[6] == 2 ? "LSB" : (message[6] == 3 ? "Patch" : ""));
								if (!"".equals(bank) && message[5] < 16 && message[5] >= 0
										&& message[7] < 128 && message[7] >= 0) {
									switch (bank) {
										// XG Drum Part Protect Mode does not apply to sysex bank changes.
										case "MSB" -> mapMSB.put(port, (int) message[5], tick, (int) message[7]);
										case "Patch" -> mapPatch.put(port, (int) message[5], tick, (int) message[7]);
										case "LSB" -> mapLSB.put(port, (int) message[5], tick, (int) message[7]);
									}
								} else if (usingNewMidiLayout >= 1 && message[6] == 7 && message[5] < 16 && message[5] >= 0 && message[7] <= 5) {
									// Reset the xg bank
									boolean isDrum = (message[7] > 0);
									mapMSB.put(port, (int) message[5], tick, isDrum ? 127 : 0);
									mapPatch.put(port, (int) message[5], tick, 0); // Reset to Standard Kit / Grand Piano
								}
							}
						} else if (!ignoreMidiText) {
							midiText.collectSysex(tick, message, iTrack);
						}
					} else if (divisionType == Sequence.PPQ && MidiUtils.isMetaTempo(msg)) {

                        int tempoRaw = MidiUtils.getTempoMPQ(msg);
                        if (tempoRaw > 0) {
                            if (iTrack > 0) tempoInHigherTracks = true;

                            if (!onlyFirstTrackTempos || iTrack == 0) {
                                // Note that this is also done in the SequencerWrapper.TempoCacheSlow

                                tempo.put(tick, new TempoEvent(tempoRaw, tick, 0L));// micros is added later
                                if (iTrack != 0) {
                                    log.fine("Track " + iTrack + " has tempo message in non-first track. " + MidiUtils.convertTempo(tempoRaw) + " BPM, tick " + tick);
                                    // we move the tempo event to track 0 so the playback
                                    // matches our internal tempos.
                                    // This means that if the user expands the midi
                                    // the expanded midi will have all tempos in track 0 too.
                                    // Not ideal but at least consistent.

                                    track.remove(evt);
                                    sz--;
                                    j--;
                                    tracks[0].add(evt);
                                    // if already tempo(s) at this tick in track 0, since this is added later will take
                                    // precedence over the existing one(s) in track 0.
                                }
                            }
                        } else {
                            if (tempoRaw == 0) log.warning("MIDI has tempo message of zero MPQ! Ignoring it..");
							if (tempoRaw < 0) log.warning("MIDI has tempo message of negative MPQ! Ignoring it..");
                            track.remove(evt);
                            sz--;
                            j--;
                        }
					} else if (msg instanceof MetaMessage m) {
                        int type = m.getType();
						byte[] data = m.getData();
						if (type == META_TIME_SIGNATURE && foundTimeSignature == null) {
							// TimeSignature in this class is used to keep track of source MIDIs meter.
							// The one in TrackInfo is used to initially populate the meter field and abcsong.
							// The one in AbcSong is used for output to abc.
							try {
								foundTimeSignature = new TimeSignature(m);
							} catch (InvalidMidiDataException e) {
								if (backupTimeSignature == null) {
									try {
										backupTimeSignature = new TimeSignature(m, true);
									} catch (InvalidMidiDataException e2) {
										// Ignore the illegal time signature
									}
								}
							}
						} else if (!ignoreMidiText && type == META_LYRIC && m.getData() != null) {				
							midiText.collectTxt(tick, data, META_LYRIC, iTrack);
						} else if (!ignoreMidiText && type == META_TEXT && m.getData() != null) {				
							midiText.collectTxt(tick, data, META_TEXT, iTrack);
						} else if (!ignoreMidiText && type == META_MARKER && m.getData() != null) {			
							midiText.collectTxt(tick, data, META_MARKER, iTrack);
						} else if (!ignoreMidiText && type == META_CUE_POINT && m.getData() != null) {			
							midiText.collectTxt(tick, data, META_CUE_POINT, iTrack);
						} else if (!ignoreMidiText && type == META_M_LIVE && m.getData() != null) {			
							midiText.collectTxt(tick, data, META_M_LIVE, iTrack);
						} else if (m.getType() == META_COPYRIGHT && tick == 0L && iTrack == 0) {
							log.finer("\n(c): "+MidiUtils.formatBytes(data));
							String tmp = "";
							if (!ignoreMidiText) tmp = MidiUtils.decodeMidiText(data).trim();
							
							if (!tmp.isEmpty()) {
								copyright = tmp;
								log.info("MIDI copyright: "+tmp);
							}
						}
					}
				}
			}
			// We don't like this illegal meter, but if nothing better came long we use it.
			if (foundTimeSignature == null)
				foundTimeSignature = backupTimeSignature;

			// Setup default banks for extensions:
			Set<Integer> activePorts = new HashSet<>(portMap.values());
			activePorts.add(0);
			for (int port : activePorts) {
				for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
					mapPatch.put(port, i, -1, 0);
					mapLSB.put(port, i, -1, 0);
				}
				if (MidiStandard.XG == standard && yamahaDrumChannels != null) {
					// Bank 127 is implicit the default on drum channels in XG.
					for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
						if (yamahaDrumChannels[i])
							mapMSB.put(port, i, -1, 127);
						else
							mapMSB.put(port, i, -1, 0);
					}
				} else if (MidiStandard.GM2 == standard) {
					// Bank 120 is implicit the default on drum channel in GM2.
					// Bank 121 is implicit the default on all other channels in GM2.

					final int GM2_MSB_DEFAULT_CHROMATIC = 121;
					final int GM2_MSB_DEFAULT_RHYTHM = 120;

					mapMSB.put(port, 0, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 1, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 2, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 3, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 4, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 5, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 6, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 7, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 8, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, DRUM_CHANNEL, -1, GM2_MSB_DEFAULT_RHYTHM);
					mapMSB.put(port, 10, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 11, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 12, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 13, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 14, -1, GM2_MSB_DEFAULT_CHROMATIC);
					mapMSB.put(port, 15, -1, GM2_MSB_DEFAULT_CHROMATIC);
				} else {
					for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
						mapMSB.put(port, i, -1, 0);
					}
				}
			}
		} else {
			// If this sequence is for preview then we only need to find lastTick and tempos
			for (int iTrack = 0; iTrack < tracks.length; iTrack++) {
				Track track = tracks[iTrack];

				for (int j = 0, sz = track.size(); j < sz; j++) {
					MidiEvent evt = track.get(j);
					MidiMessage msg = evt.getMessage();
					long tick = evt.getTick();
					if (tick > lastTick)// && msg instanceof ShortMessage
						lastTick = tick;

					if (MidiUtils.isMetaTempo(msg)) {

						int tempoRaw = MidiUtils.getTempoMPQ(msg);
						if (tempoRaw != 0) {
							tempo.put(tick, new TempoEvent(tempoRaw, tick, 0L));// micros is added later
						}
					}
				}
			}
		}

        // We now populate the tempo events with micros,
        // and we calculate total durations of each tempo in use.
        TempoEvent prevTempoEvent = null;
        for (TempoEvent tempoEvent : tempo.values()) {
            long tick = tempoEvent.tick;
            if (prevTempoEvent != null) {
                long elapsedMicros = MidiUtils.ticks2microsec(tick - prevTempoEvent.tick, prevTempoEvent.tempoMPQ, tickResolution);
                tempoLengths.put(prevTempoEvent.tempoMPQ, elapsedMicros + Util.valueOf(tempoLengths.get(prevTempoEvent.tempoMPQ), 0));
                tempoEvent.micros = prevTempoEvent.micros + elapsedMicros;
            }
            tempoByMicros.put(tempoEvent.micros, tempoEvent);//for fast micros lookup
            prevTempoEvent = tempoEvent;
        }

		// Account for the duration of the final tempo
		long elapsedMicros = MidiUtils.ticks2microsec(lastTick - prevTempoEvent.tick, prevTempoEvent.tempoMPQ, tickResolution);
		tempoLengths.put(prevTempoEvent.tempoMPQ, elapsedMicros + Util.valueOf(tempoLengths.get(prevTempoEvent.tempoMPQ), 0));

		// Convert the bend ranges into seminote integers.
		// We do this after the main iteration so that the
		// getPitchBendRange has been fully built.
		bendMap = new MapByChannelPort(0);
		for (Quad<Integer, Integer, Long, Double> raw : pitchWheelMap) {
			int semiToneBend = (int) Math.round(raw.fourth * getPitchBendRange(raw.first, raw.second, raw.third));
			bendMap.put(raw.first, raw.second, raw.third, semiToneBend);
		}

		Entry<Integer, Long> max = null;
		for (Entry<Integer, Long> entry : tempoLengths.entrySet()) {
			if (max == null || entry.getValue() > max.getValue())
				max = entry;
		}
		primaryTempoMPQ = (max == null) ? DEFAULT_TEMPO_MPQ : max.getKey();

		this.timeSignature = (foundTimeSignature == null) ? TimeSignature.FOUR_FOUR : foundTimeSignature;

		songLengthTicks = lastTick;
		
		if (!ignoreMidiText && standard != MidiStandard.ABC) log.info("Lyrics stats: "+midiText.getTextStats());
	}

	private int getRPN(int port, int channel, long tick) {
		int msb = rpnMSBMap.get(port, channel, tick);
		int lsb = rpnLSBMap.get(port, channel, tick);
		if (msb != 127 && lsb == 127) {// && rpnLSBMap.getEntries(channel,0L, tick).isEmpty()
			log.severe(fileName+": Channel "+channel+", RPN being utilized while LSB is default (NULL)! Using effective value of 0 LSB.");
			lsb = 0;
		}
		return (msb << 7) | lsb;
	}

	public boolean isXGDrumsTrack(int track) {
		if (track >= brandDrumBanks.length)
			return false;
		return brandDrumBanks[track] == DrumBankType.XG_DRUM;
	}

	public boolean isGSDrumsTrack(int track) {
		if (track >= brandDrumBanks.length)
			return false;
		return brandDrumBanks[track] == DrumBankType.GS_DRUM;
	}

	public boolean isDrumsTrack(int track) {
		if (track >= brandDrumBanks.length)
			return false;
		return brandDrumBanks[track] == DrumBankType.STANDARD_DRUM;
	}

	public boolean isGM2DrumsTrack(int track) {
		if (track >= brandDrumBanks.length)
			return false;
		return brandDrumBanks[track] == DrumBankType.GM2_DRUM;
	}

	public int getInstrument(int port, int channel, long tick) {
		return instruments.get(port, channel, tick);
	}

	/**
	 * 
	 * @param channel
	 * @param tick
	 * @param drumKit channel is set to drums/rhythmic.
	 * @return string with name of voice instrument
	 */
	public String getInstrumentExt(int port, int channel, long tick, boolean drumKit) {
		MidiStandard type = MidiStandard.GM;
		boolean rhythmChannel = channel == DRUM_CHANNEL;
		if (MidiStandard.XG == standard) {
			type = MidiStandard.XG;
			rhythmChannel = yamahaDrumChannels[channel];
		} else if (MidiStandard.GS == standard) {
			type = MidiStandard.GS;
			rhythmChannel = rolandDrumChannels[channel];
		} else if (MidiStandard.GM2 == standard) {
			type = MidiStandard.GM2;
		}

		long patchTick = mapPatch.getEntryTick(port, channel, tick);
		if (patchTick == NO_RESULT) {
			// No voice changes yet on this channel, return default.
			// TODO: Should we instead set LMB, LSB and patch to zero and let fromId handle it?
			if (drumKit) {
				return MidiInstrument.STANDARD_DRUM_KIT;
			} else {
				return MidiInstrument.PIANO.toString();
			}
		}

		byte msb = (byte) mapMSB.get(port, channel, patchTick);
		byte lsb = (byte) mapLSB.get(port, channel, patchTick);
		byte patch = (byte) mapPatch.get(port, channel, tick);

		// If the sequenceInfo classified this as a drum note, but no physical MSB event
		// was sent to change the bank, we must dynamically promote it to the Drum Bank
		if (drumKit && usingNewMidiLayout > 0) {
			if (type == MidiStandard.XG && msb != 127 && msb != 126) {
				msb = 127;// msb 127, lsb 0 is default kit
			} else if (type == MidiStandard.GM2 && msb != 120) {
				msb = 120;// msb 120, lsb 0 is default kit
				lsb = 0;
			}
			// Roland GS standard fallback handles kit mapping natively without MSB promotion
		}

		String value = ExtensionMidiInstrument.getInstance().fromId(type, msb,
				lsb, patch, drumKit, rhythmChannel);

		return value;
	}

	/**
	 * 
	 * @param channel
	 * @param tick
	 * @return volume from 0 to 127. 100 is default.
	 */
	public int getChannelVolume(int channel, long tick) {
		return channelVolume.get(channel, tick);
	}

	/**
	 * 
	 * @param channel
	 * @param tick
	 * @return expression from 0 to 127. 127 is default.
	 */
	public int getExpression(int channel, long tick) {
		return expression.get(channel, tick);
	}

	public double getPitchBendRange(int port, int channel, long tick) {
		return pitchBendRangeCoarse.get(port, channel, tick) + (pitchBendRangeFine.get(port, channel, tick) / 100.0d);
	}

    /**
     * Return the duration of the sequence in ticks.
     * NOTE: TrackInfo might have shortened the sequence,
     *       so this value might be significantly longer than
     *       the actual sequence we work with.
     *       If that's an issue, use SequenceInfo.getSequence().getTickLength()
     */
	public long getSongLengthTicks() {
		return songLengthTicks;
	}

	@Override
	public long tickToMicros(long tick) {
		if (divisionType != Sequence.PPQ)
			return (long) (TimingInfo.ONE_SECOND_MICROS * ((double) tick / (double) (divisionType * tickResolution)));

		TempoEvent te = getTempoEventForTick(tick);
		return te.micros + MidiUtils.ticks2microsec(tick - te.tick, te.tempoMPQ, tickResolution);
	}

	@Override
	public long microsToTick(long micros) {
		if (divisionType != Sequence.PPQ)
			return (long) (divisionType * tickResolution * micros / (double) TimingInfo.ONE_SECOND_MICROS);

		TempoEvent te = getTempoEventForMicros(micros);
		return te.tick + MidiUtils.microsec2ticks(micros - te.micros, te.tempoMPQ, tickResolution);
	}

	public int getTempoMPQ(long tick) {
		return getTempoEventForTick(tick).tempoMPQ;
	}

	public int getTempoBPM(long tick) {
		return (int) Math.round(MidiUtils.convertTempo(getTempoMPQ(tick)));
	}

	public int getPrimaryTempoMPQ() {
		return primaryTempoMPQ;
	}

	public int getPrimaryTempoBPM() {
		return (int) Math.round(MidiUtils.convertTempo(getPrimaryTempoMPQ()));
	}

	public int getTickResolution() {
		return tickResolution;
	}

	public TimeSignature getTimeSignature() {
		return timeSignature;
	}

	/*
	 * This is used by UI to draw bar lines. By section and tune editor to edit song.
	 * Not used by ABC exporter.
	 */
	public long getBarLengthTicks() {
		// tickResolution is in ticks per quarter note
		return 4L * tickResolution * timeSignature.numerator / timeSignature.denominator;
	}

	/**
	 * 1 based
	 * Input 1 and you get tick 0
	 * 
	 * TODO: fix it, so both use 0
	 * 
	 */
	@Override
	public long getBarToTick(int bar) {
		return getBarLengthTicks() * (bar - 1);
	}

	/**
	 * 0 based
	 */
	@Override
	public int tickToBarNumber(long tick) {
		return (int) (tick / getBarLengthTicks());
	}

    /**
     * Used in UI to show the user the play-head position and to paste its position into
     * section bar inputs.
     * Also used to print the bar positions in the abc file.
     */
	@Override
	public float tickToBarNumberFloat(long tick) {
        return (float)(Math.max(0L, tick) / (double)getBarLengthTicks());
	}

    /**
     * Used to convert the users section bar inputs to a tick.
     * Cannot be changed in any way, due to backwards compatibility.
     *
     * Notice that 4.5 might not be in the middle of a bar time-wise etc.
     * The reason is that tempo-changes inside the bar can change tick per time-unit.
     */
    public long barFloatToTick(float bar) {
        return (long)(bar * getBarLengthTicks());
    }

	public NavigableMap<Long, TempoEvent> getTempoEvents() {
		return tempo;
	}

    public boolean isTempoInHigherTracks() {
        return tempoInHigherTracks;
    }

    /**
	 * Tempo Handling
	 */
	public static class TempoEvent {
		public TempoEvent(int tempoMPQ, long startTick, long startMicros) {
			this.tempoMPQ = tempoMPQ;
			this.tick = startTick;
			this.micros = startMicros;
		}

		public static final TempoEvent DEFAULT_TEMPO = new TempoEvent(DEFAULT_TEMPO_MPQ, 0, 0);

		public final int tempoMPQ;
		public final long tick;
		public long micros;
		
		@Override
		public String toString() {
			return "BPM="+MidiUtils.convertTempo(tempoMPQ)+" MPQ="+tempoMPQ+"  tick="+tick+"  micros="+micros;
		}
	}

	public TempoEvent getATempoEvent(int tempoMPQ, long startTick, long startMicros) {
		return new TempoEvent(tempoMPQ, startTick, startMicros);
	}

    /**
     * Floor
     */
	public TempoEvent getTempoEventForTick(long tick) {
		Entry<Long, TempoEvent> entry = tempo.floorEntry(tick);
		if (entry != null)
			return entry.getValue();

		return TempoEvent.DEFAULT_TEMPO;
	}

	public TempoEvent getTempoEventForMicros(long micros) {
        Entry<Long, TempoEvent> entry = tempoByMicros.floorEntry(micros);
        if (entry != null)
            return entry.getValue();

		return TempoEvent.DEFAULT_TEMPO;
	}

	protected MapByChannelPort getBendMap() {
		return bendMap;
	}

	protected MapByChannelPort getPanMap() {
		return panMap;
	}

	public String getCopyright() {
		return copyright;
	}

	public void setCopyright(String copyright) {
		this.copyright = copyright;
	}
	
	/**
	 * Never call this when app is Abc Tools
	 */
	 public String getLyrics() {
		return midiText.getText();
	}

	/**
	 * Never call this when app is Abc Tools
	 */
	public List<LyricLine> getLyricLines() {
		return midiText.getStructuredLyrics();
	}
	
	public String getGenre() {
		return midiText.genre;
	}
	
	public String getComposer() {
		return midiText.artist;
	}

	/**
	 * Map by channel
	 */
	protected static class MapByChannel {
		private final NavigableMap<Long, Integer>[] map;
		private final int defaultValue;

		@SuppressWarnings("unchecked") //
		public MapByChannel(int defaultValue) {
			map = new NavigableMap[CHANNEL_COUNT_ABC];
			this.defaultValue = defaultValue;
		}

		public void put(int channel, long tick, Integer value) {
			if (map[channel] == null)
				map[channel] = new TreeMap<>();

			map[channel].put(tick, value);
		}

		public void putIfAbsent(int channel, long tick, Integer value) {
			if (map[channel] == null)
				map[channel] = new TreeMap<>();

			map[channel].putIfAbsent(tick, value);
		}

		public int get(int channel, long tick) {
			if (map[channel] == null)
				return defaultValue;

			Entry<Long, Integer> entry = map[channel].floorEntry(tick);
			if (entry == null) // No changes before this tick
				return defaultValue;

			return entry.getValue();
		}

		public Set<Entry<Long, Integer>> getEntries(int channel, long fromTick, long toTick) {
			if (map[channel] == null)
				return new HashSet<>();
			SortedMap<Long, Integer> subMap = map[channel].subMap(fromTick, toTick);
			return subMap.entrySet();
		}

		public long getEntryTick(int channel, long tick) {
			if (map[channel] == null)
				return NO_RESULT;

			Entry<Long, Integer> entry = map[channel].floorEntry(tick);
			if (entry == null) // No changes before this tick
				return NO_RESULT;

			return entry.getKey();
		}
	}

	/**
	 * Map by channel
	 */
	protected static class MapByChannelPort {
		private final NavigableMap<Long, Integer>[][] map;
		private final int defaultValue;

		@SuppressWarnings("unchecked") //
		public MapByChannelPort(int defaultValue) {
			map = new NavigableMap[PORT_COUNT][CHANNEL_COUNT_ABC];
			this.defaultValue = defaultValue;
		}

		public void put(int port, int channel, long tick, Integer value) {
			if (map[port][channel] == null)
				map[port][channel] = new TreeMap<>();

			map[port][channel].put(tick, value);
		}

		public void putIfAbsent(int port, int channel, long tick, Integer value) {
			if (map[port][channel] == null)
				map[port][channel] = new TreeMap<>();

			map[port][channel].putIfAbsent(tick, value);
		}

		public int get(int port, int channel, long tick) {
			if (map[port][channel] == null)
				return defaultValue;

			Entry<Long, Integer> entry = map[port][channel].floorEntry(tick);
			if (entry == null) // No changes before this tick
				return defaultValue;

			return entry.getValue();
		}

		/**
		 * Each entry is <tick, bend>
		 *
		 */
		public Set<Entry<Long, Integer>> getEntries(int port, int channel, long fromTick, long toTick) {
			if (map[port][channel] == null)
				return new HashSet<>();
			SortedMap<Long, Integer> subMap = map[port][channel].subMap(fromTick, toTick);
			return subMap.entrySet();
		}

		public long getEntryTick(int port, int channel, long tick) {
			if (map[port][channel] == null)
				return NO_RESULT;

			Entry<Long, Integer> entry = map[port][channel].floorEntry(tick);
			if (entry == null) // No changes before this tick
				return NO_RESULT;

			return entry.getKey();
		}
	}
	
	public enum DrumBankType {
		NOT_DRUM,
		XG_DRUM,
		GS_DRUM,
		STANDARD_DRUM,
		GM2_DRUM
	}
}
