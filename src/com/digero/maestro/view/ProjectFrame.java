package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
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

import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.StringCleaner;
import com.digero.common.icons.IconLoader;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.midi.MidiConstants;
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
import com.digero.common.view.BarNumberLabel;
import com.digero.common.view.ColorTable;
import com.digero.common.view.NativeVolumeBar;
import com.digero.common.view.SongPositionLabel;
import com.digero.common.view.StereoBar;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.AbcConversionException;
import com.digero.maestro.abc.AbcExporter;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcPartMetadataSource;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;
import com.digero.maestro.abc.ExportFilenameTemplate;
import com.digero.maestro.abc.PartAutoNumberer;
import com.digero.maestro.abc.PartNameTemplate;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.util.FileResolver;
import com.digero.maestro.util.XmlUtil;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

@SuppressWarnings("serial")
public class ProjectFrame extends JFrame implements TableLayoutConstants, ICompileConstants
{
	private static final int HGAP = 4, VGAP = 4;
	private static final double[] LAYOUT_COLS = new double[] { 180, FILL };
	private static final double[] LAYOUT_ROWS = new double[] { FILL };
	private static TableLayout tableLayout = new TableLayout(LAYOUT_COLS,LAYOUT_ROWS);

	private Preferences prefs = Preferences.userNodeForPackage(MaestroMain.class);

	private AbcSong abcSong;
	private boolean abcSongModified = false;

	private boolean allowOverwriteSaveFile = false;
	private boolean allowOverwriteExportFile = false;
	private NoteFilterSequencerWrapper sequencer;
	private VolumeTransceiver volumeTransceiver;
	private NoteFilterSequencerWrapper abcSequencer;
	private VolumeTransceiver abcVolumeTransceiver;
	private PartAutoNumberer partAutoNumberer;
	private PartNameTemplate partNameTemplate;
	private ExportFilenameTemplate exportFilenameTemplate;
	private SaveAndExportSettings saveSettings;
	private MiscSettings miscSettings;
	private boolean usingNativeVolume;

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
	private JMenuItem saveMenuItem;
	private JMenuItem saveAsMenuItem;
	private JMenuItem exportMenuItem;
	private JMenuItem exportAsMenuItem;
	private JMenuItem saveExpandedMidiMenuItem;
	private JMenuItem closeProject;

	private JPanel partsListPanel;
	private JList<AbcPartMetadataSource> partsList;
	private JButton newPartButton;
	private JButton deletePartButton;
	private JButton delayButton;
	private JButton numerateButton;

	private PartPanel partPanel;

	private JButton tuneEditorButton;
	static boolean abcPreviewMode = false;
	private JToggleButton abcModeRadioButton;
	private JToggleButton midiModeRadioButton;
	private JButton playButton;
	private JButton stopButton;
	private NativeVolumeBar volumeBar;
	private StereoBar stereoBar;
	private SongPositionLabel midiPositionLabel;
	private SongPositionLabel abcPositionLabel;
	private BarNumberLabel midiBarLabel;
	private BarNumberLabel abcBarLabel;

	private Icon playIcon;
	private Icon playIconDisabled;
	private Icon pauseIcon;
	private Icon pauseIconDisabled;
	private Icon abcPlayIcon;
	private Icon abcPlayIconDisabled;
	private Icon abcPauseIcon;
	private Icon abcPauseIconDisabled;

	private long abcPreviewStartTick = 0;
	private float abcPreviewTempoFactor = 1.0f;
	private boolean echoingPosition = false;

	private MainSequencerListener mainSequencerListener;
	private AbcSequencerListener abcSequencerListener;
	private boolean failedToLoadLotroInstruments = false;
	private JButton zoom = new JButton("Zoom");
	private JButton noteButton = new JButton("Note");
	private JLabel noteCountLabel = new JLabel();
	private int maxNoteCount = 0;
	private int maxNoteCountTotal = 0;
	private boolean midiResolved = false;
	/*private static Color BRIGHT_RED = new Color(255, 0, 0);
	private static Color ORANGE = new Color(235, 150, 64);
	private static Color BLACK = new Color(0, 0, 0);*/

	public ProjectFrame()
	{
		super(MaestroMain.APP_NAME);
		if ("32".equals(System.getProperty("sun.arch.data.model"))) {
			JOptionPane.showMessageDialog(null, "You are running with 32 bit Java.\nPlease start with 64 bit Java instead,\nto ensure Maestro do not out of memory.\n", "32 bit detected",
					JOptionPane.ERROR_MESSAGE);
			System.err.println("You are running with 32 bit Java.\nPlease start with 64 bit Java instead.\n Find Configure Java program in Start menu and\n configure it to start the 64 bit per default.\n\n");
			//System.exit(1);
			//return;
		}
		setMinimumSize(new Dimension(512, 384));
		Util.initWinBounds(this, prefs.node("window"), 800, 600);

		ToolTipManager.sharedInstance().setDismissDelay(8000);
		
		InputMap im = (InputMap)UIManager.get("Button.focusInputMap");
		if (im != null)
		{
			im.put(KeyStroke.getKeyStroke("pressed SPACE"), "none");
			im.put(KeyStroke.getKeyStroke("released SPACE"), "none");
		}
		
		im = (InputMap)UIManager.get("CheckBox.focusInputMap");
		if (im != null)
		{
			im.put(KeyStroke.getKeyStroke("pressed SPACE"), "none");
			im.put(KeyStroke.getKeyStroke("released SPACE"), "none");
		}
		

		String welcomeMessage = formatInfoMessage("Hello Maestro", "Drag and drop a MIDI or ABC file to open it.\n"
				+ "Or use File > Open.");

		partAutoNumberer = new PartAutoNumberer(prefs.node("partAutoNumberer"));
		partNameTemplate = new PartNameTemplate(prefs.node("partNameTemplate"));
		exportFilenameTemplate = new ExportFilenameTemplate(prefs.node("exportFilenameTemplate"));
		saveSettings = new SaveAndExportSettings(prefs.node("saveAndExportSettings"));
		miscSettings = new MiscSettings(prefs.node("miscSettings"), true /* Fallback if miscSettings is empty. Maestro 2.5.0.115 and earlier save misc settings in saveAndExportSettings */);

		usingNativeVolume = MaestroMain.isNativeVolumeSupported();
		if (usingNativeVolume)
		{
			volumeTransceiver = null;
			abcVolumeTransceiver = null;
		}
		else
		{
			volumeTransceiver = new VolumeTransceiver();
			volumeTransceiver.setVolume(prefs.getInt("volumizer", NativeVolumeBar.MAX_VOLUME));

			abcVolumeTransceiver = new VolumeTransceiver();
			abcVolumeTransceiver.setVolume(volumeTransceiver.getVolume());
		}

		try
		{
			sequencer = new NoteFilterSequencerWrapper();
			if (volumeTransceiver != null)
				sequencer.addTransceiver(volumeTransceiver);

			abcSequencer = new LotroSequencerWrapper();
			if (abcVolumeTransceiver != null)
				abcSequencer.addTransceiver(abcVolumeTransceiver);

			if (LotroSequencerWrapper.getLoadLotroSynthError() != null)
			{
				welcomeMessage = formatErrorMessage("Could not load LOTRO instrument sounds",
						"ABC Preview will use standard MIDI instruments instead\n"
								+ "(drums do not sound good in this mode).\n\n" + "Error details:\n"
								+ LotroSequencerWrapper.getLoadLotroSynthError());
				failedToLoadLotroInstruments = true;
			}
		}
		catch (MidiUnavailableException e)
		{
			JOptionPane.showMessageDialog(null, "Failed to initialize MIDI sequencer.\nThe program will now exit.\n\n"
					+ "Error details:\n" + e.getMessage(), "Failed to initialize MIDI sequencer",
					JOptionPane.ERROR_MESSAGE);
			System.exit(1);
			return;
		}

		// SWING stuff starts here
		
		try
		{
			List<Image> icons = new ArrayList<>();
			icons.add(ImageIO.read(IconLoader.class.getResourceAsStream("maestro_16.png")));
			icons.add(ImageIO.read(IconLoader.class.getResourceAsStream("maestro_32.png")));
			setIconImages(icons);
		}
		catch (Exception ex)
		{
			// Ignore
			ex.printStackTrace();
		}

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override public void windowClosing(WindowEvent e)
			{
				if (closeSong())
				{
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
		Icon stopIcon = IconLoader.getImageIcon("stop.png");
		Icon stopIconDisabled = IconLoader.getDisabledIcon("stop.png");

		partPanel = new PartPanel(sequencer, partAutoNumberer, abcSequencer);
		partPanel.addSettingsActionListener(e -> doSettingsDialog(SettingsDialog.NUMBERING_TAB));

		//TableLayout tableLayout = new TableLayout(LAYOUT_COLS, LAYOUT_ROWS);
		tableLayout.setHGap(HGAP);
		tableLayout.setVGap(VGAP);
		
		content = new JPanel(tableLayout, false);
		setContentPane(content);

		songTitleField = new JTextField();
		songTitleField.setToolTipText("Song Title");
		songTitleField.getDocument().addDocumentListener(new SimpleDocumentListener()
		{
			@Override public void changedUpdate(DocumentEvent e)
			{
				if (abcSong != null)
					abcSong.setTitle(songTitleField.getText());
			}
		});

		composerField = new JTextField();
		composerField.setToolTipText("Song Composer");
		composerField.getDocument().addDocumentListener(new SimpleDocumentListener()
		{
			@Override public void changedUpdate(DocumentEvent e)
			{
				if (abcSong != null)
					abcSong.setComposer(composerField.getText());
			}
		});
		
		genreField = new JTextField();
		genreField.setToolTipText("Song Genre(s)");
		genreField.getDocument().addDocumentListener(new SimpleDocumentListener()
		{
			@Override public void changedUpdate(DocumentEvent e)
			{
				if (abcSong != null)
					abcSong.setGenre(genreField.getText());
			}
		});
		
		moodField = new JTextField();
		moodField.setToolTipText("Song Mood(s)");
		moodField.getDocument().addDocumentListener(new SimpleDocumentListener()
		{
			@Override public void changedUpdate(DocumentEvent e)
			{
				if (abcSong != null)
					abcSong.setMood(moodField.getText());
			}
		});

		transcriberField = new JTextField(prefs.get("transcriber", ""));
		transcriberField.setToolTipText("Song Transcriber (your name)");
		transcriberFieldListener = new PrefsDocumentListener(prefs, "transcriber");
		transcriberField.getDocument().addDocumentListener(transcriberFieldListener);
		transcriberField.getDocument().addDocumentListener(new SimpleDocumentListener()
		{
			@Override public void changedUpdate(DocumentEvent e)
			{
				if (abcSong != null)
					abcSong.setTranscriber(transcriberField.getText());
			}
		});

		keySignatureField = new MyFormattedTextField(KeySignature.C_MAJOR, 5);
		keySignatureField.setToolTipText("<html>Adjust the key signature of the ABC file. "
				+ "This only affects the display, not the sound of the exported file.<br>"
				+ "Examples: C maj, Eb maj, F# min</html>");
		if (SHOW_KEY_FIELD)
		{
			keySignatureField.addPropertyChangeListener("value", evt -> {
				if (abcSong != null)
					abcSong.setKeySignature((KeySignature) keySignatureField.getValue());

			});
		}

		timeSignatureField = new MyFormattedTextField(TimeSignature.FOUR_FOUR, 5);
		timeSignatureField.setToolTipText("<html>Adjust the time signature of the ABC file.<br><br>"
				+ "This only affects the display, not the sound of the exported file.<br>"
				+ "Examples: 4/4, 3/8, 2/2</html>");
		timeSignatureField.addPropertyChangeListener("value", evt -> {
			if (abcSong != null)
				abcSong.setTimeSignature((TimeSignature) timeSignatureField.getValue());
		});

		transposeSpinner = new JSpinner(new SpinnerNumberModel(0, -48, 48, 1));
		transposeSpinner.setToolTipText("<html>Transpose the entire song by semitones.<br>"
				+ "12 semitones = 1 octave</html>");
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
			if (abcSong != null)
			{
				abcSong.setTempoBPM((Integer) tempoSpinner.getValue());

				abcSequencer.setTempoFactor(abcSong.getTempoFactor());

				if (abcSequencer.isRunning())
				{
					float delta = abcPreviewTempoFactor / abcSequencer.getTempoFactor();
					if (Math.max(delta, 1 / delta) > 1.5f)
						refreshPreviewSequence(false);
				}
			}
			else
			{
				abcSequencer.setTempoFactor(1.0f);
			}
		});

		resetTempoButton = new JButton("Reset");
		resetTempoButton.setMargin(new Insets(2, 8, 2, 8));
		resetTempoButton.setToolTipText("Set the tempo back to the source file's tempo");
		resetTempoButton.addActionListener(e -> {
			if (abcSong == null)
			{
				tempoSpinner.setValue(MidiConstants.DEFAULT_TEMPO_BPM);
			}
			else
			{
				float tempoFactor = abcSequencer.getTempoFactor();
				tempoSpinner.setValue(abcSong.getSequenceInfo().getPrimaryTempoBPM());
				if (tempoFactor != 1.0f)
					refreshPreviewSequence(false);
			}
			tempoSpinner.requestFocus();
		});

		tripletCheckBox = new JCheckBox("Triplets/swing rhythm");
		tripletCheckBox.setToolTipText("<html>Tweak the timing to allow for triplets or a swing rhythm.<br><br>"
				+ "This can cause short/fast notes to incorrectly be detected as triplets.<br>"
				+ "Leave it unchecked unless the song has triplets or a swing rhythm.</html>");
		tripletCheckBox.addActionListener(e -> {
			if (abcSong != null)
				abcSong.setTripletTiming(tripletCheckBox.isSelected());

			if (abcSequencer.isRunning())
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

			if (abcSequencer.isRunning())
				refreshPreviewSequence(false);
		});
		
		prioCheckBox = new JCheckBox("Combine Priorities");
		prioCheckBox.setToolTipText("<html>This allow to set track priority for Mix Timings.<br><br>"
				+ "Checkboxes will appear when combining tracks,<br>"
				+ "those enabled will prioritize the timings of those"
				+ "tracks over non-prioritized tracks.</html>");
		prioCheckBox.addActionListener(e -> {
			if (abcSong != null)
				abcSong.setPriorityActive(prioCheckBox.isSelected());

			if (abcSequencer.isRunning())
				refreshPreviewSequence(false);
		});

		exportButton = new JButton(); // Label set in onSaveAndExportSettingsChanged()
		exportButton.setToolTipText("<html><b>Export ABC</b><br>(Ctrl+E)</html>");
		exportButton.setIcon(IconLoader.getImageIcon("abcfile_32.png"));
		exportButton.setDisabledIcon(IconLoader.getDisabledIcon("abcfile_32.png"));
		exportButton.setHorizontalAlignment(SwingConstants.LEFT);
		exportButton.getModel().addChangeListener(new ChangeListener()
		{
			private boolean pressed = false;

			@Override public void stateChanged(ChangeEvent e)
			{
				if (exportButton.getModel().isPressed() != pressed)
				{
					pressed = exportButton.getModel().isPressed();
					if (pressed)
						exportSuccessfulLabel.setVisible(false);
				}
			}
		});
		exportButton.addActionListener(e -> exportAbc());

		exportSuccessfulLabel = new JLabel("Exported");
		exportSuccessfulLabel.setIcon(IconLoader.getImageIcon("check_16.png"));
		exportSuccessfulLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
		exportSuccessfulLabel.setVisible(false);

		partsList = new JList<>();
		partsList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		partsList.getSelectionModel().addListSelectionListener(e -> {
			AbcPart abcPart = (AbcPart) partsList.getSelectedValue();
			sequencer.getFilter().onAbcPartChanged(abcPart != null);
			abcSequencer.getFilter().onAbcPartChanged(abcPart != null);
			partPanel.setAbcPart(abcPart);
			if (abcPart != null) {
				updateButtons(false);
			} else {
				updateDelayButton();
				if (partsList.getModel().getSize() > 0) {
					// If ctrl-clicking to deselect this will ensure something is selected
					partsList.setSelectedIndex(0);
				}
			}
		});
		// Using this class as prototype cell content will set the Jlist width to be at least big enough to display LM bassoon,
		// and since this list determines the width of the flowlayout where the delete button is also, it should give space.
		class ProtoClass implements AbcPartMetadataSource {

			@Override
			public String getTitle() {
				return null;
			}

			@Override
			public int getPartNumber() {
				return 0;
			}

			@Override
			public LotroInstrument getInstrument() {
				return null;
			}
			
			@Override
			public String toString()
			{
				return "00. " + LotroInstrument.LONELY_MOUNTAIN_BASSOON + "*";
			}
			
		}
		partsList.setPrototypeCellValue(new ProtoClass());// This call is attempt of fix for no delete button on MacOS part 1
		partsList.setVisibleRowCount(8);

		// Wrap the part list in a panel that forces the list to the top
		// Fixes a swing bug where clicking after the end of the list will select the last element
		JPanel partListWrapperPanel = new JPanel(new BorderLayout());
		partListWrapperPanel.add(partsList, BorderLayout.NORTH);
		partListWrapperPanel.setBackground(partsList.getBackground());
		
		// Remove focus from text boxes if area under parts is clicked
		partListWrapperPanel.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e)
			{
				getRootPane().requestFocus();
			}
		});
		JScrollPane partsListScrollPane = new JScrollPane(partListWrapperPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
				JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		
		newPartButton = new JButton("New Part");
		newPartButton.addActionListener(e -> {
			if (abcSong != null)
				abcSong.createNewPart();
		});

		deletePartButton = new JButton("Delete");
		deletePartButton.addActionListener(e -> {
			if (abcSong != null) {
				if (abcSong.getParts().size() == 1) {
					// When deleting last past, make sure a new part is replacing it, so something is selected
					AbcPart deleteMe = (AbcPart) partsList.getSelectedValue();
					abcSong.createNewPart();
					abcSong.deletePart(deleteMe);
				} else if (abcSong.getParts().size() > 1) {
					abcSong.deletePart((AbcPart) partsList.getSelectedValue());
				}
			}
		});
		
		delayButton = new JButton("Delay Part");
		delayButton.addActionListener(e -> {
			if (partsList.getSelectedValue() != null) {
				DelayDialog.show(ProjectFrame.this, (AbcPart) partsList.getSelectedValue());
			}
		});
		delayButton.setToolTipText("Open a small dialog to edit delay on part.");
		
		numerateButton = new JButton("Numerate");
		numerateButton.addActionListener(e -> {
			if (abcSong != null) abcSong.assignNumbersToSimilarPartTypes();
		});
		numerateButton.setToolTipText("Auto assign numbers to identical instrument part titles.");
		
		if (miscSettings.showBadger) {
			songInfoLayout = new TableLayout(//
				new double[] { PREFERRED, FILL },//
				new double[] { PREFERRED, PREFERRED, PREFERRED, PREFERRED, PREFERRED });
		} else {
			songInfoLayout = new TableLayout(//
					new double[] { PREFERRED, FILL },//
					new double[] { PREFERRED, PREFERRED, PREFERRED });
		}
		songInfoLayout.setHGap(HGAP);
		songInfoLayout.setVGap(VGAP);
		songInfoPanel = new JPanel(songInfoLayout);
		{
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

		JPanel partsButtonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, HGAP, VGAP));
		partsButtonPanel.add(newPartButton);
		partsButtonPanel.add(deletePartButton);
		
		partsListPanel = new JPanel(new BorderLayout(HGAP, VGAP));
		partsListPanel.setBorder(BorderFactory.createTitledBorder("Song Parts"));
		partsListPanel.add(partsButtonPanel, BorderLayout.NORTH);
		partsListPanel.add(partsListScrollPane, BorderLayout.CENTER);
		GridLayout delayGrid = new GridLayout(2,1);
		JPanel delayPanel = new JPanel(delayGrid);
		delayPanel.add(delayButton);
		delayPanel.add(numerateButton);
		partsListPanel.add(delayPanel, BorderLayout.SOUTH);

		TableLayout settingsLayout = new TableLayout(//
				new double[] { PREFERRED, PREFERRED, FILL },//
				new double[] { });
		settingsLayout.setVGap(VGAP);
		settingsLayout.setHGap(HGAP);

		JPanel settingsPanel = new JPanel(settingsLayout);
		settingsPanel.setBorder(BorderFactory.createTitledBorder("Export Settings"));

		{
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

			if (SHOW_KEY_FIELD)
			{
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

			row++;
			settingsLayout.insertRow(row, PREFERRED);
			settingsPanel.add(exportSuccessfulLabel, "0, " + row + ", 2, " + row + ", F, F");

			row++;
			settingsLayout.insertRow(row, PREFERRED);
			settingsPanel.add(exportButton, "0, " + row + ", 2, " + row + ", F, F");
		}

		if (!SHOW_TEMPO_SPINNER)
			tempoSpinner.setEnabled(false);
		if (!SHOW_METER_TEXTBOX)
			timeSignatureField.setEnabled(false);
		if (!SHOW_KEY_FIELD)
			keySignatureField.setEnabled(false);

		volumeBar = new NativeVolumeBar(new VolumeManager());
		JPanel volumePanel = new JPanel(new TableLayout(//
				new double[] { PREFERRED },//
				new double[] { PREFERRED, PREFERRED }));
		volumePanel.add(new JLabel("Volume"), "0, 0, c, c");
		volumePanel.add(volumeBar, "0, 1, f, c");
		
		//stereoBar = new JSlider(SwingConstants.HORIZONTAL, 0, 100, 100);
		stereoBar = new StereoBar(new PanManager());
		JPanel stereoPanel = new JPanel(new TableLayout(//
				new double[] { PREFERRED },//
				new double[] { PREFERRED, PREFERRED }));
		stereoPanel.add(new JLabel("Stereo"), "0, 0, c, c");
		stereoPanel.add(stereoBar, "0, 1, f, c");

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

		final Insets playControlButtonMargin = new Insets(5, 20, 5, 20);

		playButton = new JButton(playIcon);
		playButton.setDisabledIcon(playIconDisabled);
		playButton.setMargin(playControlButtonMargin);
		playButton.addActionListener(e -> {
			SequencerWrapper curSequencer = abcPreviewMode ? abcSequencer : sequencer;

			boolean running = !curSequencer.isRunning();
			if (abcPreviewMode && running)
			{
				if (!refreshPreviewSequence(true))
					running = false;
			}

			curSequencer.setRunning(running);
			updateButtons(false);
		});

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
		
		tuneEditorButton = new JButton();
		tuneEditorButton.setText("T");
		tuneEditorButton.setToolTipText("<html><b> Tune Editor </b><br> Edit the tempo or key in specific sections </html>");
		tuneEditorButton.addActionListener(e -> TuneEditor.show(ProjectFrame.this, abcSong));

		JPanel modeButtonPanel = new JPanel(new BorderLayout());
		modeButtonPanel.add(midiModeRadioButton, BorderLayout.NORTH);
		modeButtonPanel.add(abcModeRadioButton, BorderLayout.SOUTH);
		//modeButtonPanel.add(tuneEditorButton, BorderLayout.WEST);

		JPanel playButtonPanel = new JPanel(new TableLayout(//
				new double[] { 0.5, 0.5 },//
				new double[] { PREFERRED }));
		playButtonPanel.add(playButton, "0, 0");
		playButtonPanel.add(stopButton, "1, 0");

		midiPositionLabel = new SongPositionLabel(sequencer);

		abcPositionLabel = new SongPositionLabel(abcSequencer, true /* adjustForTempo */);
		abcPositionLabel.setVisible(!midiPositionLabel.isVisible());

		midiBarLabel = new BarNumberLabel(sequencer, null);
		midiBarLabel.setToolTipText("Original Bar number");

		abcBarLabel = new BarNumberLabel(abcSequencer, null);
		abcBarLabel.setToolTipText("ABC Preview Bar number");
		abcBarLabel.setVisible(!midiBarLabel.isVisible());

		JPanel playControlPanel = new JPanel(new TableLayout(//
				new double[] { PREFERRED, 0.50, 4, PREFERRED, 4, 0.25, 0.25, PREFERRED, PREFERRED, 4 },//
				new double[] { PREFERRED, 4, PREFERRED }));
		playControlPanel.add(playButtonPanel, "3, 0, 3, 2, C, C");
		playControlPanel.add(tuneEditorButton, "0, 0, 0, 2, L, C");
		playControlPanel.add(modeButtonPanel, "1, 0, 1, 2, C, F");		
		playControlPanel.add(volumePanel, "5, 0, 5, 2, C, C");
		playControlPanel.add(stereoPanel, "6, 0, 6, 2, C, C");
		playControlPanel.add(midiPositionLabel, "8, 0, R, B");
		playControlPanel.add(abcPositionLabel, "8, 0, R, B");
		playControlPanel.add(midiBarLabel, "8, 2, R, T");
		playControlPanel.add(abcBarLabel, "8, 2, R, T");
		
		
		JPanel flowP = new JPanel(new FlowLayout());
		noteButton.addActionListener(e -> partPanel.noteToggle());
		noteButton.setToolTipText("<html>Show notepad where custom notes can be entered.<br>"
				+ "Will be saved in msx project file.</html>");
		//playControlPanel.add(noteButton, "6, 2, C, C");
		
		zoom.addActionListener(e -> partPanel.zoom());
		//playControlPanel.add(zoom, "7, 2, C, C");
		
		flowP.add(zoom);
		flowP.add(noteButton);
		playControlPanel.add(flowP, "7, 2, C, C");
		
		noteCountLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		noteCountLabel.setBorder(new EmptyBorder(0,0,0,20));//top,left,bottom,right
		noteCountLabel.setToolTipText("<html>Number of simultanious notes<br>"
				+ "that is playing.<br>"
				+ "Use as rough (as it for tech reasons typically overestimates)<br>"
				+ "guide to estimate how much of lotro max<br>"
				+ "polyphony the song will consume.<br>"
				+ "Stopped notes that are in release phase also counts.</html>");
		playControlPanel.add(noteCountLabel, "7, 0, 7, 0, L, C");		

		JPanel abcPartsAndSettings = new JPanel(new BorderLayout(HGAP, VGAP));
		abcPartsAndSettings.add(songInfoPanel, BorderLayout.NORTH);
		JPanel partsListAndColorizer = new JPanel(new BorderLayout(HGAP, VGAP));
		partsListAndColorizer.add(partsListPanel, BorderLayout.CENTER);
		if (SHOW_COLORIZER)
			partsListAndColorizer.add(new Colorizer(partPanel), BorderLayout.SOUTH);
		abcPartsAndSettings.add(partsListAndColorizer, BorderLayout.CENTER);
		abcPartsAndSettings.add(settingsPanel, BorderLayout.SOUTH);

		JPanel midiPartsAndControls = new JPanel(new BorderLayout(HGAP, VGAP));
		midiPartsAndControls.add(partPanel, BorderLayout.CENTER);
		midiPartsAndControls.add(playControlPanel, BorderLayout.SOUTH);
		midiPartsAndControls.setBorder(BorderFactory.createTitledBorder("Part Settings"));
		
		JSplitPane topLevelSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, abcPartsAndSettings, midiPartsAndControls);
		topLevelSplitPane.setBorder(BorderFactory.createEmptyBorder());
		topLevelSplitPane.setContinuousLayout(true);
		topLevelSplitPane.setFocusable(false);
		
		add(topLevelSplitPane, "0, 0, 1, 0");
		
		final FileFilterDropListener dropListener = new FileFilterDropListener(false, "mid", "midi", "kar", "abc", "txt",
				AbcSong.MSX_FILE_EXTENSION_NO_DOT);
		dropListener.addActionListener(e -> {
			final File file = dropListener.getDroppedFile();
			SwingUtilities.invokeLater(() -> openFile(file));
		});
		new DropTarget(this, dropListener);

		mainSequencerListener = new MainSequencerListener();
		sequencer.addChangeListener(mainSequencerListener);

		abcSequencerListener = new AbcSequencerListener();
		abcSequencer.addChangeListener(abcSequencerListener);

		initMenu();
		onSaveAndExportSettingsChanged();
		partPanel.showInfoMessage(welcomeMessage);
		updateButtons(true);
		
		// Add support for using spacebar for pause/play.
		ActionListener spaceBarListener = ae -> {
			if (!sequencer.isLoaded())
			{
				return;
			}
			SequencerWrapper curSequencer = abcPreviewMode ? abcSequencer : sequencer;

			boolean running = !curSequencer.isRunning();
			if (abcPreviewMode && running)
			{
				if (!refreshPreviewSequence(true))
					running = false;
			}

			curSequencer.setRunning(running);
			updateButtons(false);
		};
		this.getRootPane().registerKeyboardAction(spaceBarListener, KeyStroke.getKeyStroke(' '), JComponent.WHEN_IN_FOCUSED_WINDOW);
		
		// Add a listener to remove focus from current component when somewhere else is clicked.
		MouseAdapter listenForFocus = new MouseAdapter() {
			public void mouseClicked(MouseEvent e)
			{
				getRootPane().requestFocus();
			}
		};
		addMouseListener(listenForFocus);

	}

	private static void discardObject(IDiscardable object)
	{
		if (object != null)
			object.discard();
	}

	@Override public void dispose()
	{
		if (abcSong != null)
		{
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

	private void initMenu()
	{
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);

		JMenu fileMenu = menuBar.add(new JMenu(" File "));
		fileMenu.setMnemonic('F');

		JMenuItem openItem = fileMenu.add(new JMenuItem("Open file..."));
		openItem.setMnemonic('O');
		openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK));
		openItem.addActionListener(new ActionListener()
		{
			JFileChooser openFileChooser;

			@Override public void actionPerformed(ActionEvent e)
			{
				if (openFileChooser == null)
				{
					openFileChooser = new JFileChooser(prefs.get("openFileChooser.path", null));
					openFileChooser.setMultiSelectionEnabled(false);
					openFileChooser.setFileFilter(new ExtensionFileFilter("MIDI, ABC, and "
							+ AbcSong.MSX_FILE_DESCRIPTION_PLURAL, "mid", "midi", "kar", "abc", "txt",
							AbcSong.MSX_FILE_EXTENSION_NO_DOT));
				}

				int result = openFileChooser.showOpenDialog(ProjectFrame.this);
				if (result == JFileChooser.APPROVE_OPTION)
				{
					openFile(openFileChooser.getSelectedFile());
					prefs.put("openFileChooser.path", openFileChooser.getCurrentDirectory().getAbsolutePath());
				}
			}
		});

		fileMenu.addSeparator();

		saveMenuItem = fileMenu.add(new JMenuItem("Save " + AbcSong.MSX_FILE_DESCRIPTION));
		saveMenuItem.setIcon(IconLoader.getImageIcon("msxfile_16.png"));
		saveMenuItem.setDisabledIcon(IconLoader.getDisabledIcon("msxfile_16.png"));
		saveMenuItem.setMnemonic('S');
		saveMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK));
		saveMenuItem.addActionListener(e -> save());

		saveAsMenuItem = fileMenu.add(new JMenuItem("Save " + AbcSong.MSX_FILE_DESCRIPTION + " As..."));
		saveAsMenuItem.setMnemonic('A');
		saveAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK
				| KeyEvent.SHIFT_DOWN_MASK));
		saveAsMenuItem.addActionListener(e -> saveAs());

		fileMenu.addSeparator();

		exportMenuItem = fileMenu.add(new JMenuItem("Export ABC"));
		exportMenuItem.setIcon(IconLoader.getImageIcon("abcfile_16.png"));
		exportMenuItem.setDisabledIcon(IconLoader.getDisabledIcon("abcfile_16.png"));
		exportMenuItem.setMnemonic('E');
		exportMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK));
		exportMenuItem.addActionListener(e -> exportAbc());

		exportAsMenuItem = fileMenu.add(new JMenuItem("Export ABC As..."));
		exportAsMenuItem.setMnemonic('p');
		exportAsMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, KeyEvent.CTRL_DOWN_MASK
				| KeyEvent.SHIFT_DOWN_MASK));
		exportAsMenuItem.addActionListener(e -> exportAbcAs());

		fileMenu.addSeparator();
		
		saveExpandedMidiMenuItem = fileMenu.add(new JMenuItem("Export Expanded MIDI..."));
		saveExpandedMidiMenuItem.addActionListener(e -> expandMidi());
		
		fileMenu.addSeparator();
		
		closeProject = fileMenu.add(new JMenuItem("Close Project"));
		closeProject.addActionListener(e -> closeSong());

		JMenuItem exitItem = fileMenu.add(new JMenuItem("Exit"));
		exitItem.setMnemonic('x');
		exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, KeyEvent.ALT_DOWN_MASK));
		exitItem.addActionListener(e -> {
			if (closeSong())
			{
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
		settingsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, KeyEvent.CTRL_DOWN_MASK));
		settingsItem.addActionListener(e -> doSettingsDialog());

		toolsMenu.addSeparator();

		JMenuItem aboutItem = toolsMenu.add(new JMenuItem("About " + MaestroMain.APP_NAME + "..."));
		aboutItem.setMnemonic('A');
		aboutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
		aboutItem.addActionListener(e -> AboutDialog.show(ProjectFrame.this, MaestroMain.APP_NAME, MaestroMain.APP_VERSION, MaestroMain.APP_URL,
				"maestro_64.png"));
	}

	private int currentSettingsDialogTab = 0;

	private void doSettingsDialog()
	{
		doSettingsDialog(currentSettingsDialogTab);
	}

	private void doSettingsDialog(int tab)
	{
		SettingsDialog dialog = new SettingsDialog(ProjectFrame.this, partAutoNumberer,
				partNameTemplate, exportFilenameTemplate, saveSettings.getCopy(), miscSettings.getCopy());
		dialog.setActiveTab(tab);
		dialog.setVisible(true);
		if (dialog.isSuccess())
		{
			if (dialog.isNumbererSettingsChanged())
			{
				partAutoNumberer.setSettings(dialog.getNumbererSettings());
				partAutoNumberer.renumberAllParts();
			}
			partNameTemplate.setSettings(dialog.getNameTemplateSettings());
			partPanel.settingsChanged();
			
			exportFilenameTemplate.setSettings(dialog.getExportFilenameTemplateSettings());

			saveSettings.copyFrom(dialog.getSaveAndExportSettings());
			saveSettings.saveToPrefs();
			onSaveAndExportSettingsChanged();
			
			miscSettings.copyFrom(dialog.getMiscSettings());
			miscSettings.saveToPrefs();
		}
		else if (dialog.isSettingPageReset())
		{
			switch(dialog.getResetPageIndex())
			{
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
			case 3: // save and export
				saveSettings.restoreDefaults();
				break;
			case 4: // misc
				miscSettings.restoreDefaults();
				break;
			}
		}
		currentSettingsDialogTab = dialog.getActiveTab();
		dialog.dispose();
	}

	private void onSaveAndExportSettingsChanged()
	{
		if (saveSettings.showExportFileChooser)
		{
			exportAsMenuItem.setVisible(false);
			exportMenuItem.setText("Export ABC As...");
		}
		else
		{
			exportAsMenuItem.setVisible(true);
			exportMenuItem.setText("Export ABC");
		}

		if (shouldExportAbcAs())
			exportButton.setText("Export ABC As...");
		else
			exportButton.setText("Export ABC");

		if (abcSong != null)
			abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
		
		//if (abcSong != null)
		//	abcSong.setShowPruned(saveSettings.showPruned);
		
		noteCountLabel.setVisible(miscSettings.showMaxPolyphony);
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

	public void onVolumeChanged()
	{
		volumeBar.repaint();
	}

	private class VolumeManager implements NativeVolumeBar.Callback
	{
		@Override public void setVolume(int volume)
		{
			if (usingNativeVolume)
			{
				MaestroMain.setVolume((float) volume / NativeVolumeBar.MAX_VOLUME);
			}
			else
			{
				if (volumeTransceiver != null)
					volumeTransceiver.setVolume(volume);
				if (abcVolumeTransceiver != null)
					abcVolumeTransceiver.setVolume(volume);
				prefs.putInt("volumizer", volume);
			}
		}

		@Override public int getVolume()
		{
			if (usingNativeVolume)
			{
				return (int) (MaestroMain.getVolume() * NativeVolumeBar.MAX_VOLUME);
			}
			else
			{
				if (volumeTransceiver != null)
					return volumeTransceiver.getVolume();
				if (abcVolumeTransceiver != null)
					return abcVolumeTransceiver.getVolume();
				return NativeVolumeBar.MAX_VOLUME;
			}
		}
	}
	
	private class PanManager implements StereoBar.Callback
	{
		@Override public void setPan(int pan)
		{
			if (pan != prefs.getInt("stereoPan", 100)) {
				prefs.putInt("stereoPan", pan);
				SequencerWrapper curSequencer = abcPreviewMode ? abcSequencer : sequencer;

				boolean running = curSequencer.isRunning();
				if (abcPreviewMode && running)
				{
					//curSequencer.setRunning(false);
					refreshPreviewSequence(true);
					//curSequencer.setRunning(true);
				}
				saveSettings.saveToPrefs();
			}
		}

		@Override public int getPan()
		{
			return prefs.getInt("stereoPan", 100);
		}
	}

	private class MainSequencerListener implements Listener<SequencerEvent>
	{
		@Override public void onEvent(SequencerEvent evt)
		{
			updateButtons(false);
			if (evt.getProperty() == SequencerProperty.IS_RUNNING)
			{
				if (sequencer.isRunning())
					abcSequencer.stop();
			}
			else if (!echoingPosition)
			{
				try
				{
					echoingPosition = true;
					if (evt.getProperty() == SequencerProperty.POSITION)
					{
						abcSequencer.setTickPosition(Util.clamp(sequencer.getTickPosition(), abcPreviewStartTick,
								abcSequencer.getTickLength()));
					}
					else if (evt.getProperty() == SequencerProperty.DRAG_POSITION)
					{
						abcSequencer.setDragTick(Util.clamp(sequencer.getDragTick(), abcPreviewStartTick,
								abcSequencer.getTickLength()));
					}
					else if (evt.getProperty() == SequencerProperty.IS_DRAGGING)
					{
						abcSequencer.setDragging(sequencer.isDragging());
					}
				}
				finally
				{
					echoingPosition = false;
				}
			}
		}
	}
	
	private void updateNoteCount () {
		noteCountLabel.setVisible(miscSettings.showMaxPolyphony);
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
	
	private void updateNoteCountLabel () {
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
		//String pad1 = (maxNoteCount < 10)?"&ensp;":"&nbsp;</pre>";
		//String pad2 = (maxNoteCountTotal < 10)?"&ensp;":"&nbsp;";
		
		String strAdd1 = String.format("<html>Notes: "+maxColor+"%02d", maxNoteCount);
		String strAdd3 = String.format("(Peak: "+totalColor+"%02d", maxNoteCountTotal);
		
		String strAdd2 = " </font>";
		if (maxNoteCount > 63) {
			strAdd2 = "+</font>";
		}
		String strAdd4 = "</font> )</html>";
		if (maxNoteCountTotal > 63) {
			strAdd4 = "+</font>)</html>";
		}
		//System.err.println(strAdd1+strAdd2+strAdd3+strAdd4);
		
		noteCountLabel.setText(strAdd1+strAdd2+strAdd3+strAdd4);
	}

	private class AbcSequencerListener implements Listener<SequencerEvent>
	{
		@Override public void onEvent(SequencerEvent evt)
		{
			updateButtons(false);
			updateNoteCount();
			if (evt.getProperty() == SequencerProperty.IS_RUNNING)
			{
				if (abcSequencer.isRunning())
					sequencer.stop();
			}
			else if (!echoingPosition)
			{
				try
				{
					echoingPosition = true;
					if (evt.getProperty() == SequencerProperty.POSITION)
					{
						sequencer.setTickPosition(Util.clamp(abcSequencer.getTickPosition(), 0,
								sequencer.getTickLength()));
					}
					else if (evt.getProperty() == SequencerProperty.DRAG_POSITION)
					{
						sequencer.setDragTick(Util.clamp(abcSequencer.getDragTick(), 0, sequencer.getTickLength()));
					}
					else if (evt.getProperty() == SequencerProperty.IS_DRAGGING)
					{
						sequencer.setDragging(abcSequencer.isDragging());
					}
				}
				finally
				{
					echoingPosition = false;
				}
			}
		}
	}

	private static abstract class SimpleDocumentListener implements DocumentListener
	{
		@Override public void insertUpdate(DocumentEvent e)
		{
			this.changedUpdate(e);
		}

		@Override public void removeUpdate(DocumentEvent e)
		{
			this.changedUpdate(e);
		}
	}

	private static class PrefsDocumentListener implements DocumentListener
	{
		private Preferences prefs;
		private String prefName;
		private boolean ignoreChanges = false;

		public PrefsDocumentListener(Preferences prefs, String prefName)
		{
			this.prefs = prefs;
			this.prefName = prefName;
		}

		public void setIgnoreChanges(boolean ignoringChanges)
		{
			this.ignoreChanges = ignoringChanges;
		}

		private void updatePrefs(javax.swing.text.Document doc)
		{
			if (ignoreChanges)
				return;

			String txt;
			try
			{
				txt = doc.getText(0, doc.getLength());
			}
			catch (BadLocationException e)
			{
				txt = "";
			}
			prefs.put(prefName, txt);
		}

		@Override public void changedUpdate(DocumentEvent e)
		{
			updatePrefs(e.getDocument());
		}

		@Override public void insertUpdate(DocumentEvent e)
		{
			updatePrefs(e.getDocument());
		}

		@Override public void removeUpdate(DocumentEvent e)
		{
			updatePrefs(e.getDocument());
		}
	}

	private boolean updateButtonsPending = false;
	private Runnable updateButtonsTask = new Runnable()
	{
		@Override public void run()
		{
			boolean hasAbcNotes = false;
			if (abcSong != null)
			{
				for (AbcPart part : abcSong.getParts())
				{
					if (part.getEnabledTrackCount() > 0)
					{
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

			if (!hasAbcNotes)
			{
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
			if (midiLoaded && abcSong.tuneBars != null) {
				tuneEditorButton.setForeground(new Color(0.2f, 0.8f, 0.2f));
			} else if (midiLoaded) {
				tuneEditorButton.setForeground(Color.black);
			} else {
				tuneEditorButton.setForeground(Color.gray);
			}
			resetTempoButton.setEnabled(midiLoaded && abcSong != null && abcSong.getTempoFactor() != 1.0f);
			resetTempoButton.setVisible(resetTempoButton.isEnabled());
			keySignatureField.setEnabled(midiLoaded);
			timeSignatureField.setEnabled(midiLoaded);
			tripletCheckBox.setEnabled(midiLoaded);
			mixCheckBox.setEnabled(midiLoaded);
			prioCheckBox.setEnabled(midiLoaded && mixCheckBox.isSelected());
			zoom.setEnabled(midiLoaded);
			noteButton.setEnabled(midiLoaded);
			if (midiLoaded) {
				midiModeRadioButton.setText("Original ("+SequenceInfo.standard+(SequenceInfo.hasPorts?"+)":")"));
			} else {
				midiModeRadioButton.setText("Original");
			}
			
			double[] LAYOUT_COLS_DYN = new double[] { partsList.getFixedCellWidth() + 32, FILL };
			tableLayout.setColumn(LAYOUT_COLS_DYN);// This call is attempt of fix for no delete button on MacOS part 2
			
			String partListTitle = "Song Parts";
			if (abcSong != null)
			{
				partListTitle = partListTitle + " (Count: " + abcSong.getActivePartCount() + ")";
			}
			
			partsListPanel.setBorder(BorderFactory.createTitledBorder(partListTitle));
			
			updateButtonsPending = false;
		}
	};
	
	public void updateDelayButton () {
		if (partsList.getSelectedIndex() != -1 && partPanel != null && partPanel.getAbcPart() != null && partPanel.getAbcPart().delay != 0) {
			delayButton.setForeground(new Color(0.2f, 0.8f, 0.2f));//green
		} else if (partsList.getSelectedIndex() != -1) {
			Color c = UIManager.getColor("TextField.foreground");
			delayButton.setForeground(c);
		} else {
			// This is needed since when starting to set foreground color manually,
			// it will no longer appear greyed out when disabled automatically.
			delayButton.setForeground(new Color(0.6f, 0.6f, 0.6f));
		}
		delayButton.setEnabled(partsList.getSelectedIndex() != -1);
	}

	private void updateButtons(boolean immediate)
	{
		if (immediate)
		{
			updateButtonsTask.run();
		}
		else if (!updateButtonsPending)
		{
			updateButtonsPending = true;
			SwingUtilities.invokeLater(updateButtonsTask);
		}
	}

	private boolean updateTitlePending = false;

	private void updateTitle()
	{
		if (!updateTitlePending)
		{
			updateTitlePending = true;
			SwingUtilities.invokeLater(() -> {
				updateTitlePending = false;
				String title = MaestroMain.APP_NAME;
				if (abcSong != null)
				{
					if (abcSong.getSaveFile() != null)
					{
						title += " - " + abcSong.getSaveFile().getName();
						if (abcSong.getSourceFile() != null)
							title += " [" + abcSong.getSourceFile().getName() + "]";
					}
					else if (abcSong.getSourceFile() != null)
					{
						title += " - " + abcSong.getSourceFile().getName();
					}

					if (isAbcSongModified())
						title += "*";
				}
				setTitle(title);
			});
		}
	}

	private Listener<AbcPartEvent> abcPartListener = new Listener<AbcPartEvent>()
	{
		@Override public void onEvent(AbcPartEvent e)
		{
			if (e.getProperty() == AbcPartProperty.TRACK_ENABLED)
				updateButtons(false);
			
			if (e.getProperty() == AbcPartProperty.TITLE && partPanel != null)
				partPanel.setNewTitle(e.getSource());

			partsList.repaint();
			
			setAbcSongModified(true);

			if (e.isAbcPreviewRelated() && abcSequencer.isRunning())
				refreshPreviewSequence(false);
			else if (e.isAbcPreviewRelated() && abcPreviewMode)
				refreshPreviewSequence(false);
			
			if (e.isAbcPreviewRelated() && partPanel != null) {
				partPanel.repaint();
			}
		}
	};

	private Listener<AbcSongEvent> abcSongListener = new Listener<AbcSongEvent>()
	{
		@Override public void onEvent(AbcSongEvent e)
		{
			if (abcSong == null || abcSong != e.getSource())
				return;

			int idx;

			switch (e.getProperty())
			{
			case TITLE:
				if (!songTitleField.getText().equals(abcSong.getTitle()))
				{
					songTitleField.setText(abcSong.getTitle());
					songTitleField.select(0, 0);
				}
				break;
			case COMPOSER:
				if (!composerField.getText().equals(abcSong.getComposer()))
				{
					composerField.setText(abcSong.getComposer());
					composerField.select(0, 0);
				}
				break;
			case TRANSCRIBER:
				if (!transcriberField.getText().equals(abcSong.getTranscriber()))
				{
					transcriberFieldListener.setIgnoreChanges(true);
					transcriberField.setText(abcSong.getTranscriber());
					transcriberField.select(0, 0);
					transcriberFieldListener.setIgnoreChanges(false);
				}
				break;

			case TEMPO_FACTOR:
				if (getTempo() != abcSong.getTempoBPM())
					tempoSpinner.setValue(abcSong.getTempoBPM());
				break;
			case TRANSPOSE:
				if (getTranspose() != abcSong.getTranspose())
					transposeSpinner.setValue(abcSong.getTranspose());
				break;
			case KEY_SIGNATURE:
				if (SHOW_KEY_FIELD)
				{
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
				partsList.setSelectedIndex(idx);
				partsList.ensureIndexIsVisible(idx);
				partsList.repaint();
				updateButtons(false);
				maxNoteCountTotal = 0;
				maxNoteCount = 0;
				break;
				
			case TUNE_EDIT:
				updateButtons(false);
				if (partsList.getSelectedValue() != null) {
					// We do this to show the tempo panel if tune editor has changed something
					partPanel.tuneUpdated((AbcPart) partsList.getSelectedValue());
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
					partsList.setSelectedIndex(idx - 1);
				else if (abcSong.getParts().size() > 1) {
					partsList.setSelectedIndex(1);
				}

				if (abcSong.getParts().size() == 0)
				{
					sequencer.stop();
					partPanel.showInfoMessage(formatInfoMessage("Add a part", "This ABC song has no parts.\n" + //
							"Click the " + newPartButton.getText() + " button to add a new part."));
				}

				if (abcSequencer.isRunning())
					refreshPreviewSequence(false);

				partsList.repaint();
				updateButtons(false);
				maxNoteCountTotal = 0;
				maxNoteCount = 0;
				break;

			case PART_LIST_ORDER:
				partsList.setSelectedIndex(abcSong.getParts().indexOf(partPanel.getAbcPart()));
				partsList.repaint();
				updateButtons(false);
				break;

			case SKIP_SILENCE_AT_START:
				if (saveSettings.skipSilenceAtStart != abcSong.isSkipSilenceAtStart())
				{
					saveSettings.skipSilenceAtStart = abcSong.isSkipSilenceAtStart();
					saveSettings.saveToPrefs();
				}
				break;
			case GENRE:
				if (!genreField.getText().equals(abcSong.getGenre()))
				{
					genreField.setText(abcSong.getGenre());
					genreField.select(0, 0);
				}
				break;
			case MOOD:
				if (!moodField.getText().equals(abcSong.getMood()))
				{
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

			setAbcSongModified(true);
		}
	};

	private ListDataListener partsListListener = new ListDataListener()
	{
		@Override public void intervalAdded(ListDataEvent e)
		{
			partsList.repaint();
			updateButtons(false);
		}

		@Override public void intervalRemoved(ListDataEvent e)
		{
			partsList.repaint();
			updateButtons(false);
		}

		@Override public void contentsChanged(ListDataEvent e)
		{
			partsList.repaint();
			updateButtons(false);
		}
	};

	private void setAbcSongModified(boolean abcSongModified)
	{
		if (this.abcSongModified != abcSongModified)
		{
			this.abcSongModified = abcSongModified;
			updateTitle();
		}
		if (abcSongModified) {
			maxNoteCount = 0;
			maxNoteCountTotal = 0;
		}
	}
	
	public void setMIDIFileResolved () {
		midiResolved = true;
	}

	private boolean isAbcSongModified()
	{
		return abcSong != null && (abcSongModified || !partPanel.getNote().equals(abcSong.getNote()));
	}

	public int getTranspose()
	{
		return (Integer) transposeSpinner.getValue();
	}

	public int getTempo()
	{
		return (Integer) tempoSpinner.getValue();
	}

	private boolean closeSong()
	{
		SectionEditor.clearClipboard();
		sequencer.stop();
		abcSequencer.stop();

		boolean promptSave = isAbcSongModified() && (saveSettings.promptSaveNewSong || abcSong.getSaveFile() != null);
		if (promptSave)
		{
			String message;
			if (abcSong.getSaveFile() == null)
				message = "Do you want to save this new song?";
			else
				message = "Do you want to save changes to \"" + abcSong.getSaveFile().getName() + "\"?";

			int result = JOptionPane.showConfirmDialog(this, message, "Save Changes", JOptionPane.YES_NO_CANCEL_OPTION,
					JOptionPane.QUESTION_MESSAGE, IconLoader.getImageIcon("msxfile_32.png"));
			if (result == JOptionPane.CANCEL_OPTION)
				return false;

			if (result == JOptionPane.YES_OPTION)
			{
				if (!save())
					return false;
			}
		}

		if (abcSong != null)
		{
			abcSong.getParts().getListModel().removeListDataListener(partsListListener);
			abcSong.discard();
			abcSong = null;
		}

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
		partPanel.setNote("");
		partPanel.noteVisible(false);

		return true;
	}

	public void openFile(File file)
	{
		if (!closeSong())
			return;
		
		maxNoteCountTotal = 0;
		maxNoteCount = 0;
		
		file = Util.resolveShortcut(file);
		allowOverwriteSaveFile = false;
		allowOverwriteExportFile = false;
		setAbcSongModified(false);

		try
		{
			abcSong = new AbcSong(file, partAutoNumberer, partNameTemplate, exportFilenameTemplate, openFileResolver);
			abcSong.setAllOut(miscSettings.showBadger && miscSettings.allBadger);
			abcSong.setBadger(miscSettings.showBadger);
			abcSong.addSongListener(abcSongListener);
			for (AbcPart part : abcSong.getParts())
			{
				part.addAbcListener(abcPartListener);
			}

			songTitleField.setText(abcSong.getTitle());
			songTitleField.select(0, 0);
			composerField.setText(abcSong.getComposer());
			composerField.select(0, 0);
			genreField.setText(abcSong.getGenre());
			genreField.select(0, 0);
			moodField.setText(abcSong.getMood());
			moodField.select(0, 0);

			if (abcSong.isFromAbcFile() || abcSong.isFromXmlFile())
			{
				transcriberFieldListener.setIgnoreChanges(true);
				transcriberField.setText(abcSong.getTranscriber());
				transcriberField.select(0, 0);
				transcriberFieldListener.setIgnoreChanges(false);
			}
			else
			{
				abcSong.setTranscriber(transcriberField.getText());
			}
			
			if (abcSong.isFromXmlFile())
			{
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

			if (abcSong.isFromXmlFile())
			{
				allowOverwriteSaveFile = true;
			}

			if (abcSong.isFromAbcFile() || abcSong.isFromXmlFile())
			{
				if (abcSong.getParts().isEmpty())
				{
					updateButtons(true);
					abcSong.createNewPart();
				}
				else
				{
					partsList.setSelectedIndex(0);
					updatePreviewMode(true, true);
					updateButtons(true);
				}
			}
			else
			{
				updateButtons(true);
				if (abcSong.getParts().isEmpty())
				{
					abcSong.createNewPart();
				}
				sequencer.start();
			}

			abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
			//abcSong.setShowPruned(saveSettings.showPruned);

			setAbcSongModified(midiResolved);
			midiResolved = false;
			updateTitle();
		}
		catch (SAXParseException e)
		{
			String message = e.getMessage();
			if (e.getLineNumber() >= 0)
			{
				message += "\nLine " + e.getLineNumber();
				if (e.getColumnNumber() >= 0)
					message += ", column " + e.getColumnNumber();
			}

			partPanel.showInfoMessage(formatErrorMessage("Could not open " + file.getName(), message));
			midiResolved = false;
		}
		catch (InvalidMidiDataException | IOException | ParseException | SAXException e)
		{
			partPanel.showInfoMessage(formatErrorMessage("Could not open " + file.getName(), e.getMessage()));
			midiResolved = false;
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void setPartsListModel () {
		// Not pretty..
		partsList.setModel((DefaultListModel) (abcSong.getParts().getListModel()));
	}

	/** Used when the MIDI file in a Maestro song project can't be loaded. */
	private FileResolver openFileResolver = new FileResolver()
	{
		@Override public File locateFile(File original, String message)
		{
			message += "\n\nWould you like to try to locate the file?";
			return resolveHelper(original, message);
		}

		@Override public File resolveFile(File original, String message)
		{
			message += "\n\nWould you like to pick a different file?";
			return resolveHelper(original, message);
		}

		private File resolveHelper(File original, String message)
		{
			int result = JOptionPane.showConfirmDialog(ProjectFrame.this, message, "Failed to open file",
					JOptionPane.OK_CANCEL_OPTION);

			File alternateFile = null;
			if (result == JOptionPane.OK_OPTION)
			{
				JFileChooser jfc = new JFileChooser();
				if (original != null)
					jfc.setSelectedFile(original);

				if (jfc.showOpenDialog(ProjectFrame.this) == JFileChooser.APPROVE_OPTION)
					alternateFile = jfc.getSelectedFile();
			}

			return alternateFile;
		}
	};

	private static String formatInfoMessage(String title, String message)
	{
		return "<html><h3>" + Util.htmlEscape(title) + "</h3>" + Util.htmlEscape(message).replace("\n", "<br>")
				+ "<h3>&nbsp;</h3></html>";
	}

	private static String formatErrorMessage(String title, String message)
	{
		return "<html><h3><font color=\"" + ColorTable.PANEL_TEXT_ERROR.getHtml() + "\">" + Util.htmlEscape(title)
				+ "</font></h3>" + Util.htmlEscape(message).replace("\n", "<br>") + "<h3>&nbsp;</h3></html>";
	}

	private void updatePreviewMode(boolean abcPreviewModeNew)
	{
		SequencerWrapper oldSequencer = abcPreviewMode ? abcSequencer : sequencer;
		updatePreviewMode(abcPreviewModeNew, oldSequencer.isRunning());
	}

	private void updatePreviewMode(boolean newAbcPreviewMode, boolean running)
	{
		boolean runningNow = abcPreviewMode ? abcSequencer.isRunning() : sequencer.isRunning();

		if (newAbcPreviewMode != abcPreviewMode || runningNow != running)
		{
			if (running && newAbcPreviewMode)
			{
				if (!refreshPreviewSequence(true))
				{
					running = false;

					SequencerWrapper oldSequencer = abcPreviewMode ? abcSequencer : sequencer;
					oldSequencer.stop();
				}
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

	private class RefreshPreviewTask implements Runnable
	{
		@Override public void run()
		{
			if (refreshPreviewPending)
			{
				if (!refreshPreviewSequence(true))
					abcSequencer.stop();
			}
		}
	}

	private boolean refreshPreviewSequence(boolean immediate)
	{
		if (!immediate)
		{
			if (!refreshPreviewPending)
			{
				refreshPreviewPending = true;
				SwingUtilities.invokeLater(new RefreshPreviewTask());
			}
			return true;
		}

		refreshPreviewPending = false;

		if (abcSong == null)
		{
			abcPreviewStartTick = 0;
			abcPreviewTempoFactor = 1.0f;
			abcSequencer.clearSequence();
			abcSequencer.reset(false);
			abcBarLabel.setBarNumberCache(null);
			abcBarLabel.setInitialOffsetTick(abcPreviewStartTick);
			abcPositionLabel.setInitialOffsetTick(abcPreviewStartTick);
			return false;
		}

		try
		{
			abcSong.setSkipSilenceAtStart(saveSettings.skipSilenceAtStart);
			//abcSong.setShowPruned(saveSettings.showPruned);
			AbcExporter exporter = abcSong.getAbcExporter();
			exporter.stereoPan = prefs.getInt("stereoPan", 100);
			SequenceInfo previewSequenceInfo = SequenceInfo.fromAbcParts(exporter, !failedToLoadLotroInstruments);

			long tick = sequencer.getTickPosition();
			abcPreviewStartTick = exporter.getExportStartTick();
			abcPreviewTempoFactor = abcSequencer.getTempoFactor();
			abcBarLabel.setBarNumberCache(exporter.getTimingInfo());
			abcBarLabel.setInitialOffsetTick(abcPreviewStartTick);
			abcPositionLabel.setInitialOffsetTick(abcPreviewStartTick);

			boolean running = abcSequencer.isRunning();
			abcSequencer.reset(false);
			abcSequencer.setSequence(previewSequenceInfo.getSequence());

			if (tick < abcPreviewStartTick)
				tick = abcPreviewStartTick;

			if (tick >= abcSequencer.getTickLength())
			{
				tick = 0;
				running = false;
			}

			if (running && sequencer.isRunning())
				sequencer.stop();

			abcSequencer.setTickPosition(tick);
			abcSequencer.setRunning(running);
		}
		catch (InvalidMidiDataException | AbcConversionException e)
		{
			sequencer.stop();
			abcSequencer.stop();
			JOptionPane.showMessageDialog(ProjectFrame.this, e.getMessage(), "Error previewing ABC",
					JOptionPane.WARNING_MESSAGE);
			return false;
		}

		return true;
	}

	private void commitAllFields()
	{
		try
		{
			abcSong.setNote(partPanel.getNote());
			partPanel.commitAllFields();
			transposeSpinner.commitEdit();
			tempoSpinner.commitEdit();
			timeSignatureField.commitEdit();
			keySignatureField.commitEdit();
		}
		catch (java.text.ParseException e)
		{
			// Ignore
		}
	}

	private File doSaveDialog(File defaultFile, File allowOverwriteFile, String extension, FileFilter fileFilter)
	{
		JFileChooser jfc = new JFileChooser();
		jfc.setFileFilter(fileFilter);
		jfc.setSelectedFile(defaultFile);

		while (true)
		{
			int result = jfc.showSaveDialog(this);
			if (result != JFileChooser.APPROVE_OPTION || jfc.getSelectedFile() == null)
				return null;

			File selectedFile = jfc.getSelectedFile();
			String fileName = selectedFile.getName();
			int dot = fileName.lastIndexOf('.');
			if (dot <= 0 || !fileName.substring(dot).equalsIgnoreCase(extension))
			{
				fileName += extension;
				selectedFile = new File(selectedFile.getParent(), fileName);
			}

			if (selectedFile.exists() && !selectedFile.equals(allowOverwriteFile))
			{
				int res = JOptionPane.showConfirmDialog(this, "File \"" + fileName + "\" already exists.\n"
						+ "Do you want to replace it?", "Confirm Replace File", JOptionPane.YES_NO_CANCEL_OPTION);
				if (res == JOptionPane.CANCEL_OPTION)
					return null;
				if (res != JOptionPane.YES_OPTION)
					continue;
			}

			return selectedFile;
		}
	}

	private boolean exportAbcAs()
	{
		exportSuccessfulLabel.setVisible(false);

		if (abcSong == null)
		{
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		File exportFile = abcSong.getExportFile();
		File allowOverwriteFile = allowOverwriteExportFile ? exportFile : null;

		if (exportFile == null || exportFilenameTemplate.isEnabled())
		{
			String defaultFolder = Util.getLotroMusicPath(false).getAbsolutePath();
			String folder = prefs.get("exportDialogFolder", defaultFolder);
			if (!new File(folder).exists())
				folder = defaultFolder;
			
			String fileName = "mySong.abc";
			
			if (exportFilenameTemplate.isEnabled())
			{
				fileName = exportFilenameTemplate.formatName();
			}
			else
			{
				exportFile = abcSong.getSourceFile();
				if (exportFile == null)
				{
					fileName = abcSong.getSequenceInfo().getFileName();
				} else {
					fileName = exportFile.getName(); 
				}
			}

			int dot = fileName.lastIndexOf('.');
			if (dot > 0)
				fileName = fileName.substring(0, dot);
			else if (dot == 0)
				fileName = "";
			fileName = StringCleaner.cleanForFileName(fileName);
			fileName += ".abc";

			exportFile = new File(folder, fileName);
		}

		exportFile = doSaveDialog(exportFile, allowOverwriteFile, ".abc", new ExtensionFileFilter(
				"ABC files (*.abc, *.txt)", "abc", "txt"));

		if (exportFile == null)
		{
			return false;
		}

		prefs.put("exportDialogFolder", exportFile.getAbsoluteFile().getParent());

		abcSong.setExportFile(exportFile);
		allowOverwriteExportFile = true;
		return finishExportAbc();
	}

	private boolean shouldExportAbcAs()
	{
		return saveSettings.showExportFileChooser || !allowOverwriteExportFile || abcSong.getExportFile() == null
				|| !abcSong.getExportFile().exists();
	}

	private boolean exportAbc()
	{
		exportSuccessfulLabel.setVisible(false);
		if (abcSong == null)
		{
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (shouldExportAbcAs())
			return exportAbcAs();

		return finishExportAbc();
	}

	private boolean finishExportAbc()
	{
		exportSuccessfulLabel.setVisible(false);
		commitAllFields();

		try
		{
			StringCleaner.cleanABC = saveSettings.convertABCStringsToBasicAscii;
			abcSong.exportAbc(abcSong.getExportFile());

			SwingUtilities.invokeLater(() -> {
				exportSuccessfulLabel.setText(abcSong.getExportFile().getName());
				exportSuccessfulLabel.setToolTipText("Exported " + abcSong.getExportFile().getName());
				exportSuccessfulLabel.setVisible(true);
				if (exportLabelHideTimer == null)
				{
					exportLabelHideTimer = new Timer(8000, e -> exportSuccessfulLabel.setVisible(false));
					exportLabelHideTimer.setRepeats(false);
				}
				exportLabelHideTimer.stop();
				exportLabelHideTimer.start();
				onSaveAndExportSettingsChanged();
			});
			return true;
		}
		catch (FileNotFoundException e)
		{
			JOptionPane.showMessageDialog(this, "Failed to create file!\n" + e.getMessage(), "Failed to create file",
					JOptionPane.ERROR_MESSAGE);
			return false;
		}
		catch (IOException | AbcConversionException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private boolean saveAs()
	{
		if (abcSong == null)
		{
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		File saveFile = abcSong.getSaveFile();
		File allowOverwriteFile = allowOverwriteSaveFile ? saveFile : null;

		if (saveFile == null || exportFilenameTemplate.isEnabled())
		{
			String defaultFolder;
			if (abcSong.getExportFile() != null)
				defaultFolder = abcSong.getExportFile().getAbsoluteFile().getParent();
			else
				defaultFolder = Util.getLotroMusicPath(false).getAbsolutePath();

			String folder = prefs.get("saveDialogFolder", defaultFolder);
			if (!new File(folder).exists())
				folder = defaultFolder;
			
			String fileName = "mySong.msx";
			
			if (exportFilenameTemplate.isEnabled())
			{
				fileName = exportFilenameTemplate.formatName();
			}
			else
			{
				saveFile = abcSong.getExportFile();
				if (saveFile == null)
					saveFile = abcSong.getSourceFile();
				if (saveFile == null)
					saveFile = new File(folder, abcSong.getSequenceInfo().getFileName());
				fileName = saveFile.getName();
			}

			int dot = fileName.lastIndexOf('.');
			if (dot > 0)
				fileName = fileName.substring(0, dot);
			fileName += AbcSong.MSX_FILE_EXTENSION;

			saveFile = new File(folder, fileName);
		}

		saveFile = doSaveDialog(saveFile, allowOverwriteFile, AbcSong.MSX_FILE_EXTENSION, new ExtensionFileFilter(
				AbcSong.MSX_FILE_DESCRIPTION_PLURAL + " (*" + AbcSong.MSX_FILE_EXTENSION + ")",
				AbcSong.MSX_FILE_EXTENSION_NO_DOT));

		if (saveFile == null)
			return false;

		prefs.put("saveDialogFolder", saveFile.getAbsoluteFile().getParent());
		abcSong.setSaveFile(saveFile);
		allowOverwriteSaveFile = true;
		return finishSave();
	}

	private boolean save()
	{
		if (abcSong == null)
		{
			JOptionPane.showMessageDialog(this, "No ABC Song is open", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		if (!allowOverwriteSaveFile || abcSong.getSaveFile() == null || !abcSong.getSaveFile().exists())
		{
			return saveAs();
		}

		return finishSave();
	}

	private boolean finishSave()
	{
		commitAllFields();

		try
		{
			XmlUtil.saveDocument(abcSong.saveToXml(), abcSong.getSaveFile());
		}
		catch (FileNotFoundException e)
		{
			JOptionPane.showMessageDialog(this, "Failed to create file!\n" + e.getMessage(), "Failed to create file",
					JOptionPane.ERROR_MESSAGE);

			return false;
		}
		catch (IOException | TransformerException e)
		{
			JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		setAbcSongModified(false);
		return true;
	}
	
	private boolean expandMidi()
	{
		if (abcSong == null || abcSong.getSourceFile() == null) {
			JOptionPane.showMessageDialog(this, "No midi loaded", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		if (SequenceInfo.standard.equals("ABC")) {
			JOptionPane.showMessageDialog(this, "Cannot expand ABC song", "Error", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		
		if (abcSong.getSourceFile().getName().startsWith("expanded_")) {
			JOptionPane.showMessageDialog(this, "This midi has already been expanded", "Error", JOptionPane.ERROR_MESSAGE);
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
			String fileName = "expanded_"+saveFile.getName();
			Path path = Paths.get(saveFile.getAbsolutePath());
			String directory = path.getParent().toString();
						
			int dot = fileName.lastIndexOf('.');
			if (dot > 0)
				fileName = fileName.substring(0, dot);
			fileName += ".mid";

			saveFile = new File(directory, fileName);
		}

		saveFile = doSaveDialog(saveFile, saveFile, ".mid", new ExtensionFileFilter(
				"MIDI songs (*.mid)", "mid"));

		if (saveFile == null)
			return false;

		return finishExpand(saveFile);
	}

	private boolean finishExpand(File saveFile)
	{
		try {
			Sequence sequence2 = abcSong.getSequenceInfo().split();
			if (sequence2 == null) {
				JOptionPane.showMessageDialog(this, "Something went wrong in the splitting process", "Error", JOptionPane.ERROR_MESSAGE);
				return false;
			}
			int[] types = MidiSystem.getMidiFileTypes(sequence2);
			if (types.length != 0) {
				//expandedFile.delete();
				//expandedFile.createNewFile();
				System.out.println("Writing type "+types[types.length-1]+" expanded midi as '"+saveFile.getAbsolutePath()+"'");
				MidiSystem.write(sequence2, types[types.length-1], saveFile);
			} else {
				JOptionPane.showMessageDialog(this, "Something went wrong when in midi type handling", "Error", JOptionPane.ERROR_MESSAGE);
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
		
		int result = JOptionPane.showConfirmDialog(this, "Would you also like to load the new expanded midi?", "Expanded MIDI", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		
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
	private static class MyFormattedTextField extends JFormattedTextField
	{
		public MyFormattedTextField(Object value, int columns)
		{
			super(value);
			setColumns(columns);
		}

		@Override protected void processFocusEvent(FocusEvent e)
		{
			super.processFocusEvent(e);
			if (e.getID() == FocusEvent.FOCUS_GAINED)
				selectAll();
		}
	}
}
