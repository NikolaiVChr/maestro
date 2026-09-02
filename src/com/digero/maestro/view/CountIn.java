package com.digero.maestro.view;

import com.digero.common.abc.Dynamics;
import com.digero.common.midi.Note;
import com.digero.common.view.UIText;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.LotroCombiDrumInfo;
import com.digero.maestro.abc.LotroDrumInfo;

import java.awt.*;
import javax.swing.*;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.digero.maestro.view.CountIn.CountInDynamics.*;
import static com.digero.maestro.view.CountIn.CountInPattern.*;

public class CountIn {
    public final float barCount;
    public final CountInPattern pattern;
    public final AbcPart part;
    public final LotroDrumInfo hit;
    public long micros;// in abc export time. set by abcExporter

    public CountIn(CountInPattern pattern, float barCount, AbcPart part, LotroDrumInfo hit) {
        this.pattern = pattern;
        this.barCount = barCount;
        this.part = part;
        this.hit = hit;
    }

    /**
     * Constructs a CountIn object from the given MSX strings.
     * Throws exceptions if the given strings are invalid.
     */
    public CountIn(String pattern, String barCount, AbcPart part, String hitId) {
        this.pattern = CountInPattern.valueOf(pattern);
        this.barCount = Float.parseFloat(barCount);
        this.part = part;
        this.hit = LotroDrumInfo.getById(Integer.parseInt(hitId));
        if (this.hit == null) {
            throw new IllegalArgumentException("Invalid hit ID: " + hitId);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o == this) return true;
        if (!(o instanceof CountIn that)) return false;
        if (pattern != that.pattern) return false;
        if (barCount != that.barCount) return false;
        if (hit != that.hit) return false;
        if (part != that.part) return false;
        return true;
    }

    public enum CountInPattern {
        OFF (UIText.get("maestro.countin.off"),
                "",
                //new TimeSignature(4,4),
                1,
                new CountInDynamics[]{}),
        ONE_TWO_THREE_FOUR ("1 2 3 4",
                "(4/4)",
                //new TimeSignature(4,4),
                1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND_FOUR_AND (UIText.get("maestro.countin.1.and.2.and.3.and.4.and"),
                "(4/4, 2/2, 12/8)",
                //new TimeSignature(4,4),
                1,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE_FOUR_2 ("1 2 3 4 | 1 2 3 4",
                "(4/4, 2/2, 12/8)",
                //new TimeSignature(4,4),
                2,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,ACCENTED,STANDARD,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND_FOUR_AND_2 (UIText.get("maestro.countin.1.and.2.and.3.and.4.1.and.2.and.3.and.4"),
                "(4/4, 2/2, 12/8)",
                //new TimeSignature(4,4),
                2,
                new CountInDynamics[]{
                        STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,
                        STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT
                        }),
        ONE_TWO_THREE ("1 2 3",
                "(3/4, 6/8)",
                //new TimeSignature(3,4),
                1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND (UIText.get("maestro.countin.1.and.2.and.3.and"),
                "(3/4, 6/8)",
                //new TimeSignature(3,4),
                1,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE_2 ("1 2 3 | 1 2 3",
                "(3/4, 6/8)",
                //new TimeSignature(3,4),
                2,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,ACCENTED,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND_2 (UIText.get("maestro.countin.1.and.2.and.3.and.1.and.2.and.3.and"),
                "(3/4, 6/8)",
                //new TimeSignature(3,4),
                2,
                new CountInDynamics[]{
                        STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,
                        STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT
                        }),
        ONE_TWO ("1 2",
                UIText.get("maestro.countin.2.4.2.2.4.4.6.8.dotted"),
                //new TimeSignature(2,4),
                1,
                new CountInDynamics[]{ACCENTED,STANDARD}),
        ONE_AND_TWO_AND (UIText.get("maestro.countin.1.and.2.and"),
                "(2/4)",
                //new TimeSignature(2,4),
                1,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_2 ("1 2 | 1 2",
                UIText.get("maestro.countin.2.4.2.2.6.8.dotted"),
                //new TimeSignature(2,4),
                2,
                new CountInDynamics[]{ACCENTED,STANDARD,ACCENTED,STANDARD}),
        ONE_AND_TWO_AND_2 (UIText.get("maestro.countin.1.and.2.and.1.and.2.and"),
                "(2/4)",
                //new TimeSignature(2,4),
                2,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE_FOUR_FIVE ("1 2 3 4 5",
                "(5/4, 5/8)",
                //new TimeSignature(5,4),
                1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,STANDARD}),
        ONE_TWO_THREE_FOUR_FIVE_SIX ("1 2 3 4 5 6",
                "(6/8)",
                //new TimeSignature(6,8),
                1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD}),
        ONE_TWO_THREE_FOUR_FIVE_SIX_SEVEN ("1 2 3 4 5 6 7",
                "(7/4, 7/8)",
                //new TimeSignature(7,8),
                1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD}),
        ONE_TWO_THREE_FOUR_FIVE_SIX_SEVEN_EIGHT_NINE ("1 2 3 4 5 6 7 8 9",
                "(9/8)",
                //new TimeSignature(7,8),
                1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD}),
        ONE_AND_A_TWO_AND_A (UIText.get("maestro.countin.1.and.a.2.and.a"),
                UIText.get("maestro.countin.6.8.jig.12.8.compound"),
                //new TimeSignature(6,8),
                1,
                new CountInDynamics[]{STANDARD,SOFT,SOFT,STANDARD,SOFT,SOFT}),
        FOUR_TRIPLETS (UIText.get("maestro.countin.1.trip.let.2.trip.let.3.trip.let.4.trip.let"),
                UIText.get("maestro.countin.4.4.swing"),
                //new TimeSignature(4,4),
                1,
                new CountInDynamics[]{
                        ACCENTED_TRIPLET,STANDARD,STANDARD,ACCENTED_TRIPLET,STANDARD,STANDARD,
                        ACCENTED_TRIPLET,STANDARD,STANDARD,ACCENTED_TRIPLET,STANDARD,STANDARD
                        }),
        THREE_TRIPLETS (UIText.get("maestro.countin.1.trip.let.2.trip.let.3.trip.let"),
                UIText.get("maestro.countin.3.4.jazz.waltz"),
                //new TimeSignature(3,4),
                1,
                new CountInDynamics[]{ACCENTED_TRIPLET,STANDARD,STANDARD,ACCENTED_TRIPLET,STANDARD,STANDARD,
                            ACCENTED_TRIPLET,STANDARD,STANDARD
                            }),
        TWO_TRIPLETS (UIText.get("maestro.countin.1.trip.let.2.trip.let"),
                UIText.get("maestro.countin.6.8.12.8.4.4.swing.3.4.swing"),
                //new TimeSignature(6,8),
                1,
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
        //final TimeSignature timeSignature;
        public final CountInDynamics[] dynamics;
        final int bars;

        CountInPattern(String name, String usedBy, int defaultBars, CountInDynamics[] dynamics) {
            this.name = name;
            this.usedBy = usedBy;
            //this.timeSignature = timeSignature;
            this.dynamics = dynamics;
            this.bars = defaultBars;
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
            panel.add(rightLabel, BorderLayout.CENTER);

            // Ensure panel respects the original jcombobox cell height
            panel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends CountInPattern> list, CountInPattern value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {

            String leftText = value.name + " ";
            String rightText = value.usedBy;

            // Get available width and prioritize left text
            int totalWidth = list.getWidth() - 16; // Account for padding and borders
            if (totalWidth > 0 && !rightText.isEmpty()) {
                FontMetrics leftFm = leftLabel.getFontMetrics(leftLabel.getFont());
                FontMetrics rightFm = rightLabel.getFontMetrics(rightLabel.getFont());

                int leftWidth = leftFm.stringWidth(leftText);
                int rightWidth = rightFm.stringWidth(rightText);
                int minGap = 8; // Minimum space between labels

                // If they would overlap, truncate the RIGHT text to make room for left
                if (leftWidth + rightWidth + minGap > totalWidth) {
                    int maxRightWidth = totalWidth - leftWidth - minGap - rightFm.stringWidth("...");
                    if (maxRightWidth > rightFm.stringWidth("...")) {
                        rightText = truncateText(rightText, rightFm, maxRightWidth) + "...";
                    } else {
                        // no room, hide the right text
                        rightText = "";
                    }
                }
            }

            leftLabel.setText(leftText);
            rightLabel.setText(rightText);

            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();

            leftLabel.setForeground(fg);
            rightLabel.setForeground(fg);
            panel.setBackground(bg);
            panel.setOpaque(isSelected);

            // Make the right label slightly dimmer when not selected
            if (!isSelected && !rightText.isEmpty()) {
                rightLabel.setForeground(fg.darker());
            }

            return panel;
        }

        private String truncateText(String text, FontMetrics fm, int maxWidth) {
            if (fm.stringWidth(text) <= maxWidth) {
                return text;
            }

            for (int i = text.length() - 1; i > 0; i--) {
                String truncated = text.substring(0, i);
                if (fm.stringWidth(truncated) <= maxWidth) {
                    return truncated;
                }
            }
            return "";
        }

    }

    private static JDialog dialog = null;
    private static CountIn lastCountIn = null;
    private static AbcPart currentPart = null;
    private static JLabel partHeader = null;

    public static void setLastCountIn(CountIn lastCountIn) {
        if (dialog != null) {
            dialog.setVisible(false);
        }
        CountIn.lastCountIn = lastCountIn;
    }

    public static CountIn show(Component parent, AbcPart part, CountIn lastCountIn) {
        if (dialog == null) {
            dialog = new JDialog();
            dialog.setTitle(UIText.get("maestro.countin.count.in"));
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            GridBagLayout layout = new GridBagLayout();
            dialog.setLayout(layout);
            dialog.setSize(250, 200);
            dialog.setLocationRelativeTo(parent);
            dialog.setResizable(false);
            dialog.setModal(true);

            partHeader = new JLabel(part.getTitle());
            partHeader.setHorizontalAlignment(SwingConstants.CENTER);

            JComboBox<CountInPattern> patternBox = new JComboBox<>(CountInPattern.values());
            patternBox.setEditable(false);
            patternBox.setRenderer(new PatternRenderer());
            patternBox.setMaximumRowCount(CountInPattern.values().length);

            List<LotroDrumInfo> usableDIs = new ArrayList<>();
            for (LotroDrumInfo di : LotroDrumInfo.ALL_DRUMS) {
                if (di.note == Note.REST) continue;
                usableDIs.add(di);
            }
            JComboBox<LotroDrumInfo> hitBox = new JComboBox<>(usableDIs.toArray(new LotroDrumInfo[]{}));
            hitBox.setEditable(false);

            JLabel barText = new JLabel(UIText.get("maestro.countin.bars"));
            barText.setHorizontalAlignment(SwingConstants.RIGHT);
            barText.setAlignmentX(Component.RIGHT_ALIGNMENT);

            JTextField barField = new JTextField(String.format(Locale.US, "%.2f", 1.0f));
            barField.setHorizontalAlignment(SwingConstants.CENTER);

            String toolTipText = UIText.get("maestro.countin.rules");

            barText.setToolTipText(toolTipText);
            barField.setToolTipText(toolTipText);
            patternBox.setToolTipText(UIText.get("maestro.countin.if.this.count.in.is.activated.then"));

            if (lastCountIn != null) {
                patternBox.setSelectedItem(lastCountIn.pattern);
                hitBox.setSelectedItem(lastCountIn.hit);
                barField.setText(String.format(Locale.US, "%.2f", lastCountIn.barCount));
            } else {
                /*
                I originally want some auto-detection here,
                but it cannot be done reliable.
                */
                patternBox.setSelectedItem(OFF);
                hitBox.setSelectedItem(LotroDrumInfo.getById(Note.Ds3.id));// Rimshot 1
            }

            patternBox.addActionListener(e -> {
                CountIn.lastCountIn = getInstance(patternBox, barField, hitBox, currentPart);
            });
            barField.addActionListener(e -> {
                CountIn.lastCountIn = getInstance(patternBox, barField, hitBox, currentPart);
            });
            hitBox.addActionListener(e -> {
                CountIn.lastCountIn = getInstance(patternBox, barField, hitBox, currentPart);
            });
            dialog.addWindowListener(new WindowAdapter() {

                @Override
                public void windowClosing(WindowEvent windowEvent) {
                    CountIn.lastCountIn = getInstance(patternBox, barField, hitBox, currentPart);
                }

                @Override
                public void windowClosed(WindowEvent windowEvent) {

                }
            });


            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 8, 6, 8);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Row 0: part header (spans 2 columns)
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            dialog.getContentPane().add(partHeader, gbc);

            // Row 1: pattern combo (spans 2 columns)
            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            dialog.getContentPane().add(patternBox, gbc);

            // Row 2: "Bars" label
            gbc = (GridBagConstraints) gbc.clone();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 1;
            gbc.weightx = 0.5;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.LINE_END;
            dialog.getContentPane().add(barText, gbc);

            // Row 2: bars text field
            gbc = (GridBagConstraints) gbc.clone();
            gbc.gridx = 1;
            gbc.gridy = 2;
            gbc.weightx = 0.0;
            gbc.fill = GridBagConstraints.NONE;
            gbc.anchor = GridBagConstraints.CENTER;
            dialog.getContentPane().add(barField, gbc);

            // Row 3: hit drum combo (spans 2 columns)
            gbc = (GridBagConstraints) gbc.clone();
            gbc.gridx = 0;
            gbc.gridy = 3;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            dialog.getContentPane().add(hitBox, gbc);

        }
        currentPart = part;
        CountIn.lastCountIn = lastCountIn;
        partHeader.setText(part.getTitle());
        dialog.setVisible(!dialog.isVisible());
        return CountIn.lastCountIn;
    }

    private static CountIn getInstance(JComboBox<CountInPattern> comboBox, JTextField txtField, JComboBox<LotroDrumInfo> hitBox, AbcPart part) {
        CountInPattern pattern = (CountInPattern) comboBox.getSelectedItem();
        LotroDrumInfo hit = (LotroDrumInfo) hitBox.getSelectedItem();
        if (hit == null || pattern == null) return null;

        float barCount = (float) pattern.bars;
        try {
            barCount = Float.parseFloat(txtField.getText().replace(',', '.'));
        } catch (NumberFormatException nfe) {
            txtField.setText(String.format(Locale.US, "%.2f", barCount));
            return null;
        }
        if (hit.note == Note.REST) return null;
        if (pattern == OFF) return null;
        return new CountIn(pattern, barCount, part, hit);
    }
}
