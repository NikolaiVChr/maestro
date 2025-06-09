package com.digero.abcplayer.view;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import com.digero.abcplayer.SetFilenameTemplate;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.util.Util;
import com.digero.maestro.abc.ExportFilenameTemplate;

import net.miginfocom.swing.MigLayout;

public class PlaylistSetExportWizard extends JDialog {
		
	public static final String[] partColumnsSettings = { "part", "instrument", "none" };
	public static final String[] partColumnsLabels = { "Use Part Names", "Use Instrument Names", "Don't Include" };
	
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
		private String partColumns; // part, instrument, none
		private boolean exportVisibleColumns;
		private HashMap<String, Boolean> csvColumnsEnabled;

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
			partColumns = prefs.get("partColumns", partColumnsSettings[0]);
			exportVisibleColumns = prefs.getBoolean("exportVisibleColumns", true);
			// CSV part sheet custom columns
			csvColumnsEnabled = new HashMap<String, Boolean>();
			List<String> columns = AbcInfoTableModel.getColNames();
			for (String col : columns) {
				boolean def = false;
				if (Arrays.stream(AbcInfoTableModel.DEFAULT_ENABLED_COLS).anyMatch(col::equals)) {
					def = true;
				}
				String prefName = columnNameToCsvPrefKey(col);
				csvColumnsEnabled.put(prefName, prefs.getBoolean(prefName, def));
			}
		}
		
		public static String columnNameToCsvPrefKey(String columnName) {
			return "csvCol" + columnName.replaceAll("\\s+", "");
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
			
			prefs.put("partColumns", partColumns);
			prefs.putBoolean("exportVisibleColumns", exportVisibleColumns);
			for (String key : csvColumnsEnabled.keySet()) {
				prefs.putBoolean(key, csvColumnsEnabled.get(key));
			}
		}

		private void copyFrom(SetExportSettings source) {
			this.outputDirectory = source.outputDirectory;
			this.renameAbcFiles = source.renameAbcFiles;
			this.exportAsZip = source.exportAsZip;
			this.exportPartSheet = source.exportPartSheet;
			this.filenamePattern = source.filenamePattern;
			this.whitespaceReplaceText = source.whitespaceReplaceText;
			this.partCountZeroPadded = source.partCountZeroPadded;
			this.partColumns = source.partColumns;
			this.exportVisibleColumns = source.exportVisibleColumns;
			this.csvColumnsEnabled = source.csvColumnsEnabled;
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
		
		public String getPartColumns() {
			return partColumns;
		}

		public void setPartColumns(String partColumns) {
			this.partColumns = partColumns;
		}
		
		public boolean getExportVisibleColumns() {
			return exportVisibleColumns;
		}
		
		public void setExportVisibleColumns(boolean exportVisibleColumns) {
			this.exportVisibleColumns = exportVisibleColumns;
		}
		
		public boolean getCsvColumnEnabled(String columnName) {
			String columnPrefName = columnNameToCsvPrefKey(columnName);
			if (csvColumnsEnabled.containsKey(columnPrefName)) {
				return csvColumnsEnabled.get(columnPrefName);
			}
			return false;
		}
		
		public void setCsvColumnEnabled(String columnName, boolean enabled) {
			String columnPrefName = columnNameToCsvPrefKey(columnName);
			csvColumnsEnabled.put(columnPrefName, enabled);
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
	private List<JCheckBox> colCheckBoxes;
	
	public PlaylistSetExportWizard(JFrame owner, Preferences prefNode, File playlistFile, List<AbcInfo> setData, List<String> visibleColumns) {
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
			List<String> columns = visibleColumns;
			if (!settings.exportVisibleColumns) {
				columns = new ArrayList<>();
				for (JCheckBox box : colCheckBoxes) {
					if (box.isSelected()) {
						columns.add(box.getText());
					}
				}
			}
			new SetExportWorker(setNameField.getText(), columns).execute();
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
		directoryLabel.setToolTipText("Directory in which the set folder or zip will be exported.");
		
		JButton chooseDirectoryButton = new JButton("Output Folder...");
		chooseDirectoryButton.setToolTipText("Choose the directory in which the set folder or zip will be exported.");
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
		setNameLabel.setToolTipText("The set will be exported into a folder or a zip file with this name.");
		String setName = "Untitled Set";
		if (playlistFile != null) {
			setName = playlistFile.getName();
			if (setName.contains(".")) {
				setName = setName.substring(0, setName.lastIndexOf('.'));
			}
		}
		setNameField = new JTextField(setName);
		setNameField.setToolTipText("The set will be exported into a folder or a zip file with this name.");
		
		JCheckBox exportAsZip = new JCheckBox("Export As Zip");
		exportAsZip.setToolTipText("If selected, the set will be exported as a zip file rather than into a folder.");
		exportAsZip.setSelected(settings.isExportAsZip());
		exportAsZip.addActionListener(e -> {
			settings.setExportAsZip(exportAsZip.isSelected());
		});
		
		JCheckBox exportCsvPartsheet = new JCheckBox("Export CSV Part Sheet");
		exportCsvPartsheet.setToolTipText("<html>If selected, a CSV part sheet will be generated alongside the ABC files.<br>See the CSV Part Sheet tab for related options.</html>");
		exportCsvPartsheet.setSelected(settings.isExportPartSheet());
		exportCsvPartsheet.addActionListener(e -> {
			settings.setExportPartSheet(exportCsvPartsheet.isSelected());
		});
		
		JCheckBox renameAbcFiles = new JCheckBox("Rename ABC Files using Pattern");
		renameAbcFiles.setToolTipText("<html>If selected, ABC files will be renamed and reordered in the exported set<br>according to the pattern specified in the ABC File Renaming tab.</html>");
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
		settings.setWhitespaceReplaceText(ExportFilenameTemplate.spaceReplaceChars[0]);

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
		
		exampleAbcFileLabel = new JLabel(Util.ABC_FILE_EXTENSION);
		
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
		
		JLabel partChoiceLabel = new JLabel("Part columns content:");
		
		JComboBox<String> partContentChoice = new JComboBox<String>(partColumnsLabels);
		int selectedIndex = 0;
		for (int i = 0; i < partColumnsSettings.length; i++) {
			if (settings.getPartColumns().equals(partColumnsSettings[i])) {
				selectedIndex = i;
				break;
			}
		}
		partContentChoice.setSelectedIndex(selectedIndex);
		partContentChoice.addActionListener(e -> {
			settings.setPartColumns(partColumnsSettings[partContentChoice.getSelectedIndex()]);
		});
		
		List<String> colNames = AbcInfoTableModel.getColNames();
		colCheckBoxes = new ArrayList<>(colNames.size());
		
		JRadioButton visibleColumns = new JRadioButton("Use visible table columns");
		JRadioButton customColumns = new JRadioButton("Use custom columns");
		ButtonGroup columnModeGroup = new ButtonGroup();
		ActionListener columnListener = e -> {
			boolean useVisibleColumns = visibleColumns.isSelected();
			settings.setExportVisibleColumns(useVisibleColumns);
			for (JCheckBox checkBox: colCheckBoxes) {
				checkBox.setEnabled(!useVisibleColumns);
			}
		};
		columnModeGroup.add(visibleColumns);
		columnModeGroup.add(customColumns);
		visibleColumns.addActionListener(columnListener);
		customColumns.addActionListener(columnListener);
		if (settings.exportVisibleColumns) {
			visibleColumns.setSelected(true);
		} else {
			customColumns.setSelected(true);
		}
		
		JLabel columnLabel = new JLabel("<html><u><b>Select Custom Columns</b></u></html");
		
		partSheetPanel.add(partChoiceLabel, "align right");
		partSheetPanel.add(partContentChoice, "wrap");
		partSheetPanel.add(visibleColumns);
		partSheetPanel.add(customColumns, "wrap");
		partSheetPanel.add(columnLabel, "span 2, wrap");
		
		int i = 0;
		for (String col : colNames) {
			JCheckBox colCheckBox = new JCheckBox(col);
			boolean enabled = settings.getCsvColumnEnabled(col);
			colCheckBox.setSelected(enabled);
			colCheckBox.addActionListener(e -> {
				settings.setCsvColumnEnabled(col, colCheckBox.isSelected());
			});
			colCheckBoxes.add(colCheckBox);
			colCheckBox.setEnabled(!settings.exportVisibleColumns);
			partSheetPanel.add(colCheckBox, i % 2 == 0 ? "" : "wrap");
			i++;
		}
		
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
		private String error = "";
		private File zipFile;
		private List<String> columns;
		
		public SetExportWorker(String setName, List<String> columns) {
			this.setName = setName;
			this.columns = columns;
			progressBar.setValue(0);
		}
		
		// Begin thread processing methods, DONT touch Swing components
		
		// Test for existence of output folder or output zip and fail if it exists
		// Create output folder or create temp folder to zip
		private boolean initializeOutput() {
			String outputFolder = settings.outputDirectory;
			
			if (settings.exportAsZip) {
				Path tempDir;
				String zipName = setName + ".zip";
				zipFile = Paths.get(outputFolder, zipName).toFile();
				if (zipFile.exists()) {
					error = "Set output zip file already exists";
					return false;
				}
				try {
					tempDir = Files.createTempDirectory("setExport");
				} catch (IOException e) {
					error = "Failed to create ABC export directory";
					return false;
				}
				copyToFolder = Paths.get(tempDir.toString(), setName).toFile();
			} else {
				copyToFolder = Paths.get(outputFolder, setName).toFile();
				if (copyToFolder.exists()) {
					error = "Set output folder already exists";
					return false;
				}
			}
			
			try {
				Files.createDirectory(copyToFolder.toPath());
			} catch (IOException e) {
				error = "Failed to create ABC export directory";
				return false;
			}
			
			publish(new SetExportProgress(10, "Created destination folder..."));
			
			return true;
		}
		
		private String getFormattedName(AbcInfo inf, int i) {
			filenameTemplate.setAbcInfo(inf);
			filenameTemplate.setIndex(i);
			filenameTemplate.setFilename(inf.getSourceFiles().get(0).getName());
			return filenameTemplate.formatName(settings);
		}
		
		private boolean copyAbcFilesToSet() {
			int count = setData.size();
			float inc = 70.f / (float)count;
			float progress = 10;
			for (int i = 0; i < count; i++) {
				AbcInfo inf = setData.get(i);
				String newName = inf.getSourceFiles().get(0).getName();
				if (settings.renameAbcFiles) {
					newName = getFormattedName(inf, i);
				}
				Path source = inf.getSourceFiles().get(0).toPath();
				Path dest = copyToFolder.toPath().resolve(newName);
				try {
					Files.copy(source, dest);
				} catch (IOException e) {
					e.printStackTrace();
					error = "Failed to copy abc file '" + source.getFileName() + "' to set directory";
					return false;
				}
				progress += inc;
				publish(new SetExportProgress((int)progress, "Copied ABC " + (i + 1) + " of " + count + "..."));
			}
			
			return true;
		}
		
		private boolean generateCsvPartSheetIfNeeded() {
			if (!settings.exportPartSheet) {
				publish(new SetExportProgress(90, "No CSV part sheet needed"));
				return true;
			}
			
			List<String> headerList = new ArrayList<>();
			
			for (String column : columns) {
				headerList.add(column);
			}
			
			// Add part column headers if including part columns
			if (!settings.partColumns.equals(partColumnsSettings[2])) {
				int maxPartCount = 0;
				
				for (AbcInfo inf : setData) {
					if (maxPartCount < inf.getPartCount()) {
						maxPartCount = inf.getPartCount();
					}
				}
				
				for (int i = 0; i < maxPartCount; i++) {
					headerList.add("Part " + (i + 1));
				}
			}
			
			String[] headers = headerList.toArray(new String[0]);
			CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers).get();
			
			int[] indices = new int[columns.size()];
			List<String> allColumns = AbcInfoTableModel.getColNames();
			for (int i = 0; i < indices.length; i++) {
				indices[i] = allColumns.indexOf(columns.get(i));
			}
			
			try {
				Path csvPath = copyToFolder.toPath().resolve(setName + ".csv");
				FileWriter out = new FileWriter(csvPath.toFile());
				try(CSVPrinter printer = new CSVPrinter(out, format)) {
					for (int i = 0; i < setData.size(); i++) {
						AbcInfo inf = setData.get(i);
						List<Object> record = new ArrayList<Object>();
						
						File tmp = null;
						if (settings.renameAbcFiles) {
							tmp = inf.getSourceFiles().get(0);
							inf.getSourceFiles().set(0, new File(tmp.getParent(), getFormattedName(inf, i)));
						}
						
						for (int j = 0; j < indices.length; j++) {
							Object col = AbcInfoTableModel.getColumnValueForAbcInfo(inf, indices[j]);
							record.add(col);
						}
						
						// Parts - skip if set to not include
						if (!settings.partColumns.equals(partColumnsSettings[2])) {
							for (int j = 0; j < inf.getPartCount(); j++ ) {
								String name = inf.getPartInstrument(j + 1).friendlyName;
								// if part column setting is part and not instrument
								if (settings.partColumns.equals(partColumnsSettings[0])) {
									name = inf.getPartFullName(j + 1);
								}
								record.add(name);
							}
						}

						printer.printRecord(record);
						
						if (tmp != null) {
							inf.getSourceFiles().set(0, tmp);
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}
			
			publish(new SetExportProgress(90, "Generated CSV part sheet"));
			
			return true;
		}
		
		private boolean zipSetIfNeeded() {
			if (!settings.exportAsZip) {
				publish(new SetExportProgress(100, "No zipping needed"));
				return true;
			}
			
			try {
				Path zipPath = Files.createFile(zipFile.toPath());
				try(ZipOutputStream zs = new ZipOutputStream(Files.newOutputStream(zipPath))) {
					Path copyPath = copyToFolder.getParentFile().toPath();
					Files.walk(copyPath)
						.filter(path -> !Files.isDirectory(path))
						.forEach(path -> {
							ZipEntry zi = new ZipEntry(copyPath.relativize(path).toString());
							try {
								zs.putNextEntry(zi);
								Files.copy(path, zs);
								zs.closeEntry();
							} catch (IOException e) {
								error = "Failed to zip the set";
							}
						});
				}
			} catch (Exception e) {
				error = "Failed to zip the set";
				e.printStackTrace();
				return false;
			}
			
			if (!error.isEmpty()) {
				return false;
			}
			
			publish(new SetExportProgress(100, "Zipped the set"));
			
			return true;
		}
		
		@Override
		protected Boolean doInBackground() throws Exception {
			boolean success = initializeOutput();
			if (!success) {
				return false;
			}
			
			success = copyAbcFilesToSet();
			if (!success) {
				return false;
			}
			
			success = generateCsvPartSheetIfNeeded();
			if (!success) {
				return false;
			}
			
			success = zipSetIfNeeded();
			if (!success) {
				return false;
			}
			
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
			
			if (!error.isEmpty()) {
				progressLabel.setText(error);
				JOptionPane.showMessageDialog(PlaylistSetExportWizard.this, error, "Export Error", JOptionPane.ERROR_MESSAGE);
			} else {
				progressLabel.setText("Set export finished");
				settings.save();
			}
		}
	}
}
