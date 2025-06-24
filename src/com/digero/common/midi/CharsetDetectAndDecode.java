package com.digero.common.midi;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import com.digero.common.util.Pair;
import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

public class CharsetDetectAndDecode {
	private static final Logger log = Logger.getLogger("import.midi.text");
	
	public static Pair<String, Charset> decodeMidiData(byte[] data) {
		return decodeMidiData(data, false);
	}
	
	public static Pair<String, Charset> decodeMidiData(byte[] data, boolean western) {
		
		if (data == null || data.length == 0) return new Pair<String, Charset>("", null);

        // Marker sniff
        String marker = sniffAsciiMarker(data);
        if (marker != null) {
            return decodeWithMarker(data, marker);
        }

        // BOM sniff for UTF-8, UTF-16BE, UTF-16LE
        Pair<String, Charset> fromBom = tryBomDecode(data);
        if (fromBom != null) {
            return fromBom;
        }
        
        Pair<String, Charset> fromHeuristic16 = tryHeuristicUtf16(data);
        if (fromHeuristic16 != null) {
            return fromHeuristic16;
        }

        // Strict UTF-8 trial
        Pair<String, Charset> utf8 = tryStrictUtf8(data);
        if (utf8 != null) {
            return utf8;
        }
                
        Charset sjis = Charset.forName("Windows-31J");
        
        // Mix of ascii and Shift_JIS will trigger this
        // Strict
        if (isValidShiftJis(data) && (!western || data.length > 8)) {
            try {
                CharsetDecoder dec = sjis.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
                CharBuffer buf = dec.decode(ByteBuffer.wrap(data));
                String s = buf.toString();
                if (isPrintableAndNoSurrogates(s) && decodedHasJapaneseChar(s)) {
                    return new Pair<>(decodeWithReplace(data, sjis), sjis);
                }
            } catch (CharacterCodingException e) {
            }
        }

        if (isValidEucJp(data) && !western) {
            try {
                CharsetDecoder dec = Charset.forName("EUC-JP")
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
                String s = dec.decode(ByteBuffer.wrap(data)).toString();
                if (isPrintableAndNoSurrogates(s) && decodedHasJapaneseChar(s)) {
                	return new Pair<String, Charset>(decodeWithReplace(data,Charset.forName("EUC-JP")),Charset.forName("EUC-JP"));
                }
            } catch (CharacterCodingException e) {
            }
        }

        if (looksLikeWindows1252(data)) {
        	log.finer("loooks like windows-1252");
            // decode as Windows-1252 directly
        	Charset cs = Charset.forName("windows-1252");
            String s = decodeWithReplace(data, cs);
            log.fine("shorted win-1252");
            return new Pair<>(s, cs);
        }
        
        // Pure half width Shift_JIS will trigger this, with at 60% of bytes being japanese
        if (looksLikeAsciiOrHalfwidthKatakana(data)) {
            String decoded = decodeWithReplace(data, sjis);
            if (decodedHasMajorityHalfwidthKatakana(decoded, western?100:60)) {
                return new Pair<>(decoded, sjis);
            }
        }
        
        // Pure full width Shift_JIS will trigger this
        if (looksLikeSjisDoubleBytes(data, western?100:60)) {
        	String s = decodeWithReplace(data, sjis);
        	if (isPrintableAndNoSurrogates(s) && decodedHasJapaneseChar(s)) {
        		return new Pair<String, Charset>(s,sjis);
        	}
        }
        
        Pair<String, Charset> result = detectAndDecode(data, 65);//25
        if (result != null && result.first.length() > 2) {
        	// TODO: consider moving this after legacy when legacy gets low confidence.
        	//       icu4j not very good with short data.
        	log.fine("ICU4J returned "+result.first);
        	return result;
        }
        
        return bestFitLegacyDecode(data, western);
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
        String head = new String(data, 0, len, StandardCharsets.US_ASCII).toLowerCase();
        for (String m : markers) {
            if (head.startsWith(m.toLowerCase())) {
                return m;
            }
        }
        return null;
    }

    private static Pair<String, Charset> decodeWithMarker(byte[] data, String marker) {
        int offset = marker.length();
        if (data.length <= offset) return new Pair<String, Charset>("", null);
        byte[] tail = Arrays.copyOfRange(data, offset, data.length);
        Charset cs;
        switch (marker) {
            case "@LATIN":     cs = StandardCharsets.ISO_8859_1; break;
            case "@JP":        cs = Charset.forName("windows-31j"); break;
            case "@UTF-16LE":  cs = StandardCharsets.UTF_16LE; break;
            case "@UTF-16BE":  cs = StandardCharsets.UTF_16BE; break;
            default:           cs = StandardCharsets.ISO_8859_1;
        }
        return new Pair<String, Charset>(new String(tail, cs), cs);
    }

    private static Pair<String, Charset> tryBomDecode(byte[] data) {
        // UTF-8 BOM
        if (data.length >= 3
         && (data[0]&0xFF)==0xEF && (data[1]&0xFF)==0xBB && (data[2]&0xFF)==0xBF) {
            String s = new String(data, 3, data.length-3, StandardCharsets.UTF_8);
            if (isPrintableAndNoSurrogates(s)) return new Pair<String, Charset>(s, StandardCharsets.UTF_8);
        }
        // UTF-16BE BOM
        if (data.length >= 2
         && (data[0]&0xFF)==0xFE && (data[1]&0xFF)==0xFF) {
            String s = new String(data, 2, data.length-2, StandardCharsets.UTF_16BE);
            if (isPrintableAndNoSurrogates(s)) return new Pair<String, Charset>(s, StandardCharsets.UTF_16BE);
        }
        // UTF-16LE BOM
        if (data.length >= 2
         && (data[0]&0xFF)==0xFF && (data[1]&0xFF)==0xFE) {
            String s = new String(data, 2, data.length-2, StandardCharsets.UTF_16LE);
            if (isPrintableAndNoSurrogates(s)) return new Pair<String, Charset>(s, StandardCharsets.UTF_16LE);
        }
        return null;
    }
    
    /**
     * Detects UTF-16LE/BE without a BOM by looking for alternating zero bytes.
     * Returns the decoded string or {@code null} if the pattern is not convincing.
     */
    private static Pair<String, Charset> tryHeuristicUtf16(byte[] data) {
        if (data.length < 4 || (data.length & 1) != 0) {
            return null;                           // too short or odd length
        }

        // Count zeroes in even & odd positions
        int evenZero = 0, oddZero = 0;
        for (int i = 0; i < data.length; i += 2) {
            if (data[i] == 0)   evenZero++;
            if (data[i + 1] == 0) oddZero++;
        }
        int pairs = data.length / 2;
        double evenRatio = evenZero / (double) pairs;
        double oddRatio  = oddZero  / (double) pairs;

        // Require at least 25 % zeros on one side and at most 5 % on the other
        // require BOTH a relative and an absolute minimum of zeros
        int MIN_ZERO_COUNT = 2;
        boolean looks16BE = evenZero >= MIN_ZERO_COUNT && ((double)evenZero/pairs) > 0.25 && ((double)oddZero/pairs) < 0.05;
        boolean looks16LE = oddZero  >= MIN_ZERO_COUNT && ((double)oddZero/pairs)  > 0.25 && ((double)evenZero/pairs) < 0.05;
        if (!looks16BE && !looks16LE) {
            return null;
        }
        
        /**
         * If >25% of bytes are zero at the same offset (even or odd), we treat the input
         * as UTF-16BE (zeros at even indices) or UTF-16LE (zeros at odd indices).
         */
        
        Charset cs = looks16BE ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE;
        String s = new String(data, cs);
        return isPrintableAndNoSurrogates(s) ? new Pair<String, Charset>(s, cs) : null;
    }
 
    private static Pair<String, Charset> tryStrictUtf8(byte[] data) {
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
                return new Pair<String, Charset>(s,StandardCharsets.UTF_8);
            }
        } catch (CharacterCodingException ignored){}
        return null;
    }
    
    static List<Charset> candidates = Arrays.asList(
        // order matters only if tie in scoring
		Charset.forName("x-MacRoman"),     // Classic Mac OS Roman (legacy Mac text)
		Charset.forName("CP437"),          // OEM US (IBM PC DOS US)
		Charset.forName("CP850"),          // OEM Multilingual Latin I (DOS Western Europe)
		StandardCharsets.ISO_8859_1,       // ISO Latin-1 (Western Europe)
		Charset.forName("CP866"),          // OEM Cyrillic (DOS Russian)
		Charset.forName("windows-1251"),   // Windows Cyrillic (Eastern Europe, Russian)
		Charset.forName("KOI8-R"),         // Unix Russian (KOI8-R Cyrillic)
		Charset.forName("windows-1252"),   // Windows Western European (Latin-1 superset)
		Charset.forName("ISO-8859-15"),    // western which has french æ and €
		Charset.forName("windows-1250"),   // Windows Central/Eastern European
		//Charset.forName("Shift_JIS"),      // Legacy Japanese Shift_JIS
		Charset.forName("windows-31j"),    // Windows-31J (Microsoft's superset of Shift_JIS with extensions)
		Charset.forName("EUC-JP"),         // Unix Japanese (EUC-JP)
		Charset.forName("GB18030"),        // Chinese Simplified (PRC standard, superset of GBK)
		Charset.forName("Big5")            // Chinese Traditional (Taiwan/HK)
		
		//List of their name():
		/*
		x-MacRoman
		IBM437
		IBM850
		ISO-8859-1
		IBM866
		windows-1251
		KOI8-R
		windows-1252
		ISO-8859-15
		windows-1250
		windows-31j
		EUC-JP
		GB18030
		Big5
		*/
    );
    /*
    // output their name() as its different from string used to fetch them
    static {
    	for (Charset c : candidates) {
    		System.out.println(c.name()+" "+getScript(c.name()));
    	}
    }
    */
    
    private static boolean isAsian(Charset cs) {
		String n = cs.name().toUpperCase();
		return n.contains("SHIFT_JIS")
			|| n.contains("WINDOWS-31J")
			|| n.equals("EUC-JP")
			|| n.equals("GB18030")
			|| n.equals("BIG5");
    }
    
    public static Script getScript(String csName) {
    	csName = csName.toLowerCase();
      if (csName.contains("shift_jis") 
       || csName.contains("windows-31j")
       || csName.contains("euc-jp"))     return Script.JAPANESE;
      if (csName.contains("gb18030") 
       || csName.contains("big5"))       return Script.CHINESE;
      if (csName.equals("windows-1251") 
       || csName.equals("ibm866") 
       || csName.equals("koi8-r"))       return Script.CYRILLIC;
      return Script.WESTERN;
    }
    
    static Set<String> ignored = new HashSet<>();
    
    static {
    	CharsetDetector detector = new CharsetDetector();
    	
    	for (String detect : CharsetDetector.getAllDetectableCharsets()) {
    		boolean on = false;
	    	for (Charset cs : candidates) {
	    		if (cs.name().equalsIgnoreCase(detect)) {
	    			on = true;
	    		} else {
		    		for (String alias : cs.aliases()) {
		    			if (alias.equalsIgnoreCase(detect)) {
			    			on = true;
		    			}
		    	    }
	    		}	    		
	    	}
	    	if (!on) {
	    		ignored.add(detect);
	    	}
    	}
    }
    
    /**
     * True iff the data is composed *only* of:
     *   – Printable ASCII (0x20…0x7E), or
     *   – Half-width Katakana byte values (0xA1…0xDF),
     * and it contains at least one half-width Katakana byte.
     */
    private static boolean looksLikeAsciiOrHalfwidthKatakanaOld(byte[] data) {
        boolean sawKatakana = false;
        for (byte bb : data) {
            int b = bb & 0xFF;
            if (b >= 0x20 && b <= 0x7E) {
                // ASCII printable
                continue;
            }
            if (b >= 0xA1 && b <= 0xDF) {
                // half-width katakana
                sawKatakana = true;
                continue;
            }
            return false;
        }
        return sawKatakana;
    }
    
    private static boolean looksLikeAsciiOrHalfwidthKatakana(byte[] data) {
        int hwkCount = 0;
        for (byte bb : data) {
            int b = bb & 0xFF;
            if (b >= 0x20 && b <= 0x7E) {
                // printable ASCII
                continue;
            } else if (b >= 0xA1 && b <= 0xDF) {
                // half-width Katakana
                hwkCount++;
                continue;
            } else {
                // contains something outside ASCII/half-width Katakana
                return false;
            }
        }
        // require at least two half-width Katakana bytes to trigger Shift-JIS path
        return hwkCount >= 2;
    }

    /**
     * Returns true if at least half of the code points in the string
     * are half-width Katakana (U+FF61-U+FF9F).
     */
    private static boolean decodedHasMajorityHalfwidthKatakana(String s, int percent) {
        // Count only the non-ASCII code points
        long nonAscii = s.codePoints()
                         .filter(cp -> cp > 0x7F)
                         .count();
        if (nonAscii == 0) return false;   // no non-ASCII means no katakana
        
        long kata = s.codePoints()
                     .filter(cp -> (cp >= 0xFF61 && cp <= 0xFF9F))
                     .count();
        log.finer("JIS: "+kata+" nonAscii: "+nonAscii);
        // Now require >=60% of the non-ASCII be half-width Katakana
        return kata*100 >= nonAscii*percent;
    }
    
    /**
     * Returns true if we see at least % valid SJIS two-byte sequences
     * anywhere in the data.
     */
    private static boolean looksLikeSjisDoubleBytes(byte[] data, int percent) {
        int n = data.length;
        if (n < 5 || percent <= 0) return false;

        int pairs = n - 1;
        int doubleSeqCount = 0;
        for (int i = 0; i + 1 < n; i++) {
            int b1 = data[i]   & 0xFF;
            int b2 = data[i+1] & 0xFF;
            boolean leadOK  = (b1 >= 0x81 && b1 <= 0x9F)
                           || (b1 >= 0xE0 && b1 <= 0xFC);
            boolean trailOK = (b2 >= 0x40 && b2 <= 0x7E)
                           || (b2 >= 0x80 && b2 <= 0xFC);
            if (leadOK && trailOK) {
                doubleSeqCount++;
                i++;  // skip the trail byte to avoid overlapping
            }
        }

        // now check ratio: doubleSeqCount/pairs >= percent/100
        return doubleSeqCount * 100 >= pairs * percent;
    }
    
    private static boolean looksLikeWesternTextOld(byte[] data) {
        int western = 0, total = data.length;
        for (byte bb : data) {
            int b = bb & 0xFF;
            // printable ASCII + common Latin-1 punctuation (including ©)
            if ((b >= 0x20 && b <= 0x7E) || b == 0xA9) {
                western++;
            }
        }
        // if 95%+ of bytes are Western, treat as Western text
        return total > 0 && western * 100 >= 95 * total;
    }
    
    private static boolean looksLikeWindows1252Old(byte[] data) {
        for (byte bb : data) {
            int b = bb & 0xFF;
            if (b >= 0x80 && b <= 0x9F) {
                // In CP-1252 these map to things like “€” (0x80), ‘‚’ (0x82), “smart quotes”, etc.
                // In Latin-1 they'd be ISO controls, so seeing one here is a strong Win-1252 signal.
                return true;
            }
        }
        return false;
    }
    
    /**
     * True iff every byte maps to a printable CP-1252 character (no controls or undefined),
     * and at least one byte is a CP-1252-only extension (0x80–0x9F printable slot).
     */
    private static boolean looksLikeWindows1252(byte[] data) {
        int sawWinExt = 0;
        for (byte bb : data) {
            int b = bb & 0xFF;
            if (b >= 0x20 && b <= 0x7E) {
                // ASCII printable
                continue;
            } else if (b == 0x81 || b == 0x8D || b == 0x8F || b == 0x90 || b == 0x9D) {
            	//unused slots
            	return false;
            } else if (b >= 0xA0 && b <= 0xFF) {
                // Latin-1 printable (also CP-1252)
                continue;
            } else {
                // b is in 0x80–0x9F: only some of these are printable in CP-1252
                switch (b) {
                    case 0x80: // €
                    case 0x82: // ‚
                    case 0x83: // ƒ
                    case 0x84: // „
                    case 0x85: // …
                    //case 0x86: // †
                    //case 0x87: // ‡
                    case 0x88: // ˆ
                    //case 0x89: // ‰
                    case 0x8A: // Š
                    case 0x8B: // ‹
                    case 0x8C: // Œ
                    case 0x8E: // Ž
                    case 0x91: // ‘
                    case 0x92: // ’
                    case 0x93: // “
                    case 0x94: // ”
                    //case 0x95: // •
                    case 0x96: // –
                    case 0x97: // —
                    case 0x99: // ™
                    case 0x9A: // š
                    case 0x9B: // ›
                    case 0x9C: // œ
                    case 0x9E: // ž
                    case 0x9F: // Ÿ
                        sawWinExt++;
                        break;
                    default:
                        // either an ISO control or an undefined slot in CP-1252
                        return false;
                }
            }
        }
        // only true if we actually saw at least one CP-1252-specific printable
        return sawWinExt > 0;
    }
    
    public static Pair<String, Charset> detectAndDecode(byte[] data, int minConfidence) {
        CharsetDetector detector = new CharsetDetector();
        detector.setText(data);
        for (String detectNot : ignored) {
        	detector.setDetectableCharset(detectNot, false);
        }
        CharsetMatch[] match;
        try {
            match = detector.detectAll();
        } catch (Exception e) {
            return null;
        }
        if (match == null || match.length == 0) {
            return null;
        }
        int confidence = match[0].getConfidence();  // 0-100
        if (confidence < minConfidence) {
            return null;
        }
        try {
        	log.fine("icu4j: "+match[0].getName()+" "+confidence+"% for "+match[0].getString());
            return new Pair<String, Charset>(match[0].getString(), Charset.forName(match[0].getName()));
        } catch (Exception e) {
            return null;
        }
    }

    public static Pair<String, Charset> bestFitLegacyDecode(byte[] data, boolean western) {
        log.finer("best fit");

        CandidateResult bestDecoded = null;
        int bestScore = Integer.MAX_VALUE;
        boolean tie = false;

        for (Charset cs : candidates) {
            CandidateResult cr = evaluateCandidate(data, cs, western);
            // if western preferred, penalize non-Western codepages
    		if (western && getScript(cs.name()) != Script.WESTERN) {
    			// choose a penalty large enough to swing ties, but not so large
    			// that a really bad asian decode beats a mediocre Western decode.
    			cr.score += 8;
    		}
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
        if (bestDecoded == null) return new Pair<String, Charset> ("", null);
        log.fine("Legacy winner is "+bestDecoded);
        
        return new Pair<String, Charset> (bestDecoded.decoded, bestDecoded.cs);
    }

    /** Holds evaluation results for one charset candidate. */
    private static class CandidateResult {
        Charset cs;
        String decoded;
        int replCount;//replacement chars
        int ctrlCount;
        int cyrCount;
        int jpCount;
        int asciiCount;
        int extLatinCount;
        boolean byteLevelValid;
        int score;
        
        @Override
        public String toString() {
        	return cs.name()+" '"+decoded+"' "+" replCount="+replCount+" ctrlCount="+ctrlCount+" cyrCount="+cyrCount+" jpCount="+jpCount+" asciiCount="+asciiCount+" extLatinCount="+extLatinCount+" score="+score;
        }
    }
    
    /**
     * Evaluate one Charset candidate: byte-level validity, decode in REPLACE mode,
     * count script-specific codepoints, and compute a score.
     */
    private static CandidateResult evaluateCandidateOld(byte[] data, Charset cs) {
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
        //res.printableLatinCount = latin;

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
        if (csName.equals("windows-1251") || csName.equals("ibm866") || csName.equals("koi8-r")) {
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
            // Hard-reject invalid byte patterns (rare)
            // (GB18030 is a superset of GBK so everything C0..FD is legal; we skip
            // an expensive validator and rely on replacement-char count instead.)
            int replPenalty = res.replCount * 3;    //  each � adds noise
            score += replPenalty;

            /* ---------------------------------------------------------------
             * Scoring:
             *   – tiny baseline penalty so it doesn’t beat western text by accident
             *   – reward strings that are mostly CJK ideographs
             *   – penalise if no CJK at all
             *   – mild penalty for heavy ASCII/CJK mixing
             * --------------------------------------------------------------- */
            score += 40;                            // baseline (was 200)

            long cjk = decoded.codePoints()
                              .filter(cp -> 0x4E00 <= cp && cp <= 0x9FFF)
                              .count();
            int len = decoded.codePointCount(0, decoded.length());
            boolean hasCJK = cjk > 0;
            boolean hasASCII = decoded.codePoints()
                                      .anyMatch(cp -> 0x20 <= cp && cp <= 0x7E);

            if (!hasCJK) {
                score += 300;                       // looks western; deprioritise
            } else {
                double ratio = cjk / (double) len;
                if (ratio == 1.0)      score -= 40; // all Chinese – strong evidence
                else if (ratio >= .7)  score -= 20; // mostly Chinese
            }

            if (hasASCII && hasCJK) score += 40;    // mixed Latin & Chinese
        } else if (csName.contains("gb18030") /* deprecated */) {
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
            // 1)  Hard-reject byte streams that are not valid Big-5
            if (countInvalidBig5Bytes(data) > 0) {
                score += 1_000;
            } else {
                /*
                 *  Scoring guidelines
                 *  ------------------
                 *  • Small baseline penalty so Big-5 won’t win on garbage.
                 *  • Reward presence – and predominance – of CJK ideographs.
                 *  • Penalise if there are none (very unlikely for real Big-5 text).
                 *  • Mild penalty when ASCII and CJK are heavily mixed.
                 *  • Tiny uncertainty penalty for strings shorter than 3 code points.
                 */
                score += 60;                         // baseline (was 200)

                long cjk = decoded.codePoints()
                                  .filter(cp -> 0x4E00 <= cp && cp <= 0x9FFF)
                                  .count();
                int  len = decoded.codePointCount(0, decoded.length());
                boolean hasCJK  = cjk > 0;
                boolean hasASCII = decoded.codePoints()
                                          .anyMatch(cp -> 0x20 <= cp && cp <= 0x7E);

                if (!hasCJK) {
                    score += 300;                    // no Chinese at all → almost certainly wrong
                } else {
                    double ratio = cjk / (double) len;
                    if (ratio == 1.0)      score -= 80;   // 100 % Chinese – strong evidence
                    else if (ratio >= .7)  score -= 70;   // majority Chinese – good evidence
                }

                if (hasASCII && hasCJK)  score += 40;     // mixed Latin + Chinese

                if (len < 3)             score += 30;     // very short → add a bit of doubt
            }
        } else if (csName.contains("big5") /* deprecated */) {
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
        
        //System.out.println("\n "+res);
        return res;
    }
    
    private static CandidateResult evaluateCandidate(byte[] data, Charset cs, boolean preferWestern) {
        CandidateResult res = new CandidateResult();
        res.cs = cs;
        String csName = cs.name().toLowerCase();

        // 1. Byte‐level validity for Shift_JIS / EUC‐JP
        if (csName.contains("shift_jis") || csName.contains("windows-31j")) {
            res.byteLevelValid = isValidShiftJis(data);
        } else if (csName.contains("euc-jp")) {
            res.byteLevelValid = isValidEucJp(data);
        } else {
            res.byteLevelValid = true;
        }

        // 2. Decode in REPLACE mode
        String decoded = res.byteLevelValid
                       ? decodeWithReplace(data, cs)
                       : "";
        res.decoded = decoded;

        // 3. Analyze decoded string
        int replCount      = 0;
        int ctrlCount      = 0;
        int asciiCount     = 0;
        int supplementCount= 0;
        int cyrillicCount  = 0;
        int japaneseCount  = 0;

        String replChar = "\uFFFD";
        for (int i = 0; i < decoded.length(); ) {
            int cp = decoded.codePointAt(i);
            i += Character.charCount(cp);

            // replacement char
            if (decoded.startsWith(replChar, i - replChar.length())) {
                replCount++;
                continue;
            }
            // control (except newline/tab)
            if (cp < 0x20 && cp!='\n' && cp!='\r' && cp!='\t') {
                ctrlCount++;
                continue;
            }
            // Cyrillic letters
            if (isCyrillicLetter(cp)) {
                cyrillicCount++;
                continue;
            }
            // Japanese blocks
            if ((cp >= 0x3040 && cp <= 0x30FF) ||
                (cp >= 0x4E00 && cp <= 0x9FFF) ||
                (cp >= 0xFF61 && cp <= 0xFF9F)) {
                japaneseCount++;
                continue;
            }
            // ASCII
            if (cp >= 0x20 && cp <= 0x7E) {
                asciiCount++;
                continue;
            }
            // Latin-1 Supplement
            if (cp >= 0xA0 && cp <= 0xFF) {
                supplementCount++;
                continue;
            }
            // everything else we treat as "other" (counts below)
        }

        res.replCount           = replCount;
        res.jpCount = japaneseCount;
        res.cyrCount = cyrillicCount;
        res.ctrlCount           = ctrlCount;
        res.asciiCount = asciiCount;
        res.extLatinCount = supplementCount;

        // 4. Unified "junk vs. script-match" scoring
        Script script = getScript(cs.name());

        int matchCount;
        switch (script) {
            case CYRILLIC:
                matchCount = cyrillicCount;
                break;
            case JAPANESE:
                matchCount = japaneseCount;
                break;
            case CHINESE:
                matchCount = (int) decoded.codePoints()
                                          .filter(cp -> cp >= 0x4E00 && cp <= 0x9FFF)
                                          .count();
                break;
            default:
                matchCount = asciiCount;
                break;
        }

        int totalCp  = decoded.codePointCount(0, decoded.length());
        // new: treat ASCII (0x20–0x7F) as neutral for all non-Western scripts:
        int neutral = 0;
        if (script != Script.WESTERN) {
            neutral += asciiCount;
        } else {
            // Western still allows Latin-1 Supplement as neutral
            neutral += res.extLatinCount;
            if (totalCp > 7 && res.extLatinCount*4 > asciiCount*3) {
            	// too many ext latin chars to be normal western text
            	neutral /= 2;
            } else if (totalCp > 4 && res.extLatinCount >= asciiCount) {
            	neutral /= 2;
            }
        }
        int nonMatch = totalCp - matchCount - neutral;

        // weights
        int replWeight     = 200;  // per "�"
        int ctrlWeight     = 20;  // per stray control
        int nonMatchWeight = 40;   // per code‐point not in our target script
        int matchBonus     = -5;   // per in‐script code‐point
        int tieBreaker     = script == Script.WESTERN  ? 0
                           : script == Script.CYRILLIC  ? (totalCp == 1?25:1)
                           : script == Script.JAPANESE  ? (totalCp == 1?26:2)
                           :                                (totalCp == 1?27:3);
        
        // --- new bit: charset‐specific priority ---
        int scriptPriority = getScriptPriority(csName, script);
        
        
        log.fine("\n"+csName+" score:"
        		+"\nreplace: "+(replCount * replWeight)
        		+"\ncontrol: "+(ctrlCount * ctrlWeight)
        		+"\nnonMatch:"+(nonMatch * nonMatchWeight)
        		+"\ntie:     "+tieBreaker
        		+"\nprio:    "+scriptPriority
        		+"\nmatch:   "+(matchCount * matchBonus));
        
        int junkPenalty = replCount * replWeight
                        + ctrlCount * ctrlWeight
                        + nonMatch * nonMatchWeight;

        res.score = junkPenalty
                  + matchCount * matchBonus
                  + tieBreaker
                  + scriptPriority;
        log.fine(res.toString());
        return res;
    }

    // Place this helper somewhere in the same class:
    private static int getWesternPriority(String csName) {
        // lower is better; 0 means "top Western choice"
        if (csName.equals("iso-8859-1"))  return 0;
        if (csName.equals("iso-8859-15")) return 1;
        if (csName.equals("windows-1250")) return 2;
        if (csName.equals("x-MacRoman"))  return 3;
        if (csName.equals("windows-1252")) return 4;
        return 10;  // all others
    }
    
    public enum Script { WESTERN, CYRILLIC, JAPANESE, CHINESE }

    private static int getScriptPriority(String csName, Script script) {
        switch(script) {
          case WESTERN:
            return getWesternPriority(csName);
          case CYRILLIC:
            // 0 = windows-1251, 1 = koi8-r, 2 = ibm866, 10 = everyone else
            if (csName.equals("windows-1251")) return 0;
            if (csName.equals("koi8-r"))    return 1;
            if (csName.equals("ibm866"))    return 2;
            return 10;
          case JAPANESE:
            // 0 = windows-31j/shift_jis, 1 = euc-jp, else 10
            if (csName.contains("shift_jis") || csName.contains("windows-31j")) return 0;
            if (csName.contains("euc-jp"))    return 1;
            return 10;
          case CHINESE:
            // 0 = gb18030, 1 = big5, else 10
            if (csName.contains("gb18030")) return 0;
            if (csName.contains("big5"))    return 1;
            return 10;
          default:
            return 10;
        }
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
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith("\uFFFD");              // <-- force the standard REPLACEMENT CHARACTER
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
        // Require either:
	    //   • at least 1 kana (hiragana or full-width katakana), or
	    //   • at least 2 CJK characters.
	    // This prevents Latin1 accents that form a single rare Kanji
	    // from being mis-recognised as Shift_JIS.
	    if (validMulti < 1) return false;
	
	    String decoded = decodeWithReplace(data, Charset.forName("Shift_JIS"));
	    long kanaCount = decoded.codePoints()
	            .filter(cp -> (0x3040 <= cp && cp <= 0x30FF)) // hira + kata
	            .count();
	    long cjkCount  = decoded.codePoints()
	            .filter(cp -> (0x4E00 <= cp && cp <= 0x9FFF))
	            .count();
	
	    if (kanaCount == 0 && cjkCount < 2) {
	        return false;                 // not Japanese enough
	    }
	    return true;
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
}
