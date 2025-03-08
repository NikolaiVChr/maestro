package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.util.Map.Entry;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import com.digero.abcplayer.SetFilenameTemplate;
import com.digero.common.util.Util;

import net.miginfocom.swing.MigLayout;

public class PlaylistSetExportWizard extends JDialog {
	
	private static final long serialVersionUID = -946060522761562397L;

	private JTabbedPane tabPanel;
	
	private SetFilenameTemplate filenameTemplate;
	
	public PlaylistSetExportWizard(JFrame owner, SetFilenameTemplate filenameTemplate) {
		super(owner, "Export Set Wizard", true);
		
		this.filenameTemplate = filenameTemplate;
		
		tabPanel = new JTabbedPane();
		tabPanel.addTab("Export Settings", createExportPanel());
		tabPanel.addTab("ABC File Renaming", createFileNamingPanel());
		tabPanel.addTab("CSV Part Sheet", createPartSheetPanel());
		
		JButton exportButton = new JButton("Export");
		getRootPane().setDefaultButton(exportButton);
		exportButton.addActionListener(e -> {
			
		});
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> {
			this.setVisible(false);
		});
		
		JPanel buttonsPanel = new JPanel(new MigLayout("fillx"));
		buttonsPanel.add(new JLabel(), "growx 0");
		buttonsPanel.add(cancelButton, "align right");
		buttonsPanel.add(exportButton, "align left");
		buttonsPanel.add(new JLabel(), "growx 0");
		
		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.add(tabPanel, BorderLayout.CENTER);
		mainPanel.add(buttonsPanel, BorderLayout.SOUTH);
		
		setContentPane(mainPanel);
		pack();
		setLocationRelativeTo(owner);
	}
	
	private JPanel createExportPanel() {
		JPanel exportPanel = new JPanel(new MigLayout("fillx"));
		
		JButton chooseDirectoryButton = new JButton("Output Folder...");
		chooseDirectoryButton.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setDialogTitle("Open set destination folder");
			chooser.showOpenDialog(this);
		});
		JLabel directoryLabel = new JLabel(Util.getLotroMusicPath(false).toString());
		
		JLabel setNameLabel = new JLabel("Set Name:");
		JTextField setNameField = new JTextField();
		
		JCheckBox exportAsZip = new JCheckBox("Export As Zip");
		
		JCheckBox includeCsvPartsheet = new JCheckBox("Export CSV Part Sheet");
		
		exportPanel.add(chooseDirectoryButton);
		exportPanel.add(directoryLabel, "wrap");
		
		exportPanel.add(setNameLabel, "align r");
		exportPanel.add(setNameField, "grow x, wrap");

		exportPanel.add(exportAsZip, "span 2, wrap");
		
		exportPanel.add(includeCsvPartsheet, "span 2");
		
		return exportPanel;
	}
	
	private JPanel createFileNamingPanel() {
		JPanel fileNamePanel = new JPanel(new MigLayout("fillx"));
		
		JLabel patternLabel = new JLabel("<html><b><u>Pattern for new ABC filenames</b></u></html>");
		
		JTextField patternTextField = new JTextField();
		
		JLabel nameLabel = new JLabel("<html><u><b>Variable Name</b></u></html");
		JLabel exampleLabel = new JLabel("<html><u><b>Example</b></u></html>");
		
		fileNamePanel.add(patternLabel, "grow x, span 2, wrap");
		
		fileNamePanel.add(patternTextField, "grow x, span 2, wrap");
		
		fileNamePanel.add(nameLabel);
		fileNamePanel.add(exampleLabel, "wrap");
		
		for(Entry<String, SetFilenameTemplate.Variable> entry : filenameTemplate.getVariables().entrySet()) {
			JLabel keyLabel = new JLabel(entry.getKey());
			JLabel descriptionLabel = new JLabel(entry.getValue().getValue());
			fileNamePanel.add(keyLabel);
			fileNamePanel.add(descriptionLabel, "wrap");
		}
		
		return fileNamePanel;
	}
	
	private JPanel createPartSheetPanel() {
		JPanel partSheetPanel = new JPanel(new MigLayout());
		
		JLabel partChoiceLabel = new JLabel("Part column content:");
		
		String[] partContentOptions = {"Use Part Names", "Use Instrument Names"};
		JComboBox<String> partContentChoice = new JComboBox<String>(partContentOptions);
		
		partSheetPanel.add(partChoiceLabel);
		partSheetPanel.add(partContentChoice, "wrap");
		
		return partSheetPanel;
	}
}
