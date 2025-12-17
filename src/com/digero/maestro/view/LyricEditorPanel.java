package com.digero.maestro.view;

import com.digero.common.util.LyricLine;
import com.digero.common.util.Themer;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.QuantizedTimingInfo;
import com.digero.maestro.midi.MidiText;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class LyricEditorPanel extends JPanel {
    private final LyricTable table;
    private final LyricTableModel model;
    private int highlightedRow = -1;
    private static final Color HIGHLIGHT_COLOR = Themer.isDarkMode()?ColorTable.LYRICS_HIGHLIGHT_DARK.get():ColorTable.LYRICS_HIGHLIGHT_LIGHT.get();
    public boolean modified = false;
    public AbcSong abcSong = null;

    public LyricEditorPanel() {
        setLayout(new BorderLayout());

        model = new LyricTableModel();
        table = new LyricTable(model);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(new JTextArea().getBackground()); // Match text area look
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Forces the table to stop editing and commit the current value.
     */
    public void stopEditing() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    public void setFromMidiText(MidiText midiText) {
        if (midiText != null) {
            model.setLines(midiText.getStructuredLyrics());
        } else {
            model.setLines(new ArrayList<>());
        }
    }

    /**
     *
     * @param tick song position in ticks
     */
    public void setTick(long tick) {
        int newRow = -1;
        List<LyricLine> lines = model.getLines();

        // Find the line corresponding to the current tick
        // If two lyric lines happen at the exact same tick, the last is selected
        if (lines != null && !lines.isEmpty()) {
            int low = 0;
            int high = lines.size() - 1;

            // Standard Binary Search loop
            while (low <= high) {
                // Calculate middle index (unsigned shift for safety)
                int mid = (low + high) >>> 1;
                long midTick = lines.get(mid).tick();

                if (midTick <= tick) {
                    // The line at 'mid' started in the past (or exactly now).
                    // This is a valid candidate for current line.
                    newRow = mid;

                    // We do not break here.
                    // There might be a later line that also started before tick.
                    // We move low up to search the right half for a better candidate.
                    low = mid + 1;
                } else {
                    // The line at mid starts in the future.
                    // It cannot be the current line, so we must search the left half.
                    high = mid - 1;
                }
            }
        }

        // Only redraw if the row actually changed
        if (newRow != highlightedRow) {
            highlightedRow = newRow;

            // Repaint to update the background colors
            table.repaint();

            // If the user is currently editing a cell, we might need to
            // force the editor component to update its background color too.
            if (table.isEditing()) {
                int editingRow = table.getEditingRow();
                Component editorComp = table.getEditorComponent();
                if (editorComp instanceof JScrollPane) {
                    // Extract the JTextArea from the ScrollPane wrapper
                    JTextArea editorText = (JTextArea) ((JScrollPane) editorComp).getViewport().getView();
                    if (editingRow == highlightedRow) {
                        editorText.setBackground(HIGHLIGHT_COLOR);
                    } else {
                        editorText.setBackground(new JTextArea().getBackground());
                    }
                }
            }

            // Auto-scroll logic: Only scroll if the user isn't actively interacting
            // (Standard behavior: always scroll to keep playback in view)
            if (highlightedRow != -1 && !table.isEditing()) {
                table.scrollRectToVisible(table.getCellRect(highlightedRow, 0, true));
            }
        }
    }

    public void setFromLyricLines(List<LyricLine> lines) {
        model.setLines(Objects.requireNonNullElseGet(lines, ArrayList::new));
    }

    public List<LyricLine> getLyricLines() {
        List<LyricLine> lines = model.getLines();
        if (lines.isEmpty()) return null;
        return lines;
    }

    public String getPoeticalLyrics(QuantizedTimingInfo qtm, boolean organic, AbcPart part, boolean countUp) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        long tickPrev = Long.MIN_VALUE;
        long songStartMicros = part.getAbcSong().getSongStartMicrosABC();
        for (LyricLine line : model.getLines()) {
            if (first) {
                if (!line.text().isBlank()) sb.append("% ").append(line.text().replace("\n","\n%")).append('\n');
            } else {
                long tick = line.tick();
                long micros;
                if (organic) {
                    micros = qtm.tickToMicrosABCOrganic(tick)-songStartMicros;
                } else {
                    micros = qtm.tickToMicrosABC(tick, part)-songStartMicros;
                }
                if (!countUp) {
                    micros = part.getAbcSong().getSongLengthMicros()-micros;
                }
                if (!line.text().isBlank()) {
                    if (tick != tickPrev) {
                        sb.append("% ");
                        if (micros < 0L) {
                            micros = -micros;
                            sb.append("-");
                        }
                        sb.append(Util.formatDuration(micros)).append("\n");
                    }
                    sb.append(line.text()).append('\n');
                } else {
                    sb.append('\n');
                }
                tickPrev = line.tick();
            }
            first = false;
        }
        return sb.toString();
    }

    private class LyricTable extends JTable {
        // Reusable dummy for calculating height
        private final JTextArea dummyEditor = new JTextArea();

        public LyricTable(LyricTableModel model) {
            super(model);
            setShowGrid(false);
            setIntercellSpacing(new Dimension(0, 0));
            setTableHeader(null);
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            setFillsViewportHeight(true);

            // commit edits when clicking outside the table
            putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

            // Hide the timestamp column
            getColumnModel().getColumn(0).setMinWidth(0);
            getColumnModel().getColumn(0).setMaxWidth(0);
            getColumnModel().getColumn(0).setWidth(0);

            // Set Custom Renderer and Editor for the text column
            getColumnModel().getColumn(1).setCellRenderer(new TextAreaRenderer());
            getColumnModel().getColumn(1).setCellEditor(new TextAreaEditor());

            // Recalculate heights when data changes
            model.addTableModelListener(e -> SwingUtilities.invokeLater(this::updateRowHeights));

            // Configure the dummy used for height calculations
            dummyEditor.setLineWrap(true);
            dummyEditor.setWrapStyleWord(true);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true; // Force table to fit viewport width
        }

        @Override
        public void doLayout() {
            // Ensure column width is set before calculating row heights
            super.doLayout();
            updateRowHeights();
        }

        private void updateRowHeights() {
            if (getColumnModel().getColumnCount() < 2) return;

            int columnWidth = getColumnModel().getColumn(1).getWidth();
            if (columnWidth <= 0) return;

            // Account for the border in the Renderer (5 left + 5 right = 10px)
            // If we don't subtract this, the dummy editor thinks it has more space
            // than the renderer, causing mismatch in line wrapping.
            int availableWidth = columnWidth - 10;
            if (availableWidth <= 0) availableWidth = columnWidth;

            dummyEditor.setFont(getFont());
            dummyEditor.setSize(availableWidth, Short.MAX_VALUE);

            for (int row = 0; row < getRowCount(); row++) {
                String text = (String) getValueAt(row, 1);
                dummyEditor.setText(text);

                int prefHeight = dummyEditor.getPreferredSize().height;
                // Add vertical padding (2 top + 2 bottom = 4)
                int targetHeight = Math.max(prefHeight + 4, 16);

                if (getRowHeight(row) != targetHeight) {
                    setRowHeight(row, targetHeight);
                }
            }
        }

        private void updateRowHeightsOld() {
            if (getColumnModel().getColumnCount() < 2) return;

            int width = getColumnModel().getColumn(1).getWidth();
            if (width <= 0) return;

            dummyEditor.setFont(getFont());
            dummyEditor.setSize(width, Short.MAX_VALUE);

            for (int row = 0; row < getRowCount(); row++) {
                String text = (String) getValueAt(row, 1);
                dummyEditor.setText(text);

                int prefHeight = dummyEditor.getPreferredSize().height;
                int targetHeight = Math.max(prefHeight + 4, 16); // +4 padding, min 16px

                if (getRowHeight(row) != targetHeight) {
                    setRowHeight(row, targetHeight);
                }
            }
        }
    }

    // Renders the cell as a wrapping JTextArea
    private class TextAreaRenderer extends JTextArea implements TableCellRenderer {
        public TextAreaRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            // Priority 1: Selection (user clicked)
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            }
            // Priority 2: Highlight (playback position)
            else if (row == highlightedRow) {
                setForeground(table.getForeground());
                setBackground(HIGHLIGHT_COLOR);
            }
            // Priority 3: Default
            else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }

            setFont(table.getFont());
            setText((value == null) ? "" : value.toString());
            setSize(table.getColumnModel().getColumn(column).getWidth(), 0);
            return this;
        }
    }

    // Allows editing using a wrapping JTextArea
    private class TextAreaEditor extends AbstractCellEditor implements TableCellEditor {
        private final JTextArea textArea;
        private final JScrollPane scrollPane;

        public TextAreaEditor() {
            textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

            // Wrap editor in ScrollPane to handle overflow during edit without expanding table cells
            scrollPane = new JScrollPane(textArea);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        public Object getCellEditorValue() {
            return textArea.getText();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            textArea.setFont(table.getFont());
            textArea.setText((value == null) ? "" : value.toString());
            textArea.setCaretPosition(0);

            // Apply background color logic to the editor as well
            if (row == highlightedRow) {
                textArea.setBackground(HIGHLIGHT_COLOR);
            } else {
                textArea.setBackground(new JTextArea().getBackground());
            }

            return scrollPane;
        }
    }

    class LyricTableModel extends AbstractTableModel {
        private final List<LyricLine> lines = new ArrayList<>();
        private final String[] columnNames = {"Tick", "Lyrics"};

        public void setLines(List<LyricLine> newLines) {
            lines.clear();
            lines.addAll(newLines);
            fireTableDataChanged();
        }

        public List<LyricLine> getLines() {
            return lines;
        }

        public String getTextAt(int row) {
            return lines.get(row).text();
        }

        @Override
        public int getRowCount() {
            return lines.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LyricLine line = lines.get(rowIndex);
            return (columnIndex == 0) ? line.tick() : line.text();
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1; // Only allow editing text, not ticks
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 1) {
                LyricLine old = lines.get(rowIndex);
                String newText = (String) aValue;
                if (!old.text().equals(newText)) {
                    modified = true;
                    if (abcSong != null) abcSong.notifyLyricLinesModified();
                    // Create new record with same tick, updated text
                    lines.set(rowIndex, new LyricLine(old.tick(), newText));
                    fireTableCellUpdated(rowIndex, columnIndex);
                }
            }
        }
    }
}