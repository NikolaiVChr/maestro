package com.digero.maestro.view;

import java.awt.*;

import javax.swing.*;

public class DissonanceSettingsDialog extends JDialog {

    private final MiscSettings settings;
    private boolean success = false;

    private final JCheckBox chkEnabled;
    private final JCheckBox chkExcludeShorts;
    private final JSpinner spinMin2Factor;
    private final JSpinner spinMaj2Factor;
    private final JSpinner spinMaj7Factor;
    private final JSpinner spinMin7Factor;
    private final JSpinner spinTriFactor;
    private final JSpinner spinMudFactor;

    
    private final JSpinner spinMin2Threshold;
    private final JSpinner spinMin2Penalty;
    //private final JSpinner spinMaj2Threshold;
    //private final JSpinner spinMaj2Penalty;

    public DissonanceSettingsDialog(JDialog parent, MiscSettings settings) {
        super(parent, "Dissonance Graph Settings", true);
        this.settings = settings;

        JPanel content = new JPanel(new BorderLayout());
        setContentPane(content);

        // --- Main Form Panel ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 4, 4, 4);
        
        int row = 0;

        // Enabled Checkbox
        chkEnabled = new JCheckBox("Enable Dissonance Detection");
        chkEnabled.setSelected(settings.dissEnabled); // Default to true, or load from settings if you add the field
        chkEnabled.addActionListener(e -> updateControls());
        
        c.gridx = 0; c.gridy = row++; c.gridwidth = 2;
        formPanel.add(chkEnabled, c);
        c.gridwidth = 1;

        chkExcludeShorts = new JCheckBox("Exclude ultra short overlaps");
        chkExcludeShorts.setSelected(settings.excludeShortestNotes); // Default to true, or load from settings if you add the field
        c.gridy = row++; c.gridwidth = 2;
        formPanel.add(chkExcludeShorts, c);

        JLabel label = new JLabel("<html>Note: Two notes that are not overlapping might still produce dissonance." +
                                        "<br>For example a 100 ms lute note followed by a clarinet note." +
                                        "<br>This is due to the lute note will play until its sample runs out" +
                                        "<br> and that is much longer than 100 ms.");
        c.gridy = row++; c.gridwidth = 2;
        formPanel.add(label, c);

        // Weights / Factors Section
        addHeader(formPanel, "Interval Weights (Count Multipliers)", row++);
        spinMin2Factor = addField(formPanel, "Minor 2nd Weight:", row++, settings.min2factor,0);
        spinMaj7Factor = addField(formPanel, "Major 7th Weight:", row++, settings.maj7factor,0);
        spinTriFactor  = addField(formPanel, "Tritone Weight:",   row++, settings.trifactor,0);
        spinMin7Factor = addField(formPanel, "Minor 7th Weight:", row++, settings.min7factor,0);
        spinMaj2Factor = addField(formPanel, "Major 2nd Weight:", row++, settings.maj2factor,0);
        spinMudFactor  = addField(formPanel, "Bass Mud Weight:",   row++, settings.mudfactor,0);

        spinMudFactor.setToolTipText("<html>When a note lower than C3 have an interval closer than 1 octave" +
                "<br>and is not unison, perfect fourth/fifth or minor 7th it is considered a bass mud (LIL)." +
                "<br>Remember that theorbo note samples are longer than 0.5 second, so even sequential notes can create bass mud." +
                "<br>Note: Sometimes the rumble that bass mud create is desirable." +
                "<br>Default is 0, change this only if you know what you are doing.</html>");

        // Thresholds & Penalties Section
        addHeader(formPanel, "Swarm Penalties", row++);
        spinMin2Threshold = addField(formPanel, "Min 2nd count threshold for penalty:", row++, settings.min2threshold,1);
        spinMin2Penalty   = addField(formPanel, "Min 2nd penalty for each over threshold:",   row++, settings.min2penalty,0);
        //spinMaj2Threshold = addField(formPanel, "Maj 2nd count threshold for penalty:", row++, settings.maj2threshold,1);
        //spinMaj2Penalty   = addField(formPanel, "Maj 2nd penalty for each over threshold:",   row++, settings.maj2penalty,0);

        content.add(formPanel, BorderLayout.CENTER);

        // --- Button Panel ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnOk = new JButton("OK");
        JButton btnCancel = new JButton("Cancel");
        /*
        JButton btnDefaults = new JButton("Restore Defaults");

        // Defaults Logic
        btnDefaults.addActionListener(e -> {
            // Hardcoded defaults based on your preference
            spinMin2Factor.setValue(1);
            spinMaj2Factor.setValue(1);
            spinMaj7Factor.setValue(1);
            spinMin7Factor.setValue(0);
            spinTriFactor.setValue(0);
            
            spinMin2Threshold.setValue(1);
            spinMin2Penalty.setValue(10);
            spinMaj2Threshold.setValue(1);
            spinMaj2Penalty.setValue(0);
        });
         */

        btnOk.addActionListener(e -> {
            saveSettings();
            success = true;
            setVisible(false);
        });

        btnCancel.addActionListener(e -> {
            success = false;
            setVisible(false);
        });

        //buttonPanel.add(btnDefaults);
        buttonPanel.add(btnCancel);
        buttonPanel.add(btnOk);
        content.add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
        updateControls();
    }

    private void addHeader(JPanel panel, String text, int row) {
        JLabel lbl = new JLabel("<html><b>" + text + "</b></html>");
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = row; c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 4, 4, 4); // Top margin
        panel.add(lbl, c);
    }

    private JSpinner addField(JPanel panel, String labelText, int row, int value, int minValue) {
        JLabel lbl = new JLabel(labelText);
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, minValue, 100, 1));
        
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        
        c.gridx = 0; c.gridy = row; c.weightx = 0.0;
        panel.add(lbl, c);

        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.gridx = 1; c.gridy = row; c.weightx = 1.0;
        panel.add(spinner, c);
        
        return spinner;
    }

    private void updateControls() {
        boolean enabled = chkEnabled.isSelected();
        spinMin2Factor.setEnabled(enabled);
        spinMaj2Factor.setEnabled(enabled);
        spinMaj7Factor.setEnabled(enabled);
        spinMin7Factor.setEnabled(enabled);
        spinTriFactor.setEnabled(enabled);
        spinMin2Threshold.setEnabled(enabled);
        spinMin2Penalty.setEnabled(enabled);
        //spinMaj2Threshold.setEnabled(enabled);
        //spinMaj2Penalty.setEnabled(enabled);
        chkExcludeShorts.setEnabled(enabled);
        spinMudFactor.setEnabled(enabled);
    }

    private void saveSettings() {
        settings.dissEnabled = chkEnabled.isSelected();
        
        settings.min2factor = (Integer) spinMin2Factor.getValue();
        settings.maj2factor = (Integer) spinMaj2Factor.getValue();
        settings.maj7factor = (Integer) spinMaj7Factor.getValue();
        settings.min7factor = (Integer) spinMin7Factor.getValue();
        settings.trifactor  = (Integer) spinTriFactor.getValue();
        settings.mudfactor  = (Integer) spinMudFactor.getValue();
        
        settings.min2threshold = (Integer) spinMin2Threshold.getValue();
        settings.min2penalty   = (Integer) spinMin2Penalty.getValue();
        //settings.maj2threshold = (Integer) spinMaj2Threshold.getValue();
        //settings.maj2penalty   = (Integer) spinMaj2Penalty.getValue();

        settings.excludeShortestNotes = chkExcludeShorts.isSelected();
        
        settings.saveToPrefs();
    }

    public boolean wasSuccess() {
        return success;
    }
}