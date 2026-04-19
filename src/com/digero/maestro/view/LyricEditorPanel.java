package com.digero.maestro.view;

import com.digero.common.util.LyricLine;
import com.digero.common.util.Themer;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.common.view.UIText;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.QuantizedTimingInfo;
import com.digero.maestro.midi.MidiText;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class LyricEditorPanel extends JPanel {
    private final LyricTable table;
    private final LyricTableModel model;
    private int highlightedRow = -1;
    private static final Color BACKGROUND_COLOR = new JTextArea().getBackground();
    public boolean modified = false;// If lyrics have been modified since they were read from the midi source file.
    public AbcSong abcSong = null;
    private final JScrollPane scrollPane;

    public LyricEditorPanel() {
        setLayout(new BorderLayout());

        model = new LyricTableModel();
        table = new LyricTable(model);

        scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(BACKGROUND_COLOR); // Match text area look
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
                        editorText.setBackground(Themer.isDarkMode()?ColorTable.LYRICS_HIGHLIGHT_DARK.get():ColorTable.LYRICS_HIGHLIGHT_LIGHT.get());
                    } else {
                        editorText.setBackground(BACKGROUND_COLOR);
                    }
                }
            }

            // Auto-scroll logic: Only scroll if the user isn't actively interacting
            // (Standard behavior: always scroll to keep playback in view)
            if (highlightedRow != -1 && !table.isEditing()) {
                //keep it in view
                //table.scrollRectToVisible(table.getCellRect(highlightedRow, 0, true));

                //keep it in center
                scrollToCenter(table, highlightedRow, 1);
            }
        }
    }

    private void scrollToCenter(JTable table, int rowIndex, int colIndex) {
        if (!(table.getParent() instanceof JViewport)) return;

        JViewport viewport = (JViewport) table.getParent();
        Rectangle rect = table.getCellRect(rowIndex, colIndex, true);
        Rectangle viewRect = viewport.getViewRect();

        // Calculate y to center the row
        int y = rect.y - (viewRect.height / 2) + (rect.height / 2);

        // Clamp to valid range
        y = Math.max(0, Math.min(y, table.getHeight() - viewRect.height));

        viewport.setViewPosition(new Point(viewRect.x, y));
    }

    public void setFromLyricLines(List<LyricLine> lines) {
        model.setLines(Objects.requireNonNullElseGet(lines, ArrayList::new));
    }

    public List<LyricLine> getLyricLines() {
        List<LyricLine> lines = model.getLines();
        if (lines.isEmpty()) return null;
        return lines;
    }

    public String getPoeticalLyrics(QuantizedTimingInfo qtm, boolean organic, AbcPart part, boolean countUp, boolean allTimestamps) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        long tickPrev = Long.MIN_VALUE;
        long songStartMicros = part.getAbcSong().getSongStartMicrosABC();

        boolean lastWasBlank = true; // Treat start as a new block
        long prevLineMicros = Long.MIN_VALUE;
        // If lines start > 6 seconds apart, assume a significant pause/break
        final long GAP_THRESHOLD = 6_000_000L;// minimal gap to print timestamp
        final long STANZA_THRESHOLD = 2_000_000L; // 2.0s gap -> Allow Stanza Break (print blank Line)
        final long INSTRUMENTAL_THRESHOLD = 20_000_000L;// Minimal gap to print 'instrumental'

        // Track if we are holding a blank line in suspense
        boolean pendingStanzaBreak = false;

        for (LyricLine line : model.getLines()) {
            if (first && !modified) {
                // We know it is the meta-info block if it's the first and lyrics not modified.
                if (!line.text().isBlank()) sb.append("% ").append(line.text().replace("\n","\n%")).append('\n');
                lastWasBlank = true;
            } else {
                long tick = line.tick();
                long micros;
                long microsEnd;
                if (organic) {
                    micros = qtm.tickToMicrosABCOrganic(tick)-songStartMicros;
                    microsEnd = qtm.tickToMicrosABCOrganic(line.endTick())-songStartMicros;
                } else {
                    micros = qtm.tickToMicrosABC(tick, part)-songStartMicros;
                    microsEnd = qtm.tickToMicrosABC(line.endTick(), part)-songStartMicros;
                }
                long thisLineStartMicros = micros;

                // If the line duration is zero (or very small), estimate a natural singing length.
                // This prevents zero-length lines from creating fake large gaps after them.
                long realDuration = Math.abs(microsEnd - micros);
                if (realDuration < 500_000L && !line.text().isEmpty()) {
                    // Estimate: 75 ms per character (in case it's rap)
                    long estimatedDuration = line.text().length() * 75_000L;

                    // Cap the estimate so super long text doesn't eat the whole gap
                    estimatedDuration = Math.min(estimatedDuration, 5_000_000L);

                    // Ensure the estimation is at least somewhat longer than the tiny duration
                    if (estimatedDuration > realDuration) {
                        microsEnd = micros + estimatedDuration;
                    }
                }

                if (!countUp) {
                    micros = part.getAbcSong().getSongLengthMicros()-micros;
                    microsEnd = part.getAbcSong().getSongLengthMicros()-microsEnd;
                    prevLineMicros = Math.max(micros, prevLineMicros);
                } else {
                    prevLineMicros = Math.min(micros, prevLineMicros);
                }

                if (isRealLyric(line.text())) {
                    long gap = prevLineMicros == Long.MIN_VALUE ? 0L : Math.abs(micros - prevLineMicros);
                    if (pendingStanzaBreak) {
                        // Only print the blank line if there was actual silence
                        if (gap > STANZA_THRESHOLD || prevLineMicros == Long.MIN_VALUE) {
                            sb.append('\n');
                            lastWasBlank = true;
                        } else {
                            // Newline or clear screen just for the sake of karaoke display.
                            // Ignore it, so in effect remove the newline/clear between the lines.
                            lastWasBlank = false;
                        }
                        pendingStanzaBreak = false; // Reset flag
                    }
                    boolean hasGap = prevLineMicros != Long.MIN_VALUE && (gap > GAP_THRESHOLD || allTimestamps);
                    if (lastWasBlank || ((allTimestamps || tick != tickPrev) && hasGap)) {
                        if (prevLineMicros != Long.MIN_VALUE && gap > INSTRUMENTAL_THRESHOLD) {
                            sb.append(UIText.get("maestro.lyrics.interlude"));
                        }
                        sb.append("% ");
                        if (micros < 0L) {
                            micros = -micros;
                            sb.append("-");
                        }
                        sb.append(Util.formatDuration(micros)).append("\n");
                    }
                    sb.append(line.text()).append('\n');
                    //System.out.println(line.text() + ", lastEnd " + Util.formatDuration(prevLineMicros) + " begin " + Util.formatDuration(micros) + ", end " + Util.formatDuration(microsEnd));
                    prevLineMicros = microsEnd;
                    lastWasBlank = thisLineStartMicros < 0L;
                } else if (!line.text().isBlank()) {
                    sb.append(line.text()).append('\n');
                    lastWasBlank = true;
                } else {
                    // Don't print '\n' yet! We don't know if it's real.
                    pendingStanzaBreak = true;

                    // Note: We do not update lastWasBlank here yet.
                    // We wait until the next text line decides the fate of this break.
                }
                tickPrev = line.tick();
            }
            first = false;
        }
        return sb.toString();
    }

    private boolean isRealLyric(String text) {
        if (text == null || text.isBlank()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
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

            // Recalculate heights when data changes
            model.addTableModelListener(e -> SwingUtilities.invokeLater(this::updateRowHeights));

            // Configure the dummy used for height calculations
            dummyEditor.setLineWrap(true);
            dummyEditor.setWrapStyleWord(true);

            // --- Context Menu ---
            JPopupMenu popup = new JPopupMenu();

            // Listener to stop editing when menu opens
            popup.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                    // Commit any active edits immediately
                    stopEditing();

                    // Select the row under the mouse pointer
                    Point p = getMousePosition();
                    if (p != null) {
                        int row = rowAtPoint(p);
                        if (row != -1 && !isRowSelected(row)) {
                            setRowSelectionInterval(row, row);
                        }
                    }
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

                @Override
                public void popupMenuCanceled(PopupMenuEvent e) {}
            });

            JMenuItem insertItem = new JMenuItem(UIText.get("maestro.lyrics.insert.line"));
            insertItem.addActionListener(e -> showInsertDialog());
            popup.add(insertItem);

            JMenuItem deleteItem = new JMenuItem(UIText.get("maestro.lyrics.delete.line"));
            deleteItem.addActionListener(e -> deleteSelectedLine());
            popup.add(deleteItem);

            JMenuItem changeItem = new JMenuItem(UIText.get("maestro.lyrics.change.line.timing"));
            changeItem.addActionListener(e -> changeSelectedLine());
            popup.add(changeItem);

            setComponentPopupMenu(popup);

            // Set Custom Renderer and Editor for the text column
            getColumnModel().getColumn(1).setCellRenderer(new TextAreaRenderer());
            getColumnModel().getColumn(1).setCellEditor(new TextAreaEditor(this, popup));
        }

        private void showInsertDialog() {
            if (abcSong == null) return;

            // Use the currently selected row's bar as default, or 0.0
            float defaultBar = 0.0f;
            int selectedRow = getSelectedRow();
            if (selectedRow >= 0) {
                long tick = (Long) getValueAt(selectedRow, 0);
                defaultBar = abcSong.getSequenceInfo().getDataCache().tickToBarNumberFloat(tick);
            }

            // Create UI inputs
            JTextField barField = new JTextField(String.valueOf(defaultBar).replace(",","."));

            barField.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON3) {
                        Window window = SwingUtilities.getWindowAncestor(LyricEditorPanel.this);
                        if (window instanceof ProjectFrame projectFrame) {
                            barField.setText(String.format(Locale.US, "%.3f", projectFrame.getSourcePlayHeadBar()));
                        }
                    }
                }
            });

            JTextArea textField = new JTextArea(3, 20);
            textField.setLineWrap(true);
            textField.setWrapStyleWord(true);
            JScrollPane textScroll = new JScrollPane(textField);

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(5, 5, 5, 5);
            c.fill = GridBagConstraints.HORIZONTAL;

            c.gridx = 0; c.gridy = 0;
            panel.add(new JLabel(UIText.get("maestro.lyrics.bar.number")), c);

            c.gridx = 1; c.weightx = 1.0;
            panel.add(barField, c);

            c.gridx = 0; c.gridy = 1; c.weightx = 0.0; c.anchor = GridBagConstraints.NORTHWEST;
            panel.add(new JLabel(UIText.get("maestro.lyrics.text")), c);

            c.gridx = 1; c.weightx = 1.0; c.weighty = 1.0; c.fill = GridBagConstraints.BOTH;
            panel.add(textScroll, c);

            int result = JOptionPane.showConfirmDialog(scrollPane, panel, UIText.get("maestro.lyrics.insert.new.lyric.line"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                try {
                    float bar = Float.parseFloat(barField.getText().replace(",","."));
                    String text = textField.getText().trim();
                    long tick = abcSong.getSequenceInfo().getDataCache().barFloatToTick(bar);
                    model.addLine(new LyricLine(tick, text, tick));
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(scrollPane, UIText.get("maestro.lyrics.invalid.bar.number"), UIText.get("maestro.lyrics.error"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void changeSelectedLine() {
            int row = getSelectedRow();
            if (row >= 0 && abcSong != null) {
                // Get current data
                long currentStartTick = (Long) getValueAt(row, 0);
                float currentBar = abcSong.getSequenceInfo().getDataCache().tickToBarNumberFloat(currentStartTick);

                // Create the text field manually so we can add listeners
                JTextField barField = new JTextField(String.valueOf(currentBar).replace(",","."));

                barField.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        if (e.getButton() == MouseEvent.BUTTON3) {
                            Window window = SwingUtilities.getWindowAncestor(LyricEditorPanel.this);
                            if (window instanceof ProjectFrame projectFrame) {
                                barField.setText(String.format(Locale.US, "%.3f", projectFrame.getSourcePlayHeadBar()));
                            }
                        }
                    }
                });

                int result = JOptionPane.showConfirmDialog(scrollPane,
                        new Object[] {UIText.get("maestro.lyrics.enter.new.bar.number.for.this.lyrics.line"), barField },
                        UIText.get("maestro.lyrics.move.line"),
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.OK_OPTION) {
                    try {
                        float newBar = Float.parseFloat(barField.getText().replace(",","."));
                        long newTick = abcSong.getSequenceInfo().getDataCache().barFloatToTick(newBar);
                        model.moveLine(row, newTick);
                        restoreSelection(newTick);
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(scrollPane, UIText.get("maestro.lyrics.invalid.bar.number.format"), UIText.get("maestro.lyrics.error"), JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        }

        private void restoreSelection(long targetTick) {
            for (int i = 0; i < getRowCount(); i++) {
                if ((long) getValueAt(i, 0) == targetTick) {
                    setRowSelectionInterval(i, i);
                    scrollRectToVisible(getCellRect(i, 0, true));
                    break;
                }
            }
        }

        private void deleteSelectedLine() {
            int row = getSelectedRow();
            if (row >= 0) {
                int res = JOptionPane.showConfirmDialog(scrollPane, UIText.get("maestro.lyrics.delete.selected.line"), UIText.get("maestro.lyrics.confirm.delete"), JOptionPane.YES_NO_OPTION);
                if (res == JOptionPane.YES_OPTION) {
                    model.removeLine(row);
                }
            }
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
            // Prio 1: Selection (user clicked)
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            }
            // Prio 2: Highlight (playback position)
            else if (row == highlightedRow) {
                setForeground(table.getForeground());
                setBackground(Themer.isDarkMode()?ColorTable.LYRICS_HIGHLIGHT_DARK.get():ColorTable.LYRICS_HIGHLIGHT_LIGHT.get());
            }
            // Prio 3: Default
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

        public TextAreaEditor(JTable table, JPopupMenu popup) {
            textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

            // Wrap editor in ScrollPane to handle overflow during edit without expanding table cells
            scrollPane = new JScrollPane(textArea);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());

            // Catch right-clicks on the active editor, stop editing,
            // and forward the popup to the table.
            MouseAdapter rightClickForwarder = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) doPopup(e);
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) doPopup(e);
                }

                private void doPopup(MouseEvent e) {
                    // Calculate where the click happened relative to the TABLE
                    Point editorPt = e.getPoint();
                    Point tablePt = SwingUtilities.convertPoint(e.getComponent(), editorPt, table);

                    // Stop editing immediately (this removes the editor)
                    stopEditing();
                    // Note: 'stopEditing()' calls fireEditingStopped, which updates the model.

                    // Ensure the row under the mouse is selected
                    // (The popup menu listener would do this, but the click event
                    // might not bubble up perfectly after component removal, so we force it)
                    int row = table.rowAtPoint(tablePt);
                    if (row != -1 && !table.isRowSelected(row)) {
                        table.setRowSelectionInterval(row, row);
                    }

                    // Show the Table's popup at the correct location
                    popup.show(table, tablePt.x, tablePt.y);
                }
            };

            textArea.addMouseListener(rightClickForwarder);
            scrollPane.addMouseListener(rightClickForwarder); // Catch clicks on empty space/scrollbar
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
                textArea.setBackground(Themer.isDarkMode()?ColorTable.LYRICS_HIGHLIGHT_DARK.get():ColorTable.LYRICS_HIGHLIGHT_LIGHT.get());
            } else {
                textArea.setBackground(BACKGROUND_COLOR);
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

        public void addLine(LyricLine line) {
            lines.add(line);
            // Sort by tick to ensure the correct playback order
            lines.sort(Comparator.comparingLong(LyricLine::tick));
            fireTableDataChanged();
            pushChangesToSong();
        }

        public void moveLine(int row, long newStartTick) {
            if (row >= 0 && row < lines.size()) {
                LyricLine oldLine = lines.get(row);

                if (oldLine.tick() != newStartTick) {
                    // Create new record with updated tick but same text.
                    // The end tick we set to start tick if moving backwards,
                    // and if moving forward, we keep old though not let it be lower than newStartTick.
                    long newEndTick = Math.max(newStartTick, oldLine.endTick());
                    if (newStartTick < oldLine.tick()) newEndTick = newStartTick;
                    // Could also have let the user input a new end tick.
                    // But don't want to clutter the UI or make it complex,
                    // plus the endtick should really be the start of the last syllable to adhere to
                    // how we do it with lyrics from midi.

                    LyricLine newLine = new LyricLine(newStartTick, oldLine.text(), newEndTick);

                    lines.set(row, newLine);

                    // Re-sort to maintain chronological order
                    lines.sort(Comparator.comparingLong(LyricLine::tick));

                    fireTableDataChanged();
                    pushChangesToSong();
                }
            }
        }

        public void removeLine(int row) {
            if (row >= 0 && row < lines.size()) {
                lines.remove(row);
                fireTableDataChanged();
                pushChangesToSong();
            }
        }

        private void pushChangesToSong() {
            modified = true;

            // notify abcSong so the project can get the modified flag set:
            if (abcSong != null) abcSong.notifyLyricLinesModified();
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
                    // Create new record with same tick, updated text
                    lines.set(rowIndex, new LyricLine(old.tick(), newText, old.endTick()));
                    fireTableCellUpdated(rowIndex, columnIndex);
                    pushChangesToSong();
                }
            }
        }
    }
}