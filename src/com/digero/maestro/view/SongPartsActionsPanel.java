package com.digero.maestro.view;

import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JPanel;

import com.digero.common.view.UIText;

final class SongPartsActionsPanel extends JPanel {
    private final FlowLayout layout;

    private final JButton createNewPartButton;
    private final JButton deletePartButton;
    private final JButton sortPartsButton;

    private static final int HGAP = 4;
    private static final int VGAP = 4;

    private Runnable createPartAction;
    private Runnable deletePartAction;
    private Runnable sortPartsAction;

    /**
     * Creates the panel with three buttons: "Create New Part", "Delete Part" and "Sort Parts".
     */
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

    /**
     * Sets the action to be performed when the "Create New Part" button is clicked.
     * @param action The action to be performed.
     */
    public void setCreatePartAction(Runnable action) {
        createPartAction = action;
    }

    /**
     * Sets the action to be performed when the "Delete Part" button is clicked.
     * @param action The action to be performed.
     */
    public void setDeletePartAction(Runnable action) {
        deletePartAction = action;
    }

    /**
     * Sets the action to be performed when the "Sort Parts" button is clicked.
     * @param action The action to be performed.
     */
    public void setSortPartsAction(Runnable action) {
        sortPartsAction = action;
    }

    /**
     * Enables or disables the "Create New Part" button.
     * @param enable True to enable the button, false to disable it.
     */
    public void setCreateNewPartButtonEnabled(boolean enable) {
        createNewPartButton.setEnabled(enable);
    }

    /**
     * Enables or disables the "Delete Part" button.
     * @param enable True to enable the button, false to disable it.
     */
    public void setDeletePartButtonEnabled(boolean enable) {
        deletePartButton.setEnabled(enable);
    }

    /**
     * Enables or disables the "Sort Parts" button.
     * @param enable True to enable the button, false to disable it.
     */
    public void setSortPartsButtonEnabled(boolean enable) {
        sortPartsButton.setEnabled(enable);
    }
}
