package com.digero.maestro.view.song;

import java.util.Arrays;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.digero.common.view.UIText;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

public class SongInfoPanel extends JPanel {

    private SongInfoChangeListener changeListener;
    private boolean updatingFields;

    private TableLayout layout;

    private JTextField titleField;
    private JTextField composerField;
    private JTextField transcriberField;
    private JTextField genreField;
    private JTextField moodField;

    private static final int HGAP = 4;
    private static final int VGAP = 4;

    /**
     * 
     */
    public SongInfoPanel(boolean showGenreAndMood, String defaultTranscriber) {
        titleField = createTextField("", "maestro.song.title");
        composerField = createTextField("", "maestro.song.composer.artist");
        transcriberField = createTextField(defaultTranscriber, "maestro.song.transcriber.your.name");
        genreField = createTextField("", "maestro.song.genre.s");
        moodField = createTextField("", "maestro.song.mood.s");

        addChangeListener(titleField);
        addChangeListener(composerField);
        addChangeListener(transcriberField);
        addChangeListener(genreField);
        addChangeListener(moodField);

        int rowCount = showGenreAndMood ? 5 : 3;

        layout = new TableLayout(
                new double[] { TableLayoutConstants.PREFERRED, TableLayoutConstants.FILL },
                createRows(rowCount));

        layout.setHGap(HGAP);
        layout.setVGap(VGAP);
        setLayout(layout);

        int row = 0;
        addRow("T:", titleField, row++);
        addRow("C:", composerField, row++);
        addRow("Z:", transcriberField, row++);

        if (showGenreAndMood) {
            addRow("G:", genreField, row++);
            addRow("M:", moodField, row);
        }
        setBorder(BorderFactory.createTitledBorder(
                UIText.get("maestro.song.info")));
    }

    /**
     * 
     * 
     * @param initialText
     * @param tooltipKey
     * @return The created field
     */
    private JTextField createTextField(String initialText, String tooltipKey) {
        JTextField field = new JTextField(initialText);
        field.setToolTipText(UIText.get(tooltipKey));
        return field;
    }

    /**
     * 
     * @param listener
     */
    public void setChangeListener(SongInfoChangeListener listener) {
        changeListener = listener;
    }

    /**
     * 
     * @param field
     */
    private void addChangeListener(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {

            @Override
            public void insertUpdate(DocumentEvent e) {
                // typing or pasting text
                notifyChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                // deleting text
                notifyChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // attribute or style changes
                notifyChanged();
            }

        });
    }

    /**
     * 
     */
    private void notifyChanged() {
        if (!updatingFields && changeListener != null) {
            changeListener.songInfoChanged(getSongInfo());
        }
    }

    /**
     * 
     * @return
     */
    public SongInfo getSongInfo() {
        return new SongInfo(
                titleField.getText(),
                composerField.getText(),
                transcriberField.getText(),
                genreField.getText(),
                moodField.getText());
    }

    /**
     * 
     * @param songInfo
     */
    public void setSongInfo(SongInfo songInfo) {
        Objects.requireNonNull(songInfo, "song info must not be null");

        // The updatingFields flag is important. Without it, calling setSongInfo() while
        // opening a project would trigger the same listener and make the freshly loaded
        // project appear modified. @see notifyChanged()
        updatingFields = true;

        try {
            setTextIfChanged(titleField, songInfo.title());
            setTextIfChanged(composerField, songInfo.composer());
            setTextIfChanged(transcriberField, songInfo.transcriber());
            setTextIfChanged(genreField, songInfo.genre());
            setTextIfChanged(moodField, songInfo.mood());
        } finally {
            updatingFields = false;
        }
    }

    private void setTextIfChanged(JTextField field, String value) {
        String text = value != null ? value : "";

        if (!field.getText().equals(text)) {
            field.setText(text);
            field.setCaretPosition(0);
        }
    }

    public void clearSongInfo() {
        setSongInfo(SongInfo.empty());
    }

    /**
     * 
     * @param count
     * @return
     */
    private static double[] createRows(int count) {
        double[] rows = new double[count];
        Arrays.fill(rows, TableLayoutConstants.PREFERRED);
        return rows;
    }

    /**
     * 
     * @param label
     * @param field
     * @param row
     */
    private void addRow(String label, JTextField field, int row) {
        add(new JLabel(label), "0, " + row);
        add(field, "1, " + row);
    }

    public void setEditingEnabled(boolean enabled) {
        titleField.setEnabled(enabled);
        composerField.setEnabled(enabled);
        transcriberField.setEnabled(enabled);
        genreField.setEnabled(enabled);
        moodField.setEnabled(enabled);
    }

    public void setGenreAndMoodVisible(boolean visible) {
        genreField.setVisible(visible);
        moodField.setVisible(visible);

        layout.setRow(createRows(visible ? 5 : 3));

        revalidate();
        repaint();
    }
}
