package com.digero.maestro.view;

import com.digero.maestro.abc.*;
import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

import javax.swing.*;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiDrum;
import com.digero.common.midi.MidiDrumExtended;
import com.digero.common.midi.Note;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.maestro.abc.AbcSongEvent.AbcSongProperty;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.view.TrackPanel.TrackDimensions;

public class DrumPanel extends JPanel implements ArrangementViewItem, IDiscardable, TableLayoutConstants, ICompileConstants {
	protected static final Logger log = Logger.getLogger("drumHitPanel");
	// 0 1 2 3
	// +---+--------------------+----------+--------------------+
	// | | TRACK NAME | Drum | +--------------+ |
	// 0 | | | +------+ | | (note graph) | |
	// | | Instrument(s) | +-----v+ | +--------------+ |
	// +---+--------------------+----------+--------------------+
	private static final int GUTTER_WIDTH = TrackPanel.GUTTER_WIDTH;
	private static final int COMBO_WIDTH = 122;
	private static final int TITLE_WIDTH = TrackPanel.TITLE_WIDTH_DEFAULT + TrackPanel.HGAP
			+ TrackPanel.PRIORITY_WIDTH_DEFAULT + TrackPanel.CONTROL_WIDTH_DEFAULT - COMBO_WIDTH;
	private static double[] LAYOUT_COLS = new double[] { GUTTER_WIDTH, TITLE_WIDTH, COMBO_WIDTH/*, FILL*/ };
	private static final double[] LAYOUT_ROWS = new double[] { PREFERRED };

	private TrackInfo trackInfo;
	private NoteFilterSequencerWrapper seq;
	private SequencerWrapper abcSequencer;
	private AbcPart abcPart;
	private int drumId;
	private boolean isAbcPreviewMode;
	
	private Listener<AbcSongEvent> songListener;
	private final Runnable libraryRebuild = this::rebuildModel;

	private JPanel gutter;
	private JCheckBox checkBox;
	private JComboBox<DrumChoice> drumComboBox;
	private JComboBox<LotroStudentFXInfo> drumComboBoxFX;
    private JComboBox<LotroChromaticFXInfo> drumComboBoxJauntyFX;
	private DrumNoteGraph noteGraph;
	private TrackVolumeBar trackVolumeBar;
	private ActionListener trackVolumeBarListener;
	private boolean showVolume = false;

	private TrackDimensions dims = new TrackDimensions(TITLE_WIDTH, 0, COMBO_WIDTH, -1);

	private final LotroCombiDrumInfo combiInfo;

	public DrumPanel(TrackInfo info, NoteFilterSequencerWrapper sequencer, AbcPart part, int drumNoteId,
			SequencerWrapper abcSequencer_, TrackVolumeBar trackVolumeBar_) {
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

		this.trackInfo = info;
		this.seq = sequencer;
		this.abcSequencer = abcSequencer_;
		this.abcPart = part;
		this.drumId = drumNoteId;
		this.trackVolumeBar = trackVolumeBar_;

		combiInfo = abcPart.getAbcSong().getCombiInfo();

		TableLayout tableLayout = (TableLayout) getLayout();
		tableLayout.setHGap(TrackPanel.HGAP);

		dims = TrackPanel.calculateTrackDims();

		int totalW = dims.titleWidth + dims.priorityWidth + dims.controlWidth - TrackPanel.HGAP * 2;
		int div2 = totalW / 2;

		dims.titleWidth = div2 + (div2 + div2 == totalW ? 0 : 1);
		dims.controlWidth = div2;

		LAYOUT_COLS[1] = dims.titleWidth;
		LAYOUT_COLS[2] = dims.controlWidth;
		tableLayout.setColumn(LAYOUT_COLS);

		gutter = new JPanel((LayoutManager) null);
		gutter.setOpaque(false);

		checkBox = new JCheckBox();
		checkBox.setSelected(abcPart.isPercussionNoteEnabled(trackInfo.getTrackNumber(), drumId));
		checkBox.addActionListener(
				e -> abcPart.setDrumEnabled(trackInfo.getTrackNumber(), drumId, checkBox.isSelected()));

		checkBox.setOpaque(false);

		String title = trackInfo.getTrackNumber() + ". " + trackInfo.getName();
		String instr;
		if (info.isDrumTrack()) {
			if (trackInfo.getInstrumentExCount() == 1) {
				String kit = trackInfo.getInstrumentNames();
				instr = MidiDrumExtended.getInstance().fromId(drumId, kit, info.getSequenceInfo().standard);
			} else {
				instr = MidiDrum.fromId(drumId).name;
			}
		} else {
			instr = Note.fromId(drumNoteId).abc;
			checkBox.setFont(checkBox.getFont().deriveFont(Font.BOLD));
		}

		checkBox.setToolTipText("<html><b>" + title + "</b><br>" + instr + "</html>");

		instr = Util.ellipsis(instr, dims.titleWidth, checkBox.getFont());
		checkBox.setText(instr);

		drumComboBoxFX = new JComboBox<>(LotroStudentFXInfo.ALL_FX.toArray(new LotroStudentFXInfo[0]));
		drumComboBoxFX.setSelectedItem(getSelectedFX());
		drumComboBoxFX.setMaximumRowCount(20);
		drumComboBoxFX.addActionListener(e -> {
			LotroStudentFXInfo selected = (LotroStudentFXInfo) drumComboBoxFX.getSelectedItem();
			if (selected == null) return;
			abcPart.getFXMap(trackInfo.getTrackNumber()).set(drumId, selected.note.id);
		});
        drumComboBoxJauntyFX = new JComboBox<>(LotroChromaticFXInfo.getRange(LotroInstrument.JAUNTY_HAND_KNELLS).toArray(new LotroChromaticFXInfo[0]));
        drumComboBoxJauntyFX.setSelectedItem(getSelectedJauntyFX());
        drumComboBoxJauntyFX.setMaximumRowCount(20);
        drumComboBoxJauntyFX.addActionListener(e -> {
            LotroChromaticFXInfo selected = (LotroChromaticFXInfo) drumComboBoxJauntyFX.getSelectedItem();
			if (selected == null) return;
            abcPart.getJauntyHandKnellsFXMap(trackInfo.getTrackNumber()).set(drumId, selected.note.id);
        });
		drumComboBox = new JComboBox<>();
		updatingModel = true;
		for (DrumChoice choice : buildChoices()) {
			drumComboBox.addItem(choice);
		}
		updatingModel = false;
		drumComboBox.setSelectedItem(getSelectedChoice());
		drumComboBox.setMaximumRowCount(20);
		drumComboBox.addActionListener(e -> {
			if (updatingModel) return;
			DrumChoice sel = (DrumChoice) drumComboBox.getSelectedItem();
			if (sel == null) return;
			DrumNoteMap dm = abcPart.getDrumMap(trackInfo.getTrackNumber());
			dm.set(drumId, (byte) sel.markerId());
		});
		combiInfo.addLibraryListener(libraryRebuild);

		seq.addChangeListener(sequencerListener);
		if (abcSequencer != null)
			abcSequencer.addChangeListener(sequencerListener);
		abcPart.addAbcListener(abcPartListener);
		
		noteGraph = new DrumNoteGraph(seq, trackInfo);
		noteGraph.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ColorTable.OCTAVE_LINE.get()));
		noteGraph.addMouseListener(new MouseAdapter() {
			private int soloAbcTrack = -1;
			private int soloAbcDrumId = -1;
			private int soloTrack = -1;
			private int soloDrumId = -1;
			private boolean prevSoloState = false;

			@Override
			public void mousePressed(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					int trackNumber = trackInfo.getTrackNumber();
					if (isAbcPreviewMode() && abcSequencer instanceof NoteFilterSequencerWrapper) {
						if (abcPart.isTrackEnabled(trackNumber)) {
							soloAbcTrack = abcPart.getPreviewSequenceTrackNumber();
							Note soloDrumNote = abcPart.mapNote(trackNumber, drumId, 0);
							soloAbcDrumId = (soloDrumNote == null) ? -1 : soloDrumNote.id;
						}
						
						if (soloAbcTrack >= 0 && soloAbcDrumId >= 0) {
							prevSoloState = abcPart.isSoloed();
							((NoteFilterSequencerWrapper) abcSequencer).setNoteSolo(soloAbcTrack, soloAbcDrumId, true, abcPart);
						}
					} else {
						soloTrack = trackNumber;
						soloDrumId = drumId;
						seq.setNoteSolo(trackNumber, drumId, true, null);
					}
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					if (soloAbcTrack >= 0 && soloAbcDrumId >= 0 && abcSequencer instanceof NoteFilterSequencerWrapper) {
						// Always clear the filter note-solo (keyed by note id, so a track change can't strand it)
						((NoteFilterSequencerWrapper) abcSequencer).clearNoteSolo(soloAbcDrumId, soloAbcTrack, part);

						// Restore the part's solo on its curr track, which may have changed during the mouse hold
						int curTrack = abcPart.getPreviewSequenceTrackNumber();
						if (curTrack >= 0) {
							abcSequencer.setTrackSolo(curTrack, prevSoloState);
						}
					}
					soloAbcTrack = -1;
					soloAbcDrumId = -1;

					if (soloTrack >= 0 && soloDrumId >= 0) {
						AbcPart partParam = isAbcPreviewMode() && abcSequencer instanceof NoteFilterSequencerWrapper? abcPart:null;
						seq.setNoteSolo(soloTrack, soloDrumId, false, partParam);
					}
					soloTrack = -1;
					soloDrumId = -1;
				}
			}
		});

		if (trackVolumeBar != null) {
			trackVolumeBar.addActionListener(trackVolumeBarListener = e -> updateState());
		}

		addPropertyChangeListener("enabled", evt -> updateState());
		
		abcPart.getAbcSong().addSongListener(songListener = e -> {

			if (e.getProperty() == AbcSongProperty.HIDE_EDITS_UPDATE || e.getProperty() == AbcSongProperty.TUNE_EDIT) {
				noteGraph.invalidateNoteCache();
				updateState();
				noteGraph.repaint();
			}
		});

		add(gutter, "0, 0");
		add(checkBox, "1, 0");
		add(drumComboBox, "2, 0, f, c");
		add(drumComboBoxFX, "2, 0, f, c");
        add(drumComboBoxJauntyFX, "2, 0, f, c");

		updateState();
		//noteGraph.setPreferredSize(new Dimension(noteGraph.getPreferredSize().width, getPreferredSize().height)); the getter is overridden
	}

	private boolean updatingModel = false;   // reentrancy guard for the action listener

	private void rebuildModel() {
		if (discarded) return;
		updatingModel = true;
		try {
			DrumChoice current = getSelectedChoice();          // what should be selected after rebuild
			drumComboBox.setModel(new DefaultComboBoxModel<>(
					buildChoices().toArray(new DrumChoice[0])));
			drumComboBox.setSelectedItem(current);             // matches via equals-on-(kind,markerId)
		} finally {
			updatingModel = false;
		}
	}

	/**
	 * Build a list of drum hit choices for drum combobox.
	 */
	private List<DrumChoice> buildChoices() {
		List<DrumChoice> out = new ArrayList<>();

		for (LotroDrumInfo d : LotroDrumInfo.ALL_DRUMS)  // lotro drums (+ DISABLED)
			out.add(DrumChoice.drum(d));

		for (var e : combiInfo.libraryEntries())         // every combo: locked + custom
			out.add(DrumChoice.combi(e.getKey().id, e.getValue()));

		Collator col = Collator.getInstance();
		col.setStrength(Collator.SECONDARY);
		out.sort(Comparator.comparingInt(this::sectionRank)
				.thenComparing(DrumChoice::label, col));
		return out;
	}

	/**
	 * A single item in drum combobox.
	 * @param markerId the library id of the combo, or the note id of the drum
	 */
	record DrumChoice(Kind kind, int markerId, LotroCombiDrumInfo.CombiDrumHit combi, String label) {
		enum Kind { DRUM, LOCKED, COMBI }

		static DrumChoice drum(LotroDrumInfo d) {
			return new DrumChoice(Kind.DRUM, d.note.id, null, d.toString());
		}
		/** One factory for any combo; kind follows locked-ness, id is the library id. */
		static DrumChoice combi(int libraryId, LotroCombiDrumInfo.CombiDrumHit c) {
			return new DrumChoice(c.locked() ? Kind.LOCKED : Kind.COMBI, libraryId, c, label(c));
		}
		private static String label(LotroCombiDrumInfo.CombiDrumHit c) {
			return (c.name() != null && !c.name().isEmpty())
					? c.name() : ("combi " + c.firstNote().id + "+" + c.secondNote().id);
		}
		@Override public boolean equals(Object o) {
			return o instanceof DrumChoice d && d.kind() == kind && d.markerId() == markerId;
		}
		@Override public int hashCode() { return kind.hashCode() * 31 + markerId; }
		@Override public String toString() { return label; }
	}

	/**
	 * Return the rank of a drum choice in the combobox.
	 * @param c the choice
	 * @return 0 for disabled drum, 1 for real drum, 2 for locked combo, 3 for custom combo
	 */
	private int sectionRank(DrumChoice c) {
		if (c.kind() == DrumChoice.Kind.DRUM && c.markerId() == LotroDrumInfo.DISABLED.note.id) return 0;
		return switch (c.kind()) {
			case DRUM -> 1;
			case LOCKED -> 2;
			case COMBI -> 3;
		};
	}

	/**
	 * Return the selected drum choice.
	 */
	private DrumChoice getSelectedChoice() {
		DrumNoteMap dm = abcPart.getDrumMap(trackInfo.getTrackNumber());
		int id = dm.get(drumId);
		var c = dm.resolveCombi(id);
		if (c != null) return DrumChoice.combi(id, c);
		LotroDrumInfo d = LotroDrumInfo.getById(id);
		return d != null ? DrumChoice.drum(d) : DrumChoice.drum(LotroDrumInfo.DISABLED);
	}

    @Override
	public JPanel getNoteGraph() {
		return noteGraph;
	}

    @Override
    public boolean isVerticalZoomForbidden() {
        return true;
    }

    public void setSelected(boolean selected) {
		checkBox.setSelected(selected);
		abcPart.setDrumEnabled(trackInfo.getTrackNumber(), drumId, checkBox.isSelected());
	}
	
	public boolean isSelected() {
		return checkBox.isSelected();
	}

	private boolean discarded = false;

	@Override
	public void discard() {
		discarded = true;
		noteGraph.discard();
		abcPart.removeAbcListener(abcPartListener);
		seq.removeChangeListener(sequencerListener);
		if (abcSequencer != null)
			abcSequencer.removeChangeListener(sequencerListener);
		if (trackVolumeBar != null)
			trackVolumeBar.removeActionListener(trackVolumeBarListener);
		abcPart.getAbcSong().removeSongListener(songListener);
		combiInfo.removeLibraryListener(libraryRebuild);
	}

	private Listener<AbcPartEvent> abcPartListener = e -> {
		if (e.isNoteGraphRelated()) {
			if (!SwingUtilities.isEventDispatchThread()) {
				log.severe("abcPartListener called from non-EDT thread updates swing components!!");
			}
			// Drum mapping, enabled state, instrument etc. can affect which notes are visible
			// and how they are categorised; invalidate before updateState() calls setters.
			noteGraph.invalidateNoteCache();
			checkBox.setEnabled(abcPart.isTrackEnabled(trackInfo.getTrackNumber()));
			checkBox.setSelected(abcPart.isPercussionNoteEnabled(trackInfo.getTrackNumber(), drumId));
			if (abcPart.getInstrument() == LotroInstrument.STUDENT_FIDDLE) {
                drumComboBoxFX.setSelectedItem(getSelectedFX());
            } else if (abcPart.getInstrument() == LotroInstrument.JAUNTY_HAND_KNELLS) {
                drumComboBoxJauntyFX.setSelectedItem(getSelectedJauntyFX());
			} else {
				rebuildModel();
			}
			updateState();
		}
	};

	private Listener<SequencerEvent> sequencerListener = evt -> {
		if (evt.getProperty() == SequencerProperty.TRACK_ACTIVE)
			updateState();
	};

	public void updateVolume(boolean vol) {
		showVolume = vol;
		updateState();
	}

	private void updateState() {
		boolean abcPreviewMode = isAbcPreviewMode();
		int trackNumber = trackInfo.getTrackNumber();
		boolean trackEnabled = abcPart.isTrackEnabled(trackNumber);
		boolean noteEnabled = abcPart.isPercussionNoteEnabled(trackNumber, drumId);
		boolean noteEnabledOtherPart = false;

		boolean noteActive;
		if (abcPreviewMode) {
			noteActive = false;
		} else {
			noteActive = seq.isTrackActive(trackNumber) && seq.isNoteActive(drumId);
		}

		boolean isDraggingVolumeBar = ((trackVolumeBar != null) && trackVolumeBar.isDragging()) || showVolume;
		noteGraph.setShowingNoteVelocity(isDraggingVolumeBar);

		if (isDraggingVolumeBar)
			noteGraph.setDeltaVolume(trackVolumeBar.getDeltaVolume());
		else
			noteGraph.setDeltaVolume(abcPart.getTrackVolumeAdjust(trackInfo.getTrackNumber()));

		for (AbcPart part : abcPart.getAbcSong().getParts()) {
			if (part.isTrackEnabled(trackNumber)) {
				if (part != this.abcPart && part.isPercussionNoteEnabled(trackNumber, drumId))
					noteEnabledOtherPart = true;

				if (abcPreviewMode) {
					Note drumNote = part.mapNote(trackNumber, drumId, 0);
					if (drumNote != null && abcSequencer.isTrackActive(part.getPreviewSequenceTrackNumber())
							&& abcSequencer.isNoteActive(drumNote.id)) {
						noteActive = true;
					}
				}
			}
		}

		gutter.setOpaque(noteEnabled || noteEnabledOtherPart);
		if (noteEnabled)
			gutter.setBackground(ColorTable.PANEL_HIGHLIGHT.get());
		else if (noteEnabledOtherPart)
			gutter.setBackground(ColorTable.PANEL_HIGHLIGHT_OTHER_PART.get());

		noteGraph.setShowingAbcNotesOn(noteEnabled);
		checkBox.setEnabled(trackEnabled);
		drumComboBox.setEnabled(trackEnabled);
		drumComboBox.setVisible(abcPart.getInstrument() == LotroInstrument.BASIC_DRUM);
		drumComboBoxFX.setEnabled(trackEnabled);
		drumComboBoxFX.setVisible(abcPart.getInstrument() == LotroInstrument.STUDENT_FIDDLE);
        drumComboBoxJauntyFX.setEnabled(trackEnabled);
        drumComboBoxJauntyFX.setVisible(abcPart.getInstrument() == LotroInstrument.JAUNTY_HAND_KNELLS);

		if (!noteEnabled) {
			// disabled
			noteGraph.setNoteColor(ColorTable.NOTE_DRUM_OFF);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_OFF);
			
			setBackground(ColorTable.NOTE_DRUM_OFF_BACKGROUND.get());
			noteGraph.setBackground(ColorTable.NOTE_DRUM_OFF_BACKGROUND.get());
			checkBox.setForeground(ColorTable.PANEL_TEXT_NO_PERCUSSION_MATCH.get());
		} else if (trackEnabled && noteEnabled) {
			// enabled
			noteGraph.setNoteColor(ColorTable.NOTE_DRUM_ENABLED);
			noteGraph.setBadNoteColor(ColorTable.NOTE_DRUM_DISABLED);

			setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
			noteGraph.setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
			checkBox.setForeground(ColorTable.PANEL_TEXT_ENABLED.get());
		} else {
			// should never get here
			noteGraph.setNoteColor(ColorTable.NOTE_DRUM_OFF);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_OFF);

			setBackground(ColorTable.NOTE_DRUM_OFF_BACKGROUND.get());
			noteGraph.setBackground(ColorTable.NOTE_DRUM_OFF_BACKGROUND.get());
			checkBox.setForeground(ColorTable.PANEL_TEXT_NO_PERCUSSION_MATCH.get());
		}
	}

    @Override
	public void setAbcPreviewMode(boolean isAbcPreviewMode) {
		if (this.isAbcPreviewMode != isAbcPreviewMode) {
			this.isAbcPreviewMode = isAbcPreviewMode;
			updateState();
		}
	}

    @Override
	public boolean isAbcPreviewMode() {
		return abcSequencer != null && isAbcPreviewMode;
	}

	private LotroStudentFXInfo getSelectedFX() {
		return LotroStudentFXInfo.getById(abcPart.getFXMap(trackInfo.getTrackNumber()).get(drumId));
	}

    private LotroChromaticFXInfo getSelectedJauntyFX() {
        return LotroChromaticFXInfo.getById(LotroInstrument.JAUNTY_HAND_KNELLS, abcPart.getJauntyHandKnellsFXMap(trackInfo.getTrackNumber()).get(drumId));
    }

	public class DrumNoteGraph extends NoteGraph {
		private boolean showingAbcNotesOn = true;

		public DrumNoteGraph(SequencerWrapper sequencer, TrackInfo trackInfo) {
			super(sequencer, trackInfo, -1, 1, 2, 5);
		}
		
		@Override
		public Dimension getPreferredSize() {
			return DrumPanel.this.getPreferredSize();
		}
		
		@Override
		public Dimension getMinimumSize() {
			return new Dimension(48, DrumPanel.this.getPreferredSize().height);
		}

		public void setShowingAbcNotesOn(boolean showingAbcNotesOn) {
			if (this.showingAbcNotesOn != showingAbcNotesOn) {
				this.showingAbcNotesOn = showingAbcNotesOn;
				repaint();
			}
		}

		@Override
		protected int transposeNote(int noteId, long tickStart) {
			return 0;
		}

		@Override
		protected double getMinNoteHeightPx() {
			return 0.0;
		}

		@Override
		protected boolean isNotePlayable(NoteEvent ne, int addition) {
			return abcPart.isDrumPlayable(trackInfo.getTrackNumber(), ne.note.id);
		}

		@Override
		protected boolean isShowingNotesOn() {
			if (sequencer.isRunning())
				return sequencer.isTrackActive(trackInfo.getTrackNumber());

			if (abcSequencer != null && abcSequencer.isRunning())
				return showingAbcNotesOn;

			return false;
		}

		@Override
		protected boolean isNoteVisible(NoteEvent ne) {
			return ne.note.id == drumId;
		}
		
		@Override
		protected boolean isActiveTrack() {
			return abcPart.isTrackEnabled(trackInfo.getTrackNumber());
		}

		@Override
		protected boolean audibleNote(NoteEvent ne) {
			if (abcPart.getAbcSong().isHideEdits()) return true;
			return abcPart.getAudible(trackInfo.getTrackNumber(), ne.getStartTick(), isActiveTrack())
					&& abcPart.shouldPlay(ne, trackInfo.getTrackNumber());
		}

		@Override
		protected int getSourceNoteVelocity(NoteEvent note) {
			if (abcPart.getAbcSong().isHideEdits()) return note.velocity;
			return abcPart.getSectionNoteVelocity(trackInfo.getTrackNumber(), note);
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
			if (!isActiveTrack() || abcPart.getAbcSong().isHideEdits() || abcPart.sectionsTicked == null) {
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
					list.add(new Pair<>(data.tickToMicros(entry.getValue().startTick), data.tickToMicros(entry.getValue().endTick)));
				}
			}
			return list;
		}

		@Override
		protected int[] getSectionVelocity(NoteEvent note) {
			if (abcPart.getAbcSong().isHideEdits()) return super.getSectionVelocity(note);
			return abcPart.getSectionVolumeAdjust(trackInfo.getTrackNumber(), note);
			/*
			 * int[] empty = new int[2]; empty[0] = 0; empty[1] = 100; return empty;
			 */
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
	}

	public void setAbcPart(AbcPart part) {
		part.removeAbcListener(abcPartListener);
		this.abcPart = part;
		abcPart.addAbcListener(abcPartListener);
		checkBox.setSelected(abcPart.isPercussionNoteEnabled(trackInfo.getTrackNumber(), drumId));
		drumComboBox.setSelectedItem(getSelectedChoice());
		drumComboBoxFX.setSelectedItem(getSelectedFX());
        drumComboBoxJauntyFX.setSelectedItem(getSelectedJauntyFX());
		updateState();
	}
}
