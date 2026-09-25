package com.digero.maestro.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.ParseException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.digero.common.midi.KeySignature;
import com.digero.common.midi.TimeSignature;

public class ExportSettingsFormatterTest {
    private final TimeSignatureFormatter timeSignatureFormatter = new TimeSignatureFormatter();
    private final KeySignatureFormatter keySignatureFormatter = new KeySignatureFormatter();

    static Stream<Arguments> timeSignatures() {
        return Stream.of(
                Arguments.of("4/4", "4/4"),
                Arguments.of("6:8", "6/8"),
                Arguments.of("C", "4/4"),
                Arguments.of("C|", "2/2"));
    }

    @ParameterizedTest
    @MethodSource("timeSignatures")
    public void timeSignatureParsingAndFormattingRoundTrips(String input, String expected) throws ParseException {
        TimeSignature value = assertInstanceOf(TimeSignature.class, timeSignatureFormatter.stringToValue(input));

        assertEquals(expected, timeSignatureFormatter.valueToString(value));
    }

    static Stream<Arguments> keySignatures() {
        return Stream.of(
                Arguments.of("C maj", "C maj"),
                Arguments.of("Eb major", "Eb maj"),
                Arguments.of("F# min", "F# min"),
                Arguments.of("Fs minor", "F# min"));
    }

    @ParameterizedTest
    @MethodSource("keySignatures")
    public void keySignatureParsingAndFormattingRoundTrips(String input, String expected) throws ParseException {
        KeySignature value = assertInstanceOf(KeySignature.class, keySignatureFormatter.stringToValue(input));

        assertEquals(expected, keySignatureFormatter.valueToString(value));
    }

    @Test
    public void formattersRenderNullValuesAsBlank() throws ParseException {
        assertEquals("", timeSignatureFormatter.valueToString(null));
        assertEquals("", keySignatureFormatter.valueToString(null));
    }

    @Test
    public void formattersRejectInvalidTextAndValueTypesWithParseException() {
        assertThrows(ParseException.class, () -> timeSignatureFormatter.stringToValue("5/3"));
        assertThrows(ParseException.class, () -> keySignatureFormatter.stringToValue("H major"));
        assertThrows(ParseException.class, () -> timeSignatureFormatter.valueToString(KeySignature.C_MAJOR));
        assertThrows(ParseException.class, () -> keySignatureFormatter.valueToString(TimeSignature.FOUR_FOUR));
    }
}