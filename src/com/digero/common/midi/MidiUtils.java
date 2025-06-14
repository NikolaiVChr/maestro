package com.digero.common.midi;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

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
        
        // Mix of ascii and Shift_JIS will trigger this
        // Strict
        if (isValidShiftJis(data)) {
            // decode strictly (REPORT) or with REPLACE if REPORT passes validity
            try {
                CharsetDecoder dec = Charset.forName("Shift_JIS")
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
                String s = dec.decode(ByteBuffer.wrap(data)).toString();
                if (isPrintableAndNoSurrogates(s) && decodedHasJapaneseChar(s)) {
                    return decodeWithReplace(data,Charset.forName("Shift_JIS"));
                }
            } catch (CharacterCodingException e) {
            }
        }

        if (isValidEucJp(data)) {
            try {
                CharsetDecoder dec = Charset.forName("EUC-JP")
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
                String s = dec.decode(ByteBuffer.wrap(data)).toString();
                if (isPrintableAndNoSurrogates(s) && decodedHasJapaneseChar(s)) {
                	return decodeWithReplace(data,Charset.forName("EUC-JP"));
                }
            } catch (CharacterCodingException e) {
            }
        }

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
    
    private static boolean seemsLikelyShiftJis(byte[] data) {
        // 1) Reject pure ASCII early
        boolean allAscii = true;
        for (byte b : data) {
            int ub = b & 0xFF;
            if (ub < 0x20 || ub > 0x7E) {
            	allAscii = false;
            	break;
            }
        }
        if (allAscii) return false;

        // 2) Invalid-byte check & detect multi-byte sequences
        int validMulti = 0, invalid = 0;
        int i = 0, len = data.length;
        while (i < len) {
            int b = data[i] & 0xFF;
            if ((0x81 <= b && b <= 0x9F) || (0xE0 <= b && b <= 0xEF)) {
                if (i+1 < len) {
                    int b2 = data[i+1] & 0xFF;
                    if ((0x40 <= b2 && b2 <= 0x7E) || (0x80 <= b2 && b2 <= 0xFC)) {
                        validMulti++;
                        i += 2;
                        continue;
                    }
                }
                invalid++;
                i++;
            } else {
                if ((0x00 <= b && b <= 0x7F) || (0xA1 <= b && b <= 0xDF)) {
                    // valid single-byte
                } else {
                    invalid++;
                }
                i++;
            }
        }
        if (invalid > 0) return false;              // no invalid bytes allowed
        if (validMulti < 1) return false;           // need at least one multi-byte pair

        // 3) Post-decode check
        try {
            CharsetDecoder dec = Charset.forName("Shift_JIS")
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            CharBuffer cb = dec.decode(ByteBuffer.wrap(data));
            String s = cb.toString();
            if (!isPrintableAndNoSurrogates(s)) return false;
            // Check for Japanese characters
            long jpCount = s.codePoints().filter(cp ->
                (cp >= 0x3040 && cp <= 0x30FF) || // hiragana/katakana
                (cp >= 0x4E00 && cp <= 0x9FFF)    // common kanji
            ).count();
            return jpCount >= 1;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
    
    private static String tryStrictUtf8(byte[] data) {
    	int end = data.length;
    	while (end > 0 && data[end-1] == 0) end--;
        byte[] trimmed = Arrays.copyOf(data, end);
        
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer cb = dec.decode(ByteBuffer.wrap(trimmed));
            String s = cb.toString();
            if (isPrintableAndNoSurrogates(s)) {
                return s;
            }
        } catch (CharacterCodingException ignored){}
        return null;
    }

    public static String bestFitLegacyDecode(byte[] data) {
        List<Charset> candidates = Arrays.asList(
            // order matters only if tie in scoring
			Charset.forName("x-MacRoman"),     // Classic Mac OS Roman (legacy Mac text)
			Charset.forName("CP437"),          // OEM US (IBM PC DOS US)
			Charset.forName("CP850"),          // OEM Multilingual Latin I (DOS Western Europe)
			Charset.forName("CP866"),          // OEM Cyrillic (DOS Russian)
			Charset.forName("windows-1251"),   // Windows Cyrillic (Eastern Europe, Russian)
			Charset.forName("KOI8-R"),         // Unix Russian (KOI8-R Cyrillic)
			Charset.forName("windows-1252"),   // Windows Western European (Latin-1 superset)
			StandardCharsets.ISO_8859_1,       // ISO Latin-1 (Western Europe)
			Charset.forName("windows-1250"),   // Windows Central/Eastern European
			Charset.forName("Shift_JIS"),      // Legacy Japanese Shift_JIS
			Charset.forName("windows-31j"),    // Windows-31J (Microsoft's superset of Shift_JIS with extensions)
			Charset.forName("EUC-JP"),         // Unix Japanese (EUC-JP)
			Charset.forName("GB18030"),        // Chinese Simplified (PRC standard, superset of GBK)
			Charset.forName("Big5")            // Chinese Traditional (Taiwan/HK)
        );

        CandidateResult bestDecoded = null;
        int bestScore = Integer.MAX_VALUE;
        boolean tie = false;

        for (Charset cs : candidates) {
            CandidateResult cr = evaluateCandidate(data, cs);
            if (cr.score < bestScore) {
                bestScore = cr.score;
                bestDecoded = cr;
                tie = false;
            } else if (cr.score == bestScore) {
                tie = true;
            }
        }
        if (tie) {
        	//System.out.println("Decoding tie between charsets");
        	//System.out.println(" Winner is "+bestDecoded.cs.name());
        }        
        if (bestDecoded == null) return "";
        //System.out.println(" Winner is "+bestDecoded.cs.name()+"\n "+bestDecoded.decoded);
        return bestDecoded.decoded;
    }

    /** Holds evaluation results for one charset candidate. */
    private static class CandidateResult {
        Charset cs;
        String decoded;
        int replCount;//replacement chars
        int ctrlCount;
        int cyrCount;
        int jpCount;
        int printableLatinCount;
        boolean byteLevelValid;
        int score;
        
        @Override
        public String toString() {
        	return cs.name()+" replCount="+replCount+" ctrlCount="+ctrlCount+" cyrCount="+cyrCount+" jpCount="+jpCount+" printableLatinCount="+printableLatinCount+" score="+score;
        }
    }

    /**
     * Evaluate one Charset candidate: byte-level validity, decode in REPLACE mode,
     * count script-specific codepoints, and compute a score.
     */
    private static CandidateResult evaluateCandidate(byte[] data, Charset cs) {
        CandidateResult res = new CandidateResult();
        res.cs = cs;
        String csName = cs.name().toLowerCase();

        // 1. Byte-level validity & short-name logic for Japanese
        if (csName.contains("shift_jis") || csName.contains("windows-31j")) {
            res.byteLevelValid = isValidShiftJis(data);
        } else if (csName.contains("euc-jp")) {
            res.byteLevelValid = isValidEucJp(data);
        } else {
            // For other encodings, we accept byte-level; invalid sequences will show up as replacements
            res.byteLevelValid = true;
        }

        // 2. Decode in REPLACE mode
        String decoded;
        if (!res.byteLevelValid) {
            decoded = "";
        } else {
            decoded = decodeWithReplace(data, cs);
        }
        res.decoded = decoded;

        // 3. Analyze decoded string
        int repl = 0, ctrl = 0, cyr = 0, jp = 0, latin = 0;
        int cyrLetterCount = 0;
        int nonAsciiNonCyrillic = 0;
        int digitCount = 0;
        String replacement = cs.newDecoder().replacement();
        for (int i = 0; i < decoded.length(); ) {
            int cp = decoded.codePointAt(i);
            i += Character.charCount(cp);
            
            // Replacement char from decode
            if (replacement.equals(cp)) {
                repl++;
                continue;
            }
            // Control chars (exclude newline/tab)
            if (cp < 0x20 && cp!='\n' && cp!='\r' && cp!='\t') {
                ctrl++;
                continue;
            }
            // Cyrillic block
            if (cp >= 0x0400 && cp <= 0x052F) {
                cyr++;
            }
            // Strict Cyrillic letters
            if (isCyrillicLetter(cp)) {
                cyrLetterCount++;
            }
            // Japanese
            boolean isJap = (cp >= 0x3040 && cp <= 0x30FF)
                          || (cp >= 0x4E00 && cp <= 0x9FFF)
                          || (cp >= 0xFF61 && cp <= 0xFF9F);
            if (isJap) {
                jp++;
                continue;
            }
            // ASCII digits
            if (cp >= '0' && cp <= '9') {
                digitCount++;
                latin++;
                continue;
            }
            // Printable ASCII (letters, punctuation, space)
            if (cp >= 0x20 && cp <= 0x7E) {
                latin++;
                continue;
            }
            // Anything else >0x7F that is not Cyrillic letter: junk
            if (cp > 0x7F && !isCyrillicLetter(cp)) {
                nonAsciiNonCyrillic++;
            }
        }
        res.replCount = repl;
        res.ctrlCount = ctrl;
        res.cyrCount = cyr;
        res.jpCount = jp;
        res.printableLatinCount = latin;

        // 4. Base score:
        int baseScore = 0;
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (c == '\uFFFD' || c == '?') {
                baseScore += 10;
            } else if (c == '\n' || c == '\r') {
                baseScore += 2;
            } else if (c < 0x20 && c != '\t') {
                baseScore += 5;
            } else if (Character.isSurrogate(c)) {
                baseScore += 5;
            }
        }
        int score = baseScore;

        // 5. Script-specific adjustments
        if (csName.contains("1251") || csName.equals("ibm866") || csName.equals("koi8-r")) {
        	int junkWeight = 5;
        	int letterBonus = 2;
        	int noLetterPenalty = 50;
        	int maxJunkPenalty = 100;
        	
        	score += Math.min(nonAsciiNonCyrillic * junkWeight, maxJunkPenalty);
	    	
        	if (cyr > 0) score -= 20;
        	else score += 100;
        	
        	if (cyrLetterCount > 0) {
        		score -= letterBonus*cyrLetterCount;
        	} else {
        		score += noLetterPenalty;
        	}
        } else if (csName.contains("shift_jis") || csName.contains("windows-31j")) {
            // Shift_JIS candidate: byte-level validity already ensured by isValidShiftJis
            if (!res.byteLevelValid) {
                score += 1000;
            } else if (jp >= 1) {
                score -= 20;
            } else {
                score += 100;
            }
        } else if (csName.contains("euc-jp")) {
            // EUC-JP candidate
            if (!res.byteLevelValid) {
                score += 1000;
            } else if (jp >= 1) {
                score -= 20;
            } else {
                score += 100;
            }
        } else if (csName.contains("gb18030")) {
            // baseline penalty
            score += 200;
            // ASCII vs CJK mix penalty
            boolean hasAscii = latin > 0;
            boolean hasCJK = decoded.codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
            if (hasAscii && hasCJK) score += 100;
            // If no CJK at all, heavy penalty
            if (!hasCJK) score += 200;
            // If mostly CJK but short or few ideographs, penalize
            int totalCp = decoded.codePointCount(0, decoded.length());
            long cjkCount = decoded.codePoints().filter(cp -> cp >= 0x4E00 && cp <= 0x9FFF).count();
            if (hasCJK && cjkCount < totalCp * 0.7) {
                score += 100;
            }
        } else if (csName.contains("big5")) {
            // 1) Byte-level validity: reject if invalid patterns
            if (countInvalidBig5Bytes(data) > 0) {
                score += 1000; // disqualify Big5
            } else {
                // 2) Base penalty so Big5 only wins with strong Chinese evidence
                score += 200;

                // 4) ASCII vs CJK mix penalty
                boolean hasAscii = decoded.codePoints().anyMatch(cp -> (cp >= 0x20 && cp <= 0x7E));
                boolean hasCJK = decodedHasCommonCJK(decoded);
                if (hasAscii && hasCJK) {
                    score += 100;
                }
                // 5) No CJK at all: heavy penalty
                if (!hasCJK) {
                    score += 200;
                }
                // 6) Proportion of CJK vs total length
                int totalCp = decoded.codePointCount(0, decoded.length());
                long cjkCount = decoded.codePoints().filter(cp -> cp >= 0x4E00 && cp <= 0x9FFF).count();
                if (hasCJK) {
                    // require most codepoints be CJK (e.g. >= 70%)
                    if (totalCp > 0 && cjkCount < totalCp * 0.7) {
                        score += 100;
                    }
                }
                // 7) Optionally: check for common CJK punctuation or patterns
                // e.g., Chinese punctuation like “，”、“。” often appear; if absent entirely, penalize:
                boolean hasChinesePunct = decoded.contains("，") || decoded.contains("。") || decoded.contains("：");
                if (hasCJK && !hasChinesePunct) {
                    // light penalty: many Chinese names may not have punctuation, so keep small
                    score += 20;
                }
            }
        } else {
            // Western single-byte candidate
            int len = decoded.codePointCount(0, decoded.length());
            int nonLatin = len - latin;
            score += nonLatin * 2;
            if (nonLatin > 0 && cyr == 0 && jp == 0) {
                score += nonLatin * 3;
            }
        }

        res.score = score;
        return res;
    }
    
    private static int countInvalidBig5Bytes(byte[] data) {
        int invalid = 0, i = 0;
        while (i < data.length) {
            int b = data[i] & 0xFF;
            if (b <= 0x7F) {
                // ASCII single-byte
                i++;
            } else if (b >= 0x81 && b <= 0xFE) {
                // possible two-byte Big5
                if (i + 1 < data.length) {
                    int b2 = data[i+1] & 0xFF;
                    // trail: 0x40-0x7E or 0xA1-0xFE
                    if ((0x40 <= b2 && b2 <= 0x7E) || (0xA1 <= b2 && b2 <= 0xFE)) {
                        i += 2;
                        continue;
                    }
                }
                // invalid lead or missing/invalid trail
                invalid++;
                i++;
            } else {
                // byte in 0x80 or >0xFE: invalid
                invalid++;
                i++;
            }
        }
        return invalid;
    }

    private static int countValidBig5Multi(byte[] data) {
        int cnt = 0;
        for (int i = 0; i < data.length - 1; i++) {
            int b1 = data[i] & 0xFF;
            int b2 = data[i+1] & 0xFF;
            if (b1 >= 0x81 && b1 <= 0xFE
             && ((0x40 <= b2 && b2 <= 0x7E) || (0xA1 <= b2 && b2 <= 0xFE))) {
                cnt++;
                i++;
            }
        }
        return cnt;
    }

    // Check if decoded string has CJK ideographs in the common block
    private static boolean decodedHasCommonCJK(String s) {
        return s.codePoints().anyMatch(cp -> (cp >= 0x4E00 && cp <= 0x9FFF));
    }
    
    private static boolean isCyrillicLetter(int cp) {
        // U+0410–U+042F: Cyrillic uppercase
        // U+0430–U+044F: Cyrillic lowercase
        // U+0401 (Ё), U+0451 (ё) if needed
        return (cp >= 0x0410 && cp <= 0x044F) || cp == 0x0401 || cp == 0x0451;
    }

    /** Decode bytes with REPLACE mode under given Charset. */
    private static String decodeWithReplace(byte[] data, Charset cs) {
        try {
            CharsetDecoder dec = cs.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
            CharBuffer cb = dec.decode(ByteBuffer.wrap(data));
            return cb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    public static boolean isValidShiftJis(byte[] data) {
        int len = data.length;
        if (len == 0) return false;
        // First reject invalid bytes as before
        if (countInvalidShiftJisBytes(data) > 0) return false;

        int validMulti = countValidShiftJisMulti(data);

        // Byte-level kana evidence:
        boolean hasHiragana = hasShiftJisHiragana(data);
        boolean hasFullKatakana = hasShiftJisFullwidthKatakana(data);
        boolean hasHalfKatakana = hasShiftJisHalfwidthKatakana(data);

        // If any full-width kana appear, accept
        if (hasHiragana || hasFullKatakana) {
            return true;
        }
        // Otherwise, if pure ASCII+half-width katakana:
        boolean allAsciiOrHwk = true;
        int hwkCount = 0;
        for (byte bb : data) {
            int b = bb & 0xFF;
            if (b <= 0x7F) continue;
            if (0xA1 <= b && b <= 0xDF) {
                hwkCount++;
                continue;
            }
            allAsciiOrHwk = false;
            break;
        }
        if (allAsciiOrHwk && (hwkCount > 1 || (len == 1 && hwkCount == 1))) {
            // require at least two half-width katakana for confidence
            return false;//TODO: change to false, since it detected cyrillic as jis.
        }
        // Otherwise require at least one multi-byte sequence AND decoded Japanese char
        if (validMulti < 1) return false;
        String decoded = decodeWithReplace(data, Charset.forName("Shift_JIS"));
        return decodedHasAnyJapaneseChar(decoded);//TODO: remove 'any' to require at least 1 full char.
    }
    
    /**
     * Check if the byte array contains at least one full-width hiragana sequence in Shift_JIS.
     * Hiragana in Shift_JIS: lead byte = 0x82, trail byte in 0x9F-0xF1.
     */
    private static boolean hasShiftJisHiragana(byte[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            int b1 = data[i] & 0xFF;
            int b2 = data[i+1] & 0xFF;
            if (b1 == 0x82 && (0x9F <= b2 && b2 <= 0xF1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the byte array contains at least one full-width katakana sequence in Shift_JIS.
     * Full-width Katakana in Shift_JIS: lead byte = 0x83, trail byte in 0x40-0x96.
     */
    private static boolean hasShiftJisFullwidthKatakana(byte[] data) {
        for (int i = 0; i < data.length - 1; i++) {
            int b1 = data[i] & 0xFF;
            int b2 = data[i+1] & 0xFF;
            if (b1 == 0x83 && (0x40 <= b2 && b2 <= 0x96)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the byte array contains at least one half-width katakana byte in Shift_JIS.
     * Half-width Katakana single bytes: 0xA1-0xDF.
     */
    private static boolean hasShiftJisHalfwidthKatakana(byte[] data) {
        for (byte bb : data) {
            int b = bb & 0xFF;
            if (0xA1 <= b && b <= 0xDF) {
                return true;
            }
        }
        return false;
    }

    /** Count valid Shift_JIS multi-byte sequences (lead+trail). */
    private static int countValidShiftJisMulti(byte[] data) {
        int cnt = 0;
        for (int i = 0; i < data.length - 1; i++) {
            int b1 = data[i] & 0xFF;
            int b2 = data[i+1] & 0xFF;
            if (((0x81 <= b1 && b1 <= 0x9F) || (0xE0 <= b1 && b1 <= 0xEF))
             && ((0x40 <= b2 && b2 <= 0x7E) || (0x80 <= b2 && b2 <= 0xFC))) {
                cnt++;
                i++;
            }
        }
        return cnt;
    }

    /** Count invalid bytes under Shift_JIS rules. */
    private static int countInvalidShiftJisBytes(byte[] data) {
        int invalid = 0, i = 0;
        while (i < data.length) {
            int b = data[i] & 0xFF;
            if ((0x81 <= b && b <= 0x9F) || (0xE0 <= b && b <= 0xEF)) {
                if (i + 1 < data.length) {
                    int b2 = data[i+1] & 0xFF;
                    if ((0x40 <= b2 && b2 <= 0x7E) || (0x80 <= b2 && b2 <= 0xFC)) {
                        i += 2;
                        continue;
                    }
                }
                invalid++;
                i++;
            } else {
                if ((0x00 <= b && b <= 0x7F) || (0xA1 <= b && b <= 0xDF)) {
                    // ok
                } else {
                    invalid++;
                }
                i++;
            }
        }
        return invalid;
    }

    /** Check if decoded string has at least one Japanese character. */
    private static boolean decodedHasJapaneseChar(String s) {
	    return s.codePoints().anyMatch(cp ->
	        (cp >= 0x3040 && cp <= 0x30FF) ||  // full-width Hiragana/Katakana
	        (cp >= 0x4E00 && cp <= 0x9FFF)  // Kanji
	    );
	}
    
    private static boolean decodedHasAnyJapaneseChar(String s) {
	    return s.codePoints().anyMatch(cp ->
	        (cp >= 0x3040 && cp <= 0x30FF) ||  // full-width Hiragana/Katakana
	        (cp >= 0x4E00 && cp <= 0x9FFF) ||  // Kanji
	        (cp >= 0xFF61 && cp <= 0xFF9F)     // half-width Katakana
	    );
	}

    private static int countInvalidEucJpBytes(byte[] data) {
        int invalid = 0, i = 0;
        while (i < data.length) {
            int b = data[i] & 0xFF;
            if (b <= 0x7F) {
                // ASCII
                i++;
            } else if (b >= 0xA1 && b <= 0xFE) {
                // 2-byte JIS X 0208
                if (i + 1 < data.length) {
                    int b2 = data[i+1] & 0xFF;
                    if (b2 >= 0xA1 && b2 <= 0xFE) {
                        i += 2;
                        continue;
                    }
                }
                invalid++;
                i++;
            } else if (b == 0x8E) {
                // SS2: half-width Katakana
                if (i + 1 < data.length) {
                    int b2 = data[i+1] & 0xFF;
                    if (b2 >= 0xA1 && b2 <= 0xDF) {
                        i += 2;
                        continue;
                    }
                }
                invalid++;
                i++;
            } else if (b == 0x8F) {
                // SS3: JIS X 0212 (rare)
                if (i + 2 < data.length) {
                    int b2 = data[i+1] & 0xFF;
                    int b3 = data[i+2] & 0xFF;
                    if ((b2 >= 0xA1 && b2 <= 0xFE) && (b3 >= 0xA1 && b3 <= 0xFE)) {
                        i += 3;
                        continue;
                    }
                }
                invalid++;
                i++;
            } else {
                invalid++;
                i++;
            }
        }
        return invalid;
    }

    /** Count valid EUC-JP multi-byte sequences and track if any are kana. */
    private static class EucJpMultiInfo {
        int totalMulti;
        int kanaMulti; // hiragana or full-width katakana
        int ss2Count;  // half-width katakana sequences
    }
    private static EucJpMultiInfo analyzeEucJpMulti(byte[] data) {
        EucJpMultiInfo info = new EucJpMultiInfo();
        int i = 0;
        while (i < data.length) {
            int b = data[i] & 0xFF;
            if (b >= 0xA1 && b <= 0xFE && i + 1 < data.length) {
                int b2 = data[i+1] & 0xFF;
                if (b2 >= 0xA1 && b2 <= 0xFE) {
                    info.totalMulti++;
                    // Check for hiragana (0xA4) or full-width katakana (0xA5)
                    if (b == 0xA4 || b == 0xA5) {
                        info.kanaMulti++;
                    }
                    i += 2;
                    continue;
                }
            } else if (b == 0x8E && i + 1 < data.length) {
                int b2 = data[i+1] & 0xFF;
                if (b2 >= 0xA1 && b2 <= 0xDF) {
                    info.totalMulti++;
                    info.ss2Count++;
                    i += 2;
                    continue;
                }
            } else if (b == 0x8F && i + 2 < data.length) {
                int b2 = data[i+1] & 0xFF, b3 = data[i+2] & 0xFF;
                if ((b2 >= 0xA1 && b2 <= 0xFE) && (b3 >= 0xA1 && b3 <= 0xFE)) {
                    info.totalMulti++;
                    // SS3: full-width but rare; we may treat as non-kana
                    i += 3;
                    continue;
                }
            }
            // Single-byte or invalid: skip one
            i++;
        }
        return info;
    }

    /** Validate EUC-JP name, requiring kana evidence. */
    public static boolean isValidEucJp(byte[] data) {
        int len = data.length;
        if (len == 0) return false;

        int invalid = countInvalidEucJpBytes(data);
        if (invalid > 0) return false;

        EucJpMultiInfo info = analyzeEucJpMulti(data);

        // Check pure ASCII+SS2 mix: accept if at least two SS2 sequences? or one plus other evidence
        boolean allAsciiOrSs2 = true;
        int ss2Count = 0;
        int i = 0;
        while (i < len) {
            int b = data[i] & 0xFF;
            if (b <= 0x7F) {
                i++;
            } else if (b == 0x8E && i + 1 < len) {
                int b2 = data[i+1] & 0xFF;
                if (b2 >= 0xA1 && b2 <= 0xDF) {
                    ss2Count++;
                    i += 2;
                } else {
                    allAsciiOrSs2 = false;
                    break;
                }
            } else {
                allAsciiOrSs2 = false;
                break;
            }
        }
        if (allAsciiOrSs2) {
            // require at least two SS2 sequences to reduce false positives
            if (ss2Count > 1 || (len == 1 && ss2Count == 1)) {
                // decode and check half-width kana
                String decoded = decodeWithReplace(data, Charset.forName("EUC-JP"));
                return decodedHasAnyJapaneseChar(decoded);
            }
            return false;
        }

        // Otherwise require at least one kana multi-byte (hiragana or full-width katakana)
        if (info.kanaMulti < 1) {
            return false;
        }
        // decode and confirm presence of Japanese script
        String decoded = decodeWithReplace(data, Charset.forName("EUC-JP"));
        return decodedHasJapaneseChar(decoded);
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
