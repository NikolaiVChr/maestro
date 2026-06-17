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

    private final TableLayout layout;

    private final JTextField titleField;
    private final JTextField composerField;
    private final JTextField transcriberField;
    private final JTextField genreField;
    private final JTextField moodField;

    private final JLabel genreLabel = new JLabel("G:");
    private final JLabel moodLabel = new JLabel("M:");

    private static final int HGAP = 4;
    private static final int VGAP = 4;

    /**
     * Builds the song metadata editor used by the project frame.
     *
     * @param showGenreAndMood   Whether the optional genre and mood rows should be
     *                           visible.
     * @param defaultTranscriber The initial transcriber text to show before a song
     *                           is loaded.
     */
    public SongInfoPanel(boolean showGenreAndMood, String defaultTranscriber) {
        titleField = createTextField("", "maestro.song.title");
        composerField = createTextField("", "maestro.song.composer.artist");
        transcriberField = createTextField(defaultTranscriber, "maestro.song.transcriber.your.name");
        genreField = createTextField("", "maestro.song.genre.s");
        moodField = createTextField("", "maestro.song.mood.s");

        addChangeListener(titleField, SongInfoField.TITLE);
        addChangeListener(composerField, SongInfoField.COMPOSER);
        addChangeListener(transcriberField, SongInfoField.TRANSCRIBER);
        addChangeListener(genreField, SongInfoField.GENRE);
        addChangeListener(moodField, SongInfoField.MOOD);

        layout = new TableLayout(
                new double[] { TableLayoutConstants.PREFERRED, TableLayoutConstants.FILL },
                createRows(5));

        layout.setHGap(HGAP);
        layout.setVGap(VGAP);
        setLayout(layout);

        int row = 0;
        addRow(new JLabel("T:"), titleField, row++);
        addRow(new JLabel("C:"), composerField, row++);
        addRow(new JLabel("Z:"), transcriberField, row++);
        addRow(genreLabel, genreField, row++);
        addRow(moodLabel, moodField, row);

        setGenreAndMoodVisible(showGenreAndMood);

        setBorder(BorderFactory.createTitledBorder(
                UIText.get("maestro.song.info")));
    }

    /**
     * Creates one text field with localized help text.
     *
     * @param initialText The text to place in the field when it is created.
     * @param tooltipKey  The {@link UIText} key used for the field tooltip.
     * @return The created field
     */
    private static JTextField createTextField(String initialText, String tooltipKey) {
        JTextField field = new JTextField(initialText);
        field.setToolTipText(UIText.get(tooltipKey));
        return field;
    }

    /**
     * Registers the callback that receives user edits as complete {@link SongInfo}
     * snapshots.
     *
     * @param listener The listener to notify when the user changes a metadata
     *                 field.
     */
    public void setChangeListener(SongInfoChangeListener listener) {
        changeListener = listener;
    }

    /**
     * Attaches document notifications so edits, deletes, and styled-document
     * changes all share
     * the same update path.
     *
     * @param field The text field whose document should trigger song-info changes.
     */
    private void addChangeListener(JTextField field, SongInfoField infoField) {
        field.getDocument().addDocumentListener(new DocumentListener() {

            /**
             * Sends the current panel contents to the registered listener unless the panel
             * is being
             * refreshed programmatically.
             */
            private void notifyChanged() {
                if (!updatingFields && changeListener != null) {
                    changeListener.songInfoChanged(infoField, getSongInfo());
                }
            }

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
     * Reads the current text from every metadata field.
     *
     * @return A new {@link SongInfo} snapshot matching the visible editor state.
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
     * Copies metadata into the text fields without reporting the copy as a user
     * edit.
     *
     * @param songInfo The metadata to show in the editor.
     */
    public void setSongInfo(SongInfo songInfo) {
        Objects.requireNonNull(songInfo, "song info must not be null");

        // Programmatic field changes fire DocumentEvents too. Suppress them here so
        // loading
        // or refreshing a project does not look like a user edit; see notifyChanged().
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

    /**
     * Updates a field only when its text actually differs, keeping existing caret
     * state stable
     * when no update is needed.
     */
    private static void setTextIfChanged(JTextField field, String value) {
        String text = value != null ? value : "";

        if (!field.getText().equals(text)) {
            field.setText(text);
            field.setCaretPosition(0);
        }
    }

    /**
     * Clears song-specific metadata while preserving the current transcriber value.
     */
    public void clearSongInfo() {
        setSongInfo(new SongInfo(
                "",
                "",
                transcriberField.getText(),
                "",
                ""));
    }

    /**
     * Creates the row-size array expected by {@link TableLayout}.
     *
     * @param count The number of preferred-height rows to create.
     * @return A row-size array with each row set to
     *         {@link TableLayoutConstants#PREFERRED}.
     */
    private static double[] createRows(int count) {
        double[] rows = new double[count];
        Arrays.fill(rows, TableLayoutConstants.PREFERRED);
        return rows;
    }

    /**
     * Adds one label/field pair to the two-column table layout.
     *
     * @param label The ABC metadata label.
     * @param field The text field edited on that row.
     * @param row   The zero-based table row index.
     */
    private void addRow(JLabel label, JTextField field, int row) {
        add(label, "0, " + row);
        add(field, "1, " + row);
    }

    /**
     * Enables or disables editing for every metadata field at once.
     *
     * @param enabled Whether the fields should accept user edits.
     */
    public void setEditingEnabled(boolean enabled) {
        titleField.setEnabled(enabled);
        composerField.setEnabled(enabled);
        transcriberField.setEnabled(enabled);
        genreField.setEnabled(enabled);
        moodField.setEnabled(enabled);
    }

    /**
     * Shows or hides the optional Badger-style genre and mood fields.
     *
     * @param visible Whether the genre and mood rows should be part of the visible
     *                form.
     */
    public void setGenreAndMoodVisible(boolean visible) {
        genreLabel.setVisible(visible);
        genreField.setVisible(visible);
        moodLabel.setVisible(visible);
        moodField.setVisible(visible);

        layout.setRow(createRows(visible ? 5 : 3));
        revalidate();
        repaint();
    }
}
