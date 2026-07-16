package com.digero.maestro.view;

import java.awt.Component;
import java.awt.Insets;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
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

    private final JComboBox<TimingMode> timingCombo;
    private final JComboBox<Chord.CalcDynamics> dynaCombo;

    private final JCheckBox tempoOnlyFirstCheckBox;

    private final TableLayout layout;

    public SongExportSettingsPanel() {
        this.transposeSpinner = createJSpinner(
                new SpinnerNumberModel(0, -48, 48, 1),
                UIText.get("maestro.tip.transpose.semi.tones"));
        this.tempoSpinner = createJSpinner(
                new SpinnerNumberModel(MidiConstants.DEFAULT_TEMPO_BPM, 8, 960, 1),
                UIText.get("maestro.tip.tempo"));

        this.resetTempoButton = createJButton(
                UIText.get("maestro.reset"),
                new Insets(2, 8, 2, 8),
                UIText.get("maestro.set.the.tempo.back.to.the.source.file.s.tempo"));
        this.exportButton = createJButton(
                UIText.get("maestro.menu.export.abc"),
                UIText.get("maestro.html.b.export.abc.b.br.ctrl.e.html"),
                "abcfile_32.png",
                SwingConstants.LEFT);

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
        this.keySignatureField = createJFormattedTextField(
                KeySignature.C_MAJOR,
                5,
                "<html>Adjust the key signature of the ABC file. "
                        + "This only affects the display, not the sound of the exported file.<br>"
                        + "Examples: C maj, Eb maj, F# min</html>",
                new KeySignatureFormatter());

        this.timingCombo = createJComboBox(TimingMode.values(), null);
        this.dynaCombo = createJComboBox(Chord.CalcDynamics.values(),
                UIText.get("maestro.tip.dynamics", Chord.CalcDynamics.LOUDEST, Chord.CalcDynamics.POWER_RMS_DB,
                        Chord.CalcDynamics.POWER_MID_DB, Chord.CalcDynamics.WEIGHTED, Chord.CalcDynamics.POWER_MID_DB,
                        Chord.CalcDynamics.SOFTEST));
        dynaCombo.setSelectedItem(AbcSong.dynamicsMethodDefault);

        this.tempoOnlyFirstCheckBox = createJCheckBox(
                UIText.get("maestro.only.tempo.changes.from.first.track"),
                UIText.get("maestro.tip.tempo.first.track.only"));

        this.layout = new TableLayout(
                new double[] { TableLayoutConstants.PREFERRED, TableLayoutConstants.PREFERRED,
                        TableLayoutConstants.FILL },
                new double[] {});
        this.layout.setVGap(VGAP);
        this.layout.setHGap(HGAP);
        setBorder(BorderFactory.createTitledBorder(UIText.get("maestro.export.settings")));
        int row = 0;
        addRow(row++, createJLabel(UIText.get("maestro.transpose"), null, null), transposeSpinner);
        addRow(row++, createJLabel(UIText.get("maestro.tempo"), null, null), tempoSpinner, resetTempoButton);
        addRow(row++, createJLabel(UIText.get("maestro.meter"), null, null), timeSignatureField);
        
        if (ICompileConstants.SHOW_KEY_FIELD) 
            addRow(row++, createJLabel(UIText.get("maestro.key"), null, null), keySignatureField);
        
        addRow(row++, timingCombo);
        addRow(row++, dynaCombo);
        addRow(row++, tempoOnlyFirstCheckBox);
        addRow(row++, exportSuccessfulLabel);
        addRow(row, exportButton);
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
            protected void processFocusEvent(java.awt.event.FocusEvent e) {
                super.processFocusEvent(e);
                if (e.getID() == java.awt.event.FocusEvent.FOCUS_GAINED)
                    selectAll();
            }
        };
        field.setValue(value);
        field.setColumns(columns);
        field.setToolTipText(tooltip);
        // the behavoir JFormattedTextField.COMMIT_OR_REVERT is the default
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
        for (int col = 0; col < components.length; col++) {
            Component comp = components[col];
            layout.addLayoutComponent(comp, new TableLayoutConstraints(col, row));
            add(comp);
        }
    }

    
    public void deactivateListeners() {

    }

    public int getTranspose() {
        return (Integer) transposeSpinner.getValue();
    }

    public void setTranspose(int transpose) {
        transposeSpinner.setValue(transpose);
    }

    public int getTempo() {
        return (Integer) tempoSpinner.getValue();
    }

    public void setTempo(int tempo) {
        tempoSpinner.setValue(tempo);
    }

}
