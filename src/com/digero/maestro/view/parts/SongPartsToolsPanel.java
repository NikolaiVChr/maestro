package com.digero.maestro.view.parts;

import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JPanel;

import com.digero.common.view.UIText;

public class SongPartsToolsPanel extends JPanel {

    private final GridLayout layout;

    private final JButton openPartEditorButton;
    private final JButton numeratePartsButton;


    public SongPartsToolsPanel(){
        this.openPartEditorButton = createButton("maestro.part.editor", "maestro.tip.partedit");
        this.numeratePartsButton = createButton("maestro.numerate", "maestro.tip.numerate");

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
    private JButton createButton(String labelKey, String tooltipKey) {
        JButton button = new JButton(UIText.get(labelKey));
        button.setToolTipText(UIText.get(tooltipKey));
        return button;
    }
}
