package com.digero.common.midi;

import java.nio.charset.Charset;
import java.util.logging.Logger;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

import com.digero.common.midi.SequencerWrapper.TempoCacheSlow;
import com.digero.common.util.Pair;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.common.midi.MidiConstants;

/**
 * A minimal copy of all used MidiUtils features.
 */
public class MidiUtils {
	private static final Logger log = Logger.getLogger("import.midi");
	
	public static final int DEFAULT_TEMPO_MPQ = 500000; // 120bpm
	public static final int META_END_OF_TRACK_TYPE = 0x2F;
	public static final int META_TEMPO_TYPE = 0x51;

	/**
	 * Given a microsecond time, convert to tick. returns tempo at the given time in cache.getCurrTempoMPQ
	 */
	public static long microsecond2tick(Sequence seq, long micros, TempoCacheSlow cache) {
		if (seq.getDivisionType() != Sequence.PPQ) {
			double dTick = (((double) micros) * ((double) seq.getDivisionType()) * ((double) seq.getResolution()))
					/ ((double) 1000000);
			long tick = (long) dTick;
			if (cache != null) {
				cache.currTempo = (int) cache.getTempoMPQAt(tick);
			}
			return tick;
		}

		if (cache == null) {
			cache = new TempoCacheSlow(seq);
		}
		long[] ticks = cache.ticks;
		int[] tempos = cache.tempos; // in MPQ
		int cacheCount = tempos.length;

		int resolution = seq.getResolution();

		long us = 0;
		long tick = 0;
		int newReadPos = 0;
		int i = 1;

		// walk through all tempo changes and add time for the respective blocks
		// to find the right tick
		if (micros > 0 && cacheCount > 0) {
			// this loop requires that the first tempo Event is at time 0
			while (i < cacheCount) {
				long nextTime = us + ticks2microsec(ticks[i] - ticks[i - 1], tempos[i - 1], resolution);
				if (nextTime > micros) {
					break;
				}
				us = nextTime;
				i++;
			}
			tick = ticks[i - 1] + microsec2ticks(micros - us, tempos[i - 1], resolution);
		}
		cache.currTempo = tempos[i - 1];
		return tick;
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

		int resolution = seq.getResolution();

		long[] ticks = cache.ticks;
		int[] tempos = cache.tempos; // in MPQ
		int cacheCount = tempos.length;

		// optimization to not always go through entire list of tempo events
		int snapshotIndex = cache.snapshotIndex;
		long snapshotMicro = cache.snapshotMicro;

		// walk through all tempo changes and add time for the respective blocks
		long us = 0L; // microsecond

		if (snapshotIndex <= 0 || snapshotIndex >= cacheCount || ticks[snapshotIndex] > tick) {
			snapshotMicro = 0L;
			snapshotIndex = 0;
		}
		if (cacheCount > 0) {
			// this implementation needs a tempo event at tick 0!
			int i = snapshotIndex + 1;
			while (i < cacheCount && ticks[i] <= tick) {
				snapshotMicro += ticks2microsec(ticks[i] - ticks[i - 1], tempos[i - 1], resolution);
				snapshotIndex = i;
				i++;
			}
			us = snapshotMicro + ticks2microsec(tick - ticks[snapshotIndex], tempos[snapshotIndex], resolution);
		}
		cache.snapshotIndex = snapshotIndex;
		cache.snapshotMicro = snapshotMicro;
		return us;
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
		// first check if it is a META message at all
		if (midiMsg.getLength() != 6 || midiMsg.getStatus() != MetaMessage.META) {
			return false;
		}
		// now get message and check for tempo
		byte[] msg = midiMsg.getMessage();
		// meta type must be 0x51, and data length must be 3
		return ((msg[1] & 0xFF) == META_TEMPO_TYPE) && (msg[2] == 3);
	}

	/**
	 * parses this message for a META tempo message and returns the tempo in MPQ, or -1 if this isn't a tempo message
	 */
	public static int getTempoMPQ(MidiMessage midiMsg) {
		// first check if it is a META message at all
		if (midiMsg.getLength() != 6 || midiMsg.getStatus() != MetaMessage.META) {
			return -1;
		}
		byte[] msg = midiMsg.getMessage();
		if (((msg[1] & 0xFF) != META_TEMPO_TYPE) || (msg[2] != 3)) {
			return -1;
		}
		return (msg[5] & 0xFF) | ((msg[4] & 0xFF) << 8) | ((msg[3] & 0xFF) << 16);
	}

	/** return true if the passed message is Meta End Of Track */
	public static boolean isMetaEndOfTrack(MidiMessage midiMsg) {
		// first check if it is a META message at all
		if (midiMsg.getLength() != 3 || midiMsg.getStatus() != MetaMessage.META) {
			return false;
		}
		// now get message and check for end of track
		byte[] msg = midiMsg.getMessage();
		return ((msg[1] & 0xFF) == META_END_OF_TRACK_TYPE) && (msg[2] == 0);
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
    	if (m instanceof ShortMessage shorty) {
            int command = shorty.getCommand();
    		switch (command) {
    			case ShortMessage.NOTE_ON:
    				str += "Note ON, Velocity="+shorty.getData2(); break;
    			case ShortMessage.NOTE_OFF:
    				str += "Note OFF"; break;
    			case ShortMessage.CHANNEL_PRESSURE:
    				str += "Aftertouch"; break;
    			case ShortMessage.POLY_PRESSURE:
    				str += "Aftertouch (poly)"; break;
    			case ShortMessage.CONTROL_CHANGE:
    				str += "Control Change"; break;
    			case ShortMessage.PITCH_BEND:
    				str += "Pitch Bend"; break;
    		}
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
    		}
    	}
    	str += ", Tick="+evt.getTick();
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