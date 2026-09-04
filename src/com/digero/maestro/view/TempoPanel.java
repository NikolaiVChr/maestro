package com.digero.maestro.view;

import com.digero.common.view.UIText;
import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;

import com.digero.common.midi.MidiUtils;
import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.common.view.ColorTable;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.AbcSongEvent;
import com.digero.maestro.abc.QuantizedTimingInfo.TimingInfoEvent;
import com.digero.maestro.abc.TuneLine;
import com.digero.maestro.abc.AbcSongEvent.AbcSongProperty;
import com.digero.maestro.midi.FakeNoteEvent;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceDataCache.TempoEvent;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.view.TrackPanel.TrackDimensions;

public class TempoPanel extends JPanel implements IDiscardable, TableLayoutConstants, ArrangementViewItem {
	// 0 1 2 3
	// +---+-------------------+-----------+---------------------+
	// | | | | +---------------+ |
	// 0 | | Tempo | 120 BPM | | (tempo graph) | |
	// | | | | +---------------+ |
	// +---+-------------------+-----------+---------------------+

	static final int GUTTER_COLUMN = 0;
	static final int TITLE_COLUMN = 1;
	static final int TEMPO_COLUMN = 2;
	static final int GRAPH_COLUMN = 3;

	private static final int GUTTER_WIDTH = TrackPanel.GUTTER_WIDTH;
	private static final int TITLE_WIDTH = TrackPanel.TITLE_WIDTH_DEFAULT + TrackPanel.HGAP
			+ TrackPanel.PRIORITY_WIDTH_DEFAULT;
	private static final int TEMPO_WIDTH = TrackPanel.CONTROL_WIDTH_DEFAULT;

	private static double[] LAYOUT_COLS = new double[] { GUTTER_WIDTH, TITLE_WIDTH, TEMPO_WIDTH/*, FILL*/ };
	private static double[] LAYOUT_ROWS = new double[] { 32 };

	private final SequenceInfo sequenceInfo;
	private final SequencerWrapper sequencer;
	private final SequencerWrapper abcSequencer;
	private boolean abcPreviewMode = false;
	private volatile boolean refreshWanted = true;
	
	private int minBPM = 60;
	private int maxBPM = 160;

	private TempoNoteGraph tempoGraph;
	private JLabel currentTempoLabel;

	private AbcSong abcSong;
	private Listener<AbcSongEvent> songListen;

	public TempoPanel(SequenceInfo sequenceInfo, SequencerWrapper sequencer, SequencerWrapper abcSequencer,
			AbcSong abcSong) {
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));
		this.abcSong = abcSong;
		TableLayout tableLayout = (TableLayout) getLayout();
		tableLayout.setHGap(TrackPanel.HGAP);

		TrackDimensions dims = TrackPanel.calculateTrackDims();
		LAYOUT_COLS[1] = dims.titleWidth + TrackPanel.HGAP * 2 + dims.priorityWidth;
		LAYOUT_COLS[2] = dims.controlWidth;
		tableLayout.setColumn(LAYOUT_COLS);

		setBorder(new CompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER_HORIZ.get()),
				BorderFactory.createMatteBorder(0, 0, 0, 1, ColorTable.PANEL_BORDER_VERTICAL.get())));

		this.sequenceInfo = sequenceInfo;
		this.sequencer = sequencer;
		this.abcSequencer = abcSequencer;

		calcBPMBoundsMIDI();

		JPanel gutter = new JPanel();
		gutter.setOpaque(true);
		gutter.setBackground(ColorTable.PANEL_HIGHLIGHT_OTHER_PART.get());

		this.tempoGraph = new TempoNoteGraph(sequenceInfo, sequencer);
		tempoGraph.setBackground(ColorTable.TEMPO_BACKGROUND.get());
		tempoGraph.setPreferredSize(new Dimension(tempoGraph.getPreferredSize().width, getPreferredSize().height));
		tempoGraph.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER_HORIZ.get()));
		setBackground(ColorTable.TEMPO_BACKGROUND.get());

		JLabel titleLabel = new JLabel(UIText.get("maestro.tempoGraph.tempo"));
		titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
		titleLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());

		currentTempoLabel = new JLabel();
		currentTempoLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		updateTempoLabel();

		add(gutter, GUTTER_COLUMN + ", 0");
		add(titleLabel, TITLE_COLUMN + ", 0");
		add(currentTempoLabel, TEMPO_COLUMN + ", 0, R, C");
//		add(tempoGraph, GRAPH_COLUMN + ", 0");

		sequencer.addChangeListener(sequencerListener);
		abcSequencer.addChangeListener(sequencerListener);
		
		songListen = new Listener<AbcSongEvent>() {
			@Override
			public void onEvent(AbcSongEvent e) {
				if (e.getProperty().equals(AbcSongProperty.HIDE_EDITS_UPDATE) || e.getProperty().equals(AbcSongProperty.TUNE_EDIT)) {
					refreshWanted = true;
					if (tempoGraph != null) {
						tempoGraph.invalidateNoteCache();
						tempoGraph.repaint();
					}
				}
			}
		};
		abcSong.addSongListener(songListen);
	}

	private void calcBPMBoundsMIDI() {
		minBPM = 60;
		maxBPM = 160;
		for (TempoEvent event : sequenceInfo.getDataCache().getTempoEvents().values()) {
			int bpm = (int) Math.round(MidiUtils.convertTempo(event.tempoMPQ));
			if (bpm < minBPM)
				minBPM = bpm;
			if (bpm > maxBPM)
				maxBPM = bpm;
		}
	}
	
	private void calcBPMBoundsABC(Collection<TimingInfoEvent> events3) {
		minBPM = 60;
		maxBPM = 160;
		for (TimingInfoEvent event : events3) {
			int bpm = event.info().getTempoBPM();
			if (bpm < minBPM)
				minBPM = bpm;
			if (bpm > maxBPM)
				maxBPM = bpm;
		}
	}
	
	@Override
	public TempoNoteGraph getNoteGraph() {
		return tempoGraph;
	}

	@Override
	public void discard() {
		if (abcSong != null && songListen != null)
			abcSong.removeSongListener(songListen);
		if (sequencer != null)
			sequencer.removeChangeListener(sequencerListener);
		if (abcSequencer != null)
			abcSequencer.removeChangeListener(sequencerListener);
	}

    @Override
	public void setAbcPreviewMode(boolean abcPreviewMode) {
		refreshWanted = true;
		if (this.abcPreviewMode != abcPreviewMode) {
			this.abcPreviewMode = abcPreviewMode;
			updateTempoLabel();
		}
		if (tempoGraph != null) {
			tempoGraph.invalidateNoteCache();
			tempoGraph.repaint();
		}
	}

    @Override
	public boolean isAbcPreviewMode() {
		return abcPreviewMode;
	}

	private float getCurrentTempoFactor() {
		return isAbcPreviewMode() ? abcSequencer.getTempoFactor() : sequencer.getTempoFactor();
	}

	private int lastRenderedBPM = -1;

	private void updateTempoLabel() {
		int mpq = sequenceInfo.getDataCache().getTempoMPQ(sequencer.getThumbTick());
		int mpqAbc = mpq;
		if (abcPreviewMode) { //  && !abcSong.isHideEdits()
			mpqAbc = abcSong.getAbcTempoMPQ(abcSequencer.getThumbTick());
			if (mpqAbc == 0) {
				mpqAbc = mpq;
			} else {
				mpq = mpqAbc;
			}
		}
        //TODO: replace with abcSong.getAbcTimingInfo().multiplyByExportTempoFactor():
		int bpm = (int) Math.round(MidiUtils.convertTempo(mpq) * getCurrentTempoFactor());
		if (bpm != lastRenderedBPM) {
			currentTempoLabel.setText(UIText.get("maestro.0.bpm", bpm));
			lastRenderedBPM = bpm;
		}
	}

	private Listener<SequencerEvent> sequencerListener = e -> {
		if (e.getProperty().isInMask(SequencerProperty.THUMB_POSITION_MASK | SequencerProperty.TEMPO.mask))
			updateTempoLabel();

		if (e.getProperty() == SequencerProperty.IS_RUNNING)
			tempoGraph.repaint();
	};

	private int tempoToNoteId(int tempoMPQ, int minBPM, int maxBPM) {
		int bpm = (int) Math.round(MidiUtils.convertTempo(tempoMPQ));

		float tempoFactor = getCurrentTempoFactor();
		minBPM = Math.round(minBPM * tempoFactor);
		maxBPM = Math.round(maxBPM * tempoFactor);
		bpm = Math.round(bpm * tempoFactor);

		return (bpm - minBPM) * (Note.MAX.id - Note.MIN.id) / (maxBPM - minBPM) + Note.MIN.id;
	}

	public class TempoNoteGraph extends NoteGraph {
		private List<NoteEvent> events;

		public TempoNoteGraph(SequenceInfo sequenceInfo, SequencerWrapper sequencer) {
			super(sequencer, sequenceInfo, Note.MIN.id - (Note.MIN.id + Note.MAX.id) / 4,
					Note.MAX.id + (Note.MIN.id + Note.MAX.id) / 4);

			setOctaveLinesVisible(false);
			setNoteColor(ColorTable.TEMPO);
			setNoteOnColor(ColorTable.TEMPO_ON);
			setNoteOnExtraHeightPix(0);
			setNoteOnOutlineWidthPix(0);
		}

		private void recalcTempoEvents() {
			// Underlying events list is being rebuilt; the note render cache must follow.
			invalidateNoteCache();
			// Make fake note events for every tempo event
			events = new ArrayList<>();
			Collection<TimingInfoEvent> events2 = abcSong.getTimingInfoByTick();
			if (abcPreviewMode && !abcSong.isHideEdits() && events2 != null) {
				calcBPMBoundsABC(events2);
				TimingInfoEvent prevEvent = null;
				for (TimingInfoEvent event : events2) {
					if (prevEvent != null) {
						int id = tempoToNoteId(prevEvent.info().getTempoMPQ(), minBPM, maxBPM);
						Note fakenote = Note.fromId(id);
						assert fakenote != null : "If this happens then something is wrong with min/max BPM";
						if (fakenote != null) events.add(new FakeNoteEvent(fakenote, prevEvent.tick(), event.tick(), sequenceInfo.getDataCache()));
					} else {
						//int bpm = (int) Math.round(MidiUtils.convertTempo(event.info.getTempoMPQ()));
						//System.out.println(bpm);
					}
					prevEvent = event;
				}
				
				if (prevEvent != null) {
					int id = tempoToNoteId(prevEvent.info().getTempoMPQ(), minBPM, maxBPM);
					events.add(
							new FakeNoteEvent(Note.fromId(id), prevEvent.tick(), sequenceInfo.getDataCache().getSongLengthTicks(), sequenceInfo.getDataCache()));
				} else {
					int id = tempoToNoteId(sequenceInfo.getPrimaryTempoMPQ(), minBPM, maxBPM);
					events.add(new FakeNoteEvent(Note.fromId(id), 0, sequenceInfo.getDataCache().getSongLengthTicks(), sequenceInfo.getDataCache()));
				}
			} else {
				TempoEvent prevEvent = null;
				SequenceDataCache dataCache = sequenceInfo.getDataCache();
				calcBPMBoundsMIDI();
				for (TempoEvent event : dataCache.getTempoEvents().values()) {
					if (prevEvent != null) {
						int id = tempoToNoteId(prevEvent.tempoMPQ, minBPM, maxBPM);
						Note fakenote = Note.fromId(id);
						assert fakenote != null : "If this happens then something is wrong with min/max BPM";
						if (fakenote != null) events.add(new FakeNoteEvent(fakenote, prevEvent.tick, event.tick, dataCache));
					}
					prevEvent = event;
				}
	
				if (prevEvent != null) {
					int id = tempoToNoteId(prevEvent.tempoMPQ, minBPM, maxBPM);
					events.add(
							new FakeNoteEvent(Note.fromId(id), prevEvent.tick, dataCache.getSongLengthTicks(), dataCache));
				} else {
					int id = tempoToNoteId(sequenceInfo.getPrimaryTempoMPQ(), minBPM, maxBPM);
					events.add(new FakeNoteEvent(Note.fromId(id), 0, dataCache.getSongLengthTicks(), dataCache));
				}
			}
		}

		@Override
		protected boolean isShowingNotesOn() {
			return sequencer.isRunning() || abcSequencer.isRunning();
		}

		@Override
		protected List<NoteEvent> getEvents() {
			if (events == null || refreshWanted) {
				recalcTempoEvents();
				refreshWanted = false;
			}
			return events;
		}

		@Override
		protected boolean[] getSectionsModified() {
			if (abcSong == null) {
				return null;
			}
			if (abcSong.isHideEdits()) {
				return super.getSectionsModified();
			}
			return abcSong.tuneBarsModified;
		}
		
		@Override
		protected List<Pair<Long, Long>> getMicrosModified(long from, long to) {
			if (abcSong == null) {
				return null;
			}
			if (abcSong.isHideEdits()) {
				return super.getMicrosModified(from,to);
			}
			SequenceDataCache data = sequenceInfo.getDataCache();
			long a = data.microsToTick(from);
			long b = data.microsToTick(to);
			List<Pair<Long, Long>> list = new ArrayList<>();
			for (Entry<Float, TuneLine> entry : abcSong.tuneBars.entrySet()) {
				if (entry.getValue().startTick < b && entry.getValue().endTick >= a) {
					list.add(new Pair<Long,Long>(data.tickToMicros(entry.getValue().startTick), data.tickToMicros(entry.getValue().endTick)));
				}
			}
			return list;
		}
	}

	@Override
	public boolean isVerticalZoomForbidden() {
		return true;
	}
}
