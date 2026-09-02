package com.digero.maestro.view;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.Synthesizer;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.*;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.util.ExtensionFileFilter;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.common.util.FileParseException;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.common.view.UIText;
import com.digero.maestro.abc.*;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcSongEvent.AbcSongProperty;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.util.XmlUtil;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;
import info.clearthought.layout.TableLayoutConstraints;
import net.miginfocom.swing.MigLayout;

public class TrackPanel extends JPanel implements IDiscardable, TableLayoutConstants, ICompileConstants, ArrangementViewItem {
	protected static final Logger log = Logger.getLogger("view.trackPanel");

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
	static final int PRIORITY_WIDTH_DEFAULT = 20;
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
	private AbcPart abcPart;

	private JPanel gutter;
	private JCheckBox enableTrackCheckBox;
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
	private JMenuItem pasteSelection;
	// Wrap main note graph and potentially any drum note graphs
	private JPanel noteGraphPanel;
	private TrackNoteGraph noteGraph;
	private ArrayList<DrumPanel> drumlinePanels;
	public ProjectFrame projectFrame = null;

	private Listener<AbcPartEvent> abcListener;
	private Listener<AbcSongEvent> songListener;
	private Listener<SequencerEvent> seqListener;

	private boolean showDrumPanels = false;
	private boolean isAbcPreviewMode = false;

	public TrackDimensions dims = new TrackDimensions(TITLE_WIDTH_DEFAULT, PRIORITY_WIDTH_DEFAULT,
			CONTROL_WIDTH_DEFAULT, ROW_HEIGHT_DEFAULT);

	private String badString = "";

	private ControlLayout controlLayout;

	public TrackPanel(TrackInfo info, NoteFilterSequencerWrapper sequencer, AbcPart part,
			SequencerWrapper abcSequencer_, ControlLayout controlLayout) {
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

		setBorder(new CompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER_HORIZ.get()),
					BorderFactory.createMatteBorder(0, 0, 0, 1, ColorTable.PANEL_BORDER_VERTICAL.get())));

		this.trackInfo = info;
		this.seq = sequencer;
		this.abcPart = part;
		this.abcSequencer = abcSequencer_;
		this.controlLayout = controlLayout;

		TableLayout tableLayout = (TableLayout) getLayout();
		tableLayout.setHGap(HGAP);

		dims = calculateTrackDims();

		tableLayout
				.setColumn(new double[] { GUTTER_WIDTH, dims.titleWidth, dims.priorityWidth, dims.controlWidth, FILL });
		tableLayout.setRow(new double[] { PREFERRED, FILL });

		gutter = new JPanel((LayoutManager) null);
		gutter.setOpaque(false);

		enableTrackCheckBox = new JCheckBox() {
			@Override
			public Dimension getPreferredSize() {
				// This makes the title appear centered in the TrackPanel
				Dimension dim = super.getPreferredSize();
				dim.height = Math.max(calculateTrackDims().rowHeight, dim.height);
				return dim;
			}
			@Override
			public Dimension getMinimumSize() {
				return getPreferredSize();
			}
		};
		enableTrackCheckBox.setOpaque(false);
		enableTrackCheckBox.setSelected(abcPart.isTrackEnabled(trackInfo.getTrackNumber()));

		enableTrackCheckBox.addActionListener(e -> {
			int track = trackInfo.getTrackNumber();
			boolean enabled = enableTrackCheckBox.isSelected();
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

		noteGraphPanel = new JPanel(new MigLayout("wrap 1, gap 0, ins 0, novisualpadding, fill"));
		
		noteGraph = new TrackNoteGraph(seq, trackInfo);
		noteGraph.setPreferredSize(new Dimension(48, 200));// the Y must be so big that it forces the drumlinePanels to be their minimum sizes. Atm set to 200. ~Aifel
		noteGraph.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER_HORIZ.get()));
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
				getRootPane().requestFocus();
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

			@Override
			public void mouseEntered(MouseEvent e) {
				if (projectFrame != null) {
					int trackNumber = trackInfo.getTrackNumber();
					projectFrame.highlightPartsForTrack(trackNumber);   // route to parts panel
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (projectFrame != null) {
					int trackNumber = trackInfo.getTrackNumber();
					projectFrame.clearPartsTrackHighlight(trackNumber);
				}
			}
		});
		
		noteGraphPanel.add(noteGraph, "grow 10000 1000");

		int currentTranspose = abcPart.getTrackTranspose(trackInfo.getTrackNumber());
		transposeSpinner = new JSpinner(new TrackTransposeModel(currentTranspose, -48, 48, 12));
		transposeSpinner.setToolTipText(UIText.get("maestro.transpose.this.track.by.octaves.12.semitones"));

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

		sectionButton = new JButton();
		sectionButton.setPreferredSize(new Dimension(SECTIONBUTTON_WIDTH, SECTIONBUTTON_WIDTH));
		sectionButton.setMargin(new Insets(5, 5, 5, 5));
		sectionButton.setText(UIText.get("maestro.sectionedit.button"));
		sectionButton.setToolTipText(
				UIText.get("maestro.edit.sections.of.this.track"));
		sectionButton.addActionListener(e -> {
			int track = trackInfo.getTrackNumber();
			SectionEditor.show((JFrame) sectionButton.getTopLevelAncestor(), noteGraph, abcPart, track,
					abcPart.getInstrument().isPercussion, drumlinePanels);// super hack! :(
		});
		
		
		LookAndFeel previousLF = UIManager.getLookAndFeel();
	    try {
	    	// Set dark theme for PX and P controls on dark background.
	        UIManager.setLookAndFeel(new FlatMacDarkLaf());
	        fxBox = new JCheckBox("FX");
	        priorityBox = new JCheckBox(UIText.get("maestro.prio.button"));
	        UIManager.setLookAndFeel(previousLF);
	    } catch (UnsupportedLookAndFeelException e) {}	
		
		fxBox.setToolTipText(UIText.get("maestro.effect.sounds.instead.of.chromatic.notes"));
		fxBox.addActionListener(e -> {
			int track = trackInfo.getTrackNumber();
			boolean fx = fxBox.isSelected();
			abcPart.setFX(track, fx);
		});
		//fxBox.setVerticalTextPosition(SwingConstants.BOTTOM);
		//fxBox.setHorizontalTextPosition(SwingConstants.CENTER);
		fxBox.setVisible(false);
		
		//priorityBox.setOpaque(false);
		priorityBox.setToolTipText(UIText.get("maestro.prioritize.this.tracks.rhythm.when.combining"));
		priorityBox.addActionListener(e -> {
			int track = trackInfo.getTrackNumber();
			boolean prio = priorityBox.isSelected();
			abcPart.setTrackPriority(track, prio);
		});
		priorityBox.setVerticalTextPosition(SwingConstants.BOTTOM);
		priorityBox.setHorizontalTextPosition(SwingConstants.CENTER);

		trackVolumeBar = new TrackVolumeBar(trackInfo.getMinVelocity(), trackInfo.getMaxVelocity());
		trackVolumeBar.setToolTipText(UIText.get("maestro.adjust.this.track.s.volume"));
		trackVolumeBar.setDeltaVolume(abcPart.getTrackVolumeAdjust(trackInfo.getTrackNumber()));
		trackVolumeBar.addActionListener(e -> {
			// Only update the actual ABC part when the user stops dragging the trackVolumeBar
			if (!trackVolumeBar.isDragging())
				abcPart.setTrackVolumeAdjust(trackInfo.getTrackNumber(), trackVolumeBar.getDeltaVolume());

			updateState();
		});

		JPanel controlPanel = new JPanel(new BorderLayout(0, 4));
		controlPanel.setBorder(new EmptyBorder(4, 0, 4, 0));// top, left, bottom, right
		controlPanel.setOpaque(false);
		controlPanel.add(sectionButton, BorderLayout.WEST);
		if (!trackInfo.isDrumTrack())
			controlPanel.add(transposeSpinner, BorderLayout.CENTER);
		
		controlPanel.add(trackVolumeBar, BorderLayout.SOUTH);

		checkBoxLayout_ControlsHidden             = new TableLayoutConstraints(TITLE_COLUMN, 0, CONTROL_COLUMN, 0,
				TableLayoutConstants.FULL, TableLayoutConstants.CENTER);
		checkBoxLayout_ControlsAndPriorityVisible = new TableLayoutConstraints(TITLE_COLUMN, 0);
		checkBoxLayout_ControlsVisible            = new TableLayoutConstraints(TITLE_COLUMN, 0, PRIORITY_COLUMN, 0);

		/*
		JPanel checkBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,1,0));
		checkBoxPanel.setOpaque(false);
		//checkBoxPanel.setBorder( new EmptyBorder(1, 1, 1, 1) );// Making it tiny
		
		checkBoxPanel.add(priorityBox);
		checkBoxPanel.add(fxBox);
		*/
		
		add(gutter, GUTTER_COLUMN + ", 0, " + GUTTER_COLUMN + ", 1, f, f");
		add(enableTrackCheckBox, checkBoxLayout_ControlsHidden);
		add(priorityBox, PRIORITY_COLUMN + ", 0, f, c");
		add(controlPanel, CONTROL_COLUMN + ", 0, f, c");
		
		updateBadTooltipText();
		updateTitleText();

		abcPart.addAbcListener(abcListener = e -> {

			if (e.getProperty() == AbcPartProperty.INSTRUMENT || e.getProperty() == AbcPartProperty.TRACK_ENABLED) {
				noteGraph.invalidateNoteCache(); // instrument range or visibility changed; color may stay same so setter won't invalidate
				updateColors();
				updateState();
				noteGraph.repaint();
				updateBadTooltipText();
				updateTitleText();
			} else if (e.isNoteGraphRelated()) {
				// transposeNote, getSectionDoubling, isNotePlayable, etc. may have changed
				noteGraph.invalidateNoteCache();
                updateState();
                noteGraph.repaint();
                updateBadTooltipText();
                updateTitleText();
            }
		});

		abcPart.getAbcSong().addSongListener(songListener = e -> {

			if (e.getProperty() == AbcSongProperty.HIDE_EDITS_UPDATE || e.getProperty() == AbcSongProperty.TUNE_EDIT) {
				noteGraph.invalidateNoteCache();
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

		initDrumMenuBar();
		updateState();
	}
	
	public void setAbcPart(AbcPart part) {
		abcPart.removeAbcListener(abcListener);
		this.abcPart = part;
		abcPart.addAbcListener(abcListener);
		noteGraph.invalidateNoteCache(); // instrument, transpose, doublings etc. all change when switching parts
		trackVolumeBar.setDeltaVolume(abcPart.getTrackVolumeAdjust(trackInfo.getTrackNumber()));

		if (!abcPart.isPercussionPart()) {
			int currentTranspose = abcPart.getTrackTranspose(trackInfo.getTrackNumber());
			transposeSpinner.setValue(currentTranspose);
		}

		if (drumlinePanels != null && !drumlinePanels.isEmpty()) {
			for (DrumPanel panel : drumlinePanels) {
				panel.setAbcPart(abcPart);
			}
		}
		updateState();
		updateColors();
		updateBadTooltipText();
		updateTitleText();
	}
	
	@Override
	public JPanel getNoteGraph() {
		return noteGraphPanel;
	}
		
	static TrackDimensions calculateTrackDims() {
		return calculateTrackDims(false);
	}
	
	// returns <titleWidth, priorityWidth, controlWidth
	// Also sets some static constants in this class to be scaled properly
	static TrackDimensions calculateTrackDims(boolean fx) {
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
			if (fx) extraCheckbox = 0;//font.getSize() * 2;
			dims.rowHeight = (int) ((48 + (72 - 48) * (sizeDiff / divider) + extraCheckbox) * height);
			//dims.rowHeight = (int) ((48 + (72 - 48) * (sizeDiff / divider)) * height);

			// Lerp section button width between 10pt (22) and 18pt (36)
			SECTIONBUTTON_WIDTH = (int) (22 + (36 - 22) * (sizeDiff / divider));

			return dims;
		} else {
			int extraCheckbox = 0;
			if (fx) extraCheckbox = 0;
			return new TrackDimensions(TITLE_WIDTH_DEFAULT, PRIORITY_WIDTH_DEFAULT, CONTROL_WIDTH_DEFAULT,
					(int)((extraCheckbox + ROW_HEIGHT_DEFAULT) * height));
		}
	}
	
	static public void clearDrumClipboard() {
		drumClipboard = null;
	}

	private void initDrumMenuBar() {
		if (drumControlBar != null) {
			return;
		}
		
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
		
		JMenu selectMenu = new JMenu(UIText.get("maestro.drum.menu.select"));
		drumMapMenu = new JMenu(UIText.get("maestro.drum.menu.drum.map"));
//		drumMapMenu.setVisible(abcPart.isDrumPart());
		
		drumControlBar.add(drumMapMenu);
		drumControlBar.add(selectMenu);

		JMenuItem importItem = drumMapMenu.add(new JMenuItem(UIText.get("maestro.drum.menu.import")));
		importItem.addActionListener(e -> {
			if (!abcPart.isStudentPart() && !abcPart.isJauntyHandKnellsPart()) {
				loadDrumMapping();
			}
		});
		JMenuItem exportItem = drumMapMenu.add(new JMenuItem(UIText.get("maestro.drum.menu.export")));
		exportItem.addActionListener(e -> {
			if (!abcPart.isStudentPart() && !abcPart.isJauntyHandKnellsPart()) {
				saveDrumMapping();
			}
		});
		JMenuItem editItem = drumMapMenu.add(new JMenuItem(UIText.get("maestro.drum.menu.edit.combis")));
		editItem.addActionListener(e -> {
			if (abcPart == null) return;
			if (!abcPart.isStudentPart() && !abcPart.isJauntyHandKnellsPart()) {
				editDrumCombis();
			}
		});
		
		JMenuItem selectAll = selectMenu.add(new JMenuItem(UIText.get("maestro.drum.menu.select.all")));
		selectAll.addActionListener(e -> {
			if (drumlinePanels == null) {
				return;
			}
			for (DrumPanel dp : drumlinePanels) {
				dp.setSelected(true);
				dp.repaint();
			}
		});
		JMenuItem selectNone = selectMenu.add(new JMenuItem(UIText.get("maestro.drum.menu.select.none")));
		selectNone.addActionListener(e -> {
			if (drumlinePanels == null) {
				return;
			}
			for (DrumPanel dp : drumlinePanels) {
				dp.setSelected(false);
				dp.repaint();
			}
		});
		JMenuItem invertSelection = selectMenu.add(new JMenuItem(UIText.get("maestro.drum.menu.invert.selection")));
		invertSelection.addActionListener(e -> {
			if (drumlinePanels == null) {
				return;
			}
			for (DrumPanel dp : drumlinePanels) {
				dp.setSelected(!dp.isSelected());
				dp.repaint();
			}
		});
		JMenuItem copySelection = selectMenu.add(new JMenuItem(UIText.get("maestro.drum.menu.copy.selection")));
		pasteSelection = selectMenu.add(new JMenuItem(UIText.get("maestro.drum.menu.paste.selection")));
		pasteSelection.setEnabled(drumClipboard != null);
		pasteSelection.addActionListener(e -> {
			if (drumlinePanels == null) {
				return;
			}
			int i = 0;
			for (DrumPanel dp : drumlinePanels) {
				if (i >= drumClipboard.size()) {
					break;
				}
				dp.setSelected(drumClipboard.get(i));
				dp.repaint();
				i++;
			}
		});
		copySelection.addActionListener(e -> {
			if (drumlinePanels == null) {
				return;
			}
			drumClipboard = new ArrayList<Boolean>();
			for (DrumPanel dp : drumlinePanels) {
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

    @Override
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

    @Override
	public boolean isAbcPreviewMode() {
		return abcSequencer != null && isAbcPreviewMode;
	}

	/**
	 * Bent notes are ignored and not counted
	 */
	private void updateBadTooltipText() {
		switch (abcPart.getInstrument()) {
			case BASIC_CLARINET:
				int g3count = 0;
				for (NoteEvent ne : trackInfo.getEvents()) {
					if (!(ne instanceof BentMidiNoteEvent)) {
						Note mn = abcPart.mapNote(trackInfo.getTrackNumber(), ne.note.id, ne.getStartTick());
						if (mn != null && abcPart.shouldPlay(ne, trackInfo.getTrackNumber()) && mn == Note.G3) {
							g3count += 1;
						}
					}
				}
				if (g3count == 0) {
					badString = UIText.get("maestro.b.br.bad.g3.notes.0", g3count);
				} else {
					badString = UIText.get("maestro.b.br.p.style.color.red.bad.g3.notes.0.p", g3count);
				}
				break;
			case BASIC_PIBGORN:
				int acount = 0;
				for (NoteEvent ne : trackInfo.getEvents()) {
					if (!(ne instanceof BentMidiNoteEvent)) {
						Note mn = abcPart.mapNote(trackInfo.getTrackNumber(), ne.note.id, ne.getStartTick());
						if (mn != null && abcPart.shouldPlay(ne, trackInfo.getTrackNumber())
								&& (mn == Note.A2 || mn == Note.A3 || mn == Note.A4)) {
							acount += 1;
						}
					}
				}
				if (acount == 0) {
					badString = UIText.get("maestro.b.br.bad.a.notes.0", acount);
				} else {
					badString = UIText.get("maestro.b.br.p.style.color.red.bad.a.notes.0.p", acount);
				}
				break;
			case BASIC_HARP:
				int b4count = 0;
				for (NoteEvent ne : trackInfo.getEvents()) {
					if (!(ne instanceof BentMidiNoteEvent)) {
						Note mn = abcPart.mapNote(trackInfo.getTrackNumber(), ne.note.id, ne.getStartTick());
						if (mn != null && abcPart.shouldPlay(ne, trackInfo.getTrackNumber()) && mn == Note.B4) {
							b4count += 1;
						}
					}
				}
				if (b4count == 0) {
					badString = UIText.get("maestro.b.br.bad.b4.notes.0", b4count);
				} else {
					badString = UIText.get("maestro.b.br.p.style.color.red.bad.b4.notes.0.p", b4count);
				}
				break;
            /*
            case JAUNTY_HAND_KNELLS:
                int count = 0;
                for (MidiNoteEvent ne : trackInfo.getEvents()) {
                    if (!(ne instanceof BentMidiNoteEvent)) {
                        Note mn = abcPart.mapNote(trackInfo.getTrackNumber(), ne.note.id, ne.getStartTick());
                        if (mn != null && abcPart.shouldPlay(ne, trackInfo.getTrackNumber()) && (mn.id == Note.A2.id || mn.id == Note.Gs2.id)) {
                            count += 1;
                        }
                    }
                }
                if (count == 0) {
                    badString = "</b><br>" + "Bad notes: " + count;
                } else {
                    badString = "</b><br><p style='color:red;'>" + "Bad notes: " + count + "</p>";
                }
                break;

            */
			default:
				badString = "";
		}
	}

	private void updateTitleText() {
		final int ELLIPSIS_OFFSET = 38;

		String title = trackInfo.getTrackNumber() + ". " + trackInfo.getName();
		String instr = trackInfo.getInstrumentNames();

		enableTrackCheckBox.setToolTipText("<html><b>" + title + "</b><br>" + instr + badString + "</html>");

		int titleWidth = dims.titleWidth;
		if (!trackVolumeBar.isVisible()) {
			titleWidth += dims.controlWidth + dims.priorityWidth;
		} else if (!isPriorityEnabled()) {
			titleWidth += dims.priorityWidth;
		}

		title = Util.ellipsis(title, titleWidth - ELLIPSIS_OFFSET, enableTrackCheckBox.getFont().deriveFont(Font.BOLD));
		instr = Util.ellipsis(instr, titleWidth - ELLIPSIS_OFFSET, enableTrackCheckBox.getFont());
		enableTrackCheckBox.setText("<html><b>" + title + "</b><br>" + instr + "</html>");

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
						sectionButton.setForeground(UIManager.getColor("Button.foreground"));
					} else {
						sectionButton.setForeground(ColorTable.CONTROLS_EDITED.get());
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

		if (trackSolo && trackEnabled) {
			setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED_SOLO.get());
		} else if (trackEnabled) {
			setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
		} else if (trackSolo) {
			setBackground(ColorTable.GRAPH_BACKGROUND_OFF_SOLO.get());
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
			enableTrackCheckBox.setForeground(ColorTable.PANEL_TEXT_ENABLED.get());
		} else {
			boolean inputEnabled = abcPart.isPercussionPart() == trackInfo.isDrumTrack();
			enableTrackCheckBox.setForeground(
					inputEnabled ? ColorTable.PANEL_TEXT_DISABLED.get() : ColorTable.PANEL_TEXT_NO_PERCUSSION_MATCH.get());
		}

		noteGraph.setOctaveLinesVisible(!trackInfo.isDrumTrack()
				&& !(abcPart.getInstrument().isPercussion && abcPart.isTrackEnabled(trackInfo.getTrackNumber())));
	}
	
	private void initDrumPanels() {
		if (drumlinePanels != null && !drumlinePanels.isEmpty()) {
			return;
		}
		
		drumlinePanels = new ArrayList<DrumPanel>();
		for (int noteId : trackInfo.getNotesInUse()) {
			DrumPanel drumlinePanel = new DrumPanel(trackInfo, seq, abcPart, noteId, abcSequencer, trackVolumeBar);
			drumlinePanel.setAbcPreviewMode(isAbcPreviewMode);
			drumlinePanels.add(drumlinePanel);
		}
	}

	private void updateState() {
		updateColors();

		boolean trackEnabled = abcPart.isTrackEnabled(trackInfo.getTrackNumber());
		boolean priorityEnabled = isPriorityEnabled();
		enableTrackCheckBox.setSelected(trackEnabled);

		// Update the visibility of controls
		trackVolumeBar.setVisible(trackEnabled);

		if (sectionButton != null) {
			sectionButton.setVisible(trackEnabled);
		}

		fxBox.setVisible(trackEnabled && (abcPart.getInstrument().equals(LotroInstrument.STUDENT_FIDDLE) || abcPart.getInstrument().equals(LotroInstrument.JAUNTY_HAND_KNELLS)));
		if (fxBox.isVisible()) {
			add(fxBox, CONTROL_COLUMN + ", 1, f, t");
			fxBox.setSelected(abcPart.isFX(trackInfo.getTrackNumber()));
			fxBox.setEnabled(!abcPart.isStudentFromABC());
			// TODO: disabling checkbox cannot really be seen in flatlaf :(
		} else {
			remove(fxBox);
		}
		
		TableLayout layout = (TableLayout) getLayout();
		TableLayoutConstraints newCheckBoxLayout = trackEnabled
				? ((priorityEnabled || fxBox.isVisible()) ? checkBoxLayout_ControlsAndPriorityVisible : checkBoxLayout_ControlsVisible)
				: checkBoxLayout_ControlsHidden;

		if (layout.getConstraints(enableTrackCheckBox) != newCheckBoxLayout) {
			layout.setConstraints(enableTrackCheckBox, newCheckBoxLayout);
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
		
		boolean showDrumPanelsPrev = showDrumPanels;

		showDrumPanels = trackEnabled && !abcPart.isChromatic(trackInfo.getTrackNumber());

		if (showDrumPanels != showDrumPanelsPrev) {
			if (showDrumPanels) {
				//System.out.println("Enabling drum panels on a track");

				initDrumPanels();

				add(drumControlBar, TITLE_COLUMN + ", 1," + (CONTROL_COLUMN -1) + ", 1");
				
				int row = LAYOUT_ROWS.length;
				
				for (DrumPanel drumlinePanel : drumlinePanels) {
					drumlinePanel.setAbcPreviewMode(isAbcPreviewMode);
					if (row <= layout.getNumRow())
						layout.insertRow(row, PREFERRED);
					add(drumlinePanel, "0, " + row + ", " + NOTE_COLUMN + ", " + row);
                    //row++;// a bit convoluted logic, but this line will make the order opposite
				}
				
				// Rebuild note graph panel
				noteGraph.setBorder(BorderFactory.createEmptyBorder());

				DrumPanel last = null;
				for (int i = drumlinePanels.size() - 1; i >= 0; i--) {
					DrumPanel drumlinePanel = drumlinePanels.get(i);
					noteGraphPanel.add(drumlinePanel.getNoteGraph(), "grow,shrink 0");
					last = drumlinePanel;
				}
				last.getNoteGraph().setBorder(BorderFactory.createCompoundBorder(
						BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER_HORIZ.get()),
						BorderFactory.createMatteBorder(1, 0, 0, 0, ColorTable.OCTAVE_LINE.get())));
				
				drumMapMenu.setVisible(showDrumPanels && abcPart.isDrumPart());
			}
			else { // Don't show drum panels
				//System.out.println("Disabling drum panels on a track");
				for (int i = getComponentCount() - 1; i >= 0; --i) {
					Component child = getComponent(i);
					if (child instanceof DrumPanel) {
						remove(i);
					}
				}
				
				noteGraphPanel.removeAll();

                int baseRows = LAYOUT_ROWS.length;
                while (layout.getNumRow() > baseRows) {
                    layout.deleteRow(layout.getNumRow() - 1);
                }

				noteGraph.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER_HORIZ.get()));
				noteGraphPanel.add(noteGraph, "grow");

				if (drumControlBar != null) {
					remove(drumControlBar);
				}
			}

			updateTitleText();

			revalidate();
			noteGraphPanel.revalidate();
            repaint();
            noteGraphPanel.repaint();
		}

        if (transposeSpinner != null) {
            transposeSpinner.setVisible(trackEnabled && !abcPart.isPercussionPart() && !showDrumPanels);
        }

		if (showDrumPanels) {
			drumMapMenu.setVisible(abcPart.isDrumPart());
			pasteSelection.setEnabled(drumClipboard != null);
		}
	}

	private boolean saveDrumMapping() {
		Preferences prefs = Preferences.userNodeForPackage(TrackPanel.class);

		String dirPath = prefs.get(DRUM_NOTE_MAP_DIR_PREF_KEY, null);
		File dir;
		if (dirPath == null || !(dir = new File(dirPath)).isDirectory())
			dir = Util.getLotroMusicPath(false /* create */);

		JFileChooser fileChooser = new JFileChooser(dir);
		fileChooser.setFileFilter(
				new ExtensionFileFilter(UIText.get("maestro.drum.map.1", DrumNoteMap.FILE_SUFFIX), DrumNoteMap.FILE_SUFFIX));

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
						UIText.get("maestro.file.0.already.exists.overwrite", saveFile.getName()), UIText.get("maestro.confirm.overwrite"),
						JOptionPane.OK_CANCEL_OPTION);
				if (result != JOptionPane.OK_OPTION)
					continue;
			}

			break;
		} while (true);

		try {
			abcPart.getDrumMap(trackInfo.getTrackNumber()).save(saveFile);
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, UIText.get("maestro.failed.to.save.drum.map.0", e.getMessage()),
					UIText.get("maestro.failed.to.save.drum.map"), JOptionPane.ERROR_MESSAGE);
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
		fileChooser.setFileFilter(new ExtensionFileFilter(UIText.get("maestro.drum.map.0", DrumNoteMap.FILE_SUFFIX),
				DrumNoteMap.FILE_SUFFIX, Util.TXT_FILE_EXTENSION_NO_DOT));//For backwards compat use txt filter instead of drummap.txt

		if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			return false;

		File loadFile = fileChooser.getSelectedFile();

		try {
			abcPart.getDrumMap(trackInfo.getTrackNumber()).load(loadFile);
		} catch (IOException | FileParseException e) {
			JOptionPane.showMessageDialog(this, UIText.get("maestro.failed.to.load.drum.map.0", e.getMessage()),
					UIText.get("maestro.failed.to.load.drum.map"), JOptionPane.ERROR_MESSAGE);
			return false;
		}

		prefs.put(DRUM_NOTE_MAP_DIR_PREF_KEY, fileChooser.getCurrentDirectory().getAbsolutePath());
		return true;
	}

	private void editDrumCombis() {
		LotroCombiDrumInfo combiInfo = abcPart.getAbcSong().getCombiInfo();
		if (combiInfo == null) return;
		boolean previewEnabled = abcSequencer != null;
		if (previewEnabled) {
			abcSequencer.stop();
		}
		final JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this),
				UIText.get("maestro.drum.combo.edit.dialog.title"), Dialog.ModalityType.APPLICATION_MODAL);

		// existing combos list
		final DefaultListModel<LotroCombiDrumInfo.CombiDrumHit> listModel = new DefaultListModel<>();
		Runnable refillList = () -> {
			listModel.clear();
			combiInfo.libraryEntries().stream()
					.map(Map.Entry::getValue)
					.sorted(Comparator.comparing(c -> label(c).toLowerCase()))
					.forEach(listModel::addElement);
		};
		refillList.run();

		final JList<LotroCombiDrumInfo.CombiDrumHit> list = new JList<>(listModel);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setVisibleRowCount(10);
		list.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(JList<?> l, Object v,
														  int i, boolean sel, boolean foc) {
				super.getListCellRendererComponent(l, v, i, sel, foc);
				if (v instanceof LotroCombiDrumInfo.CombiDrumHit c) {
					String t = label(c) + "   (" + drumName(c.firstNote().id)
							+ " + " + drumName(c.secondNote().id) + ")";
					if (c.locked()) t += UIText.get("maestro.drum.combo.edit.builtin");
					setText(t);
				}
				return this;
			}
		});

		// new-combo controls (real drums only, no None, no combos)
		LotroDrumInfo[] drums = LotroDrumInfo.ALL_DRUMS.stream()
				.filter(d -> d != LotroDrumInfo.DISABLED)
				.toArray(LotroDrumInfo[]::new);
		final JComboBox<LotroDrumInfo> pick1 = new JComboBox<>(drums);
		final JComboBox<LotroDrumInfo> pick2 = new JComboBox<>(drums);
		pick1.setMaximumRowCount(20);
		pick2.setMaximumRowCount(20);
		final JTextField nameField = new JTextField(14);

		final JButton previewNew = new JButton(UIText.get("maestro.drum.combo.edit.btn.preview"));
		previewNew.setEnabled(previewEnabled);
		previewNew.addActionListener(e -> {
			if (!previewEnabled) return;
			LotroDrumInfo a = (LotroDrumInfo) pick1.getSelectedItem();
			LotroDrumInfo b = (LotroDrumInfo) pick2.getSelectedItem();
			if (a != null && b != null) previewCombo(a.note.id, b.note.id);
		});

		final JLabel counter = new JLabel();
		Runnable updateCounter = () -> {
			int used = combiInfo.customCount();
			int cap  = LotroCombiDrumInfo.customCapacity();
			counter.setText(UIText.get("maestro.drum.combo.edit.count.0.1", used, cap));  // "12 / 79 combos"
			Color fg;
			if      (used <  cap * 80 / 100) fg = new Color(0, 140, 0);   // green
			else if (used <  cap * 90 / 100) fg = new Color(180, 140, 0); // yellow/amber
			else                             fg = new Color(200, 0, 0);   // red
			counter.setForeground(fg);
		};
		updateCounter.run();

		final JButton addBtn = new JButton(UIText.get("maestro.drum.combo.edit.btn.add.combo"));
		addBtn.addActionListener(e -> {
			LotroDrumInfo a = (LotroDrumInfo) pick1.getSelectedItem();
			LotroDrumInfo b = (LotroDrumInfo) pick2.getSelectedItem();
			if (a == null || b == null) return;
			if (a.note == b.note) {
				JOptionPane.showMessageDialog(dlg,
						UIText.get("maestro.drum.combo.edit.pick.two.different.drums"),
						UIText.get("maestro.drum.combo.edit.dialog.title"), JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			String nm = XmlUtil.sanitizeStringForXMLSaving(nameField.getText().trim());
			Note before = combiInfo.libraryKeyForPair(a.note, b.note);   // was it already there?
			Note key = combiInfo.addToLibrary(a.note, b.note, nm.isEmpty() ? null : nm);
			if (key == null) {
				JOptionPane.showMessageDialog(dlg,
						UIText.get("maestro.drum.combo.edit.library.is.full"),
						UIText.get("maestro.drum.combo.edit.dialog.title"), JOptionPane.WARNING_MESSAGE);
			} else {
				if (before != null) {
					JOptionPane.showMessageDialog(dlg,
							UIText.get("maestro.drum.combo.edit.that.pair.already.exists.as.0", label(combiInfo.get(key.id))),
							UIText.get("maestro.drum.combo.edit.dialog.title"), JOptionPane.INFORMATION_MESSAGE);
				} else {
					refillList.run();               // reflect the add in this dialog
					nameField.setText("");
					list.setSelectedValue(combiInfo.get(key.id), true);
				}
			}
			updateCounter.run();
			// addToLibrary already fired libraryChanged -> open DrumPanel dropdowns refreshed
		});

		// preview an existing combo
		final JButton previewSel = new JButton(UIText.get("maestro.drum.combo.edit.btn.preview.selected"));
		previewSel.setEnabled(false);
		previewSel.addActionListener(e -> {
			var sel = list.getSelectedValue();
			if (sel != null) previewCombo(sel.firstNote().id, sel.secondNote().id);
		});
		list.addListSelectionListener(e ->
				previewSel.setEnabled(previewEnabled && list.getSelectedValue() != null));

		final JButton deleteBtn = new JButton(UIText.get("maestro.drum.combo.edit.btn.delete.selected"));
		deleteBtn.setEnabled(false);
		deleteBtn.addActionListener(e -> {
			var sel = list.getSelectedValue();
			if (sel == null) return;
			if (sel.locked()) return;   // built-ins never deletable (button also disabled below)

			Note key = combiInfo.libraryKeyForPair(sel.firstNote(), sel.secondNote());
			if (key == null) return;

			int uses = countUsesInSong(key.id);
			if (uses > 0) {
				JOptionPane.showMessageDialog(dlg,
						UIText.get("maestro.drum.combo.edit.this.combo.is.in.use", uses),
						UIText.get("maestro.drum.combo.edit.delete.combo.failed"), JOptionPane.INFORMATION_MESSAGE);
				// if (r != JOptionPane.YES_OPTION) return;
				// clearUsesInSong(key.id);// set those map slots to DISABLED
			} else {
				combiInfo.removeFromLibrary(key);   // needs to exist - see below
				refillList.run();
				updateCounter.run();
			}
		});
		list.addListSelectionListener(e -> {
			var s = list.getSelectedValue();
			deleteBtn.setEnabled(s != null && !s.locked());
			previewSel.setEnabled(previewEnabled && s != null);
		});


		//  layout
		JPanel newRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		newRow.add(new JLabel(UIText.get("maestro.drum.combo.edit.hit.1")));
		newRow.add(pick1);
		newRow.add(new JLabel(UIText.get("maestro.drum.combo.edit.hit.2")));
		newRow.add(pick2);
		newRow.add(new JLabel(UIText.get("maestro.drum.combo.edit.name")));
		newRow.add(nameField);
		newRow.add(previewNew);
		newRow.add(addBtn);

		JPanel listPanel = new JPanel(new BorderLayout(4, 4));
		listPanel.add(new JLabel(UIText.get("maestro.drum.combo.edit.combos.in.this.song.s.library")), BorderLayout.NORTH);
		listPanel.add(new JScrollPane(list), BorderLayout.CENTER);
		JPanel southPanel = new JPanel(new FlowLayout());
		listPanel.add(southPanel, BorderLayout.SOUTH);
		southPanel.add(counter);
		southPanel.add(previewSel);
		southPanel.add(deleteBtn);

		JButton closeBtn = new JButton(UIText.get("maestro.drum.combo.edit.close"));
		closeBtn.addActionListener(e -> dlg.dispose());
		JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		bottom.add(closeBtn);

		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		content.add(listPanel, BorderLayout.CENTER);
		content.add(newRow, BorderLayout.NORTH);
		content.add(bottom, BorderLayout.SOUTH);

		dlg.setContentPane(content);
		dlg.pack();
		dlg.setLocationRelativeTo(this);
		dlg.setVisible(true);
	}

	private int countUsesInSong(int hit) {
		int uses = 0;
		if (hit == Note.REST.id) return uses;
		AbcSong song = abcPart.getAbcSong();
		for (AbcPart part : song.getParts()) {
			int tCount = part.getTrackCount();
			for (int t = 0; t < tCount; t++) {
				DrumNoteMap map = part.peekDrumMap(t);//this method wont create a map if its null
				if (map == null) continue;
				if (map.getKeyFor((byte)hit, t, part, song) != Note.REST.id) {
					uses++;
				}
			}
		}
		return uses;
	}

	// combo display name, falling back to the pair when unnamed
	private static String label(LotroCombiDrumInfo.CombiDrumHit c) {
		return (c.name() != null && !c.name().isEmpty())
				? c.name() : (UIText.get("maestro.drum.combo.edit.combi.0.1", c.firstNote().id,c.secondNote().id));
	}
	private static String drumName(int id) {
		LotroDrumInfo d = LotroDrumInfo.getById(id);
		return d != null ? d.toString() : String.valueOf(id);
	}

	private static final int SYNTH_DRUM_PROGRAM = MidiInstrument.SYNTH_DRUM.id();   // 118
	private static final int PREVIEW_VELOCITY = 127;
	private static final int PREVIEW_MS = 750;

	private void previewCombo(int id1, int id2) {
		Synthesizer synth = LotroSequencerWrapper.getLotroSynth();
		if (synth == null || !synth.isOpen()) return;

		MidiChannel[] chans = synth.getChannels();

		MidiChannel ch = chans[MidiConstants.CHANNEL_COUNT_ABC-1];
		int oldProgram = ch.getProgram();
		int oldChannelVolume = ch.getController(MidiConstants.CHANNEL_VOLUME_CONTROLLER_COARSE);
		int oldChannelExpr = ch.getController(MidiConstants.CHANNEL_EXPRESSION_CONTROLLER);
		boolean oldMono = ch.getMono();
		ch.programChange(SYNTH_DRUM_PROGRAM);
		ch.controlChange(MidiConstants.CHANNEL_VOLUME_CONTROLLER_COARSE, 127);
		ch.controlChange(MidiConstants.CHANNEL_EXPRESSION_CONTROLLER, 127);
		ch.setMono(true);

		ch.noteOn(id1, PREVIEW_VELOCITY);
		ch.noteOn(id2, PREVIEW_VELOCITY);

		// note-off after a short delay, on the EDT, so it doesn't hang the dialog
		javax.swing.Timer t = new javax.swing.Timer(PREVIEW_MS, e -> {
			ch.noteOff(id1);
			ch.noteOff(id2);
			ch.programChange(oldProgram);
			ch.controlChange(MidiConstants.CHANNEL_VOLUME_CONTROLLER_COARSE, oldChannelVolume);
			ch.controlChange(MidiConstants.CHANNEL_EXPRESSION_CONTROLLER, oldChannelExpr);
			ch.setMono(oldMono);
		});
		t.setRepeats(false);
		t.start();
	}

	@Override
	public void discard() {
		for (int i = getComponentCount() - 1; i >= 0; --i) {
			Component child = getComponent(i);
			if (child instanceof IDiscardable) {
				((IDiscardable) child).discard();
			}
		}
		drumlinePanels = null;
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
			if (!trackInfo.isDrumTrack()) {
				noteId += abcPart.getTranspose(trackInfo.getTrackNumber(), tickStart, !abcPart.getAbcSong().isHideEdits());
			}
			return noteId;
		}

		@Override
		protected boolean audibleNote(NoteEvent ne) {
			if (abcPart.getAbcSong().isHideEdits()) return true;

			// if not enabled we only check getAudible()
			boolean enabled = isActiveTrack();

			// is visible if not tune-edited away. Or section-edit silenced.
			boolean visible = abcPart.getAudible(trackInfo.getTrackNumber(), ne.getStartTick(), enabled);

			return visible && (!enabled || (
					// shouldPlay() is checking for section-edit pan setting
					abcPart.shouldPlay(ne, trackInfo.getTrackNumber())
					&& abcPart.mapNoteEvent(trackInfo.getTrackNumber(), ne, true) != null)
					);
		}

		@Override
		protected boolean[] getSectionsModified() {
			if (!isActiveTrack() || abcPart.getAbcSong().isHideEdits()) {
				return null;
			}
			return abcPart.sectionsModified.get(trackInfo.getTrackNumber());
		}
		
		@Override
		protected List<Pair<Long, Long>> getMicrosModified(long from, long to) {
			if (!isActiveTrack() || abcPart.getAbcSong().isHideEdits()) {
				return null;
			}
			SequenceDataCache data = sequenceInfo.getDataCache();
			long a = data.microsToTick(from);
			long b = data.microsToTick(to);
			List<Pair<Long, Long>> list = new ArrayList<>();
			TreeMap<Long, PartSection> tree = abcPart.sectionsTicked.get(trackInfo.getTrackNumber());
			NavigableMap<Long, PartSection> subtree = tree.headMap(b, false);
			for (Entry<Long, PartSection> entry : subtree.entrySet()) {
				if (entry.getValue().startTick < b && entry.getValue().endTick >= a) {
					list.add(new Pair<Long,Long>(data.tickToMicros(entry.getValue().startTick), data.tickToMicros(entry.getValue().endTick)));
				}
			}
			return list;
		}
		
		@Override
		protected boolean isActiveTrack() {
			return abcPart.isTrackEnabled(trackInfo.getTrackNumber());
		}
		
		@Override
		protected Float getLastBar() {
			if (abcPart.getAbcSong().isHideEdits()) return null;
			return abcPart.getAbcSong().getLastBar();
		}
		
		@Override
		protected Float getFirstBar() {
			if (abcPart.getAbcSong().isHideEdits()) return null;
			return abcPart.getAbcSong().getFirstBar();
		}
		
		@Override
		protected Long getLastBarTick() {
			if (abcPart.getAbcSong().isHideEdits()) return null;
			return abcPart.getAbcSong().getLastBarTick();
		}
		
		@Override
		protected Long getFirstBarTick() {
			if (abcPart.getAbcSong().isHideEdits()) return null;
			return abcPart.getAbcSong().getFirstBarTick();
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
			// this method is not called, but its inheritors are.
			int midId = transposeNote(ne.note.id + addition, ne.getStartTick());
			int lowId = midId;
			int highId = midId;
			boolean studentChromatic = abcPart.isStudentPart() && !abcPart.isFX(trackInfo.getTrackNumber());

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
				
				if (abcPart.isStudentFromABC()) {
				    // ABC dont have bent notes so this should normally not happen
				    // If source is changed to a midi in menu when source was ABC, I guess this can happen
					return abcPart.getInstrument().isPlayable(highId) && abcPart.getInstrument().isPlayable(lowId);
				}
				return abcPart.getInstrument().isPlayable(highId, studentChromatic) && abcPart.getInstrument().isPlayable(lowId, studentChromatic);
			}
			if (abcPart.isStudentFromABC()) {
				return abcPart.getInstrument().isPlayable(highId) && abcPart.getInstrument().isPlayable(lowId);
			}
			return abcPart.getInstrument().isPlayable(midId, studentChromatic);
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
	
	@Override
	public boolean isVerticalZoomForbidden() {
		return showDrumPanels;
	}
}
