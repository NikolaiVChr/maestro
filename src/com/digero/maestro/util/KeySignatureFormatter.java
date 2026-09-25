package com.digero.maestro.util;

import javax.swing.JFormattedTextField.AbstractFormatter;

import com.digero.common.midi.KeySignature;

public class KeySignatureFormatter extends AbstractFormatter {

    @Override
    public KeySignature stringToValue(String text) throws java.text.ParseException {
        try {
            return new KeySignature(text);
        } catch (Throwable e) {
            throw new java.text.ParseException("Invalid format: " + e.getMessage(), 0);
        }
    }

    @Override
    public String valueToString(Object value) throws java.text.ParseException {
        if (value == null)
            return "";
        if (!(value instanceof KeySignature))
            throw new java.text.ParseException("Invalid value type: " + value.getClass().getName(), 0);
        return value.toString();
    }
}
