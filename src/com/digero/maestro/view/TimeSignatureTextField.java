package com.digero.maestro.view;

import com.digero.common.midi.TimeSignature;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

class TimeSignatureTextField extends MyFormattedTextField {
    public TimeSignatureTextField(Object value, int columns) {
        super(new TimeSignatureFormatter());
        setValue(value);
        setColumns(columns);
        KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        Object actionName = getInputMap().get(enterKey);
        if (actionName != null) {
            Action commitOrRevert = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JFormattedTextField field = (JFormattedTextField) e.getSource();
                    try {
                        // Try to commit the value
                        field.commitEdit();
                    } catch (java.text.ParseException pe) {
                        // invalid text, revert to last valid value
                        SwingUtilities.invokeLater(() -> {
                            field.setValue(field.getValue());
                        });
                    }
                }
            };
            getActionMap().put(actionName, commitOrRevert);
        }
    }

    public static class TimeSignatureFormatter extends JFormattedTextField.AbstractFormatter {

        @Override
        public Object stringToValue(String text) throws java.text.ParseException {
            if (text == null || text.trim().isEmpty()) {
                throw new java.text.ParseException("Time signature cannot be empty", 0);
            }

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
            if (value == null) {
                return "";
            }
            if (value instanceof TimeSignature) {
                return value.toString();
            }
            throw new java.text.ParseException("Not a TimeSignature object", 0);
        }
    }
}
