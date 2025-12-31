package com.aifel.abctools;

import com.digero.common.view.UIText;
import com.digero.maestro.midi.Chord;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;

import java.awt.Component;

public class AbcToolsView extends JFrame {

	private JPanel contentPane;
	private JScrollPane txtAreaScroll;
	private JScrollPane scrollPane;
	private JButton btnDest;
	private JButton btnSource;
	private JButton btnJoin;
	private JTextArea txtArea;
	private JPanel folderPanel;
	private JLabel lblSource;
	private JLabel lblDest;
	private JButton btnTest;
	private JSeparator separator;
	private JTabbedPane tabs;
	private JPanel contentPaneMerge;

	private JPanel contentPaneAutoExport;
	private JPanel folderPanelAuto;
	private JLabel lblSourceAuto;
	private JLabel lblDestAuto;
	private JButton btnDestAuto;
	private JButton btnSourceAuto;
	private JButton btnStart;
	private JButton btnCancel;
    private JCheckBox forceLegacyTiming;
	private JCheckBox forceMixTiming;
	private JCheckBox forceOrganic;
	private JCheckBox forceOrganic2;
    private JCheckBox forceOrganic2v2;
    private JCheckBox forceVolumeMethod;
    private JComboBox<Chord.CalcDynamics> volumeMethod;
	private JScrollPane scrollPaneAutoTxt;
	private JTextPane txtAutoExport;
	private JLabel lblMidiAuto;
	private JButton btnMIDI;
	private JPanel panel_2;
	private JLabel lblNewLabel_2;
	private JLabel lblSaveMsx;
	private JCheckBox saveMSX;
	private JCheckBox saveMSXabc;
	private JCheckBox saveMSXtiming;
    private JCheckBox saveMSXvolume;
	private JProgressBar progressBar;
	private JCheckBox recursiveCheckBox;

	/**
	 * Create the frame.
	 */
	public AbcToolsView() {
		setTitle("ABC Tools");
		setMinimumSize(new Dimension(800, 450));
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 500, 550);
		contentPane = new JPanel();

		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));

		setContentPane(contentPane);
		contentPane.setLayout(new BorderLayout(0, 0));

		tabs = new JTabbedPane();

		contentPane.add(tabs, BorderLayout.CENTER);

		contentPaneMerge = new JPanel();
		contentPaneMerge.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPaneMerge.setLayout(new BorderLayout(0, 0));

		tabs.addTab("ABC Merge Tool", contentPaneMerge);

		scrollPane = new JScrollPane();
		scrollPane.setPreferredSize(new Dimension(400, 200));
		scrollPane.setSize(new Dimension(300, 200));
		scrollPane.setMinimumSize(new Dimension(300, 200));
		contentPaneMerge.add(scrollPane, BorderLayout.WEST);

		JPanel panel_1 = new JPanel();
		contentPaneMerge.add(panel_1, BorderLayout.NORTH);

		JLabel lblNewLabel = new JLabel(UIText.get("abctools.convert.single.part.abc.files.into.multi.part.abc.files"));
		panel_1.add(lblNewLabel);

		JPanel south = new JPanel();
		south.setLayout(new BorderLayout(0, 0));
		contentPaneMerge.add(south, BorderLayout.SOUTH);

		JSplitPane splitPane = new JSplitPane();
		south.add(splitPane, BorderLayout.SOUTH);

		btnSource = new JButton(UIText.get("abctools.select.folder.with.single.part.files"));
		btnSource.setToolTipText(UIText.get("abctools.this.is.the.folder.where.the.old.abc.files.are"));
		splitPane.setLeftComponent(btnSource);

		btnDest = new JButton(UIText.get("abctools.select.multi.part.destination.folder"));
		btnDest.setToolTipText(
				UIText.get("abctools.folder.where.you.want.the.new.abc.files.to.be"));
		btnDest.addActionListener(arg0 -> {
		});
		splitPane.setRightComponent(btnDest);

		folderPanel = new JPanel();
		south.add(folderPanel, BorderLayout.NORTH);
		folderPanel.setLayout(new BorderLayout(0, 0));

		lblSource = new JLabel(UIText.get("abctools.source"));
		folderPanel.add(lblSource, BorderLayout.NORTH);

		lblDest = new JLabel(UIText.get("abctools.dest"));
		folderPanel.add(lblDest, BorderLayout.SOUTH);

		JPanel panel = new JPanel();
		contentPaneMerge.add(panel, BorderLayout.EAST);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		btnJoin = new JButton(UIText.get("abctools.join.save"));
		btnJoin.setToolTipText(UIText.get("abctools.join.the.selected.abc.files.into.1.abc.song.and.then.save.it"));
		btnJoin.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add(btnJoin);

		btnTest = new JButton(UIText.get("abctools.test"));
		btnTest.setToolTipText(UIText.get("abctools.open.this.song.in.abc.player"));
		btnTest.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnTest.addActionListener(arg0 -> {
		});

		separator = new JSeparator();
		panel.add(separator);
		panel.add(btnTest);

		txtAreaScroll = new JScrollPane();
		contentPaneMerge.add(txtAreaScroll, BorderLayout.CENTER);

		txtArea = new JTextArea();
		txtArea.setEditable(false);
		txtArea.setWrapStyleWord(true);
		txtArea.setText(
				UIText.get("abctools.start.by"));
		txtArea.setLineWrap(true);
		txtArea.setColumns(10);
		txtAreaScroll.setViewportView(txtArea);

		// Auto Export tool:
		contentPaneAutoExport = new JPanel();
		contentPaneAutoExport.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPaneAutoExport.setLayout(new BorderLayout(0, 0));
		tabs.addTab(UIText.get("abctools.auto.abc.export"), contentPaneAutoExport);

		folderPanelAuto = new JPanel();

		JPanel southAuto = new JPanel();
		southAuto.setLayout(new BorderLayout(0, 0));
		contentPaneAutoExport.add(southAuto, BorderLayout.SOUTH);
		southAuto.add(folderPanelAuto, BorderLayout.NORTH);
		folderPanelAuto.setLayout(new BorderLayout(0, 0));

		lblSourceAuto = new JLabel(UIText.get("abctools.source"));
		folderPanelAuto.add(lblSourceAuto, BorderLayout.NORTH);

		lblDestAuto = new JLabel(UIText.get("abctools.dest"));
		folderPanelAuto.add(lblDestAuto, BorderLayout.SOUTH);

		lblMidiAuto = new JLabel(UIText.get("abctools.midis"));
		folderPanelAuto.add(lblMidiAuto, BorderLayout.CENTER);

		JPanel splitPaneAuto = new JPanel();
		southAuto.add(splitPaneAuto, BorderLayout.SOUTH);

		btnSourceAuto = new JButton(UIText.get("abctools.select.folder.with.msx.project.files"));
		btnSourceAuto.setToolTipText(UIText.get("abctools.this.is.the.folder.where.the.project.files.are"));
		splitPaneAuto.add(btnSourceAuto);

		btnDestAuto = new JButton(UIText.get("abctools.select.abc.destination.folder"));
		btnDestAuto.setToolTipText(
				UIText.get("abctools.folder.where.you.want.the.exported.abc.files.to.be"));
		btnDestAuto.addActionListener(arg0 -> {
		});

		btnMIDI = new JButton(UIText.get("abctools.select.folder.with.midis"));
		btnMIDI.setToolTipText(UIText.get("abctools.midi.folder.button"));
		splitPaneAuto.add(btnMIDI);
		splitPaneAuto.add(btnDestAuto);

		progressBar = new JProgressBar();
		progressBar.setMaximum(1000);
		progressBar.setStringPainted(true);
		southAuto.add(progressBar, BorderLayout.CENTER);

		JPanel panelAuto = new JPanel();
		contentPaneAutoExport.add(panelAuto, BorderLayout.WEST);
		panelAuto.setLayout(new BoxLayout(panelAuto, BoxLayout.Y_AXIS));

		btnStart = new JButton(UIText.get("abctools.start.exporting"));
		btnStart.setToolTipText(UIText.get("abctools.export.all.project.files"));
		btnStart.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(btnStart);
		
		forceOrganic = new JCheckBox(UIText.get("abctools.force.org.single.stage"));
		forceOrganic.setSelected(false);
		forceOrganic.setToolTipText(UIText.get("abctools.force.organic.single.stage.even.if.a.project.do.not.have.it.enabled"));
		forceOrganic.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(forceOrganic);
		
		forceOrganic2 = new JCheckBox(UIText.get("abctools.force.org.multi.stage"));
		forceOrganic2.setSelected(false);
        forceOrganic2.setToolTipText(UIText.get("abctools.force.organic.multi.stage.even.if.a.project.do.not.have.it.enabled"));
		forceOrganic2.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(forceOrganic2);

        forceOrganic2v2 = new JCheckBox(UIText.get("abctools.force.org.multi.stage.2"));
        forceOrganic2v2.setSelected(false);
        forceOrganic2v2.setToolTipText(UIText.get("abctools.force.organic.multi.stage.2.even.if.a.project.do.not.have.it.enabled"));
        forceOrganic2v2.setAlignmentX(Component.CENTER_ALIGNMENT);
        panelAuto.add(forceOrganic2v2);

		forceMixTiming = new JCheckBox(UIText.get("abctools.force.mix.timings"));
		forceMixTiming.setSelected(false);
        forceMixTiming.setToolTipText(UIText.get("abctools.force.mix.timings.even.if.a.project.do.not.have.it.enabled"));
		forceMixTiming.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(forceMixTiming);

        forceLegacyTiming = new JCheckBox(UIText.get("abctools.force.legacy.timings"));
        forceLegacyTiming.setSelected(false);
        forceLegacyTiming.setToolTipText(UIText.get("abctools.force.legacy.timings.even.if.a.project.do.not.have.it.enabled"));
        forceLegacyTiming.setAlignmentX(Component.CENTER_ALIGNMENT);
        panelAuto.add(forceLegacyTiming);

        forceVolumeMethod = new JCheckBox(UIText.get("abctools.force.volume.method"));
        forceVolumeMethod.setSelected(false);
        forceVolumeMethod.setToolTipText(UIText.get("abctools.force.a.volume.method.even.if.a.project.use.another.method"));
        forceVolumeMethod.setAlignmentX(Component.CENTER_ALIGNMENT);
        panelAuto.add(forceVolumeMethod);

        volumeMethod = new JComboBox<>(Chord.CalcDynamics.values());
        volumeMethod.setEditable(false);
        volumeMethod.setSelectedItem(Chord.CalcDynamics.LOUDEST);
        volumeMethod.setAlignmentX(Component.CENTER_ALIGNMENT);
        volumeMethod.setMaximumSize(volumeMethod.getPreferredSize());
        panelAuto.add(volumeMethod);

		recursiveCheckBox = new JCheckBox(UIText.get("abctools.recursive"));
		recursiveCheckBox.setToolTipText(UIText.get("abctools.go.through.sub.folders.and.create.a.similar.output.folder.tree"));
		recursiveCheckBox.setAlignmentX(0.5f);
		panelAuto.add(recursiveCheckBox);
		
		btnCancel = new JButton(UIText.get("abctools.cancel"));
		btnCancel.setToolTipText(UIText.get("abctools.cancel.exporting"));
		btnCancel.setEnabled(false);
		btnCancel.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(btnCancel);
		
		panelAuto.add(new JSeparator(JSeparator.HORIZONTAL));
		lblSaveMsx = new JLabel(UIText.get("abctools.save.project.if.needed"));
		lblSaveMsx.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(lblSaveMsx);
		
		saveMSX = new JCheckBox(UIText.get("abctools.at.source.path.change"));
		saveMSX.setToolTipText(UIText.get("abctools.save.project.files.and.include.new.midi.path.if.changed"));
		saveMSX.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(saveMSX);
		
		saveMSXabc = new JCheckBox(UIText.get("abctools.at.abc.path.change"));
		saveMSXabc.setToolTipText(UIText.get("abctools.save.project.files.and.include.exporter.abc.path.if.changed"));
		saveMSXabc.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(saveMSXabc);
		
		saveMSXtiming = new JCheckBox(UIText.get("abctools.at.timing.change"));
		saveMSXtiming.setToolTipText(UIText.get("abctools.save.project.files.if.timing.changed.due.to.forcing"));
		saveMSXtiming.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(saveMSXtiming);

        saveMSXvolume = new JCheckBox(UIText.get("abctools.at.volume.change"));
        saveMSXvolume.setToolTipText(UIText.get("abctools.save.project.files.if.volume.method.changed.due.to.forcing"));
        saveMSXvolume.setAlignmentX(Component.CENTER_ALIGNMENT);
        panelAuto.add(saveMSXvolume);

		panelAuto.add(new JSeparator(JSeparator.HORIZONTAL));

		scrollPaneAutoTxt = new JScrollPane();
		contentPaneAutoExport.add(scrollPaneAutoTxt, BorderLayout.CENTER);

		txtAutoExport = new JTextPane();
		txtAutoExport.setEditable(false);
		txtAutoExport.setContentType("text/html");
        HTMLEditorKit kit = (HTMLEditorKit) txtAutoExport.getEditorKit();
        StyleSheet styleSheet = kit.getStyleSheet();
        // This rule makes <p> tags behave like lines (no extra spacing)
        styleSheet.addRule("p { margin: 0; padding: 0; }");
		txtAutoExport.setText("Text");
		scrollPaneAutoTxt.setViewportView(txtAutoExport);

		panel_2 = new JPanel();
		contentPaneAutoExport.add(panel_2, BorderLayout.NORTH);

		lblNewLabel_2 = new JLabel(UIText.get("abctools.auto.multi.export.msx.project.files"));
		panel_2.add(lblNewLabel_2);
	}

	public JScrollPane getScrollPane() {
		return scrollPane;
	}

	public JButton getBtnDest() {
		return btnDest;
	}

	public JButton getBtnSource() {
		return btnSource;
	}

	public JButton getBtnJoin() {
		return btnJoin;
	}

	public String getTextFieldText() {
		return txtArea.getText();
	}

	public void setTextFieldText(String text) {
		txtArea.setText(text);
	}

	public String getLblSourceText() {
		return lblSource.getText();
	}

	public void setLblSourceText(String text_1) {
		lblSource.setText(text_1);
	}

	public String getLblDestText() {
		return lblDest.getText();
	}

	public void setLblDestText(String text_2) {
		lblDest.setText(text_2);
	}

	public boolean getBtnJoinEnabled() {
		return btnJoin.isEnabled();
	}

	public void setBtnJoinEnabled(boolean enabled) {
		btnJoin.setEnabled(enabled);
	}

	public boolean getBtnTestEnabled() {
		return btnTest.isEnabled();
	}

	public void setBtnTestEnabled(boolean enabled_1) {
		btnTest.setEnabled(enabled_1);
	}

	public JButton getBtnTest() {
		return btnTest;
	}

	public boolean getForceMixTimingSelected() {
		return forceMixTiming.isSelected();
	}

	public void setForceMixTimingSelected(boolean selected) {
		forceMixTiming.setSelected(selected);
	}

    public boolean getForceLegacyTimingSelected() {
        return forceLegacyTiming.isSelected();
    }

    public void setForceLegacyTimingSelected(boolean selected) {
        forceLegacyTiming.setSelected(selected);
    }

    public void setForceLegacyTimingEnabled(boolean enabled) {
        forceLegacyTiming.setEnabled(enabled);
    }

    public boolean getForceVolumeMethodSelected() {
        return forceVolumeMethod.isSelected();
    }

    public void setForceVolumeMethodEnabled(boolean enabled) {
        forceVolumeMethod.setEnabled(enabled);
    }

    public Chord.CalcDynamics getVolumeMethodSelected() {
        return (Chord.CalcDynamics) volumeMethod.getSelectedItem();
    }

    public void setVolumeMethodEnabled(boolean enabled) {
        volumeMethod.setEnabled(enabled);
    }
	
	public boolean getForceOrganicSelected() {
		return forceOrganic.isSelected();
	}

	public void setForceOrganicSelected(boolean selected) {
		forceOrganic.setSelected(selected);
	}
	
	public boolean getForceOrganic2Selected() {
		return forceOrganic2.isSelected();
	}

	public void setForceOrganic2Selected(boolean selected) {
		forceOrganic2.setSelected(selected);
	}

    public boolean getForceOrganic2v2Selected() {
        return forceOrganic2v2.isSelected();
    }

    public void setForceOrganic2v2Selected(boolean selected) {
        forceOrganic2v2.setSelected(selected);
    }

	public JButton getBtnStartExport() {
		return btnStart;
	}
	
	public JButton getBtnCancelExport() {
		return btnCancel;
	}

	public String getLblSourceAutoText() {
		return lblSourceAuto.getText();
	}

	public void setLblSourceAutoText(String text_3) {
		lblSourceAuto.setText(text_3);
	}

	public String getLblDestAutoText() {
		return lblDestAuto.getText();
	}

	public void setLblDestAutoText(String text_4) {
		lblDestAuto.setText(text_4);
	}

	public JButton getBtnSourceAuto() {
		return btnSourceAuto;
	}

	public JButton getBtnDestAuto() {
		return btnDestAuto;
	}

	public JEditorPane getTxtAutoExport() {
		return txtAutoExport;
	}

	public JButton getBtnMIDI() {
		return btnMIDI;
	}

	public String getLblMidiAutoText() {
		return lblMidiAuto.getText();
	}

	public void setLblMidiAutoText(String text_5) {
		lblMidiAuto.setText(text_5);
	}

	public boolean getForceMixTimingEnabled() {
		return forceMixTiming.isEnabled();
	}

	public void setForceMixTimingEnabled(boolean enabled_2) {
		forceMixTiming.setEnabled(enabled_2);
	}
	
	public boolean getForceOrganicEnabled() {
		return forceOrganic.isEnabled();
	}

	public void setForceOrganicEnabled(boolean enabled_2) {
		forceOrganic.setEnabled(enabled_2);
	}
	
	public boolean getForceOrganic2Enabled() {
		return forceOrganic2.isEnabled();
	}

	public void setForceOrganic2Enabled(boolean enabled_2) {
		forceOrganic2.setEnabled(enabled_2);
	}

    public boolean getForceOrganic2v2Enabled() {
        return forceOrganic2v2.isEnabled();
    }

    public void setForceOrganic2v2Enabled(boolean enabled_2) {
        forceOrganic2v2.setEnabled(enabled_2);
    }

	public boolean getBtnSourceAutoEnabled() {
		return btnSourceAuto.isEnabled();
	}

	public void setBtnSourceAutoEnabled(boolean enabled_3) {
		btnSourceAuto.setEnabled(enabled_3);
	}

	public boolean getBtnMIDIEnabled() {
		return btnMIDI.isEnabled();
	}

	public void setBtnMIDIEnabled(boolean enabled_4) {
		btnMIDI.setEnabled(enabled_4);
	}

	public boolean getBtnDestAutoEnabled() {
		return btnDestAuto.isEnabled();
	}

	public void setBtnDestAutoEnabled(boolean enabled_5) {
		btnDestAuto.setEnabled(enabled_5);
	}

	public boolean getSaveMSXSelected() {
		return saveMSX.isSelected();
	}

	public void setSaveMSXSelected(boolean selected_1) {
		saveMSX.setSelected(selected_1);
	}

	public boolean getSaveMSXEnabled() {
		return saveMSX.isEnabled();
	}

	public void setSaveMSXEnabled(boolean enabled_6) {
		saveMSX.setEnabled(enabled_6);
	}
	
	public boolean getSaveMSXabcSelected() {
		return saveMSXabc.isSelected();
	}

	public void setSaveMSXabcSelected(boolean selected_1) {
		saveMSXabc.setSelected(selected_1);
	}

	public boolean getSaveMSXabcEnabled() {
		return saveMSXabc.isEnabled();
	}

	public void setSaveMSXabcEnabled(boolean enabled_6) {
		saveMSXabc.setEnabled(enabled_6);
	}
	
	public boolean getSaveMSXtimingSelected() {
		return saveMSXtiming.isSelected();
	}

	public void setSaveMSXtimingSelected(boolean selected_1) {
		saveMSXtiming.setSelected(selected_1);
	}

	public boolean getSaveMSXtimingEnabled() {
		return saveMSXtiming.isEnabled();
	}

	public void setSaveMSXtimingEnabled(boolean enabled_6) {
		saveMSXtiming.setEnabled(enabled_6);
	}

    public boolean isSaveMSXvolumeSelected() {
        return saveMSXvolume.isSelected();
    }

    public void setSaveMSXvolumeEnabled(boolean on) {
        saveMSXvolume.setEnabled(on);
    }

	public boolean getTabsEnabled() {
		return tabs.isEnabled();
	}

	public void setTabsEnabled(boolean enabled_7) {
		tabs.setEnabled(enabled_7);
	}

	public JProgressBar getProgressBar() {
		return progressBar;
	}

	public int getProgressBarValue() {
		return progressBar.getValue();
	}

	public void setProgressBarValue(int value) {
		progressBar.setValue(value);
	}
	public boolean getRecursiveCheckBoxSelected() {
		return recursiveCheckBox.isSelected();
	}
	public void setRecursiveCheckBoxSelected(boolean selected_2) {
		recursiveCheckBox.setSelected(selected_2);
	}
	public boolean getRecursiveCheckBoxEnabled() {
		return recursiveCheckBox.isEnabled();
	}
	public void setRecursiveCheckBoxEnabled(boolean enabled_8) {
		recursiveCheckBox.setEnabled(enabled_8);
	}
	public void addForceOrganicActionListener(ActionListener l) {
		forceOrganic.addActionListener(l);
	}
    public void addForceOrganic2ActionListener(ActionListener l) {
        forceOrganic2.addActionListener(l);
    }
    public void addForceOrganic2v2ActionListener(ActionListener l) {
        forceOrganic2v2.addActionListener(l);
    }
    public void addForceLegacyActionListener(ActionListener l) {
        forceLegacyTiming.addActionListener(l);
    }
    public void addForceVolumeMethodListener(ActionListener l) {
        forceVolumeMethod.addActionListener(l);
    }
    public void addForceMixActionListener(ActionListener l) {
        forceMixTiming.addActionListener(l);
    }
}
