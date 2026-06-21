package com.digero.maestro.view.parts;

import java.awt.Color;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JPanel;

import com.digero.common.view.UIText;

public final class SongPartsToolsPanel extends JPanel {

    private final GridLayout layout;

    private final JButton openPartEditorButton;
    private final JButton numeratePartsButton;

    private Runnable openPartEditorAction;
    private Runnable numeratePartsAction;

    /**
     * Creates the panel with two buttons: "Open Part Editor" and "Numerate Parts".
     */
    public SongPartsToolsPanel() {
        this.openPartEditorButton = createButton("maestro.part.editor", "maestro.tip.partedit");
        this.numeratePartsButton = createButton("maestro.numerate", "maestro.tip.numerate");

        openPartEditorButton.addActionListener(e -> {
            if (openPartEditorAction != null)
                openPartEditorAction.run();
        });

        numeratePartsButton.addActionListener(e -> {
            if (numeratePartsAction != null)
                numeratePartsAction.run();
        });

        this.layout = new GridLayout(1, 2);
        setLayout(layout);

        add(openPartEditorButton);
        add(numeratePartsButton);
    }

    /**
     * Creates one button with localized label and tooltip.
     *
     * @param labelKey   The {@link UIText} key used for the button label.
     * @param tooltipKey The {@link UIText} key used for the tooltip.
     * @return The created {@link JButton}
     */
    private static JButton createButton(String labelKey, String tooltipKey) {
        JButton button = new JButton(UIText.get(labelKey));
        button.setToolTipText(UIText.get(tooltipKey));
        return button;
    }

    /**
     * Sets the action to be performed when the "Open Part Editor" button is clicked.
     * @param action The action to be performed.
     */
    public void setOpenPartEditorAction(Runnable action) {
        openPartEditorAction = action;
    }

    /**
     * Sets the action to be performed when the "Numerate Parts" button is clicked.
     * @param action The action to be performed.
     */
    public void setNumeratePartsAction(Runnable action) {
        numeratePartsAction = action;
    }

    /**
     * Enables or disables the "Open Part Editor" button.
     * @param enabled
     */
    public void setOpenPartEditorButtonEnabled(boolean enabled) {
        openPartEditorButton.setEnabled(enabled);
    }

    /**
     * Enables or disables the "Numerate Parts" button.
     * @param enabled
     */
    public void setNumeratePartsButtonEnabled(boolean enabled) {
        numeratePartsButton.setEnabled(enabled);
    }

    /**
     * Sets the foreground color of the "Open Part Editor" button.
     * @param color The color to set.
     */
    public void setOpenPartEditorButtonForeground(Color color) {
        openPartEditorButton.setForeground(color);
    }
}
