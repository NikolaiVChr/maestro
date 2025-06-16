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
}
