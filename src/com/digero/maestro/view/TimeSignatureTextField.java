package com.digero.maestro.view;

import com.digero.maestro.util.TimeSignatureFormatter;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

@Deprecated(forRemoval = true)
class TimeSignatureTextField extends MyFormattedTextField {
    @Deprecated(forRemoval = true)
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
}
