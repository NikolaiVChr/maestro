package com.aifel.abctools;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;
import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JEditorPane;
import javax.swing.JTextArea;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import java.awt.Component;
import javax.swing.JSeparator;
import javax.swing.JProgressBar;

@SuppressWarnings("serial")
public class MultiMergerView extends JFrame {

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
	private JCheckBox forceMixTiming;
	private JScrollPane scrollPaneAutoTxt;
	private JEditorPane txtAutoExport;
	private JLabel lblMidiAuto;
	private JButton btnMIDI;
	private JPanel panel_2;
	private JLabel lblNewLabel_2;
	private JCheckBox saveMSX;
	private JProgressBar progressBar;
	private JCheckBox recursiveCheckBox;

	/**
	 * Create the frame.
	 */
	public MultiMergerView() {
		setTitle("ABC Tools");
		setMinimumSize(new Dimension(800, 400));
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 500, 400);
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

		JLabel lblNewLabel = new JLabel("Convert single part abc files into multi part abc files");
		panel_1.add(lblNewLabel);

		JPanel south = new JPanel();
		south.setLayout(new BorderLayout(0, 0));
		contentPaneMerge.add(south, BorderLayout.SOUTH);

		JSplitPane splitPane = new JSplitPane();
		south.add(splitPane, BorderLayout.SOUTH);

		btnSource = new JButton("Select folder with single part files");
		btnSource.setToolTipText("This is the folder where the old ABC files are.");
		splitPane.setLeftComponent(btnSource);

		btnDest = new JButton("Select multi part destination folder");
		btnDest.setToolTipText(
				"This is the folder where you want the new ABC files to be. Its recommended that it is empty.");
		btnDest.addActionListener(arg0 -> {
		});
		splitPane.setRightComponent(btnDest);

		folderPanel = new JPanel();
		south.add(folderPanel, BorderLayout.NORTH);
		folderPanel.setLayout(new BorderLayout(0, 0));

		lblSource = new JLabel("Source:");
		folderPanel.add(lblSource, BorderLayout.NORTH);

		lblDest = new JLabel("Dest:");
		folderPanel.add(lblDest, BorderLayout.SOUTH);

		JPanel panel = new JPanel();
		contentPaneMerge.add(panel, BorderLayout.EAST);
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		btnJoin = new JButton("Join & save");
		btnJoin.setToolTipText("Join the selected ABC files into 1 ABC song and then save it.");
		btnJoin.setAlignmentX(Component.CENTER_ALIGNMENT);
		panel.add(btnJoin);

		btnTest = new JButton("Test");
		btnTest.setToolTipText("Open this song in Abc Player");
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
				"Start by selecting BOTH folders.\r\nThen mark 2 or more abc part files.\r\nThen click Join.\r\nThen repeat for other songs.\r\n\r\nBEWARE: It will overwrite files in destination folder, so best to start with a empty destination folder.");
		txtArea.setLineWrap(true);
		txtArea.setColumns(10);
		txtAreaScroll.setViewportView(txtArea);

		// Auto Export tool:
		contentPaneAutoExport = new JPanel();
		contentPaneAutoExport.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPaneAutoExport.setLayout(new BorderLayout(0, 0));
		tabs.addTab("Auto ABC export", contentPaneAutoExport);

		folderPanelAuto = new JPanel();

		JPanel southAuto = new JPanel();
		southAuto.setLayout(new BorderLayout(0, 0));
		contentPaneAutoExport.add(southAuto, BorderLayout.SOUTH);
		southAuto.add(folderPanelAuto, BorderLayout.NORTH);
		folderPanelAuto.setLayout(new BorderLayout(0, 0));

		lblSourceAuto = new JLabel("Source:");
		folderPanelAuto.add(lblSourceAuto, BorderLayout.NORTH);

		lblDestAuto = new JLabel("Dest:");
		folderPanelAuto.add(lblDestAuto, BorderLayout.SOUTH);

		lblMidiAuto = new JLabel("MIDIs:");
		folderPanelAuto.add(lblMidiAuto, BorderLayout.CENTER);

		JPanel splitPaneAuto = new JPanel();
		southAuto.add(splitPaneAuto, BorderLayout.SOUTH);

		btnSourceAuto = new JButton("Select folder with MSX Project files");
		btnSourceAuto.setToolTipText("This is the folder where the project files are.");
		splitPaneAuto.add(btnSourceAuto);

		btnDestAuto = new JButton("Select ABC destination folder");
		btnDestAuto.setToolTipText(
				"This is the folder where you want the exported ABC files to be. Its recommended that it is empty.");
		btnDestAuto.addActionListener(arg0 -> {
		});

		btnMIDI = new JButton("Select folder with MIDIs");
		splitPaneAuto.add(btnMIDI);
		splitPaneAuto.add(btnDestAuto);

		progressBar = new JProgressBar();
		progressBar.setMaximum(1000);
		progressBar.setStringPainted(true);
		southAuto.add(progressBar, BorderLayout.CENTER);

		JPanel panelAuto = new JPanel();
		contentPaneAutoExport.add(panelAuto, BorderLayout.WEST);
		panelAuto.setLayout(new BoxLayout(panelAuto, BoxLayout.Y_AXIS));

		btnStart = new JButton("Start Exporting");
		btnStart.setToolTipText("Export all project files in source folder to abc files in destination folder.");
		btnStart.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(btnStart);

		forceMixTiming = new JCheckBox("Force Mix Timings");
		forceMixTiming.setSelected(true);
		forceMixTiming.setToolTipText("Force mix timings even if a project do not have it enabled.");
		forceMixTiming.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(forceMixTiming);

		saveMSX = new JCheckBox("Save msx if needed");
		saveMSX.setToolTipText("Save MSX files when midi location has changes.");
		saveMSX.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(saveMSX);
		
		recursiveCheckBox = new JCheckBox("Recursive");
		recursiveCheckBox.setToolTipText("Go through sub folders and create a similar output folder tree");
		recursiveCheckBox.setAlignmentX(0.5f);
		panelAuto.add(recursiveCheckBox);
		
		btnCancel = new JButton("Cancel");
		btnCancel.setToolTipText("Cancel Exporting");
		btnCancel.setEnabled(false);
		btnCancel.setAlignmentX(Component.CENTER_ALIGNMENT);
		panelAuto.add(btnCancel);

		scrollPaneAutoTxt = new JScrollPane();
		contentPaneAutoExport.add(scrollPaneAutoTxt, BorderLayout.CENTER);

		txtAutoExport = new JEditorPane();
		txtAutoExport.setEditable(false);
		txtAutoExport.setContentType("text/html");
		txtAutoExport.setText("Text");
		scrollPaneAutoTxt.setViewportView(txtAutoExport);

		panel_2 = new JPanel();
		contentPaneAutoExport.add(panel_2, BorderLayout.NORTH);

		lblNewLabel_2 = new JLabel("Auto multi export msx project files");
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
}
