package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.digero.common.util.Triple;
import com.digero.common.util.Util;
import com.digero.maestro.abc.TimingInfo;

public class SequenceDataCache implements MidiConstants, ITempoCache, IBarNumberCache {
	private static final Logger log = Logger.getLogger("import.midi");
	private final int tickResolution;
	private final float divisionType;
	private final int primaryTempoMPQ;
	private final TimeSignature timeSignature;
	private final NavigableMap<Long, TempoEvent> tempo = new TreeMap<>();

	private final long songLengthTicks;
	private static final int NO_RESULT = -250;

	private final MapByChannelPort instruments = new MapByChannelPort(DEFAULT_INSTRUMENT);
	private final MapByChannel channelVolume = new MapByChannel(DEFAULT_CHANNEL_VOLUME);
	private final MapByChannel expression = new MapByChannel(DEFAULT_EXPRESSION);
	private final MapByChannel pitchBendRangeCoarse = new MapByChannel(DEFAULT_PITCH_BEND_RANGE_SEMITONES);
	private final MapByChannel pitchBendRangeFine = new MapByChannel(DEFAULT_PITCH_BEND_RANGE_CENTS);
	private final MapByChannel bendMap;
	private final MapByChannel panMap;
	private final MapByChannel mapMSB = new MapByChannel(0);
	private final MapByChannel mapLSB = new MapByChannel(0);
	private final MapByChannel mapPatch = new MapByChannel(0);
	private final DrumBankType[] brandDrumBanks;
	private final MidiStandard standard;
	private final boolean[] rolandDrumChannels;
	private final boolean[] yamahaDrumChannels;
	public boolean hasPorts = false;
	private String copyright = "";
	private final boolean ignoreZeroChannelVolume;
	private final MidiText midiText;
    private boolean tempoInHigherTracks = false;

	public SequenceDataCache(Sequence song, MidiStandard standard, boolean[] rolandDrumChannels,
			List<TreeMap<Long, Boolean>> yamahaDrumSwitches, boolean[] yamahaDrumChannels,
			List<TreeMap<Long, Boolean>> mmaDrumSwitches, SortedMap<Integer, Integer> portMap, boolean onlyFirstTrackTempos, boolean ignoreZeroChannelVolume, boolean ignoreMidiText) {

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

		// Keep track of the active Registered Parameter Number for pitch bend range
		int[] rpn = new int[CHANNEL_COUNT_ABC];
		Arrays.fill(rpn, REGISTERED_PARAM_NONE);

		midiText = new MidiText(this);
		
		/*
		 * We need to be able to know which tracks have drum notes. We also need to know what instrument voices are used
		 * in each track, so we build maps of voice changes that TrackInfo later can use to build strings of instruments
		 * for each track.
		 * 
		 * This among other things we will find out by iterating through all MidiEvents.
		 * 
		 */
		List<Triple<Integer, Long, Double>> rawBendMap = new ArrayList<>();
		panMap = new MapByChannel(PAN_CENTER);
		Track[] tracks = song.getTracks();
		hasPorts = false;
		long lastTick = 0L;
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
					if (tick != 0L)
						break;
					MidiMessage msg = evt.getMessage();
					if (msg instanceof MetaMessage meta) {
                        if (meta.getType() == META_PORT_CHANGE) {
							byte[] portChange = meta.getData();
							if (portChange.length == 1) {
								// Support for (non-midi-standard) port assignments used by Cakewalk and
								// Musescore.
								// We only support this for GM, and only super well-formed (tick == 0).
								port = (int) portChange[0];
								log.fine("Port change on track "+iiTrack+", tick "+tick+", port "+MidiUtils.formatBytes(portChange));
								portMap.put(iiTrack, port);
								hasPorts = MidiStandard.GM == standard;
								break;
							}
						} else if (meta.getType() == META_PORT_NAME) {
							byte[] data = meta.getData();
							log.fine("Named port: "+(new String(data)));
						}
					}
				}
			}
			TimeSignature backupTimeSignature = null;
			for (int iTrack = 0; iTrack < tracks.length; iTrack++) {
				Track track = tracks[iTrack];

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
									&& yamahaDrumSwitches != null && yamahaDrumSwitches.get(ch).floorEntry(tick) != null
									&& yamahaDrumSwitches.get(ch).floorEntry(tick).getValue()) {
								brandDrumBanks[iTrack] = DrumBankType.XG_DRUM;// XG drums
							} else if (brandDrumBanks[iTrack] != DrumBankType.GM2_DRUM && MidiStandard.GM2 == standard
									&& mmaDrumSwitches != null && mmaDrumSwitches.get(ch).floorEntry(tick) != null
									&& mmaDrumSwitches.get(ch).floorEntry(tick).getValue()) {
								brandDrumBanks[iTrack] = DrumBankType.GM2_DRUM;// GM2 drums
							} else if (ch == DRUM_CHANNEL
									&& (MidiStandard.GM == standard || MidiStandard.ABC == standard)) {
								brandDrumBanks[iTrack] = DrumBankType.STANDARD_DRUM;// GM drums on channel #10
							}
						} else if (cmd == ShortMessage.PROGRAM_CHANGE) {
							if (((ch != DRUM_CHANNEL && rolandDrumChannels == null && yamahaDrumChannels == null)
									|| ((rolandDrumChannels == null || MidiStandard.GS != standard
											|| !rolandDrumChannels[ch])
											&& (yamahaDrumChannels == null || MidiStandard.XG != standard
													|| !yamahaDrumChannels[ch])))
									&& (MidiStandard.XG != standard || yamahaDrumSwitches == null
											|| yamahaDrumSwitches.get(ch).floorEntry(tick) == null
											|| !yamahaDrumSwitches.get(ch).floorEntry(tick).getValue())
									&& (MidiStandard.GM2 != standard || mmaDrumSwitches == null
											|| mmaDrumSwitches.get(ch).floorEntry(tick) == null
											|| !mmaDrumSwitches.get(ch).floorEntry(tick).getValue())) {
								instruments.put(portMap.get(iTrack), ch, tick, shortMsg.getData1());
								log.fine("Instrument change on track "+iTrack+", tick "+tick+", instrument "+MidiInstrument.fromId(shortMsg.getData1())+ ", port "+portMap.get(iTrack)+", channel "+ch);
							}
							mapPatch.put(ch, tick, shortMsg.getData1());
						} else if (cmd == ShortMessage.CONTROL_CHANGE) {
							switch (shortMsg.getData1()) {
							case CHANNEL_VOLUME_CONTROLLER_COARSE:
								if (shortMsg.getData2() != 0 || !ignoreZeroChannelVolume)
									channelVolume.put(ch, tick, shortMsg.getData2());
								break;
							case CHANNEL_EXPRESSION_CONTROLLER:
								expression.put(ch, tick, shortMsg.getData2());
								break;
							case REGISTERED_PARAMETER_NUMBER_MSB:
								rpn[ch] = (rpn[ch] & 0x7F) | ((shortMsg.getData2() & 0x7F) << 7);
								break;
							case REGISTERED_PARAMETER_NUMBER_LSB:
								rpn[ch] = (rpn[ch] & (0x7F << 7)) | (shortMsg.getData2() & 0x7F);
								break;
							case DATA_ENTRY_COARSE:
								if (rpn[ch] == REGISTERED_PARAM_PITCH_BEND_RANGE)
									pitchBendRangeCoarse.put(ch, tick, shortMsg.getData2());
								break;
							case DATA_ENTRY_FINE:
								if (rpn[ch] == REGISTERED_PARAM_PITCH_BEND_RANGE)
									pitchBendRangeFine.put(ch, tick, shortMsg.getData2());
								break;
							case DATA_BUTTON_INCREMENT:
								if (rpn[ch] == REGISTERED_PARAM_PITCH_BEND_RANGE) {
									pitchBendRangeFine.put(ch, tick, pitchBendRangeFine.get(ch, tick) + 1);
									log.fine("DATA_BUTTON_INCREMENT for pitch bend detected.");
								}
								break;
							case DATA_BUTTON_DECREMENT:
								if (rpn[ch] == REGISTERED_PARAM_PITCH_BEND_RANGE) {
									pitchBendRangeFine.put(ch, tick, pitchBendRangeFine.get(ch, tick) - 1);
									log.fine("DATA_BUTTON_DECREMENT for pitch bend detected.");
								}
								break;
							case PAN_CONTROL:
								panMap.put(ch, tick, shortMsg.getData2());
								break;
							case BANK_SELECT_MSB:
								if (ch != DRUM_CHANNEL || MidiStandard.XG != standard || shortMsg.getData2() == 126
										|| shortMsg.getData2() == 127) {
									// Due to XG drum part protect mode being ON, drum channel 9 only can switch
									// between MSB 126 & 127.
									mapMSB.put(ch, tick, shortMsg.getData2());
								} else if (ch == DRUM_CHANNEL && MidiStandard.XG == standard && shortMsg.getData2() != 126
										&& shortMsg.getData2() != 127) {
									log.finer("XG Drum Part Protect Mode prevented bank select MSB.");
								}
								// if(ch==DRUM_CHANNEL) System.err.println("Bank select MSB "+m.getData2()+" "+tick);
								break;
							case BANK_SELECT_LSB:
								mapLSB.put(ch, tick, shortMsg.getData2());
								// if(ch==DRUM_CHANNEL) System.err.println("Bank select LSB "+m.getData2()+" "+tick);
								break;
							}
						} else if (cmd == ShortMessage.PITCH_BEND) {
							double pct = 2.0d * (((shortMsg.getData1() | (shortMsg.getData2() << 7)) / (double) (1 << 14)) - 0.5d);
							rawBendMap.add(new Triple<Integer, Long, Double>(ch, tick, pct));
							// Notice we put in the bend even if its a repeat of same bend.
							// Reason is that later on another track there might get put some
							// bends in between them.
						}
					} else if (msg instanceof SysexMessage sysex) {
                        byte[] message = sysex.getMessage();
						if (message.length == 9 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x43
								&& (message[4] & 0xFF) == 0x08 && (message[8] & 0xFF) == 0xF7) {
							String bank = message[6] == 1 ? "MSB"
									: (message[6] == 2 ? "LSB" : (message[6] == 3 ? "Patch" : ""));
							if (MidiStandard.XG == standard && !"".equals(bank) && message[5] < 16 && message[5] >= 0
									&& message[7] < 128 && message[7] >= 0) {
								switch (bank) {
								case "MSB":
									// XG Drum Part Protect Mode does not apply to sysex bank changes.
									mapMSB.put((int) message[5], tick, (int) message[7]);
									break;
								case "Patch":
									mapPatch.put((int) message[5], tick, (int) message[7]);
									break;
								case "LSB":
									mapLSB.put((int) message[5], tick, (int) message[7]);
									break;
								}
							}
						} else if (!ignoreMidiText) {
							midiText.collectSysex(tick, message, iTrack);
						}
					} else if (divisionType == Sequence.PPQ && MidiUtils.isMetaTempo(msg)) {

                        int tempoRaw = MidiUtils.getTempoMPQ(msg);
                        if (tempoRaw != 0) {
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
                            log.warning("MIDI has tempo message of zero MPQ! Ignoring it..");
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
			for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
				mapPatch.put(i, -1, 0);
				mapLSB.put(i, -1, 0);
			}
			if (MidiStandard.XG == standard && yamahaDrumChannels != null) {
				// Bank 127 is implicit the default on drum channels in XG.
				for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
					if (yamahaDrumChannels[i])
						mapMSB.put(i, -1, 127);
					else
						mapMSB.put(i, -1, 0);
				}
			} else if (MidiStandard.GM2 == standard) {
				// Bank 120 is implicit the default on drum channel in GM2.
				// Bank 121 is implicit the default on all other channels in GM2.
				
				final int GM2_MSB_DEFAULT_CHROMATIC = 121;
				final int GM2_MSB_DEFAULT_RHYTHM = 120;
				
				mapMSB.put(0, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(1, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(2, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(3, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(4, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(5, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(6, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(7, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(8, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(DRUM_CHANNEL, -1, GM2_MSB_DEFAULT_RHYTHM);
				mapMSB.put(10, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(11, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(12, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(13, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(14, -1, GM2_MSB_DEFAULT_CHROMATIC);
				mapMSB.put(15, -1, GM2_MSB_DEFAULT_CHROMATIC);
			} else {
				for (int i = 0; i < CHANNEL_COUNT_ABC; i++) {
					mapMSB.put(i, -1, 0);
				}
			}
		} else {
			// If this sequence is for preview then we only need to find lastTick
            for (Track track : tracks) {
                for (int j = 0, sz = track.size(); j < sz; j++) {
                    MidiEvent evt = track.get(j);
                    long tick = evt.getTick();
                    if (tick > lastTick)
                        lastTick = tick;
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
            prevTempoEvent = tempoEvent;
        }

		// Account for the duration of the final tempo
		long elapsedMicros = MidiUtils.ticks2microsec(lastTick - prevTempoEvent.tick, prevTempoEvent.tempoMPQ, tickResolution);
		tempoLengths.put(prevTempoEvent.tempoMPQ, elapsedMicros + Util.valueOf(tempoLengths.get(prevTempoEvent.tempoMPQ), 0));

		// Convert the bend ranges into seminote integers.
		// We do this after the main iteration so that the
		// getPitchBendRange has been fully built.
		bendMap = new MapByChannel(0);
		for (Triple<Integer, Long, Double> raw : rawBendMap) {
			int semiToneBend = (int) Math.round(raw.third * getPitchBendRange(raw.first, raw.second));
			bendMap.put(raw.first, raw.second, semiToneBend);
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
	public String getInstrumentExt(int channel, long tick, boolean drumKit) {
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

		long patchTick = mapPatch.getEntryTick(channel, tick);
		if (patchTick == NO_RESULT) {
			// No voice changes yet on this channel, return default.
			// TODO: Should we instead set LMB, LSB and patch to zero and let fromId handle it?
			if (drumKit) {
				return MidiInstrument.STANDARD_DRUM_KIT;
			} else {
				return MidiInstrument.PIANO.toString();
			}
		}

		String value = ExtensionMidiInstrument.getInstance().fromId(type, (byte) mapMSB.get(channel, patchTick),
				(byte) mapLSB.get(channel, patchTick), (byte) mapPatch.get(channel, tick), drumKit, rhythmChannel);

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

	public double getPitchBendRange(int channel, long tick) {
		return pitchBendRangeCoarse.get(channel, tick) + (pitchBendRangeFine.get(channel, tick) / 100.0);
	}

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
	
	@Override
	public float tickToBarNumberFloat(long tick) {
		int barNumber = (tick < 0) ? 0 : (tickToBarNumber(tick));
		return Util.map(tick, getBarToTick(barNumber+1), getBarToTick(barNumber+2), barNumber, barNumber+1);
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
		TempoEvent prev = TempoEvent.DEFAULT_TEMPO;
		for (Entry<Long, TempoEvent> entry : tempo.entrySet()) {
			if (entry.getValue().micros == micros) return entry.getValue();
			if (entry.getValue().micros > micros) return prev;
			prev = entry.getValue();
		}
		return prev;
	}

	protected MapByChannel getBendMap() {
		return bendMap;
	}

	protected MapByChannel getPanMap() {
		return panMap;
	}

	public String getCopyright() {
		return copyright;
	}

	public void setCopyright(String copyright) {
		this.copyright = copyright;
	}
	
	public String getLyrics() {
		return midiText.getText();
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
	private static class MapByChannelPort {
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

		public int get(int port, int channel, long tick) {
			if (map[port][channel] == null)
				return defaultValue;

			Entry<Long, Integer> entry = map[port][channel].floorEntry(tick);
			if (entry == null) // No changes before this tick
				return defaultValue;

			return entry.getValue();
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
