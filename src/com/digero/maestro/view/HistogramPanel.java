package com.digero.maestro.view;

import com.digero.maestro.abc.AbcPart;
import info.clearthought.layout.TableLayout;
import info.clearthought.layout.TableLayoutConstants;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import javax.swing.*;
import javax.swing.border.CompoundBorder;

import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.common.view.ColorTable;
import com.digero.common.view.LeanJLabel;
import com.digero.maestro.abc.AbcSong;
import com.digero.maestro.abc.PolyphonyHistogram;
import com.digero.maestro.midi.FakeNoteEvent;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.view.TrackPanel.TrackDimensions;
import org.jetbrains.annotations.NotNull;

public class HistogramPanel extends JPanel implements IDiscardable, TableLayoutConstants, ArrangementViewItem {
	// 0 1 2 3
	// +---+-------------------+-----------+---------------------+
	// | | | | +---------------+ |
	// 0 | | Poly   | 32 notes | | (histogram)                 | |
	// | | | | +---------------+ |
	// +---+-------------------+-----------+---------------------+

	static final int GUTTER_COLUMN = 0;
	static final int TITLE_COLUMN = 1;
	static final int COUNT_COLUMN = 2;
	static final int GRAPH_COLUMN = 3;
    static final int BUTTON_COLUMN = 3;
	
	public static final int CLIP_MAX_NOTES = 80;// Show from 0 to 80 notes
	public static final int ORANGE_NOTES   = 45;// Over or equal to 45 and they go orange color. The limit is 64, but emotes and dances also fill.
	public static final int RED_NOTES      = 64;//Over or equal to 64, notes become red.
	static final int EXTRA_COUNT_COLUMN_WIDTH = 50;
	static final int HISTOGRAM_HEIGHT = 64;

	private static final int GUTTER_WIDTH = TrackPanel.GUTTER_WIDTH;
	private static final int TITLE_WIDTH = TrackPanel.TITLE_WIDTH_DEFAULT + TrackPanel.HGAP
			-EXTRA_COUNT_COLUMN_WIDTH;
	private static final int COUNT_WIDTH = TrackPanel.CONTROL_WIDTH_DEFAULT+EXTRA_COUNT_COLUMN_WIDTH;
    private static final int BUTTON_WIDTH = TrackPanel.PRIORITY_WIDTH_DEFAULT;

	private static double[] LAYOUT_COLS = new double[] { GUTTER_WIDTH, TITLE_WIDTH, COUNT_WIDTH, BUTTON_WIDTH };
	private static double[] LAYOUT_ROWS = new double[] { HISTOGRAM_HEIGHT };

	private final SequencerWrapper sequencer;
	private final SequencerWrapper abcSequencer;
    private boolean show = false;
    private boolean abcPreviewMode = false;

	private HistogramNoteGraph histoGraph;
	private final LeanJLabel currentCountLabel;
    private final JButton peakButton;

	private AbcSong abcSong;
    private PolyphonyHistogram histogram = null;

    public HistogramPanel(SequenceInfo sequenceInfo, SequencerWrapper sequencer, SequencerWrapper abcSequencer,
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

		this.histoGraph = new HistogramNoteGraph(sequenceInfo, sequencer);
		histoGraph.setBackground(ColorTable.GRAPH_BACKGROUND_DISABLED.get());
		histoGraph.setPreferredSize(new Dimension(histoGraph.getPreferredSize().width, getPreferredSize().height));
		histoGraph.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ColorTable.PANEL_BORDER.get()));
		setBackground(ColorTable.GRAPH_BACKGROUND_DISABLED.get());

		JLabel titleLabel = new JLabel("Polyphony");
		titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
		titleLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());

		currentCountLabel = new LeanJLabel("128 notes (Peak: 128)");
		currentCountLabel.setForeground(ColorTable.PANEL_TEXT_DISABLED.get());
		currentCountLabel.setToolTipText("Number of concurrent playing notes.\nThis is useful due to lotro's limitation of 64 sounds, including dance footsteps and emotes.\nGreen sections have under 45 notes at once, and shouldn't have note loss.\nYellow sections have 45+ notes, and red sections have 64+ notes.");
		currentCountLabel.setHorizontalAlignment(JLabel.RIGHT);

        peakButton = new JButton("P");
        peakButton.setToolTipText("Jump to highest peak");
        peakButton.addActionListener(a -> {
            if (histogram != null) {
                long tick = histogram.getPeakTick();
                sequencer.setTickPosition(tick);
            }
        });
		
		updateCountLabel();

		add(gutter, GUTTER_COLUMN + ", 0");
		add(titleLabel, TITLE_COLUMN + ", 0");
		add(currentCountLabel, COUNT_COLUMN + ", 0, R, C");
        add(peakButton, BUTTON_COLUMN + ", 0, R, C");

//		add(tempoGraph, GRAPH_COLUMN + ", 0");

		sequencer.addChangeListener(sequencerListener);
		abcSequencer.addChangeListener(sequencerListener);
	}
	
	@Override
	public HistogramNoteGraph getNoteGraph() {
		return histoGraph;
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
        histoGraph.setVisible(abcPreviewMode && show);
        if (abcPreviewMode && show) {
            setMaximumSize(null);
            histoGraph.setMaximumSize(null);
        } else {
            setMaximumSize(new Dimension(0,0));
            histoGraph.setMaximumSize(new Dimension(0,0));
        }
        PolyphonyHistogram.enabled = show;//TODO
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
        if (histogram != null) {
            if (histogram.isDirty()) {
                histogram.sumUp(abcSong);
            }
            int notes = histogram.get(abcSequencer.getThumbPosition());// Must be abcSeq, due to tuneeditor can change micros from this call
            currentCountLabel.setText(notes + " notes (Peak: " + histogram.max() + ")");
        } else {
            currentCountLabel.setText("No preview data");
        }
	}

	private Listener<SequencerEvent> sequencerListener = e -> {
		
		histoGraph.repaint();
		
		//if (e.getProperty().isInMask(SequencerProperty.THUMB_POSITION_MASK)) {
			updateCountLabel();
		//}
	};

    public void setHistogram(PolyphonyHistogram histogram) {
        this.histogram = histogram;
        histoGraph.repaint();
        updateCountLabel();
    }

    public class HistogramNoteGraph extends NoteGraph {
		private List<NoteEvent> events = new ArrayList<>();

		public HistogramNoteGraph(SequenceInfo sequenceInfo, SequencerWrapper sequencer) {
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

        private int lastX = -1;
        private String lastStr = "Polyphony";
        private PolyphonyHistogram lastHistogram = null;

        @Override
        public String getToolTipText(MouseEvent event) {
            if (histogram != null) {
                int x = event.getX();
                if (x == lastX && histogram == lastHistogram) return lastStr;

                // Convert mouse X coordinate to midi-micros position
                AffineTransform xForm = getTransform();
                Point2D.Double pt = new Point2D.Double(x, 0);
                try {
                    xForm.inverseTransform(pt, pt);
                } catch (NoninvertibleTransformException e) {
                    lastX = -1;
                    lastStr = "Polyphony";
                    lastHistogram = null;
                    return null;
                }
                StringBuilder tooltip = new StringBuilder();
                tooltip.append("<html>Part polyphony:");
                for (AbcPart part : abcSong.getParts()) {
                    int notesPart = histogram.get((long) pt.x, part);
                    if (notesPart == 0) continue;
                    tooltip.append("<br>")
                            .append(escapeHtml(part.getTitle()))
                            .append(":&nbsp;&nbsp;")
                            .append(notesPart);
                }
                tooltip.append("</html>");
                lastX = x;
                lastStr = tooltip.toString();
                lastHistogram = histogram;
                return lastStr;
            }
            lastX = -1;
            lastStr = "Polyphony";
            lastHistogram = null;
            return null;
        }

        private String escapeHtml(@NotNull String text) {

            int len = text.length();
            int i = 0;

            for (; i < len; i++) {
                char c = text.charAt(i);
                if (c == '&' || c == '<' || c == '>' || c == '"' || c == '\'') {
                    break;
                }
            }

            if (i == len) return text;

            StringBuilder b = new StringBuilder(len + 16);
            b.append(text, 0, i); // append the clean part

            for (; i < len; i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '&' -> b.append("&amp;");
                    case '<' -> b.append("&lt;");
                    case '>' -> b.append("&gt;");
                    case '"' -> b.append("&quot;");
                    case '\'' -> b.append("&#39;");
                    default -> b.append(c);
                }
            }
            return b.toString();
        }

		private void recalcPolyphonyEvents() {
			// Make fake note events for every count event
			events = new ArrayList<>();
			if (abcSong.getQTM() == null) return;
			
			Entry<Long, Pair<Long,Integer>> prevEvent = null;

            if (histogram != null) {
                histogram.sumUp(abcSong);
                histogram.setClean();
            }

			SequenceDataCache dataCache = sequenceInfo.getDataCache();
			long prevTick = 0L;
            if (histogram != null) {
                for (Entry<Long, Pair<Long, Integer>> event : histogram.getAll()) {

                    if (prevEvent != null) {
                        //assert prevTick >= event.getValue().first : "OOPS HISTO";
                        int id = Math.min(CLIP_MAX_NOTES, prevEvent.getValue().second);
                        events.add(new FakeNoteEvent(Note.fromId(id), prevEvent.getValue().first, event.getValue().first, dataCache));
                    }
                    prevEvent = event;
                    prevTick = event.getValue().first;
                }
            }

			if (prevEvent != null) {
				int id = Math.min(CLIP_MAX_NOTES,prevEvent.getValue().second);
				events.add(
						new FakeNoteEvent(Note.fromId(id), prevEvent.getValue().first, dataCache.getSongLengthTicks(), dataCache));
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
			if (histogram == null || histogram.isDirty() || events.isEmpty())
				recalcPolyphonyEvents();
			return events;
		}

		@Override
		protected boolean[] getSectionsModified() {
			//if (abcSong == null) {
				return null;
			//}
			//return abcSong.tuneBarsModified;
		}
	}

	@Override
	public boolean isVerticalZoomForbidden() {
		return true;
	}
}
