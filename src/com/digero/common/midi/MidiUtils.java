package com.digero.common.midi;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.logging.Logger;

import javax.sound.midi.*;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.SequencerWrapper.TempoCacheSlow;
import com.digero.common.util.Pair;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceInfo;

/**
 * A minimal copy of all used MidiUtils features.
 */
public class MidiUtils {
	private static final Logger log = Logger.getLogger("import.midi");
	
	public static final int DEFAULT_TEMPO_MPQ = 500000; // 120bpm
	public static final int META_END_OF_TRACK_TYPE = 0x2F;
	public static final int META_TEMPO_TYPE = 0x51;

	/**
	 * Given a microsecond time, convert to tick.
	 */
    public static long microsecond2tick(Sequence seq, long micros, TempoCacheSlow cache) {
        if (seq.getDivisionType() != Sequence.PPQ) {
            double dTick = (((double) micros) * ((double) seq.getDivisionType()) * ((double) seq.getResolution()))
                    / ((double) 1000000);
            return (long) dTick;
        }

        if (cache == null) {
            cache = new TempoCacheSlow(seq);
        }

        int index = Arrays.binarySearch(cache.micros, micros);
        if (index < 0) {
            index = -(index + 1) - 1;
        }

        if (index < 0) index = 0;
        if (index >= cache.micros.length) index = cache.micros.length - 1;

        long microsFromBase = micros - cache.micros[index];
        return cache.ticks[index] + microsec2ticks(microsFromBase, cache.tempos[index], seq.getResolution());
    }

	/**
	 * Given a tick, convert to microsecond
	 * 
	 * @param cache tempo info and current tempo
	 */
    public static long tick2microsecond(Sequence seq, long tick, TempoCacheSlow cache) {
        if (seq.getDivisionType() != Sequence.PPQ) {
            double seconds = ((double) tick / (double) (seq.getDivisionType() * seq.getResolution()));
            return (long) (1000000 * seconds);
        }

        if (cache == null) {
            cache = new TempoCacheSlow(seq);
        }

        int index = Arrays.binarySearch(cache.ticks, tick);
        if (index < 0) {
            index = -(index + 1) - 1;
        }

        if (index < 0) index = 0;
        if (index >= cache.ticks.length) index = cache.ticks.length - 1;

        long ticksFromBase = tick - cache.ticks[index];
        return cache.micros[index] + ticks2microsec(ticksFromBase, cache.tempos[index], seq.getResolution());
    }

	/**
	 * convert tick to microsecond with given tempo. Does not take tempo changes into account. Does not work for SMPTE
	 * timing!
	 */
	public static long ticks2microsec(long tick, double tempoMPQ, int resolution) {
		return (long) (tick * tempoMPQ / resolution);
	}
	
	public static long ticks2microsec(long tick, int tempoMPQ, int resolution) {
		return tick * tempoMPQ / resolution;
	}

	/**
	 * convert tempo to microsecond with given tempo Does not take tempo changes into account. Does not work for SMPTE
	 * timing!
	 */
	public static long microsec2ticks(long us, double tempoMPQ, int resolution) {
		// do not round to nearest tick
		return (long) ((((double) resolution) * us) / tempoMPQ);
	}
	
	public static long microsec2ticks(long us, int tempoMPQ, int resolution) {
		// do not round to nearest tick
		return us * resolution / tempoMPQ;
	}

    public static long microsec2ticksCeil(long us, int tempoMPQ, int resolution) {
        // do not round to nearest tick
        long addUp = us * resolution % tempoMPQ == 0?0L:1L;
        return us * resolution / tempoMPQ + addUp;
    }

	/**
	 * converts<br>
	 * 1 - MPQ-Tempo to BPM tempo<br>
	 * 2 - BPM tempo to MPQ tempo<br>
	 */
	public static double convertTempo(double tempo) {
		if (tempo <= 0) {
			tempo = 1;
		}
		return ((double) 60000000L) / tempo;
	}

	/** return if the given message is a meta tempo message */
    public static boolean isMetaTempo(MidiMessage midiMsg) {
        // avoid byte[] allocation via getMessage()
        if (midiMsg instanceof MetaMessage meta) {
            // Check type (0x51) and total length (6 bytes -> 3 bytes data)
            return meta.getType() == META_TEMPO_TYPE && meta.getLength() == 6;
        }
        return false;
    }

	/**
	 * parses this message for a META tempo message and returns the tempo in MPQ, or -1 if this isn't a tempo message
	 */
	public static int getTempoMPQ(MidiMessage midiMsg) {
        if (!isMetaTempo(midiMsg)) {
            return -1;
        }
        MetaMessage meta = (MetaMessage) midiMsg;
		byte[] data = meta.getData();//getData() allocates less than getMessage()

        return (data[2] & 0xFF) | ((data[1] & 0xFF) << 8) | ((data[0] & 0xFF) << 16);
	}

    /**
     * Remove a pan event from a track, and replace it with a newly calculated pan event.
     * Returns the new event.
     */
    public static MidiEvent replacePanningEvent (Track track, LotroInstrument instrument, String partName, MidiEvent prevPanEvent, int panModifier, Integer userPan, PanGenerator panner, int partNumber) {
        ShortMessage panMsg = (ShortMessage) prevPanEvent.getMessage();
        int panAmount = panner.get(instrument, panModifier, userPan, partNumber);
        MidiEvent panEvent = MidiFactory.createPanEvent(panAmount, panMsg.getChannel());
        track.remove(prevPanEvent);
        track.add(panEvent);
        return panEvent;
    }

	/** return true if the passed message is Meta End Of Track */
	public static boolean isMetaEndOfTrack(MidiMessage midiMsg) {
        if (midiMsg instanceof MetaMessage meta) {
            // Type 0x2F, Total Length 3 (Status + Type + Len(0)) => Data Length 0
            return meta.getType() == META_END_OF_TRACK_TYPE && meta.getLength() == 3;
        }
        return false;
    }
	
	public static String decodeMidiText(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        log.finer("Decoding bytes: "+formatBytes(data));
        Pair<String, Charset> result = CharsetDetectAndDecode.decodeMidiData(data, false);
        log.fine("Decoder detected "+result.second.name()+", result: "+result.first);
        return result.first;
	}
	
	public static Pair<String, Charset> decodeMidiText(byte[] data, boolean preferWestern) {
        if (data == null || data.length == 0) {
            return new Pair<>("", null);
        }
        Pair<String, Charset> result = CharsetDetectAndDecode.decodeMidiData(data, preferWestern);
        if (preferWestern && CharsetDetectAndDecode.getScript(result.second.name()) != CharsetDetectAndDecode.Script.WESTERN) {
        	log.warning("Decoding lyrics bytes: "+formatBytesHexOnly(data));
        	log.warning("Decoder detected "+result.second.name()+" "
        			+CharsetDetectAndDecode.getScript(result.second.name())+", had preferred: "+CharsetDetectAndDecode.Script.WESTERN);
        } else {
        	log.fine("Decoding lyrics bytes: "+formatBytesHexOnly(data));
        	log.info("Decoder detected "+result.second.name()+", result.length: "+result.first.length()+"  "
    			+CharsetDetectAndDecode.getScript(result.second.name()));
        }
    	
        return result;
	}
    
    @SuppressWarnings("unused")
	public static String formatBytes(byte[] portChange) {
		StringBuilder str = new StringBuilder();
		for (byte by : portChange) {
			str.append((int) by).append(" ");
		}
		StringBuilder sb = new StringBuilder();
		for (byte b : portChange) {
			sb.append(String.format("%02X ", b));
		}
		str.append("[ ").append(sb).append("]");
		return str.toString();
	}
    
	public static String formatBytesHexOnly(byte[] portChange) {
		StringBuilder sb = new StringBuilder();
		for (byte b : portChange) {
			sb.append(String.format("%02X ", b));
		}
		return sb.toString();
	}
    
    @SuppressWarnings("unused")
    public static String formatCodePoints(String text) {
        StringBuilder dec = new StringBuilder();
        StringBuilder hex = new StringBuilder();

        text.codePoints().forEach(cp -> {
            dec.append(cp).append(' ');
            hex.append(String.format("%04X ", cp));
        });

        return dec.append("[ ").append(hex).append(']').toString();
    }
    
    /**
     * Convenient method for outputting basic info about a midi event for logging.
     * 
     * @param evt the midi event
     * @return short string outlining what kind of midi event it is
     */
    public static String midiEventToShortString(MidiEvent evt) {
    	String str = "";
    	MidiMessage m = evt.getMessage();
        str = midiMessageToShortString(m);
        str += ", Tick="+evt.getTick();
    	return str;
    }

    @SuppressWarnings("HardCodedStringLiteral")
    public static String midiMessageToString(MidiMessage m) {
        String str = "";
        if (m instanceof ShortMessage shorty) {
            int command = shorty.getCommand();
            switch (command) {
                case ShortMessage.NOTE_ON -> {
                    int pitch = shorty.getData1();
                    int velocity = shorty.getData2();
                    if (velocity > 0) {
                        str += "Note ON, Pitch=" + pitch + ", Velocity=" + velocity;
                    } else {
                        str += "Note OFF (via Velocity 0), Pitch=" + pitch;
                    }
                }
                case ShortMessage.NOTE_OFF -> {
                    str += "Note OFF, Pitch=" + shorty.getData1();
                }
                case ShortMessage.CHANNEL_PRESSURE -> str += "Aftertouch";
                case ShortMessage.POLY_PRESSURE -> str += "Aftertouch (poly)";
                case ShortMessage.CONTROL_CHANGE -> {
                    str += "Control Change";
                    int controller = shorty.getData1();
                    String cntr = switch(controller) {
                        // --- High-Resolution Continuous Controllers (MSB) ---
                        case MidiConstants.BANK_SELECT_MSB  -> "Bank Select (MSB)";
                        case 1  -> "Modulation Wheel (MSB)";
                        case 2  -> "Breath Controller (MSB)";
                        case 4  -> "Foot Controller (MSB)";
                        case 5  -> "Portamento Time (MSB)";
                        case MidiConstants.DATA_ENTRY_COARSE  -> "Data Entry (MSB)";
                        case MidiConstants.CHANNEL_VOLUME_CONTROLLER_COARSE  -> "Channel Volume (MSB)";
                        case 8  -> "Balance (MSB)";
                        case MidiConstants.PAN_CONTROL -> "Pan (MSB)";
                        case MidiConstants.CHANNEL_EXPRESSION_CONTROLLER -> "Expression Controller (MSB)";
                        case 12 -> "Effect Control 1 (MSB)";
                        case 13 -> "Effect Control 2 (MSB)";

                        // --- High-Resolution Continuous Controllers (LSB) ---
                        // (These are exactly 32 higher than their MSB counterparts)
                        case MidiConstants.BANK_SELECT_LSB -> "Bank Select (LSB)";
                        case 33 -> "Modulation Wheel (LSB)";
                        case 34 -> "Breath Controller (LSB)";
                        case 36 -> "Foot Controller (LSB)";
                        case 37 -> "Portamento Time (LSB)";
                        case MidiConstants.DATA_ENTRY_FINE -> "Data Entry (LSB)";
                        case MidiConstants.CHANNEL_VOLUME_CONTROLLER_FINE -> "Channel Volume (LSB)";
                        case 40 -> "Balance (LSB)";
                        case 42 -> "Pan (LSB)";
                        case 43 -> "Expression Controller (LSB)";

                        // --- Switches and Pedals ---
                        case 64 -> "Sustain Pedal / Damper";
                        case 65 -> "Portamento On/Off";
                        case 66 -> "Sostenuto Pedal";
                        case 67 -> "Soft Pedal";
                        case 68 -> "Legato Footswitch";
                        case 69 -> "Hold 2";

                        // --- Sound Controllers (Timbre/Envelope) ---
                        case 70 -> "Sound Controller 1 (Sound Variation)";
                        case 71 -> "Sound Controller 2 (Resonance / Timbre)";
                        case 72 -> "Sound Controller 3 (Release Time)";
                        case 73 -> "Sound Controller 4 (Attack Time)";
                        case 74 -> "Sound Controller 5 (Brightness / Cutoff)";

                        // --- Portamento Control ---
                        case 84 -> "Portamento Control";

                        // --- Effects Depths (Sends) ---
                        case 91 -> "Effects 1 Depth (Reverb Send)";
                        case 92 -> "Effects 2 Depth (Tremolo Depth)";
                        case 93 -> "Effects 3 Depth (Chorus Send)";
                        case 94 -> "Effects 4 Depth (Celeste/Detune)";
                        case 95 -> "Effects 5 Depth (Phaser Depth)";

                        // --- Parameter Values ---
                        case MidiConstants.DATA_BUTTON_INCREMENT  -> "Data Increment (+1)";
                        case MidiConstants.DATA_BUTTON_DECREMENT  -> "Data Decrement (-1)";
                        case 98  -> "Non-Registered Parameter Number LSB (NRPN)";
                        case 99  -> "Non-Registered Parameter Number MSB (NRPN)";
                        case MidiConstants.REGISTERED_PARAMETER_NUMBER_LSB -> "Registered Parameter Number LSB (RPN)";
                        case MidiConstants.REGISTERED_PARAMETER_NUMBER_MSB -> "Registered Parameter Number MSB (RPN)";

                        // --- Channel Mode Messages (System/Behavioral) ---
                        case 120 -> "All Sound Off";
                        case MidiConstants.RESET_ALL_CONTROLLERS -> "Reset All Controllers";
                        case 122 -> "Local Control On/Off";
                        case MidiConstants.ALL_NOTES_OFF -> "All Notes Off";
                        case 124 -> "Omni Mode Off";
                        case 125 -> "Omni Mode On";
                        case 126 -> "Mono Mode On (Poly Off)";
                        case 127 -> "Poly Mode On (Mono Off)";

                        // --- Fallback ---
                        default -> "Unknown (" + controller + ")";
                    };
                    int value = shorty.getData2();
                    str += ". Controller="+cntr+", Value="+value;
                }
                case ShortMessage.PITCH_BEND -> {
                    double pct = 2.0d * (((shorty.getData1() | (shorty.getData2() << 7)) / (double) (1 << 14)) - 0.5d);
                    str += "Pitch Bend (" + String.format("%.2f%%", pct * 100) + ")";
                }
                case ShortMessage.PROGRAM_CHANGE -> {
                    int program = shorty.getData1();
                    str += "Program Change: " + program + " (" + MidiInstrument.fromId(program) + ")";
                }
                default -> str+="";
            };
            str += ", Channel="+shorty.getChannel();
        } else if (m instanceof SysexMessage sysex) {
            str += "Sysex";
            if (sysex.getMessage()[1] == MidiConstants.SYSEX_UNIVERSAL_REALTIME) {
                str += ", Realtime";
            } else if (sysex.getMessage()[1] == MidiConstants.SYSEX_UNIVERSAL_NON_REALTIME) {
                str += ", Non-Realtime";
            }
            byte[] sysexMsg = sysex.getMessage();
            if (isSysexLyrics(sysexMsg)) {
                str += ", Lyrics";
            }
            if (isResetGM(sysexMsg)) {
                str += ", GM Reset";
            } else if (isResetGS(sysexMsg)) {
                str += ", GS Reset";
                byte[] data = sysex.getData();
                byte generation = data[data.length -3];
                str += ". Generation="+switch(generation) {
                    case 0x00 -> "SC-55 (Standard GS Reset)";
                    case 0x01 -> "SC-88";
                    case 0x02 -> "SC-88Pro";
                    case 0x03 -> "SC-8820";
                    case 0x04 -> "SC-8850";
                    case 0x05 -> "SD-90 / SD-80";
                    default -> "Unknown (0x"+String.format("%02X", generation)+")";
                };
            } else if (isResetXG(sysexMsg)) {
                str += ", XG Reset";
            } else if (isResetGM2(sysexMsg)) {
                str += ", GM2 Reset";
            } else {
                if (sysexMsg.length == 9 && (sysexMsg[0] & 0xFF) == 0xF0 && (sysexMsg[1] & 0xFF) == 0x43
                        && (sysexMsg[3] & 0xFF) == 0x4C && (sysexMsg[4] & 0xFF) == 0x08
                        && (sysexMsg[6] & 0xFF) == 0x07 && (sysexMsg[8] & 0xFF) == 0xF7) {
                    String type = "Normal";
                    if (sysexMsg[5] < 16) {
                        // From Tyros 1 data doc: part10=0x02, other parts=0x00. Korg EX-20 say this is channel.
                        // TODO: Drum Setup Reset sysex.
                        // Sure looks like Korg has it correct, at least for pre Tyros XG standard.
                        if (sysexMsg[7] == 0) {
                            type = "Normal";
                        } else if (sysexMsg[7] == 1) {
                            type = "Drums";
                        } else if (sysexMsg[7] > 1 && sysexMsg[7] <= 5) {
                            type = "Drums Setup " + (sysexMsg[7] - 1);
                        } else {
                            type = "Invalid setup: " + sysexMsg[7];
                        }
                        str += ", XG setting channel #" + sysexMsg[5] + " to " + type;
                    } else {
                        str += ", XG drum setup fail, " + formatBytesHexOnly(sysex.getMessage());
                    }
                } else if (sysexMsg.length == 9 && (sysexMsg[0] & 0xFF) == 0xF0 && (sysexMsg[1] & 0xFF) == 0x43
                        && (sysexMsg[3] & 0xFF) == 0x4C && (sysexMsg[4] & 0xFF) == 0x08 && (sysexMsg[8] & 0xFF) == 0xF7) {
                    String bank = sysexMsg[6] == 1 ? "MSB"
                            : (sysexMsg[6] == 2 ? "LSB" : (sysexMsg[6] == 3 ? "Patch" : ""));
                    if (!"".equals(bank) && sysexMsg[5] < 16 && sysexMsg[5] >= 0
                            && sysexMsg[7] < 128 && sysexMsg[7] >= 0) {
                        str += ", XG Select "+bank+" "+sysexMsg[7]+ " on Channel " + sysexMsg[5];
                    } else if (sysexMsg[5] < 16 && sysexMsg[5] >= 0) {
                        int paramAddress = sysexMsg[6] & 0xFF;
                        String paramName = switch(paramAddress) {
                            // --- Basic Setup ---
                            case 0x01 -> "Bank Select MSB";
                            case 0x02 -> "Bank Select LSB";
                            case 0x03 -> "Program Number";
                            case 0x04 -> "Receive Channel";
                            case 0x05 -> "Poly/Mono Mode";
                            case 0x07 -> "Mode (Normal/Drum)";

                            // --- Pitch & Mixer ---
                            case 0x08 -> "Note Shift (Transpose)";
                            case 0x09 -> "Detune";
                            case 0x0A -> "Volume";
                            case 0x0B -> "Velocity Sense Depth";
                            case 0x0C -> "Velocity Sense Offset";
                            case 0x0D -> "Pan";

                            // --- Effects Sends ---
                            case 0x11 -> "Dry Level";
                            case 0x12 -> "Chorus Send";
                            case 0x13 -> "Reverb Send";
                            case 0x14 -> "Variation Effect Send";

                            // --- Vibrato (LFO) ---
                            case 0x15 -> "Vibrato Rate";
                            case 0x16 -> "Vibrato Depth";
                            case 0x17 -> "Vibrato Delay";

                            // --- Filter ---
                            case 0x18 -> "Filter Cutoff Frequency";
                            case 0x19 -> "Filter Resonance";

                            // --- Envelope Generator (EG) ---
                            case 0x1A -> "EG Attack Time";
                            case 0x1B -> "EG Decay Time";
                            case 0x1C -> "EG Release Time";

                            // --- Portamento & Bend ---
                            case 0x21 -> "Portamento Switch";
                            case 0x22 -> "Portamento Time";
                            case 0x23 -> "Pitch Bend Range";
                            default -> String.format("Unknown Parameter [0x%02X]", paramAddress);
                        };
                        str += String.format(", XG Channel %d %s set to %d",
                                (sysexMsg[5]), paramName, sysexMsg[7]);
                    } else {
                        str += ", XG unknown param, " + formatBytesHexOnly(sysex.getMessage());
                    }
                } else if (sysexMsg.length == 11 && (sysexMsg[0] & 0xFF) == 0xF0 && (sysexMsg[1] & 0xFF) == 0x41
                        && (sysexMsg[3] & 0xFF) == 0x42 && (sysexMsg[4] & 0xFF) == 0x12 && (sysexMsg[5] & 0xFF) == 0x40
                        && (sysexMsg[7] & 0xFF) == 0x15 && (sysexMsg[10] & 0xFF) == 0xF7) {
                    boolean toDrums = sysexMsg[8] == 1 || sysexMsg[8] == 2;
                    int channel = -1;
                    if (sysexMsg[6] == 16) {
                        channel = MidiConstants.DRUM_CHANNEL;
                    } else if (sysexMsg[6] > 25 && sysexMsg[6] < 32) {
                        channel = sysexMsg[6] - 16;
                    } else if (sysexMsg[6] > 16 && sysexMsg[6] < 26) {
                        channel = sysexMsg[6] - 17;
                    }
                    if (channel != -1 && channel < 16) {
                        if (toDrums) {
                            str += ", GS sets channel "+(channel)+" to drums.";
                        } else {
                            str += ", GS unsets channel "+(channel)+" to drums.";
                        }
                    } else {
                        str += ", GS failed to set a channel to drums. sysexMsg[6]=" + String.format("0x%02X",sysexMsg[6]);
                    }
                } else if (sysexMsg.length == 11 && (sysexMsg[0] & 0xFF) == 0xF0 && (sysexMsg[1] & 0xFF) == 0x41
                        && (sysexMsg[3] & 0xFF) == 0x42 && (sysexMsg[4] & 0xFF) == 0x12
                        && (sysexMsg[5] & 0xFF) == 0x40 && (sysexMsg[6] & 0xFF) == 0x00 && (sysexMsg[7] & 0xFF) == 0x04
                        && (sysexMsg[10] & 0xFF) == 0xF7) {

                    int masterVol = sysexMsg[8] & 0xFF;
                    str += ", GS Device Master Volume set to " + masterVol;
                } else if (sysexMsg.length == 9 && (sysexMsg[0] & 0xFF) == 0xF0 && (sysexMsg[1] & 0xFF) == 0x43
                        && (sysexMsg[3] & 0xFF) == 0x4C
                        && (sysexMsg[4] & 0xFF) == 0x00 && (sysexMsg[5] & 0xFF) == 0x00 && (sysexMsg[6] & 0xFF) == 0x04
                        && (sysexMsg[8] & 0xFF) == 0xF7) {

                    int masterVol = sysexMsg[7] & 0xFF;
                    str += ", XG Device Master Volume set to " + masterVol;
                } else if (sysexMsg.length == 9 && (sysexMsg[0] & 0xFF) == 0xF0 && (sysexMsg[1] & 0xFF) == 0x43
                        && (sysexMsg[3] & 0xFF) == 0x4C
                        && (sysexMsg[4] & 0xFF) == 0x00 && (sysexMsg[5] & 0xFF) == 0x00 && (sysexMsg[6] & 0xFF) == 0x07
                        && (sysexMsg[8] & 0xFF) == 0xF7) {

                    str += ", XG Drum Part Protect mode " + (sysexMsg[7] == 0 ? "OFF" : "ON");
                } else if (sysexMsg.length == 9 && (sysexMsg[0] & 0xFF) == 0xF0 && (sysexMsg[1] & 0xFF) == 0x43
                        && (sysexMsg[3] & 0xFF) == 0x4C && (sysexMsg[4] & 0xF0) == 0x30 && (sysexMsg[8] & 0xFF) == 0xF7) {

                    int drumSetup = (sysexMsg[4] & 0x0F) + 1; // 30 is Setup 1, 31 is Setup 2
                    int noteNum = sysexMsg[5] & 0xFF;
                    int paramAddress = sysexMsg[6] & 0xFF;
                    int value = sysexMsg[7] & 0xFF;

                    String drumName = "Note " + noteNum;

                    String paramName = switch(paramAddress) {
                        case 0x00 -> "Pitch Coarse";
                        case 0x01 -> "Pitch Fine";
                        case 0x02 -> "Level";
                        case 0x04 -> "Pan";
                        case 0x05 -> "Reverb Send";
                        case 0x06 -> "Chorus Send";
                        case 0x07 -> "Variation Send";
                        case 0x0B -> "Filter Cutoff";
                        case 0x0C -> "Filter Resonance";
                        case 0x0D -> "EG Attack";
                        case 0x0E -> "EG Decay 1";
                        case 0x0F -> "EG Decay 2";
                        case 0x20 -> "EQ Bass Gain";
                        case 0x21 -> "EQ Treble Gain";
                        case 0x24 -> "EQ Bass Frequency";
                        case 0x25 -> "EQ Treble Frequency";
                        default -> String.format("Unknown Param [0x%02X]", paramAddress);
                    };

                    str += String.format(", XG Drum Setup %d (%s) %s set to %d", drumSetup, drumName, paramName, value);
                } else if (sysexMsg.length >= 9 && (sysexMsg[0] & 0xFF) == 0xF0 && (sysexMsg[1] & 0xFF) == 0x43
                        && (sysexMsg[3] & 0xFF) == 0x4C) {
                    // We know it's XG
                    str += String.format(", XG SysEx [Block 0x%02X]: %s",
                            sysexMsg[4], MidiUtils.formatBytesHexOnly(sysexMsg));
                } else {
                    // take note of difference of midi (7 bit unsigned) vs. java (8 bit signed):
                    str += ", " + formatBytesHexOnly(sysex.getMessage());
                }
            }
        } else if (m instanceof MetaMessage meta) {
            str += "Meta";
            if (isMetaTempo(m)) {
                str += ", Tempo";
                int tempoRaw = MidiUtils.getTempoMPQ(m);
                if (tempoRaw > 0) {
                    double bpm = convertTempo(tempoRaw);
                    if (bpm == Math.round(bpm)) {
                        str += ". BPM=" + ((int)bpm);
                    } else {
                        str += ". BPM=" + bpm;
                    }
                }
            } else if (isMetaEndOfTrack(m)) {
                str += ", EndOfTrack";
            } else if (meta.getType() == MidiConstants.META_TIME_SIGNATURE) {
                str += ", Time Signature";
                try {
                    TimeSignature sig = new TimeSignature(meta);
                    str += ". Signature="+sig;
                } catch (InvalidMidiDataException e) {
                    str += ". Invalid signature";
                }
            } else if (meta.getType() == MidiConstants.META_PORT_CHANGE) {
                str += ", Port change. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_PORT_NAME) {
                str += ", Port name. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_COPYRIGHT) {
                str += ", Copyright. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_TRACK_NAME) {
                str += ", Track Name. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_TEXT) {
                str += ", Text. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_LYRIC) {
                str += ", Lyric. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_CUE_POINT) {
                str += ", Cue Point. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_SMPTE_OFFSET) {
                byte[] data = meta.getData();
                if (data.length == 5) {
                    // Isolate the top 2 bits for the Frame Rate (using bitmask 11000000 / 0xC0)
                    int frameRateCode = (data[0] & 0xC0) >> 6;

                    // Isolate the bottom 5 bits for the Hours (using bitmask 00011111 / 0x1F)
                    int hours = data[0] & 0x1F;

                    // The rest are standard bytes
                    int minutes = data[1] & 0xFF;
                    int seconds = data[2] & 0xFF;
                    int frames = data[3] & 0xFF;
                    int subFrames = data[4] & 0xFF; // 100ths of a frame

                    // Translate the Frame Rate code
                    String fps = switch (frameRateCode) {
                        case 0 -> "24 fps";
                        case 1 -> "25 fps";
                        case 2 -> "29.97 fps (Drop Frame)";
                        case 3 -> "30 fps";
                        default -> "Unknown fps";
                    };

                    // Format it like a video timecode: [01:15:30:12.50] (24 fps)
                    str += String.format(", SMPTE Offset: %02dh:%02dm:%02ds:%02df.%02dsf (%s)",
                            hours, minutes, seconds, frames, subFrames, fps);
                } else {
                    str += ", SMPTE Offset. Unknown time encoding.";
                }
            } else if (meta.getType() == MidiConstants.META_PROGRAM_NAME) {
                str += ", Program Name. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_KEY_SIGNATURE) {
                str += ", Key Signature";
                try {
                    KeySignature sig = new KeySignature(meta);
                    str += ". Signature="+sig;
                } catch (IllegalArgumentException e) {
                    str += ". Invalid signature";
                }
            } else if (meta.getType() == MidiConstants.META_INSTRUMENT_NAME) {
                str += ", Instrument name. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_MARKER) {
                str += ", Marker. Data="+MidiUtils.formatBytesHexOnly(meta.getData());
            } else if (meta.getType() == MidiConstants.META_M_LIVE) {
                str += ", M-Live";
            } else if (meta.getType() == 0x00) {
                // Sequence Number
                // Used to identify patterns in Type 2 files, or the sequence number in Type 0/1.
                str += ", Sequence Number";
                byte[] data = meta.getData();
                if (data.length == 2) {
                    int sequenceNum = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
                    str += ". Number=" + sequenceNum;
                }
            } else if (meta.getType() == 0x20) {
                // MIDI Channel Prefix
                // Associates any subsequent SysEx or Meta events with a specific MIDI channel.
                str += ", MIDI Channel Prefix";
                byte[] data = meta.getData();
                if (data.length > 0) {
                    str += ". Channel="+((data[0] & 0xFF));//zero based channel number
                }
            } else if (meta.getType() == 0x7F) {
                // Sequencer Specific
                // DAWs use this to store proprietary data (like Ableton/Logic specific settings).
                str += ", Sequencer Specific Data";
            } else {
                str += String.format(", Unknown (0x%02X). Data=%s", meta.getType(), MidiUtils.formatBytesHexOnly(meta.getData()));
                return str;
            }
        }
        return str;
    }

    public static String midiMessageToShortString(MidiMessage m) {
        String str = "";
        if (m instanceof ShortMessage shorty) {
            int command = shorty.getCommand();
            str += switch (command) {
                case ShortMessage.NOTE_ON -> "Note ON, Velocity="+shorty.getData2();
                case ShortMessage.NOTE_OFF -> "Note OFF";
                case ShortMessage.CHANNEL_PRESSURE -> "Aftertouch";
                case ShortMessage.POLY_PRESSURE -> "Aftertouch (poly)";
                case ShortMessage.CONTROL_CHANGE -> "Control Change";
                case ShortMessage.PITCH_BEND -> "Pitch Bend";
                default -> "";
            };
            str += ", Channel="+shorty.getChannel();
        } else if (m instanceof SysexMessage sysex) {
            str += "Sysex";
            if (sysex.getMessage().length < 2) {
                str += " with only 1 byte: "+formatBytesHexOnly(sysex.getMessage());
                return str;
            }
            if (sysex.getMessage()[1] == MidiConstants.SYSEX_UNIVERSAL_REALTIME) {
                str += ", Realtime";
            } else if (sysex.getMessage()[1] == MidiConstants.SYSEX_UNIVERSAL_NON_REALTIME) {
                str += ", Non-Realtime";
            }
            if (isSysexLyrics(sysex.getMessage())) {
                str += ", Lyrics";
            }
            if (isResetGM(sysex.getMessage())) {
                str += ", GM Reset";
            } else if (isResetGS(sysex.getMessage())) {
                str += ", GS Reset";
            } else if (isResetXG(sysex.getMessage())) {
                str += ", XG Reset";
            } else if (isResetGM2(sysex.getMessage())) {
                str += ", GM2 Reset";
            } else {
                // take note of difference of midi (7 bit unsigned) vs. java (8 bit signed):
                str += ", "+formatBytesHexOnly(sysex.getMessage());
            }
        } else if (m instanceof MetaMessage meta) {
            str += "Meta";
            if (isMetaTempo(m)) {
                str += ", Tempo";
            } else if (isMetaEndOfTrack(m)) {
                str += ", EndOfTrack";
            } else if (meta.getType() == MidiConstants.META_TIME_SIGNATURE) {
                str += ", Time Signature";
            } else if (meta.getType() == MidiConstants.META_PORT_CHANGE) {
                str += ", Port change";
            } else if (meta.getType() == MidiConstants.META_PORT_NAME) {
                str += ", Port name";
            } else if (meta.getType() == MidiConstants.META_COPYRIGHT) {
                str += ", Copyright";
            } else if (meta.getType() == MidiConstants.META_TRACK_NAME) {
                str += ", Track Name";
            } else if (meta.getType() == MidiConstants.META_TEXT) {
                str += ", Text";
            } else if (meta.getType() == MidiConstants.META_LYRIC) {
                str += ", Lyric";
            } else if (meta.getType() == MidiConstants.META_CUE_POINT) {
                str += ", Cue Point";
            } else if (meta.getType() == MidiConstants.META_SMPTE_OFFSET) {
                str += ", SMPTE Offset";
            } else if (meta.getType() == MidiConstants.META_PROGRAM_NAME) {
                str += ", Program Change";
            } else if (meta.getType() == MidiConstants.META_KEY_SIGNATURE) {
                str += ", Key Signature";
            } else if (meta.getType() == MidiConstants.META_INSTRUMENT_NAME) {
                str += ", Instrument name";
            } else if (meta.getType() == MidiConstants.META_MARKER) {
                str += ", Marker";
            } else if (meta.getType() == MidiConstants.META_M_LIVE) {
                str += ", M-Live";
            } else {
                return str;
            }
        }
        return str;
    }

	public static boolean isSysexLyrics(byte[] message) {
		if (message.length > 7 && (message[0] & 0xFF) == 0xF0
				&& (message[2] & 0xFF) == 0x00 && (message[3] & 0xFF) == 0x20 && (message[4] & 0xFF) == 0x24
				&& (message[5] & 0xFF) == 0x00)
			return true;
		return false;
	}

    public static boolean isResetGM(byte[] message) {
        // byte[2] = device id
        if (message.length == 6 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x7E
                && (message[3] & 0xFF) == 0x09 && (message[4] & 0xFF) == 0x01 && (message[5] & 0xFF) == 0xF7) {
            return true;
        }
        return false;
    }

    public static boolean isResetXG(byte[] message) {
        // byte[2] = device id
        if (message.length == 9 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x43
                && (message[4] & 0xFF) == 0x00 && (message[5] & 0xFF) == 0x00 && (message[6] & 0xFF) == 0x7E
                && (message[7] & 0xFF) == 0x00 && (message[8] & 0xFF) == 0xF7) {
            return true;
        }
        return false;
    }

    public static boolean isResetGS(byte[] message) {
        // byte[2] = device id
        // byte[3] = device model
        if (message.length == 11 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x41
                && (message[3] & 0xFF) == 0x42 && (message[4] & 0xFF) == 0x12 && (message[5] & 0xFF) == 0x40
                && (message[6] & 0xFF) == 0x00 && (message[7] & 0xFF) == 0x7F && (message[8] & 0xFF) == 0x00
                && (message[10] & 0xFF) == 0xF7) {
            return true;
        }
        return false;
    }

    public static boolean isResetGM2(byte[] message) {
        // byte[2] = device id
        if (message.length == 6 && (message[0] & 0xFF) == 0xF0 && (message[1] & 0xFF) == 0x7E
                && (message[3] & 0xFF) == 0x09 && (message[4] & 0xFF) == 0x03
                && (message[5] & 0xFF) == 0xF7) {
            return true;
        }
        return false;
    }
}