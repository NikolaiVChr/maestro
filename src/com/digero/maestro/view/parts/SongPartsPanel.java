package com.digero.maestro.view.parts;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.TitledBorder;

import com.digero.common.view.UIText;
import com.digero.maestro.view.SongPartsListPanel;

public class SongPartsPanel extends JPanel {

    private final JScrollPane scrollPane;
    private final SongPartsActionsPanel actionsPanel;
    private final SongPartsToolsPanel toolsPanel;

    private final BorderLayout layout;
    private final TitledBorder border;

    private static final int HGAP = 4;
    private static final int VGAP = 4;

    public SongPartsPanel(SongPartsListPanel partsList) {
        
        this.scrollPane = new JScrollPane(partsList,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        this.actionsPanel = new SongPartsActionsPanel();
        this.toolsPanel = new SongPartsToolsPanel();

        this.layout = new BorderLayout(HGAP, VGAP);
        setLayout(layout);

        this.border = BorderFactory.createTitledBorder(UIText.get("maestro.song.parts"));
        setBorder(border);

        add(actionsPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(toolsPanel, BorderLayout.SOUTH);
    }
}
