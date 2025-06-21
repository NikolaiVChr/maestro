package com.digero.test;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.digero.common.midi.CharsetDetectAndDecode;
import com.digero.common.midi.MidiUtils;
import com.digero.common.util.Pair;

/**
 * Unit-tests for {@link CharsetDetectAndDecode}.
 */
class CharsetDetectAndDecodeTest {

    /* ------------------------------------------------ helpers -- */

    private static byte[] cat(byte[] a, byte[] b) {
        byte[] z = new byte[a.length + b.length];
        System.arraycopy(a, 0, z, 0, a.length);
        System.arraycopy(b, 0, z, a.length, b.length);
        return z;
    }

    /** Utility that asserts the detected charset matches (ignoring aliases / case). */
    private static void assertCharset(Pair<String, Charset> p,
                                      Set<String> expectedNames) {
        String actual = p.second.name();
        boolean ok = expectedNames.stream()
                                  .map(String::toLowerCase)
                                  .anyMatch(actual.toLowerCase()::equals);

        assertTrue(ok,
            () -> "unexpected charset: <" + actual + "> — expected one of " + expectedNames+". Result codePoints="+MidiUtils.formatCodePoints(p.first));
    }

    /* ------------------------------------------- parameter source -- */
       
    private static Stream<Arguments> decodeCases() {
        /* ---- sample strings ---- */
        String latin  = "Théâtre";                // Western
        String cyril  = "Привет";                 // Cyrillic
        String jpSJIS = "こんにちは";               // Japanese (hiragana)
        String jpEUC  = "あいう";                   // Japanese hiragana, shorter
        String cnBig5 = "一丁七丈三上";                    // CJK - traditional

        return Stream.of(
            /* marker (@JP) ------------------------------------------------ */
            Arguments.of(
                "@JP marker",                               // description
                cat("@JP".getBytes(StandardCharsets.US_ASCII),
                    jpSJIS.getBytes(Charset.forName("windows-31j"))),
                jpSJIS,
                Set.of("windows-31j")                       // expected charset
            ),

            /* UTF-8 BOM --------------------------------------------------- */
            Arguments.of(
                "UTF-8 BOM",
                cat(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF},
                    latin.getBytes(StandardCharsets.UTF_8)),
                latin,
                Set.of(StandardCharsets.UTF_8.name())
            ),

            /* UTF-16LE BOM ----------------------------------------------- */
            Arguments.of(
                "UTF-16LE BOM",
                cat(new byte[]{(byte)0xFF,(byte)0xFE},
                    latin.getBytes(StandardCharsets.UTF_16LE)),
                latin,
                Set.of(StandardCharsets.UTF_16LE.name())
            ),

            /* UTF-16BE BOM ----------------------------------------------- */
            Arguments.of(
                "UTF-16BE BOM",
                cat(new byte[]{(byte)0xFE,(byte)0xFF},
                    latin.getBytes(StandardCharsets.UTF_16BE)),
                latin,
                Set.of(StandardCharsets.UTF_16BE.name())
            ),

            /* strict UTF-8 (no BOM) -------------------------------------- */
            Arguments.of(
                "strict UTF-8",
                cyril.getBytes(StandardCharsets.UTF_8),
                cyril,
                Set.of(StandardCharsets.UTF_8.name())
            ),

            /* Shift_JIS path --------------------------------------------- */
            Arguments.of(
                "Shift_JIS validator",
                jpSJIS.getBytes(Charset.forName("Shift_JIS")),
                jpSJIS,
                Set.of("Shift_JIS", "windows-31j")
            ),

            /* EUC-JP path ------------------------------------------------- */
            Arguments.of(
                "EUC-JP validator",
                jpEUC.getBytes(Charset.forName("EUC-JP")),
                jpEUC,
                Set.of("EUC-JP")
            ),

            /* windows-1251 / Cyrillic ------------------------------------- */
            Arguments.of(
                "windows-1251 Cyrillic",
                cyril.getBytes(Charset.forName("windows-1251")),
                cyril,
                Set.of("windows-1251")
            ),

            /* Big5 Chinese ----------------------------------------------- */
            /*
            Arguments.of(
                "Big5 Traditional Chinese",
                cnBig5.getBytes(Charset.forName("Big5")),
                cnBig5,
                Set.of("Big5")
            ),
            */
            /* Latin-1 legacy fallback ------------------------------------- */
            Arguments.of(
                "ISO-8859-1 fallback",
                latin.getBytes(StandardCharsets.ISO_8859_1),
                latin,
                Set.of("ISO-8859-1", "windows-1252")
            )
        );
    }

    /* ------------------------------------------- parameterised test -- */

    @ParameterizedTest(name = "{0} -> detected={3}")
    @MethodSource("decodeCases")
    void decodeVarious(String description, byte[] bytes,
                       String expectedText, Set<String> expectedCharsets) {

        Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(bytes);

        assertCharset(res, expectedCharsets);
        assertEquals(expectedText, res.first,
                () -> description +
                     " → decoded text mismatch; winner charset=<" +
                     res.second.name() + '>');
    }
    
    @Test
    @DisplayName("Legacy Western fallback keeps ASCII part intact")
    void legacyWestern() {
        String original = "Théâtre";                 // bytes: 54 68 E9 E2 74 72 65
        byte[] data = original.getBytes(StandardCharsets.ISO_8859_1);

        Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
        String txt = res.first;
        Charset cs  = res.second;

        // ASCII fragment must be preserved
        assertTrue(txt.startsWith("Th") && txt.endsWith("tre"),
                   () -> "decoded text should keep ASCII part: got <" + txt + '>');

        // Length will be 6 or 7 depending on whether both high-bytes collapsed to one “?”.
        assertTrue(txt.length() >= 6 && txt.length() <= 7);

        // Winning charset should be one of the Western single-byte fall-backs
        assertTrue(
            Set.of("x-MacRoman", "CP437", "CP850", "windows-1252", "ISO-8859-1")
               .contains(cs.name()),
            () -> "unexpected charset: " + cs.name()
        );
    }

    /* ------------------------------------------- single-edge cases ----- */

    @Test
    @DisplayName("@UTF-16LE marker is honoured")
    void markerUtf16Le() {
        String body = "Track A";                                 // U+202F thin NBSP to ensure BMP+surrogate handling
        byte[] payload = body.getBytes(StandardCharsets.UTF_16LE);
        byte[] data = cat("@UTF-16LE".getBytes(StandardCharsets.US_ASCII), payload);

        Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
        assertEquals(body, res.first);
        assertEquals(StandardCharsets.UTF_16LE, res.second);
    }

    @Test @DisplayName("Just below heuristic threshold")
    void utf16BelowThreshold() {
        byte[] mix = new byte[]{
            // 2 zero bytes, 8 non-zero bytes ⇒ 20% zeros
            0x00, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49
        };
        Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(mix);
        // Should NOT detect UTF-16BE
        assertNotEquals(StandardCharsets.UTF_16BE, res.second);
    }

    @Test @DisplayName("At heuristic threshold")
    void utf16AtThreshold() {
        byte[] mix = new byte[]{
            // 3 zeros, 7 non-zeros ⇒ 30% zeros
            0x00, 0x41, 0x00, 0x42, 0x00, 0x43, 0x44, 0x45, 0x46, 0x47
        };
        Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(mix);
        assertEquals(StandardCharsets.UTF_16BE, res.second);
    }

    @Test @DisplayName("Just above heuristic threshold")
    void utf16AboveThreshold() {
        byte[] mix = new byte[]{
            // 4 zeros, 6 non-zeros ⇒ ~40% zeros
            0x00, 0x41, 0x00, 0x42, 0x00, 0x43, 0x00, 0x44, 0x45, 0x46
        };
        Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(mix);
        assertEquals(StandardCharsets.UTF_16BE, res.second);
    }
    
    @Test @DisplayName("Empty byte[] returns empty String and null")
    void emptyInput() {
        Pair<String, Charset> p = CharsetDetectAndDecode.decodeMidiData(new byte[0]);
        assertEquals("", p.first);
        assertEquals(null, p.second);
    }
    
    @Test @DisplayName("Half width Shift JIS")
    void halfJis() {
    	byte[] seq = new byte[] {
		    (byte)0xA5, (byte)0x44, (byte)0xB1, (byte)0xDB,
		    (byte)0xAB, (byte)0xDF, (byte)0x28, (byte)0xB1,
		    (byte)0xC6, (byte)0xB2, (byte)0xC3, (byte)0x29
		};
        Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(seq);
        assertTrue(
                Set.of("Shift_JIS", "windows-31j")
                   .contains(res.second.name()),
                () -> "unexpected charset: " + res.second.name()
            );
    }
    
    @Test @DisplayName("Western with copyright")
    void western() {
    	// 	"Harmony © 1997 by Hosam Adeeb Nashed"
    	byte[] data = new byte[] {
	        72, 97, 114, 109, 111, 110, 121, 32,
	        (byte)0xA9, 32,
	        49, 57, 57, 55, 32,
	        98, 121, 32,
	        72, 111, 115, 97, 109, 32,
	        65, 100, 101, 101, 98, 32,
	        78, 97, 115, 104, 101, 100
	    };
	    Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
	    assertTrue(
            Set.of("windows-1252", "ISO-8859-1")
               .contains(res.second.name()),
            () -> "unexpected charset: " + res.second.name()
        );
    }
    
    @Test @DisplayName("UTF-8")
    void u8() {
	    // "Flute" + NUL terminator
	    byte[] data = new byte[] { 70, 108, 117, 116, 101, 0 };
	    Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
	    assertEquals(StandardCharsets.UTF_8, res.second, "Result was "+res.second.name());
	}
    
    @Test
    public void testAsciiKatakanaSlapBass_SniffAndDecode() {
        // bytes: C1 AC B6 CE DF BA 2D 31 28 53 6C 61 70 42 61 73 73 31 29 + padding
        byte[] data = new byte[] {
            (byte)0xC1, (byte)0xAC, (byte)0xB6, (byte)0xCE,
            (byte)0xDF, (byte)0xBA, (byte)0x2D, (byte)0x31,
            (byte)0x28, (byte)0x53, (byte)0x6C, (byte)0x61,
            (byte)0x70, (byte)0x42, (byte)0x61, (byte)0x73,
            (byte)0x73, (byte)0x31, (byte)0x29,
            // padding spaces
            32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
            32, 32, 32, 32, 32, 32, 32
        };

        // Should match ASCII+halfwidth Katakana
        //assertTrue(CharsetDetectAndDecode.looksLikeAsciiOrHalfwidthKatakana(data),
        //           "Expected ASCII-or-HW-Katakana sniff");

        // Decode through the full pipeline
        Pair<String, Charset> result = CharsetDetectAndDecode.decodeMidiData(data);

        // Should choose Windows-31J (CP932)
        assertEquals(Charset.forName("windows-31j"), result.second,
                     "Expected windows-31j for half-width Katakana");

        // And the decoded text
        String expected = "ﾁｬｶﾎﾟｺ-1(SlapBass1)";
        assertEquals(expected, result.first.trim(),
                     "Decoded string did not match expected Katakana + ASCII");
    }
    
    @Test
    void testDecodeMidiData() {
        // Case 1: "Åh "
        byte[] bytes1 = new byte[]{ (byte)0xC5, (byte)0x68, (byte)0x20 };
        Pair<String, Charset> result1 = CharsetDetectAndDecode.decodeMidiData(bytes1);
        Charset cs1 = result1.second;
        assertEquals("Åh ", result1.first,
            () -> "Expected 'åh ' but got '" + result1.first +
                  "'. Winning charset was " + cs1.name()+" data (hex): "+MidiUtils.formatBytesHexOnly(bytes1));

        // Case 2: "får "
        byte[] bytes2 = new byte[]{ (byte)0x66, (byte)0xE5, (byte)0x72, (byte)0x20 };
        Pair<String, Charset> result2 = CharsetDetectAndDecode.decodeMidiData(bytes2);
        Charset cs2 = result2.second;
        assertEquals("får ", result2.first,
            () -> "Expected 'får ' but got '" + result2.first +
            "'. Winning charset was " + cs2.name()+" data (hex): "+MidiUtils.formatBytesHexOnly(bytes1));

        // Case 3: "tænke"
        byte[] bytes3 = new byte[]{ (byte)0x74, (byte)0xE6, (byte)0x6E, (byte)0x6B, (byte)0x65 };
        Pair<String, Charset> result3 = CharsetDetectAndDecode.decodeMidiData(bytes3);
        Charset cs3 = result3.second;
        assertEquals("tænke", result3.first,
            () -> "Expected 'tænke' but got '" + result3.first +
            "'. Winning charset was " + cs3.name()+" data (hex): "+MidiUtils.formatBytesHexOnly(bytes1));
        
        // Case 4: "tør " 
        byte[] bytes4 = new byte[]{ (byte)0x74, (byte)0xF8, (byte)0x72, (byte)0x20 };
        Pair<String, Charset> result4 = CharsetDetectAndDecode.decodeMidiData(bytes4);
        Charset cs4 = result4.second;
        assertEquals("tør ", result4.first,
            () -> "Expected 'tør ' but got '" + result4.first +
            "'. Winning charset was " + cs4.name()+" data (hex): "+MidiUtils.formatBytesHexOnly(bytes1));
                
        // Case 2: "heiß." 
        byte[] bytes5 = new byte[]{ (byte)0x68, (byte)0x65, (byte)0x69, (byte)0xDF, (byte)0x2E };
        Pair<String, Charset> result5 = CharsetDetectAndDecode.decodeMidiData(bytes5);
        Charset cs5 = result5.second;
        assertEquals("heiß.", result5.first,
            () -> "Expected 'heiß. ' but got '" + result5.first +
            "'. Winning charset was " + cs5.name()+" data (hex): "+MidiUtils.formatBytesHexOnly(bytes1));
    }
    
    @Test
    void testDecodeMidiData_CyrillicTrack() {
        // bytes: C4 EE F0 EE E6 EA E0 20 31
        // expected under Windows-1251 → "Дорожка 1"
        byte[] data = new byte[]{
            (byte)0xC4, (byte)0xEE, (byte)0xF0, (byte)0xEE,
            (byte)0xE6, (byte)0xEA, (byte)0xE0, (byte)0x20,
            (byte)0x31
        };
        Pair<String, Charset> result = CharsetDetectAndDecode.decodeMidiData(data);
        Charset cs = result.second;
        assertEquals("Дорожка 1", result.first,
            () -> "Expected 'Дорожка 1' but got '" + result.first +
                  "'. Winning charset was " + cs.name());
    }
}
