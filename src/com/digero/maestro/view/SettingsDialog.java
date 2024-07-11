package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentNick;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.Themer;
import com.digero.common.util.Util;
import com.digero.common.view.LinkButton;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.AbcMetadataSource;
import com.digero.maestro.abc.AbcPartMetadataSource;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.ExportFilenameTemplate;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.abc.PartNameTemplate;
import com.digero.maestro.abc.PartNumberingConfig;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

@SuppressWarnings("serial")
public class SettingsDialog extends JDialog implements TableLayoutConstants {
	private static final String PART_NUMBERING_CONFIG_DIRECTORY = "PartNumConfigDir";
	public static final int NUMBERING_TAB = 0;
	public static final int NAME_TEMPLATE_TAB = 1;
	public static final int SAVE_EXPORT_TAB = 2;
	public static final int MISC = 3;

	private static final int PAD = 4;

	private boolean success = false;
	private boolean settingPageReset = false;
	private int settingPageResetIndex = -1;
	private boolean numbererSettingsChanged = false;

	private JTabbedPane tabPanel;

	private PartAutoNumberer.Settings partNumbererSettings;

	private PartNameTemplate.Settings nameTemplateSettings;
	private PartNameTemplate nameTemplate;
	private JLabel nameTemplateExampleLabel;

	private ExportFilenameTemplate.Settings exportTemplateSettings;
	private ExportFilenameTemplate exportTemplate;
	private JLabel exportTemplateExampleLabel;

	private InstrNameSettings instrNameSettings;

	private SaveAndExportSettings saveSettings;
	private MiscSettings miscSettings;

	private Preferences maestroPrefs;

	private List<InstrumentSpinner> instrumentSpinners = new ArrayList<>();
	private JComboBox<Integer> incrementComboBox = new JComboBox<>(new Integer[] { 1, 10 });

	public SettingsDialog(JFrame owner, Preferences maestroPrefs, PartAutoNumberer partNumberer,
			PartNameTemplate nameTemplate, ExportFilenameTemplate exportTemplate, SaveAndExportSettings saveSettings,
			MiscSettings miscSettings, InstrNameSettings instrNameSettings) {
		super(owner, "Options", true);
		setDefaultCloseOperation(HIDE_ON_CLOSE);

		this.maestroPrefs = maestroPrefs;

		this.partNumbererSettings = partNumberer.getSettingsCopy();

		this.nameTemplate = nameTemplate;
		this.nameTemplateSettings = nameTemplate.getSettingsCopy();

		this.exportTemplate = exportTemplate;
		this.exportTemplateSettings = exportTemplate.getSettingsCopy();

		this.instrNameSettings = instrNameSettings;
		this.saveSettings = saveSettings;
		this.miscSettings = miscSettings;

		JButton okButton = new JButton("OK");
		getRootPane().setDefaultButton(okButton);
		okButton.setMnemonic('O');
		okButton.addActionListener(e -> {
			success = true;
			SettingsDialog.this.setVisible(false);
		});

		JButton resetButton = new JButton("Reset Page");
		resetButton.addActionListener(e -> {
			String page = tabPanel.getTitleAt(tabPanel.getSelectedIndex());
			String title = "Reset '" + page + "' Settings?";
			String message = "Are you sure you want to reset the " + page.toLowerCase() + " settings? No undo!";
			int result = JOptionPane.showConfirmDialog(null, message, title, JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.WARNING_MESSAGE, null);
			if (result == JOptionPane.YES_OPTION) {
				success = false;
				settingPageReset = true;
				settingPageResetIndex = tabPanel.getSelectedIndex();
				SettingsDialog.this.setVisible(false);
			}
		});

		JButton cancelButton = new JButton("Cancel");
		cancelButton.setMnemonic('C');
		cancelButton.addActionListener(e -> {
			success = false;
			SettingsDialog.this.setVisible(false);
		});

		final String CLOSE_WINDOW_ACTION = "com.digero.maestro.view.SettingsDialog:CLOSE_WINDOW_ACTION";
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				CLOSE_WINDOW_ACTION);
		getRootPane().getActionMap().put(CLOSE_WINDOW_ACTION, new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				success = false;
				SettingsDialog.this.setVisible(false);
			}
		});

		JPanel buttonsPanel = new JPanel(new TableLayout(//
				new double[] { 0.33, 0.33, 0.34 }, //
				new double[] { PREFERRED }));
		((TableLayout) buttonsPanel.getLayout()).setHGap(PAD);
		buttonsPanel.add(okButton, "0, 0, f, f");
		buttonsPanel.add(cancelButton, "1, 0, f, f");
		buttonsPanel.add(resetButton, "2, 0, f, f");
		JPanel buttonsContainerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, PAD / 2));
		buttonsContainerPanel.add(buttonsPanel);

		tabPanel = new JTabbedPane();
		tabPanel.addTab("ABC Part Numbering", createNumberingPanel()); // NUMBERING_TAB
		tabPanel.addTab("ABC Part Naming", createNameTemplatePanel()); // NAME_TEMPLATE_TAB
		tabPanel.addTab("File Naming", createExportTemplatePanel());
		tabPanel.addTab("Instrument names", createInstrNamePanel());
		tabPanel.addTab("Save & Export", createSaveAndExportSettingsPanel()); // SAVE_EXPORT_TAB
		tabPanel.addTab("Misc", createMiscPanel()); // MISC_TAB

		JPanel mainPanel = new JPanel(new BorderLayout(PAD, PAD));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
		mainPanel.add(tabPanel, BorderLayout.CENTER);
		mainPanel.add(buttonsContainerPanel, BorderLayout.SOUTH);

		setContentPane(mainPanel);
		pack();

		if (owner != null) {
			int left = owner.getX() + (owner.getWidth() - this.getWidth()) / 2;
			int top = owner.getY() + (owner.getHeight() - this.getHeight()) / 2;
			this.setLocation(left, top);
		}

		// This must be done after layout is done: the call to pack() does layout
		updateNameTemplateExample();
		updateExportFilenameExample();
	}

	private JPanel createNumberingPanel() {
		JLabel instrumentsTitle = new JLabel("<html><b><u>First part number</u></b></html>");

		TableLayout instrumentsLayout = new TableLayout(//
				new double[] { PREFERRED, PREFERRED, 2 * PAD, PREFERRED, PREFERRED }, //
				new double[] {});
		instrumentsLayout.setHGap(PAD);
		instrumentsLayout.setVGap(3);
		JPanel instrumentsPanel = new JPanel(instrumentsLayout);
		instrumentsPanel.setBorder(BorderFactory.createEmptyBorder(0, PAD, 0, 0));
		instrumentSpinners.clear();

		LotroInstrument[] instruments = LotroInstrument.values();
		for (int i = 0; i < instruments.length; i++) {
			LotroInstrument inst = instruments[i];

			int row = i;
			int col = 0;
			if (i >= (instruments.length + 1) / 2) {
				row -= (instruments.length + 1) / 2;
				col = 3;
			} else {
				instrumentsLayout.insertRow(row, PREFERRED);
			}
			InstrumentSpinner spinner = new InstrumentSpinner(inst);
			instrumentSpinners.add(spinner);
			instrumentsPanel.add(spinner, col + ", " + row);
			instrumentsPanel.add(new JLabel(inst.toString() + " "), (col + 1) + ", " + row);
		}

		JLabel incrementTitle = new JLabel("<html><b><u>Increment</u></b></html>");
		JLabel incrementDescr = new JLabel("<html>Interval between multiple parts of the same instrument.<br>"
				+ "<b>1</b>: number Lute parts as 10, 11, 12, etc.<br>"
				+ "<b>10</b>: number Lute parts as 1, 11, 21, etc.</html>");

		incrementComboBox = new JComboBox<>(new Integer[] { 1, 10 });
		incrementComboBox.setSelectedItem(partNumbererSettings.getIncrement());
		incrementComboBox.addActionListener(e -> {
			int oldInc = partNumbererSettings.getIncrement();
			int newInc = (Integer) incrementComboBox.getSelectedItem();
			if (oldInc == newInc)
				return;

			numbererSettingsChanged = true;
			for (InstrumentSpinner spinner : instrumentSpinners) {
				int firstNumber = partNumbererSettings.getFirstNumber(spinner.instrument);
				firstNumber = (firstNumber * oldInc) / newInc;
				partNumbererSettings.setFirstNumber(spinner.instrument, firstNumber);
				spinner.setValue(firstNumber);

				if (newInc == 1) {
					spinner.getModel().setMaximum(999);
				} else {
					spinner.getModel().setMaximum(10);
				}
			}

			partNumbererSettings.setIncrementByTen(newInc == 10);
		});

		TableLayout incrementPanelLayout = new TableLayout(//
				new double[] { PREFERRED, FILL }, //
				new double[] { PREFERRED });
		incrementPanelLayout.setHGap(10);
		JPanel incrementPanel = new JPanel(incrementPanelLayout);
		incrementPanel.setBorder(BorderFactory.createEmptyBorder(0, PAD, 0, 0));
		incrementPanel.add(incrementComboBox, "0, 0, C, T");
		incrementPanel.add(incrementDescr, "1, 0");

		JLabel numberingConfigLabel = new JLabel("<html><b><u>Part Numbering Config: </u></b></html>");

		LinkButton importButton = new LinkButton("Import");
		importButton.addActionListener(e -> loadPartNumberingConfig());

		JLabel separator = new JLabel(" | ");

		LinkButton exportButton = new LinkButton("Export");
		exportButton.addActionListener(e -> savePartNumberingConfig());

		TableLayout mapLayout = new TableLayout(//
				new double[] { PREFERRED, PREFERRED, PREFERRED, FILL }, //
				new double[] { PREFERRED });
		mapLayout.setVGap(PAD);
		mapLayout.setHGap(PAD);
		JPanel mapPanel = new JPanel(mapLayout);
		mapPanel.setBorder(BorderFactory.createEmptyBorder(PAD, 0, PAD, PAD));
		mapPanel.add(numberingConfigLabel, "0, 0");
		mapPanel.add(importButton, "1, 0");
		mapPanel.add(separator, "2, 0");
		mapPanel.add(exportButton, "3, 0");

		TableLayout numberingLayout = new TableLayout(//
				new double[] { FILL }, //
				new double[] { PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED });

		numberingLayout.setVGap(PAD);
		JPanel numberingPanel = new JPanel(numberingLayout);
		numberingPanel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
		numberingPanel.add(instrumentsTitle, "0, 0");
		numberingPanel.add(instrumentsPanel, "0, 1, L, F");
		numberingPanel.add(incrementTitle, "0, 3");
		numberingPanel.add(incrementPanel, "0, 4, F, F");
		numberingPanel.add(mapPanel, "0, 5");
		return numberingPanel;
	}

	private JPanel createInstrNamePanel() {
		TableLayout instrumentsLayout = new TableLayout(//
				new double[] { PREFERRED, PREFERRED, 2 * PAD, PREFERRED, PREFERRED }, //
				new double[] {});
		instrumentsLayout.setHGap(PAD);
		instrumentsLayout.setVGap(3);
		JPanel instrNamePanel = new JPanel(instrumentsLayout);
		instrNamePanel.setBorder(BorderFactory.createEmptyBorder(0, PAD, 0, 0));

		final List<InstrumentDropdown> instrumentDropdowns = new ArrayList<>();
		LotroInstrument[] instruments = LotroInstrument.values();
		for (int i = 0; i < instruments.length; i++) {
			LotroInstrument inst = instruments[i];

			int row = i;
			int col = 0;
			if (i >= (instruments.length + 1) / 2) {
				row -= (instruments.length + 1) / 2;
				col = 3;
			} else {
				instrumentsLayout.insertRow(row, PREFERRED);
			}
			InstrumentDropdown dropdown = new InstrumentDropdown(inst);

			instrumentDropdowns.add(dropdown);
			instrNamePanel.add(dropdown, col + ", " + row);
			instrNamePanel.add(new JLabel(inst.toString() + " "), (col + 1) + ", " + row);
		}

		TableLayout numberingLayout = new TableLayout(//
				new double[] { FILL }, //
				new double[] { PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED });

		numberingLayout.setVGap(PAD);
		JPanel backPanel = new JPanel(numberingLayout);
		backPanel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
		JLabel instrumentsTitle = new JLabel("<html><b><u>Default instrument naming for parts</u></b></html>");
		backPanel.add(instrumentsTitle, "0, 0, C, F");
		backPanel.add(instrNamePanel, "0, 1, L, F");

		return backPanel;
	}

	private class InstrumentDropdown extends JComboBox<String> implements ItemListener {
		private LotroInstrument instrument;

		public InstrumentDropdown(LotroInstrument instrument) {
			super();

			this.instrument = instrument;
			setEditable(true);
			addItem(instrNameSettings.getInstrNick(instrument));
			addItem(instrument.friendlyName);
			for (String nick : LotroInstrumentNick.getNicks(instrument)) {
				addItem(nick);
			}
			addItemListener(this);
		}

		@Override
		public void addItem(String item) {
			if (item == null)
				return;
			int count = getItemCount();
			for (int i = 0; i < count; i++) {
				if (item.equals(getItemAt(i))) {
					return;
				}
			}
			super.addItem(item);
		}

		@Override
		public void itemStateChanged(ItemEvent arg0) {
			instrNameSettings.setInstrNick(instrument, (String) getSelectedItem());
		}
	}

	private class InstrumentSpinner extends JSpinner implements ChangeListener {
		private LotroInstrument instrument;

		public InstrumentSpinner(LotroInstrument instrument) {
			super(new SpinnerNumberModel(partNumbererSettings.getFirstNumber(instrument), 0,
					partNumbererSettings.isIncrementByTen() ? 10 : 999, 1));

			this.instrument = instrument;
			addChangeListener(this);
		}

		@Override
		public SpinnerNumberModel getModel() {
			return (SpinnerNumberModel) super.getModel();
		}

		@Override
		public void stateChanged(ChangeEvent e) {
			partNumbererSettings.setFirstNumber(instrument, (Integer) getValue());
			numbererSettingsChanged = true;
		}
	}

	private boolean loadPartNumberingConfig() {
		Preferences prefs = Preferences.userNodeForPackage(TrackPanel.class);

		String dirPath = prefs.get(PART_NUMBERING_CONFIG_DIRECTORY, null);
		File dir;
		if (dirPath == null || !(dir = new File(dirPath)).isDirectory())
			dir = Util.getLotroMusicPath(false /* create */);

		JFileChooser fileChooser = new JFileChooser(dir);
		fileChooser.setFileFilter(
				new ExtensionFileFilter("Part numbering config file (*.partsconfig.txt)", "partsconfig.txt"));

		if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			return false;

		File loadFile = fileChooser.getSelectedFile();

		PartNumberingConfig config = new PartNumberingConfig();

		try {
			config.load(loadFile);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, "Failed to load part numbering config:\n\n" + e.getMessage(),
					"Failed to load part numbering config", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		incrementComboBox.setSelectedItem(config.increment);

		for (LotroInstrument ins : config.firstPartMap.keySet()) {
			int firstPartNo = config.firstPartMap.get(ins);

			for (InstrumentSpinner spinner : instrumentSpinners) {
				if (spinner.instrument.name().equals(ins.name())) {
					spinner.setValue(firstPartNo);
				}
			}
		}

		prefs.put(PART_NUMBERING_CONFIG_DIRECTORY, fileChooser.getCurrentDirectory().getAbsolutePath());
		return true;
	}

	private boolean savePartNumberingConfig() {
		Preferences prefs = Preferences.userNodeForPackage(TrackPanel.class);

		String dirPath = prefs.get(PART_NUMBERING_CONFIG_DIRECTORY, null);
		File dir;
		if (dirPath == null || !(dir = new File(dirPath)).isDirectory())
			dir = Util.getLotroMusicPath(false /* create */);

		JFileChooser fileChooser = new JFileChooser(dir);
		fileChooser.setFileFilter(
				new ExtensionFileFilter("Part numbering config file (*.partsconfig.txt)", "partsconfig.txt"));

		File saveFile;
		if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return false;

		saveFile = fileChooser.getSelectedFile();

		if (saveFile.getName().indexOf('.') < 0) {
			saveFile = new File(saveFile.getParentFile(), saveFile.getName() + ".partsconfig.txt");
		}

		if (saveFile.exists()) {
			int result = JOptionPane.showConfirmDialog(this,
					"File " + saveFile.getName() + " already exists. Overwrite?", "Confirm overwrite",
					JOptionPane.OK_CANCEL_OPTION);
			if (result != JOptionPane.OK_OPTION)
				return false;
		}

		Map<LotroInstrument, Integer> map = new EnumMap<>(LotroInstrument.class);
		int increment = (int) incrementComboBox.getSelectedItem();

		for (InstrumentSpinner spinner : instrumentSpinners) {
			map.put(spinner.instrument, (Integer) spinner.getValue());
		}

		PartNumberingConfig config = new PartNumberingConfig(increment, map);

		try {
			config.save(saveFile);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Failed to save part numbering config:\n\n" + e.getMessage(),
					"Failed to save part numbering config", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		prefs.put(PART_NUMBERING_CONFIG_DIRECTORY, fileChooser.getCurrentDirectory().getAbsolutePath());
		return true;
	}

	private JPanel createNameTemplatePanel() {
		final JTextField partNameTextField = new JTextField(nameTemplateSettings.getPartNamePattern(), 40);
		partNameTextField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				nameTemplateSettings.setPartNamePattern(partNameTextField.getText());
				updateNameTemplateExample();
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				nameTemplateSettings.setPartNamePattern(partNameTextField.getText());
				updateNameTemplateExample();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				nameTemplateSettings.setPartNamePattern(partNameTextField.getText());
				updateNameTemplateExample();
			}
		});

		nameTemplateExampleLabel = new JLabel(" ");
		JPanel examplePanel = new JPanel(new BorderLayout());
		examplePanel.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
		examplePanel.add(nameTemplateExampleLabel, BorderLayout.CENTER);

		TableLayout layout = new TableLayout();
		layout.insertColumn(0, PREFERRED);
		layout.insertColumn(1, FILL);
		layout.setVGap(3);
		layout.setHGap(10);

		JPanel nameTemplatePanel = new JPanel(layout);
		nameTemplatePanel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));

		int row = 0;
		layout.insertRow(row, PREFERRED);
		JLabel patternLabel = new JLabel("<html><b><u>Pattern for ABC Part Name</b></u></html>");
		nameTemplatePanel.add(patternLabel, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		nameTemplatePanel.add(partNameTextField, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		nameTemplatePanel.add(examplePanel, "0, " + row + ", 1, " + row + ", F, F");

		layout.insertRow(++row, PREFERRED);

		JLabel nameLabel = new JLabel("<html><u><b>Variable Name</b></u></html>");
		JLabel exampleLabel = new JLabel("<html><u><b>Example</b></u></html>");

		layout.insertRow(++row, PREFERRED);
		nameTemplatePanel.add(nameLabel, "0, " + row);
		nameTemplatePanel.add(exampleLabel, "1, " + row);

		AbcMetadataSource originalMetadataSource = nameTemplate.getMetadataSource();
		AbcPartMetadataSource originalAbcPart = nameTemplate.getCurrentAbcPart();

		MockMetadataSource mockMetadata = new MockMetadataSource(originalMetadataSource);
		nameTemplate.setMetadataSource(mockMetadata);
		nameTemplate.setCurrentAbcPart(mockMetadata);
		for (Entry<String, PartNameTemplate.Variable> entry : nameTemplate.getVariables().entrySet()) {
			String tooltipText = "<html><b>" + entry.getKey() + "</b><br>"
					+ entry.getValue().getDescription().replace("\n", "<br>") + "</html>";

			JLabel keyLabel = new JLabel(entry.getKey());
			keyLabel.setToolTipText(tooltipText);
			JLabel descriptionLabel = new JLabel(entry.getValue().getValue());
			descriptionLabel.setToolTipText(tooltipText);

			layout.insertRow(++row, PREFERRED);
			nameTemplatePanel.add(keyLabel, "0, " + row);
			nameTemplatePanel.add(descriptionLabel, "1, " + row);
		}
		nameTemplate.setMetadataSource(originalMetadataSource);
		nameTemplate.setCurrentAbcPart(originalAbcPart);

		return nameTemplatePanel;
	}

	private void updateNameTemplateExample() {
		AbcMetadataSource originalMetadataSource = nameTemplate.getMetadataSource();
		MockMetadataSource mockMetadata = new MockMetadataSource(originalMetadataSource);
		nameTemplate.setMetadataSource(mockMetadata);

		String exampleText = nameTemplate.formatName(nameTemplateSettings.getPartNamePattern(), mockMetadata);
		String exampleTextEllipsis = Util.ellipsis(exampleText, nameTemplateExampleLabel.getWidth(),
				nameTemplateExampleLabel.getFont());

		nameTemplateExampleLabel.setText(exampleTextEllipsis);
		if (!exampleText.equals(exampleTextEllipsis))
			nameTemplateExampleLabel.setToolTipText(exampleText);

		nameTemplate.setMetadataSource(originalMetadataSource);
	}

	private JPanel createExportTemplatePanel() {
		JLabel pageLabel = new JLabel("<html><b><u>ABC and MSX filename settings</b></u></html>");

		JLabel patternLabel = new JLabel("<html><b>Custom pattern for exported filename:</b></html>");

		JLabel whitespaceLabel = new JLabel("<html><b>Replace spaces in variables with:</b></html>");

		JComboBox<String> replaceWhitespaceComboBox = new JComboBox<>(ExportFilenameTemplate.spaceReplaceLabels);
		String replaceText = exportTemplateSettings.getWhitespaceReplaceText();
		int selectedIndex = 0;
		exportTemplateSettings.setWhitespaceReplaceText(ExportFilenameTemplate.spaceReplaceChars[0]);

		for (int i = 0; i < ExportFilenameTemplate.spaceReplaceChars.length; i++) {
			if (replaceText.equals(ExportFilenameTemplate.spaceReplaceChars[i])) {
				exportTemplateSettings.setWhitespaceReplaceText(ExportFilenameTemplate.spaceReplaceChars[i]);
				selectedIndex = i;
			}
		}
		replaceWhitespaceComboBox.setSelectedIndex(selectedIndex);
		replaceWhitespaceComboBox.setEnabled(exportTemplateSettings.isExportFilenamePatternEnabled());
		replaceWhitespaceComboBox.addActionListener(e -> {
			exportTemplateSettings.setWhitespaceReplaceText(
					ExportFilenameTemplate.spaceReplaceChars[replaceWhitespaceComboBox.getSelectedIndex()]);
			updateExportFilenameExample();
		});

		JCheckBox zeroPadPartCountCheckbox = new JCheckBox("Zero-pad part count to two digits");
		zeroPadPartCountCheckbox.setSelected(exportTemplateSettings.isPartCountZeroPadded());
		zeroPadPartCountCheckbox.addActionListener(e -> {
			boolean selected = zeroPadPartCountCheckbox.isSelected();
			exportTemplateSettings.setPartCountZeroPadded(selected);
			updateExportFilenameExample();
		});

		final JTextField exportNameTextField = new JTextField(exportTemplateSettings.getExportFilenamePattern(), 40);
		exportNameTextField.setEditable(exportTemplateSettings.isExportFilenamePatternEnabled());
		exportNameTextField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void removeUpdate(DocumentEvent e) {
				exportTemplateSettings.setExportFilenamePattern(exportNameTextField.getText());
				updateExportFilenameExample();
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				exportTemplateSettings.setExportFilenamePattern(exportNameTextField.getText());
				updateExportFilenameExample();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				exportTemplateSettings.setExportFilenamePattern(exportNameTextField.getText());
				updateExportFilenameExample();
			}
		});

		JCheckBox alwaysRegenerateCheckBox = new JCheckBox("Always regenerate filenames using pattern");
		alwaysRegenerateCheckBox.setSelected(exportTemplateSettings.shouldAlwaysRegenerateFromPattern());
		alwaysRegenerateCheckBox.setEnabled(exportTemplateSettings.isExportFilenamePatternEnabled());
		alwaysRegenerateCheckBox.setToolTipText(
				"<html>Enable this setting to have Maestro always freshly generate filenames using the pattern.<br>"
						+ "Disable this setting to have Maestro use a filename from a previous export, if available.</html>");
		alwaysRegenerateCheckBox.addActionListener(e -> {
			boolean selected = alwaysRegenerateCheckBox.isSelected();
			exportTemplateSettings.setAlwaysRegenerateFromPattern(selected);
		});

		JCheckBox enablePatternExportCheckBox = new JCheckBox("Enable custom pattern for generating filenames");
		enablePatternExportCheckBox.setSelected(exportTemplateSettings.isExportFilenamePatternEnabled());
		enablePatternExportCheckBox.setToolTipText("<html>Select to enable filename generation using patterns.<br>"
				+ "Define the pattern in the text box below, referencing any variables in the variable list.<br>"
				+ "An example filename generated from your pattern is shown below the text box.</html>");
		enablePatternExportCheckBox.addActionListener(e -> {
			boolean selected = enablePatternExportCheckBox.isSelected();
			exportTemplateSettings.setExportFilenamePatternEnabled(selected);
			replaceWhitespaceComboBox.setEnabled(selected);
			exportNameTextField.setEditable(selected);
			zeroPadPartCountCheckbox.setEnabled(selected);
			alwaysRegenerateCheckBox.setEnabled(selected);
		});

		exportTemplateExampleLabel = new JLabel(".abc");
		JPanel examplePanel = new JPanel(new BorderLayout());
		examplePanel.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 0));
		examplePanel.add(exportTemplateExampleLabel, BorderLayout.CENTER);

		TableLayout layout = new TableLayout();
		layout.insertColumn(0, PREFERRED);
		layout.insertColumn(1, FILL);
		layout.setVGap(PAD);
		layout.setHGap(10);

		int row = -1;

		JPanel panel = new JPanel(layout);
		panel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));

		layout.insertRow(++row, PREFERRED);
		panel.add(pageLabel, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(enablePatternExportCheckBox, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(alwaysRegenerateCheckBox, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(whitespaceLabel, "0, " + row);
		panel.add(replaceWhitespaceComboBox, "1, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(zeroPadPartCountCheckbox, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(patternLabel, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(exportNameTextField, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(examplePanel, "0, " + row + ", 1, " + row);

		JLabel nameLabel = new JLabel("<html><u><b>Variable Name</b></u></html>");
		JLabel exampleLabel = new JLabel("<html><u><b>Example</b></u></html>");

		layout.insertRow(++row, PREFERRED);
		panel.add(nameLabel, "0, " + row);
		panel.add(exampleLabel, "1, " + row);

		AbcMetadataSource originalMetadataSource = exportTemplate.getMetadataSource();

		MockMetadataSource mockMetadata = new MockMetadataSource(originalMetadataSource);
		exportTemplate.setMetadataSource(mockMetadata);
		for (Entry<String, ExportFilenameTemplate.Variable> entry : exportTemplate.getVariables().entrySet()) {
			String tooltipText = "<html><b>" + entry.getKey() + "</b><br>"
					+ entry.getValue().getDescription().replace("\n", "<br>") + "</html>";

			JLabel keyLabel = new JLabel(entry.getKey());
			keyLabel.setToolTipText(tooltipText);
			JLabel descriptionLabel = new JLabel(entry.getValue().getValue());
			descriptionLabel.setToolTipText(tooltipText);

			layout.insertRow(++row, PREFERRED);
			panel.add(keyLabel, "0, " + row);
			panel.add(descriptionLabel, "1, " + row);
		}
		exportTemplate.setMetadataSource(originalMetadataSource);

		return panel;
	}

	private void updateExportFilenameExample() {
		AbcMetadataSource originalMetadataSource = exportTemplate.getMetadataSource();
		MockMetadataSource mockMetadata = new MockMetadataSource(originalMetadataSource);
		exportTemplate.setMetadataSource(mockMetadata);

		String exampleText = exportTemplate.formatName(exportTemplateSettings);
		exampleText = "Example filename:  " + exampleText;
		String exampleTextEllipsis = Util.ellipsis(exampleText, exportTemplateExampleLabel.getWidth(),
				exportTemplateExampleLabel.getFont());

		exportTemplateExampleLabel.setText(exampleTextEllipsis);
		if (!exampleText.equals(exampleTextEllipsis))
			exportTemplateExampleLabel.setToolTipText(exampleText);

		exportTemplate.setMetadataSource(originalMetadataSource);
	}

	private JPanel createSaveAndExportSettingsPanel() {
		JLabel titleLabel = new JLabel("<html><u><b>Save &amp; Export</b></u></html>");

		final JCheckBox promptSaveCheckBox = new JCheckBox("Prompt to save new " + AbcSong.MSX_FILE_DESCRIPTION_PLURAL);
		promptSaveCheckBox
				.setToolTipText("<html>Select to be prompted to save new " + AbcSong.MSX_FILE_DESCRIPTION_PLURAL
						+ "<br>" + "when opening a new file or closing the application.</html>");
		promptSaveCheckBox.setSelected(saveSettings.promptSaveNewSong);
		promptSaveCheckBox.addActionListener(e -> saveSettings.promptSaveNewSong = promptSaveCheckBox.isSelected());

		final JCheckBox showExportFileChooserCheckBox = new JCheckBox(
				"Always prompt for the ABC file name when exporting");
		showExportFileChooserCheckBox.setToolTipText("<html>Select to have the <b>Export ABC</b> button always<br>"
				+ "prompt for the name of the file.</html>");
		showExportFileChooserCheckBox.setSelected(saveSettings.showExportFileChooser);
		showExportFileChooserCheckBox.addActionListener(
				e -> saveSettings.showExportFileChooser = showExportFileChooserCheckBox.isSelected());

		final JCheckBox skipSilenceAtStartCheckBox = new JCheckBox("Remove silence from start of exported ABC");
		skipSilenceAtStartCheckBox.setToolTipText("<html>" //
				+ "Exported ABC files will not include silent measures from the<br>" //
				+ "beginning of the song.<br>" //
				+ "<br>" //
				+ "Uncheck if you want to export multiple ABC files from the same<br>" //
				+ "MIDI file that will be played together and need to line up." //
				+ "</html>");
		skipSilenceAtStartCheckBox.setSelected(saveSettings.skipSilenceAtStart);
		skipSilenceAtStartCheckBox
				.addActionListener(e -> saveSettings.skipSilenceAtStart = skipSilenceAtStartCheckBox.isSelected());

		final JCheckBox deleteMinimalNotesCheckBox = new JCheckBox("Delete Minimal Notes");
		deleteMinimalNotesCheckBox.setToolTipText("<html>" //
				+ "Notes that are quantized to zero duration<br>" //
				+ "will not be exported if this setting is enabled.<br>" //
				+ "<br>" //
				+ "Enabling this can prevent fast notes overlapping in the abc export<br>" //
				+ "which they would not do in the MIDI. Enabling this can fix severe dissonance in some songs.<br>" //
				+ "Beware, this is an advanced option, if in doubt, keep it off.</html>");
		deleteMinimalNotesCheckBox.setSelected(saveSettings.deleteMinimalNotes);
		deleteMinimalNotesCheckBox
				.addActionListener(e -> saveSettings.deleteMinimalNotes = deleteMinimalNotesCheckBox.isSelected());
		
		final JCheckBox convertABCStringsToBasicAsciiCheckBox = new JCheckBox(
				"Convert unicode, most ext. ascii and diacritical marks in ABC");
		convertABCStringsToBasicAsciiCheckBox.setToolTipText("<html>" //
				+ "If checked, exported ABC files will not include letters such as<br>" //
				+ "&aelig;&#248;&#229;&#246;&#228;&#223; etc.<br>" //
				+ "<br>" //
				+ "Most songbooks cannot handle such chars, it's recommended to have this enabled." //
				+ "</html>");
		convertABCStringsToBasicAsciiCheckBox.setSelected(saveSettings.convertABCStringsToBasicAscii);
		convertABCStringsToBasicAsciiCheckBox.addActionListener(
				e -> saveSettings.convertABCStringsToBasicAscii = convertABCStringsToBasicAsciiCheckBox.isSelected());

		TableLayout layout = new TableLayout();
		layout.insertColumn(0, PREFERRED);
//		layout.insertColumn(1, FILL);
		layout.setVGap(PAD);
//		layout.setHGap(10);

		JPanel panel = new JPanel(layout);
		panel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));

		int row = -1;

		layout.insertRow(++row, PREFERRED);
		panel.add(titleLabel, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(promptSaveCheckBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(showExportFileChooserCheckBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(skipSilenceAtStartCheckBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(deleteMinimalNotesCheckBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(convertABCStringsToBasicAsciiCheckBox, "0, " + row);

		return panel;
	}

	private JPanel createMiscPanel() {
		JLabel titleLabel = new JLabel("<html><u><b>Misc</b></u></html>");
		/*
		 * final JCheckBox showPrunedCheckBox = new JCheckBox("Show discarded notes in yellow");
		 * showPrunedCheckBox.setToolTipText("<html>" // +
		 * "Notes that is going to be discarded due to lotro's limit<br>" // +
		 * "of 6 simultanious notes will be show as yellow<br>" // + "for the selected instrument." // + "</html>");
		 * showPrunedCheckBox.setSelected(saveSettings.showPruned); showPrunedCheckBox.addActionListener(new
		 * ActionListener() {
		 * 
		 * @Override public void actionPerformed(ActionEvent e) { saveSettings.showPruned =
		 * showPrunedCheckBox.isSelected(); } });
		 */
		final JCheckBox showMaxPolyphonyCheckBox = new JCheckBox("Show polyphony");
		showMaxPolyphonyCheckBox.setToolTipText(
				"<html>Show number of simultanious notes<br>" + "that is playing as histogram.</html>");
		showMaxPolyphonyCheckBox.setSelected(miscSettings.showMaxPolyphony);
		showMaxPolyphonyCheckBox
				.addActionListener(e -> miscSettings.showMaxPolyphony = showMaxPolyphonyCheckBox.isSelected());

		final JCheckBox allBadgerCheckBox = new JCheckBox("Output all playable parts per default");
		allBadgerCheckBox.setToolTipText("<html>Output max playable parts for extended songbooks.</html>");
		allBadgerCheckBox.setSelected(miscSettings.allBadger);
		allBadgerCheckBox.addActionListener(e -> miscSettings.allBadger = allBadgerCheckBox.isSelected());
		allBadgerCheckBox.setEnabled(miscSettings.showBadger);

		final JCheckBox showBadgerCheckBox = new JCheckBox("Support extended songbook");
		showBadgerCheckBox.setToolTipText(
				"<html>Output and show genre and mood fields<br>" + "that are used in extended songbooks:<br>"
						+ "Badger Chapter, White Badger and Zedrock Chapter.</html>");
		showBadgerCheckBox.setSelected(miscSettings.showBadger);
		showBadgerCheckBox.addActionListener(e -> {
			miscSettings.showBadger = showBadgerCheckBox.isSelected();
			allBadgerCheckBox.setEnabled(miscSettings.showBadger);
		});
		
		final JCheckBox ignoreExpressionMessagesCheckBox = new JCheckBox("Ignore expression messages");
		ignoreExpressionMessagesCheckBox.setToolTipText("<html>When loading a new MIDI, ignore expression messages when assigning note velocities.<br>Changing this option wont have nay effect on already made projects or MIDIs that has already been loaded into Maestro.<br>Default: False.</html>");
		ignoreExpressionMessagesCheckBox.setSelected(miscSettings.ignoreExpressionMessages);
		ignoreExpressionMessagesCheckBox.addActionListener(e -> miscSettings.ignoreExpressionMessages = ignoreExpressionMessagesCheckBox.isSelected());

		final String defaultStr = "Default";
		String preferredDevice = NoteFilterSequencerWrapper.prefs.get(NoteFilterSequencerWrapper.prefMIDISelect, null);
		final JLabel deviceText = new JLabel("Preferred MIDI out device:");
		final JComboBox<String> deviceBox = new JComboBox<>();
		deviceBox.setToolTipText("<html>Select preferred MIDI Device<br>"
				+ "Will take effect next time a midi is loaded as source.</html>");
		deviceBox.addItem(defaultStr);
		Preferences prefsNode = NoteFilterSequencerWrapper.prefs.node(NoteFilterSequencerWrapper.prefMIDIHeader);
		String[] keys = {};
		try {
			keys = prefsNode.keys();
		} catch (BackingStoreException e1) {
			// e1.printStackTrace();
		}
		for (String key : keys) {
			deviceBox.addItem(key);
		}
		if (preferredDevice != null) {
			deviceBox.setSelectedItem(preferredDevice);
		} else {
			deviceBox.setSelectedItem(defaultStr);
		}
		deviceBox.setEditable(false);
		deviceBox.addActionListener(e -> {
			String s = (String) deviceBox.getSelectedItem();
			if ("Default".equals(s)) {
				NoteFilterSequencerWrapper.prefs.remove(NoteFilterSequencerWrapper.prefMIDISelect);
			} else {
				NoteFilterSequencerWrapper.prefs.put(NoteFilterSequencerWrapper.prefMIDISelect, s);
			}
			try {
				NoteFilterSequencerWrapper.prefs.flush();
			} catch (BackingStoreException e1) {
				// e1.printStackTrace();
			}
		});

		final JLabel themeText = new JLabel("Theme (Requires restart):");
		final JComboBox<String> themeBox = new JComboBox<>();
		final JLabel fontSizeLabel = new JLabel("Font size (Requires restart):");
		final JComboBox<String> fontBox = new JComboBox<>();

		themeBox.setToolTipText(
				"<html>Select the theme for Maestro. Must restart Maestro for it to take effect.</html>");
		//themeBox.addItem(defaultStr);
		for (String theme : Themer.themes) {
			themeBox.addItem(theme);
		}
		themeBox.setEditable(false);
		themeBox.addActionListener(e -> {
			miscSettings.theme = (String) themeBox.getSelectedItem();
			fontBox.setEnabled(!miscSettings.theme.equals(defaultStr));

			maestroPrefs.putInt("splitPanePos", -1);
		});
		themeBox.setSelectedItem(miscSettings.theme);

		fontBox.setToolTipText(
				"<html>Select a font size. Must restart Maestro for it to take effect.</html>");
		for (int i : Themer.fontSizes) {
			fontBox.addItem(Integer.toString(i));
		}
		fontBox.setEditable(false);
		fontBox.addActionListener(e -> {
			try {
				miscSettings.fontSize = Integer.parseInt((String) fontBox.getSelectedItem());
				Preferences.userNodeForPackage(MaestroMain.class).putInt("splitPanePos", -1);
			} catch (Exception ex) {
			}
		});
		fontBox.setSelectedItem(Integer.toString(miscSettings.fontSize));
		fontBox.setEnabled(!miscSettings.theme.equals(defaultStr));

		final JLabel bendLabel = new JLabel("Max range for new method of handling pitch bends (Requires restart):");
		final JComboBox<String> bendBox = new JComboBox<>();
		bendBox.setToolTipText(
				"<html>Select max seminote range for new way of handling pitch bends.<br>-1 means only old method. 12 is default.</html>");
		bendBox.addItem(Integer.toString(-1));
		bendBox.addItem(Integer.toString(6));
		bendBox.addItem(Integer.toString(12));
		bendBox.addItem(Integer.toString(24));
		bendBox.setEditable(false);
		bendBox.addActionListener(e -> {
			try {
				miscSettings.maxRangeForNewBendMethod = Integer.parseInt((String) bendBox.getSelectedItem());
			} catch (Exception ex) {
			}
		});
		bendBox.setSelectedItem(Integer.toString(miscSettings.maxRangeForNewBendMethod));

		TableLayout layout = new TableLayout();
		layout.insertColumn(0, FILL);
		layout.setVGap(PAD);

		JPanel panel = new JPanel(layout);
		panel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));

		int row = -1;

		layout.insertRow(++row, PREFERRED);
		panel.add(titleLabel, "0, " + row);

		// layout.insertRow(++row, PREFERRED);
		// panel.add(showPrunedCheckBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(showMaxPolyphonyCheckBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(showBadgerCheckBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(allBadgerCheckBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(ignoreExpressionMessagesCheckBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(deviceText, "0, " + row);
		layout.insertRow(++row, PREFERRED);
		panel.add(deviceBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(themeText, "0, " + row);
		layout.insertRow(++row, PREFERRED);
		panel.add(themeBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(fontSizeLabel, "0, " + row);
		layout.insertRow(++row, PREFERRED);
		panel.add(fontBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(bendLabel, "0, " + row);
		layout.insertRow(++row, PREFERRED);
		panel.add(bendBox, "0, " + row);

		return panel;
	}

	public void setActiveTab(int tab) {
		if (tab >= 0 && tab < tabPanel.getComponentCount())
			tabPanel.setSelectedIndex(tab);
	}

	public int getActiveTab() {
		return tabPanel.getSelectedIndex();
	}

	public boolean isSuccess() {
		return success;
	}

	public boolean isSettingPageReset() {
		return settingPageReset;
	}

	public int getResetPageIndex() {
		return settingPageResetIndex;
	}

	public boolean isNumbererSettingsChanged() {
		return numbererSettingsChanged;
	}

	public PartAutoNumberer.Settings getNumbererSettings() {
		return partNumbererSettings;
	}

	public PartNameTemplate.Settings getNameTemplateSettings() {
		return nameTemplateSettings;
	}

	public ExportFilenameTemplate.Settings getExportFilenameTemplateSettings() {
		return exportTemplateSettings;
	}

	public InstrNameSettings getInstrNameSettings() {
		return instrNameSettings;
	}

	public SaveAndExportSettings getSaveAndExportSettings() {
		return saveSettings;
	}

	public MiscSettings getMiscSettings() {
		return miscSettings;
	}

	public static class MockMetadataSource implements AbcMetadataSource, AbcPartMetadataSource {
		private AbcMetadataSource originalSource;

		public MockMetadataSource(AbcMetadataSource originalSource) {
			this.originalSource = originalSource;
		}

		@Override
		public String getTitle() {
			return "First Flute";
		}

		@Override
		public LotroInstrument getInstrument() {
			return LotroInstrument.BASIC_FLUTE;
		}

		@Override
		public int getPartNumber() {
			return 4;
		}

		@Override
		public String getSongTitle() {
			if (originalSource != null && originalSource.getSongTitle().length() > 0)
				return originalSource.getSongTitle();

			return "Example Title";
		}

		@Override
		public String getComposer() {
			if (originalSource != null && originalSource.getComposer().length() > 0)
				return originalSource.getComposer();

			return "Example Composer";
		}

		@Override
		public String getTranscriber() {
			if (originalSource != null && originalSource.getTranscriber().length() > 0)
				return originalSource.getTranscriber();

			return "Your Name Here";
		}

		@Override
		public long getSongLengthMicros() {
			long length = 0;
			if (originalSource != null)
				length = originalSource.getSongLengthMicros();

			return (length != 0) ? length : 227000000/* 3:47 */;
		}

		@Override
		public File getExportFile() {
			if (originalSource != null) {
				File saveFile = originalSource.getExportFile();
				if (saveFile != null)
					return saveFile;
			}

			return new File(Util.getLotroMusicPath(false), "band/examplesong.abc");
		}

		@Override
		public String getPartName(AbcPartMetadataSource abcPart) {
			return null;
		}

		@Override
		public String getGenre() {
			return "folk";
		}

		@Override
		public String getMood() {
			return "sad";
		}

		@Override
		public String getAllParts() {
			return "N: TS  1,   4";
		}

		@Override
		public int getActivePartCount() {
			return 5;
		}

		@Override
		public String getBadgerTitle() {
			return "N: Title: " + getComposer() + " - " + getSongTitle();
		}

		@Override
		public String getSourceFilename() {
			return "Example Midi.mid";
		}
	}
}
