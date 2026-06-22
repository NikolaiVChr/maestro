package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.digero.common.view.UIText;

public class SongPartsPanel extends JPanel {

    private SongPartsActionListener actionListener;

    private final JScrollPane scrollPane;
    private final SongPartsActionsPanel actionsPanel;
    private final SongPartsToolsPanel toolsPanel;

    private static final int PREFERRED_WIDTH = PartsListItem.getProtoDimension().width;
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

        setLayout(new BorderLayout(HGAP, VGAP));
        setBorder(BorderFactory.createTitledBorder(UIText.get("maestro.song.parts")));

        add(actionsPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(toolsPanel, BorderLayout.SOUTH);
    }

    /**
     * Creates a scroll pane for the song-parts list.
     *
     * @param songPartsListPanel the panel displayed in the scroll pane
     * @return the configured scroll pane
     */
    private static JScrollPane createScrollPane(
            SongPartsListPanel songPartsListPanel) {

        JScrollPane scrollPane = new JScrollPane(
                songPartsListPanel,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        // SongPartsListPanel uses the viewport size when calculating its
        // preferred size, ensuring that it fills otherwise empty viewport space.
        songPartsListPanel.setScroll(scrollPane);

        Dimension preferredSize = scrollPane.getMinimumSize();
        preferredSize.width = PREFERRED_WIDTH;
        scrollPane.setPreferredSize(preferredSize);

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
