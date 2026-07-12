package com.digero.common.midi;

import java.lang.Character.UnicodeBlock;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.digero.common.util.Pair;
import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

@SuppressWarnings("HardCodedStringLiteral")
public class CharsetDetectAndDecode {
	private static final Logger log = Logger.getLogger("import.midi.text");

    public static String decodeMidiData(byte[] data, Charset cs) {
        if (data == null || data.length == 0) return "";
        return decodeWithReplace(data, cs);
    }

    /**
     * Decode all text blobs from one MIDI file together. A file has a single author
     * and almost always a single encoding, so tracks that decode unambiguously vote
     * on the charset for the tracks that don't. This resolves the mostly-ASCII case
     * (soundfont bank labels, "Track 1" + a few kanji) that is genuinely undecidable
     * in isolation.
     */
    public static List<Pair<String, Charset>> decodeMidiFile(List<byte[]> blobs, boolean western) {
        // Pass 1: decode each blob independently, but keep the *confidence*, not just
        // the answer. A blob is a "strong vote" only if its decode is unambiguous.
        Script fileScript = voteScript(blobs, western);
        //System.out.println("File script: " + fileScript);

        if (fileScript == null) return null;

        // Pass 2: re-decode, biasing toward the file's script where a blob is ambiguous.
        List<Pair<String, Charset>> out = new ArrayList<>(blobs.size());
        for (byte[] blob : blobs) {
            if (blob == null) {
                out.add(null);
                continue;
            }
            out.add(decodeBlobWithFileHint(blob, western, fileScript));
        }
        return out;
    }

    private static Pair<String, Charset> decodeBlobWithFileHint(byte[] data, boolean western, Script preferScript) {
        Pair<String, Charset> unbiased = decodeMidiData(data, western);
        if (preferScript == null || unbiased.second == null) return unbiased;

        Script own = getScript(unbiased.second.name());
        int evidence = scriptEvidence(unbiased.first, own);

        // This blob spoke for itself -- a clean non-ASCII decode in some script.
        // Do not let the file vote override it. Bilingual files (Russian lyrics +
        // Big5 bank names) are real; each track keeps its own answer.
        if (evidence >= 2) return unbiased;

        // Ambiguous blob with a file hint. Run the strong, unambiguous shortcuts
        // (UTF-8, BOM, marker) but NOT the weak ones (half-width katakana, CP1252),
        // then let the scorer apply the bias. The weak shortcuts are exactly the ones
        // that mis-fire on Big5/Cyrillic trail bytes -- letting them win here would
        // deny the file vote the decision it exists to make.
        Pair<String, Charset> strong = tryStrongShortcutsOnly(data);
        if (strong != null) return strong;

        // Half-width katakana track names (ﾒﾛﾃﾞｨ, ﾄﾞﾗﾑ) are pure 0xA1-0xDF, which the
        // scorer refuses to credit as Japanese (matchCount=0) to avoid Cyrillic false
        // positives -- so they can never win CP932 via the scorer, bias or not. But if
        // the FILE voted Japanese, a majority-katakana track is genuinely Japanese.
        // Honour the katakana shortcut in that case.
        if (preferScript == Script.JAPANESE && looksLikeAsciiOrHalfwidthKatakana(data)) {
            Charset sjis = Charset.forName("Windows-31J");
            String s = decodeWithReplace(data, sjis);
            if (decodedHasMajorityHalfwidthKatakana(s, 60)) {
                return new Pair<>(s, sjis);
            }
        }

        if (preferScript == Script.EASTERN) {
            String s = decodeWithReplace(data, Charset.forName("windows-1258"));
            long viet = s.codePoints().filter(VIET_PRECOMPOSED::contains).count();
            if (viet > 0 && !containsPua(s) && isPrintableAndNoSurrogates(s)) {
                return new Pair<>(s, Charset.forName("windows-1258"));
            }
        }

        return bestFitLegacyDecode(data, western, preferScript);
    }

    private static Pair<String, Charset> tryStrongShortcutsOnly(byte[] data) {
        if (data == null || data.length == 0) return new Pair<>("", null);

        String marker = sniffAsciiMarker(data);
        if (marker != null) return decodeWithMarker(data, marker);

        Pair<String, Charset> bom = tryBomDecode(data);
        if (bom != null) return bom;

        Pair<String, Charset> u16 = tryHeuristicUtf16(data);
        if (u16 != null) return u16;

        Pair<String, Charset> u8 = tryStrictUtf8(data);
        if (u8 != null) return u8;

        // Majority-ASCII CP1252 is strong enough to resist a file bias: a blob that is
        // mostly ASCII plus a couple of CP1252 punctuation bytes is Western, not a
        // mis-decode of the file's CJK. Skipping it would let "don't" in a Chinese file
        // get biased to GB18030 (92 74 -> one Han char).
        if (looksLikeWindows1252(data)) {
            Charset cs = Charset.forName("windows-1252");
            String s = decodeWithReplace(data, cs);
            long ascii = s.codePoints().filter(cp -> cp < 0x80).count();
            if (ascii * 2 >= s.codePointCount(0, s.length())) return new Pair<>(s, cs);
        }
        return null;
    }

	public static Pair<String, Charset> decodeMidiData(byte[] data) {
		return decodeMidiData(data, false);
	}

    public static Pair<String, Charset> decodeMidiData(byte[] data, boolean western) {
        return decodeMidiDataInternal(data, western, null);
    }

	private static Pair<String, Charset> decodeMidiDataInternal(byte[] data, boolean western, Script preferScript) {
		if (data == null || data.length == 0) return new Pair<>("", null);

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
        Charset eucJp = Charset.forName("EUC-JP");
        
        // Mix of ascii and Shift_JIS will trigger this
        if (looksLikeJapaneseSjis(data) && (!western || data.length > 8)) {
            try {
                CharsetDecoder dec = sjis.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
                String s = dec.decode(ByteBuffer.wrap(data)).toString();
                if (isPrintableAndNoSurrogates(s) && decodedIsMostlyJapanese(s, 60) && !containsPua(s)) {
                    log.fine("First sjis shortcut");
                    return new Pair<>(s, sjis);
                }
            } catch (CharacterCodingException e) {
            }
        }

        if (looksLikeJapaneseEucJp(data) && !western) {
            try {
                CharsetDecoder dec = Charset.forName("EUC-JP")
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
                String s = dec.decode(ByteBuffer.wrap(data)).toString();
                if (isPrintableAndNoSurrogates(s) && decodedIsMostlyJapanese(s, 60) && !containsPua(s)) {
                    log.fine("Euc shortcut");
                    return new Pair<>(s, eucJp);
                }
            } catch (CharacterCodingException e) {
            }
        }

        if (looksLikeWindows1252(data)) {
            Charset cs = Charset.forName("windows-1252");
            String s = decodeWithReplace(data, cs);
            // Real Western text is majority ASCII. A CP1252 decode that is mostly
            // high bytes is mojibake from some other codepage; let the scorer decide.
            long ascii = s.codePoints().filter(cp -> cp < 0x80).count();
            if (ascii * 2 >= s.codePointCount(0, s.length())) {
                log.fine("shorted win-1252");
                return new Pair<>(s, cs);
            }
        }
        
        // Pure half width Shift_JIS will trigger this, with at 60% of bytes being japanese
        if (looksLikeAsciiOrHalfwidthKatakana(data)) {
            String decoded = decodeWithReplace(data, sjis);
            if (decodedHasMajorityHalfwidthKatakana(decoded, western?100:60)) {
            	log.fine("Second sjis shortcut");
                return new Pair<>(decoded, sjis);
            }
        }

        // Pure full width Shift_JIS will trigger this.
        // Byte-level kana evidence is required: Cyrillic in windows-1251 occupies
        // 0xC0-0xFF and therefore forms structurally valid SJIS lead+trail pairs,
        // but it can never produce a 0x82 (hiragana) or 0x83 (katakana) lead.
        if (looksLikeSjisDoubleBytes(data, western ? 100 : 60)
                && (hasShiftJisHiragana(data) || hasShiftJisFullwidthKatakana(data))) {
            try {
                CharsetDecoder dec = sjis.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                String s = dec.decode(ByteBuffer.wrap(data)).toString();
                if (isPrintableAndNoSurrogates(s) && decodedIsMostlyJapanese(s, 60) && !containsPua(s)) {
                    log.fine("Third sjis shortcut");
                    return new Pair<>(s, sjis);
                }
            } catch (CharacterCodingException ignored) {
            }
        }
        
        Pair<String, Charset> result = detectAndDecode(data, 65);//25
        if (result != null && result.first.length() > 2) {
        	// TODO: consider moving this after legacy when legacy gets low confidence.
        	//       icu4j not very good with short data.
        	log.fine("ICU4J returned "+result.first);
        	return result;
        }
        
        return bestFitLegacyDecode(data, western, null);
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
        if (data.length <= offset) return new Pair<>("", null);
        byte[] tail = Arrays.copyOfRange(data, offset, data.length);
        Charset cs = switch (marker) {
            case "@LATIN" -> StandardCharsets.ISO_8859_1;
            case "@JP" -> Charset.forName("windows-31j");
            case "@UTF-16LE" -> StandardCharsets.UTF_16LE;
            case "@UTF-16BE" -> StandardCharsets.UTF_16BE;
            default -> StandardCharsets.ISO_8859_1;
        };
        return new Pair<>(new String(tail, cs), cs);
    }

    private static Pair<String, Charset> tryBomDecode(byte[] data) {
        // UTF-8 BOM
        if (data.length >= 3
         && (data[0]&0xFF)==0xEF && (data[1]&0xFF)==0xBB && (data[2]&0xFF)==0xBF) {
            String s = new String(data, 3, data.length-3, StandardCharsets.UTF_8);
            if (looksLikeCleanText(s)) return new Pair<>(s, StandardCharsets.UTF_8);
        }
        // UTF-16BE BOM
        if (data.length >= 2
         && (data[0]&0xFF)==0xFE && (data[1]&0xFF)==0xFF) {
            String s = new String(data, 2, data.length-2, StandardCharsets.UTF_16BE);
            if (looksLikeCleanText(s)) return new Pair<>(s, StandardCharsets.UTF_16BE);
        }
        // UTF-16LE BOM
        if (data.length >= 2
         && (data[0]&0xFF)==0xFF && (data[1]&0xFF)==0xFE) {
            String s = new String(data, 2, data.length-2, StandardCharsets.UTF_16LE);
            if (looksLikeCleanText(s)) return new Pair<>(s, StandardCharsets.UTF_16LE);
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

        // Count zeroes in even and odd positions
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
        
        /*
         * If >25% of bytes are zero at the same offset (even or odd), we treat the input
         * as UTF-16BE (zeros at even indices) or UTF-16LE (zeros at odd indices).
         */
        
        Charset cs = looks16BE ? StandardCharsets.UTF_16BE : StandardCharsets.UTF_16LE;
        String s = new String(data, cs);
        return looksLikeCleanText(s) ? new Pair<>(s, cs) : null;
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
                return new Pair<>(s, StandardCharsets.UTF_8);
            }
        } catch (CharacterCodingException ignored){}
        return null;
    }
    
    static List<Charset> candidates = Arrays.asList(
        // order matters only if tie in scoring
		Charset.forName("MacRoman"),       // Classic Mac OS Roman (legacy Mac text)
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
		,Charset.forName("windows-1258")    // viet
		
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
      if (csName.equals("windows-1258"))  return Script.EASTERN;
      return Script.WESTERN;
    }
    
    static Set<String> ignored = new HashSet<>();
    
    static {    	
    	for (String detect : CharsetDetector.getAllDetectableCharsets()) {
    		boolean on = false;
	    	for (Charset cs : candidates) {
	    		if (cs.name().equalsIgnoreCase(detect)) {
	    			on = true;
	    		} else {
		    		for (String alias : cs.aliases()) {
                        if (alias.equalsIgnoreCase(detect)) {
                            on = true;
                            break;
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
     * True iff the data is composed only of:
     *   Printable ASCII (0x20-0x7E), or
     *   Half-width Katakana byte values (0xA1-0xDF),
     * and it contains at least two half-width Katakana byte.
     */
    private static boolean looksLikeAsciiOrHalfwidthKatakana(byte[] data) {
        int hwkCount = 0;
        for (byte bb : data) {
            int b = bb & 0xFF;
            if (b >= 0x20 && b <= 0x7E) {
                // printable ASCII
            } else if (b >= 0xA1 && b <= 0xDF) {
                // half-width Katakana
                hwkCount++;
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

        int pairs = n / 2;
        int doubleSeqCount = 0;
        for (int i = 0; i + 1 < n; i++) {
            int b1 = data[i]   & 0xFF;
            int b2 = data[i+1] & 0xFF;
            boolean leadOK = isSjisLead(b1);
            boolean trailOK = isSjisTrail(b2);
            if (leadOK && trailOK) {
                doubleSeqCount++;
                i++;  // skip the trail byte to avoid overlapping
            }
        }

        // now check ratio: doubleSeqCount/pairs >= percent/100
        return doubleSeqCount * 100 >= pairs * percent;
    }

    /**
     * True if at least {@code percent} of the non-ASCII code points are Japanese.
     * Unlike decodedHasJapaneseChars this scales with length instead of using an
     * absolute count, so a long mis-decode can't pass on two lucky kanji.
     */
    private static boolean decodedIsMostlyJapanese(String s, int percent) {
        long nonAscii = s.codePoints().filter(cp -> cp > 0x7F).count();
        if (nonAscii == 0) return false;
        long jp = s.codePoints().filter(cp ->
                        (isJapaneseCp(cp)))     // fullwidth forms + halfwidth katakana
                .count();
        return jp * 100 >= nonAscii * percent;
    }
    
    /**
     * True if every byte maps to a printable CP-1252 character (no controls or undefined),
     * and at least one byte is a CP-1252-only extension (0x80–0x9F printable slot).
     */
    private static boolean looksLikeWindows1252(byte[] data) {
        int sawWinExt = 0;
        for (byte bb : data) {
            int b = bb & 0xFF;
            if (b >= 0x20 && b <= 0x7E) {
                // ASCII printable
            } else if (b == 0x81 || b == 0x8D || b == 0x8F || b == 0x90 || b == 0x9D) {
            	//unused slots
            	return false;
            } else if (b >= 0xA0 && b <= 0xFF) {
                // Latin-1 printable (also CP-1252)
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
    	
	private static final Set<Integer> VIET_PRECOMPOSED = Set.of(
	    0x0110, // Đ
	    0x0111, // đ
	    0x01A0, // Ơ
	    0x01A1  // ơ
	);
    @Deprecated
	private static final Set<Integer> VIET_DIACRITICS = Set.of(
	    0x0300, // ◌̀ (grave)
	    0x0301, // ◌́ (acute)
	    0x0303, // ◌̃ (tilde)
	    0x0309, // ◌̉ (hook above)
	    0x0323, // ◌̣ (dot below)
	    0x031B  // ◌̛ (horn)
	);
	// simple vowel set
    @Deprecated
	private static final Set<Integer> LATIN_VOWELS = Set.of(
	    (int)'a',(int)'A',
	    (int)'e',(int)'E',
	    (int)'i',(int)'I',
	    (int)'o',(int)'O',
	    (int)'u',(int)'U',
	    (int)'y',(int)'Y'
	);
	
	private static final Pattern VIET_WORDS = Pattern.compile(
	    "\\b(?:không|yêu|nhớ|người|tình|trời|đời|đừng|có lẽ|đã|của|muốn|đêm|ngày|tôi)\\b",
	    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
    
    public static Pair<String, Charset> detectAndDecode(byte[] data, int minConfidence) {
        CharsetDetector detector = new CharsetDetector();
        detector.setText(data);
        if (data.length < 40) {
        	// for short strings we only consider the most common charsets
	        for (String detectNot : ignored) {
	        	detector.setDetectableCharset(detectNot, false);
            }
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
            String s = match[0].getString();
            Charset cs = Charset.forName(match[0].getName());
            // ICU is the only path in this class that does not validate its own output.
            // It will happily return C0 controls, unpaired surrogates, and Private Use
            // Area characters -- see isPua. Hold it to the same bar as the shortcuts.
            if (!isPrintableAndNoSurrogates(s) || containsPua(s)) {
                log.fine("icu4j rejected: " + cs.name() + " " + s);
                return null;
            }
            log.fine("icu4j: " + cs.name() + " " + confidence + "% for " + s);
            return new Pair<>(s, cs);
        } catch (Exception e) {
            return null;
        }
    }

    public static Pair<String, Charset> bestFitLegacyDecode(byte[] data, boolean western, Script preferScript) {
        log.finer("best fit");

        CandidateResult bestDecoded = null;
        int bestScore = Integer.MAX_VALUE;
        boolean tie = false;

        for (Charset cs : candidates) {
            CandidateResult cr = evaluateCandidate(data, cs, western);
            if (western && getScript(cs.name()) != Script.WESTERN) {
                // choose a penalty large enough to swing ties, but not so large
                // that a really bad asian decode beats a mediocre Western decode.
                cr.score += 8;
            }

            // File-level bias: nudge toward the script the rest of the file used.
            // Small -- it only decides genuine ties like the mostly-ASCII Big5 track,
            // and must never override a clean decode in a different script.
            if (preferScript != null && getScript(cs.name()) == preferScript) {
                cr.score -= 30;
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
        if (bestDecoded == null) {
        	log.severe("Decoding failed");
        	return new Pair<>("", null);
        }
        log.fine("Legacy winner is "+bestDecoded);
        
        return new Pair<>(bestDecoded.decoded, bestDecoded.cs);
    }

    /** Holds evaluation results for one charset candidate. */
    private static class CandidateResult {
        Charset cs;
        String decoded;
        int replCount = 0;//replacement chars
        int puaCount = 0;// Private Use Area count
        int ctrlCount = 0;
        int cyrCount = 0;
        int jpCount = 0;
        int hwKatakanaCount = 0;
        int asciiCount = 0;
        int extLatinCount = 0;
		int vietCount = 0;
		int wordCount = 0;
        boolean byteLevelValid;
        int score = 0;
        
        @Override
        public String toString() {
        	if (decoded.length() < 12)
        		return cs.name()+" '"+decoded+"' "+" replCount="+replCount+" puaCount="+puaCount+" ctrlCount="+ctrlCount+" cyrCount="+cyrCount+" jpCount="+jpCount+" hwKatananaCount="+hwKatakanaCount+" vCount="+vietCount+" words="+wordCount+" asciiCount="+asciiCount+" extLatinCount="+extLatinCount+" score="+score;
        	return cs.name()+" replCount="+replCount+" puaCount="+puaCount+" ctrlCount="+ctrlCount+" cyrCount="+cyrCount+" jpCount="+jpCount+" hwKatananaCount="+hwKatakanaCount+" vCount="+vietCount+" words="+wordCount+" asciiCount="+asciiCount+" extLatinCount="+extLatinCount+" score="+score;
        }
    }
    
    private static CandidateResult evaluateCandidate(byte[] data, Charset cs, boolean preferWestern) {
        CandidateResult result = new CandidateResult();
        result.cs = cs;
        String csName = cs.name().toLowerCase();
        Script script = getScript(cs.name());
        
        // Byte-level validity for Shift_JIS / EUC-JP
        if (csName.contains("shift_jis") || csName.contains("windows-31j")) {
            result.byteLevelValid = isValidShiftJis(data);
        } else if (csName.contains("euc-jp")) {
            result.byteLevelValid = isValidEucJp(data);
        } else {
            result.byteLevelValid = true;
        }

        // Decode in REPLACE mode
        String decoded = result.byteLevelValid
                       ? decodeWithReplace(data, cs)
                       : "";
        result.decoded = decoded;

        
        // Analyze decoded string
        int neutral = 0;        
        String replChar = "\uFFFD";
        int prevCp = 0;
        for (int i = 0; i < decoded.length(); ) {
            int cp = decoded.codePointAt(i);
            i += Character.charCount(cp);

            // replacement char
            if (cp == 0xFFFD) {
                result.replCount++;
                continue;
            }
            if (isPua(cp)) {
                result.puaCount++;
                continue;
            }
            if (cp == '\n' || cp == '\r' || cp == '\t') {
                neutral++;
                continue;
            }
            // control (except newline/tab)
            if (Character.isISOControl(cp)) {
            	result.ctrlCount++;
                continue;
            }
            // Cyrillic letters
            if (isCyrillicLetter(cp)) {
            	result.cyrCount++;
                continue;
            }
            // Japanese blocks
            if (isJapaneseCp(cp)) {
                if (cp >= 0xFF61 && cp <= 0xFF9F) result.hwKatakanaCount++;
                else result.jpCount++;
                continue;
            }
            if (cp == ' ') {
                neutral++;
                continue;
            }
            // ASCII
            if (cp >= 0x20 && cp <= 0x7E) {
            	result.asciiCount++;
                continue;
            }
            // Latin-1 Supplement
            if (cp >= 0xA0 && cp <= 0xFF) {
            	result.extLatinCount++;
                continue;
            }
            
            if (VIET_PRECOMPOSED.contains(cp)) {// || (VIET_DIACRITICS.contains(cp) && LATIN_VOWELS.contains(prevCp))
            	result.vietCount++;
            	continue;
	        }
            UnicodeBlock block = Character.UnicodeBlock.of(cp);
            
            if (script == Script.EASTERN 
                    && (block == UnicodeBlock.LATIN_EXTENDED_A || block == UnicodeBlock.COMBINING_DIACRITICAL_MARKS)) {
               neutral++;
               continue;
            }
            if (script == Script.EASTERN) {
                //System.out.println(block);
                // power_of_goodbye5.mid is the only file I have seen going into this condition.
                // output was: LATIN_EXTENDED_B
                // The midi has no lyrics, and the track-names ended up decoded as Cyrillic, which appears to be correct.
            }
            
            prevCp = cp;// should also be before other returns but will be mess
            // everything else we treat as "other" (counts below)
        }

        result.wordCount = 0;
        int wordMatches = 0;
        
        // Unified "junk vs. script-match" scoring
        int matchCount;
        switch (script) {
            case CYRILLIC:
                matchCount = result.cyrCount;
                break;
            case JAPANESE:
                // Half-width katakana is credited only alongside full-width kana or kanji.
                // On its own it is almost always Latin-1 accents or uppercase Cyrillic
                // (source bytes 0xA1-0xDF) mis-read as CP932. The genuine article is
                // caught earlier by looksLikeAsciiOrHalfwidthKatakana, which requires a
                // 60% majority rather than a single character.
                matchCount = result.jpCount > 0
                        ? result.jpCount + result.hwKatakanaCount
                        : 0;
                break;
            case CHINESE:
                matchCount = (int) decoded.codePoints()
                                          .filter(cp -> cp >= 0x4E00 && cp <= 0x9FFF)
                                          .count();
                break;
            case EASTERN:
            	if (result.vietCount == 0) {
            		matchCount = 0;
            	} else {
            		matchCount = result.vietCount + result.asciiCount;
            	}            	
            	break;
            default:
                matchCount = result.asciiCount;
                break;
        }

        int totalCp  = decoded.codePointCount(0, decoded.length());

        // treat ASCII (0x20-0x7F) as neutral for all non-Western scripts:
        if (script == Script.EASTERN) {
            neutral += result.extLatinCount;
            Matcher m = VIET_WORDS.matcher(Normalizer.normalize(decoded, Normalizer.Form.NFC));
        	while (m.find()) {
        	    wordMatches++;
        	}
        	if (wordMatches < 3) {
        		matchCount = -5000;
        	}
        	result.wordCount = wordMatches;
        } else if (script != Script.WESTERN) {
            neutral += result.asciiCount;
            if (script == Script.JAPANESE || script == Script.CHINESE) {
                neutral += result.extLatinCount;
            }
        } else {
            // Western still allows Latin-1 Supplement as neutral
            neutral += result.extLatinCount;
            if (totalCp > 7 && result.extLatinCount*4 > result.asciiCount*3) {
            	// too many ext latin chars to be normal western text
            	neutral /= 2;
            } else if (totalCp > 4 && result.extLatinCount >= result.asciiCount) {
            	neutral /= 2;
            }
        }
        int nonMatch = totalCp - matchCount - neutral;

        // weights
        int replWeight     = 200;  // per replacement
        int puaWeight      = 2000;  // same as replacement: PUA is a silent U+FFFD
        int ctrlWeight     = 20;   // per stray control
        int nonMatchWeight = 40;   // per code-point not in our target script
        int matchBonus     = -5;   // per in-script code-point
        int wordBonus      = -1000;   // per in-script word
        int tieBreaker     = script == Script.WESTERN  ? 0
                           : script == Script.CYRILLIC  ? (totalCp == 1?25:1)
                           : script == Script.JAPANESE  ? (totalCp == 1?26:2)
                           : script == Script.EASTERN ?  (totalCp == 1?27:3)
                           :                            (totalCp == 1?28:4);
        
        // --- charset-specific priority ---
        int scriptPriority = getScriptPriority(csName, script);
        
        int emptyPenalty = decoded.isEmpty()?2000:0;

        
        log.fine("\n"+csName+" score:"
        		+"\nreplace: "+(result.replCount * replWeight)
                +"\npua:     "+(result.puaCount * puaWeight)
        		+"\ncontrol: "+(result.ctrlCount * ctrlWeight)
        		+"\nnonMatch:"+(nonMatch * nonMatchWeight)
        		+"\ntie:     "+tieBreaker
        		+"\nprio:    "+scriptPriority
        		+"\nempty:   "+emptyPenalty
        		+"\nmatch:   "+(matchCount * matchBonus)+" "+(wordMatches  * wordBonus));
        log.finer("Total="+totalCp+" nonMatch="+nonMatch);
        int junkPenalty = result.replCount * replWeight
                + result.puaCount * puaWeight
                + result.ctrlCount * ctrlWeight
                + nonMatch * nonMatchWeight
                + emptyPenalty;

        result.score = junkPenalty
                  + matchCount * matchBonus
                  + wordMatches * wordBonus
                  + tieBreaker
                  + scriptPriority;
        log.fine(result.toString());
        return result;
    }

    private static int getWesternPriority(String csName) {
        // csName will be lowercase
        // lower is better; 0 means "top Western choice"
        return switch (csName) {
            case "iso-8859-1" -> 0;
            case "iso-8859-15" -> 1;
            case "windows-1250" -> 2;
            case "x-macroman", "macroman" -> 3;//jdk 24 (and likely 21) needs the 'x-'
            case "windows-1252" -> 4;
            default -> 10;
        };
    }
    
    public enum Script { WESTERN, CYRILLIC, JAPANESE, CHINESE, EASTERN }

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
          case EASTERN:
        	  return 0;
          default:
            return 10;
        }
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

    /** Byte-structure validity only. No judgment about whether the text is Japanese. */
    public static boolean isValidShiftJis(byte[] data) {
        return data.length > 0 && countInvalidShiftJisBytes(data) == 0;
    }

    /** Structure + evidence that the content really is Japanese. Gate for the fast path. */
    private static boolean looksLikeJapaneseSjis(byte[] data) {
        if (!isValidShiftJis(data)) return false;

        // Full-width kana is the discriminator. Half-width katakana (0xFF61-0xFF9F)
        // is deliberately NOT accepted here: its source bytes are 0xA1-0xDF, which is
        // also uppercase Cyrillic in windows-1251, so "ПЕСНЯ" decodes to plausible
        // half-width katakana under CP932. That case belongs to the dedicated
        // looksLikeAsciiOrHalfwidthKatakana + decodedHasMajorityHalfwidthKatakana path,
        // which additionally requires a majority ratio.
        if (hasShiftJisHiragana(data) || hasShiftJisFullwidthKatakana(data)) return true;

        String decoded = decodeWithReplace(data, Charset.forName("windows-31j"));

        // Kana also lives in the 0x81 punctuation row: ー(0x815B), ・(0x8145), ヽ(0x8152).
        if (decoded.codePoints().anyMatch(cp -> 0x3040 <= cp && cp <= 0x30FF)) return true;

        // Kanji- and fullwidth-only text (尺八独奏, ３×３ＥＹＥＳ, 第１楽章：序曲) has no
        // kana at all, so the checks above miss it. Accept it, but narrowly:
        //
        //  - No ASCII. "don't can't" is 64 6F 6E 92 74 20 63 61 6E 92 74; both 92 74
        //    pairs are valid CP932 and decode to 猪. Mixed text goes to the scorer.
        //  - No half-width katakana. Source bytes 0xA1-0xDF are uppercase Cyrillic and
        //    the Latin-1 accents.
        //  - At least three. Two is what a 4-byte lowercase Cyrillic word (поле,
        //    EF EE EB E5) collapses to.
        //  - No ASCII letters/digits once padding is stripped. "don't can't" has
        //    interior ASCII; a space-padded track name "石川羚子      " does not.
        //    MIDI names are routinely padded to fixed width, so trailing/leading
        //    spaces must not disqualify.
        String core = decoded.strip();
        if (core.codePoints().anyMatch(cp -> cp < 0x80 && cp != ' ')) return false;
        if (core.codePoints().anyMatch(cp -> 0xFF61 <= cp && cp <= 0xFF9F)) return false;
        return core.codePoints().filter(CharsetDetectAndDecode::isJapaneseCp).count() >= 3;
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
    @Deprecated
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
    @Deprecated
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
            if (isSjisLead(b)) {
                if (i + 1 < data.length && isSjisTrail(data[i+1] & 0xFF)) {
                    i += 2;
                    continue;
                }
                invalid++;
            } else if (b <= 0x7F || (0xA1 <= b && b <= 0xDF)) {
                // ok: ASCII or half-width katakana
            } else {
                invalid++;
            }
            i++;
        }
        return invalid;
    }

    /** Check if decoded string has at least one Japanese character. */
    @Deprecated
    private static boolean decodedHasJapaneseChars(String s, int number) {
	    long count = s.codePoints().filter(cp ->
	        (cp >= 0x3040 && cp <= 0x30FF) ||  // full-width Hiragana/Katakana
	        (cp >= 0x4E00 && cp <= 0x9FFF)  // Kanji
	    ).count();
	    return count >= number;
	}

    @Deprecated
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

    /** Byte-structure validity only. */
    public static boolean isValidEucJp(byte[] data) {
        return data.length > 0 && countInvalidEucJpBytes(data) == 0;
    }

    /** Structure + evidence the content really is Japanese. Gate for the fast path. */
    private static boolean looksLikeJapaneseEucJp(byte[] data) {
        if (!isValidEucJp(data)) return false;

        int len = data.length;

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
                return decodedIsMostlyJapanese(decoded, 60);
            }
            return false;
        }

        // Otherwise require at least one kana multi-byte (hiragana or full-width katakana)
        if (info.kanaMulti < 1) {
            return false;
        }
        // decode and confirm presence of Japanese script
        String decoded = decodeWithReplace(data, Charset.forName("EUC-JP"));
        return decodedIsMostlyJapanese(decoded, 60);
    }
    
    private static boolean isPrintableAndNoSurrogates(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // C0 controls other than \r\n\t?
            if (c < 0x20 && c!='\n' && c!='\r' && c!='\t') return false;
            // Unpaired surrogates
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= s.length() || !Character.isLowSurrogate(s.charAt(i + 1))) return false;
                i++;
            } else if (Character.isSurrogate(c)) return false;
        }
        return true;
    }

    /**
     * CP932 lead bytes.
     *
     * The formal range is 0x81-0x9F and 0xE0-0xFC. We accept 0xFA-0xFC (the NEC-selected
     * IBM extension rows: ㈱, №, ℡ and friends, which do occur in copyright strings) but
     * deliberately reject 0xF0-0xF9, the gaiji / user-defined rows. Those map to the
     * Private Use Area (see isPua) and would otherwise decode "successfully".
     *
     * Rejecting 0xF0-0xF9 has a useful side effect: windows-1251 lowercase р-я is
     * 0xF0-0xFF, so a lowercase Cyrillic string can never be structurally valid here.
     */
    private static boolean isSjisLead(int b) {
        return (0x81 <= b && b <= 0x9F)
                || (0xE0 <= b && b <= 0xEF)
                || (0xFA <= b && b <= 0xFC);   // NEC-selected IBM extensions
    }

    private static boolean isSjisTrail(int b) {
        return (0x40 <= b && b <= 0x7E) || (0x80 <= b && b <= 0xFC);
    }

    /**
     * Code points that constitute positive evidence of Japanese text.
     *
     * Deliberately wider than the kana+kanji blocks: CP932 and EUC-JP routinely emit
     * fullwidth forms and CJK punctuation (、。「」〜　！？（）), and no Western
     * single-byte codepage can produce them, so they are strong positive evidence
     * rather than noise. Scoring them as nonMatch cost the correct decode 40 points
     * apiece and could lose real Japanese to Latin-1.
     */
    private static boolean isJapaneseCp(int cp) {
        return (cp >= 0x3000 && cp <= 0x303F)   // CJK punctuation 、。「」〜　
                || (cp >= 0x3040 && cp <= 0x309F)   // hiragana
                || (cp >= 0x30A0 && cp <= 0x30FF)   // katakana
                || (cp >= 0x3200 && cp <= 0x33FF)   // enclosed CJK / compatibility ㈱ ㍉
                || (cp >= 0x4E00 && cp <= 0x9FFF)   // CJK unified ideographs
                || (cp >= 0xF900 && cp <= 0xFAFF)   // CJK compatibility ideographs (IBM ext)
                || (cp >= 0xFF01 && cp <= 0xFF5E)   // fullwidth forms
                || (cp >= 0xFF61 && cp <= 0xFF9F)   // halfwidth katakana
                || (cp >= 0xFFE0 && cp <= 0xFFE6);  // fullwidth signs ￥￦
    }

    /**
     * Private Use Area: BMP (U+E000-U+F8FF) plus supplementary planes 15 and 16.
     *
     * PUA code points are the quiet failure mode of this class. Several candidate
     * charsets map byte ranges into the PUA rather than rejecting them, so the decode
     * "succeeds": no exception under CodingErrorAction.REPORT, no U+FFFD under REPLACE.
     * Every downstream check that keys off replacement characters or printability is
     * therefore blind to them. Known sources:
     *
     *   windows-31j  0xF040-0xF9FC  -> U+E000-U+E757  (gaiji, user-defined characters)
     *   x-MacRoman   0xF0           -> U+F8FF         (Apple logo)
     *
     * None of these can appear in a MIDI track name, lyric, or copyright string that
     * was authored on purpose. Their presence means we picked the wrong charset, so
     * they are weighted like replacement characters in scoring and rejected outright
     * in the fast paths.
     */
    private static boolean isPua(int cp) {
        return (cp >= 0xE000 && cp <= 0xF8FF)
                || (cp >= 0xF0000 && cp <= 0xFFFFD)
                || (cp >= 0x100000 && cp <= 0x10FFFD);
    }

    /** True if the string contains any Private Use Area code point. */
    private static boolean containsPua(String s) {
        return s.codePoints().anyMatch(CharsetDetectAndDecode::isPua);
    }

    /** A decode that is clean enough to accept without scoring: printable, properly
     *  paired surrogates, no replacement characters, no Private Use Area. */
    private static boolean looksLikeCleanText(String s) {
        return isPrintableAndNoSurrogates(s)
                && s.indexOf('\uFFFD') < 0
                && !containsPua(s);
    }

    private static Script voteScript(List<byte[]> blobs, boolean western) {
        Map<Script, Integer> votes = new EnumMap<>(Script.class);
        for (byte[] blob : blobs) {
            if (blob == null || blob.length == 0) continue;
            Pair<String, Charset> p = decodeMidiData(blob, western);
            if (p.second == null) continue;
            Script s = getScript(p.second.name());

            // Weight the vote by how much non-ASCII, script-specific evidence the blob
            // carried. A pure-ASCII decode contributes nothing; a blob full of Han or
            // Cyrillic contributes a lot. This is what stops the ambiguous tracks from
            // voting for themselves.
            int weight = scriptEvidence(p.first, s);
            if (weight > 0) votes.merge(s, weight, Integer::sum);
            //System.out.println("vote: " + p.second.name() + " ev=" + weight + " :: " + p.first);
        }
        if (votes.isEmpty()) return null;   // was Script.WESTERN; null = "no bias"

        var ranked = votes.entrySet().stream()
                .sorted(Map.Entry.<Script,Integer>comparingByValue().reversed())
                .toList();

        int top = ranked.get(0).getValue();
        int second = ranked.size() > 1 ? ranked.get(1).getValue() : 0;

        // Need real, dominant evidence. A handful of CJK from one possibly-mis-decoded
        // track is not enough to override every other track in the file.
        if (top < 8) return null;              // absolute floor
        if (second * 2 >= top) return null;    // mixed / contested

        return ranked.get(0).getKey();
    }

    /** Count of code points that are positive evidence for the given script. */
    /*
    private static int scriptEvidence(String s, Script script) {
        return switch (script) {
            case CHINESE  -> (int) s.codePoints().filter(cp -> cp >= 0x4E00 && cp <= 0x9FFF).count();
            case JAPANESE -> (int) s.codePoints().filter(CharsetDetectAndDecode::isJapaneseCp).count();
            case CYRILLIC -> (int) s.codePoints().filter(CharsetDetectAndDecode::isCyrillicLetter).count();
            case EASTERN  -> (int) s.codePoints().filter(VIET_PRECOMPOSED::contains).count();
            default       -> 0;   // WESTERN carries no discriminating evidence
        };
    }
    */
    /** Count of code points that are STRONG positive evidence for the given script.
     *  Half-width katakana is deliberately excluded from JAPANESE: its bytes 0xA1-0xDF
     *  are also Cyrillic, Latin-1 accents, and Big5 trail bytes, so it is the weakest
     *  possible signal and mis-decodes routinely produce it. Requiring full-width kana
     *  or kanji keeps garbage tracks from voting. */
    private static int scriptEvidence(String s, Script script) {
        return switch (script) {
            case CHINESE  -> (int) s.codePoints().filter(cp -> cp >= 0x4E00 && cp <= 0x9FFF).count();
            case JAPANESE -> (int) s.codePoints().filter(cp ->
                    (cp >= 0x3040 && cp <= 0x30FF)     // full-width kana only
                            || (cp >= 0x4E00 && cp <= 0x9FFF)).count();  // kanji
            case CYRILLIC -> (int) s.codePoints().filter(CharsetDetectAndDecode::isCyrillicLetter).count();
            case EASTERN  -> (int) s.codePoints().filter(VIET_PRECOMPOSED::contains).count();
            default       -> 0;
        };
    }
}
