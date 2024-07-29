package com.digero.maestro.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.Note;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.common.util.ParseException;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;
import com.digero.maestro.abc.AbcSongEvent.AbcSongProperty;
import com.digero.maestro.abc.DrumNoteMap;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.MidiNoteEvent;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.TrackInfo;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;
import info.clearthought.layout.TableLayoutConstraints;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class TrackPanel extends JPanel implements IDiscardable, TableLayoutConstants, ICompileConstants, PartPanelItem {

	private static final String DRUM_NOTE_MAP_DIR_PREF_KEY = "DrumNoteMap.directory";

	// 0 1 2 3
	// +---+-------------------+----------+--------------------+
	// | | TRACK NAME | octave | +--------------+ |
	// 0 | |[X] | +----^-+ | | (note graph) | |
	// | | Instrument(s) | +----v-+ | +--------------+ |
	// + +-------------------+----------+ |
	// 1 | |Drum save controls (optional) | |
	// +---+------------------------------+--------------------+
	private static final int GUTTER_COLUMN = 0;
	private static final int TITLE_COLUMN = 1;
	private static final int PRIORITY_COLUMN = 2;
	private static final int CONTROL_COLUMN = 3;
	private static final int NOTE_COLUMN = 4;

//	static final int HGAP = 4;
//	static final int SECTIONBUTTON_WIDTH = 22;
//	static final int GUTTER_WIDTH = 8;
//	static final int PRIORITY_WIDTH = 22;
//	static final int TITLE_WIDTH = 150 - PRIORITY_WIDTH;
//	static final int CONTROL_WIDTH = 64;

	static final int HGAP = 4;
	static int SECTIONBUTTON_WIDTH = 22;
	static final int GUTTER_WIDTH = 8;
	static final int PRIORITY_WIDTH_DEFAULT = 22;
	static final int TITLE_WIDTH_DEFAULT = 150 - PRIORITY_WIDTH_DEFAULT;
	static final int CONTROL_WIDTH_DEFAULT = 64;
	static final int ROW_HEIGHT_DEFAULT = 48;
	private static double[] LAYOUT_COLS = new double[] { GUTTER_WIDTH, TITLE_WIDTH_DEFAULT, PRIORITY_WIDTH_DEFAULT,
			CONTROL_WIDTH_DEFAULT, FILL };
	private static double[] LAYOUT_ROWS = new double[] { ROW_HEIGHT_DEFAULT, PREFERRED };
	
	private static ArrayList<Boolean> drumClipboard = null;

	public static class TrackDimensions {
		public TrackDimensions() {
		}

		public TrackDimensions(int titleW, int priW, int controlW, int rowH) {
			titleWidth = titleW;
			priorityWidth = priW;
			controlWidth = controlW;
			rowHeight = rowH;
		}

		public int titleWidth = TITLE_WIDTH_DEFAULT;
		public int priorityWidth = PRIORITY_WIDTH_DEFAULT;
		public int controlWidth = CONTROL_WIDTH_DEFAULT;
		public int rowHeight = ROW_HEIGHT_DEFAULT;
	}

	private final TrackInfo trackInfo;
	private final NoteFilterSequencerWrapper seq;
	private final SequencerWrapper abcSequencer;
	private final AbcPart abcPart;

	private JPanel gutter;
	private JCheckBox checkBox;
	private TableLayoutConstraints checkBoxLayout_ControlsHidden;
	private TableLayoutConstraints checkBoxLayout_ControlsVisible;
	private TableLayoutConstraints checkBoxLayout_ControlsAndPriorityVisible;
	private JButton sectionButton;
	private JCheckBox fxBox;
	private JCheckBox priorityBox;
	private JSpinner transposeSpinner;
	private TrackVolumeBar trackVolumeBar;
	private JMenuBar drumControlBar;
	private JMenu drumMapMenu;
	// Wrap main note graph and potentially any drum note graphs
	private JPanel noteGraphPanel;
	private TrackNoteGraph noteGraph;
	private ArrayList<DrumPanel> drumPanels;

	public ArrayList<DrumPanel> getDrumPanels() {
		return drumPanels;
	}

	private Listener<AbcPartEvent> abcListener;
	private Listener<AbcSongEvent> songListener;
	private Listener<SequencerEvent> seqListener;

	private boolean showDrumPanels;
	private boolean wasDrumPart;
	private boolean isAbcPreviewMode = false;

	public TrackDimensions dims = new TrackDimensions(TITLE_WIDTH_DEFAULT, PRIORITY_WIDTH_DEFAULT,
			CONTROL_WIDTH_DEFAULT, ROW_HEIGHT_DEFAULT);

	private String badString = "";

	public TrackPanel(TrackInfo info, NoteFilterSequencerWrapper sequencer, AbcPart part,
			SequencerWrapper abcSequencer_) {
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

		setBorder(new CompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()),
					BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0x555555))));

		this.trackInfo = info;
		this.seq = sequencer;
		this.abcPart = part;
		this.abcSequencer = abcSequencer_;

		TableLayout tableLayout = (TableLayout) getLayout();
		tableLayout.setHGap(HGAP);

		dims = calculateTrackDims();

		tableLayout
				.setColumn(new double[] { GUTTER_WIDTH, dims.titleWidth, dims.priorityWidth, dims.controlWidth, FILL });
		tableLayout.setRow(new double[] { dims.rowHeight, PREFERRED });

		gutter = new JPanel((LayoutManager) null);
		gutter.setOpaque(false);

		checkBox = new JCheckBox();
		checkBox.setOpaque(false);
//		checkBox.setFocusable(false);
		checkBox.setSelected(abcPart.isTrackEnabled(trackInfo.getTrackNumber()));

		checkBox.addActionListener(e -> {
			int track = trackInfo.getTrackNumber();
			boolean enabled = checkBox.isSelected();
			abcPart.setTrackEnabled(track, enabled);
			if (MUTE_DISABLED_TRACKS)
				seq.setTrackMute(track, !enabled);
			updateBadTooltipText();
			updateTitleText();
		});
		/*
		 * Font[] fonts; Font ms = null; fonts =
		 * java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts(); for (int i = 0; i < fonts.length;
		 * i++) { if (fonts[i].getFontName().contains("JhengHei")) { ms = fonts[i]; break; } }
		 * 
		 * if (ms != null) { Map attributes = ms.getAttributes(); attributes.replace(java.awt.font.TextAttribute.SIZE,
		 * 12); ms = ms.deriveFont(attributes); if (ms != null) { System.out.println(ms.getFontName());
		 * checkBox.setFont(ms); } else { System.out.println("No such font"); } } else {
		 * System.out.println("No such font"); }
		 */

		noteGraphPanel = new JPanel(new MigLayout("wrap 1, gap 0, ins 0, novisualpadding, fillx"));
		
		noteGraph = new TrackNoteGraph(seq, trackInfo);
		noteGraph.setPreferredSize(new Dimension(noteGraph.getPreferredSize().width, getPreferredSize().height));
		noteGraph.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()));
		noteGraph.addMouseListener(new MouseAdapter() {
			// soloAbcTracks will contain one track if only right click is used
			// if ctrl-right click is used, it will contain all abc preview tracks that have the midi track enabled
			private ArrayList<Integer> soloAbcTracks = new ArrayList<Integer>();
			private int soloMidiTrack = -1;

			@Override
			public void mousePressed(MouseEvent e) {
				boolean soloMouseAction = SwingUtilities.isRightMouseButton(e);
				boolean previewAllParts = e.isControlDown();
				if (soloMouseAction) {
					int trackNumber = trackInfo.getTrackNumber();
					if (isAbcPreviewMode()) {
						if (abcPart.isTrackEnabled(trackNumber) && !previewAllParts) {
							int previewTrack = abcPart.getPreviewSequenceTrackNumber();
							if (previewTrack >= 0) {
								soloAbcTracks.add(previewTrack);
							}
						} else {
							for (AbcPart part : abcPart.getAbcSong().getParts()) {
								int previewTrack = part.getPreviewSequenceTrackNumber();
								// TODO: This only solos the first part that has the track selected.
								// Should we change this behavior so right-clicking solos all parts which have the track
								// selected?
								if (part.isTrackEnabled(trackNumber) && previewTrack >= 0) {
									soloAbcTracks.add(previewTrack);
									if (!previewAllParts)
										break;
								}
							}
						}

						if (!soloAbcTracks.isEmpty()) {
							// Un-solo any other parts that may be soloed
							for (AbcPart part : abcPart.getAbcSong().getParts()) {
								int previewTrack = part.getPreviewSequenceTrackNumber();
								if (!soloAbcTracks.contains(previewTrack) && previewTrack >= 0
										&& abcSequencer.getTrackSolo(previewTrack)) {
									abcSequencer.setTrackSolo(previewTrack, false);
								}
							}

							for (int previewTrack : soloAbcTracks) {
								if (!abcSequencer.getTrackSolo(previewTrack)) {
									abcSequencer.setTrackSolo(previewTrack, true);
								}
							}
						}
					} else {
						soloMidiTrack = trackNumber;
						seq.setTrackSolo(soloMidiTrack, true);
					}
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {

				if (SwingUtilities.isRightMouseButton(e)) {
					if (abcSequencer != null) {
						// Restore solo/mute state from abcpart state for solo/mute buttons
						for (AbcPart part : abcPart.getAbcSong().getParts()) {
							int trackNo = part.getPreviewSequenceTrackNumber();
							if (trackNo >= 0) {
								if (part.isSoloed() != abcSequencer.getTrackSolo(trackNo))
									abcSequencer.setTrackSolo(trackNo, part.isSoloed());
								if (part.isMuted() != abcSequencer.getTrackMute(trackNo))
									abcSequencer.setTrackMute(trackNo, part.isMuted());
							}
						}
						soloAbcTracks = new ArrayList<Integer>();
					}

					if (soloMidiTrack >= 0)
						seq.setTrackSolo(soloMidiTrack, false);
					soloMidiTrack = -1;
				}
			}
		});
		
		noteGraphPanel.add(noteGraph, "grow");

		if (!trackInfo.isDrumTrack()) {
			int currentTranspose = abcPart.getTrackTranspose(trackInfo.getTrackNumber());
			transposeSpinner = new JSpinner(new TrackTransposeModel(currentTranspose, -48, 48, 12));
			transposeSpinner.setToolTipText("Transpose this track by octaves (12 semitones)");

			transposeSpinner.addChangeListener(e -> {
				int track = trackInfo.getTrackNumber();
				int value = (Integer) transposeSpinner.getValue();
				if (value % 12 != 0) {
					value = (abcPart.getTrackTranspose(track) / 12) * 12;
					transposeSpinner.setValue(value);
				} else {
					abcPart.setTrackTranspose(trackInfo.getTrackNumber(), value);
				}
				updateBadTooltipText();
				updateTitleText();
			});

		}

		sectionButton = new JButton();
		sectionButton.setPreferredSize(new Dimension(SECTIONBUTTON_WIDTH, SECTIONBUTTON_WIDTH));
		sectionButton.setMargin(new Insets(5, 5, 5, 5));
		sectionButton.setText("s");
		sectionButton.setToolTipText(
				"<html><b> Edit sections of this track </b><br> Use the bar counter in lower right corner to find your sections. </html>");
		sectionButton.addActionListener(e -> {
			int track = trackInfo.getTrackNumber();
			SectionEditor.show((JFrame) sectionButton.getTopLevelAncestor(), noteGraph, abcPart, track,
					abcPart.getInstrument().isPercussion, drumPanels);// super hack! :(
		});
		
		fxBox = new JCheckBox("FX");
		fxBox.setToolTipText("Effect sounds instead of chromatic notes");
		fxBox.addActionListener(e -> {
			int track = trackInfo.getTrackNumber();
			boolean fx = fxBox.isSelected();
			abcPart.setStudentFX(track, fx);
		});
		fxBox.setVisible(false);
		fxBox.setForeground(Color.WHITE);

		priorityBox = new JCheckBox();
		priorityBox.setOpaque(false);
		priorityBox.setToolTipText("Prioritize this tracks rhythm when combining tracks with Mix Timings enabled");
		priorityBox.addActionListener(e -> {
			int track = trackInfo.getTrackNumber();
			boolean prio = priorityBox.isSelected();
			abcPart.setTrackPriority(track, prio);
		});

		trackVolumeBar = new TrackVolumeBar(trackInfo.getMinVelocity(), trackInfo.getMaxVelocity());
		trackVolumeBar.setToolTipText("Adjust this track's volume");
		trackVolumeBar.setDeltaVolume(abcPart.getTrackVolumeAdjust(trackInfo.getTrackNumber()));
		trackVolumeBar.addActionListener(e -> {
			// Only update the actual ABC part when the user stops dragging the trackVolumeBar
			if (!trackVolumeBar.isDragging())
				abcPart.setTrackVolumeAdjust(trackInfo.getTrackNumber(), trackVolumeBar.getDeltaVolume());

			updateState();
		});

		JPanel controlPanel = new JPanel(new BorderLayout(0, 4));
		controlPanel.setOpaque(false);
		if (sectionButton != null)
			controlPanel.add(sectionButton, BorderLayout.WEST);
		if (transposeSpinner != null)
			controlPanel.add(transposeSpinner, BorderLayout.CENTER);
		
		controlPanel.add(trackVolumeBar, BorderLayout.SOUTH);

		checkBoxLayout_ControlsHidden = new TableLayoutConstraints(TITLE_COLUMN, 0, CONTROL_COLUMN, 0);
		checkBoxLayout_ControlsAndPriorityVisible = new TableLayoutConstraints(TITLE_COLUMN, 0);
		checkBoxLayout_ControlsVisible = new TableLayoutConstraints(TITLE_COLUMN, 0, PRIORITY_COLUMN, 0);

		add(gutter, GUTTER_COLUMN + ", 0, " + GUTTER_COLUMN + ", 1, f, f");
		add(checkBox, checkBoxLayout_ControlsHidden);
		add(priorityBox, PRIORITY_COLUMN + ", 0, f, c");
		add(controlPanel, CONTROL_COLUMN + ", 0, f, c");
//		add(noteGraph, NOTE_COLUMN + ", 0, " + NOTE_COLUMN + ", 1");

		updateBadTooltipText();
		updateTitleText();

		abcPart.addAbcListener(abcListener = e -> {
			
			if (e.isNoteGraphRelated()) {
				updateState();
				noteGraph.repaint();
				updateBadTooltipText();
				updateTitleText();
			}

			if (e.getProperty() == AbcPartProperty.INSTRUMENT || e.getProperty() == AbcPartProperty.TRACK_ENABLED) {
				updateColors();
				updateState();
				noteGraph.repaint();
				updateBadTooltipText();
				updateTitleText();
			}
		});
		
		abcPart.getAbcSong().addSongListener(songListener = e -> {
			
			if (e.getProperty() == AbcSongProperty.HIDE_EDITS_UPDATE || e.getProperty() == AbcSongProperty.TUNE_EDIT) {
				updateState();
				noteGraph.repaint();
				updateBadTooltipText();
				updateTitleText();
				updateColors();
			}
		});

		seq.addChangeListener(seqListener = evt -> {
			if (evt.getProperty() == SequencerProperty.TRACK_ACTIVE) {
				updateColors();
			} else if (evt.getProperty() == SequencerProperty.IS_RUNNING) {
				noteGraph.repaint();
			}
		});

		if (abcSequencer != null)
			abcSequencer.addChangeListener(seqListener);

		addPropertyChangeListener("enabled", evt -> updateState());

		updateState(true);
	}
	
	@Override
	public JPanel getNoteGraph() {
		return noteGraphPanel;
	}
	
	public void setRowHeight(int rowHeight) {
		if (drumPanels.size() > 0) {
			return;
		}
		TableLayout tableLayout = (TableLayout) getLayout();
		double[] rowDims = tableLayout.getRow();
		rowDims[0] = rowHeight;
		tableLayout.setRow(rowDims);
		noteGraph.setPreferredSize(new Dimension(noteGraph.getPreferredSize().width, rowHeight + 1));
		noteGraph.revalidate();
		revalidate();
	}
	
	public void setNoteGraphWidth(int noteGraphWidth) {
		noteGraph.setPreferredSize(new Dimension(noteGraphWidth, noteGraph.getPreferredSize().height));
		noteGraph.revalidate();
	}

	static TrackDimensions calculateTrackDims() {
		return calculateTrackDims(false);
	}
	
	// returns <titleWidth, priorityWidth, controlWidth
	// Also sets some static constants in this class to be scaled properly
	static TrackDimensions calculateTrackDims(boolean student) {
		Font font = UIManager.getFont("defaultFont");

		float height = 1.0f;// Will be higher than 1.0 if screen larger than FullHD
		try {
			//height = Math.max(1.0f, Toolkit.getDefaultToolkit().getScreenSize().height/1080.0f);
		} catch (java.awt.HeadlessException e) {
		}
		
		if (font != null) // Using a flat theme - resize panel based on text size
		{
			int sizeDiff = font.getSize() - 10;
			final double divider = 18.0 - 10.0; // Used for lerp

			final int widthAt18Pt = 414;
			final int widthAt10Pt = 234;

			int widthAtThisFont = (int) (widthAt10Pt + (widthAt18Pt - widthAt10Pt) * (sizeDiff / divider));
			TrackDimensions dims = new TrackDimensions();
			dims.titleWidth = (int) (widthAtThisFont * .58);
			dims.priorityWidth = (int) (widthAtThisFont * .10);
			dims.controlWidth = (int) (widthAtThisFont * .32);

			// Lerp track height between 10pt (48) and 18pt (72)
			int extraCheckbox = 0;
			if (student) extraCheckbox = 0;//font.getSize() * 2;
			dims.rowHeight = (int) ((48 + (72 - 48) * (sizeDiff / divider) + extraCheckbox) * height);

			// Lerp section button width between 10pt (22) and 18pt (36)
			SECTIONBUTTON_WIDTH = (int) (22 + (36 - 22) * (sizeDiff / divider));

			return dims;
		} else {
			int extraCheckbox = 0;
			if (student) extraCheckbox = 0;
			return new TrackDimensions(TITLE_WIDTH_DEFAULT, PRIORITY_WIDTH_DEFAULT, CONTROL_WIDTH_DEFAULT,
					(int)((extraCheckbox + ROW_HEIGHT_DEFAULT) * height));
		}
	}
	
	static public void clearDrumClipboard() {
		drumClipboard = null;
	}

	private void initDrumMenuBar() {
		// Match colors of the parts panel for selected items
		// Restore defaults after these components are created
		Color bg = (Color)UIManager.get("MenuBar.selectionBackground");
		Color fg = (Color)UIManager.get("MenuBar.selectionForeground");
		UIManager.put("MenuBar.selectionBackground",ColorTable.GRAPH_BACKGROUND_DISABLED.get());
		UIManager.put("MenuBar.selectionForeground",ColorTable.PANEL_TEXT_DISABLED.get());
		
		drumControlBar = new JMenuBar();
		drumControlBar.setOpaque(true);
		drumControlBar.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		drumControlBar.setFocusable(false);
		drumControlBar.setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
		drumControlBar.setBorder(BorderFactory.createEmptyBorder());
		
		JMenu selectMenu = new JMenu("Select");
		drumMapMenu = new JMenu("Drum Map");
		drumMapMenu.setVisible(abcPart.isDrumPart());
		
		drumControlBar.add(drumMapMenu);
		drumControlBar.add(selectMenu);

		JMenuItem importItem = drumMapMenu.add(new JMenuItem("Import..."));
		importItem.addActionListener(e -> {
			if (!abcPart.isStudentPart()) {
				loadDrumMapping();
			}
		});
		JMenuItem exportItem = drumMapMenu.add(new JMenuItem("Export..."));
		exportItem.addActionListener(e -> {
			if (!abcPart.isStudentPart()) {
				saveDrumMapping();
			}
		});
		
		JMenuItem selectAll = selectMenu.add(new JMenuItem("Select All"));
		selectAll.addActionListener(e -> {
			if (drumPanels == null) {
				return;
			}
			for (DrumPanel dp : drumPanels) {
				dp.setSelected(true);
				dp.repaint();
			}
		});
		JMenuItem selectNone = selectMenu.add(new JMenuItem("Select None"));
		selectNone.addActionListener(e -> {
			if (drumPanels == null) {
				return;
			}
			for (DrumPanel dp : drumPanels) {
				dp.setSelected(false);
				dp.repaint();
			}
		});
		JMenuItem invertSelection = selectMenu.add(new JMenuItem("Invert Selection"));
		invertSelection.addActionListener(e -> {
			if (drumPanels == null) {
				return;
			}
			for (DrumPanel dp : drumPanels) {
				dp.setSelected(!dp.isSelected());
				dp.repaint();
			}
		});
		JMenuItem copySelection = selectMenu.add(new JMenuItem("Copy Selection"));
		JMenuItem pasteSelection = selectMenu.add(new JMenuItem("Paste Selection"));
		pasteSelection.setEnabled(drumClipboard != null);
		pasteSelection.addActionListener(e -> {
			if (drumPanels == null) {
				return;
			}
			int i = 0;
			for (DrumPanel dp : drumPanels) {
				if (i >= drumClipboard.size()) {
					break;
				}
				dp.setSelected(drumClipboard.get(i));
				dp.repaint();
				i++;
			}
		});
		copySelection.addActionListener(e -> {
			if (drumPanels == null) {
				return;
			}
			drumClipboard = new ArrayList<Boolean>();
			for (DrumPanel dp : drumPanels) {
				drumClipboard.add(dp.isSelected());
			}
		});
		selectMenu.addMenuListener(new MenuListener() {

			@Override
			public void menuCanceled(MenuEvent arg0) {
			}

			@Override
			public void menuDeselected(MenuEvent arg0) {
				pasteSelection.setEnabled(drumClipboard != null);
			}

			@Override
			public void menuSelected(MenuEvent arg0) {
				pasteSelection.setEnabled(drumClipboard != null);
			}
		});
		
		// Restore LAF colors
		UIManager.put("MenuBar.selectionBackground", bg);
		UIManager.put("MenuBar.selectionForeground", fg);
	}

	public TrackInfo getTrackInfo() {
		return trackInfo;
	}

	public void setAbcPreviewMode(boolean isAbcPreviewMode) {
		if (this.isAbcPreviewMode != isAbcPreviewMode) {
			this.isAbcPreviewMode = isAbcPreviewMode;
			updateColors();
			for (Component child : getComponents()) {
				if (child instanceof DrumPanel) {
					((DrumPanel) child).setAbcPreviewMode(isAbcPreviewMode);
				}
			}
		}
	}

	private boolean isAbcPreviewMode() {
		return abcSequencer != null && isAbcPreviewMode;
	}

	/**
	 * Bent notes are ignored and not counted
	 */
	private void updateBadTooltipText() {
		if (abcPart.getInstrument().ordinal() == LotroInstrument.BASIC_CLARINET.ordinal()) {
			int g3count = 0;
			if (abcPart.isTrackEnabled(trackInfo.getTrackNumber())) {
				List<MidiNoteEvent> nel = trackInfo.getEvents();
				for (MidiNoteEvent ne : nel) {
					if (!(ne instanceof BentMidiNoteEvent)) {
						Note mn = abcPart.mapNote(trackInfo.getTrackNumber(), ne.note.id, ne.getStartTick());
						if (mn != null && abcPart.shouldPlay(ne, trackInfo.getTrackNumber()) && mn.id == Note.G3.id) {
							g3count += 1;
						}
					}
				}
			}
			if (g3count == 0) {
				badString = "</b><br>" + "Bad G3 notes: " + g3count;
			} else {
				badString = "</b><br><p style='color:red;'>" + "Bad G3 notes: " + g3count + "</p>";
			}

		} else if (abcPart.getInstrument().ordinal() == LotroInstrument.BASIC_PIBGORN.ordinal()) {
			int acount = 0;
			if (abcPart.isTrackEnabled(trackInfo.getTrackNumber())) {
				List<MidiNoteEvent> nel = trackInfo.getEvents();
				for (MidiNoteEvent ne : nel) {
					if (!(ne instanceof BentMidiNoteEvent)) {
						Note mn = abcPart.mapNote(trackInfo.getTrackNumber(), ne.note.id, ne.getStartTick());
						if (mn != null && abcPart.shouldPlay(ne, trackInfo.getTrackNumber())
								&& (mn.id == Note.A2.id || mn.id == Note.A3.id || mn.id == Note.A4.id)) {
							acount += 1;
						}
					}
				}
			}
			if (acount == 0) {
				badString = "</b><br>" + "Bad A notes: " + acount;
			} else {
				badString = "</b><br><p style='color:red;'>" + "Bad A notes: " + acount + "</p>";
			}
		} else {
			badString = "";
		}
	}

	private void updateTitleText() {
		final int ELLIPSIS_OFFSET = 38;

		String title = trackInfo.getTrackNumber() + ". " + trackInfo.getName();
		String instr = trackInfo.getInstrumentNames();

		checkBox.setToolTipText("<html><b>" + title + "</b><br>" + instr + badString + "</html>");

		int titleWidth = dims.titleWidth;
		if (!trackVolumeBar.isVisible()) {
			titleWidth += dims.controlWidth + dims.priorityWidth;
		} else if (!isPriorityEnabled()) {
			titleWidth += dims.priorityWidth;
		}

		title = Util.ellipsis(title, titleWidth - ELLIPSIS_OFFSET, checkBox.getFont().deriveFont(Font.BOLD));
		instr = Util.ellipsis(instr, titleWidth - ELLIPSIS_OFFSET, checkBox.getFont());
		checkBox.setText("<html><b>" + title + "</b><br>" + instr + "</html>");

	}

	private boolean isPriorityEnabled() {
		return abcPart.getAbcSong().isMixTiming() && abcPart.getAbcSong().isPriorityActive()
				&& abcPart.getEnabledTrackCount() > 1; // &&
														// abcPart.getAbcSong().isMixTiming()
	}

	@Override
	public void setBackground(Color bg) {
		super.setBackground(bg);
		if (trackVolumeBar != null)
			trackVolumeBar.setBackground(bg);
		if (noteGraph != null) {
			noteGraph.setBackground(bg);
		}
	}

	private void updateColors() {
		boolean abcPreviewMode = isAbcPreviewMode();
		int trackNumber = trackInfo.getTrackNumber();
		boolean trackEnabled = abcPart.isTrackEnabled(trackNumber);
		boolean trackEnabledOtherPart = trackEnabled;

		boolean trackActive;
		boolean trackSolo;

		if (abcPreviewMode) {
			// Set in the loop below
			trackActive = false;
			trackSolo = false;
		} else {
			trackActive = seq.isTrackActive(trackNumber);
			trackSolo = seq.getTrackSolo(trackNumber);
		}

		for (AbcPart part : abcPart.getAbcSong().getParts()) {
			if (part.isTrackEnabled(trackNumber)) {
				if (part != this.abcPart)
					trackEnabledOtherPart = true;
				else if (sectionButton != null) {
					if (this.abcPart.sections.get(trackNumber) == null
							&& this.abcPart.nonSection.get(trackNumber) == null) {
						sectionButton.setForeground(new Color(0.5f, 0.5f, 0.5f));
					} else {
						sectionButton.setForeground(new Color(0.2f, 0.8f, 0.2f));
					}
				}

				if (abcPreviewMode) {
					if (abcSequencer.isTrackActive(part.getPreviewSequenceTrackNumber()))
						trackActive = true;
					if (abcSequencer.getTrackSolo(part.getPreviewSequenceTrackNumber()))
						trackSolo = true;
				}
			}
		}

		gutter.setOpaque(trackEnabled || trackEnabledOtherPart);
		if (trackEnabled)
			gutter.setBackground(ColorTable.PANEL_HIGHLIGHT.get());
		else if (trackEnabledOtherPart)
			gutter.setBackground(ColorTable.PANEL_HIGHLIGHT_OTHER_PART.get());

		// Gray out the main drum panel if one of its child drum notes is solo
		if (trackActive && trackSolo && showDrumPanels) {
			SequencerWrapper activeSeq = abcPreviewMode ? abcSequencer : seq;
			if (activeSeq instanceof NoteFilterSequencerWrapper) {
				if (((NoteFilterSequencerWrapper) activeSeq).getFilter().isAnyNoteSolo()) {
					trackActive = false;
				}
			}
		}

		noteGraph.setShowingAbcNotesOn(trackActive);

		if (trackEnabled) {
			setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
		} else if (trackSolo) {
			setBackground(ColorTable.GRAPH_BACKGROUND_SOLO.get());
		} else {
			setBackground(ColorTable.GRAPH_BACKGROUND_OFF.get());
		}

		// Set note colors - always based on whether track is playing or not,
		// except show greyed out notes for drum tracks in midi mode if a non-drum part is selected - necessary?
		if (trackEnabled && trackActive) {
			noteGraph.setNoteColor(ColorTable.NOTE_ENABLED);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_ENABLED);
		} else if (!trackActive) {
			noteGraph.setNoteColor(ColorTable.NOTE_OFF);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_OFF);
		} else // disabled (lighter colored) notes for playing tracks not in the current part
		{
			boolean pseudoOff = !abcPreviewMode && (abcPart.isPercussionPart() != trackInfo.isDrumTrack());
			noteGraph.setNoteColor(pseudoOff ? ColorTable.NOTE_OFF : ColorTable.NOTE_DISABLED);
			noteGraph.setBadNoteColor(pseudoOff ? ColorTable.NOTE_BAD_OFF : ColorTable.NOTE_BAD_DISABLED);
		}

		if (trackEnabled) {
			checkBox.setForeground(ColorTable.PANEL_TEXT_ENABLED.get());
		} else {
			boolean inputEnabled = abcPart.isPercussionPart() == trackInfo.isDrumTrack();
			checkBox.setForeground(
					inputEnabled ? ColorTable.PANEL_TEXT_DISABLED.get() : ColorTable.PANEL_TEXT_OFF.get());
		}

		noteGraph.setOctaveLinesVisible(!trackInfo.isDrumTrack()
				&& !(abcPart.getInstrument().isPercussion && abcPart.isTrackEnabled(trackInfo.getTrackNumber())));
	}

	private void updateState() {
		updateState(false);
	}

	private void updateState(boolean initDrumPanels) {
		updateColors();

		boolean trackEnabled = abcPart.isTrackEnabled(trackInfo.getTrackNumber());
		boolean priorityEnabled = isPriorityEnabled();
		checkBox.setSelected(trackEnabled);

		// Update the visibility of controls
		trackVolumeBar.setVisible(trackEnabled);
		if (transposeSpinner != null)
			transposeSpinner.setVisible(trackEnabled && !abcPart.isPercussionPart());
		if (sectionButton != null)
			sectionButton.setVisible(trackEnabled);

		fxBox.setVisible(trackEnabled && abcPart.getInstrument().equals(LotroInstrument.STUDENT_FIDDLE));
		if (fxBox.isVisible()) {
			add(fxBox, CONTROL_COLUMN + ", 1");
			fxBox.setSelected(abcPart.isStudentFX(trackInfo.getTrackNumber()));
			fxBox.setEnabled(!abcPart.isStudentOverride());
			// disabling checkbox cannot really be seen in flatlaf :(
		} else {
			remove(fxBox);
		}
		
		TableLayout layout = (TableLayout) getLayout();
		TableLayoutConstraints newCheckBoxLayout = trackEnabled
				? (priorityEnabled ? checkBoxLayout_ControlsAndPriorityVisible : checkBoxLayout_ControlsVisible)
				: checkBoxLayout_ControlsHidden;

		if (layout.getConstraints(checkBox) != newCheckBoxLayout) {
			layout.setConstraints(checkBox, newCheckBoxLayout);
			updateTitleText();
		}

		priorityBox.setVisible(trackEnabled && priorityEnabled);
		priorityBox.setSelected(abcPart.isTrackPriority(trackInfo.getTrackNumber()));

		noteGraph.setShowingNoteVelocity(trackVolumeBar.isDragging());

		if (trackVolumeBar.isDragging()) {
			noteGraph.setDeltaVolume(trackVolumeBar.getDeltaVolume());
		} else {
			noteGraph.setDeltaVolume(abcPart.getTrackVolumeAdjust(trackInfo.getTrackNumber()));
		}

		boolean showDrumPanelsNew = !abcPart.isChromatic(trackInfo.getTrackNumber()) && trackEnabled;

		if (showDrumPanelsNew || initDrumPanels) {
			this.setPreferredSize(null);
		}

		if (initDrumPanels || showDrumPanels != showDrumPanelsNew || wasDrumPart != abcPart.isPercussionPart() || abcPart.getInstrument() == LotroInstrument.STUDENT_FIDDLE) {
			if (showDrumPanels != showDrumPanelsNew) {
				showDrumPanels = showDrumPanelsNew;
			}
			wasDrumPart = abcPart.isPercussionPart();

			for (int i = getComponentCount() - 1; i >= 0; --i) {
				Component child = getComponent(i);
				if (child instanceof DrumPanel) {
					((DrumPanel) child).discard();
					remove(i);
				}
			}
			
			
			
			noteGraphPanel.removeAll();
			noteGraph.setPreferredSize(new Dimension(noteGraph.getPreferredSize().width, calculateTrackDims().rowHeight + 1));
			noteGraph.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()));
			noteGraphPanel.add(noteGraph, "grow");

			drumPanels = new ArrayList<DrumPanel>();
			if (drumControlBar != null) {
				remove(drumControlBar);
			}
			
			if (showDrumPanels) {
				if (drumControlBar == null)
					initDrumMenuBar();

				add(drumControlBar, TITLE_COLUMN + ", 1," + (CONTROL_COLUMN -1) + ", 1");
				int controlHeight = getPreferredSize().height;
				
				int row = LAYOUT_ROWS.length;
				for (int noteId : trackInfo.getNotesInUse()) {
					DrumPanel panel = new DrumPanel(trackInfo, seq, abcPart, noteId, abcSequencer, trackVolumeBar);
					panel.setAbcPreviewMode(isAbcPreviewMode);
					if (row <= layout.getNumRow())
						layout.insertRow(row, PREFERRED);
					add(panel, "0, " + row + ", " + NOTE_COLUMN + ", " + row);
					if (drumPanels == null)
						drumPanels = new ArrayList<>();
					drumPanels.add(panel);
				}
				
//				noteGraph.setPreferredSize(new Dimension(noteGraph.getPreferredSize().width, getPreferredSize().height));

				// this array ends up being in reverse order from what is displayed, since we add the top row each time
				Collections.reverse(drumPanels);
				
				// Rebuild note graph panel
				noteGraph.setBorder(BorderFactory.createEmptyBorder());
				noteGraph.setPreferredSize(new Dimension(noteGraph.getPreferredSize().width, controlHeight));
				DrumPanel last = null;
				for (DrumPanel panel : drumPanels) {
					noteGraphPanel.add(panel.getNoteGraph(), "grow");
					last = panel;
				}
				last.getNoteGraph().setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()),
						BorderFactory.createMatteBorder(1, 0, 0, 0, ColorTable.OCTAVE_LINE.get())));
			} else if (abcPart.isTrackEnabled(trackInfo.getTrackNumber()) && abcPart.getInstrument() == LotroInstrument.STUDENT_FIDDLE) {
				int controlHeight = getPreferredSize().height;
				noteGraph.setPreferredSize(new Dimension(noteGraph.getPreferredSize().width, controlHeight));
			}

			updateTitleText();

			revalidate();
			noteGraphPanel.revalidate();
		}
		
		if (showDrumPanels) {
			drumMapMenu.setVisible(abcPart.isDrumPart());
		}
	}
	
	public boolean hasDrumPanels() {
		return drumPanels != null && !drumPanels.isEmpty();
	}

	private boolean saveDrumMapping() {
		Preferences prefs = Preferences.userNodeForPackage(TrackPanel.class);

		String dirPath = prefs.get(DRUM_NOTE_MAP_DIR_PREF_KEY, null);
		File dir;
		if (dirPath == null || !(dir = new File(dirPath)).isDirectory())
			dir = Util.getLotroMusicPath(false /* create */);

		JFileChooser fileChooser = new JFileChooser(dir);
		fileChooser.setFileFilter(
				new ExtensionFileFilter("Drum Map (*." + DrumNoteMap.FILE_SUFFIX + ")", DrumNoteMap.FILE_SUFFIX));

		File saveFile;
		do {
			if (fileChooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
				return false;

			saveFile = fileChooser.getSelectedFile();

			if (saveFile.getName().indexOf('.') < 0) {
				saveFile = new File(saveFile.getParentFile(), saveFile.getName() + "." + DrumNoteMap.FILE_SUFFIX);
			}

			if (saveFile.exists()) {
				int result = JOptionPane.showConfirmDialog(this,
						"File " + saveFile.getName() + " already exists. Overwrite?", "Confirm overwrite",
						JOptionPane.OK_CANCEL_OPTION);
				if (result != JOptionPane.OK_OPTION)
					continue;
			}

			break;
		} while (true);

		try {
			abcPart.getDrumMap(trackInfo.getTrackNumber()).save(saveFile);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Failed to save drum map:\n\n" + e.getMessage(),
					"Failed to save drum map", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		prefs.put(DRUM_NOTE_MAP_DIR_PREF_KEY, fileChooser.getCurrentDirectory().getAbsolutePath());
		return true;
	}

	private boolean loadDrumMapping() {
		Preferences prefs = Preferences.userNodeForPackage(TrackPanel.class);

		String dirPath = prefs.get(DRUM_NOTE_MAP_DIR_PREF_KEY, null);
		File dir;
		if (dirPath == null || !(dir = new File(dirPath)).isDirectory())
			dir = Util.getLotroMusicPath(false /* create */);

		JFileChooser fileChooser = new JFileChooser(dir);
		fileChooser.setFileFilter(new ExtensionFileFilter("Drum Map (*." + DrumNoteMap.FILE_SUFFIX + ")",
				DrumNoteMap.FILE_SUFFIX, "txt"));

		if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			return false;

		File loadFile = fileChooser.getSelectedFile();

		try {
			abcPart.getDrumMap(trackInfo.getTrackNumber()).load(loadFile);
		} catch (IOException | ParseException e) {
			JOptionPane.showMessageDialog(this, "Failed to load drum map:\n\n" + e.getMessage(),
					"Failed to load drum map", JOptionPane.ERROR_MESSAGE);
			return false;
		}

		prefs.put(DRUM_NOTE_MAP_DIR_PREF_KEY, fileChooser.getCurrentDirectory().getAbsolutePath());
		return true;
	}

	@Override
	public void discard() {
		for (int i = getComponentCount() - 1; i >= 0; --i) {
			Component child = getComponent(i);
			if (child instanceof IDiscardable) {
				((IDiscardable) child).discard();
			}
		}
		drumPanels = null;
		abcPart.removeAbcListener(abcListener);
		abcPart.getAbcSong().removeSongListener(songListener);
		seq.removeChangeListener(seqListener);
		if (abcSequencer != null)
			abcSequencer.removeChangeListener(seqListener);
		noteGraphPanel.removeAll();
		noteGraph.discard();
	}

	private static class TrackTransposeModel extends SpinnerNumberModel {
		public TrackTransposeModel(int value, int minimum, int maximum, int stepSize) {
			super(value, minimum, maximum, stepSize);
		}

		@Override
		public void setValue(Object value) {
			if (!(value instanceof Integer))
				throw new IllegalArgumentException();

			if ((Integer) value % 12 != 0)
				throw new IllegalArgumentException();

			super.setValue(value);
		}
	}

	public class TrackNoteGraph extends NoteGraph {
		private boolean showingAbcNotesOn = true;

		public TrackNoteGraph(SequencerWrapper sequencer, TrackInfo trackInfo) {
			super(sequencer, trackInfo, Note.MIN_PLAYABLE.id - 12, Note.MAX_PLAYABLE.id + 12);
		}

		public void setShowingAbcNotesOn(boolean showingAbcNotesOn) {
			if (this.showingAbcNotesOn != showingAbcNotesOn) {
				this.showingAbcNotesOn = showingAbcNotesOn;
				repaint();
			}
		}

		@Override
		Color getNoteColor(NoteEvent ne) {
			/*
			 * if (ne.isPruned(abcPart) && ProjectFrame.abcPreviewMode) { return ColorTable.NOTE_PRUNED.get(); }
			 */
			return super.getNoteColor(ne);
		}

		@Override
		Color getBadNoteColor(NoteEvent ne) {
			/*
			 * if (ne.isPruned(abcPart) && ProjectFrame.abcPreviewMode) { return ColorTable.NOTE_PRUNED.get(); }
			 */
			return super.getBadNoteColor(ne);
		}

		@Override
		protected int transposeNote(int noteId, long tickStart) {
			if (!trackInfo.isDrumTrack() && !abcPart.getAbcSong().isHideEdits()) {
				noteId += abcPart.getTranspose(trackInfo.getTrackNumber(), tickStart);
			}
			return noteId;
		}

		@Override
		protected boolean audibleNote(NoteEvent ne) {
			if (abcPart.getAbcSong().isHideEdits()) return true;
			return abcPart.getAudible(trackInfo.getTrackNumber(), ne.getStartTick(), isActiveTrack())
					&& abcPart.shouldPlay(ne, trackInfo.getTrackNumber()) && abcPart.mapNoteEvent(trackInfo.getTrackNumber(), ne, true) != null;
		}

		@Override
		protected boolean[] getSectionsModified() {
			if (!isActiveTrack() || abcPart.getAbcSong().isHideEdits()) {
				return null;
			}
			return abcPart.sectionsModified.get(trackInfo.getTrackNumber());
		}
		
		@Override
		protected boolean isActiveTrack() {
			return abcPart.isTrackEnabled(trackInfo.getTrackNumber());
		}
		
		@Override
		protected Integer getLastBar() {
			if (abcPart.getAbcSong().isHideEdits()) return null;
			return abcPart.getAbcSong().getLastBar();
		}
		
		@Override
		protected Integer getFirstBar() {
			if (abcPart.getAbcSong().isHideEdits()) return null;
			return abcPart.getAbcSong().getFirstBar();
		}

		@Override
		protected int[] getSectionVelocity(NoteEvent note) {
			if (abcPart.getAbcSong().isHideEdits()) return super.getSectionVelocity(note);
			return abcPart.getSectionVolumeAdjust(trackInfo.getTrackNumber(), note);
		}

		@Override
		protected int getSourceNoteVelocity(NoteEvent note) {
			if (abcPart.getAbcSong().isHideEdits()) return note.velocity;
			return abcPart.getSectionNoteVelocity(trackInfo.getTrackNumber(), note);
		}

		@Override
		protected Boolean[] getSectionDoubling(long tick) {
			if (abcPart.getAbcSong().isHideEdits()) {
				return super.getSectionDoubling(tick);
			}
			return abcPart.getSectionDoubling(tick, trackInfo.getTrackNumber());
		}

		@Override
		protected boolean isNotePlayable(NoteEvent ne, int addition) {
			int midId = transposeNote(ne.note.id + addition, ne.getStartTick());
			int lowId = midId;
			int highId = midId;

			if (midId < MidiConstants.LOWEST_NOTE_ID || midId > MidiConstants.HIGHEST_NOTE_ID)
				return false;

			if (abcPart.isPercussionPart())
				return abcPart.isDrumPlayable(trackInfo.getTrackNumber(), ne.note.id);

			if (trackInfo.isDrumTrack() && !abcPart.isTrackEnabled(trackInfo.getTrackNumber()))
				return true;

			if (ne instanceof BentMidiNoteEvent) {
				BentMidiNoteEvent be = (BentMidiNoteEvent) ne;

				lowId = transposeNote(be.getMinNote() + addition, ne.getStartTick());
				highId = transposeNote(be.getMaxNote() + addition, ne.getStartTick());

				if (lowId < MidiConstants.LOWEST_NOTE_ID || lowId > MidiConstants.HIGHEST_NOTE_ID)
					return false;
				if (highId < MidiConstants.LOWEST_NOTE_ID || highId > MidiConstants.HIGHEST_NOTE_ID)
					return false;
				
				if (abcPart.isStudentOverride()) {
					return abcPart.getInstrument().isPlayable(highId) && abcPart.getInstrument().isPlayable(lowId);
				}
				return abcPart.getInstrument().isPlayable(highId, abcPart.isStudentPart() && !abcPart.isStudentFX(trackInfo.getTrackNumber())) && abcPart.getInstrument().isPlayable(lowId, abcPart.isStudentPart() && !abcPart.isStudentFX(trackInfo.getTrackNumber()));
			}
			if (abcPart.isStudentOverride()) {
				return abcPart.getInstrument().isPlayable(highId) && abcPart.getInstrument().isPlayable(lowId);
			}
			return abcPart.getInstrument().isPlayable(midId, abcPart.isStudentPart() && !abcPart.isStudentFX(trackInfo.getTrackNumber()));
		}

		@Override
		protected boolean isShowingNotesOn() {
			int trackNumber = trackInfo.getTrackNumber();

			if (sequencer.isRunning())
				return sequencer.isTrackActive(trackNumber);

			if (abcSequencer != null && abcSequencer.isRunning())
				return showingAbcNotesOn;

			return false;
		}

		@Override
		protected List<NoteEvent> getEvents() {
			if (showDrumPanels)
				return Collections.emptyList();

			return super.getEvents();
		}

		@Override
		boolean isOutOfLimit(int noteId, long startTick) {
			int trackNumber = trackInfo.getTrackNumber();
			Pair<Integer, Integer> limits = abcPart.getSectionPitchLimits(trackNumber, startTick);
			return noteId + abcPart.getInstrument().octaveDelta * 12 < limits.first || noteId + abcPart.getInstrument().octaveDelta * 12 > limits.second;
			// The reason for the instrument octave addition is that it was subtracted when the note was instrument-transposed and limits should apply before that.
		}
	}

	public void setVerticalSize(int vert) {
		((TableLayout) this.getLayout()).setRow(0, vert);
		this.getLayout().layoutContainer(this);
	}
}
