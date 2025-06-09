package com.digero.abcplayer;

import java.awt.AWTKeyStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import com.digero.abcplayer.view.AbcPlayerSettingsDialog;
import com.digero.abcplayer.view.AbcPlaylistPanel;
import com.digero.abcplayer.view.AbcPlaylistPanel.PlaylistEvent;
import com.digero.abcplayer.view.HighlightAbcNotesFrame;
import com.digero.abcplayer.view.TrackListPanel;
import com.digero.abcplayer.view.TrackListPanelCallback;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.abctomidi.FileAndData;
import com.digero.common.icons.IconLoader;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.midi.VolumeTransceiver;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.FileFilterDropListener;
import com.digero.common.util.Listener;
import com.digero.common.util.LotroParseException;
import com.digero.common.util.ParseException;
import com.digero.common.util.Themer;
import com.digero.common.util.Util;
import com.digero.common.util.Version;
import com.digero.common.view.AboutDialog;
import com.digero.common.view.AudioExportManager;
import com.digero.common.view.BarNumberLabel;
import com.digero.common.view.NativeVolumeBar;
import com.digero.common.view.SongPositionBar;
import com.digero.common.view.SongPositionLabel;
import com.digero.common.view.TempoBar;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class AbcPlayer extends JFrame implements TableLayoutConstants, MidiConstants, TrackListPanelCallback {
	private static final ExtensionFileFilter ABC_FILE_FILTER = new ExtensionFileFilter("ABC Files and Playlists", Util.ABC_FILE_EXTENSION_NO_DOT, Util.TXT_FILE_EXTENSION_NO_DOT, Util.ABCP_FILE_EXTENSION_NO_DOT);
	public static final String APP_NAME = "ABC Player";
	private static final String APP_NAME_LONG = APP_NAME + " for The Lord of the Rings Online";
	private static final String APP_URL = "https://github.com/digero/maestro/";
	static Version APP_VERSION = new Version(0, 0, 0);

	private static AbcPlayer mainWindow = null;
	
	private static final boolean defaultTimerCountdown = false;

	private JMenu recentItems;
	private Queue<String> recentQueue;
	private int recentMaxItems = 11;

	private static ServerSocket serverSocket;

	public static void main(String[] args) {

		try {
			Properties props = new Properties();
			props.load(AbcPlayer.class.getResourceAsStream("version.txt"));
			String versionString = props.getProperty("version.AbcPlayer");
			if (versionString != null)
				APP_VERSION = Version.parseVersion(versionString);
		} catch (IOException ex) {
		}

		if (!openPort() && args != null && args.length > 0 && args[0].length() > 3) {
			sendArgsToPort(args);
			return;
		}

		System.setProperty("sun.sound.useNewAudioEngine", "true");

		try {
			Preferences prefs = Preferences.userNodeForPackage(AbcPlayer.class).node("miscSettings");
			Themer.setLookAndFeel(prefs.get("theme", Themer.FLAT_LIGHT_THEME), prefs.getInt("fontSize", Themer.DEFAULT_FONT_SIZE));
		} catch (Exception e) {
		}

		mainWindow = new AbcPlayer();
//		mainWindow.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainWindow.setVisible(true);
		mainWindow.openSongFromCommandLine(args);
		try {
			ready();
		} catch (UnsatisfiedLinkError err) {
			// Ignore (we weren't started via WinRun4j)
		}
	}

	public static native boolean isVolumeSupported();

	private static boolean isVolumeSupportedSafe() {
		try {
			return isVolumeSupported();
		} catch (UnsatisfiedLinkError err) {
			return false;
		}
	}

	public static native float getVolume();

	public static native void setVolume(float volume);

	public static void onVolumeChanged() {
		if (mainWindow != null && mainWindow.volumeBar != null)
			mainWindow.volumeBar.repaint();
	}

	/** Tells the WinRun4J launcher that we're ready to accept activate() calls. */
	public static native void ready();

	/** A new activation (a.k.a. a file was opened) */
	public static void activate(String[] args) {
		/*
		 * if (args != null && args.length > 0 && args[0] != null) {
		 * System.out.println(" Processing file path ("+args[0].length()+" chars):\n" +args[0]); }
		 */
		mainWindow.openSongFromCommandLine(args);
	}

	/** A new activation from WinRun4J 64bit (a.k.a. a file was opened) */
	public static void activate(String arg0) {
		final String[] args = { arg0.substring(1, arg0.length() - 1) };
		mainWindow.openSongFromCommandLine(args);
	}

	public static void execute(String cmdLine) {
		mainWindow.openSongFromCommandLine(new String[] { cmdLine });
	}

	private final SequencerWrapper sequencer;
	private boolean useLotroInstruments = true;

	private FileFilterDropListener dropListener;
	private DropTarget mainWindowDropTarget;

	private JPanel content;

	private JLabel titleLabel;

	private TrackListPanel trackListPanel;
	
	private boolean showPlaylistView = false;
	private JPanel mainCardPanel;
	private CardLayout mainCardPanelLayout;
	private JPanel songViewPanel;
	private AbcPlaylistPanel playlistViewPanel;

	private SongPositionBar songPositionBar;
	private SongPositionLabel songPositionLabel;
	private BarNumberLabel barNumberLabel;
	private JLabel tempoLabel;
	private TempoBar tempoBar;
	private NativeVolumeBar volumeBar;
	private VolumeTransceiver volumeTransceiver;

	private ImageIcon playIcon;
	private ImageIcon pauseIcon;
	private ImageIcon stopIcon;
	private ImageIcon playIconDisabled;
	private ImageIcon pauseIconDisabled;
	private ImageIcon stopIconDisabled;
	private ImageIcon playlistIcon;
	private JButton playButton;
	private JButton stopButton;
	private JButton playlistToggleButton;

	private JCheckBoxMenuItem lotroErrorsMenuItem;
	private JCheckBoxMenuItem stereoMenuItem;
	private JCheckBoxMenuItem showFullPartNameMenuItem;
	private JCheckBoxMenuItem showAbcViewMenuItem;
	private JCheckBoxMenuItem countdownMenuItem;

	private JFileChooser openFileDialog;
	private JFileChooser saveFileDialog;
	private JFileChooser exportFileDialog;

	private HighlightAbcNotesFrame abcViewFrame;
	
	private AudioExportManager audioExporter;

	private final Map<Integer, LotroInstrument> instrumentOverrideMap = new HashMap<>();
	private List<FileAndData> abcData;
	private AbcInfo abcInfo = new AbcInfo();

	private Preferences prefs = Preferences.userNodeForPackage(AbcPlayer.class);
	
	private boolean playedFromFiletree = false;

//	private boolean isExporting = false;

	public AbcPlayer() {
		super(APP_NAME);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				if (sequencer != null)
					sequencer.close();
			}
		});

		try {
			List<Image> icons = new ArrayList<>();
			icons.add(ImageIO.read(IconLoader.class.getResourceAsStream("abcplayer_16.png")));
			icons.add(ImageIO.read(IconLoader.class.getResourceAsStream("abcplayer_32.png")));
			setIconImages(icons);
		} catch (Exception ex) {
			// Ignore
		}

		dropListener = new FileFilterDropListener(true, Util.ABC_FILE_EXTENSION_NO_DOT, Util.TXT_FILE_EXTENSION_NO_DOT, Util.ABCP_FILE_EXTENSION_NO_DOT);
		dropListener.addActionListener(e -> {
			FileFilterDropListener l = (FileFilterDropListener) e.getSource();
			boolean append = (l.getDropEvent().getDropAction() == DnDConstants.ACTION_COPY);
			playlistViewPanel.resetPlaylistPosition();
			SwingUtilities.invokeLater(new OpenSongRunnable(append, l.getDroppedFiles().toArray(new File[0])));
		});
		mainWindowDropTarget = new DropTarget(this, dropListener);

		if (isVolumeSupportedSafe()) {
			volumeTransceiver = null;
		} else {
			volumeTransceiver = new VolumeTransceiver();
			volumeTransceiver.setVolume(prefs.getInt("volumizer", MidiConstants.MAX_VOLUME));
		}
		volumeBar = new NativeVolumeBar(new NativeVolumeBar.Callback() {
			@Override
			public void setVolume(int volume) {
				if (volumeTransceiver == null)
					AbcPlayer.setVolume((float) volume / NativeVolumeBar.MAX_VOLUME);
				else {
					volumeTransceiver.setVolume(volume);
					prefs.putInt("volumizer", volume);
				}
			}

			@Override
			public int getVolume() {
				if (volumeTransceiver == null)
					return (int) (AbcPlayer.getVolume() * NativeVolumeBar.MAX_VOLUME);
				else
					return volumeTransceiver.getVolume();
			}
		});

		try {
			if (useLotroInstruments) {
				sequencer = new LotroSequencerWrapper();

				if (LotroSequencerWrapper.getLoadLotroSynthError() != null) {
					Version requredJavaVersion = new Version(1, 7, 0, 0);
					Version recommendedJavaVersion = new Version(1, 7, 0, 25);

					JPanel errorMessage = new JPanel(new BorderLayout(0, 12));
					errorMessage
							.add(new JLabel("<html><b>There was an error loading the LOTRO instrument sounds</b><br>"
									+ "Playback will use standard MIDI instruments instead<br>"
									+ "(drums do not sound good in this mode).</html>"), BorderLayout.NORTH);

					final String JAVA_URL = "http://www.java.com";
					if (requredJavaVersion.compareTo(Version.parseVersion(System.getProperty("java.version"))) > 0) {
						JLabel update = new JLabel("<html>It is recommended that you install Java "
								+ recommendedJavaVersion.getMinor() + " update " + recommendedJavaVersion.getRevision()
								+ " or later.<br>" + "Get the latest version from <a href='" + JAVA_URL + "'>"
								+ JAVA_URL + "</a>.</html>");
						update.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
						update.addMouseListener(new MouseAdapter() {
							@Override
							public void mouseClicked(MouseEvent e) {
								if (e.getButton() == MouseEvent.BUTTON1) {
									Util.openURL(JAVA_URL);
								}
							}
						});
						errorMessage.add(update, BorderLayout.CENTER);
					}

					errorMessage.add(new JLabel(
							"<html>Error details:<br>" + LotroSequencerWrapper.getLoadLotroSynthError() + "</html>"),
							BorderLayout.SOUTH);

					JOptionPane.showMessageDialog(this, errorMessage, APP_NAME + " failed to load LOTRO instruments",
							JOptionPane.ERROR_MESSAGE);

					useLotroInstruments = false;
				}
				sequencer.createReceiver();// To make sure its there
			} else {
				sequencer = new SequencerWrapper();
			}
			
			sequencer.setUseSequenceTempoFactor(true);

			if (volumeTransceiver != null)
				sequencer.addTransceiver(volumeTransceiver);
		} catch (MidiUnavailableException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "MIDI error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);

			// This will never be hit, but convinces the compiler that
			// the sequencer field will never be uninitialized
			throw new RuntimeException();
		}
		
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (playlistViewPanel.promptSavePlaylist()) {
					setVisible(false);
					dispose();
					System.exit(0);
				}
			}
		});

		content = new JPanel(new TableLayout(//
				new double[] { 4, FILL, 4 }, //
				new double[] { FILL, 8, PREFERRED }));
		setContentPane(content);

		titleLabel = new JLabel(" ");
		Font f = titleLabel.getFont();
		titleLabel.setFont(f.deriveFont(Font.BOLD, f.getSize2D() * 1.3f));
		titleLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		trackListPanel = new TrackListPanel(sequencer, this);
		JScrollPane trackListScroller = new JScrollPane(trackListPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		trackListScroller.getVerticalScrollBar().setUnitIncrement(TrackListPanel.TRACKLIST_ROWHEIGHT);
		trackListScroller.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, Color.GRAY));

//		JPanel controlPanel = new JPanel(new TableLayout(//
//				new double[] { 4, SongPositionBar.SIDE_PAD, 0.5, 4, PREFERRED, 4, PREFERRED, 4, 0.5,
//						SongPositionBar.SIDE_PAD, 4, PREFERRED, 4 }, //
//				new double[] { 4, PREFERRED, 4, PREFERRED, 4 }));
		
		JPanel controlPanel = new JPanel(new MigLayout("fillx, wrap 6", "[][grow -1][grow -1][grow -1][][grow -1]"));
		
		songPositionBar = new SongPositionBar(sequencer);
		songPositionLabel = new SongPositionLabel(sequencer);
		songPositionLabel.countdown = prefs.getBoolean("countdownMenuItem", defaultTimerCountdown);
		barNumberLabel = new BarNumberLabel(sequencer, null, false);
		barNumberLabel.setToolTipText("Bar number");

		playIcon = IconLoader.getImageIcon("play.png");
		playIconDisabled = IconLoader.getDisabledIcon("play.png");
		pauseIcon = IconLoader.getImageIcon("pause.png");
		pauseIconDisabled = IconLoader.getDisabledIcon("pause.png");
		stopIcon = IconLoader.getImageIcon("stop.png");
		stopIconDisabled = IconLoader.getDisabledIcon("stop.png");
		
		final Insets playControlButtonMargin = new Insets(5, 20, 5, 20);
		if (!Themer.isDarkMode()) {
			playlistIcon = IconLoader.getImageIcon("playlist.png");
		} else {
			playlistIcon = IconLoader.getImageIcon("playlist_dark.png");
		}
		

		playButton = new JButton(playIcon);
		playButton.setDisabledIcon(playIconDisabled);
		playButton.setEnabled(false);
		playButton.setMargin(playControlButtonMargin);
		playButton.addActionListener(e -> playPause());

		stopButton = new JButton(stopIcon);
		stopButton.setDisabledIcon(stopIconDisabled);
		stopButton.setEnabled(false);
		stopButton.setMargin(playControlButtonMargin);
		stopButton.addActionListener(e -> stop());
		
		playlistToggleButton = new JButton(playlistIcon);
		playlistToggleButton.setToolTipText("Toggle between the current song view and the ABC browser / playlist view");
		playlistToggleButton.setFocusable(false);
		playlistToggleButton.setMargin(playControlButtonMargin);
		playlistToggleButton.addActionListener(e -> {
			showPlaylistView = !showPlaylistView;
			updatePlaylistCardView();
		});

		tempoBar = new TempoBar(sequencer);

		tempoLabel = new JLabel();
		tempoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		updateTempoLabel();
		JPanel tempoPanel = new JPanel(new BorderLayout());
		tempoPanel.add(tempoLabel, BorderLayout.NORTH);
		tempoPanel.add(tempoBar, BorderLayout.CENTER);

		JPanel volumePanel = new JPanel(new BorderLayout());
		JLabel volumeLabel = new JLabel("Volume");
		volumeLabel.setHorizontalAlignment(SwingConstants.CENTER);
		volumePanel.add(volumeLabel, BorderLayout.NORTH);
		volumePanel.add(volumeBar, BorderLayout.CENTER);
		
		controlPanel.add(songPositionBar, "spanx 5, growx");
		controlPanel.add(songPositionLabel, "right");
		controlPanel.add(tempoPanel, "center");
		controlPanel.add(playButton, "right");
		controlPanel.add(stopButton);
		controlPanel.add(playlistToggleButton);
		controlPanel.add(volumePanel, "center");
		controlPanel.add(barNumberLabel, "right");

		sequencer.addChangeListener(evt -> {
			SequencerProperty p = evt.getProperty();
			if (!p.isInMask(SequencerProperty.THUMB_POSITION_MASK)) {
//				p.printSetMasks();
				updateButtonStates();
			}
			
			if (p == SequencerProperty.SONG_ENDED) {
				playlistViewPanel.advanceToNextSongIfNeeded();
			}

			if (p.isInMask(SequencerProperty.TEMPO.mask | SequencerProperty.SEQUENCE.mask)) {
				updateTempoLabel();
			}
		});

		songViewPanel = new JPanel(new TableLayout(//
				new double[] { 4, FILL, 4 }, //
				new double[] { PREFERRED, 0, FILL }));
		songViewPanel.setBorder(BorderFactory.createEmptyBorder());
		songViewPanel.add(titleLabel, "1, 0");
		songViewPanel.add(trackListScroller, "1, 2");
		
		playlistViewPanel = new AbcPlaylistPanel(prefs.node("playlist"));
		playlistViewPanel.setPlaylistListener(new Listener<PlaylistEvent>(){
			@Override
			public void onEvent(PlaylistEvent e) {
				switch(e.getType()) {
				case PLAY_FROM_ABCINFO:
					AbcInfo inf = (AbcInfo)(e.getSource());
					SwingUtilities.invokeLater(new OpenSongRunnable(false, inf.getSourceFiles().get(0)));
					break;
				case PLAY_FROM_FILE:
					playlistViewPanel.resetPlaylistPosition();
					File f = (File)(e.getSource());
					playedFromFiletree = true;
					SwingUtilities.invokeLater(new OpenSongRunnable(false, f));
					if (e.getShowSongView()) {
						showPlaylistView = false;
						updatePlaylistCardView();
					}
					break;
				case CLOSE_SONG:
					closeSong();
					break;
				case PLAYLIST_OPENED:
					if (abcData == null || !sequencer.isRunning()) {
						showPlaylistView = true;
						updatePlaylistCardView();
					}
					break;
				default: break;
				}
			}
		});

		mainCardPanelLayout = new CardLayout();
		mainCardPanel = new JPanel(mainCardPanelLayout);
		mainCardPanel.add(songViewPanel, "song");
		mainCardPanel.add(playlistViewPanel, "playlist");
		
		add(mainCardPanel, "0, 0, 2, 0");
		add(controlPanel, "1, 2");
		
		audioExporter = new AudioExportManager(this, AbcPlayer.APP_NAME + AbcPlayer.APP_VERSION, prefs);

		initMenu();

		updateButtonStates();

		setMinimumSize(new Dimension(320, 168));
		Util.initWinBounds(this, prefs.node("window"), 450, 282);
		
		Set<AWTKeyStroke> emptyKeys = new HashSet<>();
		setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, emptyKeys);
		setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, emptyKeys);
		
		ActionListener tabListener = e -> {
			showPlaylistView = !showPlaylistView;
			updatePlaylistCardView();
		};
		this.getRootPane().registerKeyboardAction(tabListener, KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
	}
	
	private void updatePlaylistCardView() {
		// Drop is handled by playlist table if in playlist view
		AbcPlayer.this.setDropTarget(showPlaylistView? null : mainWindowDropTarget);
		mainCardPanelLayout.show(mainCardPanel, showPlaylistView? "playlist" : "song");
	}

	@Override
	public void setTrackInstrumentOverride(int trackIndex, LotroInstrument instrument) {
		instrumentOverrideMap.put(trackIndex, instrument);
		refreshSequence();
	}

	@Override
	public void showHighlightPanelForTrack(int trackIndex) {
		updateAbcView(/* showIfHidden = */true, /* retainScrollPosition = */false);

		abcViewFrame.scrollToLineNumber(abcInfo.getPartStartLine(trackIndex));
		abcViewFrame.setFollowedTrackNumber(trackIndex);
	}

	private void updateAbcView(boolean showIfHidden, boolean retainScrollPosition) {
		boolean hasAbcData = (abcData != null && abcInfo != null);

		if (showAbcViewMenuItem != null)
			showAbcViewMenuItem.setEnabled(hasAbcData);

		boolean isVisible = (abcViewFrame != null && abcViewFrame.isVisible());
		if (!isVisible && !showIfHidden)
			return;

		if (abcViewFrame == null) {
			abcViewFrame = new HighlightAbcNotesFrame(sequencer, prefs);
			abcViewFrame.setTitle(getTitle());
			abcViewFrame.setIconImages(getIconImages());
			abcViewFrame.addDropListener(dropListener);
			abcViewFrame.setShowFullPartName(showFullPartNameMenuItem.isSelected());
			abcViewFrame.addWindowListener(new WindowAdapter() {
				@Override
				public void windowOpened(WindowEvent e) {
					if (showAbcViewMenuItem != null) {
						showAbcViewMenuItem.setSelected(true);
						prefs.putBoolean("showAbcViewMenuItem", true);
					}
				}

				@Override
				public void windowClosed(WindowEvent e) {
					if (showAbcViewMenuItem != null) {
						showAbcViewMenuItem.setSelected(false);
						prefs.putBoolean("showAbcViewMenuItem", false);
					}
				}
			});
		}

		if (!hasAbcData) {
			abcViewFrame.setFollowedTrackNumber(-1);
			abcViewFrame.clearLinesAndRegions();
		} else {
			List<String> lines = new ArrayList<>();
			for (FileAndData entry : abcData)
				lines.addAll(entry.lines);

			final int startLine = 0;
			abcViewFrame.setLinesAndRegions(lines, startLine, abcInfo);

			if (!retainScrollPosition)
				abcViewFrame.scrollToLineNumber(startLine);
		}

		if (showIfHidden) {
			if (!isVisible)
				abcViewFrame.setVisible(true);

			abcViewFrame.toFront();
		}
	}

	private void updateTitleLabel() {
		if (abcInfo == null) {
			titleLabel.setText(" ");
			titleLabel.setToolTipText("");
			return;
		}
		
		String title = abcInfo.getTitle();
		String artist = abcInfo.getComposer_MaybeNull();

		if (artist != null) {
			titleLabel.setText("<html>" + title + "&ensp;<span style='font-size:"
					+ UIManager.getFont("defaultFont").getSize()
					+ "pt; font-weight:normal'>" + artist
					+ "</span></html>");
		} else {
			titleLabel.setText(title);
		}

		String tooltip = title;
		if (artist != null)
			tooltip += " - " + artist;
		titleLabel.setToolTipText(tooltip);
	}

	private void updateTempoLabel() {
		float tempo = sequencer.getTempoFactor();
		int t = Math.round(tempo * 100);
		tempoLabel.setText("Tempo: " + t + "%");
	}

	private void initMenu() {
		JMenuBar mainMenu = new JMenuBar();
		setJMenuBar(mainMenu);

		JMenu fileMenu = mainMenu.add(new JMenu(" File "));
		fileMenu.setMnemonic(KeyEvent.VK_F);

		JMenuItem open = fileMenu.add(new JMenuItem("Open ABC file(s)..."));
		open.setMnemonic(KeyEvent.VK_O);
		open.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		open.addActionListener(e -> openSongDialog());

		JMenuItem openAppend = fileMenu.add(new JMenuItem("Append ABC file(s)..."));
		openAppend.setMnemonic(KeyEvent.VK_D);
		openAppend.setAccelerator(
				KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
		openAppend.addActionListener(e -> appendSongDialog());

		final JMenuItem pasteMenuItem = fileMenu.add(new JMenuItem("Open from clipboard"));
		pasteMenuItem.setMnemonic(KeyEvent.VK_P);
		pasteMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
		pasteMenuItem.addActionListener(e -> {
			ArrayList<File> files = new ArrayList<>();
			if (getFileListFromClipboard(files)) {
				openSong(files.toArray(new File[0]));
				return;
			}

			ArrayList<String> lines = new ArrayList<>();
			if (getAbcDataFromClipboard(lines, false)) {
				List<FileAndData> filesData = new ArrayList<>();
				filesData.add(new FileAndData(new File("[Clipboard]"), lines));
				openSong(filesData);
				return;
			}

			Toolkit.getDefaultToolkit().beep();
		});

		final JMenuItem pasteAppendMenuItem = fileMenu.add(new JMenuItem("Append from clipboard"));
		pasteAppendMenuItem.setMnemonic(KeyEvent.VK_N);
		pasteAppendMenuItem.setAccelerator(
				KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
		pasteAppendMenuItem.addActionListener(e -> {
			ArrayList<File> files = new ArrayList<>();
			if (getFileListFromClipboard(files)) {
				appendSong(files.toArray(new File[0]));
				return;
			}

			ArrayList<String> lines = new ArrayList<>();
			if (getAbcDataFromClipboard(lines, true)) {
				List<FileAndData> data = new ArrayList<>();
				data.add(new FileAndData(new File("[Clipboard]"), lines));
				appendSong(data);
				return;
			}

			Toolkit.getDefaultToolkit().beep();
		});

		recentQueue = new LinkedList<>();
		recentItems = new JMenu();
		recentItems.setText("Recent files...");
		recentPrefsRead();
		fileMenu.add(recentItems);

		fileMenu.addSeparator();

		final JMenuItem saveMenuItem = fileMenu.add(new JMenuItem("Save a copy as ABC..."));
		saveMenuItem.setMnemonic(KeyEvent.VK_S);
		saveMenuItem.setAccelerator(
				KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
		saveMenuItem.addActionListener(e -> {
			if (!sequencer.isLoaded()) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			saveSongDialog();
		});
		
		final JMenuItem exportMp3MenuItem = fileMenu.add(new JMenuItem("Save as MP3..."));
		exportMp3MenuItem.addActionListener(e -> {
			if (!sequencer.isLoaded() || audioExporter.isExporting()) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			File abcFile = null;
			if (!abcData.isEmpty())
				abcFile = abcData.get(0).file;
			audioExporter.exportMp3Builtin((LotroSequencerWrapper)sequencer, abcFile, APP_NAME_LONG, APP_NAME);
		});

		final JMenuItem exportWavMenuItem = fileMenu.add(new JMenuItem("Save as Wave file..."));
		exportWavMenuItem.setMnemonic(KeyEvent.VK_E);
		exportWavMenuItem.setAccelerator(
				KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
		exportWavMenuItem.addActionListener(e -> {
			if (!sequencer.isLoaded() || audioExporter.isExporting()) {
				Toolkit.getDefaultToolkit().beep();
				return;
			}
			File abcFile = null;
			if (!abcData.isEmpty())
				abcFile = abcData.get(0).file;
			audioExporter.exportWav((LotroSequencerWrapper)sequencer, abcFile);
		});

		fileMenu.addSeparator();
		
		JMenuItem closeSong = fileMenu.add(new JMenuItem("Close Song"));
		closeSong.addActionListener(e -> {
			if (abcInfo != null) {
				closeSong();
				playlistViewPanel.resetPlaylistPosition();
			}
		});

		JMenuItem exit = fileMenu.add(new JMenuItem("Exit"));
		exit.setMnemonic(KeyEvent.VK_X);
		exit.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, InputEvent.ALT_DOWN_MASK));
		exit.addActionListener(e -> {
			if (playlistViewPanel.promptSavePlaylist()) {
				setVisible(false);
				dispose();
				System.exit(0);
			}
		});

		fileMenu.addMenuListener(new MenuListener() {
			@Override
			public void menuSelected(MenuEvent e) {
				boolean pasteEnabled = getFileListFromClipboard(null);
				pasteMenuItem.setEnabled(pasteEnabled || getAbcDataFromClipboard(null, false));
				pasteAppendMenuItem.setEnabled(pasteEnabled || getAbcDataFromClipboard(null, true));

				boolean saveEnabled = sequencer.isLoaded();
				saveMenuItem.setEnabled(saveEnabled);
				exportWavMenuItem.setEnabled(saveEnabled && !audioExporter.isExporting());
				exportMp3MenuItem.setEnabled(saveEnabled && !audioExporter.isExporting());
				closeSong.setEnabled(saveEnabled);
			}

			@Override
			public void menuDeselected(MenuEvent e) {
				menuCanceled(e);
			}

			@Override
			public void menuCanceled(MenuEvent e) {
				pasteMenuItem.setEnabled(true);
				pasteAppendMenuItem.setEnabled(true);

				saveMenuItem.setEnabled(true);
				exportWavMenuItem.setEnabled(true);
				exportMp3MenuItem.setEnabled(true);
				closeSong.setEnabled(true);
			}
		});

		JMenu editMenu = mainMenu.add(new JMenu(" Edit "));
		editMenu.setMnemonic(KeyEvent.VK_E);
		JMenuItem select = new JMenuItem("Select all");
		JMenuItem deselect = new JMenuItem("Deselect all");
		editMenu.add(select);
		editMenu.add(deselect);
		select.addActionListener(e -> trackListPanel.selectAll());
		deselect.addActionListener(e -> trackListPanel.deselectAll());

		JMenu toolsMenu = mainMenu.add(new JMenu(" Tools "));
		toolsMenu.setMnemonic(KeyEvent.VK_T);

		toolsMenu.add(lotroErrorsMenuItem = new JCheckBoxMenuItem("Ignore LOTRO-specific errors"));
		lotroErrorsMenuItem.setSelected(prefs.getBoolean("ignoreLotroErrors", false));
		lotroErrorsMenuItem
				.addActionListener(e -> prefs.putBoolean("ignoreLotroErrors", lotroErrorsMenuItem.isSelected()));

		toolsMenu.add(stereoMenuItem = new JCheckBoxMenuItem("Stereo pan in multi-part songs"));
		stereoMenuItem.setToolTipText("<html>Separates the parts of a multi-part song by <br>"
				+ "panning them towards the left or right speaker.</html>");
		stereoMenuItem.setSelected(prefs.getBoolean("stereoMenuItem", true));
		stereoMenuItem.addActionListener(e -> {
			prefs.putBoolean("stereoMenuItem", stereoMenuItem.isSelected());
			refreshSequence();
		});
		
		toolsMenu.add(countdownMenuItem = new JCheckBoxMenuItem("Countdown instead of up"));
		countdownMenuItem.setToolTipText("<html>The time position indicator will countdown instead of up.</html>");
		countdownMenuItem.setSelected(prefs.getBoolean("countdownMenuItem", defaultTimerCountdown));
		countdownMenuItem.addActionListener(e -> {
			prefs.putBoolean("countdownMenuItem", countdownMenuItem.isSelected());
			songPositionLabel.countdown = countdownMenuItem.isSelected();
		});

		toolsMenu.addSeparator();

		toolsMenu.add(showFullPartNameMenuItem = new JCheckBoxMenuItem("Show full part names"));
		showFullPartNameMenuItem.setSelected(prefs.getBoolean("showFullPartNameMenuItem", false));
		trackListPanel.setShowFullPartName(showFullPartNameMenuItem.isSelected());
		if (abcViewFrame != null)
			abcViewFrame.setShowFullPartName(showFullPartNameMenuItem.isSelected());
		showFullPartNameMenuItem.addActionListener(e -> {
			prefs.putBoolean("showFullPartNameMenuItem", showFullPartNameMenuItem.isSelected());
			trackListPanel.setShowFullPartName(showFullPartNameMenuItem.isSelected());
			if (abcViewFrame != null)
				abcViewFrame.setShowFullPartName(showFullPartNameMenuItem.isSelected());
		});

		final JCheckBoxMenuItem showLineNumbersMenuItem = new JCheckBoxMenuItem("Show line numbers");
		toolsMenu.add(showLineNumbersMenuItem);
		showLineNumbersMenuItem.setSelected(prefs.getBoolean("showLineNumbersMenuItem", true));
		trackListPanel.setShowLineNumbers(showLineNumbersMenuItem.isSelected());
		showLineNumbersMenuItem.addActionListener(e -> {
			prefs.putBoolean("showLineNumbersMenuItem", showLineNumbersMenuItem.isSelected());
			trackListPanel.setShowLineNumbers(showLineNumbersMenuItem.isSelected());
		});

		final JCheckBoxMenuItem showSoloButtonsMenuItem = new JCheckBoxMenuItem("Show track solo buttons");
		toolsMenu.add(showSoloButtonsMenuItem);
		showSoloButtonsMenuItem.setSelected(prefs.getBoolean("showSoloButtonsMenuItem", true));
		trackListPanel.setShowSoloButtons(showSoloButtonsMenuItem.isSelected());
		showSoloButtonsMenuItem.addActionListener(e -> {
			prefs.putBoolean("showSoloButtonsMenuItem", showSoloButtonsMenuItem.isSelected());
			trackListPanel.setShowSoloButtons(showSoloButtonsMenuItem.isSelected());
		});

		final JCheckBoxMenuItem showInstrumentComboBoxesMenuItem = new JCheckBoxMenuItem("Show instrument pickers");
		toolsMenu.add(showInstrumentComboBoxesMenuItem);
		showInstrumentComboBoxesMenuItem.setSelected(prefs.getBoolean("showInstrumentComboBoxesMenuItem", true));
		trackListPanel.setShowInstrumentComboBoxes(showInstrumentComboBoxesMenuItem.isSelected());
		showInstrumentComboBoxesMenuItem.addActionListener(e -> {
			prefs.putBoolean("showInstrumentComboBoxesMenuItem", showInstrumentComboBoxesMenuItem.isSelected());
			trackListPanel.setShowInstrumentComboBoxes(showInstrumentComboBoxesMenuItem.isSelected());
		});

		toolsMenu.addSeparator();
		
		JMenuItem otherOptions = toolsMenu.add(new JMenuItem("More Options..."));
		otherOptions.addActionListener(e -> { doSettingsDialog(); });
		
		toolsMenu.addSeparator();
		
		JMenuItem about = toolsMenu.add(new JMenuItem("About " + APP_NAME + "..."));
		about.setMnemonic(KeyEvent.VK_A);
		about.addActionListener(
				e -> AboutDialog.show(AbcPlayer.this, APP_NAME_LONG, APP_VERSION, APP_URL, "abcplayer_64.png"));

		JMenu abcViewMenu = mainMenu.add(new JMenu(" ABC View "));
		abcViewMenu.setMnemonic(KeyEvent.VK_A);

		abcViewMenu.add(showAbcViewMenuItem = new JCheckBoxMenuItem("Show ABC text"));
		showAbcViewMenuItem.addActionListener(e -> {
			if (showAbcViewMenuItem.isSelected()) {
				updateAbcView(/* showIfHidden = */true, /* retainScrollPosition = */false);
			} else if (abcViewFrame != null) {
				abcViewFrame.setVisible(false);
			}
		});
		
		mainMenu.add(playlistViewPanel.getPlaylistMenu());
	}

	private void recentAdd(final String fileNameToAdd) {
		boolean removed = false;
		if (recentQueue.contains(fileNameToAdd)) {
			removed = recentQueue.remove(fileNameToAdd);
		}
		recentQueue.add(fileNameToAdd);

		if (recentQueue.size() > recentMaxItems) {
			removed = true;
			recentQueue.remove();
		}

		if (removed) {
			recentItems.removeAll();

			for (final String string : recentQueue) {
				JMenuItem item = new JMenuItem(recentFilenameFromPath(string));
				item.addActionListener(evt -> recentActionPerformed(evt, string));
				recentItems.add(item);
			}
		} else {
			JMenuItem newRecent = new JMenuItem(recentFilenameFromPath(fileNameToAdd));
			newRecent.addActionListener(evt -> recentActionPerformed(evt, fileNameToAdd));
			recentItems.add(newRecent);
		}
		recentPrefsWrite();
	}
	
	private void doSettingsDialog() {
		AbcPlayerSettingsDialog dialog = new AbcPlayerSettingsDialog(this, prefs.node("miscSettings"));
		dialog.setVisible(true);
		dialog.dispose();
	}

	private void recentRemove(final String fileNameToRemove) {
		if (recentQueue.remove(fileNameToRemove)) {
			recentItems.removeAll();
			for (final String string : recentQueue) {
				JMenuItem item = new JMenuItem(recentFilenameFromPath(string));
				item.addActionListener(evt -> recentActionPerformed(evt, string));
				recentItems.add(item);
			}
		}
		recentPrefsWrite();
	}

	private void recentPrefsWrite() {
		Object[] recentArray = recentQueue.toArray();
		for (int i = 0; i < recentMaxItems; i++) {
			if (i < recentQueue.size()) {
				prefs.put("recent." + i, (String) recentArray[i]);
			} else {
				prefs.remove("recent." + i);
			}
		}
	}

	private void recentPrefsRead() {
		for (int i = 0; i < recentMaxItems; i++) {
			String entry = prefs.get("recent." + i, null);
			if (entry != null)
				recentQueue.add(entry);
		}
		recentItems.removeAll();

		for (final String string : recentQueue) {
			JMenuItem item = new JMenuItem(recentFilenameFromPath(string));
			item.addActionListener(evt -> recentActionPerformed(evt, string));
			recentItems.add(item);
		}
	}

	private String recentFilenameFromPath(String path) {
		Path p = Paths.get(path);
		String fileName = p.getFileName().toString();
		return fileName;
	}

	private void recentActionPerformed(ActionEvent evt, String title) {
		File[] files = { new File(title) };
		playlistViewPanel.resetPlaylistPosition();
		if (!openSong(files)) {
			// The file could not be opened, removing it from the recent list.
			recentRemove(title);
		}
	}

	private boolean getAbcDataFromClipboard(ArrayList<String> data, boolean checkContents) {
		if (data != null)
			data.clear();

		Transferable xfer = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
		if (xfer == null || !xfer.isDataFlavorSupported(DataFlavor.stringFlavor))
			return false;

		String text;
		try {
			text = (String) xfer.getTransferData(DataFlavor.stringFlavor);
		} catch (UnsupportedFlavorException | IOException e) {
			return false;
		}

		if (!checkContents && data == null)
			return true;

		StringTokenizer tok = new StringTokenizer(text, "\r\n");
		int i = 0;
		boolean isValid = !checkContents;
		while (tok.hasMoreTokens()) {
			String line = tok.nextToken();

			if (!isValid) {
				if (line.startsWith("X:") || line.startsWith("x:")) {
					isValid = true;
					if (data == null)
						break;
				} else {
					String lineTrim = line.trim();
					// If we find a line that's not a comment before the
					// X: line, then this isn't an ABC file
					if (lineTrim.length() > 0 && !lineTrim.startsWith("%")) {
						isValid = false;
						break;
					}
				}
			}

			if (data != null)
				data.add(line);
			else if (i >= 100)
				break;

			i++;
		}

		if (!isValid && data != null)
			data.clear();

		return isValid;
	}

	@SuppressWarnings("unchecked") //
	private boolean getFileListFromClipboard(ArrayList<File> data) {
		if (data != null)
			data.clear();

		Transferable xfer = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
		if (xfer == null || !xfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
			return false;

		List<File> fileList;
		try {
			fileList = (List<File>) xfer.getTransferData(DataFlavor.javaFileListFlavor);
		} catch (UnsupportedFlavorException | IOException e) {
			return false;
		}

		if (fileList.isEmpty())
			return false;

		for (File file : fileList) {
			if (!ABC_FILE_FILTER.accept(file)) {
				if (data != null)
					data.clear();
				return false;
			}

			if (data != null)
				data.add(file);
		}

		return true;
	}

	private void initOpenFileDialog() {
		if (openFileDialog == null) {
			openFileDialog = new JFileChooser(
					prefs.get("openFileDialog.currentDirectory", Util.getLotroMusicPath(true).getAbsolutePath()));

			openFileDialog.setMultiSelectionEnabled(true);
			openFileDialog.setFileFilter(ABC_FILE_FILTER);
		}
	}

	private void openSongDialog() {
		initOpenFileDialog();

		int result = openFileDialog.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			prefs.put("openFileDialog.currentDirectory", openFileDialog.getCurrentDirectory().getAbsolutePath());

			playlistViewPanel.resetPlaylistPosition();
			openSong(openFileDialog.getSelectedFiles());
		}
	}

	private void appendSongDialog() {
		if (this.abcData == null || this.abcData.isEmpty()) {
			openSongDialog();
			return;
		}

		initOpenFileDialog();
		int result = openFileDialog.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			prefs.put("openFileDialog.currentDirectory", openFileDialog.getCurrentDirectory().getAbsolutePath());

			appendSong(openFileDialog.getSelectedFiles());
		}
	}

	private void saveSongDialog() {
		if (this.abcData == null || this.abcData.isEmpty()) {
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		if (saveFileDialog == null) {
			saveFileDialog = new JFileChooser(
					prefs.get("saveFileDialog.currentDirectory", Util.getLotroMusicPath(true).getAbsolutePath()));

			saveFileDialog.setFileFilter(ABC_FILE_FILTER);
		}

		int result = saveFileDialog.showSaveDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			prefs.put("saveFileDialog.currentDirectory", saveFileDialog.getCurrentDirectory().getAbsolutePath());

			String fileName = saveFileDialog.getSelectedFile().getName();
			int dot = fileName.lastIndexOf('.');
			if (dot <= 0 || !fileName.substring(dot).equalsIgnoreCase(Util.ABC_FILE_EXTENSION))
				fileName += Util.ABC_FILE_EXTENSION;

			File saveFileTmp = new File(saveFileDialog.getSelectedFile().getParent(), fileName);
			if (saveFileTmp.exists()) {
				int res = JOptionPane.showConfirmDialog(this, "File " + fileName + " already exists. Overwrite?",
						"Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (res != JOptionPane.YES_OPTION)
					return;
			}

			saveFileDialog.setSelectedFile(saveFileTmp);
			saveSong(saveFileTmp);
		}
	}

	private boolean openSongFromCommandLine(String[] args) {
		mainWindow.setExtendedState(mainWindow.getExtendedState() & ~ICONIFIED);

		if (args.length > 0) {
			File[] argFiles = new File[args.length];
			for (int i = 0; i < args.length; i++) {
				argFiles[i] = new File(args[i]);
			}
			return openSong(argFiles);
		}
		return false;
	}

	private class OpenSongRunnable implements Runnable {
		private File[] abcFiles;
		private boolean append;

		public OpenSongRunnable(boolean append, File... abcFiles) {
			this.append = append;
			this.abcFiles = abcFiles;
		}

		@Override
		public void run() {
			if (append)
				appendSong(abcFiles);
			else
				openSong(abcFiles);
		}
	}

	private boolean openSong(File[] abcFiles) {
		List<FileAndData> data = new ArrayList<>();
		
		if (abcFiles.length == 1 && abcFiles[0].getName().toLowerCase().endsWith(".abcp")) {
			if (playlistViewPanel.promptSavePlaylist()) {
				playlistViewPanel.loadPlaylist(abcFiles[0], false);
				SwingUtilities.invokeLater(()-> {
					showPlaylistView = true;
					updatePlaylistCardView();
				});
			}
			return true;
		}

		try {
			for (File abcFile : abcFiles) {
				data.add(new FileAndData(abcFile, AbcToMidi.readLines(abcFile)));
			}
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Failed to open file", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		return openSong(data);
	}

	private boolean appendSong(File[] abcFiles) {
		List<FileAndData> data = new ArrayList<>();
		
		// TODO: support append playlist?
		if (abcFiles.length == 1 && abcFiles[0].getName().toLowerCase().endsWith(".abcp")) {
			playlistViewPanel.loadPlaylist(abcFiles[0], true);
			SwingUtilities.invokeLater(()-> {
				showPlaylistView = true;
				updatePlaylistCardView();
			});
			return true;
		}

		try {
			for (File abcFile : abcFiles) {
				data.add(new FileAndData(abcFile, AbcToMidi.readLines(abcFile)));
			}
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Failed to open file", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		return appendSong(data);
	}

	private boolean onLotroParseError(LotroParseException lpe) {
		JCheckBox checkBox = new JCheckBox("Ignore LOTRO-specific errors");
		Object[] message = new Object[] { lpe.getMessage(), checkBox };
		JOptionPane.showMessageDialog(this, message, "Error reading ABC file", JOptionPane.WARNING_MESSAGE);
		prefs.putBoolean("ignoreLotroErrors", checkBox.isSelected());
		lotroErrorsMenuItem.setSelected(checkBox.isSelected());
		return checkBox.isSelected();
	}

	private boolean openSong(List<FileAndData> data) {
		boolean isSameFile = false;
		if (abcData != null && abcData.size() == data.size()) {
			isSameFile = true;
			for (int i = 0; i < abcData.size(); i++) {
				if (!abcData.get(i).file.equals(data.get(i).file)) {
					isSameFile = false;
					break;
				}
			}
		}
		
		boolean wasPlaying = sequencer.isRunning();

		sequencer.stop(); // pause
		updateButtonStates();

		Sequence song = null;
		AbcInfo info = new AbcInfo();
		boolean retry;
		do {
			retry = false;
			try {
				AbcToMidi.Params params = new AbcToMidi.Params(data);
				params.useLotroInstruments = useLotroInstruments;
				params.abcInfo = info;
				params.enableLotroErrors = !lotroErrorsMenuItem.isSelected();
				params.stereo = stereoMenuItem.isSelected();
				params.generateRegions = true;
				song = AbcToMidi.convert(params);
			} catch (LotroParseException e) {
				if (onLotroParseError(e)) {
					retry = lotroErrorsMenuItem.isSelected();
				} else {
					return false;
				}
			} catch (ParseException e) {
				JOptionPane.showMessageDialog(this, e.getMessage(), "Error reading ABC", JOptionPane.ERROR_MESSAGE);
				return false;
			}
		} while (retry);

		this.exportFileDialog = null;
		this.saveFileDialog = null;
		this.abcData = data;
		this.abcInfo = info;
		this.instrumentOverrideMap.clear();
		if (abcViewFrame != null)
			abcViewFrame.clearLinesAndRegions();
		stop();

		try {
			sequencer.setSequence(song);
		} catch (InvalidMidiDataException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "MIDI error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		sequencer.setTempoFactor(1.0f);
		for (int i = 0; i < CHANNEL_COUNT; i++) {
			sequencer.setTrackMute(i, false);
			sequencer.setTrackSolo(i, false);
		}

		updateWindowTitle();
		trackListPanel.songChanged(abcInfo);
		updateButtonStates();
		updateTitleLabel();

		barNumberLabel.setBarNumberCache(abcInfo);
		barNumberLabel.setVisible(abcInfo.getBarCount() > 0);

		updateAbcView(/* showIfHidden = */false, /* retainScrollPosition = */isSameFile);

		sequencer.start();

		for (FileAndData fileAndData : data) {
			String fileName = fileAndData.file.getAbsolutePath();
			recentAdd(fileName);
		}
		
		if (!wasPlaying && showPlaylistView && !playlistViewPanel.isPlayingFromPlaylist() && !playedFromFiletree) {
			showPlaylistView = false;
			updatePlaylistCardView();
		}
		playedFromFiletree = false;

		return true;
	}
	
	private void closeSong() {
		sequencer.stop();
		sequencer.clearSequence();
		sequencer.reset(true);
		this.exportFileDialog = null;
		this.saveFileDialog = null;
		this.abcData = null;
		this.abcInfo = null;
		this.instrumentOverrideMap.clear();
		if (abcViewFrame != null)
			abcViewFrame.clearLinesAndRegions();
		
		barNumberLabel.setBarNumberCache(null);
		barNumberLabel.setVisible(false);
		
		updateWindowTitle();
		trackListPanel.songChanged(null);
		updateButtonStates();
		updateTitleLabel();
	}

	private boolean appendSong(List<FileAndData> appendData) {
		if (this.abcData == null || this.abcData.isEmpty()) {
			return openSong(appendData);
		}

		boolean running = sequencer.isRunning() || !sequencer.isLoaded();
		long position = sequencer.getPosition();
		sequencer.stop(); // pause

		List<FileAndData> data = new ArrayList<>(abcData);
		data.addAll(appendData);

		Sequence song = null;
		AbcInfo info = new AbcInfo();
		boolean retry;
		do {
			retry = false;
			try {
				AbcToMidi.Params params = new AbcToMidi.Params(data);
				params.useLotroInstruments = useLotroInstruments;
				params.instrumentOverrideMap = instrumentOverrideMap;
				params.abcInfo = info;
				params.enableLotroErrors = !lotroErrorsMenuItem.isSelected();
				params.stereo = stereoMenuItem.isSelected();
				params.generateRegions = true;
				song = AbcToMidi.convert(params);
			} catch (LotroParseException e) {
				if (onLotroParseError(e)) {
					retry = lotroErrorsMenuItem.isSelected();
				} else {
					return false;
				}
			} catch (ParseException e) {
				String thisFile = appendData.size() == 1 ? "this file" : "these files";
				String msg = e.getMessage() + "\n\nWould you like to close the current song and retry opening "
						+ thisFile + "?";
				int result = JOptionPane.showConfirmDialog(this, msg, "Error appending ABC", JOptionPane.YES_NO_OPTION,
						JOptionPane.ERROR_MESSAGE);
				if (result == JOptionPane.YES_OPTION) {
					boolean success = openSong(appendData);
					sequencer.setRunning(success && running);
					return success;
				} else {
					return false;
				}
			}
		} while (retry);

		this.abcData = data;
		this.abcInfo = info;

		int oldTrackCount = sequencer.getSequence().getTracks().length;

		try {
			sequencer.reset(false);
			sequencer.setSequence(song);
			sequencer.setPosition(position);
			sequencer.setRunning(running);
		} catch (InvalidMidiDataException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "MIDI error", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		// Make sure the new tracks are unmuted
		for (int i = oldTrackCount; i < CHANNEL_COUNT; i++) {
			sequencer.setTrackMute(i, false);
			sequencer.setTrackSolo(i, false);
		}

		updateWindowTitle();
		trackListPanel.songChanged(abcInfo);
		updateButtonStates();
		updateTitleLabel();

		barNumberLabel.setBarNumberCache(abcInfo);
		barNumberLabel.setVisible(abcInfo.getBarCount() > 0);

		updateAbcView(/* showIfHidden = */false, /* retainScrollPosition = */true);

		return true;
	}

	private void updateWindowTitle() {
		if (abcData == null ) {
			setTitle(APP_NAME);
			if (abcViewFrame != null) {
				abcViewFrame.setTitle(APP_NAME);
			}
			return;
		}
		
		StringBuilder fileNames = new StringBuilder();
		int c = 0;
		for (FileAndData fd : abcData) {
			File f = fd.file;
			if (++c > 1)
				fileNames.append(", ");

			if (c > 2) {
				fileNames.append("...");
				break;
			}

			fileNames.append(f.getName());
		}

		String title = APP_NAME;
		if (!"".equals(fileNames.toString()))
			title += " - " + fileNames;

		setTitle(title);
		if (abcViewFrame != null)
			abcViewFrame.setTitle(title);
	}

	private boolean saveSong(File file) {
		try (PrintStream out = new PrintStream(file)) {
			int i = 0;
			for (FileAndData fileData : abcData) {
				for (String line : fileData.lines)
					out.println(line);

				if (++i < abcData.size())
					out.println();
			}
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Failed to save file", JOptionPane.ERROR_MESSAGE);
			return false;
		}
		return true;
	}

	private void playPause() {
		if (sequencer.isRunning())
			sequencer.stop();
		else
			sequencer.start();
	}

	private void stop() {
		sequencer.stop();
		sequencer.setPosition(0);
		updateButtonStates();
	}

	private void updateButtonStates() {
		boolean loaded = (sequencer.getSequence() != null);
		playButton.setEnabled(loaded);
		playButton.setIcon(sequencer.isRunning() ? pauseIcon : playIcon);
		playButton.setDisabledIcon(sequencer.isRunning() ? pauseIconDisabled : playIconDisabled);
		stopButton.setEnabled(loaded && (sequencer.isRunning() || sequencer.getPosition() != 0));
	}

	private void refreshSequence() {
		long position = sequencer.getPosition();
		Sequence song;

		try {
			AbcToMidi.Params params = new AbcToMidi.Params(abcData);
			params.useLotroInstruments = useLotroInstruments;
			params.instrumentOverrideMap = instrumentOverrideMap;
			params.abcInfo = abcInfo;
			params.enableLotroErrors = false;
			params.stereo = stereoMenuItem.isSelected();
			song = AbcToMidi.convert(params);
		} catch (ParseException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "Error changing instrument", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			boolean running = sequencer.isRunning();
			sequencer.reset(false);
			sequencer.setSequence(song);
			sequencer.setPosition(position);
			sequencer.setRunning(running);
		} catch (InvalidMidiDataException e) {
			JOptionPane.showMessageDialog(this, e.getMessage(), "MIDI error", JOptionPane.ERROR_MESSAGE);
			return;
		}
	}

	private static boolean openPort() {

		try {
			serverSocket = new ServerSocket(9000 + APP_VERSION.getBuild());
			if (serverSocket == null) {
				// System.out.println("Port is null");
				return false;
			}
			if (serverSocket.getLocalPort() != 9000 + APP_VERSION.getBuild()) {
				// System.out.println("Port is "+serverSocket.getLocalPort());
				return false;
			}
		} catch (IOException e) {
			// e.printStackTrace();
			return false;
		}
		// System.out.println("Made port");
		(new Thread(() -> {
			try {
				while (true) {
					Socket socket = serverSocket.accept();
					// System.out.println("Accepted");
					BufferedReader in = new BufferedReader(
							new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_16));

					// while (socket.isConnected()) {
					String data = in.readLine();

					if (data != null && data.length() >= 4
							&& (data.substring(data.length() - 4).equalsIgnoreCase(Util.ABC_FILE_EXTENSION) || data.substring(data.length() - 5).equalsIgnoreCase(Util.ABCP_FILE_EXTENSION))) {// &&
																							// !data.substring(0,3).equalsIgnoreCase("GET")
																							// &&
						// System.out.println("Receiving file path ("+data.length()+" chars) from port
						// "+(9000+APP_VERSION.getBuild())+":\n"+data);
						String[] datas = { data };
						activate(datas);
					} else {
						// System.out.println("Received nothing");
					}
					// }
					socket.close();
				}
			} catch (IOException e) {
				// e.printStackTrace();
			}
		})).start();
		return true;
	}

	private static void sendArgsToPort(final String[] args) {
		if (args == null || args.length == 0 || args[0].length() < 3) {
			// System.out.println("AbcPlayer already running. No filepath detected.
			// Closing.");
			return;
		}
		try {
			Socket clientSocket = new Socket("localhost", 9000 + APP_VERSION.getBuild());
			OutputStreamWriter os = new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_16);// NTFS
																													// uses
																													// UTF16
																													// for
																													// filenames
			// for (String arg : args) {
			os.write(args[0]);
			os.close();// Must be here to flush to stream
			// System.out.println("AbcPlayer already running. Sending file path
			// ("+args[0].length()+" chars) to port
			// "+(9000+APP_VERSION.getBuild())+":\n"+args[0]);
			// }
			clientSocket.close();
		} catch (IOException e) {
			// e.printStackTrace();
		}
	}
}
