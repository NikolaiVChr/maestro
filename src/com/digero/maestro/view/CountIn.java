package com.digero.maestro.view;

import com.digero.common.abc.Dynamics;
import com.digero.common.midi.Note;
import com.digero.common.midi.TimeSignature;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.LotroCombiDrumInfo;
import com.digero.maestro.abc.LotroDrumInfo;

import java.awt.*;
import javax.swing.*;

import java.util.ArrayList;
import java.util.List;

import static com.digero.maestro.view.CountIn.CountInDynamics.*;
import static com.digero.maestro.view.CountIn.CountInPattern.*;

public class CountIn {
    public float barCount = 1.0f;
    public CountInPattern pattern = null;
    public AbcPart part = null;
    public LotroDrumInfo hit = null;
    public long micros;// set by abcExporter

    public CountIn(CountInPattern pattern, float barCount, AbcPart part, LotroDrumInfo hit) {
        this.pattern = pattern;
        this.barCount = barCount;
        this.part = part;
        this.hit = hit;
    }

    public enum CountInPattern {
        OFF ("Off",
                "",
                new TimeSignature(4,4), 1,
                new CountInDynamics[]{}),
        ONE_TWO_THREE_FOUR ("1 2 3 4",
                "(4/4)",
                new TimeSignature(4,4), 1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND_FOUR_AND ("1 and 2 and 3 and 4 and",
                "(4/4, 2/2, 12/8)",
                new TimeSignature(4,4), 1,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE_FOUR_2 ("1 2 3 4 | 1 2 3 4",
                "(4/4, 2/2, 12/8)",
                new TimeSignature(4,4), 2,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,ACCENTED,STANDARD,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND_FOUR_AND_2 ("1 and 2 and 3 and 4 | 1 and 2 and 3 and 4",
                "(4/4, 2/2, 12/8)",
                new TimeSignature(4,4), 2,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE ("1 2 3",
                "(3/4, 6/8)",
                new TimeSignature(3,4), 1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND ("1 and 2 and 3 and",
                "(3/4, 6/8)",
                new TimeSignature(3,4), 1,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE_2 ("1 2 3 | 1 2 3",
                "(3/4, 6/8)",
                new TimeSignature(3,4), 2,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,ACCENTED,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND_2 ("1 and 2 and 3 and | 1 and 2 and 3 and",
                "(3/4, 6/8)",
                new TimeSignature(3,4), 2,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO ("1 2",
                "(2/4, 2/2, 4/4)",
                new TimeSignature(2,4), 1,
                new CountInDynamics[]{ACCENTED,STANDARD}),
        ONE_AND_TWO_AND ("1 and 2 and",
                "(2/4)",
                new TimeSignature(2,4), 1,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_2 ("1 2 | 1 2",
                "(2/4, 2/2)",
                new TimeSignature(2,4), 2,
                new CountInDynamics[]{ACCENTED,STANDARD,ACCENTED,STANDARD}),
        ONE_AND_TWO_AND_2 ("1 and 2 and | 1 and 2 and",
                "(2/4)",
                new TimeSignature(2,4), 2,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE_FOUR_FIVE ("1 2 3 4 5",
                "(5/4, 5/8)",
                new TimeSignature(5,4), 1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,STANDARD}),
        ONE_TWO_THREE_FOUR_FIVE_SIX ("1 2 3 4 5 6",
                "(6/8)",
                new TimeSignature(6,8), 1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD}),
        ONE_TWO_THREE_FOUR_FIVE_SIX_SEVEN ("1 2 3 4 5 6 7",
                "(7/4, 7/8)",
                new TimeSignature(7,8), 1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD}),
        ONE_AND_A_TWO_AND_A ("1 and a 2 and a",
                "(6/8 jig, 12/8 compound)",
                new TimeSignature(6,8), 1,
                new CountInDynamics[]{STANDARD,SOFT,SOFT,STANDARD,SOFT,SOFT}),
        FOUR_TRIPLETS ("1-trip-let 2-trip-let 3-trip-let 4-trip-let",
                "(4/4 swing)",
                new TimeSignature(4,4), 1,
                new CountInDynamics[]{ACCENTED_TRIPLET,STANDARD,STANDARD,ACCENTED_TRIPLET,STANDARD,STANDARD,ACCENTED_TRIPLET,STANDARD,STANDARD,ACCENTED_TRIPLET,STANDARD,STANDARD}),
        THREE_TRIPLETS ("1-trip-let 2-trip-let 3-trip-let",
                "(3/4 jazz waltz)",
                new TimeSignature(3,4), 1,
                new CountInDynamics[]{ACCENTED_TRIPLET,STANDARD,STANDARD,ACCENTED_TRIPLET,STANDARD,STANDARD,ACCENTED_TRIPLET,STANDARD,STANDARD}),
        TWO_TRIPLETS ("1-trip-let 2-trip-let",
                "(6/8, 12/8, 4/4 swing, 3/4 swing)",
                new TimeSignature(6,8), 1,
                new CountInDynamics[]{ACCENTED_TRIPLET,STANDARD,STANDARD,ACCENTED_TRIPLET,STANDARD,STANDARD}),
        /*
        ONE_TRIP_LET_TWO_TRIP_LET_THREE_TRIP_LET_FOUR_TRIP_LET_2 ("1-trip-let 2-trip-let 3-trip-let 4-trip-let | 1-trip-let 2-trip-let 3-trip-let 4-trip-let",
                new TimeSignature(4,4), 2,
                new CountInDynamics[]{STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD}),
        ONE_TRIP_LET_TWO_TRIP_LET_THREE_TRIP_LET_2 ("1-trip-let 2-trip-let 3-trip-let | 1-trip-let 2-trip-let 3-trip-let",
                new TimeSignature(3,4), 2,
                new CountInDynamics[]{STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD})
        */
        ;

        final String name;
        final String usedBy;
        final TimeSignature timeSignature;
        public final CountInDynamics[] dynamics;
        final int bars;

        CountInPattern(String name, String usedBy, TimeSignature timeSignature, int bars, CountInDynamics[] dynamics) {
            this.name = name;
            this.usedBy = usedBy;
            this.timeSignature = timeSignature;
            this.dynamics = dynamics;
            this.bars = bars;
        }

        public String toString() {
            return name + "     " + usedBy;
        }
    }

    public enum CountInDynamics {
        ACCENTED (Dynamics.ff),
        ACCENTED_TRIPLET (Dynamics.f),
        STANDARD (Dynamics.mf),
        SOFT (Dynamics.p);

        public final Dynamics dynamics;
        CountInDynamics(Dynamics dynamics) {
            this.dynamics = dynamics;
        }
    }

    private static class PatternRenderer implements ListCellRenderer<CountInPattern> {
        private final JPanel panel;
        private final JLabel leftLabel;
        private final JLabel rightLabel;

        private PatternRenderer() {
            panel = new JPanel(new BorderLayout());
            leftLabel = new JLabel();
            rightLabel = new JLabel();

            leftLabel.setHorizontalAlignment(SwingConstants.LEFT);
            rightLabel.setHorizontalAlignment(SwingConstants.RIGHT);

            // Set a smaller font for the right label to keep it compact
            rightLabel.setFont(rightLabel.getFont().deriveFont(rightLabel.getFont().getSize() * 0.85f));

            panel.add(leftLabel, BorderLayout.WEST);
            panel.add(rightLabel, BorderLayout.EAST);

            // Ensure panel respects the original cell height
            panel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CountInPattern> list, CountInPattern value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {

            leftLabel.setText(value.name);
            rightLabel.setText(value.usedBy);

            // Apply selection colors
            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();

            leftLabel.setForeground(fg);
            rightLabel.setForeground(fg);
            panel.setBackground(bg);
            panel.setOpaque(isSelected);

            // Make the right label slightly dimmer when not selected
            if (!isSelected && !value.usedBy.isEmpty()) {
                rightLabel.setForeground(fg.darker());
            }

            return panel;
        }
    }


    private static class PatternRenderer2 extends JLabel implements ListCellRenderer<CountInPattern> {

        private PatternRenderer2() {
            super();
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CountInPattern> list, CountInPattern value, int index, boolean isSelected, boolean cellHasFocus) {

            String leftText = value.name;
            String rightText = value.usedBy;

            // Use HTML table to align text
            String htmlText = String.format(
                    "<html><table width='100%%'><tr><td align='left'>%s</td><td align='right'>%s</td></tr></table></html>",
                    leftText, rightText
            );

            setText(htmlText);
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());

            return PatternRenderer2.this;
        }
    }

    private static JDialog dialog = null;
    private static CountIn lastCountIn = null;
    private static AbcPart currentPart = null;

    public static CountIn show(Component parent, AbcPart part, CountIn lastCountIn) {
        if (dialog == null) {
            dialog = new JDialog();
            dialog.setTitle("Count-in");
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            GridBagLayout layout = new GridBagLayout();
            dialog.setLayout(layout);
            dialog.setSize(250, 200);
            dialog.setLocationRelativeTo(parent);
            dialog.setResizable(false);
            dialog.setModal(true);
            JComboBox<CountInPattern> patternBox = new JComboBox<>(CountInPattern.values());
            patternBox.setRenderer(new PatternRenderer());
            patternBox.setMaximumRowCount(CountInPattern.values().length);
            List<LotroDrumInfo> usableDIs = new ArrayList<>();
            for (LotroDrumInfo di : LotroDrumInfo.ALL_DRUMS) {
                if (di.note == Note.REST) continue;
                if (di.note.id >= LotroCombiDrumInfo.minCombi.id) continue;
                usableDIs.add(di);
            }
            JComboBox<LotroDrumInfo> hitBox = new JComboBox<>(usableDIs.toArray(new LotroDrumInfo[]{}));

            JLabel barText = new JLabel("Bars");
            JTextField barField = new JTextField(String.format("%.2f", 1.0f));

            barText.setToolTipText("Count-in cannot be longer than 6 seconds,\nand must not be overly fast.");
            barField.setToolTipText("Count-in cannot be longer than 6 seconds,\nand must not be overly fast.");
            patternBox.setToolTipText("If this count-in is activated then any count-in\non other drum parts will be cancelled.");

            barField.setHorizontalAlignment(SwingConstants.CENTER);
            if (lastCountIn != null) {
                patternBox.setSelectedItem(lastCountIn.pattern);
                hitBox.setSelectedItem(lastCountIn.hit);
                barField.setText(String.format("%.2f", (float)lastCountIn.barCount));
            } else {
                /*
                TimeSignature time = part.getAbcSong().getTimeSignature();
                for (CountInPattern pattern : CountInPattern.values()) {
                    if (pattern.timeSignature.equals(time) && pattern.bars == 1) {
                        patternBox.setSelectedItem(pattern);
                        break;
                    }
                }
                */
                patternBox.setSelectedItem(OFF);
                hitBox.setSelectedItem(LotroDrumInfo.getById(Note.Ds3.id));// Rimshot 1
            }

            patternBox.addActionListener(e -> {
                CountIn.lastCountIn = getCountIn(patternBox, barField, hitBox, currentPart);
            });
            barField.addActionListener(e -> {
                CountIn.lastCountIn = getCountIn(patternBox, barField, hitBox, currentPart);
            });
            hitBox.addActionListener(e -> {
                CountIn.lastCountIn = getCountIn(patternBox, barField, hitBox, currentPart);
            });
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                    // Handle window closing event here
                    // For example, you might want to save the current state
                    CountIn.lastCountIn = getCountIn(patternBox, barField, hitBox, currentPart);
                }

                @Override
                public void windowClosed(java.awt.event.WindowEvent windowEvent) {

                }
            });


            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 8, 6, 8);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Row 0: pattern combo (spans 2 columns)
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            dialog.getContentPane().add(patternBox, gbc);

            // Row 1: "Bars" label
            gbc = (GridBagConstraints) gbc.clone();
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.gridwidth = 1;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.LINE_START;
            dialog.getContentPane().add(barText, gbc);

            // Row 1: bars text field
            gbc = (GridBagConstraints) gbc.clone();
            gbc.gridx = 1;
            gbc.gridy = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.CENTER;
            dialog.getContentPane().add(barField, gbc);

            // Row 2: hit drum combo (spans 2 columns)
            gbc = (GridBagConstraints) gbc.clone();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            dialog.getContentPane().add(hitBox, gbc);

        }
        currentPart = part;
        CountIn.lastCountIn = lastCountIn;
        dialog.setVisible(!dialog.isVisible());
        return CountIn.lastCountIn;
    }

    private static CountIn getCountIn(JComboBox<CountInPattern> comboBox, JTextField txtField, JComboBox<LotroDrumInfo> hitBox, AbcPart part) {
        CountInPattern pattern = (CountInPattern) comboBox.getSelectedItem();
        LotroDrumInfo hit = (LotroDrumInfo) hitBox.getSelectedItem();

        float barCount = (float) pattern.bars;
        try {
            barCount = Float.parseFloat(txtField.getText().replace(',', '.'));
        } catch (NumberFormatException nfe) {
            txtField.setText(String.format("%.2f", barCount));
            return null;
        }
        if (hit.note == Note.REST) return null;
        if (pattern == OFF) return null;
        return new CountIn(pattern, barCount, part, hit);
    }
}
