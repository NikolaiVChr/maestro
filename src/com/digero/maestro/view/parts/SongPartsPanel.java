package com.digero.maestro.view.parts;

import java.awt.BorderLayout;
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

    public void setActionListener(SongPartsActionListener listener) {
        this.actionListener = listener;
    }

    private void notifyListener(Consumer<SongPartsActionListener> notification) {
        if (actionListener != null) {
            notification.accept(actionListener);
        }
    }
}
