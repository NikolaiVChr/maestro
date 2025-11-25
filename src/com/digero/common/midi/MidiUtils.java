package com.digero.common.midi;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.logging.Logger;

import javax.sound.midi.*;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.SequencerWrapper.TempoCacheSlow;
import com.digero.common.util.Pair;
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
            if (sysex.getMessage()[1] == MidiConstants.SYSEX_UNIVERSAL_REALTIME) {
                str += ", Realtime";
            } else if (sysex.getMessage()[1] == MidiConstants.SYSEX_UNIVERSAL_NON_REALTIME) {
                str += ", Non-Realtime";
            }
            if (isSysexLyrics(sysex.getMessage())) {
                str += ", Lyrics";
            }
            if (SequenceInfo.isResetGM(sysex.getMessage())) {
                str += ", GM Reset";
            } else if (SequenceInfo.isResetGS(sysex.getMessage())) {
                str += ", GS Reset";
            } else if (SequenceInfo.isResetXG(sysex.getMessage())) {
                str += ", XG Reset";
            } else if (SequenceInfo.isResetGM2(sysex.getMessage())) {
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
}