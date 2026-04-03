package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentNick;
import com.digero.common.abc.StringCleaner;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.Themer;
import com.digero.common.util.Util;
import com.digero.common.view.LinkButton;
import com.digero.common.view.UIText;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.*;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

@SuppressWarnings("serial")
public class SettingsDialog extends JDialog implements TableLayoutConstants {
    protected static final Logger log = Logger.getLogger("misc.settings");

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

	private final PartAutoNumberer.Settings partNumbererSettings;

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
    private JComboBox<PartAutoNumberer.OrderOption> orderCombo;
	private JFrame own;
	private JComboBox<String> deviceBox;
    private AbcSong song = null;

    public SettingsDialog(JFrame owner, Preferences maestroPrefs, PartAutoNumberer partNumberer,
			PartNameTemplate nameTemplate, ExportFilenameTemplate exportTemplate, SaveAndExportSettings saveSettings,
			MiscSettings miscSettings, InstrNameSettings instrNameSettings) {
		super(owner, UIText.get("maestro.options.title"), true);
		this.own = owner;
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

		JButton resetButton = new JButton(UIText.get("maestro.options.reset.page"));
		resetButton.addActionListener(e -> {
			String page = tabPanel.getTitleAt(tabPanel.getSelectedIndex());
			String title = UIText.get("maestro.options.reset.0.settings", page);
			String message = UIText.get("maestro.options.are.you.sure.you.want.to.reset.the.0.settings.no.undo", page.toLowerCase());
			int result = JOptionPane.showConfirmDialog(null, message, title, JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.WARNING_MESSAGE, null);
			if (result == JOptionPane.YES_OPTION) {
				success = false;
				settingPageReset = true;
				settingPageResetIndex = tabPanel.getSelectedIndex();
				SettingsDialog.this.setVisible(false);
			}
		});

		JButton cancelButton = new JButton(UIText.get("maestro.options.cancel"));
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
        JPanel numberingPanel = createNumberingPanel();
        if (numberingPanel == null) {
            numberingPanel = createNumberingPanel();
        }
		tabPanel.addTab(UIText.get("maestro.options.abc.part.numbering"), numberingPanel); // NUMBERING_TAB
		tabPanel.addTab(UIText.get("maestro.options.abc.part.naming"), createNameTemplatePanel()); // NAME_TEMPLATE_TAB
		tabPanel.addTab(UIText.get("maestro.options.file.naming"), createExportTemplatePanel());
		tabPanel.addTab(UIText.get("maestro.options.instrument.names"), createInstrNamePanel());
		tabPanel.addTab(UIText.get("maestro.options.save.export"), createSaveAndExportSettingsPanel()); // SAVE_EXPORT_TAB
		tabPanel.addTab(UIText.get("maestro.options.misc"), createMiscPanel()); // MISC_TAB

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

    public void setVisible(boolean visible, AbcSong song) {
		this.song = song;
        super.setVisible(visible);
    }

    public void setVisible(boolean visible) {
        if (!visible) {
            super.setVisible(false);
            return;
        }
        throw new UnsupportedOperationException("Not supported, use the other version.");
    }

	private JPanel createNumberingPanel() {
		JLabel instrumentsTitle = new JLabel(UIText.get("maestro.options.html.b.u.first.part.number.u.b.html"));

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
            if (partNumbererSettings.getFirstNumber(inst) < 0) {
                log.severe("first number of "+inst+" is less than zero: "+partNumbererSettings.getFirstNumber(inst));
                partNumbererSettings.restoreDefaults();
                return null;
            }
            if (partNumbererSettings.getFirstNumber(inst) > (partNumbererSettings.isIncrementByTen() ? 10 : 999)) {
                log.severe("first number of "+inst+" is too large: "+partNumbererSettings.getFirstNumber(inst)+"/"+(partNumbererSettings.isIncrementByTen() ? 10 : 999));
                partNumbererSettings.restoreDefaults();
                return null;
            }
			InstrumentSpinner spinner = new InstrumentSpinner(inst);
			instrumentSpinners.add(spinner);
			instrumentsPanel.add(spinner, col + ", " + row);
			instrumentsPanel.add(new JLabel(inst.getLocalFriendlyName() + " "), (col + 1) + ", " + row);
		}

		JLabel incrementTitle = new JLabel(UIText.get("maestro.options.html.b.u.increment.u.b.html"));
		JLabel incrementDescr = new JLabel(UIText.get("maestro.options.interval.between.multiple.parts.of.the.same.instrument"));

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

        JLabel orderTitle = new JLabel(UIText.get("maestro.options.html.b.u.part.order.sorting.u.b.html"));
        orderCombo = new JComboBox<>(PartAutoNumberer.OrderOption.values());
        orderCombo.setSelectedItem(partNumbererSettings.orderOption);
        orderCombo.addActionListener(e -> {
            PartAutoNumberer.OrderOption oldOrder = partNumbererSettings.orderOption;
            PartAutoNumberer.OrderOption newOrder = ((PartAutoNumberer.OrderOption)orderCombo.getSelectedItem());
            if (oldOrder == newOrder)
                return;

            numbererSettingsChanged = true;
            partNumbererSettings.orderOption = newOrder;
        });

        TableLayout incrementPanelLayout = new TableLayout(//
				new double[] { PREFERRED, FILL }, //
				new double[] { PREFERRED });
		incrementPanelLayout.setHGap(10);
		JPanel incrementPanel = new JPanel(incrementPanelLayout);
		incrementPanel.setBorder(BorderFactory.createEmptyBorder(0, PAD, 0, 0));
		incrementPanel.add(incrementComboBox, "0, 0, C, T");
		incrementPanel.add(incrementDescr, "1, 0");

		JLabel numberingConfigLabel = new JLabel(UIText.get("maestro.options.html.b.u.part.numbering.config.u.b.html"));

		LinkButton importButton = new LinkButton(UIText.get("maestro.options.import"));
		importButton.addActionListener(e -> loadPartNumberingConfig());

		JLabel separator = new JLabel(" | ");

		LinkButton exportButton = new LinkButton(UIText.get("maestro.options.export"));
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
				new double[] { PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED });

		numberingLayout.setVGap(PAD);
		JPanel numberingPanel = new JPanel(numberingLayout);
		numberingPanel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
        numberingPanel.add(orderTitle, "0, 0");
        numberingPanel.add(orderCombo, "0, 1, L, C");
		numberingPanel.add(instrumentsTitle, "0, 2");
		numberingPanel.add(instrumentsPanel, "0, 3, L, F");
		numberingPanel.add(incrementTitle, "0, 4");
		numberingPanel.add(incrementPanel, "0, 5, F, F");
		numberingPanel.add(mapPanel, "0, 6");
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

			instrNamePanel.add(dropdown, col + ", " + row);
			instrNamePanel.add(new JLabel(inst.getLocalFriendlyName() + " "), (col + 1) + ", " + row);
		}

		TableLayout numberingLayout = new TableLayout(//
				new double[] { FILL }, //
				new double[] { PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED });

		numberingLayout.setVGap(PAD);
		JPanel backPanel = new JPanel(numberingLayout);
		backPanel.setBorder(BorderFactory.createEmptyBorder(PAD, PAD, PAD, PAD));
		JLabel instrumentsTitle = new JLabel(UIText.get("maestro.options.html.b.u.default.instrument.naming.for.parts.u.b.html"));
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
			addItem(UIText.get(instrument.localFriendlyNameKey, Locale.FRENCH));
			addItem(UIText.get(instrument.localFriendlyNameKey, Locale.GERMAN));
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
			super(new SpinnerNumberModel(partNumbererSettings.getFirstNumber(instrument), 1,
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
				new ExtensionFileFilter(UIText.get("maestro.options.part.numbering.config.file.partsconfig.txt"), Util.PARTS_CONFIG_FILE_EXTENSION_NO_DOT));

		if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			return false;

		File loadFile = fileChooser.getSelectedFile();

		PartNumberingConfig config = new PartNumberingConfig();

		try {
			config.load(loadFile);
		} catch (Exception e) {
			JOptionPane.showMessageDialog(this, UIText.get("maestro.options.failed.to.load.part.numbering.config.0", e.getMessage()),
					UIText.get("maestro.options.failed.to.load.part.numbering.config"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		/*
		if (song != null && song.getParts() != null && !song.getParts().isEmpty()) {
			for (AbcPart part : song.getParts()) {
				// unlock all parts
				part.setPartNumberManuallyAssigned(false, true);
			}
		}
		*/

		incrementComboBox.setSelectedItem(config.increment);
        orderCombo.setSelectedItem(config.orderOption);

		for (LotroInstrument ins : config.firstPartMap.keySet()) {
			int firstPartNo = config.firstPartMap.get(ins);

			for (InstrumentSpinner spinner : instrumentSpinners) {
				if (spinner.instrument.name().equals(ins.name())) {
					spinner.setValue(firstPartNo);
                    spinner.stateChanged(null);
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
				new ExtensionFileFilter(UIText.get("maestro.options.part.numbering.config.file.partsconfig.txt"), Util.PARTS_CONFIG_FILE_EXTENSION_NO_DOT));

		File saveFile;
		if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return false;

		saveFile = fileChooser.getSelectedFile();

		if (saveFile.getName().indexOf('.') < 0) {
			saveFile = new File(saveFile.getParentFile(), saveFile.getName() + ".partsconfig.txt");
		}

		if (saveFile.exists()) {
			int result = JOptionPane.showConfirmDialog(this,
					UIText.get("maestro.options.file.0.already.exists.overwrite", saveFile.getName()), UIText.get("maestro.options.confirm.overwrite"),
					JOptionPane.OK_CANCEL_OPTION);
			if (result != JOptionPane.OK_OPTION)
				return false;
		}

		Map<LotroInstrument, Integer> map = new EnumMap<>(LotroInstrument.class);
		int increment = (int) incrementComboBox.getSelectedItem();

		for (InstrumentSpinner spinner : instrumentSpinners) {
			map.put(spinner.instrument, (Integer) spinner.getValue());
		}

		PartNumberingConfig config = new PartNumberingConfig(increment, map, (PartAutoNumberer.OrderOption) orderCombo.getSelectedItem());

		try {
			config.save(saveFile);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, UIText.get("maestro.options.failed.to.save.part.numbering.config.0", e.getMessage()),
					UIText.get("maestro.options.failed.to.save.part.numbering.config"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		prefs.put(PART_NUMBERING_CONFIG_DIRECTORY, fileChooser.getCurrentDirectory().getAbsolutePath());
		return true;
	}

	private JPanel createNameTemplatePanel() {
		JLabel whitespaceLabel = new JLabel(UIText.get("maestro.options.html.b.replace.spaces.in.variables.with.b.html"));

		JComboBox<String> replaceWhitespaceComboBox = new JComboBox<>(PartNameTemplate.Settings.spaceReplaceLabels);
		String replaceText = nameTemplateSettings.getWhitespaceReplaceText();
		int selectedIndex = 0;
		nameTemplateSettings.setWhitespaceReplaceText(PartNameTemplate.Settings.spaceReplaceChars[0]);

		for (int i = 0; i < PartNameTemplate.Settings.spaceReplaceChars.length; i++) {
			if (replaceText.equals(PartNameTemplate.Settings.spaceReplaceChars[i])) {
				nameTemplateSettings.setWhitespaceReplaceText(PartNameTemplate.Settings.spaceReplaceChars[i]);
				selectedIndex = i;
			}
		}
		replaceWhitespaceComboBox.setSelectedIndex(selectedIndex);
		replaceWhitespaceComboBox.addActionListener(e -> {
			nameTemplateSettings.setWhitespaceReplaceText(
					PartNameTemplate.Settings.spaceReplaceChars[replaceWhitespaceComboBox.getSelectedIndex()]);
			updateNameTemplateExample();
		});
		
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
		JLabel patternLabel = new JLabel(UIText.get("maestro.options.html.b.u.pattern.for.abc.part.name.b.u.html"));
		nameTemplatePanel.add(patternLabel, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		nameTemplatePanel.add(partNameTextField, "0, " + row + ", 1, " + row);

		layout.insertRow(++row, PREFERRED);
		nameTemplatePanel.add(whitespaceLabel, "0, " + row + ", 1, " + row + ", F, F");

		layout.insertRow(++row, PREFERRED);
		nameTemplatePanel.add(replaceWhitespaceComboBox, "0, " + row + ", 1, " + row + ", F, F");
		
		layout.insertRow(++row, PREFERRED);
		nameTemplatePanel.add(examplePanel, "0, " + row + ", 1, " + row + ", F, F");
		
		layout.insertRow(++row, PREFERRED);

		JLabel nameLabel = new JLabel(UIText.get("maestro.options.html.u.b.variable.name.b.u.html"));
		JLabel exampleLabel = new JLabel(UIText.get("maestro.options.html.u.b.example.b.u.html"));

		layout.insertRow(++row, PREFERRED);
		nameTemplatePanel.add(nameLabel, "0, " + row);
		nameTemplatePanel.add(exampleLabel, "1, " + row);

		MockMetadataSource mockMetadata = new MockMetadataSource(song);
		StringCleaner.cleanABC = saveSettings.convertABCStringsToBasicAscii;
		for (Entry<String, PartNameTemplate.Variable> entry : nameTemplate.getVariables().entrySet()) {
			String tooltipText = "<html><b>" + entry.getKey() + "</b><br>"
					+ entry.getValue().getDescription().replace("\n", "<br>") + "</html>";

			JLabel keyLabel = new JLabel(entry.getKey());
			keyLabel.setToolTipText(tooltipText);
			JLabel descriptionLabel = new JLabel(StringCleaner.cleanForABC(entry.getValue().getValue(mockMetadata, mockMetadata)));
			descriptionLabel.setToolTipText(tooltipText);

			layout.insertRow(++row, PREFERRED);
			nameTemplatePanel.add(keyLabel, "0, " + row);
			nameTemplatePanel.add(descriptionLabel, "1, " + row);
		}

		return nameTemplatePanel;
	}

	private void updateNameTemplateExample() {
		MockMetadataSource mockMetadata = new MockMetadataSource(song);

		String exampleText = nameTemplate.formatName(mockMetadata, nameTemplateSettings.getPartNamePattern(), mockMetadata, nameTemplateSettings.getWhitespaceReplaceText());
		StringCleaner.cleanABC = saveSettings.convertABCStringsToBasicAscii;
		exampleText = StringCleaner.cleanForABC(exampleText);
		String exampleTextEllipsis = Util.ellipsis(exampleText, nameTemplateExampleLabel.getWidth(),
				nameTemplateExampleLabel.getFont());

		nameTemplateExampleLabel.setText(exampleTextEllipsis);
		if (!exampleText.equals(exampleTextEllipsis))
			nameTemplateExampleLabel.setToolTipText(exampleText);

	}

	private JPanel createExportTemplatePanel() {
		JLabel pageLabel = new JLabel(UIText.get("maestro.options.html.b.u.abc.and.msx.filename.settings.b.u.html"));

		JLabel patternLabel = new JLabel(UIText.get("maestro.options.html.b.custom.pattern.for.exported.filename.b.html"));

		JLabel whitespaceLabel = new JLabel(UIText.get("maestro.options.html.b.replace.spaces.in.variables.with.b.html"));

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

		JCheckBox zeroPadPartCountCheckbox = new JCheckBox(UIText.get("maestro.options.zero.pad.part.count.to.two.digits"));
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

		JCheckBox alwaysRegenerateCheckBox = new JCheckBox(UIText.get("maestro.options.always.regenerate.filenames.using.pattern"));
		alwaysRegenerateCheckBox.setSelected(exportTemplateSettings.shouldAlwaysRegenerateFromPattern());
		alwaysRegenerateCheckBox.setEnabled(exportTemplateSettings.isExportFilenamePatternEnabled());
		alwaysRegenerateCheckBox.setToolTipText(
				UIText.get("maestro.options.tip.always.regen"));
		alwaysRegenerateCheckBox.addActionListener(e -> {
			boolean selected = alwaysRegenerateCheckBox.isSelected();
			exportTemplateSettings.setAlwaysRegenerateFromPattern(selected);
		});

		JCheckBox enablePatternExportCheckBox = new JCheckBox(UIText.get("maestro.options.enable.custom.pattern.for.generating.filenames"));
		enablePatternExportCheckBox.setSelected(exportTemplateSettings.isExportFilenamePatternEnabled());
		enablePatternExportCheckBox.setToolTipText(UIText.get("maestro.options.enable.filename.generation.using.patterns"));
		enablePatternExportCheckBox.addActionListener(e -> {
			boolean selected = enablePatternExportCheckBox.isSelected();
			exportTemplateSettings.setExportFilenamePatternEnabled(selected);
			replaceWhitespaceComboBox.setEnabled(selected);
			exportNameTextField.setEditable(selected);
			zeroPadPartCountCheckbox.setEnabled(selected);
			alwaysRegenerateCheckBox.setEnabled(selected);
		});

		exportTemplateExampleLabel = new JLabel(Util.ABC_FILE_EXTENSION);
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

		JLabel nameLabel = new JLabel(UIText.get("maestro.options.html.u.b.variable.name.b.u.html"));
		JLabel exampleLabel = new JLabel(UIText.get("maestro.options.html.u.b.example.b.u.html"));

		layout.insertRow(++row, PREFERRED);
		panel.add(nameLabel, "0, " + row);
		panel.add(exampleLabel, "1, " + row);


		MockMetadataSource mockMetadata = new MockMetadataSource(song);
		for (Entry<String, ExportFilenameTemplate.Variable> entry : exportTemplate.getVariables().entrySet()) {
			String tooltipText = "<html><b>" + entry.getKey() + "</b><br>"
					+ entry.getValue().getDescription().replace("\n", "<br>") + "</html>";

			JLabel keyLabel = new JLabel(entry.getKey());
			keyLabel.setToolTipText(tooltipText);
			JLabel descriptionLabel = new JLabel(StringCleaner.cleanForFileName(entry.getValue().getValue(exportTemplateSettings, mockMetadata)));
			descriptionLabel.setToolTipText(tooltipText);

			layout.insertRow(++row, PREFERRED);
			panel.add(keyLabel, "0, " + row);
			panel.add(descriptionLabel, "1, " + row);
		}

		return panel;
	}

	private void updateExportFilenameExample() {
		MockMetadataSource mockMetadata = new MockMetadataSource(song);

		String exampleText = StringCleaner.cleanForFileName(Util.fileNameWithoutExtension(exportTemplate.formatName(exportTemplateSettings, mockMetadata)))+Util.ABC_FILE_EXTENSION;
		exampleText = UIText.get("maestro.options.example.filename.0", exampleText);
		String exampleTextEllipsis = Util.ellipsis(exampleText, exportTemplateExampleLabel.getWidth(),
				exportTemplateExampleLabel.getFont());

		exportTemplateExampleLabel.setText(exampleTextEllipsis);
		if (!exampleText.equals(exampleTextEllipsis))
			exportTemplateExampleLabel.setToolTipText(exampleText);

	}

	private JPanel createSaveAndExportSettingsPanel() {
		JLabel titleLabel = new JLabel(UIText.get("maestro.options.html.u.b.save.amp.export.b.u.html"));

		final JCheckBox promptSaveCheckBox = new JCheckBox(UIText.get("maestro.options.prompt.to.save.new.0", AbcSong.MSX_FILE_DESCRIPTION_PLURAL));
		promptSaveCheckBox
				.setToolTipText(UIText.get("maestro.options.tip.prompted.to.save", AbcSong.MSX_FILE_DESCRIPTION_PLURAL));
		promptSaveCheckBox.setSelected(saveSettings.promptSaveNewSong);
		promptSaveCheckBox.addActionListener(e -> saveSettings.promptSaveNewSong = promptSaveCheckBox.isSelected());

		final JCheckBox showExportFileChooserCheckBox = new JCheckBox(
				UIText.get("maestro.options.always.prompt.for.the.abc.file.name.when.exporting"));
		showExportFileChooserCheckBox.setToolTipText(UIText.get("maestro.options.tip.always.prompt.for.the.name.of.the.file"));
		showExportFileChooserCheckBox.setSelected(saveSettings.showExportFileChooser);
		showExportFileChooserCheckBox.addActionListener(
				e -> saveSettings.showExportFileChooser = showExportFileChooserCheckBox.isSelected());

		final JCheckBox skipSilenceAtStartCheckBox = new JCheckBox(UIText.get("maestro.options.remove.silence.from.start.of.exported.abc"));
		skipSilenceAtStartCheckBox.setToolTipText(UIText.get("maestro.options.tip.skip.silence"));
		skipSilenceAtStartCheckBox.setSelected(saveSettings.skipSilenceAtStart);
		skipSilenceAtStartCheckBox
				.addActionListener(e -> saveSettings.skipSilenceAtStart = skipSilenceAtStartCheckBox.isSelected());

		final JCheckBox deleteMinimalNotesCheckBox = new JCheckBox(UIText.get("maestro.options.delete.minimal.notes"));
		deleteMinimalNotesCheckBox.setToolTipText(UIText.get("maestro.options.tip.delete.minimal.notes"));
		deleteMinimalNotesCheckBox.setSelected(saveSettings.deleteMinimalNotes);
		deleteMinimalNotesCheckBox
				.addActionListener(e -> saveSettings.deleteMinimalNotes = deleteMinimalNotesCheckBox.isSelected());
		
		final JCheckBox convertABCStringsToBasicAsciiCheckBox = new JCheckBox(
				UIText.get("maestro.options.convert.unicode.most.ext.ascii.and.diacritical.marks.in.abc"));
		convertABCStringsToBasicAsciiCheckBox.setToolTipText(UIText.get("maestro.options.tip.convert.to.ascii"));
		convertABCStringsToBasicAsciiCheckBox.setSelected(saveSettings.convertABCStringsToBasicAscii);
		convertABCStringsToBasicAsciiCheckBox.addActionListener(
				e -> saveSettings.convertABCStringsToBasicAscii = convertABCStringsToBasicAsciiCheckBox.isSelected());
		
		final JLabel defaultTimingText = new JLabel(UIText.get("maestro.options.default.timing"));
		final JComboBox<ProjectFrame.TimingEnum> defaultTimingComboBox = new JComboBox<>();
		defaultTimingComboBox.setToolTipText(
				UIText.get("maestro.options.html.select.default.timing.for.new.projects.from.midi.html"));
		for (ProjectFrame.TimingEnum choice : ProjectFrame.TimingEnum.values()) {
			defaultTimingComboBox.addItem(choice);
		}
		defaultTimingComboBox.setEditable(false);
		defaultTimingComboBox.addActionListener(e -> {
			try {
				saveSettings.defaultTiming = ((ProjectFrame.TimingEnum) Objects.requireNonNull(defaultTimingComboBox.getSelectedItem())).settingsString;
			} catch (Exception ignored) {
			}
		});
		defaultTimingComboBox.setSelectedItem(ProjectFrame.TimingEnum.getFromSettings(saveSettings.defaultTiming));
		
		final JCheckBox exceed6CheckBox = new JCheckBox(
				UIText.get("maestro.options.allow.more.than.6.note.polyphony.in.parts.organic.only"));
		exceed6CheckBox.setToolTipText(UIText.get("maestro.options.tip.exceed6"));
		exceed6CheckBox.setSelected(saveSettings.useRestsInChords);
		exceed6CheckBox.addActionListener(
				e -> saveSettings.useRestsInChords = exceed6CheckBox.isSelected());

        final JCheckBox warnSamePartsCheckBox = new JCheckBox(
				UIText.get("maestro.options.warn.if.two.or.more.part.has.same.name"));
        warnSamePartsCheckBox.setToolTipText(UIText.get("maestro.options.same.names.can.make.some.songbooks.get.confused"));
        warnSamePartsCheckBox.setSelected(saveSettings.warnOnExportOfSamePartNames);
        warnSamePartsCheckBox.addActionListener(
                e -> saveSettings.warnOnExportOfSamePartNames = warnSamePartsCheckBox.isSelected());

        final JCheckBox reduceFileSizeCheckBox = new JCheckBox(
				UIText.get("maestro.options.reduce.exported.abc.file.size"));
        reduceFileSizeCheckBox.setToolTipText(UIText.get("maestro.options.tip.reduced.filesize"));
        reduceFileSizeCheckBox.setSelected(saveSettings.reducedFilesize);
        reduceFileSizeCheckBox.addActionListener(
                e -> saveSettings.reducedFilesize = reduceFileSizeCheckBox.isSelected());

		final JCheckBox countUpLyricsCheckBox = new JCheckBox(
				UIText.get("maestro.options.count.up.lyrics.timestamps"));
		countUpLyricsCheckBox.setToolTipText(UIText.get("maestro.options.tip.count.up.lyrics.timestamps"));
		countUpLyricsCheckBox.setSelected(saveSettings.countUpLyrics);
		countUpLyricsCheckBox.addActionListener(
				e -> saveSettings.countUpLyrics = countUpLyricsCheckBox.isSelected());

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
		
		layout.insertRow(++row, PREFERRED);
		panel.add(defaultTimingText, "0, " + row);
		layout.insertRow(++row, PREFERRED);
		panel.add(defaultTimingComboBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(exceed6CheckBox, "0, " + row);

        layout.insertRow(++row, PREFERRED);
        panel.add(warnSamePartsCheckBox, "0, " + row);

        layout.insertRow(++row, PREFERRED);
        panel.add(reduceFileSizeCheckBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(countUpLyricsCheckBox, "0, " + row);

		return panel;
	}

	private JPanel createMiscPanel() {
		JLabel titleLabel = new JLabel(UIText.get("maestro.options.html.u.b.misc.b.u.html"));
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
		
		final JCheckBox checkForUpdatesCheckBox = new JCheckBox(UIText.get("maestro.options.check.for.updates"));
		checkForUpdatesCheckBox.setToolTipText(UIText.get("maestro.options.tip.check.for.updates"));
		checkForUpdatesCheckBox.setSelected(miscSettings.checkForUpdates);
		checkForUpdatesCheckBox.addActionListener(e -> miscSettings.checkForUpdates = checkForUpdatesCheckBox.isSelected());
		
		final JCheckBox showMaxPolyphonyCheckBox = new JCheckBox(UIText.get("maestro.options.show.polyphony"));
		showMaxPolyphonyCheckBox.setToolTipText(
				UIText.get("maestro.options.histogram"));
		showMaxPolyphonyCheckBox.setSelected(miscSettings.showMaxPolyphony);
		showMaxPolyphonyCheckBox
				.addActionListener(e -> miscSettings.showMaxPolyphony = showMaxPolyphonyCheckBox.isSelected());
		/*
		final JCheckBox allBadgerCheckBox = new JCheckBox("Output all playable parts per default");
		allBadgerCheckBox.setToolTipText("<html>Output max playable parts for extended songbooks.</html>");
		allBadgerCheckBox.setSelected(miscSettings.allBadger);
		allBadgerCheckBox.addActionListener(e -> miscSettings.allBadger = allBadgerCheckBox.isSelected());
		allBadgerCheckBox.setEnabled(miscSettings.showBadger);
		*/
		
		final JCheckBox showBadgerCheckBox = new JCheckBox(UIText.get("maestro.options.support.extended.songbook"));
		showBadgerCheckBox.setToolTipText(
				UIText.get("maestro.options.tip.badger"));
		showBadgerCheckBox.setSelected(miscSettings.showBadger);
		showBadgerCheckBox.addActionListener(e -> {
			miscSettings.showBadger = showBadgerCheckBox.isSelected();
			//allBadgerCheckBox.setEnabled(miscSettings.showBadger);
		});
		
		final JCheckBox ignoreExpressionMessagesCheckBox = new JCheckBox(UIText.get("maestro.options.ignore.expression.messages"));
		ignoreExpressionMessagesCheckBox.setToolTipText(UIText.get("maestro.options.ignore.expression.messages"));
		ignoreExpressionMessagesCheckBox.setSelected(miscSettings.ignoreExpressionMessages);
		ignoreExpressionMessagesCheckBox.addActionListener(e -> miscSettings.ignoreExpressionMessages = ignoreExpressionMessagesCheckBox.isSelected());
		
		final JCheckBox autoplayOnOpenCheckBox = new JCheckBox(UIText.get("maestro.options.autoplay.files.on.open"));
		autoplayOnOpenCheckBox.setToolTipText(UIText.get("maestro.options.tip.autoplay.files.on.open"));
		autoplayOnOpenCheckBox.setSelected(miscSettings.autoplayOnOpen);
		autoplayOnOpenCheckBox.addActionListener(e -> miscSettings.autoplayOnOpen = autoplayOnOpenCheckBox.isSelected());

		final String defaultStr = "Default"; //NON-NLS
		
		final JLabel deviceText = new JLabel(UIText.get("maestro.options.preferred.midi.out.device"));
		deviceBox = new JComboBox<>();
		deviceBox.setToolTipText(UIText.get("maestro.options.html.select.preferred.midi.device"));
		refreshDeviceBox();
		deviceBox.setEditable(false);
		deviceBox.addActionListener(e -> {
			String s = (String) deviceBox.getSelectedItem();
			if (defaultStr.equals(s)) {
				NoteFilterSequencerWrapper.prefs.remove(NoteFilterSequencerWrapper.prefMIDISelect);
			} else if (s == null) {
			} else {
				NoteFilterSequencerWrapper.prefs.put(NoteFilterSequencerWrapper.prefMIDISelect, s);
			}
			try {
				NoteFilterSequencerWrapper.prefs.flush();
			} catch (BackingStoreException e1) {
				// e1.printStackTrace();
			}
		});

		final JLabel themeText = new JLabel(UIText.get("maestro.options.theme.requires.restart"));
		final JComboBox<String> themeBox = new JComboBox<>();
		final JLabel fontSizeLabel = new JLabel(UIText.get("maestro.options.font.size.requires.restart"));
		final JComboBox<String> fontBox = new JComboBox<>();

		themeBox.setToolTipText(
				UIText.get("maestro.options.tip.theme"));
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
				UIText.get("maestro.options.tip.font.size"));
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

		final JLabel bendLabel = new JLabel(UIText.get("maestro.options.max.seminote.range.for.pitch.bends"));
		final JComboBox<String> bendBox = new JComboBox<>();
		bendBox.setToolTipText(
				UIText.get("maestro.options.tip.max.seminote.range.for.pitch.bends"));
		bendBox.addItem(Integer.toString(-1));
		bendBox.addItem(Integer.toString(6));
		bendBox.addItem(Integer.toString(12));
		bendBox.addItem(Integer.toString(16));
		bendBox.setEditable(false);
		bendBox.addActionListener(e -> {
			try {
				miscSettings.maxRangeForNewBendMethod = Integer.parseInt((String) bendBox.getSelectedItem());
			} catch (Exception ex) {
			}
		});
		bendBox.setSelectedItem(Integer.toString(miscSettings.maxRangeForNewBendMethod));

		final String ending = Util.OPTIONS_BACKUP_FILE_EXTENSION_NO_DOT;
		ExtensionFileFilter filter = new ExtensionFileFilter(UIText.get("maestro.options.maestro.settings.files.0", ending), ending);
		
		final JButton exportPrefs = new JButton(UIText.get("maestro.options.export.all.settings.to.a.file"));
		exportPrefs.addActionListener(a -> {
			try {
				JFileChooser jfc = new JFileChooser();
				jfc.setDialogTitle(UIText.get("maestro.options.export.all.settings.to.a.file"));
				jfc.setFileFilter(filter);
				jfc.setSelectedFile(new File("maestro-settings-backup."+ending));
				int returnVal = jfc.showSaveDialog(this);
				if(returnVal == JFileChooser.APPROVE_OPTION) {
					if (jfc.getSelectedFile().exists()) {
						JOptionPane.showMessageDialog(this, UIText.get("maestro.options.file.already.exist.settings.not.saved"));
					} else {
						FileOutputStream fos = new FileOutputStream(jfc.getSelectedFile());
						Preferences prefsMain = Preferences.userRoot().node("/com/digero"); //NON-NLS
						//Preferences prefsTools = Preferences.userRoot().node("/com/aifel");
						prefsMain.exportSubtree(fos);
						//prefsTools.exportSubtree(fos);
						fos.close();
						System.out.println("Backup saved successfully as "+jfc.getSelectedFile());
					}
				}
			} catch (Exception e) {
                log.log(Level.SEVERE, "Failed to export settings backup", e);
				JOptionPane.showMessageDialog(this, UIText.get("maestro.options.settings.failed.saving.0", e.toString()));
			}
		});
		
		final JButton importPrefs = new JButton(UIText.get("maestro.options.import.all.settings.and.exit.maestro"));
		importPrefs.addActionListener(a -> {
            importPrefs.setEnabled(false);
			try {
				if(((ProjectFrame)(own)).closeSong()) {
					JFileChooser jfc = new JFileChooser();
					jfc.setDialogTitle(UIText.get("maestro.options.import.all.settings.and.exit.maestro"));
					jfc.setFileFilter(filter);
					int returnVal = jfc.showOpenDialog(this);
					if(returnVal == JFileChooser.APPROVE_OPTION) {
                        // Offload I/O to Virtual Thread
                        Thread.ofVirtual().start(() -> {
                            try (FileInputStream fis = new FileInputStream(jfc.getSelectedFile())) {
                                Preferences.importPreferences(fis);
                                SwingUtilities.invokeLater(() -> {
                                    log.info("Backup loaded successfully from "+jfc.getSelectedFile());
                                    this.setVisible(false);
                                    System.exit(0);
                                });
                            } catch (Throwable e) {
                                log.log(Level.SEVERE, UIText.get("maestro.options.failed.to.load.settings.backup"), e);
                                SwingUtilities.invokeLater(() -> {
                                        JOptionPane.showMessageDialog(this, UIText.get("maestro.options.settings.failed.opening.1", e));
                                        importPrefs.setEnabled(true);
                                    }
                                );
                            }
                        });
                        return;//return so we don't re-enable the button (thread does that)
				    }
				}
			} catch (Exception e) {
                log.log(Level.SEVERE, "Failed to import settings backup", e);
				JOptionPane.showMessageDialog(this, UIText.get("maestro.options.settings.failed.opening.0", e.toString()));
			}
            importPrefs.setEnabled(true);
		});

        final JButton dissButton = new JButton(UIText.get("maestro.options.dissonance.graph"));
        dissButton.addActionListener(a -> {
            DissonanceSettingsDialog dlg = new DissonanceSettingsDialog(SettingsDialog.this, miscSettings);
            dlg.setVisible(true);
            if (dlg.wasSuccess()) {
                // Settings are already saved to miscSettings and Preferences
                miscSettings.dissModified = true;
            }
        });

		final JLabel langLabel = new JLabel("Language/Sprache/Langue"); //NON-NLS
		final JComboBox<String> langBox = new JComboBox<>(new String[]{UIText.LANG_EN, UIText.LANG_FR, UIText.LANG_DE});
		langBox.setToolTipText("<html>Changes take effect after restarting Maestro.<br><br>Änderungen werden nach einem Neustart von Maestro wirksam.<br><br>Les changements prendront effet après un redémarrage de Maestro.</html>");
		langLabel.setToolTipText("<html>Changes take effect after restarting Maestro.<br><br>Änderungen werden nach einem Neustart von Maestro wirksam.<br><br>Les changements prendront effet après un redémarrage de Maestro.</html>");
		langBox.setEditable(false);
		langBox.setSelectedItem(miscSettings.locale==null?UIText.LANG_EN:miscSettings.locale.toLowerCase());
		langBox.addActionListener(e -> {
			String item = langBox.getSelectedItem().toString();
			miscSettings.locale = item;
		});
		
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
		panel.add(checkForUpdatesCheckBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(showMaxPolyphonyCheckBox, "0, " + row);

		layout.insertRow(++row, PREFERRED);
		panel.add(showBadgerCheckBox, "0, " + row);

		//layout.insertRow(++row, PREFERRED);
		//panel.add(allBadgerCheckBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(ignoreExpressionMessagesCheckBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(autoplayOnOpenCheckBox, "0, " + row);
		
		layout.insertRow(++row, PREFERRED);
		panel.add(deviceText, "0, " + row+", L, C");
		layout.insertRow(++row, PREFERRED);
		panel.add(deviceBox, "0, " + row+", L, C");

		layout.insertRow(++row, PREFERRED);
		panel.add(themeText, "0, " + row+", L, C");
		layout.insertRow(++row, PREFERRED);
		panel.add(themeBox, "0, " + row+", L, C");

		layout.insertRow(++row, PREFERRED);
		panel.add(fontSizeLabel, "0, " + row+", L, C");
		layout.insertRow(++row, PREFERRED);
		panel.add(fontBox, "0, " + row+", L, C");

		layout.insertRow(++row, PREFERRED);
		panel.add(bendLabel, "0, " + row+", L, C");
		layout.insertRow(++row, PREFERRED);
		panel.add(bendBox, "0, " + row+", L, C");

		layout.insertRow(++row, PREFERRED);
		panel.add(exportPrefs, "0, " + row+", L, C");
		
		layout.insertRow(++row, PREFERRED);
		panel.add(importPrefs, "0, " + row+", L, C");

        layout.insertRow(++row, PREFERRED);
        panel.add(dissButton, "0, " + row+", L, C");

		layout.insertRow(++row, PREFERRED);
		panel.add(langLabel, "0, " + row+", L, C");

		layout.insertRow(++row, PREFERRED);
		panel.add(langBox, "0, " + row+", L, C");

		return panel;
	}
	
	private void refreshDeviceBox() {
		
		final String defaultStr = "Default"; //NON-NLS
		String preferredDevice = NoteFilterSequencerWrapper.prefs.get(NoteFilterSequencerWrapper.prefMIDISelect, null);
		deviceBox.removeAllItems();
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
        deviceBox.setSelectedItem(Objects.requireNonNullElse(preferredDevice, defaultStr));
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
			return UIText.get("maestro.options.first.flute");
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
			if (originalSource != null && !originalSource.getSongTitle().isEmpty())
				return originalSource.getSongTitle();

			return UIText.get("maestro.options.example.title");
		}

		@Override
		public String getComposer() {
			if (originalSource != null && !originalSource.getComposer().isEmpty())
				return originalSource.getComposer();

			return UIText.get("maestro.options.example.composer");
		}

		@Override
		public String getTranscriber() {
			if (originalSource != null && !originalSource.getTranscriber().isEmpty())
				return originalSource.getTranscriber();

			return UIText.get("maestro.options.your.name.here");
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

			return new File(Util.getLotroMusicPath(true), "band/examplesong"+Util.ABC_FILE_EXTENSION);
		}

		@Override
		public String getPartName(AbcPartMetadataSource abcPart) {
			return null;
		}

		@Override
		public String getGenre() {
			return UIText.get("maestro.options.folk.rock");
		}

		@Override
		public String getMood() {
			return UIText.get("maestro.options.sad.groovy");
		}

		@Override
		public String getPartSetup() {
			return "N: TS  1,   4";
		}

		@Override
		public int getActivePartCount() {
			if (originalSource != null && originalSource.getActivePartCount() > 0 && originalSource.getActivePartCount() < 10) {
				// Less than 10 only, so users can see effect of zero-padding
				return originalSource.getActivePartCount();
			}
			return 5;
		}

		@Override
		public String getBadgerTitle() {
			return "N: Title: " + getComposer() + " - " + getSongTitle();
		}

		@Override
		public String getSourceFilename() {
			if (originalSource != null && !Util.emptyIfNull(originalSource.getSourceFilename()).isEmpty() && !AbcSong.errorString.equals(originalSource.getSourceFilename()))
				return originalSource.getSourceFilename();
			return UIText.get("maestro.options.example.midi.0", Util.MID_FILE_EXTENSION);
		}
	}
}
