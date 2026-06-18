package com.digero.maestro.view.parts;

import javax.swing.JButton;

import com.digero.common.view.UIText;

public class SongPartsActionsPanel {
    private final JButton createNewPartButton;
    private final JButton deletePartButton;
    private final JButton sortPartsButton;

    public SongPartsActionsPanel(){
        createNewPartButton = createButton("maestro.new.part", "");
        deletePartButton = createButton("maestro.delete", "");
        sortPartsButton = createButton("maestro.sort", "maestro.tip.sort.parts");

    }

    /**
     * Creates one button with localized label text.
     *
     * @param labelKey  The {@link UIText} key used for the button label.
     * @param tooltipKey The {@link UIText} key used for the tooltip.
     * @return The created {@link JButton}
     */
    private JButton createButton(String labelKey, String tooltipKey){
        JButton button = new JButton(UIText.get(labelKey));
        if(!tooltipKey.isBlank() && tooltipKey != null)
            button.setToolTipText(UIText.get(tooltipKey));

        return button;
    }


}
