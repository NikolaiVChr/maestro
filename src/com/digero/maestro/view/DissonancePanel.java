package com.digero.maestro.view;

import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.view.ColorTable;
import com.digero.common.view.LeanJLabel;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.DissonanceDetector;
import com.digero.maestro.midi.FakeNoteEvent;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.view.TrackPanel.TrackDimensions;
import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class DissonancePanel extends JPanel implements IDiscardable, TableLayoutConstants, ArrangementViewItem {
	// 0 1 2 3
	// +---+-------------------+-----------+---------------------+
	// | | | | +---------------+ |
	// 0 | | Disso  | 32 notes | | (histogram)                 | |
	// | | | | +---------------+ |
	// +---+-------------------+-----------+---------------------+

	static final int GUTTER_COLUMN = 0;
	static final int TITLE_COLUMN = 1;
	static final int COUNT_COLUMN = 2;
	static final int GRAPH_COLUMN = 3;
    static final int BUTTON_COLUMN = 3;

	public static final int CLIP_MAX_NOTES = 50;
    public static final int RED_NOTES      = 35;//Over or equal to
    public static final int ORANGE_NOTES   = 20;// Over or equal to
	static final int EXTRA_COUNT_COLUMN_WIDTH = 50;
	static final int HISTOGRAM_HEIGHT = 32;

	private static final int GUTTER_WIDTH = TrackPanel.GUTTER_WIDTH;
	private static final int TITLE_WIDTH = TrackPanel.TITLE_WIDTH_DEFAULT + TrackPanel.HGAP
			-EXTRA_COUNT_COLUMN_WIDTH;
	private static final int COUNT_WIDTH = TrackPanel.CONTROL_WIDTH_DEFAULT+EXTRA_COUNT_COLUMN_WIDTH;
    private static final int BUTTON_WIDTH = TrackPanel.PRIORITY_WIDTH_DEFAULT;

	private static final double[] LAYOUT_COLS = new double[] { GUTTER_WIDTH, TITLE_WIDTH, COUNT_WIDTH, BUTTON_WIDTH };
	private static final double[] LAYOUT_ROWS = new double[] { HISTOGRAM_HEIGHT, TableLayoutConstants.FILL};

	private final SequencerWrapper sequencer;
	private final SequencerWrapper abcSequencer;
    private boolean show = false;
    private boolean abcPreviewMode = false;

	private DissonanceNoteGraph dissoGraph;
	private final LeanJLabel currentCountLabel;
    private final JButton peakButton;

	private final AbcSong abcSong;
    private DissonanceDetector dissonanceDetector = null;

    public DissonancePanel(SequenceInfo sequenceInfo, SequencerWrapper sequencer, SequencerWrapper abcSequencer,
                           AbcSong abcSong) {
		super(new TableLayout(LAYOUT_COLS, LAYOUT_ROWS));
		this.abcSong = abcSong;
		
		TableLayout tableLayout = (TableLayout) getLayout();
		tableLayout.setHGap(TrackPanel.HGAP);

		TrackDimensions dims = TrackPanel.calculateTrackDims();
		LAYOUT_COLS[1] = dims.titleWidth + TrackPanel.HGAP * 2 - EXTRA_COUNT_COLUMN_WIDTH;
		LAYOUT_COLS[2] = dims.controlWidth + EXTRA_COUNT_COLUMN_WIDTH;
		tableLayout.setColumn(LAYOUT_COLS);

		setBorder(new CompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()),
				BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0x555555))));

		this.sequencer = sequencer;
		this.abcSequencer = abcSequencer;

		JPanel gutter = new JPanel();
		gutter.setOpaque(true);
		gutter.setBackground(ColorTable.PANEL_HIGHLIGHT_OTHER_PART.get());

		this.dissoGraph = new DissonanceNoteGraph(sequenceInfo, sequencer);
		dissoGraph.setBackground(ColorTable.GRAPH_BACKGROUND_DISABLED.get());
		dissoGraph.setPreferredSize(new Dimension(dissoGraph.getPreferredSize().width, getPreferredSize().height));
		dissoGraph.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()));
		setBackground(ColorTable.GRAPH_BACKGROUND_DISABLED.get());

		JLabel titleLabel = new JLabel("Dissonance");
		titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
		titleLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());

		currentCountLabel = new LeanJLabel("128 score (Peak: 128)");
		currentCountLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		currentCountLabel.setToolTipText("Dissonance score");
		currentCountLabel.setHorizontalAlignment(JLabel.RIGHT);

        peakButton = new JButton("P");
        peakButton.setToolTipText("Jump to highest peak");
        peakButton.addActionListener(a -> {
            if (dissonanceDetector != null) {
                long tick = dissonanceDetector.getPeakTick(abcSong);
                sequencer.setTickPosition(tick);
            }
        });
		
		updateCountLabel();

		add(gutter, GUTTER_COLUMN + ", 0, "+GUTTER_COLUMN + ", 1, F, F");
		add(titleLabel, TITLE_COLUMN + ", 0");
		add(currentCountLabel, COUNT_COLUMN + ", 0, R, C");
        add(peakButton, BUTTON_COLUMN + ", 0, R, C");

//		add(tempoGraph, GRAPH_COLUMN + ", 0");

		sequencer.addChangeListener(sequencerListener);
		abcSequencer.addChangeListener(sequencerListener);
	}
	
	@Override
	public DissonanceNoteGraph getNoteGraph() {
		return dissoGraph;
	}

	@Override
	public void discard() {
		if (sequencer != null)
			sequencer.removeChangeListener(sequencerListener);
		if (abcSequencer != null)
			abcSequencer.removeChangeListener(sequencerListener);
	}

    public void setShowPanel(boolean show) {
        this.show = show;
        updateVisibility();
    }

    private void updateVisibility() {
        setVisible(abcPreviewMode && show);
        dissoGraph.setVisible(abcPreviewMode && show);
        if (abcPreviewMode && show) {
            setMaximumSize(null);
            dissoGraph.setMaximumSize(null);
        } else {
            setMaximumSize(new Dimension(0,0));
            dissoGraph.setMaximumSize(new Dimension(0,0));
        }
    }

    @Override
	public void setAbcPreviewMode(boolean abcPreviewMode) {
		if (this.abcPreviewMode != abcPreviewMode) {
			this.abcPreviewMode = abcPreviewMode;
			updateCountLabel();
			currentCountLabel.revalidate();
		}
        updateVisibility();
	}

    @Override
	public boolean isAbcPreviewMode() {
		return abcPreviewMode;
	}

    /**
     * Called by sequencer updates, abcPart updates and preview mode toggle.
     */
	public void updateCountLabel() {
        if (dissonanceDetector != null) {
            int notes = dissonanceDetector.get(abcSequencer.getThumbTick(), abcSong).getTotalScore();// Must be abcSeq, due to tuneeditor can change micros from this call
            currentCountLabel.setText(notes + " score (Peak: " + dissonanceDetector.max(abcSong) + ")");
        } else {
            currentCountLabel.setText("No preview data");
        }
	}

	private final Listener<SequencerEvent> sequencerListener = e -> {
		
		dissoGraph.repaint();

        updateCountLabel();
	};

    public void setDissonance(DissonanceDetector dissonanceDetector) {
        this.dissonanceDetector = dissonanceDetector;
        dissoGraph.recalcPolyphonyEvents();
        dissoGraph.repaint();
        updateCountLabel();
    }

    public class DissonanceNoteGraph extends NoteGraph {
		private List<NoteEvent> events = new ArrayList<>();

		public DissonanceNoteGraph(SequenceInfo sequenceInfo, SequencerWrapper sequencer) {
			super(sequencer, sequenceInfo, null, 0, CLIP_MAX_NOTES, 1, 2);
			
			setOctaveLinesVisible(false);
			setHistogramThresholdLinesVisible(true);
			setNoteColor(ColorTable.NOTE_POLYPHONY);
			setBadNoteColor(ColorTable.NOTE_POLYPHONY_WARNING);
			setExtraBadNoteColor(ColorTable.NOTE_POLYPHONY_OVER);
			setNoteOnColor(ColorTable.NOTE_POLYPHONY_ON);
			setNoteOnExtraHeightPix(0);
			setNoteOnOutlineWidthPix(0);
            setToolTipText("Polyphony");
		}

        @Override
        public int getLowThreshold() {
            return ORANGE_NOTES;
        }

        @Override
        public int getHighThreshold() {
            return RED_NOTES;
        }

        private int lastX = -1;
        private String lastStr = "Dissonance";
        private DissonanceDetector lastDissonance = null;

        @Override
        public String getToolTipText(MouseEvent event) {
            if (dissonanceDetector != null) {
                int x = event.getX();
                if (x == lastX && dissonanceDetector == lastDissonance) return lastStr;

                // Convert mouse X coordinate to midi-micros position
                AffineTransform xForm = getTransform();
                Point2D.Double pt = new Point2D.Double(x, 0);
                try {
                    xForm.inverseTransform(pt, pt);
                } catch (NoninvertibleTransformException e) {
                    lastX = -1;
                    lastStr = "Dissonance";
                    lastDissonance = null;
                    return null;
                }

                DissonanceDetector.DissonanceEvent notesPart = dissonanceDetector.get(sequenceInfo.getDataCache ().microsToTick((long) pt.x), abcSong);
                String tooltip = notesPart ==null?"":notesPart.getTooltipHtml();

                lastX = x;
                lastStr = tooltip;
                lastDissonance = dissonanceDetector;
                return lastStr;
            }
            lastX = -1;
            lastStr = "Dissonance";
            lastDissonance = null;
            return null;
        }

		private void recalcPolyphonyEvents() {
			// Make fake note events for every count event
			events = new ArrayList<>();
			if (abcSong.getQTM() == null) return;

            Entry<Long, DissonanceDetector.DissonanceEvent> prevEvent = null;

			SequenceDataCache dataCache = sequenceInfo.getDataCache();

            if (dissonanceDetector != null) {
                for (Entry<Long, DissonanceDetector.DissonanceEvent> event : dissonanceDetector.getResults(abcSong).entrySet()) {

                    if (prevEvent != null) {
                        int id = Math.min(CLIP_MAX_NOTES, prevEvent.getValue().getTotalScore());
                        events.add(new FakeNoteEvent(Note.fromId(id), prevEvent.getValue().tick, event.getValue().tick, dataCache));
                    }
                    prevEvent = event;
                }
            }

			if (prevEvent != null) {
				int id = Math.min(CLIP_MAX_NOTES,prevEvent.getValue().getTotalScore());
				events.add(
						new FakeNoteEvent(Note.fromId(id), prevEvent.getValue().tick, dataCache.getSongLengthTicks(), dataCache));
			} else {
				int id = 0;
				events.add(new FakeNoteEvent(Note.fromId(id), 0, dataCache.getSongLengthTicks(), dataCache));
			}
		}
		
		@Override
		protected boolean isNotePlayable(NoteEvent ne, int addition) {
			return ne.note.id < ORANGE_NOTES;
		}
		
		@Override
		protected boolean isNoteExtraBad(NoteEvent ne, int addition) {
			return ne.note.id >= RED_NOTES;
		}
		
		@Override
		protected boolean isShowingNotesOn() {
			return sequencer.isRunning() || abcSequencer.isRunning();
		}

		@Override
		protected List<NoteEvent> getEvents() {
			if (dissonanceDetector == null || events.isEmpty() || dissonanceDetector.isDirty())
				recalcPolyphonyEvents();
			return events;
		}

		@Override
		protected boolean[] getSectionsModified() {
			return null;
		}

        protected boolean isBars() {
            return true;
        }
	}

	@Override
	public boolean isVerticalZoomForbidden() {
		return false;
	}
}
