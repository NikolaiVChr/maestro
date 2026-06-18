package com.digero.maestro.view.parts;

import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JPanel;

import com.digero.common.view.UIText;

public final class SongPartsActionsPanel extends JPanel {
    private final FlowLayout layout;

    private final JButton createNewPartButton;
    private final JButton deletePartButton;
    private final JButton sortPartsButton;

    private static final int HGAP = 4;
    private static final int VGAP = 4;

    private Runnable createPartAction;
    private Runnable deletePartAction;
    private Runnable sortPartsAction;

    public SongPartsActionsPanel() {
        this.createNewPartButton = createButton("maestro.new.part");
        this.deletePartButton = createButton("maestro.delete");
        this.sortPartsButton = createButton("maestro.sort", "maestro.tip.sort.parts");

        this.layout = new FlowLayout(FlowLayout.CENTER, HGAP, VGAP);
        setLayout(layout);

        createNewPartButton.addActionListener(e -> {
            if (createPartAction != null)
                createPartAction.run();
        });

        deletePartButton.addActionListener(e -> {
            if (deletePartAction != null)
                deletePartAction.run();
        });

        sortPartsButton.addActionListener(e -> {
            if (sortPartsAction != null)
                sortPartsAction.run();
        });

        add(createNewPartButton);
        add(deletePartButton);
        add(sortPartsButton);
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
     * Creates one button with localized label
     * 
     * @param labelKey The {@link UIText} key used for the button label.
     * @return The created {@link JButton}
     */
    private static JButton createButton(String labelKey) {
        return new JButton(UIText.get(labelKey));
    }

    public void setCreatePartAction(Runnable action) {
        createPartAction = action;
    }

    public void setDeletePartAction(Runnable action) {
        deletePartAction = action;
    }

    public void setSortPartsAction(Runnable action) {
        sortPartsAction = action;
    }

}
