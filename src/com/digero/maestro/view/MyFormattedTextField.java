package com.digero.maestro.view;

import javax.swing.*;
import java.awt.event.FocusEvent;

/**
 * Slight modification to JFormattedTextField to select the contents when it receives focus.
 */
class MyFormattedTextField extends JFormattedTextField {
    public MyFormattedTextField(Object value, int columns) {
        super(value);
        setColumns(columns);
    }

    public MyFormattedTextField(JFormattedTextField.AbstractFormatter formatter) {
        super(formatter);
    }

    @Override
    protected void processFocusEvent(FocusEvent e) {
        super.processFocusEvent(e);
        if (e.getID() == FocusEvent.FOCUS_GAINED)
            selectAll();
    }
}
