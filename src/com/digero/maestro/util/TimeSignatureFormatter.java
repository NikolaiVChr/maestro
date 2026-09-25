package com.digero.maestro.util;

import javax.swing.JFormattedTextField.AbstractFormatter;

import com.digero.common.midi.TimeSignature;

public class TimeSignatureFormatter extends AbstractFormatter {

    @Override
    public Object stringToValue(String text) throws java.text.ParseException {
        if (text == null || text.trim().isEmpty())
            throw new java.text.ParseException("Time signature cannot be empty", 0);

        try {
            return new TimeSignature(text, true);
        } catch (Throwable e) {
            // very important that the throwable is converted to ParseException
            // for the field to work properly.
            throw new java.text.ParseException("Invalid format: " + e.getMessage(), 0);
        }
    }

    @Override
    public String valueToString(Object value) throws java.text.ParseException {
        if (value == null)
            return "";
        
        if (!(value instanceof TimeSignature))
            throw new java.text.ParseException("Not a TimeSignature object", 0);

        return value.toString();
    }
}
