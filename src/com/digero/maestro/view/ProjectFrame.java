package com.digero.maestro.view;

import static java.awt.event.InputEvent.ALT_DOWN_MASK;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS;

import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

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
import javax.swing.SwingWorker;
import javax.xml.transform.TransformerException;

import com.digero.common.util.*;
import com.digero.maestro.midi.SequenceDataCache;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

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
import com.digero.maestro.abc.QuantizedTimingInfo;
import com.digero.maestro.midi.Chord;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.util.FileResolver;
import com.digero.maestro.util.ListModelWrapper;
import com.digero.maestro.util.RecentlyOpenedList;
import com.digero.maestro.util.XmlUtil;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;
import net.miginfocom.swing.MigLayout;

public class ProjectFrame extends JFrame implements TableLayoutConstants, ICompileConstants {
	private static final Logger log = Logger.getLogger("view");

    private boolean uiEnabled = true;
    private boolean sourceChangeEnabled = true;

	private static final int HGAP = 4;
	private static final int VGAP = 4;
	private static final double[] LAYOUT_COLS = new double[] { 180, FILL };
	private static final double[] LAYOUT_ROWS = new double[] { FILL };
	private static final TableLayout tableLayout = new TableLayout(LAYOUT_COLS, LAYOUT_ROWS);

	private final Preferences prefs = Preferences.userNodeForPackage(MaestroMain.class);

	private AbcSong abcSong;
	private boolean abcSongModified = false;

    private PolyphonyHistogram histogram = null;

	private boolean allowOverwriteSaveFile = false;
	private boolean allowOverwriteExportFile = false;
	private NoteFilterSequencerWrapper sequencer;
	private long firstMidiNoteTick = 0;
	private VolumeTransceiver volumeTransceiver;
	private LotroSequencerWrapper abcSequencer;
	private VolumeTransceiver abcVolumeTransceiver;
	private PartAutoNumberer partAutoNumberer;
	private final PartNameTemplate partNameTemplate;
	private final ExportFilenameTemplate exportFilenameTemplate;
	private final InstrNameSettings instrNameSettings;
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
	private final JLabel genreLabel = new JLabel("G:");
	private final JLabel moodLabel = new JLabel("M:");
	private PrefsDocumentListener transcriberFieldListener;
	private JSpinner transposeSpinner;
	private JSpinner tempoSpinner;
	private JButton resetTempoButton;
	private JFormattedTextField keySignatureField;
	private JFormattedTextField timeSignatureField;
    private JComboBox<TimingEnum> timingCombo;

    private JCheckBox tempoOnlyFirstCheckBox;
	private JComboBox<Chord.CalcDynamics> dynaCombo;
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

	private FileFilterDropListener dropListener = null;
	
	private JPanel partsListPanel;
	private PartsList partsList;
	private JButton newPartButton;
	private JButton deletePartButton;
	private JButton sortPartsButton;
	private JButton partEditorButton;
	private JButton numerateButton;
	private PartEditor partEditor;

	private JPanel settingsPanel;

	private ArrangementView arrangementView;

	private JButton tuneEditorButton;
	private JCheckBox hideEditsCheckbox;
	static boolean abcPreviewMode = false;
	private JToggleButton abcModeRadioButton;
	private JToggleButton midiModeRadioButton;
	private JButton playButton;
	private JButton stopButton;
	private JSlider volumeSlider;
	private JSlider stereoSlider;
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
	private JButton sidepanelButton;
	private boolean midiResolved = false;
	
	private AudioExportManager audioExporter;

	private volatile Version latestVer;
	private CompletableFuture<Void> future = null;
	
	private JLabel feedLabel;
	private static volatile String feed = "";
	private static volatile String feedFull = "";
    private PreviewExportWorker previewWorker = null;

    // these properties have in common that they stop UI
    // listeners in doing all their work:
    private boolean fireTransposeListeners = true;
    private boolean fireMeterListeners = true;
    private boolean fireTempoListeners = true;
    private boolean fireDynaListeners = true;
    private JMenuItem openItem;

	public ProjectFrame() {
        super(MaestroMain.APP_NAME + " " + MaestroMain.APP_VERSION);
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

        if (miscSettings.checkForUpdates) checkVersionCompare();

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

        loadControlIcons();

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

        dropListener = new FileFilterDropListener(false, Util.MID_FILE_EXTENSION_NO_DOT,
                Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT, Util.ABC_FILE_EXTENSION_NO_DOT,
                Util.TXT_FILE_EXTENSION_NO_DOT, Util.MSX_FILE_EXTENSION_NO_DOT);
        dropListener.addActionListener(e -> {
            final File file = dropListener.getDroppedFile();
            SwingUtilities.invokeLater(() -> openFile(file));
        });
        new DropTarget(this, dropListener);

        //dropListener.exclude = partsList; // not the cause of the partsList d'n'd flicker

        mainSequencerListener = new MainSequencerListener();
        sequencer.addChangeListener(mainSequencerListener);

        abcSequencerListener = new AbcSequencerListener();
        abcSequencer.addChangeListener(abcSequencerListener);

        audioExporter = new AudioExportManager(this, MaestroMain.APP_NAME + " " + MaestroMain.APP_VERSION, prefs);

        initMenu();
        onSaveAndExportSettingsChanged();
        arrangementView.showInfoMessage(welcomeMessage);
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

        // Add a listener to remove focus from the current component when somewhere else is
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
		composerField.setToolTipText("Song Composer/Artist");
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
                // It's not a big deal, but when programmatically setting
                // the text, removeUpdate and insertUpdate will both
                // call this method, so it gets called twice.
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
		
		sortPartsButton = new JButton("Sort") {
			public Dimension getMaximumSize() {
				return getPreferredSize();
			}
		};
		sortPartsButton.setToolTipText("Enable auto-sort of the parts. To disable just drag and drop them.");
		sortPartsButton.addActionListener(e -> {
			if (abcSong != null) {
				abcSong.autoSortParts();
			}
		});

		partsList = new PartsList(abcSequencer, miscSettings);
		partsList.addListSelectionListener(e -> {
			AbcPart abcPart = partsList.getSelectedPart();
			sequencer.getFilter().onAbcPartChanged(abcPart != null);
			abcSequencer.getFilter().onAbcPartChanged(abcPart != null);
			arrangementView.setAbcPart(abcPart, false);
			if (abcPart != null) {
				updateButtons(false);
			} else {
				updatePartEditorButton();
				if (partsList.getModel().getSize() > 0) {
					// If ctrl-clicking to deselect this will ensure something is selected
					partsList.selectPart(0);
				}
			}
		});
		
		
		
		partEditor = new PartEditor(this, sequencer, miscSettings);

		/*
		 * Wrap the part list in a panel that forces the list to the top. Fixes a swing bug where clicking after the end
		 * of the list will select the last element.
		 */
		/*
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
		*/
		JScrollPane partsListScrollPane = new JScrollPane(partsList, VERTICAL_SCROLLBAR_ALWAYS,
				HORIZONTAL_SCROLLBAR_AS_NEEDED);
		partsList.setScroll(partsListScrollPane);
		// Remove focus from text boxes if area under parts is clicked
		partsList.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				getRootPane().requestFocus();
			}
		});
		
		Dimension sz = partsListScrollPane.getMinimumSize();
		sz.width = PartsListItem.getProtoDimension().width;
		partsListScrollPane.setPreferredSize(sz);

		partEditorButton = new JButton("Part Editor");
		partEditorButton.addActionListener(e -> {
			partEditor.setVisible(!partEditor.isVisible());
		});
		partEditorButton.setToolTipText("Open a small window to edit parts.");

		numerateButton = new JButton("Numerate");
		numerateButton.addActionListener(e -> {
			if (abcSong != null)
				abcSong.assignNumbersToSimilarPartTypes();
		});
		numerateButton.setToolTipText("Auto assign numbers to identical instrument part titles.");

		JPanel partsButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, HGAP, VGAP));
		partsButtonPanel.add(newPartButton);
		partsButtonPanel.add(deletePartButton);
		partsButtonPanel.add(sortPartsButton);

		partsListPanel = new JPanel(new BorderLayout(HGAP, VGAP));
		partsListPanel.setBorder(BorderFactory.createTitledBorder("Song Parts"));
		partsListPanel.add(partsButtonPanel, BorderLayout.NORTH);
		partsListPanel.add(partsListScrollPane, BorderLayout.CENTER);

		GridLayout delayGrid = new GridLayout(1, 2);
		JPanel delayPanel = new JPanel(delayGrid);
		delayPanel.add(partEditorButton);
		delayPanel.add(numerateButton);
		partsListPanel.add(delayPanel, BorderLayout.SOUTH);
	}

	private void generateExportSettingsPanel() {
		transposeSpinner = new JSpinner(new SpinnerNumberModel(0, -48, 48, 1));
		transposeSpinner
				.setToolTipText("<html>Transpose the entire song by semitones.<br>" + "12 semitones = 1 octave</html>");
		transposeSpinner.addChangeListener(e -> {
			if (abcSong != null && fireTransposeListeners)
                abcSong.setTranspose(getTranspose());
            refreshPreviewSequence(false);
		});

		tempoSpinner = new JSpinner(new SpinnerNumberModel(MidiConstants.DEFAULT_TEMPO_BPM /* value */, 8 /* min */,
				960 /* max */, 1 /* step */));
		tempoSpinner.setToolTipText("<html>Tempo in beats per minute.<br><br>"
				+ "This number represents the <b>Main Tempo</b>, which is the tempo that covers<br>"
				+ "the largest portion of the song. If parts of the song play at a different tempo,<br>"
				+ "they will all be adjusted proportionally.</html>");
		tempoSpinner.addChangeListener(e -> {
			if (abcSong != null) {
				if (fireTempoListeners) abcSong.setTempoBPM((Integer) tempoSpinner.getValue());

				abcSequencer.setTempoFactor(abcSong.getTempoFactor());

				refreshPreviewSequence(false);
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
				float tempoFactor = abcSong.getTempoFactor();
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
			if (abcSong != null && fireMeterListeners)
				abcSong.setTimeSignature((TimeSignature) timeSignatureField.getValue());

            // Breaking up of long notes can depend on time signature for bar lines.
            refreshPreviewSequence(false);
		});
		timeSignatureField.addActionListener(e -> {
			// This is for when pressing enter on an illegal time signature
            // This listener will not run when setting value programmatically.
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

        timingCombo = new JComboBox<>(TimingEnum.values());

        timingCombo.addActionListener(e -> {
            TimingEnum enm = ((TimingEnum) Objects.requireNonNull(timingCombo.getSelectedItem()));
            timingCombo.setToolTipText(enm.getTooltip());

            enm.action(abcSong);

            refreshPreviewSequence(false);
        });
		
		dynaCombo = new JComboBox<>(Chord.CalcDynamics.values());
		dynaCombo.setSelectedItem(AbcSong.dynamicsMethodDefault);
		dynaCombo.addItemListener(i -> {
			if (abcSong != null) {
				if (fireDynaListeners) abcSong.dynamicsMethod = (Chord.CalcDynamics) dynaCombo.getSelectedItem();
				refreshPreviewSequence(false);
			}
		});
		dynaCombo.setToolTipText("Volume calculation method for when multiple notes start at same time in a part.\n"
				+ Chord.CalcDynamics.LOUDEST+": Volume of the loudest note.\n"
				+ Chord.CalcDynamics.POWER_RMS_DB+": decibel mean.\n"
				+ Chord.CalcDynamics.POWER_MID_DB+": A bit softer than RMS.\n"
				+ Chord.CalcDynamics.WEIGHTED+": Generally softer than "+Chord.CalcDynamics.POWER_MID_DB+".\n"
				+ Chord.CalcDynamics.SOFTEST+": Volume of the softest note.");

        tempoOnlyFirstCheckBox = new JCheckBox("Only tempo changes from first track");
        tempoOnlyFirstCheckBox.setToolTipText(
                "<html>If the midi only have tempos in first track then this option cannot be changed.<br><br>"
                + "Furthermore if a midi is expanded (from menu) with this option disabled,<br>"
                + "the expanded midi will have all its tempos put into first track.<br><br>"
                + "Changing this option can change the layout of the bar lines,<br>"
                + "so be sure to review the section/tune edits if changing this option.</html>");
        tempoOnlyFirstCheckBox.addActionListener(e -> {
            if (abcSong == null) {
                return;
            }

            if (abcSong.getProjectFile() == null) {
                //return; // should be an invalid state, item is disabled if no msx file
            }

            abcSong.setUsingOldTempos(tempoOnlyFirstCheckBox.isSelected());

            setAbcSongModified(true);
            File sourceFile = abcSong.getSourceFile();
            reloadWithNewSource(sourceFile);
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
        settingsPanel.add(timingCombo, "0, " + row + ", 2, " + row + ", L, C");

		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(dynaCombo, "0, " + row + ", 2, " + row + ", L, C");
        row++;
        settingsLayout.insertRow(row, PREFERRED);
        settingsPanel.add(tempoOnlyFirstCheckBox, "0, " + row + ", 2, " + row + ", L, C");
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
		arrangementView = new ArrangementView(sequencer, partAutoNumberer, abcSequencer, miscSettings.showMaxPolyphony);
		arrangementView.addSettingsActionListener(e -> doSettingsDialog(SettingsDialog.NUMBERING_TAB));

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
		});

		ActionListener modeButtonListener = e -> {
			updatePreviewMode(abcModeRadioButton.isSelected());
			if (arrangementView != null) {
				arrangementView.repaint();
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
        SequencerWrapper.isAbcPreview = abcPreviewMode;

		volumeSlider = new JSlider(0, MidiConstants.MAX_VOLUME, getVolume());
		volumeSlider.setFocusable(false);
		volumeSlider.addChangeListener(e -> {
			setVolume(volumeSlider.getValue());
		});

		stereoSlider = new JSlider(0, 100, getPan());
		stereoSlider.setFocusable(false);
		stereoSlider.addChangeListener(e -> {
			setPan(stereoSlider.getValue());
		});

		midiPositionLabel = new SongPositionLabel(sequencer, "000:00/000:00");

		abcPositionLabel = new SongPositionLabel(abcSequencer, true /* adjustForTempo */, "000:00/000:00");
		abcPositionLabel.setVisible(!midiPositionLabel.isVisible());

		midiBarLabel = new BarNumberLabel(sequencer, null, true, "0000,00/0000");
		midiBarLabel.setToolTipText("Original Bar number");

		abcBarLabel = new BarNumberLabel(abcSequencer, null, false,"0000/0000");
		abcBarLabel.setToolTipText("ABC Preview Bar number");
		abcBarLabel.setVisible(!midiBarLabel.isVisible());

		sidepanelButton = new JButton("");
        if (Themer.isDarkMode()) {
            sidepanelButton.setIcon(IconLoader.getImageIcon("sidepanel_dark.png"));
            sidepanelButton.setDisabledIcon(IconLoader.getDisabledIcon("sidepanel_dark.png"));
        } else {
            sidepanelButton.setIcon(IconLoader.getImageIcon("sidepanel.png"));
            sidepanelButton.setDisabledIcon(IconLoader.getDisabledIcon("sidepanel.png"));
        }
		sidepanelButton.addActionListener(e -> arrangementView.sidepanelToggle());
		sidepanelButton.setToolTipText("<html>Show sidepanel where custom comments can be entered.<br>"
				+ "Notes will be saved in project file.</html>");
				
		feedLabel = new JLabel();
		feedLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				feed("", null);
				showFeed();
			}
		});
		
		playControlPanel = new JPanel(new MigLayout("fillx, hidemode 3, wrap 8, gap 8 4",
				"[][][][][][][grow -1][grow -1]"));
		//playControlPanel.setDoubleBuffered(false);
		playControlPanel.add(tuneEditorButton);
		playControlPanel.add(midiModeRadioButton);
		playControlPanel.add(playButton, "spany 2, alignx right");
		playControlPanel.add(stopButton, "spany 2");
		playControlPanel.add(new JLabel("Volume:"), "alignx right");
		playControlPanel.add(volumeSlider);
		playControlPanel.add(sidepanelButton, "spany 2, center");
		playControlPanel.add(midiPositionLabel);
		playControlPanel.add(abcPositionLabel, "wrap");
		
		playControlPanel.add(hideEditsCheckbox);
		playControlPanel.add(abcModeRadioButton);
		//play
		//stop
		playControlPanel.add(new JLabel("Stereo:"), "alignx right");
		playControlPanel.add(stereoSlider);
		//note
		playControlPanel.add(midiBarLabel);
		playControlPanel.add(abcBarLabel);

		playControlPanel.add(feedLabel, "span 8, center");

		midiPartsAndControls = new JPanel(new BorderLayout(HGAP, VGAP));
		midiPartsAndControls.add(arrangementView, BorderLayout.CENTER);
		midiPartsAndControls.add(playControlPanel, BorderLayout.SOUTH);
		midiPartsAndControls.setBorder(BorderFactory.createTitledBorder("Part Settings"));
	}
	
	/**
	 * Call this from any thread
	 * @param str info to be shown, can be null.
	 * @param str2 tooltip, can be null.
	 */
	public static synchronized void feed(String str, String str2) {
		feed = str;
		feedFull = str2;
	}
	
	/**
	 * Call this from AWT thread only
	 */
	public void showFeed() {
		synchronized(ProjectFrame.class) {
			if (feed == null) {
				feedLabel.setText(null);
			} else {
				String dismiss = feed.isEmpty()?"":"  [click to dismiss]";
				feedLabel.setText(feed + dismiss);
			}
			feedLabel.setToolTipText(feedFull);
			playControlPanel.validate();
		}
	}

	/**
	 * Play/pause button (or spacebar)
	 */
	private void updateSequencer() {
		SequencerWrapper curSequencer = abcPreviewMode ? abcSequencer : sequencer;

		boolean running = !curSequencer.isRunning();

		if (abcPreviewMode) {
			if (!refreshPreviewSequence(true) && running) {
				running = false;
			} else {
				long tick = abcSequencer.getTickPosition();
				if (tick < abcPreviewStartTick)
					tick = abcPreviewStartTick;

				if (tick >= abcSequencer.getTickLength()) {
					tick = 0;
					running = false;
				}
				abcSequencer.setTickPosition(tick);
			}
		} else if (curSequencer.isAtStart()) {
			curSequencer.setTickPosition(firstMidiNoteTick);
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
			partsListAndColorizer.add(new Colorizer(arrangementView), BorderLayout.SOUTH);
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
            // 15, 32, 48 and 256 are the most common used icon sizes that a modern Windows uses
			List<Image> icons = new ArrayList<>();
            BufferedImage icon16 = IconLoader.getImage("maestro_16.png");
            BufferedImage icon32 = IconLoader.getImage("maestro_32.png");
            BufferedImage icon48 = IconLoader.getImage("maestro_48.png");
            BufferedImage icon256 = IconLoader.getImage("maestro_256.png");
			if (icon16 != null) icons.add(icon16);
            if (icon32 != null) icons.add(icon32);
            if (icon48 != null) icons.add(icon48);
            if (icon256 != null) icons.add(icon256);
			setIconImages(icons);
		} catch (Exception ex) {
			// Ignore
			log.log(Level.WARNING, "Error when loading icons", ex);
		}
	}

    private void loadControlIcons() {
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

		arrangementView.setTextnote("");
        arrangementView.setLyrics("");
        arrangementView.setStats("");
		arrangementView.sidepanelVisible(false);

		super.dispose();
	}

	private void initMenu() {
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu fileMenu = menuBar.add(new JMenu(" File "));
		fileMenu.setMnemonic('F');

        openItem = fileMenu.add(new JMenuItem("Open file..."));
		openItem.setMnemonic('O');
		openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, CTRL_DOWN_MASK));
		openItem.addActionListener(new ActionListener() {
			JFileChooser openFileChooser;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (openFileChooser == null) {
					openFileChooser = new JFileChooser(prefs.get("openFileChooser.path", null));
					openFileChooser.setMultiSelectionEnabled(false);
                    openFileChooser.addChoosableFileFilter(
                            new ExtensionFileFilter("MIDI",
                                    Util.MID_FILE_EXTENSION_NO_DOT,
                                    Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT));
                    openFileChooser.addChoosableFileFilter(
                            new ExtensionFileFilter("ABC",
                                    Util.ABC_FILE_EXTENSION_NO_DOT,
                                    Util.TXT_FILE_EXTENSION_NO_DOT));
                    openFileChooser.addChoosableFileFilter(
                            new ExtensionFileFilter("Project",
                                    Util.MSX_FILE_EXTENSION_NO_DOT));
					openFileChooser.setFileFilter(
							new ExtensionFileFilter("MIDI, ABC, and Project",
									Util.MID_FILE_EXTENSION_NO_DOT,
									Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT,
									Util.ABC_FILE_EXTENSION_NO_DOT,
									Util.TXT_FILE_EXTENSION_NO_DOT, Util.MSX_FILE_EXTENSION_NO_DOT));
                    openFileChooser.setAcceptAllFileFilterUsed(false);
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
			refreshPreviewSequence(true);//important, so last edits gets written
			audioExporter.exportWav(abcSequencer, getAbcExportFile());
		});
		
		fileMenu.addSeparator();
		
		chooseMidiFileMenuItem = fileMenu.add(new JMenuItem("Change MIDI file..."));
		chooseMidiFileMenuItem.addActionListener(e -> {
			if (abcSong == null || abcSong.getSourceFile() == null || abcSong.getProjectFile() == null) {
				return; // should be an invalid state, item is disabled if no msx file
			}
			
			int result = JOptionPane.showConfirmDialog(ProjectFrame.this, "If this doesn't work, your project will remain unaffected. For best results, pick a file similar to the current midi file of the project. Would you like to continue?", "Proceed?",
					JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (result != JOptionPane.YES_OPTION)
				return;
			
			JFileChooser openMidiChooser = new JFileChooser(abcSong.getSourceFile().getAbsoluteFile().getParent());
			openMidiChooser.setMultiSelectionEnabled(false);
            openMidiChooser.addChoosableFileFilter(
                    new ExtensionFileFilter("MIDI",
                            Util.MID_FILE_EXTENSION_NO_DOT,
                            Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT));
            openMidiChooser.addChoosableFileFilter(
                    new ExtensionFileFilter("ABC",
                            Util.ABC_FILE_EXTENSION_NO_DOT,
                            Util.TXT_FILE_EXTENSION_NO_DOT));
			openMidiChooser.setFileFilter(
					new ExtensionFileFilter("MIDI and ABC files", Util.MID_FILE_EXTENSION_NO_DOT,
							Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT, Util.ABC_FILE_EXTENSION_NO_DOT,
							Util.TXT_FILE_EXTENSION_NO_DOT));
            openMidiChooser.setAcceptAllFileFilterUsed(false);

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
			if (abcSong == null || abcSong.getProjectFile() == null) {
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
				System.exit(0);
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
		
		JMenuItem helpItem = toolsMenu.add(new JMenuItem("Help (Opens in browser)"));
		helpItem.setMnemonic('H');
		helpItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
		helpItem.addActionListener(e -> {
			Util.openURL(MaestroMain.WIKI_URL, this);
		});
		
		JMenuItem versionItem = toolsMenu.add(new JMenuItem("Check for Updates"));
		versionItem.setMnemonic('V');
		versionItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
		versionItem.addActionListener(e -> {
			checkVersionCompare();
		});
		
		toolsMenu.addSeparator();

		JMenuItem aboutItem = toolsMenu.add(new JMenuItem("About " + MaestroMain.APP_NAME + "..."));
		aboutItem.setMnemonic('A');
		aboutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
		aboutItem.addActionListener(e -> AboutDialog.show(ProjectFrame.this, MaestroMain.APP_NAME,
				MaestroMain.APP_VERSION, MaestroMain.WIKI_URL, "maestro_64.png"));
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
			dialog.setVisible(true, abcSong);
			if (dialog.isSuccess()) {
				if (dialog.isNumbererSettingsChanged()) {
					partAutoNumberer.setSettings(dialog.getNumbererSettings());
					partAutoNumberer.renumberAllParts(abcSong.getParts());
				}
				partNameTemplate.setSettings(dialog.getNameTemplateSettings());
				arrangementView.settingsChanged();

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
					partAutoNumberer.renumberAllParts(abcSong.getParts());
					break;
				case 1: // part naming
					partNameTemplate.restoreDefaultSettings();
					arrangementView.settingsChanged();
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
					if (sequencer != null) sequencer.reset(true);// To repopulate the devices
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

        boolean needRefresh = false;

		if (abcSong != null) {
            if (abcSong.isSkipSilenceAtStart() != saveSettings.skipSilenceAtStart
                    || abcSong.isDeleteMinimalNotes() != saveSettings.deleteMinimalNotes
                    || abcSong.isUseRestsInChords() != saveSettings.useRestsInChords) {
                // we do it here instead of in the song listener,
                // so we don't get nested calls to refresh.
                needRefresh = true;
            }
			abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
			abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
            abcSong.setReducedFilesize(saveSettings.reducedFilesize);
            abcSong.setUseRestsInChords(saveSettings.useRestsInChords);
		}

		// if (abcSong != null)
		// abcSong.setShowPruned(saveSettings.showPruned);

		arrangementView.setPolyphony(miscSettings.showMaxPolyphony);
		if (abcSong != null) {
			abcSong.setBadger(miscSettings.showBadger);
		}

        if (abcSong != null && miscSettings.importLyrics) {
            String lyrics = abcSong.getLyrics();
            if (lyrics.isBlank()) {
                lyrics = "Contains no lyrics";
            }
            arrangementView.setLyrics(lyrics);
        } else {
            arrangementView.setLyrics("Lyrics is disabled in options!");
        }

		String wantedDevice = NoteFilterSequencerWrapper.prefs.get(NoteFilterSequencerWrapper.prefMIDISelect, null);
		if ((NoteFilterSequencerWrapper.deviceInUse != null && !NoteFilterSequencerWrapper.deviceInUse.equals(wantedDevice)) || (NoteFilterSequencerWrapper.deviceInUse == null && wantedDevice != null)) {
			long tick = sequencer.getTickPosition();
			boolean running = sequencer.isRunning();
			sequencer.reset(true);
			sequencer.setTickPosition(tick);
			if (running) sequencer.setRunning(true); 
		}
		updateButtons(false);
        if (needRefresh) refreshPreviewSequence(false);
	}
	
	private void updateExportOrExportAsButton() {
		String exportText = shouldExportAbcAs() ? "Export ABC As..." : "Export ABC";
		if (!exportButton.getText().equals(exportText)) {
			exportButton.setText(exportText);
			exportButton.repaint();
		}
	}

    @Deprecated
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
				if (sequencer.isRunning()) {
					abcSequencer.stop();
				}
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
				} finally {
					echoingPosition = false;
				}
			}
		}
	}

	private class AbcSequencerListener implements Listener<SequencerEvent> {
		@Override
		public void onEvent(SequencerEvent evt) {
			updateButtons(false);
			if (evt.getProperty() == SequencerProperty.IS_RUNNING) {
				if (abcSequencer.isRunning()) {
					sequencer.stop();
				}
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
		private final Preferences prefs;
		private final String prefName;
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
	private final Runnable updateButtonsTask = () -> {
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
			midiModeRadioButton.setSelected(true);
			abcSequencer.setRunning(false);
			//updatePreviewMode(false);
            abcSequencer.clearSequence();
		}

        volumeSlider.setEnabled(uiEnabled);
        stereoSlider.setEnabled(uiEnabled);

		playButton.setEnabled(midiLoaded && uiEnabled);
		midiModeRadioButton.setEnabled((midiLoaded || hasAbcNotes) && uiEnabled);
		abcModeRadioButton.setEnabled(hasAbcNotes && uiEnabled);
		stopButton.setEnabled((midiLoaded && (sequencer.isRunning() || !sequencer.isAtStart()))
				|| (abcSequencer.isLoaded() && (abcSequencer.isRunning() || !abcSequencer.isAtStart())) && uiEnabled);

		newPartButton.setEnabled(abcSong != null && uiEnabled);
		deletePartButton.setEnabled(partsList.getSelectedIndex() != -1 && uiEnabled);
		sortPartsButton.setEnabled(abcSong != null && uiEnabled);
		numerateButton.setEnabled(midiLoaded && uiEnabled);
		updatePartEditorButton();
		exportButton.setEnabled(hasAbcNotes);// so that it keep focus, we keep it enabled during export.
		exportMenuItem.setEnabled(hasAbcNotes && uiEnabled);
		exportAsMenuItem.setEnabled(hasAbcNotes && uiEnabled);
		saveMenuItem.setEnabled(abcSong != null && uiEnabled);
		saveAsMenuItem.setEnabled(abcSong != null && uiEnabled);
		saveExpandedMidiMenuItem.setEnabled(abcSong != null && uiEnabled);
		exportAudioMenu.setEnabled(abcSong != null && uiEnabled);
		exportMp3MenuItem.setEnabled(abcSong != null && uiEnabled);
		exportWavMenuItem.setEnabled(abcSong != null && uiEnabled);
		String errStr = "<html><p style='color:red;'>Must save as an MSX project first</p></html>";
		chooseMidiFileMenuItem.setEnabled(abcSong != null && abcSong.getProjectFile() != null && uiEnabled && sourceChangeEnabled);
		chooseMidiFileMenuItem.setToolTipText(abcSong != null && abcSong.getProjectFile() == null ? errStr : "");
		reloadMidiFileMenuItem.setEnabled(abcSong != null && abcSong.getProjectFile() != null && uiEnabled && sourceChangeEnabled);
		reloadMidiFileMenuItem.setToolTipText(abcSong != null && abcSong.getProjectFile() == null ? errStr : "");
        openRecentMenu.setEnabled(sourceChangeEnabled);
        openItem.setEnabled(sourceChangeEnabled);

		closeProject.setEnabled(midiLoaded && uiEnabled && sourceChangeEnabled);

		songTitleField.setEnabled(midiLoaded && uiEnabled);
		composerField.setEnabled(midiLoaded && uiEnabled);
		transcriberField.setEnabled(midiLoaded && uiEnabled);
		moodField.setEnabled(midiLoaded && uiEnabled);
		genreField.setEnabled(midiLoaded && uiEnabled);
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
		transposeSpinner.setEnabled(midiLoaded && uiEnabled);
		tempoSpinner.setEnabled(midiLoaded && uiEnabled);
		tuneEditorButton.setEnabled(midiLoaded && uiEnabled);
		hideEditsCheckbox.setEnabled(midiLoaded && uiEnabled);
		if (!midiLoaded) hideEditsCheckbox.setSelected(false);
		if (midiLoaded && (abcSong.tuneBars != null || abcSong.getFirstBar() != null || abcSong.getLastBar() != null)) {
			tuneEditorButton.setForeground(ColorTable.CONTROLS_EDITED.get());
		} else {
			Color c = UIManager.getColor("Button.foreground");
			tuneEditorButton.setForeground(c);
		}
		resetTempoButton.setEnabled(midiLoaded && abcSong != null && abcSong.getTempoFactor() != 1.0f && uiEnabled);
		resetTempoButton.setVisible(resetTempoButton.isEnabled());
		keySignatureField.setEnabled(midiLoaded && uiEnabled);
		timeSignatureField.setEnabled(midiLoaded && uiEnabled);
        timingCombo.setEnabled(midiLoaded && uiEnabled);

		dynaCombo.setEnabled(midiLoaded && uiEnabled);
        tempoOnlyFirstCheckBox.setEnabled(abcSong != null && abcSong.getSequenceInfo().getDataCache().isTempoInHigherTracks() && uiEnabled);//  && abcSong.getProjectFile() != null
		sidepanelButton.setEnabled(midiLoaded && uiEnabled);
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

		showFeed();
		
		updateButtonsPending = false;
	};

	public void updatePartEditorButton() {
        Color c = UIManager.getColor("Button.foreground");
        if (abcSong != null) {
            partEditorButton.setForeground(abcSong.isPartEdited() ? ColorTable.CONTROLS_EDITED.get() : c);
        } else {
            tuneEditorButton.setForeground(c);
        }
		partEditorButton.setEnabled(partsList.getSelectedIndex() != -1 && uiEnabled);
	}
	
	void updateButtons(boolean immediate) {
		if (immediate) {
			updateButtonsTask.run();
		} else if (!updateButtonsPending) {
			updateButtonsPending = true;
			SwingUtilities.invokeLater(updateButtonsTask);
		}
	}

	private boolean updateTitlePending = false;

    /**
     * Update title of maestro window
     */
	private void updateTitle() {
		if (!updateTitlePending) {
			updateTitlePending = true;
			SwingUtilities.invokeLater(() -> {
				updateTitlePending = false;
				String title = MaestroMain.APP_NAME + " " + MaestroMain.APP_VERSION;
				if (abcSong != null) {
					if (abcSong.getProjectFile() != null) {
						title += " - " + abcSong.getProjectFile().getName();
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

	private final Listener<AbcPartEvent> abcPartListener = e -> {
        //log.warning(this.getClass().getTypeName()+" AbcPartEvent: "+e.getProperty());

        if (e.getProperty() == AbcPartProperty.EXCLUSION) {
            if (histogram != null) {
                histogram.setDirty();
                compileStats();
            }
            // This is a runtime event only, so we return
            return;
        }

		if (e.getProperty() == AbcPartProperty.TRACK_ENABLED)
			updateButtons(false);

		if (e.getProperty() == AbcPartProperty.TITLE && arrangementView != null)
			arrangementView.setNewTitle(e.getSource());

        if (e.getProperty() == AbcPartProperty.PART_NUMBER_MANUAL)
            partAutoNumberer.renumberAllParts(abcSong.getParts());

		partsList.repaint();
		partEditor.repaint();

		setAbcSongModified(true);

		if (e.isAbcPreviewRelated()) {
            // must be immediate since song.parts can change in subsequent
            // part listeners and generate preview now runs on a different thread
            // update: since it now uses copy of abcsong, non-immediate is ok.
			refreshPreviewSequence(false);
		}

		if (e.isAbcPreviewRelated() && arrangementView != null) {
			arrangementView.repaint();
		}
		
		updateExportOrExportAsButton();
	};

	private final Listener<AbcSongEvent> abcSongListener = e -> {
		if (abcSong == null || abcSong != e.getSource())
			return;

		int idx;
        boolean modified = true;

        //log.warning(this.getClass().getTypeName()+" AbcSongEvent: "+e.getProperty());

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
				setTempo(abcSong.getTempoBPM());

            //not needed as listeners on spinner will refresh
			//refreshPreviewSequence(false);

			break;
		case TRANSPOSE:
			setTranspose(abcSong.getTranspose());
			break;
		case KEY_SIGNATURE:
			if (SHOW_KEY_FIELD) {
				if (!keySignatureField.getValue().equals(abcSong.getKeySignature()))
					keySignatureField.setValue(abcSong.getKeySignature());
			}
			break;
		case TIME_SIGNATURE:
			setMeter(abcSong.getTimeSignature());
			break;
		case ORGANIC:
        case TRIPLET_TIMING:
        case MIX_TIMING:
        case MIX_TIMING_COMBINE_PRIORITIES:
            break;
        case TIMINGS_MULTI:
            // one or more timing settings were change in abc song

            // setting on model dont fire action listener
            timingCombo.getModel().setSelectedItem(TimingEnum.getInstance(abcSong.isOrganic(), abcSong.isOrganic2(), abcSong.isMixTiming(), abcSong.isTripletTiming(), abcSong.isPriorityActive()));

			updateButtons(false);
			break;
		case CALC_DYNAMICS:
			setDyna(abcSong.dynamicsMethod);
			break;
		case PART_ADDED:
			e.getPart().addAbcListener(abcPartListener);

			idx = abcSong.getParts().indexOf(e.getPart());
			partsList.selectPart(idx);
			partsList.ensureIndexIsVisible(idx);
			partsList.repaint();
			partEditor.repaint();
			updateButtons(false);
			compileStats();
			break;
		case BADGER:
			AbcPart ap = partsList.getSelectedPart();
			partsList.updateParts();
			idx = abcSong.getParts().indexOf(ap);
			partsList.selectPart(idx);
			partsList.ensureIndexIsVisible(idx);
			partsList.repaint();
			partEditor.updateParts();
			partEditor.repaint();
			updateButtons(false);
			break;
		case TUNE_EDIT:
			updateButtons(false);
			if (partsList.getSelectedPart() != null) {
				// We do this to show the tempo panel if the tune editor has changed something
				arrangementView.setAbcPart(partsList.getSelectedPart(), true);
			}
            if (abcPreviewMode)
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
				arrangementView.showInfoMessage(formatInfoMessage("Add a part", "This ABC song has no parts.\n" + //
						"Click the " + newPartButton.getText() + " button to add a new part."));
			}

			partsList.repaint();
			updateButtons(false);
			break;
        case AFTER_PART_REMOVED:
            refreshPreviewSequence(false);
            break;
		case PART_LIST_ORDER:
            partsList.updateParts();//important to run before the below code
            partsList.selectPart(abcSong.getParts().indexOf(arrangementView.getAbcPart()));
            // this is important, else after a deletion, the tracklist might be in the wrong state:
            if (partsList.getSelectedPart() != null) {
                // might be null shortly after loading from midi
                arrangementView.setAbcPart(partsList.getSelectedPart(), true);
            }

			partsList.repaint();
			partEditor.repaint();
			updateButtons(false);
			break;

		case SKIP_SILENCE_AT_START:
			if (saveSettings.skipSilenceAtStart != abcSong.isSkipSilenceAtStart()) {
                /*
                 not sure that this is sane
                 skip is not a song property it's a setting
                 it's only in abcSong for convenience
                 I cannot think of a case where setting it on abcsong
                 should propagate to settings.
                 Same for DELETE_MINIMAL_NOTES.
                 So commented out.
                */

				//saveSettings.skipSilenceAtStart = abcSong.isSkipSilenceAtStart();
				//saveSettings.saveToPrefs();
			}
            modified = false;
			break;
		case DELETE_MINIMAL_NOTES:
			if (saveSettings.deleteMinimalNotes != abcSong.isDeleteMinimalNotes()) {
				//saveSettings.deleteMinimalNotes = abcSong.isDeleteMinimalNotes();
				//saveSettings.saveToPrefs();
            }
            modified = false;
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
        case COUNT_IN:
            setAbcSongModified(true);

            //must be true so countin props get set on actual abcSong, not a copy:
            refreshPreviewSequence(true);

            if (abcSong != null) {
                if (abcSong.getCountIn() != null) {
                    abcSequencer.setCountInMicros(abcSong.getCountIn().micros);
                    break;
                }
            }
            abcSequencer.setCountInMicros(0L);
            break;
		case EXPORT_FILE:
			// Don't care
			break;
		case SONG_CLOSING:
			// Don't care
			break;
		case HIDE_EDITS_UPDATE:
			// Don't care
            modified = false;
			break;
		}

		updateExportOrExportAsButton();
		if (modified) setAbcSongModified(true);
	};

	private final ListDataListener partsListListener = new ListDataListener() {
		@Override
		public void intervalAdded(ListDataEvent e) {
			partsList.updateParts();
			partEditor.updateParts();
			updateButtons(false);
		}

		@Override
		public void intervalRemoved(ListDataEvent e) {
			partsList.updateParts();
			partEditor.updateParts();
			updateButtons(false);
		}

		@Override
		public void contentsChanged(ListDataEvent e) {
			partsList.updateParts();
			partEditor.updateParts();
			updateButtons(false);
		}
	};

	private void setAbcSongModified(boolean abcSongModified) {
		if (this.abcSongModified != abcSongModified) {
			this.abcSongModified = abcSongModified;
			updateTitle();
		}
	}

	public void setMIDIFileResolved() {
		midiResolved = true;
	}

	private boolean isAbcSongModified() {
		return abcSong != null && (abcSongModified || !arrangementView.getTextnote().equals(abcSong.getNote()));
	}

	public int getTranspose() {
		return (Integer) transposeSpinner.getValue();
	}

    /**
     * Will not activate the changelistener to set abcSong
     */
    public void setTranspose(int transpose) {
        fireTransposeListeners = false;
        transposeSpinner.setValue(transpose);
        fireTransposeListeners = true;
    }

    /**
     * Will not activate the changelistener to set abcSong
     */
    private void setMeter(TimeSignature ts) {
        fireMeterListeners = false;
        timeSignatureField.setValue(ts);
        fireMeterListeners = true;
    }

    /**
     * Will not activate the changelistener to set abcSong
     */
    private void setTempo(int tempoBPM) {
        fireTempoListeners = false;
        tempoSpinner.setValue(tempoBPM);
        fireTempoListeners = true;
    }

	public int getTempo() {
		return (Integer) tempoSpinner.getValue();
	}

    /**
     * Will not activate the changelistener to set abcSong
     */
    private void setDyna(Chord.CalcDynamics dyna) {
        fireDynaListeners = false;
        dynaCombo.setSelectedItem(dyna);
        fireDynaListeners = true;
    }


	/**
	 * 
	 * @return true if it was closed
	 */
	public boolean closeSong() {
		SectionEditor.clearClipboard();
		TrackPanel.clearDrumClipboard();
		sequencer.stop();
		abcSequencer.stop();

		boolean promptSave = isAbcSongModified() && (saveSettings.promptSaveNewSong || abcSong.getProjectFile() != null);
		if (promptSave) {
			String message;
			if (abcSong.getProjectFile() == null)
				message = "Do you want to save this new song?";
			else
				message = "Do you want to save changes to \"" + abcSong.getProjectFile().getName() + "\"?";

			int result = JOptionPane.showConfirmDialog(this, message, "Save Changes", JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE, IconLoader.getImageIcon("msxfile_32.png"));
			if (result == JOptionPane.CANCEL_OPTION || result == JOptionPane.CLOSED_OPTION)
				return false;

			if (result == JOptionPane.YES_OPTION) {
				if (!save())
					return false;
			}
		}
		
		log.fine("Closing project");
		
		hideEditsCheckbox.setSelected(false);//best to have this before song is set to null

		if (abcSong != null) {
			abcSong.getParts().getListModel().removeListDataListener(partsListListener);
			abcSong.discard();
			abcSong = null;
		}

		arrangementView.setAbcPart(null, false);
		arrangementView.setTextnote("");
        arrangementView.setLyrics("");
        arrangementView.setStats("");
		arrangementView.sidepanelVisible(false);
		arrangementView.unZoom();
		arrangementView.closeAbcSong();
		
		partEditor.setVisible(false);

		partsList.updateParts();
		partEditor.updateParts();

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
        timingCombo.getModel().setSelectedItem(TimingEnum.MIX);
        dynaCombo.setSelectedItem(AbcSong.dynamicsMethodDefault);
        tempoOnlyFirstCheckBox.setSelected(false);
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
	
	private File filetemp = null;
	private File file = null;
	private boolean inCloseFile = false;// In progress of asking user if wanting to save
	private boolean inOpenFile = false;// In progress of either opening file or finding midi.

	public void openFile(File openfile, boolean updateLastOpenedList) {
		// begin system for preventing cascading dialogs
		// As long as the dialog for asking to close open project is there,
		// double-clicking in explorer in windows will change which song eventually gets open.
		// When dialog for finding midi is there, subsequent explorer double clicks are ignored
		// until after midi found or canceled.
		// Not ideal, but works.
		filetemp = openfile;
		if (inCloseFile || inOpenFile) {
			return;
		}
		inCloseFile = true;
		if (!closeSong()) {
			inCloseFile = false;
			return;
		}
		inOpenFile = true;
		inCloseFile = false;
		file = filetemp;
		// end system for preventing cascading dialogs


		file = Util.resolveShortcut(file);
		allowOverwriteSaveFile = false;
		allowOverwriteExportFile = false;
		setAbcSongModified(false);

		log.info("Attempting to open "+file.getName());//dont reveal full path in log files
		try {
			abcSong = new AbcSong(file, partAutoNumberer, partNameTemplate, exportFilenameTemplate, instrNameSettings,
					openFileResolver, miscSettings, saveSettings);
			SequencerWrapper.onlyFirstTrackTempos = abcSong.isUsingOldTempos();
			abcSong.setBadger(miscSettings.showBadger);
			abcSong.addSongListener(abcSongListener);
			abcSong.addSongListener(partsList.songListener);
			abcSong.addSongListener(partEditor.getSongListener());
			for (AbcPart part : abcSong.getParts()) {
				part.addAbcListener(abcPartListener);
				part.addAbcListener(partsList.partListener);
				part.addAbcListener(partEditor.getPartListener());
			}

			songTitleField.setText(abcSong.getTitle());
			songTitleField.select(0, 0);
			composerField.setText(abcSong.getComposer());
			composerField.select(0, 0);
			genreField.setText(abcSong.getGenre());
			genreField.select(0, 0);
			moodField.setText(abcSong.getMood());
			moodField.select(0, 0);
			setDyna(abcSong.dynamicsMethod);

			if (abcSong.isFromAbcFile() || abcSong.isFromXmlFile()) {
				transcriberFieldListener.setIgnoreChanges(true);
				transcriberField.setText(abcSong.getTranscriber());
				transcriberField.select(0, 0);
				transcriberFieldListener.setIgnoreChanges(false);
			} else {
				abcSong.setTranscriber(transcriberField.getText());
			}

            arrangementView.sidepanelTab("Notes");

			if (miscSettings.importLyrics) {
                String lyrics = abcSong.getLyrics();
                if (!lyrics.isEmpty()) {
                    arrangementView.sidepanelVisible(true);
                    arrangementView.sidepanelTab("Lyrics");
                }
                if (lyrics.isBlank()) {
                    lyrics = "Contains no lyrics";
                }
                arrangementView.setLyrics(lyrics);
            } else {
                arrangementView.setLyrics("Lyrics is disabled in options!");
            }

            if (abcSong.isFromXmlFile()) {
                String note = abcSong.getNote();
                if (note != null) {
                    arrangementView.setTextnote(note);
                    if (!note.isEmpty()) {
                        arrangementView.sidepanelTab("Notes");
                        arrangementView.sidepanelVisible(true);
                    }
                }
            }

			setTranspose(abcSong.getTranspose());
			setTempo(abcSong.getTempoBPM());
			keySignatureField.setValue(abcSong.getKeySignature());
			setMeter(abcSong.getTimeSignature());

            // setting on model dont fire action listener
            timingCombo.getModel().setSelectedItem(TimingEnum.getInstance(abcSong.isOrganic(),abcSong.isOrganic2(),abcSong.isMixTiming(),abcSong.isTripletTiming(),abcSong.isPriorityActive()));

            tempoOnlyFirstCheckBox.setSelected(abcSong.isUsingOldTempos());

			SequenceInfo sequenceInfo = abcSong.getSequenceInfo();
			sequencer.setSequence(sequenceInfo.getSequence());
            // TODO: should we not have sequencer be a LotroSeqWrapper when loading from abc?
            //       in which case we should here call seq.setCurrentTrackInfos(sequenceInfo.getLastTrackInfos());
            //       else its only abc player that can play abc files with more then 15 parts.
            //       Haven't checked how easy it is to do. NoteFilterSequencerWrapper differs in some ways.
			sequencer.setRealDura(sequenceInfo.realDuraTicks);
			
			firstMidiNoteTick = sequenceInfo.calcFirstNoteTick();
			if (!abcSong.isFromXmlFile() && !abcSong.isFromAbcFile()) {
				sequencer.setTickPosition(firstMidiNoteTick);
			}
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
					boolean autoplay = miscSettings.autoplayOnOpen;
					updatePreviewMode(true, autoplay);
					updateButtons(true);
				}
			} else {
				updateButtons(true);
				if (abcSong.getParts().isEmpty()) {
					abcSong.createNewPart();
				}
				
				if (miscSettings.autoplayOnOpen) {
					sequencer.start();
				}
			}

			abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
			abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
            abcSong.setReducedFilesize(saveSettings.reducedFilesize);
            abcSong.setUseRestsInChords(saveSettings.useRestsInChords);

			
			// abcSong.setShowPruned(saveSettings.showPruned);

			setAbcSongModified(midiResolved);
			midiResolved = false;
			updateTitle();
            arrangementView.scrollToTop();
		} catch (SAXParseException e) {
			String message = e.getMessage();
			if (e.getLineNumber() >= 0) {
				message += "\nLine " + e.getLineNumber();
				if (e.getColumnNumber() >= 0)
					message += ", column " + e.getColumnNumber();
			}

			arrangementView.showInfoMessage(formatErrorMessage("Could not open " + file.getName(), message));
			midiResolved = false;
		} catch (InvalidMidiDataException | IOException | ParseException | SAXException e) {
			arrangementView.showInfoMessage(formatErrorMessage("Could not open " + file.getName(), e.getMessage()));
			midiResolved = false;
		}
		
		// Don't update last opened list when reading tmp msx file for midi reloading
		if (updateLastOpenedList && file.getAbsolutePath().endsWith(Util.MSX_FILE_EXTENSION)) {
			recentlyOpenedList.addOpenedFile(file);
			updateOpenRecentMenu();
		}
		inOpenFile = false;
	}
	
	private boolean reloadWithNewSource(File newSource) {
		List<Pair<Boolean, Boolean>> soloMuteState = partsList.getSoloMuteStates();
		File originalMsx = abcSong.getProjectFile();
		File oldSource = abcSong.getSourceFile();
		boolean modified = abcSongModified;
		File tmpMsx;
		try {
			tmpMsx = File.createTempFile("tmpproj", Util.MSX_FILE_EXTENSION);
		} catch (IOException e) {
			return false;
		}
		
		abcSong.setProjectFile(tmpMsx);
		abcSong.setSourceFile(newSource);
		
		if (!finishSave(false)) {
			// failed to save tmp - restore
			abcSong.setProjectFile(originalMsx);
			abcSong.setSourceFile(oldSource);
			return false;
		}

        // discard old abcSong and abcParts
		AbcSong oldAbcSong = abcSong;
        if (oldAbcSong != null) {
            oldAbcSong.getParts().getListModel().removeListDataListener(partsListListener);
            oldAbcSong.discard();
        }
        boolean abcPreview = abcPreviewMode;
        boolean hideEdits = hideEditsCheckbox.isSelected();
        abcSongModified = false;

		openFile(tmpMsx, false);
		if (abcSong != null) {
			abcSong.setProjectFile(originalMsx);
			setAbcSongModified(newSource != oldSource || modified);	
			updateTitle();
		}
		
		partsList.restoreSoloMuteState(soloMuteState);

        updatePreviewMode(abcPreview, miscSettings.autoplayOnOpen);
        if (hideEdits && !hideEditsCheckbox.isSelected()) hideEditsCheckbox.doClick();
		
		return true;
	}

	private void setPartsListModel() {
		// Not really used as a model anymore since switching to PartsList rather than JList
		partsList.setModel(abcSong.getParts().getListModel());
		partEditor.setModel(abcSong.getParts().getListModel());
	}

	/** Used when the MIDI file in a Maestro song project can't be loaded. */
	private final FileResolver openFileResolver = new FileResolver() {
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
                jfc.addChoosableFileFilter(
                        new ExtensionFileFilter("MIDI",
                                Util.MID_FILE_EXTENSION_NO_DOT,
                                Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT));
                jfc.addChoosableFileFilter(
                        new ExtensionFileFilter("ABC",
                                Util.ABC_FILE_EXTENSION_NO_DOT,
                                Util.TXT_FILE_EXTENSION_NO_DOT));
				jfc.setFileFilter(new ExtensionFileFilter("MIDI and ABC files", Util.MID_FILE_EXTENSION_NO_DOT,
						Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT, Util.ABC_FILE_EXTENSION_NO_DOT,
						Util.TXT_FILE_EXTENSION_NO_DOT));
                jfc.setAcceptAllFileFilterUsed(false);
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

    public float getSourcePlayHeadBar() {
        long tickLength = Math.max(0, sequencer.getTickLength());
        long tick = Math.min(tickLength, sequencer.getThumbTick());
        SequenceDataCache cache = abcSong.getSequenceInfo().getDataCache();
        /*
        QuantizedTimingInfo qtm = null;
        try {
            qtm = abcSong.getAbcExporter().getTimingInfo();
        } catch (AbcConversionException e) {
            throw new RuntimeException(e);
        }
         */
        return cache.tickToBarNumberFloat(tick);
    }

	private void updatePreviewMode(boolean abcPreviewModeNew) {
		SequencerWrapper oldSequencer = abcPreviewMode ? abcSequencer : sequencer;
		updatePreviewMode(abcPreviewModeNew, oldSequencer.isRunning());
	}

	private void updatePreviewMode(boolean newAbcPreviewMode, boolean shouldBeRunning) {
		boolean runningNow = abcPreviewMode ? abcSequencer.isRunning() : sequencer.isRunning();

		if (newAbcPreviewMode != abcPreviewMode || runningNow != shouldBeRunning) {
			if (shouldBeRunning && newAbcPreviewMode) {
				if (!refreshPreviewSequence(true)) {
					shouldBeRunning = false;

					SequencerWrapper oldSequencer = abcPreviewMode ? abcSequencer : sequencer;
					oldSequencer.stop();
				}
			} else if (abcSong != null && abcSong.getActivePartCount() > 0) {
				// for histogram. The condition is due to it might be
                // refreshPreviewSequence thats calling us, and we
                // don't want infinite loop.
				refreshPreviewSequence(false);
			}

			midiPositionLabel.setVisible(!newAbcPreviewMode);
			abcPositionLabel.setVisible(newAbcPreviewMode);
			midiBarLabel.setVisible(!newAbcPreviewMode);
			abcBarLabel.setVisible(newAbcPreviewMode);
			midiModeRadioButton.setSelected(!newAbcPreviewMode);
			abcModeRadioButton.setSelected(newAbcPreviewMode);

			SequencerWrapper newSequencer = newAbcPreviewMode ? abcSequencer : sequencer;
			newSequencer.setRunning(shouldBeRunning);

			abcPreviewMode = newAbcPreviewMode;
            SequencerWrapper.isAbcPreview = abcPreviewMode;

			arrangementView.setAbcPreviewMode(abcPreviewMode);
			updateButtons(false);
		}
		if (abcPreviewMode) {
			long tick = abcSequencer.getTickPosition();
			if (tick < abcPreviewStartTick)
				tick = abcPreviewStartTick;

			if (tick >= abcSequencer.getTickLength()) {
				tick = 0;
				abcSequencer.setRunning(false);
			}
			abcSequencer.setTickPosition(tick);
		}
	}

    private JPanel playControlPanel;

    private class PreviewExportWorker extends SwingWorker<SequenceInfo, Boolean> {

        private final AbcExporter myExporter;
        private final AbcSong mySong;
        private final boolean lotroInstruments;
        private final boolean oldVelocities;
        private Throwable backgroundException = null;

        public PreviewExportWorker(AbcSong mySong, boolean lotroInstruments, boolean oldVelocities, int pan) throws AbcConversionException {
            this.mySong = new AbcSong(mySong);
            this.myExporter = this.mySong.getAbcExporter();
            this.myExporter.stereoPan = pan;
            this.lotroInstruments = lotroInstruments;
            this.oldVelocities = oldVelocities;
        }

        /**
         * This runs on non-Swing thread
         */
        @Override
        protected SequenceInfo doInBackground() throws AbcConversionException, InvalidMidiDataException {
            try {
                return SequenceInfo.fromAbcParts(mySong, lotroInstruments, oldVelocities);
            } catch (Throwable t) {
                backgroundException = t;
                throw t;
            }
        }

        /**
         * This runs on Swing thread
         */
        @Override
        protected void done() {
            if (isCancelled()) {
                if (backgroundException != null) {
                    // Log the error that
                    // happened even though we were canceled.
                    log.log(Level.WARNING, "Exception occurred during cancelled preview export", backgroundException);
                }
                return;
            }
            try {
                // This check is to guard against preview generated on worker thread,
                // but while that happens, the user sends a new file to Maestro via Windows
                // explorer. So we check if the abcSong matches.
                if (abcSong == mySong.origSong) applyPreview(get(), myExporter);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.log(Level.WARNING, "Error exporting preview", cause);

                sequencer.stop();
                abcSequencer.stop();
                JOptionPane.showMessageDialog(ProjectFrame.this, cause.getMessage(), "Error previewing ABC",
                        JOptionPane.WARNING_MESSAGE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable e) {
                log.log(Level.WARNING, "Error applying preview", e);
                Throwable cause = e.getCause() != null ? e.getCause() : e;

                sequencer.stop();
                abcSequencer.stop();
                JOptionPane.showMessageDialog(ProjectFrame.this, cause.getMessage(), "Error previewing ABC",
                        JOptionPane.WARNING_MESSAGE);
            } finally {
                setSourceChangeEnabled(true);
            }
        }
    }

    /**
     * Runs only in Swing Thread
     */
    private void applyPreview(SequenceInfo previewSequenceInfo, AbcExporter exporter) {
        abcPreviewStartTick = exporter.getExportStartTick();
        abcPreviewTempoFactor = abcSequencer.getTempoFactor();
        abcBarLabel.setBarNumberCache(exporter.getTimingInfo());
        abcBarLabel.setInitialOffsetTick(abcPreviewStartTick);
        abcPositionLabel.setInitialOffsetTick(abcPreviewStartTick);

        long tick = sequencer.getTickPosition();

        boolean abcRunning = abcSequencer.isRunning();
        if (abcPreviewMode) {
            // Refreshing while playing Original (GS) will cause a GS Reset,
            // which will mess with volume, hence the if statement.
            abcSequencer.reset(false);
        }
        try {
            abcSequencer.setSequence(previewSequenceInfo.getSequence());
            abcSequencer.setCurrentTrackInfos(previewSequenceInfo.getLastTrackInfos());
            for(AbcPart p : abcSong.getParts()) {
                p.numberOfExportedNotes = 0;
                p.numberOfRemovedNotesForSafety = 0;
                p.setMaxPoly(0);
            }
            if (previewSequenceInfo.getLastTrackInfos() != null) {
                for (AbcExporter.ExportTrackInfo trackInfo : previewSequenceInfo.getLastTrackInfos()) {
                    //threadsafe to do it here
                    trackInfo.part.setPreviewSequenceTrackNumber(trackInfo.trackNumber);
                    trackInfo.part.numberOfExportedNotes = trackInfo.numberOfExportedNotes;
                    trackInfo.part.numberOfRemovedNotesForSafety = trackInfo.numberOfRemovedNotesForSafety;
                    trackInfo.part.setMaxPoly(trackInfo.maxPoly);
                }
            }
            abcSequencer.setStartTick(abcPreviewStartTick);// Needed for MP3 and WAV exports.

            long lengthABC = abcSong.getSongLengthMicros();

            log.info("A new preview was generated");
            log.info("Duration MIDI:    " + Util.formatDurationM(sequencer.getLength()));
            log.info("Duration Preview: " + Util.formatDurationM(abcSequencer.getLength() - abcSequencer.tickToMicros(abcPreviewStartTick)) + ", rounded up: " + Util.formatDuration(abcSequencer.getLength() - abcSequencer.tickToMicros(abcPreviewStartTick)));
            log.info("Duration ABC:     " + Util.formatDurationM(lengthABC) + ", rounded up: " + Util.formatDuration(lengthABC));

            /*
            if (tick < abcPreviewStartTick)
                tick = abcPreviewStartTick;

            if (tick >= abcSequencer.getTickLength()) {
                tick = 0;
                abcRunning = false;
            }
            */

            if (abcRunning && sequencer.isRunning())
                sequencer.stop();

            abcSequencer.setTickPosition(tick);
            abcSequencer.setRunning(abcRunning);
            previewSequenceInfo.histogram.setSequencer(abcSequencer);
            arrangementView.setHistogram(previewSequenceInfo.histogram);
            histogram = previewSequenceInfo.histogram;
        } catch (InvalidMidiDataException e) {
            log.log(Level.WARNING, "Error after exporting preview", e);
            sequencer.stop();
            abcSequencer.stop();
            arrangementView.setHistogram(null);
            histogram = null;
            JOptionPane.showMessageDialog(ProjectFrame.this, e.getMessage(), "Error previewing ABC",
                    JOptionPane.WARNING_MESSAGE);
        }
        compileStats();
    }

    private boolean refreshPreviewSequence(boolean immediate) {
        if (!SwingUtilities.isEventDispatchThread()) {
            log.log(Level.SEVERE, "refreshPreviewSequence: not on Swing thread", new RuntimeException());
            return false;
        }
        PreviewExportWorker oldWorker = null;
        if (previewWorker != null) {
            if (!previewWorker.isDone()) {
                oldWorker = previewWorker;
                previewWorker.cancel(true);
            }
        }

        if (abcSong == null || abcSong.getActivePartCount() == 0) {
            abcPreviewStartTick = 0L;
            abcPreviewTempoFactor = 1.0f;
            abcSequencer.clearSequence();
            abcSequencer.reset(false);
            abcBarLabel.setBarNumberCache(null);
            abcBarLabel.setInitialOffsetTick(abcPreviewStartTick);
            abcPositionLabel.setInitialOffsetTick(abcPreviewStartTick);
            arrangementView.setHistogram(new PolyphonyHistogram());
            histogram = null;
            updatePreviewMode(false);
            setSourceChangeEnabled(true);
            return false;
        }

        if (!immediate) {
            try {
                abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
                abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
                abcSong.setReducedFilesize(saveSettings.reducedFilesize);
                abcSong.setUseRestsInChords(saveSettings.useRestsInChords);
                // abcSong.setShowPruned(saveSettings.showPruned);
                previewWorker = new PreviewExportWorker(abcSong, !failedToLoadLotroInstruments, false, prefs.getInt("stereoPan", 100));
                setSourceChangeEnabled(false);
                previewWorker.execute();
            } catch (AbcConversionException e) {
                log.log(Level.WARNING, "Error exporting preview", e);
                sequencer.stop();
                abcSequencer.stop();
                JOptionPane.showMessageDialog(ProjectFrame.this, e.getMessage(), "Error previewing ABC",
                        JOptionPane.WARNING_MESSAGE);
                setSourceChangeEnabled(true);
            }

            return true;
        }

        try {
            if (oldWorker != null) {
                try {
                    oldWorker.get();
                    // doInBackground has now finished
                    // wait even though its canceled cause
                    // getBacExporter might change abcExporter and
                    // mess up its internal state
                } catch (Throwable ignored) {
                }
            }

            abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
            abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
            abcSong.setReducedFilesize(saveSettings.reducedFilesize);
            abcSong.setUseRestsInChords(saveSettings.useRestsInChords);
            // abcSong.setShowPruned(saveSettings.showPruned);
            AbcExporter exporter = abcSong.getAbcExporter();
            exporter.stereoPan = prefs.getInt("stereoPan", 100);
            SequenceInfo previewSequenceInfo = SequenceInfo.fromAbcParts(exporter, !failedToLoadLotroInstruments, false);
            applyPreview(previewSequenceInfo, exporter);
            setSourceChangeEnabled(true);
            return true;
        } catch(Exception e) {
            //setUIEnabled(true);
            log.log(Level.WARNING, "Error exporting preview (immediate)", e);
            Throwable cause = e.getCause() != null ? e.getCause() : e;

            sequencer.stop();
            abcSequencer.stop();
            JOptionPane.showMessageDialog(ProjectFrame.this, cause.getMessage(), "Error previewing ABC",
                    JOptionPane.WARNING_MESSAGE);
        }
        setSourceChangeEnabled(true);
        return false;
    }

	private void commitAllFields() {
		try {
			abcSong.setNote(arrangementView.getTextnote());
			arrangementView.commitAllFields();
			transposeSpinner.commitEdit();
			tempoSpinner.commitEdit();
			timeSignatureField.commitEdit();
			keySignatureField.commitEdit();
		} catch (java.text.ParseException e) {
			// Ignore
		}
	}
	
	public void compileStats() {
        boolean hasAbcNotes = false;
        if (abcSong != null) {
            for (AbcPart part : abcSong.getParts()) {
                if (part.getEnabledTrackCount() > 0) {
                    hasAbcNotes = true;
                    break;
                }
            }
        }
		if (abcSong == null || !hasAbcNotes) {
			arrangementView.setStats("No stats available");
			return;
		}
		String tempNote = "";
		//tempNote += "Main export tempo is " + getTempo()+"\n\n";
		tempNote += getTimingStats();
		tempNote += checkDuplicatePartTitles();
		//tempNote += getNumberOfExportNotes(); // if enable this, then also output why notes got deleted, else confusing.
        tempNote += getPoly6plusStats();
		tempNote += getEmptyParts();
        tempNote += abcSong.getStats();
        if (histogram != null) {
            if (histogram.isDirty()) {
                // important for solo/mute parts
                histogram.sumUp(abcSong);
            }
            tempNote += histogram.getStats();
        }
		arrangementView.setStats(tempNote);

        /*
            TODO:
                Stats on broken up notes.
                Stats on bent notes. How many per part, and how many they got expanded to.
                Stats on removed notes and why. And then enable number of exported notes.
                Stats on shortest/longest note durations in song.
                Stats on highest/lowest exported pitch.
                Number of song section-edits.
         */
	}
	
	private String getTimingStats() {
		String out = "";
		QuantizedTimingInfo qtm = abcSong.getQTM();
		if (qtm != null) {
			out += qtm.getStats()+"\n";
			out += qtm.getTempoStats()+"\n";
		}		
		return out;
	}
	
	private String getNumberOfExportNotes() {
		StringBuilder out = new StringBuilder();
        int songNotes = 0;
		for (AbcPart part : abcSong.getParts()) {
			out.append("Part #").append(part.getPartNumber()).append(" will export ").append(part.numberOfExportedNotes).append(" notes.\n");
            songNotes += part.numberOfExportedNotes;
		}
        out.append("\nSong").append(" will export ").append(songNotes).append(" notes.\n");
        if (saveSettings.deleteMinimalNotes && !abcSong.isOrganic()) {
            out.append("\n");
            out.append("Delete minimal notes:\n");
            boolean active = false;
            for (AbcPart part : abcSong.getParts()) {
                if (part.numberOfRemovedNotesForSafety > 0) {
                    out.append("Part #").append(part.getPartNumber()).append(" removed ").append(part.numberOfRemovedNotesForSafety).append(" very short notes to reduce undesired dissonance.\n");
                    active = true;
                }
            }
            if (!active) {
                out.append(" None were deleted.\n");
            }
        }
		out.append("\n");
		return out.toString();
	}

    private String getPoly6plusStats() {
        StringBuilder out = new StringBuilder();
        if (abcSong != null && abcSong.isOrganic() && abcSong.isUseRestsInChords()) {
            // expensive to compute, so we only do it for poly 6+
            out.append("\n");
            out.append("Part polyphony:\n");
            for (AbcPart part : abcSong.getParts()) {
                out.append("Part #").append(part.getPartNumber()).append(" has max ");
                out.append(part.getMaxPoly()).append("\n");
            }
            out.append("\n");
        }
        return out.toString();
    }

	private String getEmptyParts() {
		StringBuilder out = new StringBuilder();
		for (AbcPart part : abcSong.getParts()) {
			if (part.getEnabledTrackCount() == 0) {
				out.append("Part #").append(part.getPartNumber()).append(" has no assigned tracks!\n\n");
			}
		}
		return out.toString();
	}
	
	private String checkDuplicatePartTitles() {
		StringBuilder out = new StringBuilder();
		ListModelWrapper<AbcPart> parts = abcSong.getParts();
		for (int i = 0 ; i < parts.size(); i++) {
			AbcPart part1 = parts.get(i);
			for (int j = i+1 ; j < parts.size(); j++) {
				AbcPart part2 = parts.get(j);
				if (part1.getTitle().equals(part2.getTitle())) {
					out.append("Warning: Part #").append(part1.getPartNumber()).append(" and part #").append(part2.getPartNumber()).append(" has same title:\n ").append(part1.getTitle()).append("\n\n");
				}
			}
		}
		return out.toString();
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
				if (res == JOptionPane.CANCEL_OPTION || res == JOptionPane.CLOSED_OPTION)
					return null;
				if (res != JOptionPane.YES_OPTION)
					continue;
			}

			return selectedFile;
		}
	}
	
	private File getAbcExportFile() {
		File exportFile = abcSong.getExportFile();

		String defaultFolder = Util.getLotroMusicPath(true).getAbsolutePath();
		String folder = prefs.get("exportDialogFolder", defaultFolder);
		if (exportFile != null) // Use previously exported folder if it exists
			folder = exportFile.getAbsoluteFile().getParent();
		if (!new File(folder).exists())
			folder = defaultFolder;

		String fileName = "mySong"+Util.ABC_FILE_EXTENSION;

		// Always regenerate setting from pattern export is highest precedent
		if (exportFilenameTemplate.shouldRegenerateFilename()) {
			fileName = exportFilenameTemplate.formatName(abcSong);
		} else if (exportFile != null) // else use abc filename if exists already
		{
			fileName = exportFile.getName();
		} else if (abcSong.getProjectFile() != null) // else use msx filename if exists already
		{
			fileName = abcSong.getProjectFile().getName();
		} else if (exportFilenameTemplate.isEnabled()) // else use pattern if usage is enabled
		{
			fileName = exportFilenameTemplate.formatName(abcSong);
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
		fileName += Util.ABC_FILE_EXTENSION;

		exportFile = new File(folder, fileName);
		
		return exportFile;
	}

	private boolean exportAbcAs() {
		exportSuccessfulLabel.setVisible(false);

		if (abcSong == null) {
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

        if (!confirmExportDespiteWarnings()) {
            return false;
        }
		
		File exportFile = getAbcExportFile();
		File allowOverwriteFile = allowOverwriteExportFile ? abcSong.getExportFile() : null;

		exportFile = doSaveDialog(exportFile, allowOverwriteFile, Util.ABC_FILE_EXTENSION,
				new ExtensionFileFilter("ABC files (*.abc, *.txt)", Util.ABC_FILE_EXTENSION_NO_DOT, Util.TXT_FILE_EXTENSION_NO_DOT));

		if (exportFile == null) {
			return false;
		}

		prefs.put("exportDialogFolder", exportFile.getAbsoluteFile().getParent());

		abcSong.setExportFile(exportFile);
		allowOverwriteExportFile = true;
		return finishExportAbc(exportFile);
	}

	private boolean shouldExportAbcAs() {
		boolean regeneratedFilenameIsDifferent =
				abcSong != null && abcSong.getExportFile() != null &&
				exportFilenameTemplate.shouldRegenerateFilename() &&
				!exportFilenameTemplate.formatName(abcSong).equals(abcSong.getExportFile().getName());

        File exportFile = abcSong == null ? null : abcSong.getExportFile();

		return saveSettings.showExportFileChooser || !allowOverwriteExportFile || exportFile == null
				|| !exportFile.exists() || regeneratedFilenameIsDifferent;
	}

	private boolean exportAbc() {
		exportSuccessfulLabel.setVisible(false);
		if (abcSong == null) {
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (shouldExportAbcAs()) {
            return exportAbcAs();
        } else if (!confirmExportDespiteWarnings()) {
            return false;
        }

		return finishExportAbc(abcSong.getExportFile());
	}

    private boolean confirmExportDespiteWarnings() {
        List<String> warnings = abcSong.getExportWarnings(histogram);
        for(String warning : warnings) {
            int option = JOptionPane.showConfirmDialog(this, warning+"\nDo you want to proceed with exporting without fixing it?", "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (option != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        return true;
    }

	private boolean finishExportAbc(File exportFile) {
        setUIEnabled(false);
		exportSuccessfulLabel.setVisible(false);
		commitAllFields();
        StringCleaner.cleanABC = saveSettings.convertABCStringsToBasicAscii;

        AbcExportWorker worker = new AbcExportWorker(exportFile);
        worker.execute();
        return true;
        /*
		try {

			abcSong.exportAbc(exportFile, MaestroMain.APP_NAME);

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
        */
	}

    private class AbcExportWorker extends SwingWorker<Void, Void> {

        private final File exportFile;

        public AbcExportWorker(File exportFile) {
            this.exportFile = exportFile;
        }

        /**
         * This runs on non-Swing thread
         */
        @Override
        protected Void doInBackground() throws IOException, AbcConversionException {
            abcSong.exportAbc(exportFile, MaestroMain.APP_NAME);
            return null;
        }

        /**
         * This runs on Swing thread
         */
        @Override
        protected void done() {
            try {
                get(); // get exceptions from doInBackground()

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
            } catch (CancellationException ignored) {
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.log(Level.WARNING, "Error when exporting ABC", cause);
                if (cause instanceof FileNotFoundException) {
                    JOptionPane.showMessageDialog(ProjectFrame.this, "Failed to create file!\n" + cause.getMessage(),
                            "Failed to create file", JOptionPane.ERROR_MESSAGE);
                } else if (cause instanceof IOException || cause instanceof AbcConversionException) {
                    JOptionPane.showMessageDialog(ProjectFrame.this, cause.getMessage(), "Error",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    // Catch any other unexpected errors
                    JOptionPane.showMessageDialog(ProjectFrame.this, "An unexpected error occurred:\n" + cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }

            } catch (Exception e) {
                log.log(Level.SEVERE, "Error exporting ABC", e);
                JOptionPane.showMessageDialog(ProjectFrame.this, "An unexpected UI error occurred:\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                setUIEnabled(true);
            }
        }
    }

    private final MouseAdapter blocker = new MouseAdapter() {};

    /**
     * A setEnabled for changing source
     */
    private void setSourceChangeEnabled(boolean on) {
        sourceChangeEnabled = on;
        updateButtons(true);
    }

    /**
     * A setEnabled for the entire Maestro App.
     */
    private void setUIEnabled(boolean on) {
        uiEnabled = on;
        Component glassPane = getGlassPane();
        if (!on) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            glassPane.addMouseListener(blocker);
            glassPane.setVisible(true);
        } else {
            glassPane.setVisible(false);
            glassPane.removeMouseListener(blocker);
            setCursor(Cursor.getDefaultCursor());
        }

        // Disable the dialogs also,
        // as any changes from them can bring multi-threading trouble:
        partEditor.uiEnabled(on);
        SectionEditor.uiEnabled(on);
        TuneEditor.uiEnabled(on);
    }

	private boolean saveAs() {
		if (abcSong == null) {
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		File saveFile = abcSong.getProjectFile();
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

		String fileName = "mySong"+Util.MSX_FILE_EXTENSION;

		// Always regenerate setting from pattern export is highest precedent
		if (exportFilenameTemplate.shouldRegenerateFilename()) {
			fileName = exportFilenameTemplate.formatName(abcSong);
		} else if (saveFile != null) // else use MSX file if exists already
		{
			fileName = saveFile.getName();
		} else if (abcSong.getExportFile() != null) // else use ABC filename if exists
		{
			fileName = abcSong.getExportFile().getName();
		} else if (exportFilenameTemplate.isEnabled()) // else use pattern if enabled
		{
			fileName = exportFilenameTemplate.formatName(abcSong);
		} else if (abcSong.getSourceFile() != null) // else use source (midi/abc) file
		{
			fileName = abcSong.getSourceFilename();
		}

		int dot = fileName.lastIndexOf('.');
		if (dot > 0)
			fileName = fileName.substring(0, dot);
		fileName += Util.MSX_FILE_EXTENSION;

		saveFile = new File(folder, fileName);

		saveFile = doSaveDialog(saveFile, allowOverwriteFile, Util.MSX_FILE_EXTENSION,
				new ExtensionFileFilter(AbcSong.MSX_FILE_DESCRIPTION_PLURAL + " (*" + Util.MSX_FILE_EXTENSION + ")",
						Util.MSX_FILE_EXTENSION_NO_DOT));

		if (saveFile == null)
			return false;

		prefs.put("saveDialogFolder", saveFile.getAbsoluteFile().getParent());
		abcSong.setProjectFile(saveFile);
		allowOverwriteSaveFile = true;
		return finishSave();
	}

	private boolean save() {
		if (abcSong == null) {
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (!allowOverwriteSaveFile || abcSong.getProjectFile() == null || !abcSong.getProjectFile().exists()) {
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
			XmlUtil.saveDocument(abcSong.saveToXml(), abcSong.getProjectFile());
		} catch (FileNotFoundException e) {
			JOptionPane.showMessageDialog(this, "Failed to create file!\n" + e.getMessage(), "Failed to create file",
					JOptionPane.ERROR_MESSAGE);

			return false;
		} catch (IOException | TransformerException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		if (updateRecentlyOpenedFiles) {
			recentlyOpenedList.addOpenedFile(abcSong.getProjectFile());
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
        fileName += Util.MID_FILE_EXTENSION;

        saveFile = new File(directory, fileName);

        saveFile = doSaveDialog(saveFile, saveFile, Util.MID_FILE_EXTENSION, new ExtensionFileFilter("MIDI songs (*.mid)", Util.MID_FILE_EXTENSION_NO_DOT));

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
				log.info("Writing type " + types[types.length - 1] + " expanded midi as '"
						+ saveFile.getAbsolutePath() + "'");
				MidiSystem.write(sequence2, types[types.length - 1], saveFile);
			} else {
				JOptionPane.showMessageDialog(this, "Something went wrong when in midi type handling", "Error",
						JOptionPane.ERROR_MESSAGE);
				return false;
			}
		} catch (FileNotFoundException e) {
			log.warning(e.getMessage());
			JOptionPane.showMessageDialog(this, "Failed to create file!\n" + e.getMessage(), "Failed to create file",
					JOptionPane.ERROR_MESSAGE);

			return false;
		} catch (InvalidMidiDataException | IOException e) {
			log.severe(e.getMessage());
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
		case JOptionPane.CANCEL_OPTION, JOptionPane.CLOSED_OPTION:
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
	 * @return A string ready to be printed out
	 */
	@SuppressWarnings("unused")
	private static String threadDump(boolean lockedMonitors, boolean lockedSynchronizers) {
		StringBuilder threadDump = new StringBuilder(System.lineSeparator());
		ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
		for (ThreadInfo threadInfo : threadMXBean.dumpAllThreads(lockedMonitors, lockedSynchronizers)) {
			threadDump.append(threadInfo.toString());
		}
		return threadDump.toString();
	}
	
	private void checkVersionCompare() {
		if(future == null || future.isDone()) {
			future = CompletableFuture.runAsync(() -> {
				try {
					String fileUrl = "https://raw.githubusercontent.com/NikolaiVChr/mver/refs/heads/main/main";
					URI uri = new URI(fileUrl);
					URL url = uri.toURL();
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setConnectTimeout(4000);
					connection.setReadTimeout(6000);
					connection.setRequestMethod("GET");
					try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
						String line;
						while ((line = reader.readLine()) != null) {
							latestVer = Version.parseVersion(line);
							Version myVersion = MaestroMain.APP_VERSION;
							if (latestVer != null && myVersion.compareTo(latestVer) < 0) {
								SwingUtilities.invokeLater(() -> {
									int result = JOptionPane.showConfirmDialog(ProjectFrame.this, "Version "+latestVer+" is available, do you want to close and download it?", "Version check",
											JOptionPane.YES_NO_OPTION);
										if (result == JOptionPane.YES_OPTION) {
											URI uriDownload;
											try {
												uriDownload = new URI(MaestroMain.DOWNLOAD_URL);											
												if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
													if (closeSong()) {
														Desktop.getDesktop().browse(uriDownload);
														System.exit(0);
													}
												}
											} catch (URISyntaxException | IOException e) {
												log.log(Level.WARNING, "Failed to open browser", e);
											}
										}
									}
								);
								break;
							}
						}
					} catch (Exception io) {
                        log.log(Level.WARNING, "Failed to read current version string from HTTP", io);
					}
					connection.disconnect();
				} catch (Throwable e) {
                    log.log(Level.WARNING, "Failed to connect to github to read current version string", e);
				}
			});
		}
	}

    public enum TimingEnum {
        ORGANIC_MULTISTAGE ("Organic Multi-stage",true, true, false,false,false,"Organic Multistage"),
        ORGANIC_SINGLESTAGE ("Organic Single-stage", true, false, false,false,false,"Organic Singlestage"),
        MIX ("Mix Timings", false, false, true,false,false,"Mix Timings"),
        MIX_SWING ("Mix Timings, Swing", false, false, true,true,false,"Mix Timings Swing/Triplet"),
        MIX_PRIO ("Mix Timings, Combine Priorities", false, false, true,false,true,"Mix Timings Combine Priorities"),
        MIX_SWING_PRIO ("Mix Timings, Swing, Combine Priorities", false, false, true,true,true,"Mix Timings Swing/Triplet Combine Priorities"),
        LEGACY ("Legacy Timings", false, false, false,false,false,"Legacy"),
        LEGACY_SWING ("Legacy Timings, Swing", false, false, false,true,false,"Legacy Swing/Triplet"),
        ;
        {}
        public final boolean organic;
        public final boolean multistage;
        public final boolean mixTimings;
        public final boolean swing;
        public final boolean priority;
        public final String info;
        public final String settingsString;// use this for settings prefs. And never change the strings.

        TimingEnum(String info, boolean organic, boolean multistage, boolean mixTimings, boolean swing, boolean priority, String settings) {
            this.info = info;
            this.organic = organic;
            this.multistage = multistage;
            this.mixTimings = mixTimings;
            this.swing = swing;
            this.priority = priority;
            this.settingsString = settings;
        }

        public static TimingEnum getFromSettings(String defaultTiming) {
            Objects.requireNonNull(defaultTiming);
            for (TimingEnum timing : TimingEnum.values()) {
                if (timing.settingsString.equals(defaultTiming)) {
                    return timing;
                }
            }
            return MIX;
        }

        public void action(@Nullable AbcSong abcSong) {
            if (abcSong != null) {
                abcSong.setTimings(organic, multistage, mixTimings, swing, priority);
            }
        }

        String getTooltip() {
            return switch (this) {
                case ORGANIC_MULTISTAGE -> "<html>Different approach to exporting fluid timings.<br>"
                        + "This is a beta feature, use on own risk.</html>";
                case ORGANIC_SINGLESTAGE -> "<html>Export more fluid timings.<br>"
                        + "This is a beta feature, use on own risk.</html>";
                case LEGACY -> "<html>Export whole song in the same fixed timing grid.<html>";
                case LEGACY_SWING -> "<html>Export whole song in the same fixed timing grid." +
                        "<br>Will setup the grid for triplets or a swing rhythm.<br><br>"
                        + "This can cause short/fast notes to incorrectly be output as triplets.<br>"
                        + "Don't use this unless the song has many triplets or a swing/jig rhythm.</html>";
                case MIX_SWING_PRIO -> "<html>Allow Maestro to detect which notes<br>"
                        + "that needs triplet/swing timing.<br><br>"
                        + "It is done per part, so some notes in a parts might export as swing/tuplets<br>"
                        + "while other parts at same time export even notes." +
                        "<br>In case of uncertainty swing/triplet will be choosen."+
                        "<br>Allows to set track priority.<br>" +
                        "Checkboxes will appear when combining tracks,<br>" +
                        "those enabled will prioritize the timings of those tracks over non-prioritized tracks.</html>";
                case MIX -> "<html>Allow Maestro to detect which notes<br>"
                        + "that needs triplet/swing timing.<br><br>"
                        + "It is done per part, so some notes in a parts might export as swing/tuplets<br>"
                        + "while other parts at same time export even notes.</html>";
                case MIX_SWING -> "<html>Allow Maestro to detect which notes<br>"
                        + "that needs triplet/swing timing.<br><br>"
                        + "It is done per part, so some notes in a parts might export as swing/tuplets<br>"
                        + "while other parts at same time export even notes.<br>" +
                        "In case of uncertainty swing/triplet will be choosen.</html>";
                case MIX_PRIO -> "<html>Allow Maestro to detect which notes<br>"
                        + "that needs triplet/swing timing.<br><br>"
                        + "It is done per part, so some notes in a parts might export as swing/tuplets<br>"
                        + "while other parts at same time export even notes." +
                        "<br>Allows to set track priority.<br>" +
                        "Checkboxes will appear when combining tracks,<br>" +
                        "those enabled will prioritize the timings of those tracks over non-prioritized tracks.</html>";
                default -> null;
            };
        }

        static TimingEnum getInstance(boolean organic, boolean multistage, boolean mixTimings, boolean swing, boolean priority) {
            if (organic) {
                if (multistage) return ORGANIC_MULTISTAGE;
                else return ORGANIC_SINGLESTAGE;
            } else if (mixTimings) {
                if (swing) {
                    if (priority) return MIX_SWING_PRIO;
                    else return MIX_SWING;
                } else {
                    if (priority) return MIX_PRIO;
                    else return MIX;
                }
            } else {
                if (swing) return LEGACY_SWING;
                else return LEGACY;
            }
        }

        @Override
        public String toString() {
            return info;
        }
    }
}
