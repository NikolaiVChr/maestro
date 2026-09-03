package com.digero.maestro.view;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.text.ParseException;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.JLabel;

import com.digero.common.icons.IconLoader;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.TimeSignature;
import com.digero.common.util.ICompileConstants;
import com.digero.common.view.UIText;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.midi.Chord;
import com.digero.maestro.util.KeySignatureFormatter;
import com.digero.maestro.util.TimeSignatureFormatter;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;
import info.clearthought.layout.TableLayoutConstraints;

public class SongExportSettingsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final int HGAP = 4;
    private static final int VGAP = 4;

    private final JSpinner transposeSpinner;
    private final JSpinner tempoSpinner;

    private final JButton resetTempoButton;
    private final JButton exportButton;

    private final JLabel exportSuccessfulLabel;

    private final JFormattedTextField timeSignatureField;
    private final JFormattedTextField keySignatureField;

    private final JComboBox<TimingMode> timingModeCombo;
    private final JComboBox<Chord.CalcDynamics> dynamicChordModeCombo;

    private final JCheckBox countOnlyTempoChangesFromFirstTrackCheckBox;

    private final TableLayout layout;

    private SongExportSettingsListener actionListener;

    public SongExportSettingsPanel() {
        // Initialize components
        this.transposeSpinner = createJSpinner(
                new SpinnerNumberModel(0, -48, 48, 1),
                UIText.get("maestro.tip.transpose.semi.tones"));

        this.tempoSpinner = createJSpinner(
                new SpinnerNumberModel(MidiConstants.DEFAULT_TEMPO_BPM, 8, 960, 1),
                UIText.get("maestro.tip.tempo"));
        tempoSpinner.setEnabled(ICompileConstants.SHOW_TEMPO_SPINNER);

        this.resetTempoButton = createJButton(
                UIText.get("maestro.reset"),
                new Insets(2, 8, 2, 8),
                UIText.get("maestro.set.the.tempo.back.to.the.source.file.s.tempo"));

        this.exportButton = createJButton(
                null,
                UIText.get("maestro.html.b.export.abc.b.br.ctrl.e.html"),
                "abcfile_32.png",
                SwingConstants.LEFT);
        this.exportButton.setPreferredSize(new Dimension(300, this.exportButton.getPreferredSize().height));

        this.exportSuccessfulLabel = createJLabel(
                UIText.get("maestro.exported"),
                "check_16.png",
                BorderFactory.createEmptyBorder(0, 2, 0, 0));
        exportSuccessfulLabel.setVisible(false);

        this.timeSignatureField = createJFormattedTextField(
                TimeSignature.FOUR_FOUR,
                5,
                UIText.get("maestro.tip.time.signature"),
                new TimeSignatureFormatter());
        timeSignatureField.setEnabled(ICompileConstants.SHOW_METER_TEXTBOX);

        this.keySignatureField = createJFormattedTextField(
                KeySignature.C_MAJOR,
                5,
                "<html>Adjust the key signature of the ABC file. "
                        + "This only affects the display, not the sound of the exported file.<br>"
                        + "Examples: C maj, Eb maj, F# min</html>",
                new KeySignatureFormatter());
        keySignatureField.setEnabled(ICompileConstants.SHOW_KEY_FIELD);

        this.timingModeCombo = createJComboBox(TimingMode.values(), null);
        this.dynamicChordModeCombo = createJComboBox(Chord.CalcDynamics.values(),
                UIText.get("maestro.tip.dynamics", Chord.CalcDynamics.LOUDEST, Chord.CalcDynamics.POWER_RMS_DB,
                        Chord.CalcDynamics.POWER_MID_DB, Chord.CalcDynamics.WEIGHTED, Chord.CalcDynamics.POWER_MID_DB,
                        Chord.CalcDynamics.SOFTEST));
        dynamicChordModeCombo.setSelectedItem(AbcSong.dynamicsMethodDefault);

        this.countOnlyTempoChangesFromFirstTrackCheckBox = createJCheckBox(
                UIText.get("maestro.only.tempo.changes.from.first.track"),
                UIText.get("maestro.tip.tempo.first.track.only"));

        // Add listeners to the components to notify the action listener of changes
        transposeSpinner.addChangeListener(e -> notifyListener(SongExportSettingsListener::transposeSettingsChanged));

        if (ICompileConstants.SHOW_TEMPO_SPINNER) {
            tempoSpinner.addChangeListener(e -> notifyListener(SongExportSettingsListener::tempoSettingsChanged));
        }

        resetTempoButton.addActionListener(e -> notifyListener(SongExportSettingsListener::tempoResetRequested));

        if (ICompileConstants.SHOW_METER_TEXTBOX) {
            timeSignatureField.addPropertyChangeListener("value", e -> {
                if (e.getOldValue() != null && e.getOldValue().equals(e.getNewValue()))
                    return;

                notifyListener(SongExportSettingsListener::timeSignatureChanged);
            });
        }

        if (ICompileConstants.SHOW_KEY_FIELD) {
            keySignatureField.addPropertyChangeListener("value",
                    e -> notifyListener(SongExportSettingsListener::keySignatureChanged));
        }

        timingModeCombo.addActionListener(e -> notifyListener(SongExportSettingsListener::timingModeChanged));

        dynamicChordModeCombo.addActionListener(
                e -> notifyListener(SongExportSettingsListener::dynamicChordModeChanged));

        countOnlyTempoChangesFromFirstTrackCheckBox.addActionListener(
                e -> notifyListener(SongExportSettingsListener::countOnlyTempoChangesFromFirstTrackSettingsChanged));

        exportButton.addChangeListener(new ChangeListener() {
            private boolean pressed = false;

            @Override
            public void stateChanged(ChangeEvent e) {
                if (exportButton.getModel().isPressed() != pressed) {
                    pressed = exportButton.getModel().isPressed();
                    if (pressed)
                        exportSuccessfulLabel.setVisible(false);
                }
            }
        });

        exportButton.addActionListener(e -> notifyListener(SongExportSettingsListener::exportRequested));

        // Set up the layout and add components to the panel
        this.layout = new TableLayout(
                new double[] { TableLayoutConstants.PREFERRED, TableLayoutConstants.PREFERRED,
                        TableLayoutConstants.FILL },
                new double[] {});
        this.layout.setVGap(VGAP);
        this.layout.setHGap(HGAP);
        setLayout(this.layout);

        setBorder(BorderFactory.createTitledBorder(UIText.get("maestro.export.settings")));
        int row = 0;
        addRow(row++, createJLabel(UIText.get("maestro.transpose"), null, null), transposeSpinner);
        addRow(row++, createJLabel(UIText.get("maestro.tempo"), null, null), tempoSpinner, resetTempoButton);
        addLabelledSpanningRow(row++, createJLabel(UIText.get("maestro.meter"), null, null), timeSignatureField);

        if (ICompileConstants.SHOW_KEY_FIELD)
            addLabelledSpanningRow(row++, createJLabel(UIText.get("maestro.key"), null, null), keySignatureField);

        addSpanningRow(row++, timingModeCombo);
        addSpanningRow(row++, dynamicChordModeCombo);
        addSpanningRow(row++, countOnlyTempoChangesFromFirstTrackCheckBox);
        addSpanningRow(row++, exportSuccessfulLabel);
        addSpanningRow(row, exportButton);
    }

    /**
     * Creates a JSpinner with the specified model and tooltip.
     *
     * @param model   the SpinnerNumberModel for the JSpinner
     * @param tooltip the tooltip text for the JSpinner
     * @return the created JSpinner
     */
    private static JSpinner createJSpinner(SpinnerNumberModel model, String tooltip) {
        JSpinner spinner = new JSpinner(model);
        spinner.setToolTipText(tooltip);
        return spinner;
    }

    /**
     * Creates a JButton with the specified text, margin, and tooltip.
     * 
     * @param text    the text for the JButton
     * @param margin  the margin for the JButton
     * @param tooltip the tooltip text for the JButton
     * @return the created JButton
     */
    private static JButton createJButton(String text, Insets margin, String tooltip) {
        JButton button = new JButton(text);
        button.setMargin(margin);
        button.setToolTipText(tooltip);
        return button;
    }

    /**
     * Creates a JButton with the specified text, tooltip, icon, disabled icon, and
     * alignment.
     * 
     * @param text      the text for the JButton
     * @param tooltip   the tooltip text for the JButton
     * @param iconName  the name of the icon and disabled icon for the JButton
     * @param alignment the horizontal alignment for the JButton
     * @return the created JButton
     */
    private static JButton createJButton(String text, String tooltip, String iconName,
            int alignment) {
        JButton button = createJButton(text, null, tooltip);
        if (iconName != null) {
            button.setIcon(IconLoader.getImageIcon(iconName));
            button.setDisabledIcon(IconLoader.getDisabledIcon(iconName));
        }
        button.setHorizontalAlignment(alignment);
        return button;
    }

    /**
     * Creates a JLabel with the specified text, icon, and border.
     * 
     * @param text     the text for the JLabel
     * @param iconName the name of the icon for the JLabel
     * @param border   the border for the JLabel
     * @return the created JLabel
     */
    private static JLabel createJLabel(String text, String iconName, Border border) {
        JLabel label = new JLabel(text);
        if (iconName != null) {
            label.setIcon(IconLoader.getImageIcon(iconName));
        }
        if (border != null) {
            label.setBorder(border);
        }
        return label;
    }

    /**
     * Creates a JFormattedTextField with the specified value, columns, tooltip, and
     * formatter.
     * 
     * @param value     the initial value for the JFormattedTextField
     * @param columns   the number of columns for the JFormattedTextField
     * @param tooltip   the tooltip text for the JFormattedTextField
     * @param formatter the AbstractFormatter for the JFormattedTextField
     * @return the created JFormattedTextField
     */
    private static JFormattedTextField createJFormattedTextField(Object value, int columns, String tooltip,
            AbstractFormatter formatter) {
        JFormattedTextField field = new JFormattedTextField(formatter) {
            @Override
            protected void processFocusEvent(FocusEvent e) {
                super.processFocusEvent(e);
                if (e.getID() == FocusEvent.FOCUS_GAINED) {
                    selectAll();
                }
            }
        };

        field.setValue(value);
        field.setColumns(columns);
        field.setToolTipText(tooltip);
        field.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);

        KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        Object actionKey = field.getInputMap().get(enterKey);

        if (actionKey != null) {
            field.getActionMap().put(actionKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    try {
                        field.commitEdit();
                    } catch (ParseException ex) {
                        field.setValue(field.getValue());
                    }
                }
            });
        }

        return field;
    }

    /**
     * Creates a JCheckBox with the specified text and tooltip.
     * 
     * @param text    the text for the JCheckBox
     * @param tooltip the tooltip text for the JCheckBox
     * @return the created JCheckBox
     */
    private static JCheckBox createJCheckBox(String text, String tooltip) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.setToolTipText(tooltip);
        return checkBox;
    }

    /**
     * Creates a JComboBox with the specified items and tooltip.
     * 
     * @param <T>     the type of the items in the JComboBox
     * @param items   the items for the JComboBox
     * @param tooltip the tooltip text for the JComboBox
     * @return the created JComboBox<T>
     */
    private static <T> JComboBox<T> createJComboBox(T[] items, String tooltip) {
        JComboBox<T> comboBox = new JComboBox<>(items);
        comboBox.setToolTipText(tooltip);
        return comboBox;
    }

    /**
     * Adds a row of components to the panel using the TableLayout.
     * 
     * @param row        the row index to add the components to
     * @param components the components to add to the row
     */
    private void addRow(int row, Component... components) {
        layout.insertRow(row, TableLayoutConstants.PREFERRED);
        for (int col = 0; col < components.length; col++) {
            Component comp = components[col];
            add(comp, new TableLayoutConstraints(col, row));
        }
    }

    /**
     * Adds a component that spans multiple columns in the specified row.
     * 
     * @param row       the row index to add the component to
     * @param component the component to add that spans multiple columns
     */
    private void addSpanningRow(int row, Component component) {
        layout.insertRow(row, TableLayoutConstants.PREFERRED);
        add(component,
                new TableLayoutConstraints(
                        0, row,
                        2, row,
                        TableLayoutConstants.LEFT, TableLayoutConstants.CENTER));
    }

    /**
     * Adds a labeled component that spans multiple columns in the specified row.
     * 
     * @param row       the row index to add the components to
     * @param label     the label component
     * @param component the component to add that spans multiple columns
     */
    private void addLabelledSpanningRow(int row, Component label, Component component) {
        layout.insertRow(row, TableLayoutConstants.PREFERRED);

        add(label, new TableLayoutConstraints(0, row));

        add(component, new TableLayoutConstraints(
                1, row,
                2, row,
                TableLayoutConstants.LEFT,
                TableLayoutConstants.FULL));
    }

    public void setActionListener(SongExportSettingsListener listener) {
        this.actionListener = listener;
    }

    /**
     * Notifies the action listener of a user action by accepting a notification
     * consumer that calls the appropriate method on the listener.
     * 
     * @param notification A consumer that accepts the action listener and calls the
     *                     appropriate method for the user action.
     */
    private void notifyListener(Consumer<SongExportSettingsListener> notification) {
        if (actionListener != null) {
            notification.accept(actionListener);
        }
    }

    // Transpose methods
    public int getTranspose() {
        return (Integer) transposeSpinner.getValue();
    }

    public void setTranspose(int transpose) {
        transposeSpinner.setValue(transpose);
    }

    public void setTransposeSpinnerEnabled(boolean enabled) {
        transposeSpinner.setEnabled(enabled);
    }

    // Tempo methods
    public int getTempo() {
        return (Integer) tempoSpinner.getValue();
    }

    public void setTempo(int tempo) {
        tempoSpinner.setValue(tempo);
    }

    public void setTempoSpinnerEnabled(boolean enabled) {
        tempoSpinner.setEnabled(enabled);
    }

    // Time signature methods
    public TimeSignature getTimeSignature() {
        return (TimeSignature) timeSignatureField.getValue();
    }

    public void setTimeSignature(TimeSignature timeSignature) {
        timeSignatureField.setValue(timeSignature);
    }

    public void setTimeSignatureFieldEnabled(boolean enabled) {
        timeSignatureField.setEnabled(enabled);
    }

    // Key signature methods
    public KeySignature getKeySignature() {
        return (KeySignature) keySignatureField.getValue();
    }

    public void setKeySignature(KeySignature keySignature) {
        keySignatureField.setValue(keySignature);
    }

    public void setKeySignatureFieldEnabled(boolean enabled) {
        keySignatureField.setEnabled(enabled);
    }

    // Timing mode methods
    public TimingMode getTimingMode() {
        return (TimingMode) timingModeCombo.getSelectedItem();
    }

    public void setTimingMode(TimingMode timingMode) {
        timingModeCombo.setSelectedItem(timingMode);
    }

    public void setTimingModeToolTipText(String tooltip) {
        timingModeCombo.setToolTipText(tooltip);
    }

    public void setTimingModeComboEnabled(boolean enabled) {
        timingModeCombo.setEnabled(enabled);
    }

    // Dynamic chord mode methods
    public Chord.CalcDynamics getDynamicChordMode() {
        return (Chord.CalcDynamics) dynamicChordModeCombo.getSelectedItem();
    }

    public void setDynamicChordMode(Chord.CalcDynamics dynamicsMode) {
        dynamicChordModeCombo.setSelectedItem(dynamicsMode);
    }

    public void setDynamicChordModeComboEnabled(boolean enabled) {
        dynamicChordModeCombo.setEnabled(enabled);
    }

    // Count only tempo changes from first track methods
    public boolean isCountOnlyTempoChangesFromFirstTrackSelected() {
        return countOnlyTempoChangesFromFirstTrackCheckBox.isSelected();
    }

    public void setCountOnlyTempoChangesFromFirstTrackSelected(boolean selected) {
        countOnlyTempoChangesFromFirstTrackCheckBox.setSelected(selected);
    }

    public void setCountOnlyTempoChangesFromFirstTrackCheckBoxEnabled(boolean enabled) {
        countOnlyTempoChangesFromFirstTrackCheckBox.setEnabled(enabled);
    }

    // Export button methods
    public void updateExportButton(boolean exportAsAbc) {
        String exportText = exportAsAbc ? UIText.get("maestro.export.abc.as") : UIText.get("maestro.export.abc");
        if (!exportButton.getText().equals(exportText)) {
            exportButton.setText(exportText);
            exportButton.repaint();
        }
    }

    public void setExportButtonEnabled(boolean enabled) {
        exportButton.setEnabled(enabled);
    }

    // Reset tempo button methods
    public void setResetTempoButtonEnabledAndVisible(boolean enabled) {
        resetTempoButton.setEnabled(enabled);
        resetTempoButton.setVisible(enabled);
    }

    // Export successful label methods
    public void setExportSuccessfulLabelText(String name) {
        exportSuccessfulLabel.setText(name);
    }

    public void setExportSuccessfulLabelToolTipText(String string) {
        exportSuccessfulLabel.setToolTipText(string);
    }

    public void setExportSuccessfulLabelVisible(boolean b) {
        exportSuccessfulLabel.setVisible(b);
    }

    public void commitAllFields() throws ParseException {
        tempoSpinner.commitEdit();
        transposeSpinner.commitEdit();
        timeSignatureField.commitEdit();
        keySignatureField.commitEdit();
    }
}
