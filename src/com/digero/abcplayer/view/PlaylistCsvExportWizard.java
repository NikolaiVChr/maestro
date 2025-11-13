package com.digero.abcplayer.view;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.util.ExtensionFileFilter;
import net.miginfocom.swing.MigLayout;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class PlaylistCsvExportWizard extends JDialog {
	private File playlistFile;
	private List<AbcInfo> setData;
	private List<JCheckBox> colCheckBoxes;
	private JLabel statusLabel;

	private File lastSavedCsv;

	private PlaylistSetExportWizard.SetExportSettings settings;

	public PlaylistCsvExportWizard(JFrame owner, Preferences prefNode, File playlistFile, List<AbcInfo> setData, List<String> visibleColumns) {
		super(owner, "Export CSV Partsheet", true);

		this.playlistFile = playlistFile;
		this.setData = setData;

		settings = new PlaylistSetExportWizard.SetExportSettings(prefNode);

		JButton exportButton = new JButton("Export");
		getRootPane().setDefaultButton(exportButton);
		exportButton.addActionListener(e -> {
			List<String> columns = visibleColumns;
			if (!settings.getExportVisibleColumns()) {
				columns = new ArrayList<>();
				for (JCheckBox box : colCheckBoxes) {
					if (box.isSelected()) {
						columns.add(box.getText());
					}
				}
			}
			boolean result = generateCsvPartSheet(columns);
			if (!result) {
				statusLabel.setText("Export failed!");
			} else {
				statusLabel.setText("Export succeeded.");
				settings.save();
				settings.saveCsvExportSettings();
			}
		});

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> {
			this.setVisible(false);
		});

		statusLabel = new JLabel(" ");

		JPanel buttonsPanel = new JPanel(new MigLayout("fillx"));
		buttonsPanel.add(statusLabel, "span 4, grow, wrap");
		buttonsPanel.add(new JLabel(), "growx -1");
		buttonsPanel.add(cancelButton, "align right");
		buttonsPanel.add(exportButton, "align left");
		buttonsPanel.add(new JLabel(), "growx -1");

		JPanel mainPanel = new JPanel(new BorderLayout());
		mainPanel.add(createPartSheetPanel(), BorderLayout.CENTER);
		mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

		setContentPane(mainPanel);
		pack();
		setLocationRelativeTo(owner);
	}

	private JPanel createPartSheetPanel() {
		JPanel partSheetPanel = new JPanel(new MigLayout());

		JLabel partChoiceLabel = new JLabel("Part columns content:");

		JComboBox<String> partContentChoice = new JComboBox<String>(PlaylistSetExportWizard.partColumnsLabels);
		int selectedIndex = 0;
		for (int i = 0; i < PlaylistSetExportWizard.partColumnsSettings.length; i++) {
			if (settings.getPartColumns().equals(PlaylistSetExportWizard.partColumnsSettings[i])) {
				selectedIndex = i;
				break;
			}
		}
		partContentChoice.setSelectedIndex(selectedIndex);
		partContentChoice.addActionListener(e -> {
			settings.setPartColumns(PlaylistSetExportWizard.partColumnsSettings[partContentChoice.getSelectedIndex()]);
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
		if (settings.getExportVisibleColumns()) {
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
			colCheckBox.setEnabled(!settings.getExportVisibleColumns());
			partSheetPanel.add(colCheckBox, i % 2 == 0 ? "" : "wrap");
			i++;
		}

		return partSheetPanel;
	}

	private boolean generateCsvPartSheet(List<String> columns) {
		List<String> headerList = new ArrayList<>();

		lastSavedCsv = null;

		for (String column : columns) {
			headerList.add(column);
		}

		// Add part column headers if including part columns
		if (!settings.getPartColumns().equals(PlaylistSetExportWizard.partColumnsSettings[2])) {
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

		String csvFilename = "untitled_playlist";
		if (playlistFile != null) {
			csvFilename = playlistFile.getName();
			if (csvFilename.contains(".")) {
				csvFilename = csvFilename.substring(0, csvFilename.lastIndexOf('.'));
			}
		}
		csvFilename = csvFilename + ".csv";

		File chooserFile = Paths.get(settings.getCsvExportOutputDirectory(), csvFilename).toFile();

		JFileChooser chooser = new JFileChooser();
		chooser.setFileFilter(new ExtensionFileFilter("CSV files (*.csv, *.txt)", "csv", "txt"));
		chooser.setSelectedFile(chooserFile);
		int result = chooser.showSaveDialog(this);

		if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
			return false;
		}

		File selectedFile = chooser.getSelectedFile();

		if (selectedFile.exists()) {
			int res = JOptionPane.showConfirmDialog(this,
					"File \"" + selectedFile.toString() + "\" already exists.\n" + "Do you want to replace it?",
					"Confirm Replace File", JOptionPane.OK_CANCEL_OPTION);
			if (res == JOptionPane.CANCEL_OPTION || res == JOptionPane.CLOSED_OPTION)
				return false;
		}

		settings.setCsvExportOutputDirectory(selectedFile.getParent().toString());

		try {
			Path csvPath = selectedFile.toPath();
			FileWriter out = new FileWriter(csvPath.toFile());
			try(CSVPrinter printer = new CSVPrinter(out, format)) {
				for (int i = 0; i < setData.size(); i++) {
					AbcInfo inf = setData.get(i);
					List<Object> record = new ArrayList<Object>();

					for (int j = 0; j < indices.length; j++) {
						Object col = AbcInfoTableModel.getColumnValueForAbcInfo(inf, indices[j]);
						record.add(col);
					}

					// Parts - skip if set to not include
					if (!settings.getPartColumns().equals(PlaylistSetExportWizard.partColumnsSettings[2])) {
						for (int j = 0; j < inf.getPartCount(); j++ ) {
							String name = inf.getPartInstrument(j + 1).friendlyName;
							// if part column setting is part and not instrument
							if (settings.getPartColumns().equals(PlaylistSetExportWizard.partColumnsSettings[0])) {
								name = inf.getPartFullName(j + 1);
							}
							record.add(name);
						}
					}

					printer.printRecord(record);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		lastSavedCsv = selectedFile;

		return true;
	}
}
