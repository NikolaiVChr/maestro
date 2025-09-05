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
    public float barCount = 1;
    public CountInPattern pattern = null;
    public AbcPart part = null;
    public LotroDrumInfo hit = null;
    public long startTick;// set by abcExporter

    public CountIn(CountInPattern pattern, float barCount, AbcPart part, LotroDrumInfo hit) {
        this.pattern = pattern;
        this.barCount = pattern.bars;
        this.part = part;
        this.hit = hit;
    }

    public enum CountInPattern {
        OFF ("Off",
                new TimeSignature(4,4), 1,
                new CountInDynamics[]{}),
        ONE_TWO_THREE_FOUR ("1 2 3 4",
                new TimeSignature(4,4), 1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND_FOUR_AND ("1 and 2 and 3 and 4 and",
                new TimeSignature(4,4), 1,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE_FOUR_ONE_TWO_THREE_FOUR ("1 2 3 4 | 1 2 3 4",
                new TimeSignature(4,4), 2,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,STANDARD,ACCENTED,STANDARD,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND_FOUR_AND_ONE_AND_TWO_AND_THREE_AND_FOUR_AND ("1 and 2 and 3 and 4 | 1 and 2 and 3 and 4",
                new TimeSignature(4,4), 2,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE ("1 2 3",
                new TimeSignature(3,4), 1,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND ("1 and 2 and 3 and",
                new TimeSignature(3,4), 1,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TWO_THREE_ONE_TWO_THREE ("1 2 3 | 1 2 3",
                new TimeSignature(3,4), 2,
                new CountInDynamics[]{ACCENTED,STANDARD,STANDARD,ACCENTED,STANDARD,STANDARD}),
        ONE_AND_TWO_AND_THREE_AND_ONE_AND_TWO_AND_THREE_AND ("1 and 2 and 3 and | 1 and 2 and 3 and",
                new TimeSignature(3,4), 2,
                new CountInDynamics[]{STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT,STANDARD,SOFT}),
        ONE_TRIP_LET_TWO_TRIP_LET_THREE_TRIP_LET_FOUR_TRIP_LET ("1-trip-let 2-trip-let 3-trip-let 4-trip-let",
                new TimeSignature(4,4), 1,
                new CountInDynamics[]{STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD,STANDARD})
        ;

        final String name;
        final TimeSignature timeSignature;
        public final CountInDynamics[] dynamics;
        final int bars;

        CountInPattern(String name, TimeSignature timeSignature, int bars, CountInDynamics[] dynamics) {
            this.name = name;
            this.timeSignature = timeSignature;
            this.dynamics = dynamics;
            this.bars = bars;
        }

        public String toString() {
            return name;
        }
    }

    public enum CountInDynamics {
        ACCENTED (Dynamics.ff),
        STANDARD (Dynamics.mf),
        SOFT (Dynamics.p);

        public final Dynamics dynamics;
        CountInDynamics(Dynamics dynamics) {
            this.dynamics = dynamics;
        }
    }

    private static JDialog dialog = null;
    private static CountIn lastCountIn = null;

    public static CountIn show(Component parent, AbcPart part, CountIn lastCountIn) {
        if (dialog == null) {
            dialog = new JDialog();
            dialog.setTitle("Count-in");
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            GridBagLayout layout = new GridBagLayout();
            dialog.setLayout(layout);
            dialog.setSize(400, 200);
            dialog.setLocationRelativeTo(parent);
            dialog.setResizable(false);
            dialog.setModal(true);
            JComboBox<CountInPattern> comboBox = new JComboBox<>(CountInPattern.values());
            List<LotroDrumInfo> usableDIs = new ArrayList<>();
            for (LotroDrumInfo di : LotroDrumInfo.ALL_DRUMS) {
                if (di.note == Note.REST) continue;
                if (di.note.id >= LotroCombiDrumInfo.minCombi.id) continue;
                usableDIs.add(di);
            }
            JComboBox<LotroDrumInfo> hitBox = new JComboBox<>(usableDIs.toArray(new LotroDrumInfo[]{}));

            JLabel barText = new JLabel("Bars");
            JTextField txtField = new JTextField(String.format("%.2f", 1f));
            txtField.setHorizontalAlignment(SwingConstants.CENTER);
            if (lastCountIn != null) {
                comboBox.setSelectedItem(lastCountIn.pattern);
                hitBox.setSelectedItem(lastCountIn.hit);
                txtField.setText(String.format("%.2f", (float)lastCountIn.barCount));
            } else {
                /*
                TimeSignature time = part.getAbcSong().getTimeSignature();
                for (CountInPattern pattern : CountInPattern.values()) {
                    if (pattern.timeSignature.equals(time) && pattern.bars == 1) {
                        comboBox.setSelectedItem(pattern);
                        break;
                    }
                }
                */
                comboBox.setSelectedItem(OFF);
                hitBox.setSelectedItem(LotroDrumInfo.getById(Note.Ds3.id));// Rimshot 1
            }

            comboBox.addActionListener(e -> {
                CountIn.lastCountIn = getCountIn(comboBox, txtField, hitBox, part);
            });
            txtField.addActionListener(e -> {
                CountIn.lastCountIn = getCountIn(comboBox, txtField, hitBox, part);
            });
            hitBox.addActionListener(e -> {
                CountIn.lastCountIn = getCountIn(comboBox, txtField, hitBox, part);
            });

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(6, 8, 6, 8);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // Row 0: pattern combo (spans 2 columns)
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            dialog.getContentPane().add(comboBox, gbc);

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
            dialog.getContentPane().add(txtField, gbc);

            // Row 2: hit drum combo (spans 2 columns)
            gbc = (GridBagConstraints) gbc.clone();
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            dialog.getContentPane().add(hitBox, gbc);

        }
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
