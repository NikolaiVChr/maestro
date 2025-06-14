package com.digero.common.midi;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import com.digero.common.midi.SequencerWrapper.TempoCacheSlow;

/**
 * A minimal copy of all used MidiUtils features.
 */
public class MidiUtils {
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
		return (long) (((double) tick) * tempoMPQ / resolution);
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
		return (long) ((((double) us) * resolution) / tempoMPQ);
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

        // Marker sniff
        String marker = sniffAsciiMarker(data);
        if (marker != null) {
            return decodeWithMarker(data, marker);
        }

        // BOM sniff for UTF-8, UTF-16BE, UTF-16LE
        String fromBom = tryBomDecode(data);
        if (fromBom != null) {
            return fromBom;
        }

        // Strict UTF-8 trial
        String utf8 = tryStrictUtf8(data);
        if (utf8 != null) {
            return utf8;
        }
        
        // Mix of acsii and Shift_JIS will trigger this
        String sjis = tryStrictShiftJis(data);
        if (sjis != null) {
            return sjis;
        }

        // Best-fit among legacy charsets
        return bestFitLegacyDecode(data);
    }

	/*
	 * Text that starts with any of these indicate charset:
	 * "@LATIN", "@JP", "@UTF-16LE" or "@UTF-16BE"
	 * According to MIDI specs.
	 * Although I have never seen any midi file actually use them.
	 */
    private static String sniffAsciiMarker(byte[] data) {
        String[] markers = { "@LATIN", "@JP", "@UTF-16LE", "@UTF-16BE" };
        int maxLen = Arrays.stream(markers).mapToInt(String::length).max().orElse(0);
        int len = Math.min(data.length, maxLen);
        String head = new String(data, 0, len, StandardCharsets.US_ASCII);
        for (String m : markers) {
            if (head.startsWith(m)) {
                return m;
            }
        }
        return null;
    }

    private static String decodeWithMarker(byte[] data, String marker) {
        int offset = marker.length();
        if (data.length <= offset) return "";
        byte[] tail = Arrays.copyOfRange(data, offset, data.length);
        Charset cs;
        switch (marker) {
            case "@LATIN":     cs = StandardCharsets.ISO_8859_1; break;
            case "@JP":        cs = Charset.forName("windows-31j"); break;
            case "@UTF-16LE":  cs = StandardCharsets.UTF_16LE; break;
            case "@UTF-16BE":  cs = StandardCharsets.UTF_16BE; break;
            default:           cs = StandardCharsets.ISO_8859_1;
        }
        return new String(tail, cs);
    }

    private static String tryBomDecode(byte[] data) {
        // UTF-8 BOM
        if (data.length >= 3
         && (data[0]&0xFF)==0xEF && (data[1]&0xFF)==0xBB && (data[2]&0xFF)==0xBF) {
            String s = new String(data, 3, data.length-3, StandardCharsets.UTF_8);
            if (isPrintableAndNoSurrogates(s)) return s;
        }
        // UTF-16BE BOM
        if (data.length >= 2
         && (data[0]&0xFF)==0xFE && (data[1]&0xFF)==0xFF) {
            String s = new String(data, 2, data.length-2, StandardCharsets.UTF_16BE);
            if (isPrintableAndNoSurrogates(s)) return s;
        }
        // UTF-16LE BOM
        if (data.length >= 2
         && (data[0]&0xFF)==0xFF && (data[1]&0xFF)==0xFE) {
            String s = new String(data, 2, data.length-2, StandardCharsets.UTF_16LE);
            if (isPrintableAndNoSurrogates(s)) return s;
        }
        return null;
    }
    
    private static boolean seemsShiftJis(byte[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            int b1 = data[i]   & 0xFF;
            int b2 = data[i+1] & 0xFF;
            // Shift_JIS lead bytes: 0x81–0x9F, 0xE0–0xEF
            // trail bytes:        0x40–0x7E, 0x80–0xFC
            if (((b1 >= 0x81 && b1 <= 0x9F) || (b1 >= 0xE0 && b1 <= 0xEF))
             && ((b2 >= 0x40 && b2 <= 0x7E) || (b2 >= 0x80 && b2 <= 0xFC))) {
                return true;
            }
        }
        return false;
    }
    
    private static String tryStrictShiftJis(byte[] data) {
        if (!seemsShiftJis(data)) {
            return null;
        }

        CharsetDecoder dec = Charset.forName("Shift_JIS")
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

        try {
            CharBuffer cb = dec.decode(ByteBuffer.wrap(data));
            String s = cb.toString();
            // ensure no stray controls/unpaired surrogates
            if (isPrintableAndNoSurrogates(s)) {
                return s;
            }
        } catch (CharacterCodingException e) {
            // decoding failed under REPORT mode
        }

        return null;
    }

    private static String tryStrictUtf8(byte[] data) {
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer cb = dec.decode(ByteBuffer.wrap(data));
            String s = cb.toString();
            if (isPrintableAndNoSurrogates(s)) {
                return s;
            }
        } catch (CharacterCodingException ignored){}
        return null;
    }

    private static String bestFitLegacyDecode(byte[] data) {
        Charset[] candidates = new Charset[]{
            Charset.forName("windows-1252"),
            StandardCharsets.ISO_8859_1,
            Charset.forName("x-MacRoman"),
            Charset.forName("CP437"),
            Charset.forName("CP850"),
            Charset.forName("windows-1250"),
            Charset.forName("windows-1251"),
            Charset.forName("Shift_JIS"),
            Charset.forName("EUC-JP"),
            Charset.forName("GB18030"),
            Charset.forName("Big5")
        };

        String best = "";
        int bestScore = Integer.MAX_VALUE;

        for (Charset cs : candidates) {
            CharsetDecoder dec = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            try {
                CharBuffer cb = dec.decode(ByteBuffer.wrap(data));
                String s = cb.toString();
                int score = scoreString(s);
                if (score < bestScore) {
                    bestScore = score;
                    best = s;
                    if (score == 0) break;
                }
            } catch (CharacterCodingException ignore) {}
        }
        return best;
    }

    private static boolean isPrintableAndNoSurrogates(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // C0 controls other than \r\n\t?
            if (c < 0x20 && c!='\n' && c!='\r' && c!='\t') return false;
            // Unpaired surrogates
            if (Character.isSurrogate(c)) return false;
        }
        return true;
    }

    private static int scoreString(String s) {
        int score = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\uFFFD' || c == '?') {
                score += 10;
            } else if (c < 0x20 && c!='\n' && c!='\r' && c!='\t') {
                score += 5;
            } else if (Character.isSurrogate(c)) {
                score += 5;
            }
        }
        return score;
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
}
