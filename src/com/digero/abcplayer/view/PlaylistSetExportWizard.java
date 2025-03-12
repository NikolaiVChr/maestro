package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.digero.abcplayer.SetFilenameTemplate;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.util.Util;
import com.digero.maestro.abc.ExportFilenameTemplate;

import net.miginfocom.swing.MigLayout;

public class PlaylistSetExportWizard extends JDialog {
	
	public static final String[] spaceReplaceChars = { " ", "", "_", "-" };
	public static final String[] spaceReplaceLabels = { "Don't Replace", "Remove Spaces", "_ (Underscore)",
			"- (Dash)" };
	
	public static class SetExportSettings {
		// Main settings
		private String outputDirectory;
		private boolean renameAbcFiles;
		private boolean exportAsZip;
		private boolean exportPartSheet;
		
		// Pattern
		private String filenamePattern;
		private String whitespaceReplaceText;
		private boolean partCountZeroPadded;
		
		// CSV part sheet
		private boolean usePartNames;

		private final Preferences prefs;

		private SetExportSettings(Preferences prefs) {
			this.prefs = prefs;
			// General settings
			outputDirectory = prefs.get("outputDirectory", Util.getLotroMusicPath(false).getAbsolutePath());
			renameAbcFiles = prefs.getBoolean("renameAbcFiles", true);
			exportAsZip = prefs.getBoolean("exportAsZip", true);
			exportPartSheet = prefs.getBoolean("exportPartSheet", false);
			
			// Export filename settings
			filenamePattern = prefs.get("filenamePattern", "$SongIndex_$FileName");
			whitespaceReplaceText = prefs.get("whitespaceReplaceText", " ");
			partCountZeroPadded = prefs.getBoolean("partCountZeroPadded", true);
			
			// CSV part sheet settings
			usePartNames = prefs.getBoolean("usePartNames", false);
		}

		public SetExportSettings(SetExportSettings source) {
			this.prefs = source.prefs;
			copyFrom(source);
		}

		private void save() {
			prefs.put("outputDirectory", outputDirectory);
			prefs.putBoolean("renameAbcFiles", renameAbcFiles);
			prefs.putBoolean("exportAsZip", exportAsZip);
			prefs.putBoolean("exportPartSheet", exportPartSheet);
			
			prefs.put("filenamePattern", filenamePattern);
			prefs.put("whitespaceReplaceText", whitespaceReplaceText);
			prefs.putBoolean("partCountZeroPadded", partCountZeroPadded);
			
			prefs.putBoolean("usePartNames", usePartNames);
		}

		private void copyFrom(SetExportSettings source) {
			this.filenamePattern = source.filenamePattern;
			this.whitespaceReplaceText = source.whitespaceReplaceText;
			this.partCountZeroPadded = source.partCountZeroPadded;
		}
		
		public String getOutputDirectory() {
			return outputDirectory;
		}

		public void setOutputDirectory(String outputDirectory) {
			this.outputDirectory = outputDirectory;
		}

		public boolean isRenameAbcFiles() {
			return renameAbcFiles;
		}

		public void setRenameAbcFiles(boolean renameAbcFiles) {
			this.renameAbcFiles = renameAbcFiles;
		}

		public boolean isExportAsZip() {
			return exportAsZip;
		}

		public void setExportAsZip(boolean exportAsZip) {
			this.exportAsZip = exportAsZip;
		}

		public boolean isExportPartSheet() {
			return exportPartSheet;
		}

		public void setExportPartSheet(boolean exportPartSheet) {
			this.exportPartSheet = exportPartSheet;
		}

		public String getExportFilenamePattern() {
			return filenamePattern;
		}

		public void setExportFilenamePattern(String exportFilenamePattern) {
			this.filenamePattern = exportFilenamePattern;
		}

		public String getWhitespaceReplaceText() {
			return whitespaceReplaceText;
		}

		public void setWhitespaceReplaceText(String whitespaceReplaceText) {
			this.whitespaceReplaceText = whitespaceReplaceText;
		}

		public boolean isPartCountZeroPadded() {
			return partCountZeroPadded;
		}

		public void setPartCountZeroPadded(boolean zeroPadded) {
			partCountZeroPadded = zeroPadded;
		}
		
		public boolean isUsePartNames() {
			return usePartNames;
		}

		public void setUsePartNames(boolean usePartNames) {
			this.usePartNames = usePartNames;
		}

		public void restoreDefaults() {
			try {
				prefs.clear();
			} catch (BackingStoreException e) {
				e.printStackTrace();
			}

			SetExportSettings fresh = new SetExportSettings(prefs);
			this.copyFrom(fresh);
		}
	}
	
	private static final long serialVersionUID = -946060522761562397L;

	private JTabbedPane tabPanel;
	private SetExportSettings settings;
	private SetFilenameTemplate filenameTemplate;
	
	private JLabel progressLabel;
	private JProgressBar progressBar;
	
	private JTextField setNameField;
	private JLabel exampleAbcFileLabel;
	
	private File playlistFile;
	private List<AbcInfo> setData;
	
	public PlaylistSetExportWizard(JFrame owner, Preferences prefNode, File playlistFile, List<AbcInfo> setData) {
		super(owner, "Export Set Wizard", true);
		
		this.playlistFile = playlistFile;
		this.setData = setData;
		
		settings = new SetExportSettings(prefNode);
		this.filenameTemplate = new SetFilenameTemplate(settings);
		
		tabPanel = new JTabbedPane();
		tabPanel.addTab("Export Settings", createExportPanel());
		tabPanel.addTab("ABC File Renaming", createFileNamingPanel());
		tabPanel.addTab("CSV Part Sheet", createPartSheetPanel());
		
		JButton exportButton = new JButton("Export");
		getRootPane().setDefaultButton(exportButton);
		exportButton.addActionListener(e -> {
			new SetExportWorker(setNameField.getText()).execute();
		});
		
		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> {
			this.setVisible(false);
		});
		
		progressLabel = new JLabel();
		progressBar = new JProgressBar();
		
		JPanel buttonsPanel = new JPanel(new MigLayout("fillx"));
		buttonsPanel.add(progressLabel, "span 4, grow, wrap");
		buttonsPanel.add(progressBar, "span 4, grow, wrap");
		buttonsPanel.add(new JLabel(), "growx -1");
		buttonsPanel.add(cancelButton, "align right");
		buttonsPanel.add(exportButton, "align left");
		buttonsPanel.add(new JLabel(), "growx -1");
		
		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.add(tabPanel, BorderLayout.CENTER);
		mainPanel.add(buttonsPanel, BorderLayout.SOUTH);
		
		setContentPane(mainPanel);
		pack();
		setLocationRelativeTo(owner);
		
		updateFilenameExample();
	}
	
	private JPanel createExportPanel() {
		JPanel exportPanel = new JPanel(new MigLayout("fillx", "[grow -1][]"));
		
		JLabel directoryLabel = new JLabel(settings.getOutputDirectory());
		
		JButton chooseDirectoryButton = new JButton("Output Folder...");
		chooseDirectoryButton.addActionListener(e -> {
			JFileChooser chooser = new JFileChooser();
			chooser.setApproveButtonText("Select");
			chooser.setCurrentDirectory(new File(directoryLabel.getText()));
			chooser.setDialogTitle("Select Set Destination");
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setMultiSelectionEnabled(false);
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				String dir = chooser.getSelectedFile().toString();
				directoryLabel.setText(dir);
				settings.setOutputDirectory(dir);
			}
		});
		
		JLabel setNameLabel = new JLabel("Set Name:");
		String setName = "Untitled Set";
		if (playlistFile != null) {
			setName = playlistFile.getName();
			if (setName.contains(".")) {
				setName = setName.substring(0, setName.lastIndexOf('.'));
			}
		}
		setNameField = new JTextField(setName);
		
		JCheckBox exportAsZip = new JCheckBox("Export As Zip");
		exportAsZip.setSelected(settings.isExportAsZip());
		exportAsZip.addActionListener(e -> {
			settings.setExportAsZip(exportAsZip.isSelected());
		});
		
		JCheckBox exportCsvPartsheet = new JCheckBox("Export CSV Part Sheet");
		exportCsvPartsheet.setSelected(settings.isExportPartSheet());
		exportCsvPartsheet.addActionListener(e -> {
			settings.setExportPartSheet(exportCsvPartsheet.isSelected());
		});
		
		JCheckBox renameAbcFiles = new JCheckBox("Rename ABCs using Pattern");
		renameAbcFiles.setSelected(settings.isRenameAbcFiles());
		renameAbcFiles.addActionListener(e -> {
			settings.setRenameAbcFiles(renameAbcFiles.isSelected());
		});
		
		exportPanel.add(chooseDirectoryButton, "align r");
		exportPanel.add(directoryLabel, "growx, wrap");
		
		exportPanel.add(setNameLabel, "shrink, align r");
		exportPanel.add(setNameField, "growx, wrap");
		
		exportPanel.add(renameAbcFiles, "span 2, wrap");
		exportPanel.add(exportAsZip, "span 2, wrap");
		exportPanel.add(exportCsvPartsheet, "span 2");
		
		return exportPanel;
	}
	
	private JPanel createFileNamingPanel() {
		JPanel fileNamePanel = new JPanel(new MigLayout("fillx"));
		
		JLabel whitespaceLabel = new JLabel("<html><b>Replace spaces in variables with:</b></html>");
		
		JComboBox<String> replaceWhitespaceComboBox = new JComboBox<>(ExportFilenameTemplate.spaceReplaceLabels);
		String replaceText = settings.getWhitespaceReplaceText();
		int selectedIndex = 0;
		settings.setWhitespaceReplaceText(spaceReplaceChars[0]);

		for (int i = 0; i < ExportFilenameTemplate.spaceReplaceChars.length; i++) {
			if (replaceText.equals(ExportFilenameTemplate.spaceReplaceChars[i])) {
				settings.setWhitespaceReplaceText(ExportFilenameTemplate.spaceReplaceChars[i]);
				selectedIndex = i;
			}
		}
		replaceWhitespaceComboBox.setSelectedIndex(selectedIndex);
		replaceWhitespaceComboBox.addActionListener(e -> {
			settings.setWhitespaceReplaceText(
					ExportFilenameTemplate.spaceReplaceChars[replaceWhitespaceComboBox.getSelectedIndex()]);
			updateFilenameExample();
		});
		
		JLabel patternLabel = new JLabel("<html><b><u>Pattern for new ABC filenames</b></u></html>");
		
		JTextField patternTextField = new JTextField(settings.getExportFilenamePattern(), 40);
		patternTextField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				update();
			}
			@Override
			public void insertUpdate(DocumentEvent e) {
				update();
			}
			@Override
			public void removeUpdate(DocumentEvent e) {
				update();
			}
			public void update() {
				settings.setExportFilenamePattern(patternTextField.getText());
				updateFilenameExample();
			}
		});
		
		exampleAbcFileLabel = new JLabel(".abc");
		
		JLabel nameLabel = new JLabel("<html><u><b>Variable Name</b></u></html");
		JLabel exampleLabel = new JLabel("<html><u><b>Example</b></u></html>");
		
		fileNamePanel.add(whitespaceLabel);
		fileNamePanel.add(replaceWhitespaceComboBox, "grow x, wrap");
		
		fileNamePanel.add(patternLabel, "grow x, span 2, wrap");
		
		fileNamePanel.add(patternTextField, "grow x, span 2, wrap");
		
		fileNamePanel.add(exampleAbcFileLabel, "grow x, span 2, wrap");
		
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
	
	private void updateFilenameExample() {
		String exampleText = filenameTemplate.formatName(settings);
		exampleText = "Example filename:   " + exampleText;
		exampleAbcFileLabel.setText(exampleText);
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
	
	private static class SetExportProgress {
		public int progress;
		public String message;
		
		public SetExportProgress(int i, String s) {
			progress = i;
			message = s;
		}
	}
	
	private class SetExportWorker extends SwingWorker<Boolean, SetExportProgress> {
		
		private String setName;
		private File copyToFolder;
		private String error;
		
		public SetExportWorker(String setName) {
			this.setName = setName;
		}
		
		// Begin thread processing methods, DONT touch Swing components
		
		// Test for existence of output folder or output zip and fail if it exists
		// Create output folder or create temp folder to zip
		private boolean initializeOutput() {
			String outputFolder = settings.outputDirectory;
			System.out.println("HERE");
			
			if (settings.exportAsZip) {
				Path tempDir;
				String zipName = setName + ".zip";
				File zipFile = Paths.get(outputFolder, zipName).toFile();
				System.out.println("HERE2 " + zipFile.toString());
				if (zipFile.exists()) {
					error = "Set output zip file '" + zipName +"' already exists in the target directory";
					return false;
				}
				System.out.println("HERE33");
				try {
					tempDir = Files.createTempDirectory("setExport");
				} catch (IOException e) {
					error = "Failed to create ABC export directory";
					System.out.println("FAILED HERE");
					return false;
				}
				System.out.println("HERE3");
				copyToFolder = Paths.get(tempDir.toString(), setName).toFile();
				System.out.println("HERE4");
			} else {
				copyToFolder = Paths.get(outputFolder, setName).toFile();
				if (copyToFolder.exists()) {
					error = "Set output folder '" + setName + "' already exists in the target directory";
					return false;
				}
			}
			
			try {
				Files.createDirectory(copyToFolder.toPath());
			} catch (IOException e) {
				error = "Failed to create ABC export directory";
				return false;
			}
			
			System.out.println("Created copy to folder: " + copyToFolder.toString());
			
			publish(new SetExportProgress(10, "Created destination folder..."));
			
			return true;
		}
		
		private boolean copyAbcFilesToSet() {
			int count = setData.size();
			float inc = 70.f / (float)count;
			float progress = 10;
			for (int i = 0; i < count; i++) {
				AbcInfo inf = setData.get(i);
				filenameTemplate.setAbcInfo(inf);
				filenameTemplate.setIndex(i);
				filenameTemplate.setFilename(inf.getSourceFiles().get(0).getName());
				String newName = filenameTemplate.formatName(settings);
				Path source = inf.getSourceFiles().get(0).toPath();
				Path dest = copyToFolder.toPath().resolve(newName);
				System.out.println("src:" + source.toString() + " dst:" + dest.toString() + " i:" + i);
				try {
					Files.copy(source, dest);
				} catch (IOException e) {
					e.printStackTrace();
					error = "Failed to copy abc file '" + source.getFileName() + "' to set directory";
					return false;
				}
				progress += inc;
				publish(new SetExportProgress((int)progress, "Copied file " + (i + 1) + " of " + count + "..."));
			}
			
			return true;
		}
		
		private boolean generateCsvPartSheetIfNeeded() {
			return true;
		}
		
		private boolean zipSetIfNeeded() {
			if (!settings.exportAsZip) {
				return true;
			}
			
			
			
			return true;
		}
		
		@Override
		protected Boolean doInBackground() throws Exception {
			
			boolean success = initializeOutput();
			if (!success) {
				// TODO: Error
				System.out.println(error);
				return false;
			}
			System.out.println("Init output S");
			Thread.sleep(50);
			
			success = copyAbcFilesToSet();
			if (!success) {
				System.out.println(error);
				return false;
			}
			System.out.println("Copy ABC S");
			
			success = generateCsvPartSheetIfNeeded();
			if (!success) {
				System.out.println(error);
				return false;
			}
			System.out.println("Part sheet S");
			
			success = zipSetIfNeeded();
			if (!success) {
				System.out.println(error);
				return false;
			}
			System.out.println("Zip S");
			
			return true;
		}
		
		// End thread processing methods
		
		@Override
		protected void process(List<SetExportProgress> chunks) {
			super.process(chunks);
			
			SetExportProgress p = chunks.get(chunks.size() - 1);
			progressBar.setValue(p.progress);
			progressLabel.setText(p.message);
		}

		@Override
		protected void done() {
			super.done();
			
			System.out.println(error);
			
			progressBar.setValue(0);
			progressLabel.setText(error);
		}
	}
}
