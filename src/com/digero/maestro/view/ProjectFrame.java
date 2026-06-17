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
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.time.Duration;
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

import javax.sound.midi.*;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.xml.transform.TransformerException;

import com.digero.common.abc.AbcConstants;
import com.digero.common.midi.*;
import com.digero.common.util.*;
import com.digero.common.view.ColorSelector;
import com.digero.common.view.UIText;
import com.digero.maestro.abc.DissonanceDetector;
import com.digero.maestro.midi.SequenceDataCache;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.digero.common.abc.StringCleaner;
import com.digero.common.icons.IconLoader;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
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
import com.digero.maestro.view.song.SongInfo;
import com.digero.maestro.view.song.SongInfoField;
import com.digero.maestro.view.song.SongInfoPanel;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;
import net.miginfocom.swing.MigLayout;

public class ProjectFrame extends JFrame implements TableLayoutConstants, ICompileConstants {
	private static final Logger log = Logger.getLogger("view");

	// future refactors might be able to make this field final
	private SongInfoPanel songInfoPanel;

    private boolean uiEnabled = true;
    private boolean sourceChangeEnabled = true;

	private static final int HGAP = 4;
	private static final int VGAP = 4;
	private static final double[] LAYOUT_COLS = new double[] { 180, FILL };
	private static final double[] LAYOUT_ROWS = new double[] { FILL };
	private static final TableLayout tableLayout = new TableLayout(LAYOUT_COLS, LAYOUT_ROWS);

	private final Preferences prefs = Preferences.userNodeForPackage(MaestroMain.class);

	private final int defaultStereo = 25;
	private final int defaultVolume = MidiConstants.MAX_VOLUME;

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
	private JDialog themeEditorDialog;

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

	private long abcPreviewStartTick = 0L;
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
					UIText.get("maestro.you.are.running.with.32.bit.java"),
					UIText.get("maestro.32.bit.detected"), JOptionPane.ERROR_MESSAGE);
            System.err.println(
                    "You are running with 32 bit Java.\nPlease start with 64 bit Java instead.\n Find Configure Java program in Start menu and\n configure it to start the 64 bit per default.\n\n");
            // System.exit(1);
            // return;
        }

		setRootPane(new JRootPane() {
			@Override
			public void requestFocus() {
				super.requestFocus();
				if (arrangementView != null) arrangementView.stopEditingLyrics();
			}
		});

        setMinimumSize(new Dimension(512, 384));
        Util.initWinBounds(this, prefs.node("window"), 800, 600);

        ToolTipManager.sharedInstance().setDismissDelay(8000);

        disableSpaceFocus();

        partAutoNumberer = new PartAutoNumberer(
			prefs.node("partAutoNumberer"));

        partNameTemplate = new PartNameTemplate(
			prefs.node("partNameTemplate"));

        exportFilenameTemplate = new ExportFilenameTemplate(
			prefs.node("exportFilenameTemplate"));

        instrNameSettings = new InstrNameSettings(
			prefs.node("instrNameSettings"));

        saveSettings = new SaveAndExportSettings(
			prefs.node("saveAndExportSettings"));

		//if misc settings is empty use fallback; Maestro 2.5.0.115 and earlier save misc settings in saveAndExportSettings
        miscSettings = new MiscSettings(
			prefs.node("miscSettings"),
                true);

        if (miscSettings.checkForUpdates) checkVersionCompare();

		String welcomeMessageTitle = UIText.get("maestro.welcomeMessageTitle");
		String welcomeMessage =	UIText.get("maestro.DnD.file.to.open") + UIText.get("maestro.use.file.open");

		// SongInfoPanel
		boolean showGenreAndMood = miscSettings.showBadger;
		String defaultTranscriber = prefs.get("abcplayer.transcriber", "");
		songInfoPanel = new SongInfoPanel(showGenreAndMood, defaultTranscriber);
        songInfoPanel.setChangeListener(this::updateAbcSongFromSongInfo);

        checkVolumeTransceiver();

        try {
            sequencer = new NoteFilterSequencerWrapper();
            if (volumeTransceiver != null)
                sequencer.addTransceiver(volumeTransceiver);

            abcSequencer = new LotroSequencerWrapper();
            if (abcVolumeTransceiver != null)
                abcSequencer.addTransceiver(abcVolumeTransceiver);

            if (LotroSequencerWrapper.getLoadLotroSynthError() != null) {
                welcomeMessageTitle = UIText.get("maestro.could.not.load.lotro.instrument.sounds");
				welcomeMessage = UIText.get("maestro.abc.preview.will.use.standard.midi.instruments.instead", LotroSequencerWrapper.getLoadLotroSynthError());
                failedToLoadLotroInstruments = true;
            }

        } catch (MidiUnavailableException e) {
            JOptionPane
                    .showMessageDialog(
                            null, UIText.get("maestro.failed.to.initialize.midi.sequencer.msg", e.getMessage()),
							UIText.get("maestro.failed.to.initialize.midi.sequencer.title"), JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        // SWING stuff starts here

        loadIcons();

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (closeProjectForShutdown()) {
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

        generateSongPartsPanel();

        generateExportSettingsPanel();

        generateMidiPartsAndControlsPanel();

		initTheme();//after arrangementView is defined, but before welcome message is set.

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
        arrangementView.showInfoMessage(formatInfoMessage(welcomeMessageTitle, welcomeMessage, getHTMLFontSizeNormal()));
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
		content.addMouseListener(listenForFocus);

		// Add to main structural panels that might capture clicks
		if (songInfoPanel != null) songInfoPanel.addMouseListener(listenForFocus);
		if (settingsPanel != null) settingsPanel.addMouseListener(listenForFocus);
		if (partsListPanel != null) partsListPanel.addMouseListener(listenForFocus);
		if (midiPartsAndControls != null) midiPartsAndControls.addMouseListener(listenForFocus);
		if (playControlPanel != null) playControlPanel.addMouseListener(listenForFocus);
		if (arrangementView != null) arrangementView.addMouseListener(listenForFocus);

		// Make sure content is capable of taking focus if RootPane refuses
		content.setFocusable(true);
		getRootPane().setFocusable(true);
		arrangementView.setFocusable(true);
    }

    private void updateAbcSongFromSongInfo(SongInfoField field,
        SongInfo songInfo) {		
		
		if (abcSong == null)
        		return;

    	switch (field) {
        	case TITLE ->
            	abcSong.setTitle(songInfo.title());

        	case COMPOSER ->
           		abcSong.setComposer(songInfo.composer());

        	case TRANSCRIBER -> {
            	abcSong.setTranscriber(songInfo.transcriber());
            	prefs.put("abcplayer.transcriber", songInfo.transcriber());
        	}

        	case GENRE ->
            	abcSong.setGenre(songInfo.genre());

        	case MOOD ->
            	abcSong.setMood(songInfo.mood());
    	}
	}

	private void updateSongInfoFromAbcSong() {
		if (!abcSong.isFromAbcFile() && !abcSong.isFromXmlFile()) {
        	abcSong.setTranscriber(songInfoPanel.getSongInfo().transcriber());
    	}

    	songInfoPanel.setSongInfo(new SongInfo(
            abcSong.getTitle(),
            abcSong.getComposer(),
            abcSong.getTranscriber(),
            abcSong.getGenre(),
            abcSong.getMood()
    	));
	}

	private void clearSongInfoPanel(){
		songInfoPanel.clearSongInfo();
	}

	/**
	 * 
	 * @return
	 */
	private SongInfo getCurrentSongInfo() {
		if (abcSong == null)
        	return SongInfo.empty();

		return new SongInfo(
            abcSong.getTitle(),
            abcSong.getComposer(),
            abcSong.getTranscriber(),
            abcSong.getGenre(),
            abcSong.getMood()
    	);
	}

	private void generateSongPartsPanel() {
		newPartButton = new JButton(UIText.get("maestro.new.part"));
		newPartButton.addActionListener(e -> {
			if (abcSong != null)
				abcSong.createNewPart();
		});

		deletePartButton = new JButton(UIText.get("maestro.delete"));
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
		
		sortPartsButton = new JButton(UIText.get("maestro.sort")) {
			public Dimension getMaximumSize() {
				return getPreferredSize();
			}
		};
		sortPartsButton.setToolTipText(UIText.get("maestro.tip.sort.parts"));
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
		
		
		
		partEditor = new PartEditor(this, null, miscSettings);

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
				arrangementView.stopEditingLyrics();
			}
		});
		
		Dimension sz = partsListScrollPane.getMinimumSize();
		sz.width = PartsListItem.getProtoDimension().width;
		partsListScrollPane.setPreferredSize(sz);

		partEditorButton = new JButton(UIText.get("maestro.part.editor"));
		partEditorButton.addActionListener(e -> {
			partEditor.setVisible(!partEditor.isVisible());
		});
		partEditorButton.setToolTipText(UIText.get("maestro.tip.partedit"));

		numerateButton = new JButton(UIText.get("maestro.numerate"));
		numerateButton.addActionListener(e -> {
			if (abcSong != null)
				abcSong.assignNumbersToSimilarPartTypes();
		});
		numerateButton.setToolTipText(UIText.get("maestro.tip.numerate"));

		JPanel partsButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, HGAP, VGAP));
		partsButtonPanel.add(newPartButton);
		partsButtonPanel.add(deletePartButton);
		partsButtonPanel.add(sortPartsButton);

		partsListPanel = new JPanel(new BorderLayout(HGAP, VGAP));
		partsListPanel.setBorder(BorderFactory.createTitledBorder(UIText.get("maestro.song.parts")));
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
				.setToolTipText(UIText.get("maestro.tip.transpose.semi.tones"));
		transposeSpinner.addChangeListener(e -> {
			if (abcSong != null && fireTransposeListeners)
                abcSong.setTranspose(getTranspose());
            refreshPreviewSequence(false);
		});

		tempoSpinner = new JSpinner(new SpinnerNumberModel(MidiConstants.DEFAULT_TEMPO_BPM /* value */, 8 /* min */,
				960 /* max */, 1 /* step */));
		tempoSpinner.setToolTipText(UIText.get("maestro.tip.tempo"));
		tempoSpinner.addChangeListener(e -> {
			if (abcSong != null) {
				if (fireTempoListeners) abcSong.setTempoBPM((Integer) tempoSpinner.getValue());

				abcSequencer.setTempoFactor(abcSong.getTempoFactor());

				refreshPreviewSequence(false);
			} else {
				abcSequencer.setTempoFactor(1.0f);
			}
		});

		resetTempoButton = new JButton(UIText.get("maestro.reset"));
		resetTempoButton.setMargin(new Insets(2, 8, 2, 8));
		resetTempoButton.setToolTipText(UIText.get("maestro.set.the.tempo.back.to.the.source.file.s.tempo"));
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

		timeSignatureField = new TimeSignatureTextField(TimeSignature.FOUR_FOUR, 5);
		timeSignatureField.setToolTipText(UIText.get("maestro.tip.time.signature"));
        // Tell the field to revert to the last valid value if the user enters invalid text
        timeSignatureField.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
		timeSignatureField.addPropertyChangeListener("value", evt -> {
            if (evt.getOldValue() != null && evt.getOldValue().equals(evt.getNewValue())) {
                return;
            }

			if (abcSong != null && fireMeterListeners)
				abcSong.setTimeSignature((TimeSignature) timeSignatureField.getValue());

            // Breaking up of long notes can depend on time signature for bar lines.
            refreshPreviewSequence(false);
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
		dynaCombo.setToolTipText(UIText.get("maestro.tip.dynamics", Chord.CalcDynamics.LOUDEST,Chord.CalcDynamics.POWER_RMS_DB,Chord.CalcDynamics.POWER_MID_DB,Chord.CalcDynamics.WEIGHTED,Chord.CalcDynamics.POWER_MID_DB,Chord.CalcDynamics.SOFTEST));

        tempoOnlyFirstCheckBox = new JCheckBox(UIText.get("maestro.only.tempo.changes.from.first.track"));
        tempoOnlyFirstCheckBox.setToolTipText(UIText.get("maestro.tip.tempo.first.track.only"));
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

		exportSuccessfulLabel = new JLabel(UIText.get("maestro.exported"));
		exportSuccessfulLabel.setIcon(IconLoader.getImageIcon("check_16.png"));
		exportSuccessfulLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
		exportSuccessfulLabel.setVisible(false);

		exportButton = new JButton(); // Label set in onSaveAndExportSettingsChanged()
		exportButton.setToolTipText(UIText.get("maestro.html.b.export.abc.b.br.ctrl.e.html"));
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
		settingsPanel.setBorder(BorderFactory.createTitledBorder(UIText.get("maestro.export.settings")));
		int row = 0;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(new JLabel(UIText.get("maestro.transpose")), "0, " + row);
		settingsPanel.add(transposeSpinner, "1, " + row);
		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(new JLabel(UIText.get("maestro.main.tempo")), "0, " + row);
		settingsPanel.add(tempoSpinner, "1, " + row);
		settingsPanel.add(resetTempoButton, "2, " + row + ", L, F");
		row++;
		settingsLayout.insertRow(row, PREFERRED);
		settingsPanel.add(new JLabel(UIText.get("maestro.meter")), "0, " + row);
		settingsPanel.add(timeSignatureField, "1, " + row + ", 2, " + row + ", L, F");
		if (SHOW_KEY_FIELD) {
			row++;
			settingsLayout.insertRow(row, PREFERRED);
			settingsPanel.add(new JLabel(UIText.get("maestro.key")), "0, " + row);
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
		arrangementView = new ArrangementView(sequencer, partAutoNumberer, abcSequencer, miscSettings.showMaxPolyphony, miscSettings.dissEnabled);
		arrangementView.setPoeticalLyricsAdvancement(saveSettings.countUpLyrics);
		arrangementView.setPoeticalLyricsTimestampEveryLine(saveSettings.lyricsTimestampEveryLine);
		arrangementView.addSettingsActionListener(e -> doSettingsDialog(SettingsDialog.NUMBERING_TAB));

		tuneEditorButton = new JButton();
		tuneEditorButton.setText(UIText.get("maestro.tune.editor"));
		tuneEditorButton
				.setToolTipText(UIText.get("maestro.tuneEdit.tip"));
		tuneEditorButton.addActionListener(e -> TuneEditor.show(ProjectFrame.this, abcSong));

		hideEditsCheckbox = new JCheckBox();
		hideEditsCheckbox.setText(UIText.get("maestro.hide.edits"));
		hideEditsCheckbox
				.setToolTipText(UIText.get("maestro.html.hide.edits.on.the.tracks.html"));
		hideEditsCheckbox.addActionListener(e -> abcSong.setHideEdits(hideEditsCheckbox.isSelected()));
		
		final Insets playControlButtonMargin = new Insets(5, 20, 5, 20);

		playButton = new JButton(playIcon);
		playButton.setDisabledIcon(playIconDisabled);
		playButton.setMargin(playControlButtonMargin);
		playButton.addActionListener(e -> updateSequencer());

		stopButton = new JButton(stopIcon);
		stopButton.setDisabledIcon(stopIconDisabled);
		stopButton.setToolTipText(UIText.get("maestro.stop"));
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

		midiModeRadioButton = new JRadioButton(UIText.get("maestro.original"));
		midiModeRadioButton.addActionListener(modeButtonListener);
		midiModeRadioButton.setMargin(new Insets(1, 5, 1, 5));

		abcModeRadioButton = new JRadioButton(UIText.get("maestro.abc.preview"));
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
		midiBarLabel.setToolTipText(UIText.get("maestro.original.bar.number"));

		abcBarLabel = new BarNumberLabel(abcSequencer, null, false,"0000/0000");
		abcBarLabel.setToolTipText(UIText.get("maestro.abc.preview.bar.number"));
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
		sidepanelButton.setToolTipText(UIText.get("maestro.tip.show.sidepanel"));
				
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
		playControlPanel.add(new JLabel(UIText.get("maestro.volume")), "alignx right");
		playControlPanel.add(volumeSlider);
		playControlPanel.add(sidepanelButton, "spany 2, center");
		playControlPanel.add(midiPositionLabel);
		playControlPanel.add(abcPositionLabel, "wrap");
		
		playControlPanel.add(hideEditsCheckbox);
		playControlPanel.add(abcModeRadioButton);
		//play
		//stop
		playControlPanel.add(new JLabel(UIText.get("maestro.stereo")), "alignx right");
		playControlPanel.add(stereoSlider);
		//note
		playControlPanel.add(midiBarLabel);
		playControlPanel.add(abcBarLabel);

		playControlPanel.add(feedLabel, "span 8, center");

		midiPartsAndControls = new JPanel(new BorderLayout(HGAP, VGAP));
		midiPartsAndControls.add(arrangementView, BorderLayout.CENTER);
		midiPartsAndControls.add(playControlPanel, BorderLayout.SOUTH);
		midiPartsAndControls.setBorder(BorderFactory.createTitledBorder(UIText.get("maestro.part.settings")));
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
				feedLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
			} else {
				String dismiss = feed.isEmpty()?"": UIText.get("maestro.click.to.dismiss");
				feedLabel.setText(feed + dismiss);
				if (!feed.isEmpty()) {
					feedLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.RED, 2), BorderFactory.createEmptyBorder(0, 2, 0, 2)));
				} else {
					feedLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
				}
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
		volumeTransceiver.setVolume(prefs.getInt("volumizer", defaultVolume));

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
		arrangementView.setLyricLines(null, false);
        arrangementView.setStats("");
		arrangementView.sidepanelVisible(false);

		super.dispose();
	}

	private void initMenu() {
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu fileMenu = menuBar.add(new JMenu(UIText.get("maestro.menu.file")));
		fileMenu.setMnemonic('F');

        openItem = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.open.file")));
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
                            new ExtensionFileFilter(UIText.get("maestro.project"),
                                    Util.MSX_FILE_EXTENSION_NO_DOT));
					openFileChooser.setFileFilter(
							new ExtensionFileFilter(UIText.get("maestro.midi.abc.and.project"),
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
		
		openRecentMenu = new JMenu(UIText.get("maestro.menu.open.recent.projects"));
		fileMenu.add(openRecentMenu);
		
		updateOpenRecentMenu();

		fileMenu.addSeparator();

		saveMenuItem = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.save.0", AbcSong.MSX_FILE_DESCRIPTION)));
		saveMenuItem.setIcon(IconLoader.getImageIcon("msxfile_16.png"));
		saveMenuItem.setDisabledIcon(IconLoader.getDisabledIcon("msxfile_16.png"));
		saveMenuItem.setMnemonic('S');
		saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_DOWN_MASK));
		saveMenuItem.addActionListener(e -> save());

		saveAsMenuItem = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.save.0.as", AbcSong.MSX_FILE_DESCRIPTION)));
		saveAsMenuItem.setMnemonic('A');
		saveAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_DOWN_MASK | SHIFT_DOWN_MASK));
		saveAsMenuItem.addActionListener(e -> saveAs());

		fileMenu.addSeparator();

		exportMenuItem = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.export.abc")));
		exportMenuItem.setIcon(IconLoader.getImageIcon("abcfile_16.png"));
		exportMenuItem.setDisabledIcon(IconLoader.getDisabledIcon("abcfile_16.png"));
		exportMenuItem.setMnemonic('E');
		exportMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, CTRL_DOWN_MASK));
		exportMenuItem.addActionListener(e -> exportAbc());

		exportAsMenuItem = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.export.abc.as")));
		exportAsMenuItem.setMnemonic('p');
		exportAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, CTRL_DOWN_MASK | SHIFT_DOWN_MASK));
		exportAsMenuItem.addActionListener(e -> exportAbcAs());

		fileMenu.addSeparator();

		saveExpandedMidiMenuItem = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.export.expanded.midi")));
		saveExpandedMidiMenuItem.addActionListener(e -> expandMidi());

		fileMenu.addSeparator();
		
		exportAudioMenu = new JMenu(UIText.get("maestro.menu.export.audio"));
		
		fileMenu.add(exportAudioMenu);
		
		exportMp3MenuItem = exportAudioMenu.add(new JMenuItem(UIText.get("maestro.menu.export.mp3.file")));
		exportMp3MenuItem.addActionListener(e -> {
			if (!abcSequencer.isLoaded() || abcSong == null || audioExporter.isExporting()) {
				Toolkit.getDefaultToolkit().beep();
                log.warning("Cannot export audio. abcSequencer.isLoaded()"+abcSequencer.isLoaded()+" abcSong != null "+(abcSong != null)+" audioExporter.isExporting()"+audioExporter.isExporting());
				return;
			}
			audioExporter.exportMp3Builtin(abcSequencer, getAbcExportFile(), abcSong.getTitle(), abcSong.getComposer());
		});

		exportWavMenuItem = exportAudioMenu.add(new JMenuItem(UIText.get("maestro.menu.export.wav.file")));
		exportWavMenuItem.addActionListener(e -> {
			if (!abcSequencer.isLoaded() || abcSong == null || audioExporter.isExporting()) {
				Toolkit.getDefaultToolkit().beep();
                log.warning("Cannot export audio. abcSequencer.isLoaded()"+abcSequencer.isLoaded()+" abcSong != null "+(abcSong != null)+" audioExporter.isExporting()"+audioExporter.isExporting());
				return;
			}
			refreshPreviewSequence(true);//important, so last edits gets written
			audioExporter.exportWav(abcSequencer, getAbcExportFile());
		});
		
		fileMenu.addSeparator();
		
		chooseMidiFileMenuItem = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.change.midi.file")));
		chooseMidiFileMenuItem.addActionListener(e -> {
			if (abcSong == null || abcSong.getSourceFile() == null || abcSong.getProjectFile() == null) {
				return; // should be an invalid state, item is disabled if no msx file
			}
			
			int result = JOptionPane.showConfirmDialog(ProjectFrame.this, UIText.get("maestro.would.you.like.to.continue"), UIText.get("maestro.proceed"),
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
					new ExtensionFileFilter(UIText.get("maestro.menu.midi.and.abc.files"), Util.MID_FILE_EXTENSION_NO_DOT,
							Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT, Util.ABC_FILE_EXTENSION_NO_DOT,
							Util.TXT_FILE_EXTENSION_NO_DOT));
            openMidiChooser.setAcceptAllFileFilterUsed(false);

			result = openMidiChooser.showOpenDialog(ProjectFrame.this);
			if (result != JFileChooser.APPROVE_OPTION) {
				return;
			}
			
			reloadWithNewSource(openMidiChooser.getSelectedFile());
		});
		
		reloadMidiFileMenuItem = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.reload.midi.file")));
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

		closeProject = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.close.project")));
		closeProject.addActionListener(e -> closeProjectForNormal());

		JMenuItem exitItem = fileMenu.add(new JMenuItem(UIText.get("maestro.menu.exit")));
		exitItem.setMnemonic('x');
		exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, ALT_DOWN_MASK));
		exitItem.addActionListener(e -> {
			if (closeProjectForShutdown()) {
				setVisible(false);
				dispose();
				System.exit(0);
			}
		});

		JMenu toolsMenu = menuBar.add(new JMenu(UIText.get("maestro.menu.tools")));
		toolsMenu.setMnemonic('T');

		JMenuItem settingsItem = toolsMenu.add(new JMenuItem(UIText.get("maestro.menu.options")));
		settingsItem.setIcon(IconLoader.getImageIcon("gear_16.png"));
		settingsItem.setDisabledIcon(IconLoader.getDisabledIcon("gear_16.png"));
		settingsItem.setMnemonic('O');
		settingsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, CTRL_DOWN_MASK));
		settingsItem.addActionListener(e -> doSettingsDialog());

		JMenuItem themeItem = toolsMenu.add(new JMenuItem(UIText.get("maestro.menu.colors")));
		themeItem.setMnemonic('C');
		themeItem.addActionListener(e -> showThemeEditor());

		toolsMenu.addSeparator();
		
		JMenuItem helpItem = toolsMenu.add(new JMenuItem(UIText.get("maestro.menu.help.opens.in.browser")));
		helpItem.setMnemonic('H');
		helpItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
		helpItem.addActionListener(e -> {
			Util.openURL(MaestroMain.WIKI_URL, this);
		});
		
		JMenuItem versionItem = toolsMenu.add(new JMenuItem(UIText.get("maestro.menu.check.for.updates")));
		versionItem.setMnemonic('V');
		versionItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
		versionItem.addActionListener(e -> {
			checkVersionCompare();
		});
		
		toolsMenu.addSeparator();

		JMenuItem aboutItem = toolsMenu.add(new JMenuItem(UIText.get("maestro.menu.about.0", MaestroMain.APP_NAME)));
		aboutItem.setMnemonic('A');
		aboutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
		aboutItem.addActionListener(e -> AboutDialog.show(ProjectFrame.this, MaestroMain.APP_NAME,
				MaestroMain.APP_VERSION, MaestroMain.WIKI_URL, "maestro_64.png"));
	}

	private void initTheme() {
		themeEditorDialog = new JDialog(this, "Color Editor", false); // false = non-modal

		ColorSelector selector = new ColorSelector(arrangementView);

		themeEditorDialog.setContentPane(selector);
		//themeEditorDialog.setSize(550, 700);
		themeEditorDialog.setMinimumSize(new Dimension(450, 400));

		themeEditorDialog.pack();
		themeEditorDialog.setLocationRelativeTo(this);
	}

	private void showThemeEditor() {
		if (!themeEditorDialog.isVisible()) {
			themeEditorDialog.setVisible(true);
		} else {
			themeEditorDialog.toFront();
		}
	}

	public void themeUiEnabled(boolean on) {
		if (themeEditorDialog != null) {
			Component glassPane = themeEditorDialog.getGlassPane();
			if (!on) {
				themeEditorDialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
				glassPane.addMouseListener(blocker);
				glassPane.setVisible(true);
			} else {
				glassPane.setVisible(false);
				glassPane.removeMouseListener(blocker);
				themeEditorDialog.setCursor(Cursor.getDefaultCursor());
			}
		}
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
		
		JMenuItem clearMenuItem = openRecentMenu.add(new JMenuItem(UIText.get("maestro.menu.clear.list")));
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
			exportMenuItem.setText(UIText.get("maestro.menu.export.abc.as"));
		} else {
			exportAsMenuItem.setVisible(true);
			exportMenuItem.setText(UIText.get("maestro.menu.export.abc"));
		}
		
		updateExportOrExportAsButton();

        boolean needRefresh = false;

		if (abcSong != null) {
            if (abcSong.isSkipSilenceAtStart() != saveSettings.skipSilenceAtStart
                    || abcSong.isDeleteMinimalNotes() != saveSettings.deleteMinimalNotes
                    || abcSong.isUseRestsInChords() != saveSettings.useRestsInChords
            || miscSettings.dissModified) {
                // we do it here instead of in the song listener,
                // so we don't get nested calls to refresh.
                needRefresh = true;
                miscSettings.dissModified = false;
            }
			abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
			abcSong.setDeleteMinimalNotes(saveSettings.deleteMinimalNotes);
            abcSong.setReducedFilesize(saveSettings.reducedFilesize);
            abcSong.setUseRestsInChords(saveSettings.useRestsInChords);
		}

		// if (abcSong != null)
		// abcSong.setShowPruned(saveSettings.showPruned);

		arrangementView.setPolyphony(miscSettings.showMaxPolyphony);
        arrangementView.setDissonanceEnabled(miscSettings.dissEnabled);
		arrangementView.setPoeticalLyricsAdvancement(saveSettings.countUpLyrics);
		arrangementView.setPoeticalLyricsTimestampEveryLine(saveSettings.lyricsTimestampEveryLine);
		if (abcSong != null) {
			abcSong.setBadger(miscSettings.showBadger);
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
		String exportText = shouldExportAbcAs() ? UIText.get("maestro.export.abc.as") : UIText.get("maestro.export.abc");
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
		return defaultVolume;
	}
	
	public void setPan(int pan) {
		if (pan != prefs.getInt("stereoPan", defaultStereo)) {
			prefs.putInt("stereoPan", pan);
			saveSettings.saveToPrefs();
            updateStereo();
		}
	}
	
	public int getPan() {
		return prefs.getInt("stereoPan", defaultStereo);
	}

    /**
     * Change stereo rendition
     *
     */
    private void updateStereo() {
        if (abcSequencer != null && abcSequencer.getSequence() != null && abcSong != null) {
            int panModifier = prefs.getInt("stereoPan", defaultStereo);
            Sequence seq = abcSequencer.getSequence();
            Track[] tracks = seq.getTracks();
            PanGenerator panner = new PanGenerator();
            // explicit copy it, just to be 100% sure it's a shallow copy:
			List<AbcPart> panSortedParts = new ArrayList<>(abcSong.getParts().size());
			panSortedParts.addAll(abcSong.getParts());
            abcSong.allPans = new ArrayList<>();
            panner.sortParts(panSortedParts, abcSong.allPans);
            for (AbcPart part : panSortedParts) {
                MidiEvent prevEvent = part.getPanEvent();
                if (prevEvent == null) continue;
                if (part.getPreviewSequenceTrackNumber() > tracks.length - 1) {
                    log.warning("updateStereo: sequence vs. part preview track-number mismatch. tracks="+tracks.length+" part="+part.getPreviewSequenceTrackNumber());
                    break;
                }
                Track track = tracks[part.getPreviewSequenceTrackNumber()];
                MidiEvent newPanEvent = MidiUtils.replacePanningEvent(track, part.getInstrument(), part.getTitle(), prevEvent, panModifier, part.getUserPan(), panner, part.getPartNumber());
                part.setPanEvent(newPanEvent);
                abcSequencer.injectPanEvent(newPanEvent);
            }
        }
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
						if (abcSequencer.getTickLength() < abcPreviewStartTick) {
							// I don't fully understand how this can happen, bug report here:
							// https://discord.com/channels/1127545258729803797/1132590018985201664/1465902468419551324
							log.severe("MainSequencerListener: tick-length mismatch.");
							abcPreviewStartTick = 0L;
						}
						abcSequencer.setTickPosition(Util.clamp(sequencer.getTickPosition(),
								Math.min(abcPreviewStartTick,abcSequencer.getTickLength()),
								abcSequencer.getTickLength()));
					} else if (evt.getProperty() == SequencerProperty.DRAG_POSITION) {
						if (abcSequencer.getTickLength() < abcPreviewStartTick) {
							log.severe("MainSequencerListener: tick-length mismatch.");
							abcPreviewStartTick = 0L;
						}
						abcSequencer.setDragTick(
								Util.clamp(sequencer.getDragTick(), Math.min(abcPreviewStartTick,abcSequencer.getTickLength()), abcSequencer.getTickLength()));
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

		private void updatePrefs(Document doc) {
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
		String errStr = UIText.get("maestro.html.p.style.color.red.must.save.as.an.msx.project.first.p.html");
		chooseMidiFileMenuItem.setEnabled(abcSong != null && abcSong.getProjectFile() != null && uiEnabled && sourceChangeEnabled);
		chooseMidiFileMenuItem.setToolTipText(abcSong != null && abcSong.getProjectFile() == null ? errStr : "");
		reloadMidiFileMenuItem.setEnabled(abcSong != null && abcSong.getProjectFile() != null && uiEnabled && sourceChangeEnabled);
		reloadMidiFileMenuItem.setToolTipText(abcSong != null && abcSong.getProjectFile() == null ? errStr : "");
        openRecentMenu.setEnabled(sourceChangeEnabled);
        openItem.setEnabled(sourceChangeEnabled);

		closeProject.setEnabled(midiLoaded && uiEnabled && sourceChangeEnabled);

		songInfoPanel.setEditingEnabled(midiLoaded && uiEnabled);
		songInfoPanel.setGenreAndMoodVisible(miscSettings.showBadger);

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
			midiModeRadioButton.setText(UIText.get("maestro.original.0", abcSong.getSequenceInfo().standard+(abcSong.getSequenceInfo().hasPorts?"+":"")));
		} else {
			midiModeRadioButton.setText(UIText.get("maestro.original"));
		}

//		double[] LAYOUT_COLS_DYN = new double[] { partsList.getFixedCellWidth() + 32, FILL };
		double[] LAYOUT_COLS_DYN = new double[] { 300 + 32, FILL };
		tableLayout.setColumn(LAYOUT_COLS_DYN);// This call is attempt of fix for no delete button on MacOS part 2

		String partListTitle = UIText.get("maestro.song.parts");
		if (abcSong != null) {
			partListTitle = UIText.get("maestro.0.count.1", partListTitle, abcSong.getActivePartCount());
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
            // so that the project doesn't get marked modified
            return;
        }

        if (e.getProperty() == AbcPartProperty.PREVIEW_TRACK_NUMBER) {
            // This is a runtime event only, so we return
            // so that the project doesn't get marked modified
            return;
        }

		if (e.getProperty() == AbcPartProperty.TRACK_ENABLED)
			updateButtons(false);

		if (e.getProperty() == AbcPartProperty.TITLE && arrangementView != null)
			arrangementView.setNewTitle(e.getSource());

        if (e.getProperty() == AbcPartProperty.PART_NUMBER_MANUAL)
            partAutoNumberer.renumberAllParts(abcSong.getParts());

        if (e.getProperty() == AbcPartProperty.USER_PAN) {
            updateStereo();
        }

		partsList.repaint();
		partEditor.repaint();

		setAbcSongModified(true);

		if (e.isAbcPreviewRelated()) {
            // must be immediate since song.parts can change in subsequent
            // part listeners and generate preview now runs on a different thread
            // update: since it now uses copy of abcsong, non-immediate is ok.
			refreshPreviewSequence(false);

			if (arrangementView != null) {
				arrangementView.repaint();
			}
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
			case COMPOSER:
			case TRANSCRIBER:
			case GENRE:
			case MOOD:
    			songInfoPanel.setSongInfo(getCurrentSongInfo());
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
				timingCombo.getModel().setSelectedItem(TimingEnum.getInstance(abcSong.isOrganic(), abcSong.isOrganic2(), abcSong.isMixTiming(), abcSong.isTripletTiming(), abcSong.isPriorityActive(), abcSong.isUpgraded()));

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
				modified = false;
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
					arrangementView.showInfoMessage(formatInfoMessage(UIText.get("maestro.add.a.part"), UIText.get("maestro.this.abc.song.has.no.parts.click.the.0.button.to.add.a.new.part", newPartButton.getText()),getHTMLFontSizeNormal()));
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
				refreshPreviewSequence(false);// autoPan depend on part order
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
				modified = false;
				break;
			case HIDE_EDITS_UPDATE:
				// Don't care
				modified = false;
				break;
			case USER_NOTE:
				break;
			case USER_LYRICS:
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
		return abcSong != null && abcSongModified;
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

	private enum CloseProjectMode {
    	NORMAL,
    	SHUTDOWN
	}

	/**
	 * closes the current project as part of the application shutdown process
	 * @return true if it was closed
	 */
	protected boolean closeProjectForShutdown() {
		return closeProject(CloseProjectMode.SHUTDOWN);
	}

	/**
	 * closes the current project as part of normal project closing,
	 * either to open another project or just to close the current project without opening another
	 * @return true if it was closed
	 */
	protected boolean closeProjectForNormal() {
		return closeProject(CloseProjectMode.NORMAL);
	}

	/**
	 * Close the current song, prompting to save if there are unsaved changes.
	 * Does not prompt to save if there is no current song, even if there are unsaved changes.
	 * 
	 * @return true if it was closed
	 */
	private boolean closeProject(CloseProjectMode mode) {
		boolean skipSequencerReset = mode == CloseProjectMode.SHUTDOWN;
		SectionEditor.clearClipboard();
		TrackPanel.clearDrumClipboard();
		sequencer.stop();
		abcSequencer.stop();

		boolean promptSave = isAbcSongModified() && (saveSettings.promptSaveNewSong || abcSong.getProjectFile() != null);
		if (promptSave) {
			String message;
			if (abcSong.getProjectFile() == null)
				message = UIText.get("maestro.do.you.want.to.save.this.new.song");
			else
				message = UIText.get("maestro.do.you.want.to.save.changes.to.0", abcSong.getProjectFile().getName());

			int result = JOptionPane.showConfirmDialog(this, message, UIText.get("maestro.save.changes"), JOptionPane.YES_NO_CANCEL_OPTION,
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
		arrangementView.setLyricLines(null, false);
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

		/*
		 * If we shut down we do not need to reset the sequencers as they get disposed in ProjectFrame.dispose()
		 * If we are just closing the project, we want to reset them so that they are fresh for the next project.
		 * Before the implementation of this if-Statement, SequencerWrapper.reset(boolean) caused a recreation of
		 * the sequencer only to have it immediately discarded in ProjectFrame.dispose()
		*/
		if (!skipSequencerReset)  {
			sequencer.reset(true);
			abcSequencer.reset(false);
		}

		abcSequencer.setTempoFactor(1.0f);
		abcPreviewStartTick = 0L;

		clearSongInfoPanel();

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
        if (!uiEnabled || (audioExporter != null && audioExporter.isExporting())) {
            log.warning("Cannot open file. uiEnabled="+uiEnabled+" audioExporter.isExporting()="+(audioExporter != null && audioExporter.isExporting()));
            Toolkit.getDefaultToolkit().beep();
            return;
        }

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
		if (!closeProjectForNormal()) {
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

			updateSongInfoFromAbcSong();
			setDyna(abcSong.dynamicsMethod);

            arrangementView.sidepanelTab(UIText.get("maestro.notes"));

            String lyrics = abcSong.getLyrics();
            if (lyrics.isBlank()) {
                lyrics = UIText.get("maestro.contains.no.lyrics");
            } else if (!abcSong.isFromXmlFile()) {
                arrangementView.sidepanelVisible(true);
                arrangementView.sidepanelTab(UIText.get("maestro.lyrics"));
            } else {
				arrangementView.sidepanelTab(UIText.get("maestro.lyrics"));
			}
            arrangementView.setLyrics(lyrics);
			if (abcSong.getLyricLines() != null) arrangementView.setLyricLines(abcSong.getLyricLines(), true);
			else arrangementView.setLyricLines(abcSong.getSequenceInfo().getDataCache().getLyricLines(), false);

            if (abcSong.isFromXmlFile()) {
                String note = abcSong.getNote();
                if (note != null && !note.equals(lyrics)) {
                	// the check for note==lyrics is to clear userNote if it's an old project and
                	// lyrics were saved unchanged in the note.
                    arrangementView.setTextnote(note);
                    if (!note.isEmpty()) {
                        arrangementView.sidepanelTab(UIText.get("maestro.notes"));
                        arrangementView.sidepanelVisible(true);
                    }
                }
            }

			setTranspose(abcSong.getTranspose());
			setTempo(abcSong.getTempoBPM());
			keySignatureField.setValue(abcSong.getKeySignature());
			setMeter(abcSong.getTimeSignature());

            // setting on model dont fire action listener
            timingCombo.getModel().setSelectedItem(TimingEnum.getInstance(abcSong.isOrganic(),abcSong.isOrganic2(),abcSong.isMixTiming(),abcSong.isTripletTiming(),abcSong.isPriorityActive(), abcSong.isUpgraded()));

            tempoOnlyFirstCheckBox.setSelected(abcSong.isUsingOldTempos());

			SequenceInfo sequenceInfo = abcSong.getSequenceInfo();
			sequencer.setSequence(sequenceInfo.getSequence());
			sendMIDIResets(sequenceInfo.standard);
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
					// Uncomment this line to preview lots of midis and skipping their intro:
					//sequencer.setTickPosition(sequencer.getTickLength()/4L);
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

			arrangementView.showInfoMessage(formatErrorMessage(UIText.get("maestro.could.not.open.0", file.getName()), message, getHTMLFontSizeNormal()));
			midiResolved = false;
		} catch (InvalidMidiDataException | IOException | FileParseException | SAXException e) {
			arrangementView.showInfoMessage(formatErrorMessage(UIText.get("maestro.could.not.open.0", file.getName()), e.getMessage(), getHTMLFontSizeNormal()));
			midiResolved = false;
		}
		
		// Don't update last opened list when reading tmp msx file for midi reloading
		if (updateLastOpenedList && file.getAbsolutePath().endsWith(Util.MSX_FILE_EXTENSION)) {
			recentlyOpenedList.addOpenedFile(file);
			updateOpenRecentMenu();
		}
		inOpenFile = false;
	}

	private void sendMIDIResets(MidiStandard standard) {
		volumeTransceiver.setStandard(standard);
		if (sequencer.isDefault() || standard == MidiStandard.ABC) {
			return;
		}
		try {

			volumeTransceiver.send(MidiFactory.createGMReset(), -1);
			Thread.sleep(100);
			//volumeTransceiver.send(MidiFactory.createGSReset(), -1);
			//Thread.sleep(200);
			switch (standard) {
				case GM2:
					volumeTransceiver.send(MidiFactory.createGM2Reset(), -1);
					Thread.sleep(100);
					break;
				case GS:
					volumeTransceiver.send(MidiFactory.createGSReset(), -1);
					Thread.sleep(100);
					break;
				case XG:
					volumeTransceiver.send(MidiFactory.createXGReset(), -1);
					Thread.sleep(100);
					break;
				case GM:
					//volumeTransceiver.send(MidiFactory.createGMReset(), -1);
					//Thread.sleep(100);
					break;
				default:
					break;
			}

			/*
			volumeTransceiver.send(MidiFactory.createNoteOnEventEx(64,0,100,0).getMessage(), -1);
			Thread.sleep(150);
			volumeTransceiver.send(MidiFactory.createNoteOffEventEx(64,0,0,0).getMessage(), -1);
			Thread.sleep(50);
			volumeTransceiver.send(MidiFactory.createNoteOnEventEx(64,0,100,0).getMessage(), -1);
			Thread.sleep(150);
			volumeTransceiver.send(MidiFactory.createNoteOffEventEx(64,0,0,0).getMessage(), -1);
			System.out.println("MIDI sent");
			Thread.sleep(200);
			*/
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
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
			message += UIText.get("maestro.would.you.like.to.try.to.locate.the.file");
			return resolveHelper(original, message);
		}

		@Override
		public File resolveFile(File original, String message) {
			message += UIText.get("maestro.would.you.like.to.pick.a.different.file");
			return resolveHelper(original, message);
		}

		private File resolveHelper(File original, String message) {
			int result = JOptionPane.showConfirmDialog(ProjectFrame.this, message, UIText.get("maestro.failed.to.open.file"),
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
				jfc.setFileFilter(new ExtensionFileFilter(UIText.get("maestro.midi.and.abc.files"), Util.MID_FILE_EXTENSION_NO_DOT,
						Util.MIDI_FILE_EXTENSION_NO_DOT, Util.KAR_FILE_EXTENSION_NO_DOT, Util.ABC_FILE_EXTENSION_NO_DOT,
						Util.TXT_FILE_EXTENSION_NO_DOT));
                jfc.setAcceptAllFileFilterUsed(false);
				jfc.setDialogTitle(UIText.get("maestro.open.missing.midi.abc"));
				if (original != null)
					jfc.setSelectedFile(original);

				if (jfc.showOpenDialog(ProjectFrame.this) == JFileChooser.APPROVE_OPTION)
					alternateFile = jfc.getSelectedFile();
			}

			return alternateFile;
		}
	};

	private static String formatInfoMessage(String title, String message, int fontSize) {
		int fontSizeHeader = Math.min(7, fontSize + 2);
		return "<html><font size='"+fontSizeHeader+"' color=\"" + ColorTable.CENTER_TEXT.getHtml()+ "\"><b>" + Util.htmlEscape(title)
				+ "<br></b></font><font size='"+fontSize+"' color=\"" + ColorTable.CENTER_TEXT.getHtml()+ "\">"
				+ Util.htmlEscape(message).replace("\n", "<br>")
				+ "</font><h3>&nbsp;</h3></html>";
	}

	private static String formatErrorMessage(String title, String message, int fontSize) {
		int fontSizeHeader = Math.min(7, fontSize + 2);
		return "<html><font size='"+fontSizeHeader+"' color=\"" + ColorTable.CENTER_TEXT_ERROR.getHtml() + "\"><b>" + Util.htmlEscape(title)
				+ "<br></b></font><font size='"+fontSize+"' color=\"" + ColorTable.CENTER_TEXT.getHtml()+ "\">"
				+ Util.htmlEscape(message).replace("\n", "<br>")
				+ "</font><h3>&nbsp;</h3></html>";
	}

	private int getHTMLFontSizeNormal() {
		return switch ((Integer)miscSettings.fontSize) {
			case Integer i when i < 7  -> 1; // approx 8pt
			case Integer i when i < 9  -> 2; // approx 10pt
			case Integer i when i < 11 -> 3; // approx 12pt (Standard)
			case Integer i when i < 13 -> 4; // approx 14pt
			case Integer i when i < 15 -> 5; // approx 18pt
			case Integer i when i < 19 -> 6; // approx 24pt
			default                    -> 7; // 36pt+
        };
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

			if (!newAbcPreviewMode && abcSong != null) {
				sendMIDIResets(abcSong.getSequenceInfo().standard);
			}

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
                JOptionPane.showMessageDialog(ProjectFrame.this, cause.getMessage(), UIText.get("maestro.error.previewing.abc"),
                        JOptionPane.WARNING_MESSAGE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable e) {
                log.log(Level.WARNING, "Error applying preview", e);
                Throwable cause = e.getCause() != null ? e.getCause() : e;

                sequencer.stop();
                abcSequencer.stop();
                JOptionPane.showMessageDialog(ProjectFrame.this, cause.getMessage(), UIText.get("maestro.error.previewing.abc"),
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
                p.numberOfRemovedNotesFromPruning = 0;
                p.numberOfRemovedNotesZeros = 0;
                p.numberOfRemovedNotesFromFitting = 0;
                p.setPanEvent(null);
                p.setMaxPoly(0);

                // Resetting to -1 is important, else when part A
                // disables all its tracks, it still keeps its preview-track
                // number from previous preview. Another part B might have
                // gotten the same preview track number in the meantime,
                // so when part A toggles mute or solo, it will affect
                // the wrong part.
                p.setPreviewSequenceTrackNumber(-1);
            }
            if (previewSequenceInfo.getLastTrackInfos() != null) {
                //System.out.println("\nApply preview:");
                for (AbcExporter.ExportTrackInfo trackInfo : previewSequenceInfo.getLastTrackInfos()) {
                    //threadsafe to do it here
                    trackInfo.part.setPreviewSequenceTrackNumber(trackInfo.trackNumber);
                    trackInfo.part.numberOfExportedNotes = trackInfo.numberOfExportedNotes;
                    trackInfo.part.numberOfRemovedNotesForSafety = trackInfo.numberOfRemovedNotesForSafety;
                    trackInfo.part.numberOfRemovedNotesFromPruning = trackInfo.numberOfRemovedNotesFromPruning;
                    trackInfo.part.numberOfRemovedNotesZeros = trackInfo.numberOfRemovedNotesZeros;
                    trackInfo.part.numberOfRemovedNotesFromFitting = trackInfo.numberOfRemovedNotesFromFitting;
                    trackInfo.part.setMaxPoly(trackInfo.maxPoly);
                    trackInfo.part.setPanEvent(trackInfo.panEvent);
					trackInfo.seqInfo = previewSequenceInfo;
                    //System.out.println(trackInfo);
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
            if (previewSequenceInfo.dissonance != null) previewSequenceInfo.dissonance.setSequencer(abcSequencer);
            arrangementView.setHistogram(previewSequenceInfo.histogram);
            arrangementView.setDissonance(previewSequenceInfo.dissonance);
            histogram = previewSequenceInfo.histogram;
            updateStereo();// we call this here to benefit PanVisualizerPanel
        } catch (InvalidMidiDataException e) {
            log.log(Level.WARNING, "Error after exporting preview", e);
            sequencer.stop();
            abcSequencer.stop();
            arrangementView.setHistogram(null);
            arrangementView.setDissonance(null);
            histogram = null;
            JOptionPane.showMessageDialog(ProjectFrame.this, e.getMessage(), UIText.get("maestro.error.previewing.abc"),
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
            arrangementView.setDissonance(new DissonanceDetector(null));
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
                previewWorker = new PreviewExportWorker(abcSong, !failedToLoadLotroInstruments, false, prefs.getInt("stereoPan", defaultStereo));
                setSourceChangeEnabled(false);
                previewWorker.execute();
            } catch (AbcConversionException e) {
                log.log(Level.WARNING, "Error exporting preview", e);
                sequencer.stop();
                abcSequencer.stop();
                JOptionPane.showMessageDialog(ProjectFrame.this, e.getMessage(), UIText.get("maestro.error.previewing.abc"),
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
            exporter.stereoPan = prefs.getInt("stereoPan", defaultStereo);
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
            JOptionPane.showMessageDialog(ProjectFrame.this, cause.getMessage(), UIText.get("maestro.error.previewing.abc"),
                    JOptionPane.WARNING_MESSAGE);
        }
        setSourceChangeEnabled(true);
        return false;
    }

	private void commitAllFields() {
		try {
			abcSong.setNote(arrangementView.getTextnote(), false);
			if (arrangementView.isLyricsModified()) abcSong.setLyricLines(arrangementView.getLyricLines(), false);
			else abcSong.setLyricLines(null, false);
			arrangementView.commitAllFields();
			transposeSpinner.commitEdit();
			tempoSpinner.commitEdit();
			timeSignatureField.commitEdit();
			keySignatureField.commitEdit();
		} catch (ParseException ignore) {
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
		tempNote += "Source song:\n" + abcSong.getSourceFile().getAbsolutePath()+"\n\n";
		tempNote += getTimingStats();
		tempNote += checkDuplicatePartTitles();
		tempNote += getNumberOfExportNotes();
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
        tempNote += "\nMain export tempo will be " + getTempo() + ".\n"
                + (AbcConstants.isStrangeBPM(getTempo())?(
                "Recommendation: To ease output of fractions"
                +" without repeating decimals, Maestro recommend"
                +" to decrease the tempo to "
                +(AbcConstants.isStrangeBPM(getTempo()-1)?getTempo()-2:getTempo()-1)):"");
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

	@SuppressWarnings("HardCodedStringLiteral")
	private String getNumberOfExportNotes() {
        StringBuilder out = new StringBuilder();
        int songNotes = 0;
        for (AbcPart part : abcSong.getParts()) {
            out.append("Part #").append(part.getPartNumber()).append(" will export ").append(part.numberOfExportedNotes).append(" notes.\n");
            songNotes += part.numberOfExportedNotes;
        }
        out.append("\nSong").append(" will export ").append(songNotes).append(" notes.\n");
        if ((saveSettings.deleteMinimalNotes && !abcSong.isOrganic()) || (abcSong.isOrganic() && abcSong.isOrganic2())) {
            out.append("\n");
            if (abcSong.isOrganic()) out.append("Delete short notes that became dissonant due to fitting:\n");
            else out.append("Delete minimal (very short) notes that due to slight fitting might produce undesired dissonance:\n");
            boolean active = false;
            for (AbcPart part : abcSong.getParts()) {
                if (part.numberOfRemovedNotesForSafety > 0) {
                    out.append("Part #").append(part.getPartNumber()).append(" removed ").append(part.numberOfRemovedNotesForSafety).append("\n");
                    active = true;
                }
            }
            if (!active) {
                out.append(" None.\n");
            }
        }
        out.append("\nDeletion of notes (or partial notes) that start at same time and exceeded max poly for a part:\n");
        int sum = 0;
        for (AbcPart part : abcSong.getParts()) {
            if (part.numberOfRemovedNotesFromPruning > 0) {
                sum += part.numberOfRemovedNotesFromPruning;
                out.append("Part #").append(part.getPartNumber()).append(" removed ").append(part.numberOfRemovedNotesFromPruning).append("\n");
            }
        }
        if (sum == 0) out.append("None.\n");
        if (abcSong.isOrganic()) {
            out.append("\nDeletion of notes (or partial notes) that didn't fit into timeline, considering lotro constraints:\n");
            sum = 0;
            for (AbcPart part : abcSong.getParts()) {
                if (part.numberOfRemovedNotesFromFitting > 0) {
                    sum += part.numberOfRemovedNotesFromFitting;
                    out.append("Part #").append(part.getPartNumber()).append(" removed ").append(part.numberOfRemovedNotesFromFitting).append("\n");
                }
            }
            if (sum == 0) out.append("None.\n");
        }
        if (abcSong.isOrganic() && !abcSong.isOrganic2()) {
            out.append("\nDeletion of notes that had zero duration in source midi:\n");
            sum = 0;
            for (AbcPart part : abcSong.getParts()) {
                if (part.numberOfRemovedNotesZeros > 0) {
                    sum += part.numberOfRemovedNotesZeros;
                    out.append("Part #").append(part.getPartNumber()).append(" removed ").append(part.numberOfRemovedNotesZeros).append("\n");
                }
            }
            if (sum == 0) out.append("None.\n");
        }
        out.append("\nBeside the above deletions, export counts can differ due to the way bent notes are subdivided," +
                " how arpeggios are handled and how long notes are being interrupted. Also note that some deletions are of partial notes.\n");
		out.append("\n");
		return out.toString();
	}

    private String getPoly6plusStats() {
        StringBuilder out = new StringBuilder();
        if (abcSong != null && abcSong.isOrganic() && abcSong.isUseRestsInChords()) {
            // expensive to compute, so we only do it for poly 6+
            out.append("\n");
            out.append(UIText.get("maestro.part.polyphony"));
            for (AbcPart part : abcSong.getParts()) {
                out.append(UIText.get("maestro.partnumber")).append(part.getPartNumber()).append(UIText.get("maestro.has.max"));
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
				out.append(UIText.get("maestro.partnumber")).append(part.getPartNumber()).append(UIText.get("maestro.has.no.assigned.tracks"));
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
					out.append(UIText.get("maestro.warning.part")).append(part1.getPartNumber()).append(UIText.get("maestro.and.part")).append(part2.getPartNumber()).append(UIText.get("maestro.has.same.title")).append(part1.getTitle()).append("\n\n");
				}
			}
		}
		return out.toString();
	}

	private File doSaveDialog(File defaultFile, File allowOverwriteFile, String extension, FileFilter fileFilter) {
		JFileChooser jfc = new JFileChooser();
		jfc.setFileFilter(fileFilter);
		jfc.setSelectedFile(defaultFile);

		jfc.setDialogTitle(UIText.get("maestro.save.to.0", Util.ellipsis(jfc.getCurrentDirectory().getAbsolutePath(), 64)));
		jfc.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, new PropertyChangeListener() {
			@Override
			public void propertyChange(PropertyChangeEvent evt) {
				File currentDir = (File) evt.getNewValue();
				if (currentDir != null) {
					jfc.setDialogTitle(UIText.get("maestro.save.to.0", Util.ellipsis(jfc.getCurrentDirectory().getAbsolutePath(), 64)));
				}
			}
		});

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
						UIText.get("maestro.file.0.already.exists.do.you.want.to.replace.it", fileName),
						UIText.get("maestro.confirm.replace.file"), JOptionPane.YES_NO_CANCEL_OPTION);
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
			JOptionPane.showMessageDialog(this, UIText.get("maestro.no.abc.song.is.open"), UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

        if (!confirmExportDespiteWarnings()) {
            return false;
        }
		
		File exportFile = getAbcExportFile();
		File allowOverwriteFile = allowOverwriteExportFile ? abcSong.getExportFile() : null;

		exportFile = doSaveDialog(exportFile, allowOverwriteFile, Util.ABC_FILE_EXTENSION,
				new ExtensionFileFilter(UIText.get("maestro.abc.files.abc.txt"), Util.ABC_FILE_EXTENSION_NO_DOT, Util.TXT_FILE_EXTENSION_NO_DOT));

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
			JOptionPane.showMessageDialog(this, UIText.get("maestro.no.abc.song.is.open"), UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
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
            int option = JOptionPane.showConfirmDialog(this, UIText.get("maestro.0.do.you.want.to.proceed.with.exporting.without.fixing.it", warning), UIText.get("maestro.warning"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
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
                exportSuccessfulLabel.setToolTipText(UIText.get("maestro.exported.0", abcSong.getExportFile().getName()));
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
                    JOptionPane.showMessageDialog(ProjectFrame.this, UIText.get("maestro.failed.to.create.file.0", cause.getMessage()),
							UIText.get("maestro.failed.to.create.file"), JOptionPane.ERROR_MESSAGE);
                } else if (cause instanceof IOException || cause instanceof AbcConversionException) {
                    JOptionPane.showMessageDialog(ProjectFrame.this, cause.getMessage(), UIText.get("maestro.error"),
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    // Catch any other unexpected errors
                    JOptionPane.showMessageDialog(ProjectFrame.this, UIText.get("maestro.an.unexpected.error.occurred.0", cause.getMessage()),
							UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
                }

            } catch (Exception e) {
                log.log(Level.SEVERE, "Error exporting ABC", e);
                JOptionPane.showMessageDialog(ProjectFrame.this, UIText.get("maestro.an.unexpected.ui.error.occurred.0", e.getMessage()),
						UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
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
		themeUiEnabled(on);
    }

	private boolean saveAs() {
		if (abcSong == null) {
			JOptionPane.showMessageDialog(this, UIText.get("maestro.no.abc.song.is.open"), UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
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
			JOptionPane.showMessageDialog(this, UIText.get("maestro.no.abc.song.is.open"), UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
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
			JOptionPane.showMessageDialog(this, UIText.get("maestro.failed.to.create.file.0", e.getMessage()), UIText.get("maestro.failed.to.create.file"),
					JOptionPane.ERROR_MESSAGE);

			return false;
		} catch (IOException | TransformerException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
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
			JOptionPane.showMessageDialog(this, UIText.get("maestro.no.midi.loaded"), UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (abcSong.getSequenceInfo().standard == MidiStandard.ABC) {
			JOptionPane.showMessageDialog(this, UIText.get("maestro.cannot.expand.abc.song"), UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (abcSong.getSourceFile().getName().startsWith("expanded_")) {
			JOptionPane.showMessageDialog(this, UIText.get("maestro.this.midi.has.already.been.expanded"), UIText.get("maestro.error"),
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

        saveFile = doSaveDialog(saveFile, saveFile, Util.MID_FILE_EXTENSION, new ExtensionFileFilter(UIText.get("maestro.midi.songs.mid"), Util.MID_FILE_EXTENSION_NO_DOT));

		if (saveFile == null)
			return false;

		return finishExpand(saveFile);
	}

	private boolean finishExpand(File saveFile) {
		try {
			Sequence sequence2 = abcSong.getSequenceInfo().split();
			if (sequence2 == null) {
				JOptionPane.showMessageDialog(this, UIText.get("maestro.something.went.wrong.in.the.splitting.process"), UIText.get("maestro.error"),
						JOptionPane.ERROR_MESSAGE);
				return false;
			}
			int[] types = MidiSystem.getMidiFileTypes(sequence2);
			if (types.length != 0) {
				log.info("Writing type " + types[types.length - 1] + " expanded midi as '"
						+ saveFile.getAbsolutePath() + "'");
				MidiSystem.write(sequence2, types[types.length - 1], saveFile);
			} else {
				JOptionPane.showMessageDialog(this, UIText.get("maestro.something.went.wrong.when.in.midi.type.handling"), UIText.get("maestro.error"),
						JOptionPane.ERROR_MESSAGE);
				return false;
			}
		} catch (FileNotFoundException e) {
			log.warning(e.getMessage());
			JOptionPane.showMessageDialog(this, UIText.get("maestro.failed.to.create.file.0", e.getMessage()), UIText.get("maestro.failed.to.create.file"),
					JOptionPane.ERROR_MESSAGE);

			return false;
		} catch (InvalidMidiDataException | IOException | FileParseException e) {
			log.severe(e.getMessage());
			JOptionPane.showMessageDialog(this, e.getMessage(), UIText.get("maestro.error"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		int result = JOptionPane.showConfirmDialog(this, UIText.get("maestro.would.you.also.like.to.load.the.new.expanded.midi"),
				UIText.get("maestro.expanded.midi"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

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

	/**
	 * Checks if there is a new version of Maestro available on github, and if so, prompts the user to download it.
	 * To avoid spamming github with requests, it only does this check if there isn't already a check in progress, or if the previous check is finished.
	 */
    private void checkVersionCompare() {
        if (future == null || future.isDone()) {
            future = CompletableFuture.runAsync(() -> {
                try {
                    try (var client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(4))
                            .build()) {

                        var request = HttpRequest.newBuilder()
                                .uri(new URI("https://raw.githubusercontent.com/NikolaiVChr/mver/refs/heads/main/main"))
                                .timeout(Duration.ofSeconds(10))
                                .GET()
                                .build();

                        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                            String line = response.body().lines().findFirst().orElse("");
                            latestVer = Version.parseVersion(line);
                            Version myVersion = MaestroMain.APP_VERSION;
                            if (latestVer != null && myVersion.compareTo(latestVer) < 0) {
                                SwingUtilities.invokeLater(() -> {
                                        int result = JOptionPane.showConfirmDialog(ProjectFrame.this, UIText.get("maestro.version.0.is.available.do.you.want.to.close.and.download.it", latestVer), UIText.get("maestro.version.check"),
                                                JOptionPane.YES_NO_OPTION);
                                        if (result == JOptionPane.YES_OPTION) {
                                            URI uriDownload;
                                            try {
                                                uriDownload = new URI(MaestroMain.DOWNLOAD_URL);
                                                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
													// Attempt to open the download URL in the user's default browser
													// If the project can be closed successfully, open the browser and exit the application
                                                    if (closeProjectForShutdown()) {
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
                            }
                        } else {
                            log.warning("Failed to read current version string from HTTPS. Response: " + response.statusCode());
                        }
                    }
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to connect to github for version check", e);
                }
            });
        }
    }

    public enum TimingEnum {
        ORGANIC_MULTISTAGE2 (UIText.get("maestro.timing.organic.multi.stage.2"),true, true, false,false,false,"Organic Multistage 2", true),
        ORGANIC_MULTISTAGE (UIText.get("maestro.timing.organic.multi.stage"),true, true, false,false,false,"Organic Multistage", false),
        ORGANIC_SINGLESTAGE (UIText.get("maestro.timing.organic.single.stage"), true, false, false,false,false,"Organic Singlestage", false),
        MIX (UIText.get("maestro.timing.mix.timings"), false, false, true,false,false,"Mix Timings", false),
        MIX_SWING (UIText.get("maestro.timing.mix.timings.swing"), false, false, true,true,false,"Mix Timings Swing/Triplet", false),
        MIX_PRIO (UIText.get("maestro.timing.mix.timings.combine.priorities"), false, false, true,false,true,"Mix Timings Combine Priorities", false),
        MIX_SWING_PRIO (UIText.get("maestro.timing.mix.timings.swing.combine.priorities"), false, false, true,true,true,"Mix Timings Swing/Triplet Combine Priorities", false),
        LEGACY (UIText.get("maestro.timing.legacy.timings"), false, false, false,false,false,"Legacy", false),
        LEGACY_SWING (UIText.get("maestro.timing.legacy.timings.swing"), false, false, false,true,false,"Legacy Swing/Triplet", false),
        ;

        public final boolean organic;
        public final boolean multistage;
        public final boolean mixTimings;
        public final boolean swing;
        public final boolean priority;
        public final String info;
        public final String settingsString;// use this for settings prefs. And never change the strings.
        public final boolean upgraded;

        TimingEnum(String info, boolean organic, boolean multistage, boolean mixTimings, boolean swing, boolean priority, @NonNls String settings, boolean upgraded) {
            this.info = info;
            this.organic = organic;
            this.multistage = multistage;
            this.mixTimings = mixTimings;
            this.swing = swing;
            this.priority = priority;
            this.settingsString = settings;
            this.upgraded = upgraded;
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
                abcSong.setTimings(organic, multistage, mixTimings, swing, priority, upgraded);
            }
        }

        String getTooltip() {
            return switch (this) {
                case ORGANIC_MULTISTAGE2 -> UIText.get("maestro.tip.multi2");
                case ORGANIC_MULTISTAGE -> UIText.get("maestro.tip.multi1");
                case ORGANIC_SINGLESTAGE -> UIText.get("maestro.tip.single");
                case LEGACY -> UIText.get("maestro.tip.legacy");
                case LEGACY_SWING -> UIText.get("maestro.tip.legacy.swing");
                case MIX_SWING_PRIO -> UIText.get("maestro.tip.mix.swing.prio");
                case MIX -> UIText.get("maestro.tip.mix");
                case MIX_SWING -> UIText.get("maestro.tip.mix.swing");
                case MIX_PRIO -> UIText.get("maestro.tip.mix.prio");
                default -> null;
            };
        }

        static TimingEnum getInstance(boolean organic, boolean multistage, boolean mixTimings, boolean swing, boolean priority, boolean upgraded) {
            if (organic) {
                if (multistage) {
                    if (upgraded) return ORGANIC_MULTISTAGE2;
                    return ORGANIC_MULTISTAGE;
                }
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
