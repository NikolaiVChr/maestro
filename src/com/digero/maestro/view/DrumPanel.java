package com.digero.maestro.view;

import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiDrum;
import com.digero.common.midi.Note;
import com.digero.common.midi.NoteFilterSequencerWrapper;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.ICompileConstants;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Util;
import com.digero.common.view.ColorTable;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.AbcPartEvent;
import com.digero.maestro.abc.AbcSongEvent;
import com.digero.maestro.abc.LotroDrumInfo;
import com.digero.maestro.abc.LotroStudentFXInfo;
import com.digero.maestro.abc.AbcSongEvent.AbcSongProperty;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.view.TrackPanel.TrackDimensions;

@SuppressWarnings("serial")
public class DrumPanel extends JPanel implements IDiscardable, TableLayoutConstants, ICompileConstants {
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

	private JPanel gutter;
	private JCheckBox checkBox;
	private JComboBox<LotroDrumInfo> drumComboBox;
	private JComboBox<LotroStudentFXInfo> drumComboBoxFX;
	private DrumNoteGraph noteGraph;
	private TrackVolumeBar trackVolumeBar;
	private ActionListener trackVolumeBarListener;
	private boolean showVolume = false;

	private TrackDimensions dims = new TrackDimensions(TITLE_WIDTH, 0, COMBO_WIDTH, -1);

	public DrumPanel(TrackInfo info, NoteFilterSequencerWrapper sequencer, AbcPart part, int drumNoteId,
			SequencerWrapper abcSequencer_, TrackVolumeBar trackVolumeBar_) {
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));

		this.trackInfo = info;
		this.seq = sequencer;
		this.abcSequencer = abcSequencer_;
		this.abcPart = part;
		this.drumId = drumNoteId;
		this.trackVolumeBar = trackVolumeBar_;

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
		checkBox.setSelected(abcPart.isDrumEnabled(trackInfo.getTrackNumber(), drumId));
		checkBox.addActionListener(
				e -> abcPart.setDrumEnabled(trackInfo.getTrackNumber(), drumId, checkBox.isSelected()));

		checkBox.setOpaque(false);

		String title = trackInfo.getTrackNumber() + ". " + trackInfo.getName();
		String instr;
		if (info.isDrumTrack())
			instr = MidiDrum.fromId(drumId).name;
		else {
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
			abcPart.getFXMap(trackInfo.getTrackNumber()).set(drumId, selected.note.id);
		});
		drumComboBox = new JComboBox<>(LotroDrumInfo.ALL_DRUMS.toArray(new LotroDrumInfo[0]));
		drumComboBox.setSelectedItem(getSelectedDrum());
		drumComboBox.setMaximumRowCount(20);
		drumComboBox.addActionListener(e -> {
			LotroDrumInfo selected = (LotroDrumInfo) drumComboBox.getSelectedItem();
			abcPart.getDrumMap(trackInfo.getTrackNumber()).set(drumId, selected.note.id);
		});

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
							((NoteFilterSequencerWrapper) abcSequencer).setNoteSolo(soloAbcTrack, soloAbcDrumId, true);
						}
					} else {
						soloTrack = trackNumber;
						soloDrumId = drumId;
						seq.setNoteSolo(trackNumber, drumId, true);
					}
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON3) {
					if (soloAbcTrack >= 0 && soloAbcDrumId >= 0 && abcSequencer instanceof NoteFilterSequencerWrapper) {
						((NoteFilterSequencerWrapper) abcSequencer).setNoteSolo(soloAbcTrack, soloAbcDrumId, false);
						abcSequencer.setTrackSolo(soloAbcTrack, prevSoloState);
					}
					soloAbcTrack = -1;
					soloAbcDrumId = -1;

					if (soloTrack >= 0 && soloDrumId >= 0) {
						seq.setNoteSolo(soloTrack, soloDrumId, false);
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
				updateState();
				noteGraph.repaint();
			}
		});

		add(gutter, "0, 0");
		add(checkBox, "1, 0");
		add(drumComboBox, "2, 0, f, c");
		add(drumComboBoxFX, "2, 0, f, c");

		updateState();
		noteGraph.setPreferredSize(new Dimension(noteGraph.getPreferredSize().width, getPreferredSize().height));
	}
	
	public DrumNoteGraph getNoteGraph() {
		return noteGraph;
	}
	
	public void setSelected(boolean selected) {
		checkBox.setSelected(selected);
		abcPart.setDrumEnabled(trackInfo.getTrackNumber(), drumId, checkBox.isSelected());
	}
	
	public boolean isSelected() {
		return checkBox.isSelected();
	}

	@Override
	public void discard() {
		noteGraph.discard();
		abcPart.removeAbcListener(abcPartListener);
		seq.removeChangeListener(sequencerListener);
		if (abcSequencer != null)
			abcSequencer.removeChangeListener(sequencerListener);
		if (trackVolumeBar != null)
			trackVolumeBar.removeActionListener(trackVolumeBarListener);
		abcPart.getAbcSong().removeSongListener(songListener);
	}

	private Listener<AbcPartEvent> abcPartListener = e -> {
		if (e.isNoteGraphRelated()) {
			checkBox.setEnabled(abcPart.isTrackEnabled(trackInfo.getTrackNumber()));
			checkBox.setSelected(abcPart.isDrumEnabled(trackInfo.getTrackNumber(), drumId));
			if (abcPart.getInstrument() == LotroInstrument.STUDENT_FIDDLE) {
				drumComboBoxFX.setSelectedItem(getSelectedFX());
			} else {
				drumComboBox.setSelectedItem(getSelectedDrum());
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
		boolean noteEnabled = abcPart.isDrumEnabled(trackNumber, drumId);
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
				if (part != this.abcPart && part.isDrumEnabled(trackNumber, drumId))
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

		noteGraph.setShowingAbcNotesOn(noteActive);
		checkBox.setEnabled(trackEnabled);
		drumComboBox.setEnabled(trackEnabled);
		drumComboBox.setVisible(abcPart.getInstrument() == LotroInstrument.BASIC_DRUM);
		drumComboBoxFX.setEnabled(trackEnabled);
		drumComboBoxFX.setVisible(abcPart.getInstrument() == LotroInstrument.STUDENT_FIDDLE);

		if (!noteActive) {
			noteGraph.setNoteColor(ColorTable.NOTE_DRUM_OFF);
			noteGraph.setBadNoteColor(ColorTable.NOTE_DRUM_OFF);

			setBackground(ColorTable.GRAPH_BACKGROUND_OFF.get());
			noteGraph.setBackground(ColorTable.GRAPH_BACKGROUND_OFF.get());
			checkBox.setForeground(ColorTable.PANEL_TEXT_OFF.get());
		} else if (trackEnabled && abcPart.isDrumEnabled(trackNumber, drumId)) {
			noteGraph.setNoteColor(ColorTable.NOTE_DRUM_ENABLED);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_ENABLED);

			setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
			noteGraph.setBackground(ColorTable.GRAPH_BACKGROUND_ENABLED.get());
			checkBox.setForeground(ColorTable.PANEL_TEXT_ENABLED.get());
		} else {
			noteGraph.setNoteColor(ColorTable.NOTE_DRUM_OFF);
			noteGraph.setBadNoteColor(ColorTable.NOTE_BAD_OFF);

			setBackground(ColorTable.GRAPH_BACKGROUND_OFF.get());
			noteGraph.setBackground(ColorTable.GRAPH_BACKGROUND_OFF.get());
			checkBox.setForeground(ColorTable.PANEL_TEXT_OFF.get());
		}
	}

	public void setAbcPreviewMode(boolean isAbcPreviewMode) {
		if (this.isAbcPreviewMode != isAbcPreviewMode) {
			this.isAbcPreviewMode = isAbcPreviewMode;
			updateState();
		}
	}

	private boolean isAbcPreviewMode() {
		return abcSequencer != null && isAbcPreviewMode;
	}

	private LotroDrumInfo getSelectedDrum() {
		return LotroDrumInfo.getById(abcPart.getDrumMap(trackInfo.getTrackNumber()).get(drumId));
	}

	private LotroStudentFXInfo getSelectedFX() {
		return LotroStudentFXInfo.getById(abcPart.getFXMap(trackInfo.getTrackNumber()).get(drumId));
	}

	public class DrumNoteGraph extends NoteGraph {
		private boolean showingAbcNotesOn = true;

		public DrumNoteGraph(SequencerWrapper sequencer, TrackInfo trackInfo) {
			super(sequencer, trackInfo, -1, 1, 2, 5);
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
		protected int[] getSectionVelocity(NoteEvent note) {
			if (abcPart.getAbcSong().isHideEdits()) return super.getSectionVelocity(note);
			return abcPart.getSectionVolumeAdjust(trackInfo.getTrackNumber(), note);
			/*
			 * int[] empty = new int[2]; empty[0] = 0; empty[1] = 100; return empty;
			 */
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
	}
}
