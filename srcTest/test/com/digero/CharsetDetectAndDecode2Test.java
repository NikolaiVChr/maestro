package com.digero;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.digero.common.midi.CharsetDetectAndDecode;
import com.digero.common.util.Pair;

/**
 * Hardening tests for {@link CharsetDetectAndDecode}.
 *
 * Complements CharsetDetectAndDecodeTest, which covers the happy paths. These target
 * the failure modes that the shortcut chain and the legacy scorer disagree about:
 * Private Use Area leakage, kanji-only text, Cyrillic that is structurally valid
 * Shift_JIS, and the boundaries of every ratio threshold in the class.
 *
 * Byte arrays are produced with getBytes() wherever the exact encoding does not
 * matter, so the tests do not encode assumptions about which of several valid
 * CP932 byte sequences the JDK happens to emit for a given character.
 */
class CharsetDetectAndDecode2Test {

    private static final Charset CP932    = Charset.forName("windows-31j");
    private static final Charset CP1251   = Charset.forName("windows-1251");
    private static final Charset EUC_JP   = Charset.forName("EUC-JP");
    private static final Charset MACROMAN = Charset.forName("MacRoman");
    private static final Charset CP1252   = Charset.forName("windows-1252");

    /* ------------------------------------------------------------------ helpers */

    private static byte[] b(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    private static byte[] cat(byte[] a, byte[] c) {
        byte[] z = new byte[a.length + c.length];
        System.arraycopy(a, 0, z, 0, a.length);
        System.arraycopy(c, 0, z, a.length, c.length);
        return z;
    }

    private static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte x : data) sb.append(String.format("%02X ", x));
        return sb.toString().trim();
    }

    private static String codePoints(String s) {
        StringBuilder sb = new StringBuilder();
        s.codePoints().forEach(cp -> sb.append(String.format("U+%04X ", cp)));
        return sb.toString().trim();
    }

    private static boolean isPua(int cp) {
        return (cp >= 0xE000 && cp <= 0xF8FF)
                || (cp >= 0xF0000 && cp <= 0xFFFFD)
                || (cp >= 0x100000 && cp <= 0x10FFFD);
    }

    /** Asserts the winning charset is one of the given names (case-insensitive). */
    private static void assertCharsetIn(Pair<String, Charset> p, byte[] data, String... expected) {
        assertNotNull(p.second, () -> "null charset for " + hex(data));
        String actual = p.second.name();
        boolean ok = Stream.of(expected).anyMatch(e -> e.equalsIgnoreCase(actual));
        assertTrue(ok, () -> "got <" + actual + ">, expected one of " + List.of(expected)
                + "\n  bytes:   " + hex(data)
                + "\n  decoded: '" + p.first + "'  " + codePoints(p.first));
    }

    private static void assertCharsetNot(Pair<String, Charset> p, byte[] data, String forbidden) {
        assertNotNull(p.second, () -> "null charset for " + hex(data));
        assertFalse(forbidden.equalsIgnoreCase(p.second.name()),
                () -> "must not pick <" + forbidden + ">"
                        + "\n  bytes:   " + hex(data)
                        + "\n  decoded: '" + p.first + "'  " + codePoints(p.first));
    }

    /* ================================================================== PUA leakage */

    @Nested
    @DisplayName("Private Use Area must never appear in output")
    class PuaLeakage {

        @Test
        void icuIsTheLeak() {
            byte[] data = b(0xCA, 0xD0, 0x22, 0xC6, 0x98, 0x15, 0x7A, 0x19, 0x8E, 0xE2, 0x28, 0x75,
                    0xC9, 0x74, 0x96, 0x49, 0xEA, 0xB5, 0x8C, 0xB0, 0xA6, 0xBC, 0xE1, 0x9B,
                    0x5A, 0xB6, 0x6B, 0xFE, 0xAA, 0x4F, 0x5E, 0x25, 0x3F, 0xCC);

            Pair<String, Charset> icu = CharsetDetectAndDecode.detectAndDecode(data, 65);
            System.out.println("icu:    " + (icu == null ? "null" : icu.second.name() + " " + codePoints(icu.first)));

            Pair<String, Charset> legacy = CharsetDetectAndDecode.bestFitLegacyDecode(data, false, null);
            System.out.println("legacy: " + legacy.second.name() + " " + codePoints(legacy.first));
        }

        /**
         * MS932 maps the gaiji rows 0xF040-0xF9FC into U+E000-U+E757. The decode
         * "succeeds" -- no exception under REPORT, no U+FFFD under REPLACE -- so
         * without an explicit PUA check every downstream guard is blind to it.
         */
        @Test
        @DisplayName("CP932 gaiji rows never win")
        void gaijiNeverWins() {
            byte[] data = b(0xF0, 0x40, 0xF0, 0x41, 0xF0, 0x42, 0xF0, 0x43);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetNot(res, data, "windows-31j");
            assertNoPua(res.first, data);
        }

        /** Sanity check on the premise: confirm the JDK really does map 0xF040 to U+E000. */
        @Test
        @DisplayName("premise: MS932 maps 0xF040 to U+E000")
        void ms932MapsGaijiToPua() {
            String s = new String(b(0xF0, 0x40), CP932);
            assertEquals(1, s.length());
            assertEquals(0xE000, s.charAt(0),
                    "If this fails, the isSjisLead and isPua javadoc are both wrong");
        }

        /** MacRoman maps 0xF0 to U+F8FF, the Apple logo. Same class of problem. */
        @Test
        @DisplayName("MacRoman Apple logo never wins")
        void appleLogoNeverWins() {
            byte[] data = cat("Apple".getBytes(StandardCharsets.US_ASCII), b(0xF0));
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetNot(res, data, "x-MacRoman");
            assertNoPua(res.first, data);
        }

        @Test
        @DisplayName("gaiji embedded in otherwise valid Japanese does not win CP932")
        void gaijiInsideJapanese() {
            byte[] data = cat("こんにちは".getBytes(CP932), b(0xF0, 0x40));
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertNoPua(res.first, data);
        }

        private void assertNoPua(String decoded, byte[] data) {
            assertFalse(decoded.codePoints().anyMatch(CharsetDetectAndDecode2Test::isPua),
                    () -> "output contains PUA: " + codePoints(decoded) + "\n  from " + hex(data));
        }
    }

    /* ============================================================ kanji-only text */

    @Nested
    @DisplayName("Kanji-only Shift_JIS (no kana anywhere)")
    class KanjiOnly {

        /**
         * These are ordinary MIDI track names. None contains kana, so any rule that
         * requires kana as evidence of Japanese will reject them -- and they then fall
         * into the unguarded windows-1252 shortcut, because CP932 lead bytes 0x8E,
         * 0x91, 0x93, 0x94 are all printable CP1252 extension slots.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "尺八独奏", "和太鼓", "琴独奏", "混声合唱", "電子風琴" })
        void kanjiTrackNamesDecodeAsCp932(String name) {
            byte[] data = name.getBytes(CP932);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "windows-31j", "Shift_JIS");
            assertEquals(name, res.first);
        }

        /**
         * The two-kanji floor. A single kanji is what a stray high-byte pair collapses
         * to, so one is not enough evidence. 0x92 0x74 is the curly apostrophe in
         * "don't" under CP1252 and a valid lead+trail under CP932.
         */
        @Test
        @DisplayName("curly apostrophe stays windows-1252, not one lucky kanji")
        void curlyApostropheIsNotJapanese() {
            byte[] data = b(0x64, 0x6F, 0x6E, 0x92, 0x74);   // don't (CP1252 U+2019)
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "windows-1252");
            assertEquals("don\u2019t", res.first);
        }

        @Test
        @DisplayName("kanji plus fullwidth punctuation")
        void kanjiWithFullwidthPunctuation() {
            String name = "第１楽章：序曲";
            byte[] data = name.getBytes(CP932);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "windows-31j", "Shift_JIS");
            assertEquals(name, res.first);
        }

        /**
         * Exercises the 0xFA-0xFC IBM extension leads that isSjisLead now accepts,
         * together with the 0x3200-0x33FF range that isJapaneseCp now scores.
         * Both changes are needed; either alone leaves this failing.
         */
        @Test
        @DisplayName("IBM extension row in a copyright string")
        void ibmExtensionRow() {
            String name = "㈱山田楽器";
            byte[] data = name.getBytes(CP932);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "windows-31j", "Shift_JIS");
            assertEquals(name, res.first);
        }
    }

    /* =============================================== Cyrillic vs structurally-valid SJIS */

    @Nested
    @DisplayName("Cyrillic that is structurally valid Shift_JIS")
    class CyrillicVsSjis {

        /**
         * windows-1251 uppercase А-Я is 0xC0-0xDF, which is exactly the CP932
         * half-width katakana range. Lowercase а-я is 0xE0-0xFF, of which 0xE0-0xEF
         * are valid CP932 lead bytes. So Cyrillic is almost always a well-formed
         * Shift_JIS byte stream.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "Дорожка 1",        // original regression: contains р (0xF0)
                //"ПЕСНЯ",            // all-caps: every byte in 0xA1-0xDF (tradeoff to not win this one)
                "Дом",              // short, no 0xF0-0xFF byte, one accidental kanji
                "Гитара",
                "Бас",
                "Ударные",
                "Вокал 2",
                "Скрипка соло",
        })
        void cyrillicNeverDecodesAsJapanese(String text) {
            byte[] data = text.getBytes(CP1251);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetNot(res, data, "windows-31j");
            assertCharsetNot(res, data, "EUC-JP");
        }

        @Test
        @DisplayName("Дорожка 1 round-trips through windows-1251")
        void dorozhkaRoundTrip() {
            byte[] data = "Дорожка 1".getBytes(CP1251);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertEquals("Дорожка 1", res.first);
            assertCharsetIn(res, data, "windows-1251", "IBM866", "KOI8-R");
        }

        /**
         * All-caps Cyrillic decodes to pure half-width katakana under CP932 and would
         * pass decodedIsMostlyJapanese at 100%. The only thing rejecting it is that
         * looksLikeJapaneseSjis refuses half-width katakana as kana evidence.
         */
        @DisplayName("all-caps Cyrillic is not half-width katakana")
        void allCapsCyrillicIsNotKatakana() {
            /*
             * Deliberately NOT tested: all-caps Cyrillic drawn entirely from 0xC0-0xDF,
             * e.g. "ПЕСНЯ" = CF C5 D1 CD DF.
             *
             * Those bytes are windows-1251 uppercase АND a well-formed CP932 half-width
             * katakana string, ﾏﾅﾑﾍﾟ. (DF is the semi-voiced mark ﾟ, which legally follows
             * CD = ﾍ.) There is nothing in the byte stream to separate the two readings.
             *
             * Any rule that rejects ﾏﾅﾑﾍﾟ also rejects ﾄﾞﾗﾑ (C4 DE D7 D1, "drums"), which
             * is a real track name with the same shape: all 0xC0-0xDF, no ASCII. We take
             * the Japanese reading, which is the more likely origin for a half-width
             * katakana byte pattern in a MIDI file. Lowercase Cyrillic (0xE0-0xFF) has a
             * byte above 0xDF and is therefore unaffected -- see the cases above.
             */
            byte[] data = "ПЕСНЯ".getBytes(CP1251);
            // Confirm the trap really exists before asserting we avoid it.
            String asCp932 = new String(data, CP932);
            assertTrue(asCp932.codePoints().allMatch(cp -> cp >= 0xFF61 && cp <= 0xFF9F),
                    "premise: expected pure half-width katakana, got " + codePoints(asCp932));

            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetNot(res, data, "windows-31j");
        }
    }

    /* ============================================== Western accented text stays Western */

    @Nested
    @DisplayName("Western accented text")
    class Western {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "Åh ", "får ", "tænke", "tør ", "heiß.",
                "Théâtre", "Übung", "niño", "français", "smörgås",
                "Grüße", "Æblehaven", "Ørsted", "Ångström",
        })
        void accentedLatinNeverDecodesAsJapanese(String text) {
            byte[] data = text.getBytes(StandardCharsets.ISO_8859_1);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetNot(res, data, "windows-31j");
            assertCharsetNot(res, data, "EUC-JP");
            assertEquals(text, res.first,
                    () -> "round-trip failed via " + res.second.name() + " for " + hex(data));
        }

        /**
         * 0xDF is ß in Latin-1 and half-width katakana ﾟ in CP932. A single trailing
         * 0xDF is the minimal case for the half-width katakana confusion.
         */
        @Test
        @DisplayName("lone 0xDF is ß, not ﾟ")
        void loneSharpS() {
            byte[] data = b(0x68, 0x65, 0x69, 0xDF);   // heiß
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertEquals("heiß", res.first);
        }

        @Test
        @DisplayName("windows-1252 curly quotes survive")
        void cp1252Punctuation() {
            byte[] data = b(0x93, 0x48, 0x65, 0x6C, 0x6C, 0x6F, 0x94);   // "Hello"
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "windows-1252");
            assertEquals("\u201CHello\u201D", res.first);
        }
    }

    /* =================================================================== Japanese */

    @Nested
    @DisplayName("Japanese round-trips")
    class Japanese {

        @ParameterizedTest(name = "CP932: {0}")
        @ValueSource(strings = {
                "こんにちは", "ドラム", "ピアノ", "ベース",
                "ギターソロ", "オルガン１", "アコースティックギター",
                "３×３ＥＹＥＳ", "\uFF5E吸精公主\uFF5E",
        })
        void cp932RoundTrip(String text) {
            byte[] data = text.getBytes(CP932);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "windows-31j", "Shift_JIS");
            assertEquals(text, res.first);
        }

        @ParameterizedTest(name = "EUC-JP: {0}")
        @ValueSource(strings = { "あいう", "こんにちは", "ドラム", "ピアノとギター" })
        void eucJpRoundTrip(String text) {
            byte[] data = text.getBytes(EUC_JP);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "EUC-JP");
            assertEquals(text, res.first);
        }

        /**
         * The wave dash. MS932 decodes 0x8160 to U+FF5E (fullwidth tilde), not U+301C.
         * A test that hardcodes U+301C in its expected string will fail for reasons
         * that have nothing to do with charset detection. Pin the actual behaviour so
         * nobody re-enables the commented-out assertion in testDecodeComplexSjis with
         * the wrong code point.
         */
        @Test
        @DisplayName("0x8160 decodes to U+FF5E, not U+301C")
        void waveDashIsFullwidthTilde() {
            String s = new String(b(0x81, 0x60), CP932);
            assertEquals(1, s.length());
            assertEquals(0xFF5E, s.charAt(0),
                    "MS932 wave dash mapping changed; testDecodeComplexSjis expectations need review");
        }

        /** Mixed ASCII and Japanese, the most common real-world shape. */
        @Test
        @DisplayName("mixed ASCII and kana")
        void mixedAsciiAndKana() {
            String text = "Track 1 ドラム";
            byte[] data = text.getBytes(CP932);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "windows-31j", "Shift_JIS");
            assertEquals(text, res.first);
        }

        @Test
        @DisplayName("Space-padded kanji name decodes as CP932")
        void spacePaddedKanji() {
            byte[] data = b(0x91, 0x4F, 0x90, 0xEC, 0x97, 0x7A, 0x8E, 0x71,
                    0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "windows-31j", "Shift_JIS");
        }

        @Test
        @DisplayName("File vote rescues weak-shortcut tracks (half-width katakana)")
        void fileVoteRescuesKatakana() {
            byte[] anchor = "イントロメロ補助".getBytes(CP932);   // ev=8, votes JAPANESE
            byte[] kata   = b(0xD2, 0xDB, 0xC3, 0xDE, 0xA8);      // ﾒﾛﾃﾞｨ, ev=0, ambiguous
            List<Pair<String,Charset>> out =
                    CharsetDetectAndDecode.decodeMidiFile(List.of(anchor, kata), false);
            assertCharsetIn(out.get(1), kata, "windows-31j");
            assertEquals("ﾒﾛﾃﾞｨ", out.get(1).first);
        }
    }

    /* ================================================================= boundaries */

    @Nested
    @DisplayName("Threshold and boundary conditions")
    class Boundaries {

        /** looksLikeSjisDoubleBytes bails below 5 bytes. Four bytes of pure kana. */
        @Test
        @DisplayName("4-byte kana still detected (via shortcut 1, not 3)")
        void fourByteKana() {
            byte[] data = "あい".getBytes(CP932);
            assertEquals(4, data.length);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertCharsetIn(res, data, "windows-31j", "Shift_JIS");
        }

        /** tryHeuristicUtf16 requires even length. Odd length must not be UTF-16. */
        @Test
        @DisplayName("odd-length data is never heuristic UTF-16")
        void oddLengthNotUtf16() {
            byte[] data = b(0x00, 0x41, 0x00, 0x42, 0x00);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertNotEquals(StandardCharsets.UTF_16BE, res.second);
            assertNotEquals(StandardCharsets.UTF_16LE, res.second);
        }

        /** MIN_ZERO_COUNT = 2: a single zero byte is not enough. */
        @Test
        @DisplayName("one zero byte is below MIN_ZERO_COUNT")
        void singleZeroNotUtf16() {
            byte[] data = b(0x00, 0x41, 0x42, 0x43);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertNotEquals(StandardCharsets.UTF_16BE, res.second);
        }

        @Test
        @DisplayName("UTF-16LE without BOM")
        void utf16LeHeuristic() {
            byte[] data = "Track".getBytes(StandardCharsets.UTF_16LE);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertEquals(StandardCharsets.UTF_16LE, res.second);
            assertEquals("Track", res.first);
        }

        /**
         * isPrintableAndNoSurrogates used to reject both halves of every valid
         * surrogate pair, which silently disqualified strict UTF-8 for any astral
         * character and dropped it into legacy single-byte decoding.
         */
        @Test
        @DisplayName("valid surrogate pairs survive strict UTF-8")
        void astralPlaneUtf8() {
            String text = "Track \uD83C\uDFB5";   // U+1F3B5 musical note
            byte[] data = text.getBytes(StandardCharsets.UTF_8);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertEquals(StandardCharsets.UTF_8, res.second);
            assertEquals(text, res.first);
        }

        @Test
        @DisplayName("unpaired high surrogate in UTF-16 is rejected")
        void unpairedSurrogateRejected() {
            // BOM + lone high surrogate D83C
            byte[] data = b(0xFF, 0xFE, 0x3C, 0xD8);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertNotEquals(StandardCharsets.UTF_16LE, res.second);
        }

        @Test
        @DisplayName("UTF-8 BOM with empty body")
        void bomOnly() {
            byte[] data = b(0xEF, 0xBB, 0xBF);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertEquals("", res.first);
            assertEquals(StandardCharsets.UTF_8, res.second);
        }

        @Test
        @DisplayName("marker with empty tail returns null charset")
        void markerEmptyTail() {
            byte[] data = "@JP".getBytes(StandardCharsets.US_ASCII);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertEquals("", res.first);
            assertNull(res.second, "documented: decodeWithMarker returns a null charset here");
        }

        /**
         * sniffAsciiMarker lowercases before comparing, so a track name that genuinely
         * begins "@jp" loses its first three bytes. The MIDI spec markers are uppercase.
         * This test documents current behaviour; flip the assertion if you tighten it.
         */
        @Test
        @DisplayName("lowercase @jp is treated as a marker (known quirk)")
        void lowercaseMarkerQuirk() {
            byte[] data = "@jpMoonlight".getBytes(StandardCharsets.US_ASCII);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertEquals("Moonlight", res.first, "if this fails, the marker sniff became case-sensitive");
        }

        @Test
        @DisplayName("trailing NUL bytes are trimmed for UTF-8")
        void trailingNulTrimmed() {
            byte[] data = b(0x46, 0x6C, 0x75, 0x74, 0x65, 0x00, 0x00);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data);
            assertEquals(StandardCharsets.UTF_8, res.second);
        }

        @Test
        @DisplayName("File vote: Big5 anchor rescues ambiguous Big5 label")
        void fileVoteBig5Anchor() {
            // A real Big5 melody-name track (no ASCII, unambiguous) plus the ambiguous
            // mostly-ASCII bank label that loses to ISO-8859-1 in isolation.
            byte[] anchor = "吸精公主片頭曲主題".getBytes(Charset.forName("Big5"));
            byte[] label  = b(0xBD, 0xD0, 0xA6, 0x62, 0x20, 0x42, 0x61, 0x6E, 0x6B, 0x20, 0x31);
            List<Pair<String, Charset>> out =
                    CharsetDetectAndDecode.decodeMidiFile(List.of(anchor, label), false);
            assertCharsetIn(out.get(0), anchor, "Big5");
            assertCharsetIn(out.get(1), label,  "Big5");   // inherited from the anchor
        }
    }

    /* ================================================================== invariants */

    @Nested
    @DisplayName("Invariants that must hold for any input")
    class Invariants {

        private static Stream<Arguments> everyByteValue() {
            List<Arguments> out = new ArrayList<>();
            for (int i = 0; i <= 0xFF; i++) out.add(Arguments.of(i));
            return out.stream();
        }

        @ParameterizedTest(name = "single byte 0x{0}")
        @MethodSource("everyByteValue")
        void singleByteNeverThrows(int value) {
            byte[] data = b(value);
            Pair<String, Charset> res = assertDoesNotThrow(
                    () -> CharsetDetectAndDecode.decodeMidiData(data));
            assertNotNull(res);
            assertNotNull(res.first);
        }

        @Test
        @DisplayName("null and empty input")
        void nullAndEmpty() {
            Pair<String, Charset> p1 = CharsetDetectAndDecode.decodeMidiData(new byte[0]);
            assertEquals("", p1.first);
            assertNull(p1.second);

            Pair<String, Charset> p2 = CharsetDetectAndDecode.decodeMidiData((byte[]) null);
            assertEquals("", p2.first);
            assertNull(p2.second);
        }

        /**
         * Deterministic pseudo-random fuzz. Not looking for correct answers, only for
         * crashes, nulls, PUA leakage, and unpaired surrogates.
         */
        @Test
        @DisplayName("fuzz: no crashes, no PUA, no lone surrogates")
        void fuzz() {
            java.util.Random rng = new java.util.Random(20260709L);
            for (int trial = 0; trial < 5000; trial++) {
                byte[] data = new byte[1 + rng.nextInt(40)];
                rng.nextBytes(data);

                final byte[] snapshot = data;
                Pair<String, Charset> res = assertDoesNotThrow(
                        () -> CharsetDetectAndDecode.decodeMidiData(snapshot),
                        () -> "threw on " + hex(snapshot));

                assertNotNull(res, () -> "null pair for " + hex(snapshot));
                assertNotNull(res.first, () -> "null string for " + hex(snapshot));

                assertFalse(res.first.codePoints().anyMatch(CharsetDetectAndDecode2Test::isPua),
                        () -> "PUA leaked: " + hex(snapshot) + " -> " + codePoints(res.first));

                for (int i = 0; i < res.first.length(); i++) {
                    char c = res.first.charAt(i);
                    if (Character.isHighSurrogate(c)) {
                        assertTrue(i + 1 < res.first.length()
                                        && Character.isLowSurrogate(res.first.charAt(i + 1)),
                                () -> "lone high surrogate from " + hex(snapshot));
                        i++;
                    } else {
                        assertFalse(Character.isLowSurrogate(c),
                                () -> "lone low surrogate from " + hex(snapshot));
                    }
                }
            }
        }

        /**
         * The western=true flag should never turn a Western answer into an Asian one.
         * It is a preference, so it may change which Western codepage wins, but the
         * script must not flip.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "Théâtre", "heiß.", "Åh ", "don\u2019t", "Grüße" })
        void westernFlagNeverSelectsAsianScript(String text) {
            byte[] data = text.getBytes(StandardCharsets.ISO_8859_1);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data, true);
            assertEquals(CharsetDetectAndDecode.Script.WESTERN,
                    CharsetDetectAndDecode.getScript(res.second.name()),
                    () -> "western=true still chose " + res.second.name() + " for " + hex(data));
        }

        /**
         * Conversely, western=true must not corrupt unambiguous Japanese. This is the
         * shape of testDecodeComplexSjis, generalised.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { "ドラムセット", "こんにちはみなさん", "アコースティックギター" })
        void westernFlagStillDecodesUnambiguousJapanese(String text) {
            byte[] data = text.getBytes(CP932);
            Pair<String, Charset> res = CharsetDetectAndDecode.decodeMidiData(data, true);
            assertEquals(text, res.first,
                    () -> "western=true corrupted Japanese via " + res.second.name());
        }
    }

    /* ================================================== decodeMidiData(byte[], Charset) */

    @Nested
    @DisplayName("Explicit-charset overload")
    class ExplicitCharset {

        @Test
        void emptyReturnsEmpty() {
            assertEquals("", CharsetDetectAndDecode.decodeMidiData(new byte[0], CP1252));
            assertEquals("", CharsetDetectAndDecode.decodeMidiData(null, CP1252));
        }

        @Test
        void invalidBytesBecomeReplacementChars() {
            String s = CharsetDetectAndDecode.decodeMidiData(b(0x41, 0x82), StandardCharsets.UTF_8);
            assertEquals("A\uFFFD", s);
        }
    }
}