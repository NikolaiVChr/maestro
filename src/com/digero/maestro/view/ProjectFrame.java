package com.digero.maestro.view;

import static java.awt.event.InputEvent.ALT_DOWN_MASK;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.xml.transform.TransformerException;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.StringCleaner;
import com.digero.common.icons.IconLoader;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiStandard;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.midi.TimeSignature;
import com.digero.common.midi.VolumeTransceiver;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.FileFilterDropListener;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.ParseException;
import com.digero.common.util.Util;
import com.digero.common.view.AboutDialog;
import com.digero.common.view.AudioExportManager;
import com.digero.common.view.BarNumberLabel;
import com.digero.common.view.ColorTable;
import com.digero.common.view.SongPositionLabel;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.AbcConversionException;
import com.digero.maestro.abc.AbcExporter;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;
import com.digero.maestro.abc.ExportFilenameTemplate;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.abc.PartNameTemplate;
import com.digero.maestro.abc.PolyphonyHistogram;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.util.FileResolver;
import com.digero.maestro.util.RecentlyOpenedList;
import com.digero.maestro.util.XmlUtil;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class ProjectFrame extends JFrame implements TableLayoutConstants, ICompileConstants {
	private static final int HGAP = 4;
	private static final int VGAP = 4;
	private static final double[] LAYOUT_COLS = new double[] { 180, FILL };
	private static final double[] LAYOUT_ROWS = new double[] { FILL };
	private static TableLayout tableLayout = new TableLayout(LAYOUT_COLS, LAYOUT_ROWS);

	private Preferences prefs = Preferences.userNodeForPackage(MaestroMain.class);

	private AbcSong abcSong;
	private boolean abcSongModified = false;

	private boolean allowOverwriteSaveFile = false;
	private boolean allowOverwriteExportFile = false;
	private NoteFilterSequencerWrapper sequencer;
	private VolumeTransceiver volumeTransceiver;
	private LotroSequencerWrapper abcSequencer;
	private VolumeTransceiver abcVolumeTransceiver;
	private PartAutoNumberer partAutoNumberer;
	private PartNameTemplate partNameTemplate;
	private ExportFilenameTemplate exportFilenameTemplate;
	private InstrNameSettings instrNameSettings;
	private SaveAndExportSettings saveSettings;
	private MiscSettings miscSettings;

	private JPanel content;
	private JTextField songTitleField;
	private JTextField composerField;
	private JTextField transcriberField;
	private JTextField genreField;
	private JTextField moodField;
	private TableLayout songInfoLayout;
	private JPanel songInfoPanel;
	private JLabel genreLabel = new JLabel("G:");
	private JLabel moodLabel = new JLabel("M:");
	private PrefsDocumentListener transcriberFieldListener;
	private JSpinner transposeSpinner;
	private JSpinner tempoSpinner;
	private JButton resetTempoButton;
	private JFormattedTextField keySignatureField;
	private JFormattedTextField timeSignatureField;
	private JCheckBox tripletCheckBox;
	private JCheckBox mixCheckBox;
	private JCheckBox prioCheckBox;
	private JButton exportButton;
	private JLabel exportSuccessfulLabel;
	private Timer exportLabelHideTimer;
	private JMenu openRecentMenu;
	private JMenuItem saveMenuItem;
	private JMenuItem saveAsMenuItem;
	private JMenuItem exportMenuItem;
	private JMenuItem exportAsMenuItem;
	private JMenuItem saveExpandedMidiMenuItem;
	private JMenu exportAudioMenu;
	private JMenuItem exportMp3MenuItem;
	private JMenuItem exportWavMenuItem;
	private JMenuItem chooseMidiFileMenuItem;
	private JMenuItem reloadMidiFileMenuItem;
	private JMenuItem closeProject;
	
	private RecentlyOpenedList recentlyOpenedList;

	private JPanel partsListPanel;
	private PartsList partsList;
	private JButton newPartButton;
	private JButton deletePartButton;
	private JButton delayButton;
	private JButton numerateButton;
	private JButton maxButton;

	private JPanel settingsPanel;

	private PartPanel partPanel;

	private JButton tuneEditorButton;
	private JCheckBox hideEditsCheckbox;
	static boolean abcPreviewMode = false;
	private JToggleButton abcModeRadioButton;
	private JToggleButton midiModeRadioButton;
	private JButton playButton;
	private JButton stopButton;
	private JSlider volumeSlider;
	private JSlider panSlider;
	private SongPositionLabel midiPositionLabel;
	private SongPositionLabel abcPositionLabel;
	private BarNumberLabel midiBarLabel;
	private BarNumberLabel abcBarLabel;

	private JPanel midiPartsAndControls;

	private Icon playIcon;
	private Icon playIconDisabled;
	private Icon pauseIcon;
	private Icon pauseIconDisabled;
	private Icon abcPlayIcon;
	private Icon abcPlayIconDisabled;
	private Icon abcPauseIcon;
	private Icon abcPauseIconDisabled;
	private Icon stopIcon;
	private Icon stopIconDisabled;

	private long abcPreviewStartTick = 0;
	private float abcPreviewTempoFactor = 1.0f;// deprecated
	private boolean echoingPosition = false;

	private MainSequencerListener mainSequencerListener;
	private AbcSequencerListener abcSequencerListener;
	private boolean failedToLoadLotroInstruments = false;
	private JButton zoomButton;
	private JButton noteButton;
	private JLabel noteCountLabel;
	private JLabel peakLabel;
	private int maxNoteCount = 0;
	private int maxNoteCountTotal = 0;
	private boolean midiResolved = false;
	
	private AudioExportManager audioExporter;

	
	
	/*
	 * private static Color BRIGHT_RED = new Color(255, 0, 0); private static Color ORANGE = new Color(235, 150, 64);
	 * private static Color BLACK = new Color(0, 0, 0);
	 */

	public ProjectFrame() {
		super(MaestroMain.APP_NAME);
		if ("32".equals(System.getProperty("sun.arch.data.model"))) {
			JOptionPane.showMessageDialog(null,
					"You are running with 32 bit Java.\nPlease start with 64 bit Java instead,\nto ensure Maestro do not run out of memory.\n",
					"32 bit detected", JOptionPane.ERROR_MESSAGE);
			System.err.println(
					"You are running with 32 bit Java.\nPlease start with 64 bit Java instead.\n Find Configure Java program in Start menu and\n configure it to start the 64 bit per default.\n\n");
			// System.exit(1);
			// return;
		}
		setMinimumSize(new Dimension(512, 384));
		Util.initWinBounds(this, prefs.node("window"), 800, 600);

		ToolTipManager.sharedInstance().setDismissDelay(8000);

		disableSpaceFocus();

		String welcomeMessage = formatInfoMessage("Hello Maestro",
				"Drag and drop a MIDI or ABC file to open it.\n" + "Or use File > Open.");

		partAutoNumberer = new PartAutoNumberer(prefs.node("partAutoNumberer"));
		partNameTemplate = new PartNameTemplate(prefs.node("partNameTemplate"));
		exportFilenameTemplate = new ExportFilenameTemplate(prefs.node("exportFilenameTemplate"));
		instrNameSettings = new InstrNameSettings(prefs.node("instrNameSettings"));
		saveSettings = new SaveAndExportSettings(prefs.node("saveAndExportSettings"));
		miscSettings = new MiscSettings(prefs.node("miscSettings"),
				true /*
						 * Fallback if miscSettings is empty. Maestro 2.5.0.115 and earlier save misc settings in
						 * saveAndExportSettings
						 */);

		checkVolumeTransceiver();

		try {
			sequencer = new NoteFilterSequencerWrapper();
			if (volumeTransceiver != null)
				sequencer.addTransceiver(volumeTransceiver);

			abcSequencer = new LotroSequencerWrapper();
			if (abcVolumeTransceiver != null)
				abcSequencer.addTransceiver(abcVolumeTransceiver);

			if (LotroSequencerWrapper.getLoadLotroSynthError() != null) {
				welcomeMessage = formatErrorMessage("Could not load LOTRO instrument sounds",
						"ABC Preview will use standard MIDI instruments instead\n"
								+ "(drums do not sound good in this mode).\n\n" + "Error details:\n"
								+ LotroSequencerWrapper.getLoadLotroSynthError());
				failedToLoadLotroInstruments = true;
			}
			PolyphonyHistogram.setSequencer(abcSequencer);
		} catch (MidiUnavailableException e) {
			JOptionPane
					.showMessageDialog(
							null, "Failed to initialize MIDI sequencer.\nThe program will now exit.\n\n"
									+ "Error details:\n" + e.getMessage(),
							"Failed to initialize MIDI sequencer", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
			return;
		}

		// SWING stuff starts here

		loadIcons();

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (closeSong()) {
					setVisible(false);
					dispose();
					System.exit(0);
				}
			}
		});

		playIcon = IconLoader.getImageIcon("play_blue.png");
		playIconDisabled = IconLoader.getDisabledIcon("play_blue.png");
		pauseIcon = IconLoader.getImageIcon("pause_blue.png");
		pauseIconDisabled = IconLoader.getDisabledIcon("pause_blue.png");
		abcPlayIcon = IconLoader.getImageIcon("play_yellow.png");
		abcPlayIconDisabled = IconLoader.getDisabledIcon("play_yellow.png");
		abcPauseIcon = IconLoader.getImageIcon("pause.png");
		abcPauseIconDisabled = IconLoader.getDisabledIcon("pause.png");
		stopIcon = IconLoader.getImageIcon("stop.png");
		stopIconDisabled = IconLoader.getDisabledIcon("stop.png");

		// TableLayout tableLayout = new TableLayout(LAYOUT_COLS, LAYOUT_ROWS);
		tableLayout.setHGap(HGAP);
		tableLayout.setVGap(VGAP);

		content = new JPanel(tableLayout, false);
		setContentPane(content);

		generateSongInfoPanel();

		generateSongPartsPanel();

		generateExportSettingsPanel();

		generateMidiPartsAndControlsPanel();

		if (!SHOW_TEMPO_SPINNER)
			tempoSpinner.setEnabled(false);
		if (!SHOW_METER_TEXTBOX)
			timeSignatureField.setEnabled(false);
		if (!SHOW_KEY_FIELD)
			keySignatureField.setEnabled(false);

		add(generateTopLevelSplitPane(), "0, 0, 1, 0");

		final FileFilterDropListener dropListener = new FileFilterDropListener(false, "mid", "midi", "kar", "abc",
				"txt", AbcSong.MSX_FILE_EXTENSION_NO_DOT);
		dropListener.addActionListener(e -> {
			final File file = dropListener.getDroppedFile();
			SwingUtilities.invokeLater(() -> openFile(file));
		});
		new DropTarget(this, dropListener);

		mainSequencerListener = new MainSequencerListener();
		sequencer.addChangeListener(mainSequencerListener);

		abcSequencerListener = new AbcSequencerListener();
		abcSequencer.addChangeListener(abcSequencerListener);
		
		audioExporter = new AudioExportManager(this, MaestroMain.APP_NAME + MaestroMain.APP_VERSION, prefs);

		initMenu();
		onSaveAndExportSettingsChanged();
		partPanel.showInfoMessage(welcomeMessage);
		updateButtons(false);//must be false since we are not in AWT thread now.

		// Add support for using spacebar for pause/play.
		ActionListener spaceBarListener = e -> {
			if (!sequencer.isLoaded()) {
				return;
			}
			updateSequencer();
			// Attempt to fix a bug where spacebar will later affect focused components
			disableSpaceFocus();
		};
		this.getRootPane().registerKeyboardAction(spaceBarListener, KeyStroke.getKeyStroke(' '),
				JComponent.WHEN_IN_FOCUSED_WINDOW);

		// Add a listener to remove focus from current component when somewhere else is
		// clicked.
		MouseAdapter listenForFocus = new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		};
		addMouseListener(listenForFocus);

	}

	private void generateSongInfoPanel() {
		songTitleField = new JTextField();
		songTitleField.setToolTipText("Song Title");
		songTitleField.getDocument().addDocumentListener(new SimpleDocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				if (abcSong != null)
					abcSong.setTitle(songTitleField.getText());
			}
		});

		composerField = new JTextField();
		composerField.setToolTipText("Song Composer");
		composerField.getDocument().addDocumentListener(new SimpleDocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				if (abcSong != null)
					abcSong.setComposer(composerField.getText());
			}
		});

		transcriberField = new JTextField(prefs.get("transcriber", ""));
		transcriberField.setToolTipText("Song Transcriber (your name)");
		transcriberFieldListener = new PrefsDocumentListener(prefs, "transcriber");
		transcriberField.getDocument().addDocumentListener(transcriberFieldListener);
		transcriberField.getDocument().addDocumentListener(new SimpleDocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				if (abcSong != null)
					abcSong.setTranscriber(transcriberField.getText());
			}
		});

		genreField = new JTextField();
		genreField.setToolTipText("Song Genre(s)");
		genreField.getDocument().addDocumentListener(new SimpleDocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				if (abcSong != null)
					abcSong.setGenre(genreField.getText());
			}
		});

		moodField = new JTextField();
		moodField.setToolTipText("Song Mood(s)");
		moodField.getDocument().addDocumentListener(new SimpleDocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				if (abcSong != null)
					abcSong.setMood(moodField.getText());
			}
		});

		if (miscSettings.showBadger) {
			songInfoLayout = new TableLayout(//
					new double[] { PREFERRED, FILL }, //
					new double[] { PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED });
		} else {
			songInfoLayout = new TableLayout(//
					new double[] { PREFERRED, FILL }, //
					new double[] { PREFERRED, PREFERRED, PREFERRED });
		}
		songInfoLayout.setHGap(HGAP);
		songInfoLayout.setVGap(VGAP);

		songInfoPanel = new JPanel(songInfoLayout);
		int row = 0;
		songInfoPanel.add(new JLabel("T:"), "0, " + row);
		songInfoPanel.add(songTitleField, "1, " + row);
		row++;
		songInfoPanel.add(new JLabel("C:"), "0, " + row);
		songInfoPanel.add(composerField, "1, " + row);
		row++;
		songInfoPanel.add(new JLabel("Z:"), "0, " + row);
		songInfoPanel.add(transcriberField, "1, " + row);
		row++;
		songInfoPanel.add(genreLabel, "0, " + row);
		songInfoPanel.add(genreField, "1, " + row);
		row++;
		songInfoPanel.add(moodLabel, "0, " + row);
		songInfoPanel.add(moodField, "1, " + row);
		songInfoPanel.setBorder(BorderFactory.createTitledBorder("Song Info"));
	}

	private void generateSongPartsPanel() {
		newPartButton = new JButton("New Part");
		newPartButton.addActionListener(e -> {
			if (abcSong != null)
				abcSong.createNewPart();
		});

		deletePartButton = new JButton("Delete");
		deletePartButton.addActionListener(e -> {
			if (abcSong != null) {
				if (abcSong.getParts().size() == 1) {
					// When deleting last past, make sure a new part is replacing it, so something
					// is selected
					AbcPart deleteMe = partsList.getSelectedPart();
					abcSong.createNewPart();
					abcSong.deletePart(deleteMe);
				} else if (abcSong.getParts().size() > 1) {
					abcSong.deletePart(partsList.getSelectedPart());
				}
			}
		});

		partsList = new PartsList(abcSequencer);
		partsList.addListSelectionListener(e -> {
			AbcPart abcPart = partsList.getSelectedPart();
			sequencer.getFilter().onAbcPartChanged(abcPart != null);
			abcSequencer.getFilter().onAbcPartChanged(abcPart != null);
			partPanel.setAbcPart(abcPart, false);
			if (abcPart != null) {
				updateButtons(false);
			} else {
				updateDelayButton();
				if (partsList.getModel().getSize() > 0) {
					// If ctrl-clicking to deselect this will ensure something is selected
					partsList.selectPart(0);
				}
			}
		});

		/**
		 * Wrap the part list in a panel that forces the list to the top. Fixes a swing bug where clicking after the end
		 * of the list will select the last element.
		 */
		JPanel partListWrapperPanel = new JPanel(new BorderLayout());
		partListWrapperPanel.add(partsList, BorderLayout.NORTH);
		partListWrapperPanel.setBackground(partsList.getBackground());

		// Remove focus from text boxes if area under parts is clicked
		partListWrapperPanel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		});
		JScrollPane partsListScrollPane = new JScrollPane(partListWrapperPanel, VERTICAL_SCROLLBAR_ALWAYS,
				HORIZONTAL_SCROLLBAR_AS_NEEDED);
		Dimension sz = partsListScrollPane.getMinimumSize();
		sz.width = PartsListItem.getProtoDimension().width;
		partsListScrollPane.setPreferredSize(sz);

		delayButton = new JButton("Delay Part");
		delayButton.addActionListener(e -> {
			if (partsList.getSelectedPart() != null) {
				DelayDialog.show(ProjectFrame.this, partsList.getSelectedPart());
			}
		});
		delayButton.setToolTipText("Open a small dialog to edit delay on part.");

		numerateButton = new JButton("Numerate");
		numerateButton.addActionListener(e -> {
			if (abcSong != null)
				abcSong.assignNumbersToSimilarPartTypes();
		});
		numerateButton.setToolTipText("Auto assign numbers to identical instrument part titles.");

		maxButton = new JButton("Max part notes");
		maxButton.addActionListener(e -> {
			if (partsList.getSelectedPart() != null) {
				MaxDialog.show(ProjectFrame.this, partsList.getSelectedPart());
			}
		});
		maxButton.setToolTipText("Open a small dialog to edit a parts max notes.");
		
		JPanel partsButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, HGAP, VGAP));
		partsButtonPanel.add(newPartButton);
		partsButtonPanel.add(deletePartButton);

		partsListPanel = new JPanel(new BorderLayout(HGAP, VGAP));
		partsListPanel.setBorder(BorderFactory.createTitledBorder("Song Parts"));
		partsListPanel.add(partsButtonPanel, BorderLayout.NORTH);
		partsListPanel.add(partsListScrollPane, BorderLayout.CENTER);

		GridLayout delayGrid = new GridLayout(2, 1);
		JPanel delayPanel = new JPanel(delayGrid);
		delayPanel.add(delayButton);
		delayPanel.add(numerateButton);
		delayPanel.add(maxButton);
		partsListPanel.add(delayPanel, BorderLayout.SOUTH);
	}

	private void generateExportSettingsPanel() {
		transposeSpinner = new JSpinner(new SpinnerNumberModel(0, -48, 48, 1));
		transposeSpinner
				.setToolTipText("<html>Transpose the entire song by semitones.<br>" + "12 semitones = 1 octave</html>");
		transposeSpinner.addChangeListener(e -> {
			if (abcSong != null)
				abcSong.setTranspose(getTranspose());
		});

		tempoSpinner = new JSpinner(new SpinnerNumberModel(MidiConstants.DEFAULT_TEMPO_BPM /* value */, 8 /* min */,
				960 /* max */, 1 /* step */));
		tempoSpinner.setToolTipText("<html>Tempo in beats per minute.<br><br>"
				+ "This number represents the <b>Main Tempo</b>, which is the tempo that covers<br>"
				+ "the largest portion of the song. If parts of the song play at a different tempo,<br>"
				+ "they will all be adjusted proportionally.</html>");
		tempoSpinner.addChangeListener(e -> {
			if (abcSong != null) {
				abcSong.setTempoBPM((Integer) tempoSpinner.getValue());

				abcSequencer.setTempoFactor(abcSong.getTempoFactor());

				//if (abcSequencer.isRunning()) {
					//float delta = abcPreviewTempoFactor / abcSequencer.getTempoFactor();
					//if (Math.max(delta, 1 / delta) > 1.5f)
						refreshPreviewSequence(false);
				//}
			} else {
				abcSequencer.setTempoFactor(1.0f);
			}
		});

		resetTempoButton = new JButton("Reset");
		resetTempoButton.setMargin(new Insets(2, 8, 2, 8));
		resetTempoButton.setToolTipText("Set the tempo back to the source file's tempo");
		resetTempoButton.addActionListener(e -> {
			if (abcSong == null) {
				tempoSpinner.setValue(MidiConstants.DEFAULT_TEMPO_BPM);
			} else {
				float tempoFactor = abcSequencer.getTempoFactor();
				tempoSpinner.setValue(abcSong.getSequenceInfo().getPrimaryTempoBPM());
				if (tempoFactor != 1.0f)
					refreshPreviewSequence(false);
			}
			tempoSpinner.requestFocus();
		});

		timeSignatureField = new MyFormattedTextField(TimeSignature.FOUR_FOUR, 5);
		timeSignatureField.setToolTipText("<html>Adjust the time signature of the ABC file.<br><br>"
				+ "This mainly affects the display only, but can affect long notes slightly.<br>"
				+ "Examples: 4/4, 3/4, 3/8, 2/2, 2/4, 6/8</html>");
		timeSignatureField.addPropertyChangeListener("value", evt -> {
			if (abcSong != null)
				abcSong.setTimeSignature((TimeSignature) timeSignatureField.getValue());

			//if (abcSequencer.isRunning()) {
				// Breaking up of long notes can depend on time signature for bar lines.
				refreshPreviewSequence(false);
			//}
		});
		timeSignatureField.addActionListener(e -> {
			// This is for when pressing enter on an illegal time signature
			// To update the UI back to the meter of last legal from AbcSong.
			// This is ran after propertychange above. (hopefully always)
			// TODO: This ugly hack could be done better
			if (abcSong != null) {
				if (!abcSong.getTimeSignature().toString().equals(timeSignatureField.getText())) {
					timeSignatureField.setValue(abcSong.getTimeSignature());
				}
			}
		});

		keySignatureField = new MyFormattedTextField(KeySignature.C_MAJOR, 5);
		keySignatureField.setToolTipText("<html>Adjust the key signature of the ABC file. "
				+ "This only affects the display, not the sound of the exported file.<br>"
				+ "Examples: C maj, Eb maj, F# min</html>");
		if (SHOW_KEY_FIELD) {
			keySignatureField.addPropertyChangeListener("value", evt -> {
				if (abcSong != null)
					abcSong.setKeySignature((KeySignature) keySignatureField.getValue());

			});
		}

		tripletCheckBox = new JCheckBox("Triplets/swing rhythm");
		tripletCheckBox.setToolTipText("<html>Tweak the timing to allow for triplets or a swing rhythm.<br><br>"
				+ "This can cause short/fast notes to incorrectly be detected as triplets.<br>"
				+ "Leave it unchecked unless the song has triplets or a swing rhythm.</html>");
		tripletCheckBox.addActionListener(e -> {
			if (abcSong != null)
				abcSong.setTripletTiming(tripletCheckBox.isSelected());

			//if (abcSequencer.isRunning())
				refreshPreviewSequence(false);
		});

		mixCheckBox = new JCheckBox("Mix Timings");
		mixCheckBox.setToolTipText("<html>Allow Maestro to detect which notes<br>"
				+ "that differs from the above triplet/swing setting.<br><br>"
				+ "It is done per part, so some notes in a parts might export as swing/tuplets<br>"
				+ "while other parts at same time export even notes.</html>");
		mixCheckBox.addActionListener(e -> {
			if (abcSong != null)
				abcSong.setMixTiming(mixCheckBox.isSelected());

			//if (abcSequencer.isRunning())
				refreshPreviewSequence(false);
		});

		prioCheckBox = new JCheckBox("Combine Priorities");
		prioCheckBox.setToolTipText("<html>This allow to set track priority for Mix Timings.<br><br>"
				+ "Checkboxes will appear when combining tracks,<br>"
				+ "those enabled will prioritize the timings of those" + "tracks over non-prioritized tracks.</html>");
		prioCheckBox.addActionListener(e -> {
			if (abcSong != null)
				abcSong.setPriorityActive(prioCheckBox.isSelected());

			//if (abcSequencer.isRunning())
				refreshPreviewSequence(false);
		});
		
		exportSuccessfulLabel = new JLabel("Exported");
		exportSuccessfulLabel.setIcon(IconLoader.getImageIcon("check_16.png"));
		exportSuccessfulLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
		exportSuccessfulLabel.setVisible(false);

		exportButton = new JButton(); // Label set in onSaveAndExportSettingsChanged()
		exportButton.setToolTipText("<html><b>Export ABC</b><br>(Ctrl+E)</html>");
		exportButton.setIcon(IconLoader.getImageIcon("abcfile_32.png"));
		exportButton.setDisabledIcon(IconLoader.getDisabledIcon("abcfile_32.png"));
		exportButton.setHorizontalAlignment(SwingConstants.LEFT);
		exportButton.getModel().addChangeListener(new ChangeListener() {
			private boolean pressed = false;

			@Override
			public void stateChanged(ChangeEvent e) {
				if (exportButton.getModel().isPressed() != pressed) {
					pressed = exportButton.getModel().isPressed();
					if (pressed)
						exportSuccessfulLabel.setVisible(false);
				}
			}
		});
		exportButton.addActionListener(e -> exportAbc());

		// Add everything to panel
		TableLayout settingsLayout = new TableLayout(//
				new double[] { PREFERRED, PREFERRED, FILL }, //
				new double[] {});
		settingsLayout.setVGap(VGAP);
		settingsLayout.setHGap(HGAP);

		settingsPanel = new JPanel(settingsLayout);
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Export Settings"));
		int row = 0;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(new JLabel("Transpose:"), "0, " + row);
		settingsPanel.add(transposeSpinner, "1, " + row);
		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(new JLabel("Main Tempo:"), "0, " + row);
		settingsPanel.add(tempoSpinner, "1, " + row);
		settingsPanel.add(resetTempoButton, "2, " + row + ", L, F");
		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(new JLabel("Meter:"), "0, " + row);
		settingsPanel.add(timeSignatureField, "1, " + row + ", 2, " + row + ", L, F");
		if (SHOW_KEY_FIELD) {
			row++;
			settingsLayout.insertRow(row, PREFERRED);
			settingsPanel.add(new JLabel("Key:"), "0, " + row);
			settingsPanel.add(keySignatureField, "1, " + row + ", 2, " + row + ", L, F");
		}
		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(tripletCheckBox, "0, " + row + ", 2, " + row + ", L, C");
		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(mixCheckBox, "0, " + row + ", 2, " + row + ", L, C");
		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(prioCheckBox, "0, " + row + ", 2, " + row + ", C, C");
		//row++;
		//settingsLayout.insertRow(row, PREFERRED);
		//settingsPanel.add(zeroDropdown, "0, " + row + ", 2, " + row + ", L, C");
		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(exportSuccessfulLabel, "0, " + row + ", 2, " + row + ", F, F");
		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(exportButton, "0, " + row + ", 2, " + row + ", F, F");
	}

	private void generateMidiPartsAndControlsPanel() {
		partPanel = new PartPanel(sequencer, partAutoNumberer, abcSequencer, miscSettings.showMaxPolyphony);
		partPanel.addSettingsActionListener(e -> doSettingsDialog(SettingsDialog.NUMBERING_TAB));

		tuneEditorButton = new JButton();
		tuneEditorButton.setText("Tune-editor");
		tuneEditorButton
				.setToolTipText("<html><b> Tune Editor </b><br> Edit the tempo or key in specific sections </html>");
		tuneEditorButton.addActionListener(e -> TuneEditor.show(ProjectFrame.this, abcSong));

		hideEditsCheckbox = new JCheckBox();
		hideEditsCheckbox.setText("Hide Edits");
		hideEditsCheckbox
				.setToolTipText("<html>Hide edits on the tracks</html>");
		hideEditsCheckbox.addActionListener(e -> abcSong.setHideEdits(hideEditsCheckbox.isSelected()));
		
		final Insets playControlButtonMargin = new Insets(5, 20, 5, 20);

		playButton = new JButton(playIcon);
		playButton.setDisabledIcon(playIconDisabled);
		playButton.setMargin(playControlButtonMargin);
		playButton.addActionListener(e -> updateSequencer());

		stopButton = new JButton(stopIcon);
		stopButton.setDisabledIcon(stopIconDisabled);
		stopButton.setToolTipText("Stop");
		stopButton.setMargin(playControlButtonMargin);
		stopButton.addActionListener(e -> {
			abcSequencer.stop();
			sequencer.stop();
			abcSequencer.reset(false);
			sequencer.reset(false);
			maxNoteCountTotal = 0;
			maxNoteCount = 0;
			updateNoteCountLabel();
		});

		ActionListener modeButtonListener = e -> {
			updatePreviewMode(abcModeRadioButton.isSelected());
			if (partPanel != null) {
				partPanel.repaint();
			}
		};

		midiModeRadioButton = new JRadioButton("Original");
		midiModeRadioButton.addActionListener(modeButtonListener);
		midiModeRadioButton.setMargin(new Insets(1, 5, 1, 5));

		abcModeRadioButton = new JRadioButton("ABC Preview");
		abcModeRadioButton.addActionListener(modeButtonListener);
		abcModeRadioButton.setMargin(new Insets(1, 5, 1, 5));

		ButtonGroup modeButtonGroup = new ButtonGroup();
		modeButtonGroup.add(abcModeRadioButton);
		modeButtonGroup.add(midiModeRadioButton);

		midiModeRadioButton.setSelected(true);
		abcPreviewMode = abcModeRadioButton.isSelected();

		volumeSlider = new JSlider(0, MidiConstants.MAX_VOLUME, getVolume());
		volumeSlider.setFocusable(false);
		volumeSlider.addChangeListener(e -> {
			setVolume(volumeSlider.getValue());
		});

		panSlider = new JSlider(0, 100, getPan());
		panSlider.setFocusable(false);
		panSlider.addChangeListener(e -> {
			setPan(panSlider.getValue());
		});

		midiPositionLabel = new SongPositionLabel(sequencer);

		abcPositionLabel = new SongPositionLabel(abcSequencer, true /* adjustForTempo */);
		abcPositionLabel.setVisible(!midiPositionLabel.isVisible());

		midiBarLabel = new BarNumberLabel(sequencer, null);
		midiBarLabel.setToolTipText("Original Bar number");

		abcBarLabel = new BarNumberLabel(abcSequencer, null);
		abcBarLabel.setToolTipText("ABC Preview Bar number");
		abcBarLabel.setVisible(!midiBarLabel.isVisible());

		noteButton = new JButton("Note");
		noteButton.addActionListener(e -> partPanel.noteToggle());
		noteButton.setToolTipText("<html>Show notepad where custom notes can be entered.<br>"
				+ "Will be saved in msx project file.</html>");

		zoomButton = new JButton("Zoom");
		zoomButton.addActionListener(e -> partPanel.zoom());
		
		noteCountLabel = new JLabel();
//		noteCountLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
//		noteCountLabel.setBorder(new EmptyBorder(0, 0, 0, 20));// top,left,bottom,right
		noteCountLabel.setToolTipText("<html>Number of simultanious notes<br>" + "that is playing.<br>"
				+ "Use as rough (as it for tech reasons typically overestimates)<br>"
				+ "guide to estimate how much of lotro max<br>" + "polyphony the song will consume.<br>"
				+ "Stopped notes that are in release phase also counts.</html>");
		peakLabel = new JLabel();
		peakLabel.setToolTipText("<html>Number of simultanious notes<br>" + "that is playing.<br>"
				+ "Use as rough (as it for tech reasons typically overestimates)<br>"
				+ "guide to estimate how much of lotro max<br>" + "polyphony the song will consume.<br>"
				+ "Stopped notes that are in release phase also counts.</html>");
		
		JPanel playControlPanel = new JPanel(new MigLayout("fillx, hidemode 3, wrap 9",
				"[][][][][][][grow -1][grow -1]"));
		playControlPanel.add(tuneEditorButton);
		playControlPanel.add(midiModeRadioButton);
		playControlPanel.add(playButton, "spany 2, right");
		playControlPanel.add(stopButton, "spany 2");
		playControlPanel.add(new JLabel("Volume:"), "right");
		playControlPanel.add(volumeSlider);
		playControlPanel.add(noteCountLabel, "right, hidemode 0");
		playControlPanel.add(peakLabel, "left, hidemode 0");
		playControlPanel.add(midiPositionLabel);
		playControlPanel.add(abcPositionLabel, "wrap");
		
		playControlPanel.add(hideEditsCheckbox);
		playControlPanel.add(abcModeRadioButton);
		playControlPanel.add(new JLabel("Stereo:"), "right");
		playControlPanel.add(panSlider);
		playControlPanel.add(zoomButton, "right");
		playControlPanel.add(noteButton);
		playControlPanel.add(midiBarLabel);
		playControlPanel.add(abcBarLabel);

		midiPartsAndControls = new JPanel(new BorderLayout(HGAP, VGAP));
		midiPartsAndControls.add(partPanel, BorderLayout.CENTER);
		midiPartsAndControls.add(playControlPanel, BorderLayout.SOUTH);
		midiPartsAndControls.setBorder(BorderFactory.createTitledBorder("Part Settings"));
	}

	private void updateSequencer() {
		SequencerWrapper curSequencer = abcPreviewMode ? abcSequencer : sequencer;

		boolean running = !curSequencer.isRunning();

		if (abcPreviewMode) {
			if (!refreshPreviewSequence(true) && running)
				running = false;
		}

		curSequencer.setRunning(running);
		updateButtons(false);
	}

	private JSplitPane generateTopLevelSplitPane() {
		JPanel abcPartsAndSettings = new JPanel(new BorderLayout(HGAP, VGAP));
		abcPartsAndSettings.add(songInfoPanel, BorderLayout.NORTH);
		JPanel partsListAndColorizer = new JPanel(new BorderLayout(HGAP, VGAP));
		partsListAndColorizer.add(partsListPanel, BorderLayout.CENTER);
		if (SHOW_COLORIZER)
			partsListAndColorizer.add(new Colorizer(partPanel), BorderLayout.SOUTH);
		abcPartsAndSettings.add(partsListAndColorizer, BorderLayout.CENTER);
		abcPartsAndSettings.add(settingsPanel, BorderLayout.SOUTH);

		int splitPanePos = prefs.getInt("splitPanePos", -1);

		JSplitPane topLevelSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, abcPartsAndSettings,
				midiPartsAndControls);
		topLevelSplitPane.setBorder(BorderFactory.createEmptyBorder());
		topLevelSplitPane.setContinuousLayout(true);
		topLevelSplitPane.setFocusable(false);
		if (splitPanePos != -1) {
			topLevelSplitPane.setDividerLocation(splitPanePos);
		}
		topLevelSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
			prefs.putInt("splitPanePos", (Integer) e.getNewValue());
		});
		return topLevelSplitPane;
	}

	private void loadIcons() {
		try {
			List<Image> icons = new ArrayList<>();
			icons.add(ImageIO.read(IconLoader.class.getResourceAsStream("maestro_16.png")));
			icons.add(ImageIO.read(IconLoader.class.getResourceAsStream("maestro_32.png")));
			setIconImages(icons);
		} catch (Exception ex) {
			// Ignore
			ex.printStackTrace();
		}
	}

	private void checkVolumeTransceiver() {
		volumeTransceiver = new VolumeTransceiver();
		volumeTransceiver.setVolume(prefs.getInt("volumizer", MidiConstants.MAX_VOLUME));

		abcVolumeTransceiver = new VolumeTransceiver();
		abcVolumeTransceiver.setVolume(volumeTransceiver.getVolume());
	}

	private void disableSpaceFocus() {
		InputMap im = (InputMap) UIManager.get("Button.focusInputMap");
		if (im != null) {
			im.put(KeyStroke.getKeyStroke("pressed SPACE"), "none");
			im.put(KeyStroke.getKeyStroke("released SPACE"), "none");
		}

		im = (InputMap) UIManager.get("CheckBox.focusInputMap");
		if (im != null) {
			im.put(KeyStroke.getKeyStroke("pressed SPACE"), "none");
			im.put(KeyStroke.getKeyStroke("released SPACE"), "none");
		}
	}

	private static void discardObject(IDiscardable object) {
		if (object != null)
			object.discard();
	}

	@Override
	public void dispose() {
		hideEditsCheckbox.setSelected(false);
		if (abcSong != null) {
			abcSong.getParts().getListModel().removeListDataListener(partsListListener);
		}

		discardObject(sequencer);
		discardObject(abcSequencer);
		discardObject(abcSong);
		discardObject(midiPositionLabel);
		discardObject(abcPositionLabel);
		discardObject(midiBarLabel);
		discardObject(abcBarLabel);

		partPanel.setNote("");
		partPanel.noteVisible(false);

		super.dispose();
	}

	private void initMenu() {
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu fileMenu = menuBar.add(new JMenu(" File "));
		fileMenu.setMnemonic('F');

		JMenuItem openItem = fileMenu.add(new JMenuItem("Open file..."));
		openItem.setMnemonic('O');
		openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, CTRL_DOWN_MASK));
		openItem.addActionListener(new ActionListener() {
			JFileChooser openFileChooser;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (openFileChooser == null) {
					openFileChooser = new JFileChooser(prefs.get("openFileChooser.path", null));
					openFileChooser.setMultiSelectionEnabled(false);
					openFileChooser.setFileFilter(
							new ExtensionFileFilter("MIDI, ABC, and " + AbcSong.MSX_FILE_DESCRIPTION_PLURAL, "mid",
									"midi", "kar", "abc", "txt", AbcSong.MSX_FILE_EXTENSION_NO_DOT));
				}

				int result = openFileChooser.showOpenDialog(ProjectFrame.this);
				if (result == JFileChooser.APPROVE_OPTION) {
					openFile(openFileChooser.getSelectedFile());
					prefs.put("openFileChooser.path", openFileChooser.getCurrentDirectory().getAbsolutePath());
				}
			}
		});
		
		recentlyOpenedList = new RecentlyOpenedList(prefs.node("recentlyOpened"));
		
		openRecentMenu = new JMenu("Open Recent Projects");
		fileMenu.add(openRecentMenu);
		
		updateOpenRecentMenu();

		fileMenu.addSeparator();

		saveMenuItem = fileMenu.add(new JMenuItem("Save " + AbcSong.MSX_FILE_DESCRIPTION));
		saveMenuItem.setIcon(IconLoader.getImageIcon("msxfile_16.png"));
		saveMenuItem.setDisabledIcon(IconLoader.getDisabledIcon("msxfile_16.png"));
		saveMenuItem.setMnemonic('S');
		saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_DOWN_MASK));
		saveMenuItem.addActionListener(e -> save());

		saveAsMenuItem = fileMenu.add(new JMenuItem("Save " + AbcSong.MSX_FILE_DESCRIPTION + " As..."));
		saveAsMenuItem.setMnemonic('A');
		saveAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_DOWN_MASK | SHIFT_DOWN_MASK));
		saveAsMenuItem.addActionListener(e -> saveAs());

		fileMenu.addSeparator();

		exportMenuItem = fileMenu.add(new JMenuItem("Export ABC"));
		exportMenuItem.setIcon(IconLoader.getImageIcon("abcfile_16.png"));
		exportMenuItem.setDisabledIcon(IconLoader.getDisabledIcon("abcfile_16.png"));
		exportMenuItem.setMnemonic('E');
		exportMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, CTRL_DOWN_MASK));
		exportMenuItem.addActionListener(e -> exportAbc());

		exportAsMenuItem = fileMenu.add(new JMenuItem("Export ABC As..."));
		exportAsMenuItem.setMnemonic('p');
		exportAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, CTRL_DOWN_MASK | SHIFT_DOWN_MASK));
		exportAsMenuItem.addActionListener(e -> exportAbcAs());

		fileMenu.addSeparator();

		saveExpandedMidiMenuItem = fileMenu.add(new JMenuItem("Export Expanded MIDI..."));
		saveExpandedMidiMenuItem.addActionListener(e -> expandMidi());

		fileMenu.addSeparator();
		
		exportAudioMenu = new JMenu("Export Audio");
		
		fileMenu.add(exportAudioMenu);
		
		exportMp3MenuItem = exportAudioMenu.add(new JMenuItem("Export MP3 File..."));
		exportMp3MenuItem.addActionListener(e -> {
			if (!abcSequencer.isLoaded() || abcSong == null || audioExporter.isExporting()) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			audioExporter.exportMp3Builtin(abcSequencer, getAbcExportFile(), abcSong.getTitle(), abcSong.getComposer());
		});

		exportWavMenuItem = exportAudioMenu.add(new JMenuItem("Export WAV file..."));
		exportWavMenuItem.addActionListener(e -> {
			if (!abcSequencer.isLoaded() || abcSong == null || audioExporter.isExporting()) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			audioExporter.exportWav(abcSequencer, getAbcExportFile());
		});
		
		fileMenu.addSeparator();
		
		chooseMidiFileMenuItem = fileMenu.add(new JMenuItem("Change MIDI file..."));
		chooseMidiFileMenuItem.addActionListener(e -> {
			if (abcSong == null || abcSong.getSourceFile() == null || abcSong.getSaveFile() == null) {
				return; // should be an invalid state, item is disabled if no msx file
			}
			
			int result = JOptionPane.showConfirmDialog(ProjectFrame.this, "If this doesn't work, your project will remain unaffected. For best results, pick a file similar to the current midi file of the project. Would you like to continue?", "Proceed?",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION)
				return;
			
			JFileChooser openMidiChooser = new JFileChooser(abcSong.getSourceFile().getAbsoluteFile().getParent());
			openMidiChooser.setMultiSelectionEnabled(false);
			openMidiChooser.setFileFilter(
					new ExtensionFileFilter("MIDI and ABC files", "mid",
							"midi", "kar", "abc", "txt"));

			result = openMidiChooser.showOpenDialog(ProjectFrame.this);
			if (result != JFileChooser.APPROVE_OPTION) {
				return;
			}
			
			reloadWithNewSource(openMidiChooser.getSelectedFile());
		});
		
		reloadMidiFileMenuItem = fileMenu.add(new JMenuItem("Reload MIDI file"));
		reloadMidiFileMenuItem.setMnemonic(KeyEvent.VK_R);
		reloadMidiFileMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, CTRL_DOWN_MASK));
		reloadMidiFileMenuItem.addActionListener(e -> {
			if (abcSong == null || abcSong.getSaveFile() == null) {
				return; // should be an invalid state, item is disabled if no msx file
			}
			File sourceFile = abcSong.getSourceFile();
			reloadWithNewSource(sourceFile);
		});
		
		fileMenu.addSeparator();

		closeProject = fileMenu.add(new JMenuItem("Close Project"));
		closeProject.addActionListener(e -> closeSong());

		JMenuItem exitItem = fileMenu.add(new JMenuItem("Exit"));
		exitItem.setMnemonic('x');
		exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, ALT_DOWN_MASK));
		exitItem.addActionListener(e -> {
			if (closeSong()) {
				setVisible(false);
				dispose();
			}
		});

		JMenu toolsMenu = menuBar.add(new JMenu(" Tools "));
		toolsMenu.setMnemonic('T');

		JMenuItem settingsItem = toolsMenu.add(new JMenuItem("Options..."));
		settingsItem.setIcon(IconLoader.getImageIcon("gear_16.png"));
		settingsItem.setDisabledIcon(IconLoader.getDisabledIcon("gear_16.png"));
		settingsItem.setMnemonic('O');
		settingsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, CTRL_DOWN_MASK));
		settingsItem.addActionListener(e -> doSettingsDialog());

		toolsMenu.addSeparator();

		JMenuItem aboutItem = toolsMenu.add(new JMenuItem("About " + MaestroMain.APP_NAME + "..."));
		aboutItem.setMnemonic('A');
		aboutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
		aboutItem.addActionListener(e -> AboutDialog.show(ProjectFrame.this, MaestroMain.APP_NAME,
				MaestroMain.APP_VERSION, MaestroMain.APP_URL, "maestro_64.png"));
	}
	
	private void updateOpenRecentMenu() {
		openRecentMenu.removeAll();
		
		LinkedList<File> recentList = recentlyOpenedList.getList();
		
		if (recentList.isEmpty()) {
			openRecentMenu.setEnabled(false);
			return;
		}
		
		openRecentMenu.setEnabled(true);
		
		for (File file : recentList) {
			JMenuItem item = openRecentMenu.add(new JMenuItem(file.getName()));
			item.setToolTipText(file.getAbsolutePath());
			item.addActionListener(e -> {
				JMenuItem src = (JMenuItem)e.getSource();
				openFile(new File(src.getToolTipText()));
			});
		}
			
		openRecentMenu.addSeparator();
		
		JMenuItem clearMenuItem = openRecentMenu.add(new JMenuItem("Clear List"));
		clearMenuItem.addActionListener(e -> {
			recentlyOpenedList.clearList();
			updateOpenRecentMenu();
		});
	}

	private int currentSettingsDialogTab = 0;

	private void doSettingsDialog() {
		doSettingsDialog(currentSettingsDialogTab);
	}

	private void doSettingsDialog(int tab) {
		boolean showSettingsAgain = false;
		int x = -1;
		int y = -1;
		do {
			showSettingsAgain = false;
			SettingsDialog dialog = new SettingsDialog(ProjectFrame.this, prefs, partAutoNumberer, partNameTemplate,
					exportFilenameTemplate, saveSettings.getCopy(), miscSettings.getCopy(),
					instrNameSettings.getCopy());
			if (x > 0 && y > 0) {
				dialog.setLocation(x, y);
			}
			dialog.setActiveTab(tab);
			dialog.setVisible(true);
			if (dialog.isSuccess()) {
				if (dialog.isNumbererSettingsChanged()) {
					partAutoNumberer.setSettings(dialog.getNumbererSettings());
					partAutoNumberer.renumberAllParts();
				}
				partNameTemplate.setSettings(dialog.getNameTemplateSettings());
				partPanel.settingsChanged();

				exportFilenameTemplate.setSettings(dialog.getExportFilenameTemplateSettings());

				instrNameSettings.copyFrom(dialog.getInstrNameSettings());
				instrNameSettings.saveToPrefs();

				saveSettings.copyFrom(dialog.getSaveAndExportSettings());
				saveSettings.saveToPrefs();

				miscSettings.copyFrom(dialog.getMiscSettings());
				miscSettings.saveToPrefs();
				
				onSaveAndExportSettingsChanged();
			} else if (dialog.isSettingPageReset()) {
				tab = dialog.getResetPageIndex();
				switch (tab) {
				case 0: // part auto numberer
					partAutoNumberer.restoreDefaultSettings();
					partAutoNumberer.renumberAllParts();
					break;
				case 1: // part naming
					partNameTemplate.restoreDefaultSettings();
					partPanel.settingsChanged();
					break;
				case 2: // file naming
					exportFilenameTemplate.restoreDefaultSettings();
					break;
				case 3: // instr naming
					instrNameSettings.restoreDefaults();
					break;
				case 4: // save and export
					saveSettings.restoreDefaults();
					break;
				case 5: // misc
					miscSettings.restoreDefaults();
					break;
				}
				showSettingsAgain = true;
				x = dialog.getLocation().x;
				y = dialog.getLocation().y;
				onSaveAndExportSettingsChanged();
			}
			currentSettingsDialogTab = dialog.getActiveTab();
			dialog.dispose();
		} while (showSettingsAgain);
	}

	private void onSaveAndExportSettingsChanged() {
		if (saveSettings.showExportFileChooser) {
			exportAsMenuItem.setVisible(false);
			exportMenuItem.setText("Export ABC As...");
		} else {
			exportAsMenuItem.setVisible(true);
			exportMenuItem.setText("Export ABC");
		}
		
		updateExportOrExportAsButton();

		if (abcSong != null) {
			abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
			abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
		}

		// if (abcSong != null)
		// abcSong.setShowPruned(saveSettings.showPruned);

		noteCountLabel.setVisible(miscSettings.showMaxPolyphony && false);
		peakLabel.setVisible(miscSettings.showMaxPolyphony && false);
		partPanel.setPolyphony(miscSettings.showMaxPolyphony);
		if (!miscSettings.showMaxPolyphony) {
			maxNoteCount = 0;
			maxNoteCountTotal = 0;
		}
		if (abcSong != null) {
			abcSong.setAllOut(miscSettings.showBadger && miscSettings.allBadger);
			abcSong.setBadger(miscSettings.showBadger);
		}
		updateButtons(false);
	}
	
	private void updateExportOrExportAsButton() {
		String exportText = shouldExportAbcAs() ? "Export ABC As..." : "Export ABC";
		if (!exportButton.getText().equals(exportText)) {
			exportButton.setText(exportText);
			exportButton.repaint();
		}
	}

	public void onVolumeChanged() {
		volumeSlider.setValue(getVolume());
		volumeSlider.repaint();
	}
	
	private void setVolume(int volume) {
		if (volumeTransceiver != null)
			volumeTransceiver.setVolume(volume);
		if (abcVolumeTransceiver != null)
			abcVolumeTransceiver.setVolume(volume);
		prefs.putInt("volumizer", volume);
	}
	
	public int getVolume() {
		if (volumeTransceiver != null)
			return volumeTransceiver.getVolume();
		if (abcVolumeTransceiver != null)
			return abcVolumeTransceiver.getVolume();
		return MidiConstants.MAX_VOLUME;
	}
	
	public void setPan(int pan) {
		if (pan != prefs.getInt("stereoPan", 100)) {
			prefs.putInt("stereoPan", pan);
			SequencerWrapper curSequencer = abcPreviewMode ? abcSequencer : sequencer;

			boolean running = curSequencer.isRunning();
			if (abcPreviewMode && running) {
				// curSequencer.setRunning(false);
				refreshPreviewSequence(true);
				// curSequencer.setRunning(true);
			}
			saveSettings.saveToPrefs();
		}
	}
	
	public int getPan() {
		return prefs.getInt("stereoPan", 100);
	}

	private class MainSequencerListener implements Listener<SequencerEvent> {
		@Override
		public void onEvent(SequencerEvent evt) {
			updateButtons(false);
			if (evt.getProperty() == SequencerProperty.IS_RUNNING) {
				if (sequencer.isRunning())
					abcSequencer.stop();
			} else if (!echoingPosition) {
				try {
					echoingPosition = true;
					if (evt.getProperty() == SequencerProperty.POSITION) {
						abcSequencer.setTickPosition(Util.clamp(sequencer.getTickPosition(), abcPreviewStartTick,
								abcSequencer.getTickLength()));
					} else if (evt.getProperty() == SequencerProperty.DRAG_POSITION) {
						abcSequencer.setDragTick(
								Util.clamp(sequencer.getDragTick(), abcPreviewStartTick, abcSequencer.getTickLength()));
					} else if (evt.getProperty() == SequencerProperty.IS_DRAGGING) {
						abcSequencer.setDragging(sequencer.isDragging());
					}
					maxNoteCountTotal = 0;// Not fool proof for preventing it counts too many notes when skipping
					maxNoteCount = 0;// But better than nothing
				} finally {
					echoingPosition = false;
				}
			}
		}
	}

	private void updateNoteCountLabel() {
		String totalColor = "<font>";
		if (maxNoteCountTotal > 63) {
			totalColor = "<font color=RED>";
		} else if (maxNoteCountTotal > 53) {
			totalColor = "<font color=ORANGE>";
		}
		String maxColor = "<font>";
		if (maxNoteCount > 63) {
			maxColor = "<font color=RED>";
		} else if (maxNoteCount > 53) {
			maxColor = "<font color=ORANGE>";
		}
		
		noteCountLabel.setText("<html><nobr>Notes: " + maxColor + String.format("%03d", maxNoteCount) + "</font></nobr></html>");
		peakLabel.setText("<html><nobr>(Peak: " + totalColor + String.format("%03d", maxNoteCountTotal) + "</font>)</nobr></html>");
	}

	private class AbcSequencerListener implements Listener<SequencerEvent> {
		@Override
		public void onEvent(SequencerEvent evt) {
			updateButtons(false);
			//updateNoteCount();
			if (evt.getProperty() == SequencerProperty.IS_RUNNING) {
				if (abcSequencer.isRunning())
					sequencer.stop();
			} else if (!echoingPosition) {
				try {
					echoingPosition = true;
					if (evt.getProperty() == SequencerProperty.POSITION) {
						sequencer.setTickPosition(
								Util.clamp(abcSequencer.getTickPosition(), 0, sequencer.getTickLength()));
					} else if (evt.getProperty() == SequencerProperty.DRAG_POSITION) {
						sequencer.setDragTick(Util.clamp(abcSequencer.getDragTick(), 0, sequencer.getTickLength()));
					} else if (evt.getProperty() == SequencerProperty.IS_DRAGGING) {
						sequencer.setDragging(abcSequencer.isDragging());
					}
				} finally {
					echoingPosition = false;
				}
			}
		}

		@Deprecated
		private void updateNoteCount() {
			noteCountLabel.setVisible(miscSettings.showMaxPolyphony && false);
			peakLabel.setVisible(miscSettings.showMaxPolyphony && false);
			if (!miscSettings.showMaxPolyphony) {
				return;
			}
			if (midiModeRadioButton.isSelected()) {
				maxNoteCount = 0;
				maxNoteCountTotal = 0;
			} else {
				maxNoteCount = LotroSequencerWrapper.getNoteCount();
				maxNoteCountTotal = Math.max(maxNoteCountTotal, maxNoteCount);
			}
			updateNoteCountLabel();
		}
	}

	private abstract static class SimpleDocumentListener implements DocumentListener {
		@Override
		public void insertUpdate(DocumentEvent e) {
			this.changedUpdate(e);
		}

		@Override
		public void removeUpdate(DocumentEvent e) {
			this.changedUpdate(e);
		}
	}

	private static class PrefsDocumentListener implements DocumentListener {
		private Preferences prefs;
		private String prefName;
		private boolean ignoreChanges = false;

		public PrefsDocumentListener(Preferences prefs, String prefName) {
			this.prefs = prefs;
			this.prefName = prefName;
		}

		public void setIgnoreChanges(boolean ignoringChanges) {
			this.ignoreChanges = ignoringChanges;
		}

		private void updatePrefs(javax.swing.text.Document doc) {
			if (ignoreChanges)
				return;

			String txt;
			try {
				txt = doc.getText(0, doc.getLength());
			} catch (BadLocationException e) {
				txt = "";
			}
			prefs.put(prefName, txt);
		}

		@Override
		public void changedUpdate(DocumentEvent e) {
			updatePrefs(e.getDocument());
		}

		@Override
		public void insertUpdate(DocumentEvent e) {
			updatePrefs(e.getDocument());
		}

		@Override
		public void removeUpdate(DocumentEvent e) {
			updatePrefs(e.getDocument());
		}
	}

	private boolean updateButtonsPending = false;
	private Runnable updateButtonsTask = () -> {
		boolean hasAbcNotes = false;
		if (abcSong != null) {
			for (AbcPart part : abcSong.getParts()) {
				if (part.getEnabledTrackCount() > 0) {
					hasAbcNotes = true;
					break;
				}
			}
		}

		boolean midiLoaded = sequencer.isLoaded();

		SequencerWrapper curSequencer = abcPreviewMode ? abcSequencer : sequencer;
		Icon curPlayIcon = abcPreviewMode ? abcPlayIcon : playIcon;
		Icon curPlayIconDisabled = abcPreviewMode ? abcPlayIconDisabled : playIconDisabled;
		Icon curPauseIcon = abcPreviewMode ? abcPauseIcon : pauseIcon;
		Icon curPauseIconDisabled = abcPreviewMode ? abcPauseIconDisabled : pauseIconDisabled;
		playButton.setIcon(curSequencer.isRunning() ? curPauseIcon : curPlayIcon);
		playButton.setDisabledIcon(curSequencer.isRunning() ? curPauseIconDisabled : curPlayIconDisabled);

		if (!hasAbcNotes) {
			maxNoteCountTotal = 0;
			maxNoteCount = 0;
			updateNoteCountLabel();
			midiModeRadioButton.setSelected(true);
			abcSequencer.setRunning(false);
			updatePreviewMode(false);
		}

		playButton.setEnabled(midiLoaded);
		midiModeRadioButton.setEnabled(midiLoaded || hasAbcNotes);
		abcModeRadioButton.setEnabled(hasAbcNotes);
		stopButton.setEnabled((midiLoaded && (sequencer.isRunning() || sequencer.getPosition() != 0))
				|| (abcSequencer.isLoaded() && (abcSequencer.isRunning() || abcSequencer.getPosition() != 0)));

		newPartButton.setEnabled(abcSong != null);
		deletePartButton.setEnabled(partsList.getSelectedIndex() != -1);
		numerateButton.setEnabled(midiLoaded);
		updateDelayButton();
		exportButton.setEnabled(hasAbcNotes);
		exportMenuItem.setEnabled(hasAbcNotes);
		exportAsMenuItem.setEnabled(hasAbcNotes);
		saveMenuItem.setEnabled(abcSong != null);
		saveAsMenuItem.setEnabled(abcSong != null);
		saveExpandedMidiMenuItem.setEnabled(abcSong != null);
		exportAudioMenu.setEnabled(abcSong != null);
		exportMp3MenuItem.setEnabled(abcSong != null);
		exportWavMenuItem.setEnabled(abcSong != null);
		String errStr = "<html><p style='color:red;'>Must save as an MSX project first</p></html>";
		chooseMidiFileMenuItem.setEnabled(abcSong != null && abcSong.getSaveFile() != null);
		chooseMidiFileMenuItem.setToolTipText(abcSong != null && abcSong.getSaveFile() == null ? errStr : "");
		reloadMidiFileMenuItem.setEnabled(abcSong != null && abcSong.getSaveFile() != null);
		reloadMidiFileMenuItem.setToolTipText(abcSong != null && abcSong.getSaveFile() == null ? errStr : "");
		
		closeProject.setEnabled(midiLoaded);

		songTitleField.setEnabled(midiLoaded);
		composerField.setEnabled(midiLoaded);
		transcriberField.setEnabled(midiLoaded);
		moodField.setEnabled(midiLoaded);
		genreField.setEnabled(midiLoaded);
		if (miscSettings.showBadger) {
			songInfoLayout.setRow(new double[] { PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED });
		} else {
			songInfoLayout.setRow(new double[] { PREFERRED, PREFERRED, PREFERRED });
		}
		songInfoLayout.layoutContainer(songInfoPanel);
		moodField.setVisible(miscSettings.showBadger);
		genreField.setVisible(miscSettings.showBadger);
		moodLabel.setVisible(miscSettings.showBadger);
		genreLabel.setVisible(miscSettings.showBadger);
		transposeSpinner.setEnabled(midiLoaded);
		tempoSpinner.setEnabled(midiLoaded);
		tuneEditorButton.setEnabled(midiLoaded);
		hideEditsCheckbox.setEnabled(midiLoaded);
		if (!midiLoaded) hideEditsCheckbox.setSelected(false);
		if (midiLoaded && (abcSong.tuneBars != null || abcSong.getFirstBar() != null || abcSong.getLastBar() != null)) {
			tuneEditorButton.setForeground(new Color(0.2f, 0.8f, 0.2f));
		} else {
			Color c = UIManager.getColor("Button.foreground");
			tuneEditorButton.setForeground(c);
		}
		resetTempoButton.setEnabled(midiLoaded && abcSong != null && abcSong.getTempoFactor() != 1.0f);
		resetTempoButton.setVisible(resetTempoButton.isEnabled());
		keySignatureField.setEnabled(midiLoaded);
		timeSignatureField.setEnabled(midiLoaded);
		tripletCheckBox.setEnabled(midiLoaded);
		mixCheckBox.setEnabled(midiLoaded);
		prioCheckBox.setEnabled(midiLoaded && mixCheckBox.isSelected());
		zoomButton.setEnabled(midiLoaded);
		noteButton.setEnabled(midiLoaded);
		if (midiLoaded) {
			midiModeRadioButton.setText("Original ("
					+ ((abcSong.getSequenceInfo().standard == MidiStandard.GM && abcSong.getSequenceInfo().hasPorts)
							? MidiStandard.GM_PLUS
							: abcSong.getSequenceInfo().standard)
					+ ")");
		} else {
			midiModeRadioButton.setText("Original");
		}

//		double[] LAYOUT_COLS_DYN = new double[] { partsList.getFixedCellWidth() + 32, FILL };
		double[] LAYOUT_COLS_DYN = new double[] { 300 + 32, FILL };
		tableLayout.setColumn(LAYOUT_COLS_DYN);// This call is attempt of fix for no delete button on MacOS part 2

		String partListTitle = "Song Parts";
		if (abcSong != null) {
			partListTitle = partListTitle + " (Count: " + abcSong.getActivePartCount() + ")";
		}

		partsListPanel.setBorder(BorderFactory.createTitledBorder(partListTitle));

		updateButtonsPending = false;
	};

	public void updateDelayButton() {
		if (partsList.getSelectedIndex() != -1 && partPanel != null && partPanel.getAbcPart() != null
				&& partPanel.getAbcPart().delay != 0) {
			delayButton.setForeground(new Color(0.2f, 0.8f, 0.2f));// green
		} else if (partsList.getSelectedIndex() != -1) {
			Color c = UIManager.getColor("TextField.foreground");
			delayButton.setForeground(c);
		} else {
			// This is needed since when starting to set foreground color manually,
			// it will no longer appear greyed out when disabled automatically.
			delayButton.setForeground(new Color(0.6f, 0.6f, 0.6f));
		}
		delayButton.setEnabled(partsList.getSelectedIndex() != -1);
		
		if (partsList.getSelectedIndex() != -1 && partPanel != null && partPanel.getAbcPart() != null
				&& partPanel.getAbcPart().getNoteMax() != AbcConstants.MAX_CHORD_NOTES) {
			maxButton.setForeground(new Color(0.2f, 0.8f, 0.2f));// green
		} else if (partsList.getSelectedIndex() != -1) {
			Color c = UIManager.getColor("TextField.foreground");
			maxButton.setForeground(c);
		} else {
			// This is needed since when starting to set foreground color manually,
			// it will no longer appear greyed out when disabled automatically.
			maxButton.setForeground(new Color(0.6f, 0.6f, 0.6f));
		}
		maxButton.setEnabled(partsList.getSelectedIndex() != -1);
	}

	private void updateButtons(boolean immediate) {
		if (immediate) {
			updateButtonsTask.run();
		} else if (!updateButtonsPending) {
			updateButtonsPending = true;
			SwingUtilities.invokeLater(updateButtonsTask);
		}
	}

	private boolean updateTitlePending = false;

	private void updateTitle() {
		if (!updateTitlePending) {
			updateTitlePending = true;
			SwingUtilities.invokeLater(() -> {
				updateTitlePending = false;
				String title = MaestroMain.APP_NAME;
				if (abcSong != null) {
					if (abcSong.getSaveFile() != null) {
						title += " - " + abcSong.getSaveFile().getName();
						if (abcSong.getSourceFile() != null)
							title += " [" + abcSong.getSourceFile().getName() + "]";
					} else if (abcSong.getSourceFile() != null) {
						title += " - " + abcSong.getSourceFile().getName();
					}

					if (isAbcSongModified())
						title += "*";
				}
				setTitle(title);
			});
		}
	}

	private Listener<AbcPartEvent> abcPartListener = e -> {
		if (e.getProperty() == AbcPartProperty.TRACK_ENABLED)
			updateButtons(false);

		if (e.getProperty() == AbcPartProperty.TITLE && partPanel != null)
			partPanel.setNewTitle(e.getSource());

		partsList.repaint();

		setAbcSongModified(true);

		if (e.isAbcPreviewRelated()) {
			refreshPreviewSequence(false);
		}

		if (e.isAbcPreviewRelated() && partPanel != null) {
			partPanel.repaint();
		}
		
		updateExportOrExportAsButton();
	};

	private Listener<AbcSongEvent> abcSongListener = e -> {
		if (abcSong == null || abcSong != e.getSource())
			return;

		int idx;

		switch (e.getProperty()) {
		case TITLE:
			if (!songTitleField.getText().equals(abcSong.getTitle())) {
				songTitleField.setText(abcSong.getTitle());
				songTitleField.select(0, 0);
			}
			break;
		case COMPOSER:
			if (!composerField.getText().equals(abcSong.getComposer())) {
				composerField.setText(abcSong.getComposer());
				composerField.select(0, 0);
			}
			break;
		case TRANSCRIBER:
			if (!transcriberField.getText().equals(abcSong.getTranscriber())) {
				transcriberFieldListener.setIgnoreChanges(true);
				transcriberField.setText(abcSong.getTranscriber());
				transcriberField.select(0, 0);
				transcriberFieldListener.setIgnoreChanges(false);
			}
			break;

		case TEMPO_FACTOR:
			if (getTempo() != abcSong.getTempoBPM())
				tempoSpinner.setValue(abcSong.getTempoBPM());
			//if (abcSequencer.isRunning())
				refreshPreviewSequence(false);
			//else if (abcPreviewMode)
			//	refreshPreviewSequence(false);
			break;
		case TRANSPOSE:
			if (getTranspose() != abcSong.getTranspose())
				transposeSpinner.setValue(abcSong.getTranspose());
			break;
		case KEY_SIGNATURE:
			if (SHOW_KEY_FIELD) {
				if (!keySignatureField.getValue().equals(abcSong.getKeySignature()))
					keySignatureField.setValue(abcSong.getKeySignature());
			}
			break;
		case TIME_SIGNATURE:
			if (!timeSignatureField.getValue().equals(abcSong.getTimeSignature()))
				timeSignatureField.setValue(abcSong.getTimeSignature());
			break;
		case TRIPLET_TIMING:
			if (tripletCheckBox.isSelected() != abcSong.isTripletTiming())
				tripletCheckBox.setSelected(abcSong.isTripletTiming());
			maxNoteCountTotal = 0;
			maxNoteCount = 0;
			break;
		case MIX_TIMING:
			if (mixCheckBox.isSelected() != abcSong.isMixTiming())
				mixCheckBox.setSelected(abcSong.isMixTiming());
			maxNoteCountTotal = 0;
			maxNoteCount = 0;
			break;
		case MIX_TIMING_COMBINE_PRIORITIES:
			if (prioCheckBox.isSelected() != abcSong.isPriorityActive())
				prioCheckBox.setSelected(abcSong.isPriorityActive());
			break;
		case PART_ADDED:
			e.getPart().addAbcListener(abcPartListener);

			idx = abcSong.getParts().indexOf(e.getPart());
			partsList.selectPart(idx);
			partsList.ensureIndexIsVisible(idx);
			partsList.repaint();
			updateButtons(false);
			maxNoteCountTotal = 0;
			maxNoteCount = 0;
			break;

		case TUNE_EDIT:
			updateButtons(false);
			if (partsList.getSelectedPart() != null) {
				// We do this to show the tempo panel if tune editor has changed something
				partPanel.setAbcPart(partsList.getSelectedPart(), true);
			}
			if (abcSequencer.isRunning())
				refreshPreviewSequence(false);
			else if (abcPreviewMode)
				refreshPreviewSequence(false);
			break;

		case BEFORE_PART_REMOVED:
			e.getPart().removeAbcListener(abcPartListener);

			idx = abcSong.getParts().indexOf(e.getPart());
			if (idx > 0)
				partsList.selectPart(idx - 1);
			else if (abcSong.getParts().size() > 1) {
				partsList.selectPart(1);
			}

			if (abcSong.getParts().isEmpty()) {
				sequencer.stop();
				partPanel.showInfoMessage(formatInfoMessage("Add a part", "This ABC song has no parts.\n" + //
						"Click the " + newPartButton.getText() + " button to add a new part."));
			}

			if (abcSequencer.isRunning())
				refreshPreviewSequence(false);
			else if (abcPreviewMode)
				refreshPreviewSequence(false);

			partsList.repaint();
			updateButtons(false);
			maxNoteCountTotal = 0;
			maxNoteCount = 0;
			break;

		case PART_LIST_ORDER:
			partsList.selectPart(abcSong.getParts().indexOf(partPanel.getAbcPart()));
			partsList.repaint();
			updateButtons(false);
			break;

		case SKIP_SILENCE_AT_START:
			if (saveSettings.skipSilenceAtStart != abcSong.isSkipSilenceAtStart()) {
				saveSettings.skipSilenceAtStart = abcSong.isSkipSilenceAtStart();
				saveSettings.saveToPrefs();
			}
			break;
		case DELETE_MINIMAL_NOTES:
			if (saveSettings.deleteMinimalNotes != abcSong.isDeleteMinimalNotes()) {
				saveSettings.deleteMinimalNotes = abcSong.isDeleteMinimalNotes();
				saveSettings.saveToPrefs();
			}
			if (abcSequencer.isRunning())
				refreshPreviewSequence(false);
			else if (abcPreviewMode)
				refreshPreviewSequence(false);
			break;
		case GENRE:
			if (!genreField.getText().equals(abcSong.getGenre())) {
				genreField.setText(abcSong.getGenre());
				genreField.select(0, 0);
			}
			break;
		case MOOD:
			if (!moodField.getText().equals(abcSong.getMood())) {
				moodField.setText(abcSong.getMood());
				moodField.select(0, 0);
			}
			break;

		case EXPORT_FILE:
			// Don't care
			break;
		case SONG_CLOSING:
			// Don't care
			break;
		}

		updateExportOrExportAsButton();
		setAbcSongModified(true);
	};

	private ListDataListener partsListListener = new ListDataListener() {
		@Override
		public void intervalAdded(ListDataEvent e) {
			partsList.updateParts();
			updateButtons(false);
		}

		@Override
		public void intervalRemoved(ListDataEvent e) {
			partsList.updateParts();
			updateButtons(false);
		}

		@Override
		public void contentsChanged(ListDataEvent e) {
			partsList.updateParts();
			updateButtons(false);
		}
	};

	private void setAbcSongModified(boolean abcSongModified) {
		if (this.abcSongModified != abcSongModified) {
			this.abcSongModified = abcSongModified;
			updateTitle();
		}
		if (abcSongModified) {
			maxNoteCount = 0;
			maxNoteCountTotal = 0;
		}
	}

	public void setMIDIFileResolved() {
		midiResolved = true;
	}

	private boolean isAbcSongModified() {
		return abcSong != null && (abcSongModified || !partPanel.getNote().equals(abcSong.getNote()));
	}

	public int getTranspose() {
		return (Integer) transposeSpinner.getValue();
	}

	public int getTempo() {
		return (Integer) tempoSpinner.getValue();
	}

	private boolean closeSong() {
		SectionEditor.clearClipboard();
		TrackPanel.clearDrumClipboard();
		sequencer.stop();
		abcSequencer.stop();

		boolean promptSave = isAbcSongModified() && (saveSettings.promptSaveNewSong || abcSong.getSaveFile() != null);
		if (promptSave) {
			String message;
			if (abcSong.getSaveFile() == null)
				message = "Do you want to save this new song?";
			else
				message = "Do you want to save changes to \"" + abcSong.getSaveFile().getName() + "\"?";

			int result = JOptionPane.showConfirmDialog(this, message, "Save Changes", JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE, IconLoader.getImageIcon("msxfile_32.png"));
			if (result == JOptionPane.CANCEL_OPTION)
				return false;

			if (result == JOptionPane.YES_OPTION) {
				if (!save())
					return false;
			}
		}
		
		hideEditsCheckbox.setSelected(false);//best to have this before song is set to null

		if (abcSong != null) {
			abcSong.getParts().getListModel().removeListDataListener(partsListListener);
			abcSong.discard();
			abcSong = null;
		}

		partPanel.setAbcPart(null, false);
		partPanel.setNote("");
		partPanel.noteVisible(false);

		partsList.updateParts();

		allowOverwriteSaveFile = false;
		allowOverwriteExportFile = false;

		sequencer.clearSequence();
		abcSequencer.clearSequence();
		sequencer.reset(true);
		abcSequencer.reset(false);
		abcSequencer.setTempoFactor(1.0f);
		abcPreviewStartTick = 0;

		songTitleField.setText("");
		composerField.setText("");
		genreField.setText("");
		moodField.setText("");
		transposeSpinner.setValue(0);
		tempoSpinner.setValue(MidiConstants.DEFAULT_TEMPO_BPM);
		keySignatureField.setValue(KeySignature.C_MAJOR);
		timeSignatureField.setValue(TimeSignature.FOUR_FOUR);
		tripletCheckBox.setSelected(false);
		mixCheckBox.setSelected(true);
		prioCheckBox.setSelected(false);

		midiBarLabel.setBarNumberCache(null);
		abcBarLabel.setBarNumberCache(null);
		abcBarLabel.setInitialOffsetTick(abcPreviewStartTick);
		abcPositionLabel.setInitialOffsetTick(abcPreviewStartTick);

		setAbcSongModified(false);
		updateButtons(false);
		updateTitle();

		return true;
	}
	
	public void openFile(File file) {
		openFile(file, true);
	}

	public void openFile(File file, boolean updateLastOpenedList) {
		if (!closeSong())
			return;

		maxNoteCountTotal = 0;
		maxNoteCount = 0;

		file = Util.resolveShortcut(file);
		allowOverwriteSaveFile = false;
		allowOverwriteExportFile = false;
		setAbcSongModified(false);

		try {
			abcSong = new AbcSong(file, partAutoNumberer, partNameTemplate, exportFilenameTemplate, instrNameSettings,
					openFileResolver, miscSettings);
			abcSong.setAllOut(miscSettings.showBadger && miscSettings.allBadger);
			abcSong.setBadger(miscSettings.showBadger);
			abcSong.addSongListener(abcSongListener);
			abcSong.addSongListener(partsList.songListener);
			for (AbcPart part : abcSong.getParts()) {
				part.addAbcListener(abcPartListener);
				part.addAbcListener(partsList.partListener);
			}

			songTitleField.setText(abcSong.getTitle());
			songTitleField.select(0, 0);
			composerField.setText(abcSong.getComposer());
			composerField.select(0, 0);
			genreField.setText(abcSong.getGenre());
			genreField.select(0, 0);
			moodField.setText(abcSong.getMood());
			moodField.select(0, 0);

			if (abcSong.isFromAbcFile() || abcSong.isFromXmlFile()) {
				transcriberFieldListener.setIgnoreChanges(true);
				transcriberField.setText(abcSong.getTranscriber());
				transcriberField.select(0, 0);
				transcriberFieldListener.setIgnoreChanges(false);
			} else {
				abcSong.setTranscriber(transcriberField.getText());
			}

			if (abcSong.isFromXmlFile()) {
				String note = abcSong.getNote();
				if (note != null) {
					partPanel.setNote(note);
					if (note.length() > 0) {
						partPanel.noteVisible(true);
					}
				}
			}

			transposeSpinner.setValue(abcSong.getTranspose());
			tempoSpinner.setValue(abcSong.getTempoBPM());
			keySignatureField.setValue(abcSong.getKeySignature());
			timeSignatureField.setValue(abcSong.getTimeSignature());
			tripletCheckBox.setSelected(abcSong.isTripletTiming());
			mixCheckBox.setSelected(abcSong.isMixTiming());
			prioCheckBox.setSelected(abcSong.isPriorityActive());

			SequenceInfo sequenceInfo = abcSong.getSequenceInfo();
			sequencer.setSequence(sequenceInfo.getSequence());

			sequencer.setTickPosition(sequenceInfo.calcFirstNoteTick());
			midiBarLabel.setBarNumberCache(sequenceInfo.getDataCache());

			setPartsListModel();
			abcSong.getParts().getListModel().addListDataListener(partsListListener);

			if (abcSong.isFromXmlFile()) {
				allowOverwriteSaveFile = true;
			}

			if (abcSong.isFromAbcFile() || abcSong.isFromXmlFile()) {
				if (abcSong.getParts().isEmpty()) {
					updateButtons(true);
					abcSong.createNewPart();
				} else {
					partsList.selectPart(0);
					updatePreviewMode(true, true);
					updateButtons(true);
				}
			} else {
				updateButtons(true);
				if (abcSong.getParts().isEmpty()) {
					abcSong.createNewPart();
				}
				sequencer.start();
			}

			abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
			abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
			
			// abcSong.setShowPruned(saveSettings.showPruned);

			setAbcSongModified(midiResolved);
			midiResolved = false;
			updateTitle();
		} catch (SAXParseException e) {
			String message = e.getMessage();
			if (e.getLineNumber() >= 0) {
				message += "\nLine " + e.getLineNumber();
				if (e.getColumnNumber() >= 0)
					message += ", column " + e.getColumnNumber();
			}

			partPanel.showInfoMessage(formatErrorMessage("Could not open " + file.getName(), message));
			midiResolved = false;
		} catch (InvalidMidiDataException | IOException | ParseException | SAXException e) {
			partPanel.showInfoMessage(formatErrorMessage("Could not open " + file.getName(), e.getMessage()));
			midiResolved = false;
		}
		
		// Don't update last opened list when reading tmp msx file for midi reloading
		if (updateLastOpenedList && file.getAbsolutePath().endsWith(AbcSong.MSX_FILE_EXTENSION)) {
			recentlyOpenedList.addOpenedFile(file);
			updateOpenRecentMenu();
		}
	}
	
	private boolean reloadWithNewSource(File newSource) {
		File originalMsx = abcSong.getSaveFile();
		File oldSource = abcSong.getSourceFile();
		boolean modified = abcSongModified;
		File tmpMsx;
		try {
			tmpMsx = File.createTempFile("tmpproj", ".msx");
		} catch (IOException e) {
			return false;
		}
		
		abcSong.setSaveFile(tmpMsx);
		abcSong.setSourceFile(newSource);
		
		if (!finishSave(false)) {
			// failed to save tmp - restore
			abcSong.setSaveFile(originalMsx);
			abcSong.setSourceFile(oldSource);
			return false;
		}
		
		openFile(tmpMsx, false);
		if (abcSong != null) {
			abcSong.setSaveFile(originalMsx);
			setAbcSongModified(newSource != oldSource || modified);	
			updateTitle();
		}
		
		return true;
	}

	private void setPartsListModel() {
		// Not really used as a model anymore since switching to PartsList rather than JList
		partsList.setModel(abcSong.getParts().getListModel());
	}

	/** Used when the MIDI file in a Maestro song project can't be loaded. */
	private FileResolver openFileResolver = new FileResolver() {
		@Override
		public File locateFile(File original, String message) {
			message += "\n\nWould you like to try to locate the file?";
			return resolveHelper(original, message);
		}

		@Override
		public File resolveFile(File original, String message) {
			message += "\n\nWould you like to pick a different file?";
			return resolveHelper(original, message);
		}

		private File resolveHelper(File original, String message) {
			int result = JOptionPane.showConfirmDialog(ProjectFrame.this, message, "Failed to open file",
					JOptionPane.OK_CANCEL_OPTION);

			File alternateFile = null;
			if (result == JOptionPane.OK_OPTION) {
				JFileChooser jfc = new JFileChooser();
				jfc.setFileFilter(new ExtensionFileFilter("MIDI and ABC files", "mid",
						"midi", "kar", "abc", "txt"));
				jfc.setDialogTitle("Open missing MIDI/ABC");
				if (original != null)
					jfc.setSelectedFile(original);

				if (jfc.showOpenDialog(ProjectFrame.this) == JFileChooser.APPROVE_OPTION)
					alternateFile = jfc.getSelectedFile();
			}

			return alternateFile;
		}
	};

	private static String formatInfoMessage(String title, String message) {
		return "<html><h3>" + Util.htmlEscape(title) + "</h3>" + Util.htmlEscape(message).replace("\n", "<br>")
				+ "<h3>&nbsp;</h3></html>";
	}

	private static String formatErrorMessage(String title, String message) {
		return "<html><h3><font color=\"" + ColorTable.PANEL_TEXT_ERROR.getHtml() + "\">" + Util.htmlEscape(title)
				+ "</font></h3>" + Util.htmlEscape(message).replace("\n", "<br>") + "<h3>&nbsp;</h3></html>";
	}

	private void updatePreviewMode(boolean abcPreviewModeNew) {
		SequencerWrapper oldSequencer = abcPreviewMode ? abcSequencer : sequencer;
		updatePreviewMode(abcPreviewModeNew, oldSequencer.isRunning());
	}

	private void updatePreviewMode(boolean newAbcPreviewMode, boolean running) {
		boolean runningNow = abcPreviewMode ? abcSequencer.isRunning() : sequencer.isRunning();

		if (newAbcPreviewMode != abcPreviewMode || runningNow != running) {
			if (running && newAbcPreviewMode) {
				if (!refreshPreviewSequence(true)) {
					running = false;

					SequencerWrapper oldSequencer = abcPreviewMode ? abcSequencer : sequencer;
					oldSequencer.stop();
				}
			} else {
				// for histogram
				refreshPreviewSequence(false);
			}

			midiPositionLabel.setVisible(!newAbcPreviewMode);
			abcPositionLabel.setVisible(newAbcPreviewMode);
			midiBarLabel.setVisible(!newAbcPreviewMode);
			abcBarLabel.setVisible(newAbcPreviewMode);
			midiModeRadioButton.setSelected(!newAbcPreviewMode);
			abcModeRadioButton.setSelected(newAbcPreviewMode);

			SequencerWrapper newSequencer = newAbcPreviewMode ? abcSequencer : sequencer;
			newSequencer.setRunning(running);

			abcPreviewMode = newAbcPreviewMode;

			maxNoteCountTotal = 0;
			maxNoteCount = 0;
			updateNoteCountLabel();

			partPanel.setAbcPreviewMode(abcPreviewMode);
			updateButtons(false);
		}
	}

	private boolean refreshPreviewPending = false;

	private class RefreshPreviewTask implements Runnable {
		@Override
		public void run() {
			if (refreshPreviewPending) {
				if (!refreshPreviewSequence(true))
					abcSequencer.stop();
			}
		}
	}

	private boolean refreshPreviewSequence(boolean immediate) {
		if (!immediate) {
			if (!refreshPreviewPending) {
				refreshPreviewPending = true;
				SwingUtilities.invokeLater(new RefreshPreviewTask());
			}
			return true;
		}

		refreshPreviewPending = false;

		if (abcSong == null || abcSong.getActivePartCount() == 0) {
			abcPreviewStartTick = 0;
			abcPreviewTempoFactor = 1.0f;
			abcSequencer.clearSequence();
			abcSequencer.reset(false);
			abcBarLabel.setBarNumberCache(null);
			abcBarLabel.setInitialOffsetTick(abcPreviewStartTick);
			abcPositionLabel.setInitialOffsetTick(abcPreviewStartTick);
			return false;
		}
		
		try {
			abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
			abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
			// abcSong.setShowPruned(saveSettings.showPruned);
			AbcExporter exporter = abcSong.getAbcExporter();
			exporter.stereoPan = prefs.getInt("stereoPan", 100);
			SequenceInfo previewSequenceInfo = SequenceInfo.fromAbcParts(exporter, !failedToLoadLotroInstruments,
					false);
			
			
			long tick = sequencer.getTickPosition();
			abcPreviewStartTick = exporter.getExportStartTick();
			abcPreviewTempoFactor = abcSequencer.getTempoFactor();
			abcBarLabel.setBarNumberCache(exporter.getTimingInfo());
			abcBarLabel.setInitialOffsetTick(abcPreviewStartTick);
			abcPositionLabel.setInitialOffsetTick(abcPreviewStartTick);

			boolean running = abcSequencer.isRunning();
			if (abcPreviewMode) {
				// Refreshing while playing Original (GS) will cause a GS Reset,
				// which will mess with volume, hence the if statement.
				abcSequencer.reset(false);
			}
			abcSequencer.setSequence(previewSequenceInfo.getSequence());
			abcSequencer.setStartTick(abcPreviewStartTick);// Needed for MP3 and WAV exports.

			if (tick < abcPreviewStartTick)
				tick = abcPreviewStartTick;

			if (tick >= abcSequencer.getTickLength()) {
				tick = 0;
				running = false;
			}

			if (running && sequencer.isRunning())
				sequencer.stop();

			abcSequencer.setTickPosition(tick);
			abcSequencer.setRunning(running);
		} catch (InvalidMidiDataException | AbcConversionException e) {
			sequencer.stop();
			abcSequencer.stop();
			JOptionPane.showMessageDialog(ProjectFrame.this, e.getMessage(), "Error previewing ABC",
					JOptionPane.WARNING_MESSAGE);
			return false;
		}

		return true;
	}

	private void commitAllFields() {
		try {
			abcSong.setNote(partPanel.getNote());
			partPanel.commitAllFields();
			transposeSpinner.commitEdit();
			tempoSpinner.commitEdit();
			timeSignatureField.commitEdit();
			keySignatureField.commitEdit();
		} catch (java.text.ParseException e) {
			// Ignore
		}
	}

	private File doSaveDialog(File defaultFile, File allowOverwriteFile, String extension, FileFilter fileFilter) {
		JFileChooser jfc = new JFileChooser();
		jfc.setFileFilter(fileFilter);
		jfc.setSelectedFile(defaultFile);

		while (true) {
			int result = jfc.showSaveDialog(this);
			if (result != JFileChooser.APPROVE_OPTION || jfc.getSelectedFile() == null)
				return null;

			File selectedFile = jfc.getSelectedFile();
			String fileName = selectedFile.getName();
			int dot = fileName.lastIndexOf('.');
			if (dot <= 0 || !fileName.substring(dot).equalsIgnoreCase(extension)) {
				fileName += extension;
				selectedFile = new File(selectedFile.getParent(), fileName);
			}

			if (selectedFile.exists() && !selectedFile.equals(allowOverwriteFile)) {
				int res = JOptionPane.showConfirmDialog(this,
						"File \"" + fileName + "\" already exists.\n" + "Do you want to replace it?",
						"Confirm Replace File", JOptionPane.YES_NO_CANCEL_OPTION);
				if (res == JOptionPane.CANCEL_OPTION)
					return null;
				if (res != JOptionPane.YES_OPTION)
					continue;
			}

			return selectedFile;
		}
	}
	
	private File getAbcExportFile() {
		File exportFile = abcSong.getExportFile();

		String defaultFolder = Util.getLotroMusicPath(false).getAbsolutePath();
		String folder = prefs.get("exportDialogFolder", defaultFolder);
		if (exportFile != null) // Use previously exported folder if it exists
			folder = exportFile.getAbsoluteFile().getParent();
		if (!new File(folder).exists())
			folder = defaultFolder;

		String fileName = "mySong.abc";

		// Always regenerate setting from pattern export is highest precedent
		if (exportFilenameTemplate.shouldRegenerateFilename()) {
			fileName = exportFilenameTemplate.formatName();
		} else if (exportFile != null) // else use abc filename if exists already
		{
			fileName = exportFile.getName();
		} else if (abcSong.getSaveFile() != null) // else use msx filename if exists already
		{
			fileName = abcSong.getSaveFile().getName();
		} else if (exportFilenameTemplate.isEnabled()) // else use pattern if usage is enabled
		{
			fileName = exportFilenameTemplate.formatName();
		} else if (abcSong.getSourceFile() != null) // else default to source file (midi/abc)
		{
			fileName = abcSong.getSourceFilename();
		}

		int dot = fileName.lastIndexOf('.');
		if (dot > 0)
			fileName = fileName.substring(0, dot);
		else if (dot == 0)
			fileName = "";
		fileName = StringCleaner.cleanForFileName(fileName);
		fileName += ".abc";

		exportFile = new File(folder, fileName);
		
		return exportFile;
	}

	private boolean exportAbcAs() {
		exportSuccessfulLabel.setVisible(false);

		if (abcSong == null) {
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		File exportFile = getAbcExportFile();
		File allowOverwriteFile = allowOverwriteExportFile ? abcSong.getExportFile() : null;

		exportFile = doSaveDialog(exportFile, allowOverwriteFile, ".abc",
				new ExtensionFileFilter("ABC files (*.abc, *.txt)", "abc", "txt"));

		if (exportFile == null) {
			return false;
		}

		prefs.put("exportDialogFolder", exportFile.getAbsoluteFile().getParent());

		abcSong.setExportFile(exportFile);
		allowOverwriteExportFile = true;
		return finishExportAbc();
	}

	private boolean shouldExportAbcAs() {
		boolean regeneratedFilenameIsDifferent =
				abcSong != null && abcSong.getExportFile() != null &&
				exportFilenameTemplate.shouldRegenerateFilename() &&
				!exportFilenameTemplate.formatName().equals(abcSong.getExportFile().getName());

		return saveSettings.showExportFileChooser || !allowOverwriteExportFile || abcSong.getExportFile() == null
				|| !abcSong.getExportFile().exists() || regeneratedFilenameIsDifferent;
	}

	private boolean exportAbc() {
		exportSuccessfulLabel.setVisible(false);
		if (abcSong == null) {
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (shouldExportAbcAs())
			return exportAbcAs();

		return finishExportAbc();
	}

	private boolean finishExportAbc() {
		exportSuccessfulLabel.setVisible(false);
		commitAllFields();

		try {
			StringCleaner.cleanABC = saveSettings.convertABCStringsToBasicAscii;
			abcSong.exportAbc(abcSong.getExportFile());

			SwingUtilities.invokeLater(() -> {
				exportSuccessfulLabel.setText(abcSong.getExportFile().getName());
				exportSuccessfulLabel.setToolTipText("Exported " + abcSong.getExportFile().getName());
				exportSuccessfulLabel.setVisible(true);
				if (exportLabelHideTimer == null) {
					exportLabelHideTimer = new Timer(8000, e -> exportSuccessfulLabel.setVisible(false));
					exportLabelHideTimer.setRepeats(false);
				}
				exportLabelHideTimer.stop();
				exportLabelHideTimer.start();
				onSaveAndExportSettingsChanged();
			});
			return true;
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(this, "Failed to create file!\n" + e.getMessage(), "Failed to create file",
					JOptionPane.ERROR_MESSAGE);
			return false;
		} catch (IOException | AbcConversionException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private boolean saveAs() {
		if (abcSong == null) {
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		File saveFile = abcSong.getSaveFile();
		File allowOverwriteFile = allowOverwriteSaveFile ? saveFile : null;

		String defaultFolder;
		if (abcSong.getExportFile() != null)
			defaultFolder = abcSong.getExportFile().getAbsoluteFile().getParent();
		else
			defaultFolder = Util.getLotroMusicPath(false).getAbsolutePath();

		String folder = prefs.get("saveDialogFolder", defaultFolder);
		if (saveFile != null)
			folder = saveFile.getAbsoluteFile().getParent();
		if (!new File(folder).exists())
			folder = defaultFolder;

		String fileName = "mySong.msx";

		// Always regenerate setting from pattern export is highest precedent
		if (exportFilenameTemplate.shouldRegenerateFilename()) {
			fileName = exportFilenameTemplate.formatName();
		} else if (saveFile != null) // else use MSX file if exists already
		{
			fileName = saveFile.getName();
		} else if (abcSong.getExportFile() != null) // else use ABC filename if exists
		{
			fileName = abcSong.getExportFile().getName();
		} else if (exportFilenameTemplate.isEnabled()) // else use pattern if enabled
		{
			fileName = exportFilenameTemplate.formatName();
		} else if (abcSong.getSourceFile() != null) // else use source (midi/abc) file
		{
			fileName = abcSong.getSourceFilename();
		}

		int dot = fileName.lastIndexOf('.');
		if (dot > 0)
			fileName = fileName.substring(0, dot);
		fileName += AbcSong.MSX_FILE_EXTENSION;

		saveFile = new File(folder, fileName);

		saveFile = doSaveDialog(saveFile, allowOverwriteFile, AbcSong.MSX_FILE_EXTENSION,
				new ExtensionFileFilter(AbcSong.MSX_FILE_DESCRIPTION_PLURAL + " (*" + AbcSong.MSX_FILE_EXTENSION + ")",
						AbcSong.MSX_FILE_EXTENSION_NO_DOT));

		if (saveFile == null)
			return false;

		prefs.put("saveDialogFolder", saveFile.getAbsoluteFile().getParent());
		abcSong.setSaveFile(saveFile);
		allowOverwriteSaveFile = true;
		return finishSave();
	}

	private boolean save() {
		if (abcSong == null) {
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (!allowOverwriteSaveFile || abcSong.getSaveFile() == null || !abcSong.getSaveFile().exists()) {
			return saveAs();
		}

		return finishSave();
	}
	
	private boolean finishSave() {
		return finishSave(true);
	}

	private boolean finishSave(boolean updateRecentlyOpenedFiles) {
		commitAllFields();

		try {
			XmlUtil.saveDocument(abcSong.saveToXml(), abcSong.getSaveFile());
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(this, "Failed to create file!\n" + e.getMessage(), "Failed to create file",
					JOptionPane.ERROR_MESSAGE);

			return false;
		} catch (IOException | TransformerException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		if (updateRecentlyOpenedFiles) {
			recentlyOpenedList.addOpenedFile(abcSong.getSaveFile());
			updateOpenRecentMenu();
		}

		setAbcSongModified(false);
		return true;
	}

	private boolean expandMidi() {
		if (abcSong == null || abcSong.getSourceFile() == null) {
			JOptionPane.showMessageDialog(this, "No midi loaded", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (abcSong.getSequenceInfo().standard == MidiStandard.ABC) {
			JOptionPane.showMessageDialog(this, "Cannot expand ABC song", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (abcSong.getSourceFile().getName().startsWith("expanded_")) {
			JOptionPane.showMessageDialog(this, "This midi has already been expanded", "Error",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}

		File saveFile = null;

		if (saveFile == null) {
			String defaultFolder;

			defaultFolder = Util.getLotroMusicPath(false).getAbsolutePath();

			String folder = prefs.get("saveDialogFolder", defaultFolder);
			if (!new File(folder).exists())
				folder = defaultFolder;

			saveFile = abcSong.getSourceFile();
			String fileName = "expanded_" + saveFile.getName();
			Path path = Paths.get(saveFile.getAbsolutePath());
			String directory = path.getParent().toString();

			int dot = fileName.lastIndexOf('.');
			if (dot > 0)
				fileName = fileName.substring(0, dot);
			fileName += ".mid";

			saveFile = new File(directory, fileName);
		}

		saveFile = doSaveDialog(saveFile, saveFile, ".mid", new ExtensionFileFilter("MIDI songs (*.mid)", "mid"));

		if (saveFile == null)
			return false;

		return finishExpand(saveFile);
	}

	private boolean finishExpand(File saveFile) {
		try {
			Sequence sequence2 = abcSong.getSequenceInfo().split();
			if (sequence2 == null) {
				JOptionPane.showMessageDialog(this, "Something went wrong in the splitting process", "Error",
						JOptionPane.ERROR_MESSAGE);
				return false;
			}
			int[] types = MidiSystem.getMidiFileTypes(sequence2);
			if (types.length != 0) {
				// expandedFile.delete();
				// expandedFile.createNewFile();
				System.out.println("Writing type " + types[types.length - 1] + " expanded midi as '"
						+ saveFile.getAbsolutePath() + "'");
				MidiSystem.write(sequence2, types[types.length - 1], saveFile);
			} else {
				JOptionPane.showMessageDialog(this, "Something went wrong when in midi type handling", "Error",
						JOptionPane.ERROR_MESSAGE);
				return false;
			}
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(this, "Failed to create file!\n" + e.getMessage(), "Failed to create file",
					JOptionPane.ERROR_MESSAGE);

			return false;
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		int result = JOptionPane.showConfirmDialog(this, "Would you also like to load the new expanded midi?",
				"Expanded MIDI", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

		switch (result) {
		case JOptionPane.YES_OPTION:
			openFile(saveFile);
			break;
		case JOptionPane.NO_OPTION:
			break;
		case JOptionPane.CANCEL_OPTION:
			break;
		}

		return true;
	}

	/**
	 * Slight modification to JFormattedTextField to select the contents when it receives focus.
	 */
	private static class MyFormattedTextField extends JFormattedTextField {
		public MyFormattedTextField(Object value, int columns) {
			super(value);
			setColumns(columns);
		}

		@Override
		protected void processFocusEvent(FocusEvent e) {
			super.processFocusEvent(e);
			if (e.getID() == FocusEvent.FOCUS_GAINED)
				selectAll();
		}
	}

	/**
	 * 
	 * Will output what all threads are doing.
	 * 
	 * @param lockedMonitors
	 * @param lockedSynchronizers
	 * @return A string ready to be printed out
	 */
	@SuppressWarnings("unused")
	private static String threadDump(boolean lockedMonitors, boolean lockedSynchronizers) {
		StringBuffer threadDump = new StringBuffer(System.lineSeparator());
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
			threadDump.append(threadInfo.toString());
		}
		return threadDump.toString();
	}
}
