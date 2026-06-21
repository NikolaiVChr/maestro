package com.digero.maestro.view.parts;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;

import com.digero.common.view.UIText;
import com.digero.maestro.view.SongPartsListPanel;

public class SongPartsPanel extends JPanel {

    private SongPartsActionListener actionListener;

    private final JScrollPane scrollPane;
    private final SongPartsActionsPanel actionsPanel;
    private final SongPartsToolsPanel toolsPanel;

    private final BorderLayout layout;
    private final TitledBorder border;

    private static final int MINIMUM_LIST_WIDTH = 220;
    private static final int HGAP = 4;
    private static final int VGAP = 4;

    /**
     * Creates a new SongPartsPanel with the given parts list panel.
     * 
     * @param partsList The parts list panel to be displayed in this panel.
     */
    public SongPartsPanel(SongPartsListPanel partsList) {

        this.scrollPane = createScrollPane(partsList);
        this.actionsPanel = new SongPartsActionsPanel();
        this.toolsPanel = new SongPartsToolsPanel();

        actionsPanel.setCreatePartAction(
                () -> notifyListener(SongPartsActionListener::createPartRequested));

        actionsPanel.setDeletePartAction(
                () -> notifyListener(SongPartsActionListener::deletePartRequested));

        actionsPanel.setSortPartsAction(
                () -> notifyListener(SongPartsActionListener::sortPartsRequested));

        toolsPanel.setOpenPartEditorAction(
                () -> notifyListener(SongPartsActionListener::openPartEditorRequested));

        toolsPanel.setNumeratePartsAction(
                () -> notifyListener(SongPartsActionListener::numeratePartsRequested));

        this.layout = new BorderLayout(HGAP, VGAP);
        setLayout(layout);

        this.border = BorderFactory.createTitledBorder(UIText.get("maestro.song.parts"));
        setBorder(border);

        add(actionsPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(toolsPanel, BorderLayout.SOUTH);
    }

    /**
     * Creates a JScrollPane containing the given parts list panel, with appropriate
     * scroll bar policies and minimum size.
     * 
     * @param partsListPanel The parts list panel to be displayed in the scroll
     *                       pane.
     * @return The created JScrollPane containing the parts list panel.
     */
    private static JScrollPane createScrollPane(SongPartsListPanel partsListPanel) {

        JScrollPane scrollPane = new JScrollPane(
                partsListPanel,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        Dimension minimumSize = scrollPane.getMinimumSize();
        minimumSize.width = MINIMUM_LIST_WIDTH;
        scrollPane.setMinimumSize(minimumSize);

        return scrollPane;
    }

    /**
     * Sets the action listener for this panel, which will be notified of user
     * actions
     * such as creating, deleting, sorting, numerating parts, or opening the part
     * editor.
     * 
     * @param listener The action listener to be notified of user actions.
     */
    public void setActionListener(SongPartsActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * Notifies the action listener of a user action by accepting a notification
     * consumer that calls the appropriate method on the listener.
     * 
     * @param notification A consumer that accepts the action listener and calls the
     *                     appropriate method for the user action.
     */
    private void notifyListener(Consumer<SongPartsActionListener> notification) {
        if (actionListener != null) {
            notification.accept(actionListener);
        }
    }

    /**
     * Enables or disables the buttons in this panel based on the given parameters.
     * 
     * @param enableNewAndSort Whether to enable the "Create New Part" and "Sort
     *                         Parts" buttons.
     * @param enableDelete     Whether to enable the "Delete Part" button.
     * @param enableNumerate   Whether to enable the "Numerate Parts" button.
     * @param enablePartEditor Whether to enable the "Open Part Editor" button.
     */
    public void setButtonsEnabled(boolean enableNewAndSort, boolean enableDelete, boolean enableNumerate,
            boolean enablePartEditor) {
        actionsPanel.setCreateNewPartButtonEnabled(enableNewAndSort);
        actionsPanel.setSortPartsButtonEnabled(enableNewAndSort);
        actionsPanel.setDeletePartButtonEnabled(enableDelete);
        toolsPanel.setNumeratePartsButtonEnabled(enableNumerate);
        toolsPanel.setOpenPartEditorButtonEnabled(enablePartEditor);
    }

    /**
     * Sets the foreground color of the "Open Part Editor" button.
     * 
     * @param color The color to set.
     */
    public void setOpenEditorButtonForeground(Color color) {
        toolsPanel.setOpenPartEditorButtonForeground(color);
    }
}
