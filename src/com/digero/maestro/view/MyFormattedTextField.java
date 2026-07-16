package com.digero.maestro.view;

import javax.swing.*;
import java.awt.event.FocusEvent;

/**
 * Slight modification to JFormattedTextField to select the contents when it receives focus.
 */
@Deprecated(forRemoval = true)
class MyFormattedTextField extends JFormattedTextField {
    @Deprecated(forRemoval = true)
    public MyFormattedTextField(Object value, int columns) {
        super(value);
        setColumns(columns);
    }

    @Deprecated(forRemoval = true)
    public MyFormattedTextField(JFormattedTextField.AbstractFormatter formatter) {
        super(formatter);
    }

    @Deprecated(forRemoval = true)
    @Override
    protected void processFocusEvent(FocusEvent e) {
        super.processFocusEvent(e);
        if (e.getID() == FocusEvent.FOCUS_GAINED)
            selectAll();
    }
}
