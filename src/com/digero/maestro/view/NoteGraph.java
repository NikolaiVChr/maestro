package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.digero.common.abc.Dynamics;
import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Util;
import com.digero.common.view.BarNumberLabel;
import com.digero.common.view.ColorTable;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.midi.TrackInfo;

@SuppressWarnings("serial")
public class NoteGraph extends JPanel implements Listener<SequencerEvent>, IDiscardable {
	protected final SequencerWrapper sequencer;
	protected SequenceInfo sequenceInfo;
	protected TrackInfo trackInfo;

	protected final int MIN_RENDERED;
	protected final int MAX_RENDERED;
	protected final double NOTE_WIDTH_PX;
	protected final double NOTE_HEIGHT_PX;
	private double noteOnOutlineWidthPix = 0.5;
	private double noteOnExtraHeightPix = 0.5;
	private static final double NOTE_VELOCITY_MIN_WIDTH_PX = 2;
	private static final double NOTE_VELOCITY_MIN_HEIGHT_PX = 6;

	private ColorTable noteColor = ColorTable.NOTE_ENABLED;
	private ColorTable badNoteColor = ColorTable.NOTE_BAD_ENABLED;
	private ColorTable noteOnColor = ColorTable.NOTE_ON;
	private ColorTable noteOnBorder = ColorTable.NOTE_ON_BORDER;
	private ColorTable extraBadNoteColor = ColorTable.NOTE_BAD_ENABLED;

	private boolean octaveLinesVisible = false;
	private boolean histogramThresholdLinesVisible = false;

	private Color[] noteColorByDynamics = new Color[Dynamics.values().length];
	private Color[] badNoteColorByDynamics = new Color[Dynamics.values().length];
	private boolean showingNoteVelocity = false;
	private int deltaVolume = 0;

	private JPanel indicatorLine;

	public NoteGraph(SequencerWrapper sequencer, TrackInfo trackInfo, int minRenderedNoteId, int maxRenderedNoteId) {
		this(sequencer, trackInfo, minRenderedNoteId, maxRenderedNoteId, 2, 2);
	}

	public NoteGraph(SequencerWrapper sequencer, TrackInfo trackInfo, int minRenderedNoteId, int maxRenderedNoteId,
			double noteWidthPx, double noteHeightPx) {
		this(sequencer, (trackInfo == null) ? null : trackInfo.getSequenceInfo(), trackInfo, minRenderedNoteId,
				maxRenderedNoteId, 2, 2);
	}

	public NoteGraph(SequencerWrapper sequencer, SequenceInfo sequenceInfo, int minRenderedNoteId,
			int maxRenderedNoteId) {
		this(sequencer, sequenceInfo, null, minRenderedNoteId, maxRenderedNoteId, 2, 2);
	}

	protected NoteGraph(SequencerWrapper sequencer, SequenceInfo sequenceInfo, TrackInfo trackInfo, int minRenderedNoteId,
			int maxRenderedNoteId, double noteWidthPx, double noteHeightPx) {
		super((LayoutManager) null);

		this.sequencer = sequencer;
		this.trackInfo = trackInfo;
		this.sequenceInfo = sequenceInfo;
		this.MIN_RENDERED = minRenderedNoteId;
		this.MAX_RENDERED = maxRenderedNoteId;
		this.NOTE_WIDTH_PX = noteWidthPx;
		this.NOTE_HEIGHT_PX = noteHeightPx;
		
//		this.setBorder(BorderFactory.createEmptyBorder());
		
//		this.setOpaque(true);

		this.sequencer.addChangeListener(this);

		indicatorLine = new JPanel((LayoutManager) null);
		indicatorLine.setSize(1, getHeight());
		indicatorLine.setBackground(ColorTable.INDICATOR.get());
		indicatorLine.setOpaque(true);
		add(indicatorLine);

		MyMouseListener mouseListener = new MyMouseListener();
		addMouseListener(mouseListener);
		addMouseMotionListener(mouseListener);

		setOpaque(true);
		setPreferredSize(new Dimension(200, 16));

		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				invalidateTransform();
				repositionIndicator();
			}
		});
	}

	@Override
	public void discard() {
		sequencer.removeChangeListener(this);
	}

	protected int transposeNote(int noteId, long tickStart) {
		return noteId;
	}

	protected boolean[] getSectionsModified() {
		return null;
	}

	protected boolean audibleNote(NoteEvent ne) {
		return true;
	}

	protected boolean isNotePlayable(NoteEvent ne, int addition) {
		return true;
	}
	
	protected boolean isNoteExtraBad(NoteEvent ne, int addition) {
		return false;
	}

	protected boolean isNoteVisible(NoteEvent ne) {
		return true;
	}

	public void setOctaveLinesVisible(boolean octaveLinesVisible) {
		if (this.octaveLinesVisible != octaveLinesVisible) {
			this.octaveLinesVisible = octaveLinesVisible;
			repaint();
		}
	}

	public boolean isOctaveLinesVisible() {
		return octaveLinesVisible;
	}
	
	public void setHistogramThresholdLinesVisible(boolean histogramThresholdLinesVisible) {
		if (this.histogramThresholdLinesVisible != histogramThresholdLinesVisible) {
			this.histogramThresholdLinesVisible = histogramThresholdLinesVisible;
			repaint();
		}
	}
	
	public boolean isHistogramThresholdLinesVisible() {
		return histogramThresholdLinesVisible;
	}

	public final void setNoteColor(ColorTable noteColor) {
		if (this.noteColor != noteColor) {
			this.noteColor = noteColor;
			Arrays.fill(noteColorByDynamics, null);
			repaint();
		}
	}

	public final void setBadNoteColor(ColorTable badNoteColor) {
		if (this.badNoteColor != badNoteColor) {
			this.badNoteColor = badNoteColor;
			Arrays.fill(badNoteColorByDynamics, null);
			repaint();
		}
	}
	
	public final void setExtraBadNoteColor(ColorTable extraBadNoteColor) {
		if (this.extraBadNoteColor != extraBadNoteColor) {
			this.extraBadNoteColor = extraBadNoteColor;
			repaint();
		}
	}

	public final void setNoteOnColor(ColorTable noteOnColor) {
		if (this.noteOnColor != noteOnColor) {
			this.noteOnColor = noteOnColor;
			repaint();
		}
	}

	public void setDeltaVolume(int deltaVolume) {
		if (this.deltaVolume != deltaVolume) {
			this.deltaVolume = deltaVolume;
			repaint();
		}
	}

	public int getDeltaVolume() {
		return deltaVolume;
	}

	public void setTrackInfo(TrackInfo trackInfo) {
		this.trackInfo = trackInfo;
		invalidateTransform();
		repaint();
	}

	public void setShowingNoteVelocity(boolean showingNoteVelocity) {
		if (this.showingNoteVelocity != showingNoteVelocity) {
			this.showingNoteVelocity = showingNoteVelocity;
			repaint();
		}
	}

	public boolean isShowingNoteVelocity() {
		return showingNoteVelocity;
	}

	public void setNoteOnExtraHeightPix(double noteOnExtraHeightPix) {
		if (this.noteOnExtraHeightPix != noteOnExtraHeightPix) {
			this.noteOnExtraHeightPix = noteOnExtraHeightPix;
			if (isShowingNotesOn())
				repaint();
		}
	}

	public double getNoteOnExtraHeightPix() {
		return noteOnExtraHeightPix;
	}

	public void setNoteOnOutlineWidthPix(double noteOnOutlineWidthPix) {
		if (this.noteOnOutlineWidthPix != noteOnOutlineWidthPix) {
			this.noteOnOutlineWidthPix = noteOnOutlineWidthPix;
			if (isShowingNotesOn())
				repaint();
		}
	}

	public double getNoteOnOutlineWidthPix() {
		return noteOnOutlineWidthPix;
	}

	protected List<NoteEvent> getEvents() {
		if (trackInfo == null)
			return Collections.emptyList();

		List<NoteEvent> list = new ArrayList<>();
		list.addAll(trackInfo.getEvents());
		
		return list;
	}

	private AffineTransform noteToScreenXForm = null; // Always use getTransform()

	protected final void invalidateTransform() {
		noteToScreenXForm = null;
		repaint();
	}

	/**
	 * Gets a transform that converts song coordinates into screen coordinates.
	 */
	protected final AffineTransform getTransform() {
		if (noteToScreenXForm == null) {
			// The transform currently depends on:
			// * This panel's width/height
			// * The length of the sequence
			// If it changes to depend on anything else, call invalidateTransform()
			// whenever any of its dependencies changes.

			double scrnX = 0;
			double scrnY = NOTE_HEIGHT_PX;
			double scrnW = getWidth();
			double scrnH = getHeight() - NOTE_HEIGHT_PX;

			double noteX = 0;
			double noteY = MAX_RENDERED; // The max note gets mapped to 0
			double noteW = sequencer.getLength();
			double noteH = MIN_RENDERED - MAX_RENDERED;

			AffineTransform scrnXForm;
			if (noteW <= 0 || scrnW <= 0 || scrnH <= 0) {
				scrnXForm = new AffineTransform();
			} else {
				scrnXForm = new AffineTransform(scrnW, 0, 0, scrnH, scrnX, scrnY);
				try {
					AffineTransform noteXForm = new AffineTransform(noteW, 0, 0, noteH, noteX, noteY);
					noteXForm.invert();
					scrnXForm.concatenate(noteXForm);
				} catch (NoninvertibleTransformException e) {
					e.printStackTrace();
					scrnXForm.setToIdentity();
				}
			}

			noteToScreenXForm = scrnXForm;
		}

		return noteToScreenXForm;
	}

	private long lastPaintedMinSongPos = -1;
	private long lastPaintedSongPos = -1;
	private long songPos = -1;

	protected boolean isShowingNotesOn() {
		if (trackInfo == null)
			return false;

		return sequencer.isRunning() && sequencer.isTrackActive(trackInfo.getTrackNumber());
	}

	private void repositionIndicator() {
		AffineTransform xform = getTransform();
		Point2D.Double pt = new Point2D.Double(sequencer.getThumbPosition(), 0);
		xform.transform(pt, pt);
		indicatorLine.setBounds((int) pt.x, 0, 1, getHeight());
	}

	@Override
	public void onEvent(SequencerEvent evt) {
		if (evt.getProperty() == SequencerProperty.LENGTH) {
			invalidateTransform();
		}

		if (evt.getProperty().isInMask(SequencerProperty.THUMB_POSITION_MASK)) {
			repositionIndicator();
		}

		if (evt.getProperty() == SequencerProperty.IS_DRAGGING) {
			indicatorLine.setBackground(
					sequencer.isDragging() ? ColorTable.INDICATOR_ACTIVE.get() : ColorTable.INDICATOR.get());
		}

		// Repaint the parts that need it
		if (evt.getProperty() == SequencerProperty.POSITION) {
			final long currentSongPos = sequencer.getPosition();
			final long leftSongPos = Math.min(currentSongPos, Math.min(lastPaintedMinSongPos, songPos));
			final long rightSongPos = Math.max(currentSongPos, Math.max(lastPaintedSongPos, songPos))
					+ SequencerWrapper.UPDATE_FREQUENCY_MICROS;
			songPos = currentSongPos;

			if (leftSongPos < 0) {
				repaint();
			} else {
				AffineTransform xform = getTransform();
				long left = leftSongPos;
				long right = rightSongPos;

				// The song position changes frequently, so only repaint the rect that
				// contains the notes that were/are playing
				if (isShowingNotesOn()) {
					for (NoteEvent ne : getEvents()) {
						if (ne.getEndMicros() < leftSongPos)
							continue;
						if (ne.getStartMicros() > rightSongPos)
							break;

						// This note event is or was playing
						if (ne.getStartMicros() < left)
							left = ne.getStartMicros();
						if (ne.getEndMicros() > right)
							right = ne.getEndMicros();
					}
				}

				// Transform to screen coordinates
				Point2D.Double pt = new Point2D.Double(left, 0);
				xform.transform(pt, pt);
				int x = (int) Math.floor(pt.x - noteOnOutlineWidthPix) - 2;
				pt.setLocation(right, 0);
				xform.transform(pt, pt);
				int width = (int) Math.ceil(pt.x + 2 * noteOnOutlineWidthPix) - x + 4;
				repaint(x, 0, width, getHeight());
			}
		} else {
			switch (evt.getProperty()) {
			case DRAG_POSITION:
			case IS_DRAGGING:
			case TEMPO:
			case POSITION:
				break;

			// These properties don't change often; just repaint the whole thing
			case IS_LOADED:
			case IS_RUNNING:
			case LENGTH:
			case SEQUENCE:
			case TRACK_ACTIVE:
			default:
				repaint();
				break;
			}

		}
	}

	private Rectangle2D.Double rectTmp = new Rectangle2D.Double();

	private void fillNote(Graphics2D g2, NoteEvent ne, int noteId, double minWidth, double height) {
		fillNote(g2, ne, noteId, minWidth, height, 0, 0);
	}

	@SuppressWarnings("unchecked")
	private void fillNote(Graphics2D g2, NoteEvent ne, int noteId, double minWidth, double height, double extraWidth,
			double extraHeight) {
		if (ne instanceof BentMidiNoteEvent) {
			BentMidiNoteEvent be = (BentMidiNoteEvent) ne;

			Set<Entry<Long, Integer>> bendSet = be.bends.entrySet();
			Object[] bends = bendSet.toArray();

			ITempoCache tempoCache = ne.getTempoCache();
			for (int i = 0; i < bends.length; i++) {
				Entry<Long, Integer> bend1 = (Entry<Long, Integer>) bends[i];

				long bend1tick = bend1.getKey();
				int bend1bend = bend1.getValue();
				long bend2tick = Long.MIN_VALUE;
				if (i != bends.length - 1) {
					bend2tick = ((Entry<Long, Integer>) bends[i + 1]).getKey();
				} else {
					bend2tick = ne.getEndTick();
				}
				long startMicro = tempoCache.tickToMicros(bend1tick);
				double width = Math.max(minWidth, tempoCache.tickToMicros(bend2tick) - startMicro);
				double y = Util.clamp(noteId + bend1bend, MIN_RENDERED, MAX_RENDERED);
				rectTmp.setRect(startMicro - extraWidth, y - extraHeight, width + 2 * extraWidth,
						height + 2 * extraHeight);
				g2.fill(rectTmp);
			}
		} else {
			double width = Math.max(minWidth, ne.getLengthMicros());
			double y = Util.clamp(noteId, MIN_RENDERED, MAX_RENDERED);
			rectTmp.setRect(ne.getStartMicros() - extraWidth, y - extraHeight, width + 2 * extraWidth,
					height + 2 * extraHeight);
			g2.fill(rectTmp);
		}
	}

	private void fillNoteVelocity(Graphics2D g2, NoteEvent ne, Dynamics dynamics) {
		// int velocity = dynamics.midiVol;

		AffineTransform xform = getTransform();

		double minWidth = NOTE_VELOCITY_MIN_WIDTH_PX / xform.getScaleX();
		double width = Math.max(minWidth, ne.getLengthMicros());

		double minHeight = Math.abs(NOTE_VELOCITY_MIN_HEIGHT_PX / xform.getScaleY());
		// double height = ((double) (velocity - Dynamics.MINIMUM.midiVol) /
		// Dynamics.MAXIMUM.midiVol)
		double height = ((double) (dynamics.ordinal()) / (double) Dynamics.MAXIMUM.ordinal())
				* (MAX_RENDERED - MIN_RENDERED - minHeight) + minHeight;

		rectTmp.setRect(ne.getStartMicros(), MIN_RENDERED, width, height);
		g2.fill(rectTmp);
	}

	private BitSet notesOn = null;
	private BitSet notesBad = null;
	private BitSet notesBad0 = null;
	private BitSet notesBad1 = null;
	private BitSet notesBad2 = null;
	private BitSet notesBad3 = null;
	
	// For histogram panel - "extra bad" "notes" (> 64 polyphony)
	private BitSet notesExtraBad = null;

	private static float[] hsb;

	private static final int SAT = 1;
	private static final int BRT = 2;

	private static Color makeDynamicColor(Color base, Dynamics dyn, float weight) {
		hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), hsb);
		// Adjust the brightness based on the volume dynamics
		hsb[BRT] *= (1 - weight) + weight * dyn.midiVol / 128.0f;

		// If the brightness is nearing max, also reduce the saturation to enhance the
		// effect
		if (hsb[BRT] > 0.9f) {
			hsb[SAT] = Math.max(0.0f, hsb[SAT] - (hsb[BRT] - 0.9f));
			hsb[BRT] = Math.min(1.0f, hsb[BRT]);
		}

		return new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]));
	}

	private Color getNoteColorEx(NoteEvent ne, Color baseColor, Color[] cachedColorByDynamics) {
		Dynamics dyn = Dynamics.fromMidiVelocity(ne.velocity + deltaVolume);
		if (cachedColorByDynamics[dyn.ordinal()] == null) {
			cachedColorByDynamics[dyn.ordinal()] = makeDynamicColor(baseColor, dyn, 0.25f);
		}
		return cachedColorByDynamics[dyn.ordinal()];
	}

	Color getNoteColor(NoteEvent ne) {
		return getNoteColorEx(ne, noteColor.get(), noteColorByDynamics);
	}

	Color getNoteVColor(NoteEvent ne) {
		return getNoteColorEx(ne, noteColor.get(), noteColorByDynamics);
	}

	Color getBadNoteColor(NoteEvent ne) {
		return getNoteColorEx(ne, badNoteColor.get(), badNoteColorByDynamics);
	}
	
	// Used only for histogram
	Color getExtraBadNoteColor(NoteEvent ne) {
		return extraBadNoteColor.get();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;

		Object hintAntialiasSav = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		AffineTransform xformSav = g2.getTransform();

		AffineTransform xform = getTransform();
		double minLength = NOTE_WIDTH_PX / xform.getScaleX();
		double height = Math.abs(NOTE_HEIGHT_PX / xform.getScaleY());

		long clipPosStart = Long.MIN_VALUE;
		long clipPosEnd = Long.MAX_VALUE;

		Rectangle clipRect = g2.getClipBounds();
		if (clipRect != null) {
			// Add +/- 2 to account for antialiasing (1 would probably be enough)
			Point2D.Double leftPoint = new Point2D.Double(clipRect.getMinX() - 2, clipRect.getMinY());
			Point2D.Double rightPoint = new Point2D.Double(clipRect.getMaxX() + 2, clipRect.getMaxY());
			try {
				xform.inverseTransform(leftPoint, leftPoint);
				xform.inverseTransform(rightPoint, rightPoint);

				clipPosStart = (long) Math.floor(Math.min(leftPoint.x, rightPoint.x));
				clipPosEnd = (long) Math.ceil(Math.max(leftPoint.x, rightPoint.x));
			} catch (NoninvertibleTransformException e) {
				e.printStackTrace();
			}
		}

		g2.transform(xform);

		if (sequenceInfo != null) {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

			double lineWidth = Math.abs(1.0 / xform.getScaleX());

			SequenceDataCache data = sequenceInfo.getDataCache();
			long barLengthTicks = data.getBarLengthTicks();

			long firstBarTick = (data.microsToTick(clipPosStart) / barLengthTicks) * barLengthTicks;
			long lastBarTick = (data.microsToTick(clipPosEnd) / barLengthTicks) * barLengthTicks;

			boolean[] barArray = getSectionsModified();
			long barCount = data.microsToTick(clipPosStart) / barLengthTicks - 1;
			long barMicros = clipPosStart;
			boolean barEdited = false;
			boolean barBothEdited = false;
			for (long barTick = firstBarTick; barTick <= lastBarTick + barLengthTicks; barTick += barLengthTicks) {
				barEdited = false;
				long barTempMicros = data.tickToMicros(barTick);
				if (barArray != null && barCount < barArray.length && barCount > -1) {
					if (barArray[(int) barCount]) {
						rectTmp.setRect(barMicros + lineWidth, MIN_RENDERED - 1, barTempMicros - barMicros - lineWidth,
								MAX_RENDERED - MIN_RENDERED + 2);
						g2.setColor(ColorTable.BAR_EDITED.get());
						g2.fill(rectTmp);
						barEdited = true;
					}
				}
				barCount++;
				barMicros = barTempMicros;
				rectTmp.setRect(barMicros, MIN_RENDERED - 1, lineWidth, MAX_RENDERED - MIN_RENDERED + 2);
				barBothEdited = false;
				if (barEdited && barArray != null && barCount < barArray.length && barCount > -1) {
					if (barArray[(int) barCount]) {
						barBothEdited = true;
					}
				}
				g2.setColor(barBothEdited ? ColorTable.BAR_LINE_EDITED.get() : ColorTable.BAR_LINE.get());
				g2.fill(rectTmp);
			}
		}

		if (octaveLinesVisible) {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

			int minBarOctave = MIN_RENDERED / 12 + 1;
			int maxBarOctave = MAX_RENDERED / 12 - 1;
			double lineHeight = Math.abs(1 / xform.getScaleY());
			g2.setColor(ColorTable.OCTAVE_LINE.get());
			for (int barOctave = minBarOctave; barOctave <= maxBarOctave; barOctave++) {
				rectTmp.setRect(0, barOctave * 12, sequencer.getLength(), lineHeight);
				g2.fill(rectTmp);
			}
		} else if (histogramThresholdLinesVisible) {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

			int minBarOctave = MIN_RENDERED / 12 + 1;
			int maxBarOctave = MAX_RENDERED / 12 - 1;
			double lineHeight = Math.abs(1 / xform.getScaleY());
			g2.setColor(ColorTable.OCTAVE_LINE.get());

			double y1 = Util.clamp(HistogramPanel.ORANGE_NOTES, MIN_RENDERED, MAX_RENDERED);
			double y2 = Util.clamp(HistogramPanel.RED_NOTES, MIN_RENDERED, MAX_RENDERED);
			rectTmp.setRect(0, y1, sequencer.getLength(), lineHeight);
			g2.fill(rectTmp);
			rectTmp.setRect(0, y2, sequencer.getLength(), lineHeight);
			g2.fill(rectTmp);
		}

		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		boolean showNotesOn = isShowingNotesOn() && songPos >= 0;
		long minSongPos = songPos;

		if (showNotesOn) {
			// Highlight all notes that are on, or were on since we last painted (up to
			// 100ms ago)
			if (lastPaintedSongPos >= 0 && lastPaintedSongPos < songPos) {
				minSongPos = Math.max(lastPaintedSongPos, songPos - 4 * SequencerWrapper.UPDATE_FREQUENCY_MICROS);
			}
		}

		lastPaintedMinSongPos = minSongPos;
		lastPaintedSongPos = songPos;

		List<NoteEvent> noteEvents = getEvents();

		if (notesOn != null)
			notesOn.clear();
		if (notesBad != null)
			notesBad.clear();
		if (notesBad0 != null)
			notesBad0.clear();
		if (notesBad1 != null)
			notesBad1.clear();
		if (notesBad2 != null)
			notesBad2.clear();
		if (notesBad3 != null)
			notesBad3.clear();
		if (notesExtraBad != null)
			notesExtraBad.clear();

		if (!isShowingNoteVelocity()) {
			// Normal rendering

			// Paint the playable notes and keep track of the currently sounding and
			// unplayable notes
			g2.setColor(noteColor.get());
			for (int i = 0; i < noteEvents.size(); i++) {
				NoteEvent ne = noteEvents.get(i);

				// Don't bother drawing the note if it's clipped
				if ((ne.isZeroDuration() && !sequenceInfo.keep) || ne.getEndMicros() < clipPosStart || ne.getStartMicros() > clipPosEnd)
					continue;

				if (isNoteVisible(ne) && audibleNote(ne)) {

					int noteId = transposeNote(ne.note.id, ne.getStartTick());

					if (showNotesOn && songPos >= ne.getStartMicros() && minSongPos <= ne.getEndMicros()
							&& sequencer.isNoteActive(ne.note.id)) {
						if (notesOn == null)
							notesOn = new BitSet(noteEvents.size());
						notesOn.set(i);
					} else if (isNoteExtraBad(ne, 0)) {
						if (notesExtraBad == null)
							notesExtraBad = new BitSet(noteEvents.size());
						notesExtraBad.set(i);
					} else if (!isNotePlayable(ne, 0)) {
						if (notesBad == null)
							notesBad = new BitSet(noteEvents.size());
						notesBad.set(i);
					} else {
						g2.setColor(getNoteColor(ne));
						fillNote(g2, ne, noteId, minLength, height);
					}
					for (int k = 0; k < 4; k++) {
						if (!getSectionDoubling(ne.getStartTick())[k]) {
							continue;
						}
						int addition = 12;
						if (k == 0) {
							addition = -24;
						} else if (k == 1) {
							addition = -12;
						} else if (k == 3) {
							addition = 24;
						}

						int noteIdDouble = transposeNote(ne.note.id + addition, ne.getStartTick());
						
						if (isOutOfLimit(noteIdDouble, ne.getStartTick())) {
							continue;
						}

						if (showNotesOn && songPos >= ne.getStartMicros() && minSongPos <= ne.getEndMicros()
								&& sequencer.isNoteActive(ne.note.id)) {
							//
						} else if (!isNotePlayable(ne, addition)) {
							BitSet notesBadDouble;

							if (k == 0) {
								if (notesBad0 == null)
									notesBad0 = new BitSet(noteEvents.size());
								notesBadDouble = notesBad0;
							} else if (k == 1) {
								if (notesBad1 == null)
									notesBad1 = new BitSet(noteEvents.size());
								notesBadDouble = notesBad1;
							} else if (k == 2) {
								if (notesBad2 == null)
									notesBad2 = new BitSet(noteEvents.size());
								notesBadDouble = notesBad2;
							} else {
								if (notesBad3 == null)
									notesBad3 = new BitSet(noteEvents.size());
								notesBadDouble = notesBad3;
							}

							notesBadDouble.set(i);
						} else {
							NoteEvent nd = new NoteEvent(Note.fromId(noteIdDouble), ne.velocity, ne.getStartTick(),
									ne.getEndTick(), ne.getTempoCache());

							g2.setColor(getNoteColor(nd));
							fillNote(g2, nd, noteIdDouble, minLength, height);
						}
					}
				}
			}

			if (notesBad != null) {
				for (int i = notesBad.nextSetBit(0); i >= 0; i = notesBad.nextSetBit(i + 1)) {
					NoteEvent ne = noteEvents.get(i);
					g2.setColor(getBadNoteColor(ne));
					int noteId = transposeNote(ne.note.id, ne.getStartTick());
					fillNote(g2, ne, noteId, minLength, height);
				}
			}
			if (notesExtraBad != null) {
				for (int i = notesExtraBad.nextSetBit(0); i >= 0; i = notesExtraBad.nextSetBit(i + 1)) {
					NoteEvent ne = noteEvents.get(i);
					g2.setColor(getExtraBadNoteColor(ne));
					int noteId = transposeNote(ne.note.id, ne.getStartTick());
					fillNote(g2, ne, noteId, minLength, height);
				}
			}
			if (notesBad0 != null) {
				for (int i = notesBad0.nextSetBit(0); i >= 0; i = notesBad0.nextSetBit(i + 1)) {
					NoteEvent ne = noteEvents.get(i);
					NoteEvent nd = new NoteEvent(Note.fromId(ne.note.id - 24), ne.velocity, ne.getStartTick(),
							ne.getEndTick(), ne.getTempoCache());
					g2.setColor(getBadNoteColor(nd));
					int noteId = transposeNote(nd.note.id, nd.getStartTick());
					fillNote(g2, nd, noteId, minLength, height);
				}
			}
			if (notesBad1 != null) {
				for (int i = notesBad1.nextSetBit(0); i >= 0; i = notesBad1.nextSetBit(i + 1)) {
					NoteEvent ne = noteEvents.get(i);
					NoteEvent nd = new NoteEvent(Note.fromId(ne.note.id - 12), ne.velocity, ne.getStartTick(),
							ne.getEndTick(), ne.getTempoCache());
					g2.setColor(getBadNoteColor(nd));
					int noteId = transposeNote(nd.note.id, nd.getStartTick());
					fillNote(g2, nd, noteId, minLength, height);
				}
			}
			if (notesBad2 != null) {
				for (int i = notesBad2.nextSetBit(0); i >= 0; i = notesBad2.nextSetBit(i + 1)) {
					NoteEvent ne = noteEvents.get(i);
					NoteEvent nd = new NoteEvent(Note.fromId(ne.note.id + 12), ne.velocity, ne.getStartTick(),
							ne.getEndTick(), ne.getTempoCache());
					g2.setColor(getBadNoteColor(nd));
					int noteId = transposeNote(nd.note.id, nd.getStartTick());
					fillNote(g2, nd, noteId, minLength, height);
				}
			}
			if (notesBad3 != null) {
				for (int i = notesBad3.nextSetBit(0); i >= 0; i = notesBad3.nextSetBit(i + 1)) {
					NoteEvent ne = noteEvents.get(i);
					NoteEvent nd = new NoteEvent(Note.fromId(ne.note.id + 24), ne.velocity, ne.getStartTick(),
							ne.getEndTick(), ne.getTempoCache());
					g2.setColor(getBadNoteColor(nd));
					int noteId = transposeNote(nd.note.id, nd.getStartTick());
					fillNote(g2, nd, noteId, minLength, height);
				}
			}

			if (notesOn != null) {
				double noteOnOutlineWidthX = noteOnOutlineWidthPix / xform.getScaleX();
				double noteOnOutlineWidthY = Math.abs(noteOnOutlineWidthPix / xform.getScaleY());
				double noteOnExtraHeightY = Math.abs(noteOnExtraHeightPix / xform.getScaleY());

				g2.setColor(noteOnBorder.get());
				for (int i = notesOn.nextSetBit(0); i >= 0; i = notesOn.nextSetBit(i + 1)) {
					NoteEvent ne = noteEvents.get(i);
					int noteId = transposeNote(ne.note.id, ne.getStartTick());

					fillNote(g2, noteEvents.get(i), noteId, minLength, height, noteOnOutlineWidthX,
							noteOnExtraHeightY + noteOnOutlineWidthY);

					for (int k = 0; k < 4; k++) {
						if (!getSectionDoubling(ne.getStartTick())[k]) {
							continue;
						}
						int addition = 12;
						if (k == 0) {
							addition = -24;
						} else if (k == 1) {
							addition = -12;
						} else if (k == 3) {
							addition = 24;
						}

						int noteIdDouble = transposeNote(ne.note.id + addition, ne.getStartTick());

						fillNote(g2, ne, noteIdDouble, minLength, height, noteOnOutlineWidthX,
								noteOnExtraHeightY + noteOnOutlineWidthY);
					}
				}

				g2.setColor(noteOnColor.get());
				for (int i = notesOn.nextSetBit(0); i >= 0; i = notesOn.nextSetBit(i + 1)) {
					NoteEvent ne = noteEvents.get(i);
					int noteId = transposeNote(ne.note.id, ne.getStartTick());

					fillNote(g2, noteEvents.get(i), noteId, minLength, height, 0, noteOnExtraHeightY);

					for (int k = 0; k < 4; k++) {
						if (!getSectionDoubling(ne.getStartTick())[k]) {
							continue;
						}
						int addition = 12;
						if (k == 0) {
							addition = -24;
						} else if (k == 1) {
							addition = -12;
						} else if (k == 3) {
							addition = 24;
						}

						int noteIdDouble = transposeNote(ne.note.id + addition, ne.getStartTick());

						fillNote(g2, ne, noteIdDouble, minLength, height, 0, noteOnExtraHeightY);
					}
				}
			}
		} else {
			// Render the volume of each note instead of its note value
			Dynamics[] dynamicsValues = Dynamics.values();

			// Render from highest dynamics to lowest.
			// Out of range notes are rendered with (d == dynamicsValues.length) and (d ==
			// -1)
			for (int d = dynamicsValues.length; d >= -1; --d) {
				for (NoteEvent ne : noteEvents) {
					if ((ne.isZeroDuration() && !sequenceInfo.keep) || ne.getEndMicros() < clipPosStart || ne.getStartMicros() > clipPosEnd || !audibleNote(ne))
						continue;

					int[] sv = getSectionVelocity(ne);
					int velocity = getSourceNoteVelocity(ne);
					velocity = (int) ((velocity + deltaVolume + sv[0]) * 0.01f * (float) sv[1] * 0.01f * (float) sv[2]);

					Dynamics dynamicsRenderedInThisPass = null;
					if (d == dynamicsValues.length)
						dynamicsRenderedInThisPass = Dynamics.MAXIMUM;
					else if (d == -1)
						dynamicsRenderedInThisPass = Dynamics.MINIMUM;
					else
						dynamicsRenderedInThisPass = dynamicsValues[d];

					boolean isOutOfRange = (velocity < Dynamics.MINIMUM.midiVol)
							|| (velocity > Dynamics.MAXIMUM.midiVol);

					// Note that we're rendering the "above max" dynamics in the *second* pass
					// (the first is d == dynamicsValues.length). This lets us render those bad
					// notes on top and makes them more visible.
					if (d == dynamicsValues.length - 1) {
						// Only rendering notes where (velocity > Dynamics.MAXIMUM.midiVol) in this pass
						if (!(velocity > Dynamics.MAXIMUM.midiVol))
							continue;
					} else if (d == -1) {
						// Only rendering notes where (velocity < Dynamics.MINIMUM.midiVol) in this pass
						if (!(velocity < Dynamics.MINIMUM.midiVol))
							continue;
					} else if (isOutOfRange || Dynamics.fromMidiVelocity(velocity) != dynamicsRenderedInThisPass) {
						// Only rendering notes that have the particular velocity in this pass
						continue;
					}

					if (isNoteVisible(ne)) {
						setColorAndFillVelocity(g2, showNotesOn, minSongPos, ne, dynamicsRenderedInThisPass,
								isOutOfRange);
					}
				}
			}
		}

		g2.setTransform(xformSav);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hintAntialiasSav);
	}

	boolean isOutOfLimit(int noteIdDouble, long startTick) {
		return false;
	}

	private void setColorAndFillVelocity(Graphics2D g2, boolean showNotesOn, long minSongPos, NoteEvent ne,
			Dynamics dynamicsRenderedInThisPass, boolean isOutOfRange) {
		if (showNotesOn && songPos >= ne.getStartMicros() && minSongPos <= ne.getEndMicros()
				&& sequencer.isNoteActive(ne.note.id)) {
			g2.setColor(noteOnColor.get());
			fillNoteVelocity(g2, ne, dynamicsRenderedInThisPass);
		} else if (isOutOfRange) {
			g2.setColor(badNoteColor.get());
			fillNoteVelocity(g2, ne, dynamicsRenderedInThisPass);
		} else {
			g2.setColor(getNoteVColor(ne));
			fillNoteVelocity(g2, ne, dynamicsRenderedInThisPass);
		}
	}

	private class MyMouseListener extends MouseAdapter {
		JPopupMenu barIndicator = new JPopupMenu();
		JLabel barLabel = new JLabel();
		private long positionFromEvent(MouseEvent e) {
			AffineTransform xform = getTransform();
			Point2D.Double pt = new Point2D.Double(e.getX(), e.getY());
			try {
				xform.inverseTransform(pt, pt);
				long ret = (long) pt.x;
				if (ret < 0)
					ret = 0;
				if (ret >= sequencer.getLength())
					ret = sequencer.getLength() - 1;
				return ret;
			} catch (NoninvertibleTransformException e1) {
				e1.printStackTrace();
				return 0;
			}
		}

		private boolean isDragCanceled(MouseEvent e) {
			if (sequenceInfo == null)
				return true;

			// Allow drag to continue anywhere within the scroll pane
			Component dragArea = SwingUtilities.getAncestorOfClass(JScrollPane.class, NoteGraph.this);
			if (dragArea == null)
				dragArea = NoteGraph.this;

			Point pt = SwingUtilities.convertPoint(NoteGraph.this, e.getPoint(), dragArea);
			return (pt.x < -32 || pt.x > dragArea.getWidth() + 32) || (pt.y < -32 || pt.y > dragArea.getHeight() + 32);
		}

		@Override
		public void mousePressed(MouseEvent e) {
			if (e.getButton() == MouseEvent.BUTTON1 && sequenceInfo != null) {
				sequencer.setDragging(true);
				sequencer.setDragPosition(positionFromEvent(e));
				barLabel = new JLabel("Bar " + BarNumberLabel.getBarString(sequencer, sequenceInfo.getDataCache()));
				barLabel.setFocusable(false);
				barLabel.setBorder(BorderFactory.createEmptyBorder(5, 25, 5, 5));
		        barIndicator = new JPopupMenu();
		        barIndicator.add(barLabel);
		        barIndicator.show(NoteGraph.this, e.getX(), e.getY());
		        barIndicator.setVisible(true);
			}
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			if ((e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0) {
				if (!isDragCanceled(e)) {
					sequencer.setDragging(true);
					sequencer.setDragPosition(positionFromEvent(e));
					barLabel.setText("Bar " + BarNumberLabel.getBarString(sequencer, sequenceInfo.getDataCache()));
					barIndicator.show(NoteGraph.this, e.getX(), e.getY());
				} else {
					sequencer.setDragging(false);
					barIndicator.setVisible(false);
				}
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			if (e.getButton() == MouseEvent.BUTTON1) {
				sequencer.setDragging(false);
				barIndicator.setVisible(false);
				if (!isDragCanceled(e)) {
					sequencer.setPosition(positionFromEvent(e));
				}
			}
		}
	}

	protected int getSourceNoteVelocity(NoteEvent note) {
		return note.velocity;
	}

	protected int[] getSectionVelocity(NoteEvent note) {
		int[] empty = new int[3];
		empty[0] = 0;//   volume offset
		empty[1] = 100;// volume factor in percent (section-editor)
		empty[2] = 100;// volume factor in percent (tune-editor)
		return empty;
	}

	protected Boolean[] getSectionDoubling(long tick) {
		Boolean[] empty = new Boolean[4];
		empty[0] = false;
		empty[1] = false;
		empty[2] = false;
		empty[3] = false;
		return empty;
	}
}