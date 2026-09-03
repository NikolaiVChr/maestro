package com.digero.maestro.view;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.digero.common.abc.Dynamics;
import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.common.util.Util;
import com.digero.common.view.BarNumberLabel;
import com.digero.common.view.ColorTable;
import com.digero.common.view.UIText;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.midi.TrackInfo;

public abstract class NoteGraph extends JPanel implements Listener<SequencerEvent>, IDiscardable {
	protected static final Logger log = Logger.getLogger("view.noteGraph");
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
	private static final long LONG_NOTE_THRESHOLD_MICROS = 10_000_000L; // 10 seconds

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
		setMaximumSize(new Dimension(1000000, 2000));

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
		if (staticNotesImage != null) {
			staticNotesImage.flush();
			staticNotesImage = null;
		}
		if (bitmapRebuildTimer != null) {
			bitmapRebuildTimer.stop();
			bitmapRebuildTimer = null;
		}
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

    public int getLowThreshold() {
        return 0;
    }

    public int getHighThreshold() {
        return 0;
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

	@Override
	public void setBackground(Color bg) {
		super.setBackground(bg);
		invalidateBitmapCache();
	}

	public void setOctaveLinesVisible(boolean octaveLinesVisible) {
		if (this.octaveLinesVisible != octaveLinesVisible) {
			this.octaveLinesVisible = octaveLinesVisible;
			invalidateBitmapCache();
			repaint();
		}
	}

	public boolean isOctaveLinesVisible() {
		return octaveLinesVisible;
	}

	public void setHistogramThresholdLinesVisible(boolean histogramThresholdLinesVisible) {
		if (this.histogramThresholdLinesVisible != histogramThresholdLinesVisible) {
			this.histogramThresholdLinesVisible = histogramThresholdLinesVisible;
			invalidateBitmapCache();
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
			invalidateNoteCache();
			repaint();
		}
	}

	public final void setBadNoteColor(ColorTable badNoteColor) {
		if (this.badNoteColor != badNoteColor) {
			this.badNoteColor = badNoteColor;
			Arrays.fill(badNoteColorByDynamics, null);
			invalidateNoteCache();
			repaint();
		}
	}

	public final void setExtraBadNoteColor(ColorTable extraBadNoteColor) {
		if (this.extraBadNoteColor != extraBadNoteColor) {
			this.extraBadNoteColor = extraBadNoteColor;
			invalidateNoteCache();
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
			invalidateNoteCache();
			repaint();
		}
	}

	public int getDeltaVolume() {
		return deltaVolume;
	}

	public void setTrackInfo(TrackInfo trackInfo) {
		this.trackInfo = trackInfo;
		invalidateNoteCache();
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
	// Width of note leading edge in song-time coords, recomputed whenever the transform is rebuilt.
	private double leadingEdgeWidthSong = 0.0;
	// Note edge shade amount: positive mixes toward white, negative toward black.
	private static final float EDGE_SHADE_AMOUNT = -0.3f;
	private Color lastEdgeBaseColor = null;
	private Color lastEdgeShadedColor = null;

	private static Color shadeColor(Color c) {
		if (EDGE_SHADE_AMOUNT >= 0) {
			int r = c.getRed()   + Math.round((255 - c.getRed())   * EDGE_SHADE_AMOUNT);
			int g = c.getGreen() + Math.round((255 - c.getGreen()) * EDGE_SHADE_AMOUNT);
			int b = c.getBlue()  + Math.round((255 - c.getBlue())  * EDGE_SHADE_AMOUNT);
			return new Color(r, g, b);
		} else {
			float k = 1 + EDGE_SHADE_AMOUNT;
			return new Color(Math.round(c.getRed() * k), Math.round(c.getGreen() * k), Math.round(c.getBlue() * k));
		}
	}

	protected final void invalidateTransform() {
		noteToScreenXForm = null;
		leadingEdgeWidthSong = 0.0;
		invalidateBitmapCache();
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
				// The song doesn't seem to be loaded yet, we don't cache the transform
				String tracker = trackInfo==null?"No track: ":trackInfo.getTrackNumber()+" ("+trackInfo.getName()+"): ";
				log.warning(tracker+"NoteGraph transform could not be calculated. noteW=" + noteW+" scrnW="+scrnW+" scrnH="+scrnH+" class="+getClass().getName());
				invalidateTransform();
				return new AffineTransform();
			} else {
				scrnXForm = new AffineTransform(scrnW, 0, 0, scrnH, scrnX, scrnY);
				try {
					AffineTransform noteXForm = new AffineTransform(noteW, 0, 0, noteH, noteX, noteY);
					noteXForm.invert();
					scrnXForm.concatenate(noteXForm);
				} catch (NoninvertibleTransformException e) {
					log.log(Level.SEVERE, "Notegraph transform could not be inverted", e);
					scrnXForm.setToIdentity();
				}
			}

			noteToScreenXForm = scrnXForm;
			double sx = scrnXForm.getScaleX();
			leadingEdgeWidthSong = (sx != 0) ? 1.5 / sx : 0.0;
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
		if (getWidth() <= 0 || getHeight() <= 0) {
			// DissonancePanel will get in here when not shown.
			return;
		}

		if (evt.getProperty() == SequencerProperty.LENGTH) {
			invalidateTransform();
		}

		if (evt.getProperty().isInMask(SequencerProperty.THUMB_POSITION_MASK)) {
			repositionIndicator();
		}

		if (evt.getProperty() == SequencerProperty.IS_DRAGGING) {
			indicatorLine.setBackground(
					sequencer.isDragging() ? ColorTable.INDICATOR_DRAGGING.get() : ColorTable.INDICATOR.get());
		}

		// Repaint the parts that need it
		if (evt.getProperty() == SequencerProperty.POSITION) {
			final long currentSongPos = sequencer.getDelayedPosition();
			long delta = currentSongPos - songPos;
			boolean discontinuous = songPos < 0L
					|| delta < 0L                                             // jumped backward
					|| delta > 4L * SequencerWrapper.UPDATE_FREQUENCY_MICROS; // jumped forward more than a few frames

			if (!sequencer.isDragging() && discontinuous) {
				songPos = currentSongPos;
				lastPaintedMinSongPos = -1;   // reset so next paint doesn't trust stale span
				lastPaintedSongPos = -1;
				repaint();                    // full repaint: erases all old highlights, draws all new
				//if we dont do this then all highlighted notes that start outside new window will stay highlighted
				//until next repaint or song playback window reaches them again.
				return;
			}

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
				// contains the notes that were/are playing.
				// Use cachedEvents (already allocated) instead of getEvents() to avoid
				// creating a new ArrayList every frame.
				if (isShowingNotesOn()) {
					int startIdx = binarySearchStartMicrosEvents(cachedEvents, leftSongPos - LONG_NOTE_THRESHOLD_MICROS);
					for (int i = startIdx; i < cachedEvents.size(); i++) {
						NoteEvent ne = cachedEvents.get(i);
						if (ne.getStartMicros() > rightSongPos)
							break;
						if (ne.getEndMicros() < leftSongPos)
							continue;

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
			case SEQUENCE:
				invalidateNoteCache();
				invalidateTransform();
				repaint();
				break;
			case IS_RUNNING:
			case LENGTH:
			case TRACK_ACTIVE:
			case SONG_ENDED:
				repaint();
				break;
			default:
				repaint();
				break;
			}

		}
	}

	private Rectangle2D.Double rectTmp = new Rectangle2D.Double();

	/**
	 * Pre-rendered image of all static content: bar lines, octave lines, and every note
	 * (no note-on highlighting).  Null when dirty.  Rebuilt lazily in paintComponent.
	 * Only used in normal (non-velocity) mode; pixel-space so must be discarded on resize.
	 */
	private BufferedImage staticNotesImage = null;
	private boolean bitmapDirty = false;

	// Debounce interval before a bitmap rebuild is triggered after the last invalidation
	private static final int BITMAP_DEBOUNCE_MS = 150;
	// Total budget for bitmap caches across all note graphs (4 bytes / pixel)
	private static final long TOTAL_BITMAP_BUDGET_PIXELS = 256 * 1024 * 1024 / 4;
	// Timer to debounce rebuilding the bitmap when the bitmap cache goes dirty
	private Timer bitmapRebuildTimer = null;

	private void invalidateBitmapCache() {
		bitmapDirty = true;
		if (bitmapRebuildTimer != null) {
			bitmapRebuildTimer.stop();
		}
		final Timer[] self = new Timer[1];               // holder so the lambda can see its own timer
		self[0] = new Timer(BITMAP_DEBOUNCE_MS, e -> {
			// When timer fires, it queues this lambda to run on the EDT,
			// its not ran instantly.
			// Inbetween the timer firing and lambda running, bitmapRebuildTimer may have been replaced
			// so check if we're still the current timer before nulling the timer.
			if (bitmapRebuildTimer == self[0]) {         // only clear if a newer one hasn't replaced us
				bitmapRebuildTimer = null;
			}
			repaint();
		});
		bitmapRebuildTimer = self[0];
		bitmapRebuildTimer.setRepeats(false);
		bitmapRebuildTimer.start();
	}

	private boolean noteCacheDirty = true;
	private int cacheRebuildCount = 0; // For debug
	// Note cache lists
	private List<CachedNote> noteCache = new ArrayList<>();
	private List<CachedNote> cacheBad = new ArrayList<>();
	private List<CachedNote> cacheExtraBad = new ArrayList<>();
	private List<CachedNote> cacheWithDoublings = new ArrayList<>();
	// Subset of noteCache where note duration exceeds LONG_NOTE_THRESHOLD_MICROS; checked separately after binary search.
	private List<CachedNote> cacheLongHeld = new ArrayList<>();
	// The event list from the last rebuildNoteCache(); reused by onEvent(POSITION) to avoid allocating a new list 20×/second.
	private List<NoteEvent> cachedEvents = Collections.emptyList();

	/**
	 * Pre-computed rendering data for one visible+audible NoteEvent (primary note)
	 * and all of its active section doublings.  Rebuilt once per cache rebuild cycle;
	 * valid until {@link #invalidateNoteCache()} is called.
	 */
	private static final class CachedNote {
		final NoteEvent source;           // original event (for fillNote + timing checks)
		final long startMicros;           // = source.getStartMicros() at cache build time
		final long endMicros;             // = source.getEndMicros()   at cache build time
		final int noteId;                 // transposed primary note ID
		final Color color;                // pre-computed drawing color for this entry
		final boolean bad;                // primary is out of playable range
		final boolean extraBad;           // primary is extra-bad (e.g., over polyphony limit)
		// Doubling arrays – null when no active doublings exist for this note.
		// Includes ALL doublings where getSectionDoubling[k] == true, including out-of-limit ones
		// (out-of-limit doublings are skipped in normal drawing but still drawn in note-on highlights,
		//  matching the original rendering behaviour).
		final int[]       doubIds;        // transposed doubling note IDs
		final boolean[]   doubBad;        // true if !isNotePlayable for this doubling (ignored when doubOutOfLimit)
		final boolean[]   doubOutOfLimit; // true if isOutOfLimit → skip in normal drawing passes
		final Color[]     doubColors;     // null for out-of-limit entries; else pre-computed color
		final NoteEvent[] doubSrc;        // null for out-of-limit; synthetic NoteEvent for normal drawing
		                                  //   (suppresses bend rendering on doubled notes, matching original behaviour)

		CachedNote(NoteEvent source, int noteId, Color color, boolean bad, boolean extraBad,
				   int[] doubIds, boolean[] doubBad, boolean[] doubOutOfLimit,
				   Color[] doubColors, NoteEvent[] doubSrc) {
			this.source        = source;
			this.startMicros   = source.getStartMicros();
			this.endMicros     = source.getEndMicros();
			this.noteId        = noteId;
			this.color         = color;
			this.bad           = bad;
			this.extraBad      = extraBad;
			this.doubIds       = doubIds;
			this.doubBad       = doubBad;
			this.doubOutOfLimit = doubOutOfLimit;
			this.doubColors    = doubColors;
			this.doubSrc       = doubSrc;
		}
	}

	public void invalidateNoteCache() {
		// Invalidate both caches
		noteCacheDirty = true;
		invalidateBitmapCache();
	}

	/**
	 * Rebuilds the note render cache from the current events list.
	 * All virtual methods (transposeNote, isNotePlayable, getSectionDoubling, getNoteColor, …)
	 * are called here once per note, rather than on every paint frame.
	 */
	private void rebuildNoteCache() {
		cacheRebuildCount++;

		List<NoteEvent> events = getEvents();
		cachedEvents = events;
		noteCache = new ArrayList<>(events.size());
		cacheBad = new ArrayList<>();
		cacheExtraBad = new ArrayList<>();
		cacheWithDoublings = new ArrayList<>();
		cacheLongHeld = new ArrayList<>();

		// Temporary lists for doubling data (reused across iterations to reduce allocation)
		List<Integer>   tmpIds    = new ArrayList<>(4);
		List<Boolean>   tmpBad    = new ArrayList<>(4);
		List<Boolean>   tmpOol    = new ArrayList<>(4);
		List<Color>     tmpColors = new ArrayList<>(4);
		List<NoteEvent> tmpSrc    = new ArrayList<>(4);

		for (NoteEvent ne : events) {
			if (!isNoteVisible(ne) || !audibleNote(ne)) continue;

			int noteId    = transposeNote(ne.note.id, ne.getStartTick());
			boolean exBad = isNoteExtraBad(ne, 0);
			boolean bad   = !exBad && !isNotePlayable(ne, 0);
			Color color;
			if      (exBad) color = getExtraBadNoteColor(ne);
			else if (bad)   color = getBadNoteColor(ne);
			else            color = getNoteColor(ne);

			Boolean[] sectDoubling = getSectionDoubling(ne.getStartTick());
			boolean isBent = ne instanceof BentMidiNoteEvent;

			tmpIds.clear(); tmpBad.clear(); tmpOol.clear(); tmpColors.clear(); tmpSrc.clear();
			for (int k = 0; k < 4; k++) {
				if (!Boolean.TRUE.equals(sectDoubling[k])) continue;
				int addition = DOUBLING_ADDITIONS[k];
				int doubId   = transposeNote(ne.note.id + addition, ne.getStartTick());
				boolean ool  = isOutOfLimit(doubId, ne.getStartTick());
				tmpIds.add(doubId);
				tmpOol.add(ool);
				if (ool) {
					tmpBad.add(false);
					tmpColors.add(null);
					tmpSrc.add(null);
				} else {
					boolean dBad = !isNotePlayable(ne, addition);
					tmpBad.add(dBad);
					tmpColors.add(dBad ? getBadNoteColor(ne) : getNoteColor(ne));
					// Use a synthetic (non-bent) NoteEvent as draw source for doublings so that
					// bend segments are not rendered on doubled notes in normal mode, matching
					// the original behaviour where 'nd = new NoteEvent(...)' was passed to fillNote.
					tmpSrc.add(isBent
						? new NoteEvent(Note.fromId(doubId), ne.velocity, ne.getStartTick(), ne.getEndTick(), ne.getTempoCache())
						: ne);
				}
			}

			int[]       doubIds   = null;
			boolean[]   doubBad   = null;
			boolean[]   doubOol   = null;
			Color[]     doubColors = null;
			NoteEvent[] doubSrc   = null;
			if (!tmpIds.isEmpty()) {
				int n       = tmpIds.size();
				doubIds     = new int[n];
				doubBad     = new boolean[n];
				doubOol     = new boolean[n];
				doubColors  = new Color[n];
				doubSrc     = new NoteEvent[n];
				for (int i = 0; i < n; i++) {
					doubIds[i]    = tmpIds.get(i);
					doubBad[i]    = tmpBad.get(i);
					doubOol[i]    = tmpOol.get(i);
					doubColors[i] = tmpColors.get(i);
					doubSrc[i]    = tmpSrc.get(i);
				}
			}

			CachedNote cn = new CachedNote(ne, noteId, color, bad, exBad,
					doubIds, doubBad, doubOol, doubColors, doubSrc);
			noteCache.add(cn);
			if (bad)            cacheBad.add(cn);
			if (exBad)          cacheExtraBad.add(cn);
			if (doubIds != null) cacheWithDoublings.add(cn);
			if (cn.endMicros - cn.startMicros > LONG_NOTE_THRESHOLD_MICROS) cacheLongHeld.add(cn);
		}

		noteCacheDirty = false;
	}

	/** Returns the index of the first element in {@code list} whose {@code startMicros >= targetMicros}. */
	private static int binarySearchStartMicros(List<CachedNote> list, long targetMicros) {
		int lo = 0, hi = list.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (list.get(mid).startMicros < targetMicros) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

	/** Returns the index of the first element in {@code list} whose {@code getStartMicros() >= targetMicros}. */
	private static int binarySearchStartMicrosEvents(List<NoteEvent> list, long targetMicros) {
		int lo = 0, hi = list.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (list.get(mid).getStartMicros() < targetMicros) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

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
            if (isBars()) {
                rectTmp.setRect(ne.getStartMicros() - extraWidth, MIN_RENDERED, width + 2 * extraWidth, y-MIN_RENDERED);
                g2.fill(rectTmp);
            } else {
                rectTmp.setRect(ne.getStartMicros() - extraWidth, y - extraHeight, width + 2 * extraWidth,
                        height + 2 * extraHeight);
                g2.fill(rectTmp);
                // Draw a darker leading edge so back-to-back same-pitch notes are distinguishable.
                if (extraWidth == 0 && extraHeight == 0 && leadingEdgeWidthSong > 0 && leadingEdgeWidthSong < width) {
                    Color baseColor = g2.getColor();
                    if (baseColor != lastEdgeBaseColor) {
                        lastEdgeShadedColor = shadeColor(baseColor);
                        lastEdgeBaseColor = baseColor;
                    }
                    g2.setColor(lastEdgeShadedColor);
                    rectTmp.setRect(ne.getStartMicros(), y, leadingEdgeWidthSong, height);
                    g2.fill(rectTmp);
                    g2.setColor(baseColor);
                }
            }
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

	// Semitone additions for the 4 section-doubling offsets (k=0..3).
	private static final int[] DOUBLING_ADDITIONS = { -24, -12, 12, 24 };

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

	/**
	 * Renders bar lines, octave lines, and all notes into {@link #staticNotesImage}.
	 * Reuses the existing image when dimensions are unchanged to avoid reallocation.
	 * Returns false if the bitmap was not built (size guard or zero dimensions),
	 * in which case the caller should fall back to direct rendering.
	 */
	private boolean renderStaticNotesToImage(AffineTransform xform, double minLength, double height) {
		int w = getWidth();
		int h = getHeight();
		if (w <= 0 || h <= 0) return false;

		// Technically this calc should include histogram and tempo, but as a heuristic it's fine.
		int graphCount = (sequenceInfo != null) ? Math.max(1, sequenceInfo.getTrackCount()) : 1;
		long maxPixels = TOTAL_BITMAP_BUDGET_PIXELS / graphCount;
		if ((long) w * h > maxPixels) {
			staticNotesImage = null;
			return false;
		}

		// Only allocate when dimensions change; reuse the existing image otherwise.
		if (staticNotesImage == null || staticNotesImage.getWidth() != w || staticNotesImage.getHeight() != h) {
			GraphicsConfiguration gc = getGraphicsConfiguration();
			staticNotesImage = (gc != null)
					? gc.createCompatibleImage(w, h, Transparency.OPAQUE)
					: new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		}

		Graphics2D bg = staticNotesImage.createGraphics();
		try {
			bg.setColor(getBackground());
			bg.fillRect(0, 0, w, h);
			bg.transform(xform);

			paintBarLines(bg, xform, 0, sequencer.getLength());
			paintOctaveLines(bg, xform);

			bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			// Draw all notes unconditionally — showNotesOn=false means no notes are
			// deferred and paintNoteOnHighlights is a no-op at the end of the call.
			paintNotesNormal(bg, xform, minLength, height, Long.MIN_VALUE, Long.MAX_VALUE, false, 0);
		} finally {
			bg.dispose();
		}
		return true;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D g2 = (Graphics2D) g;

		Object hintAntialiasSav = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		AffineTransform xformSav = g2.getTransform();

		AffineTransform xform = getTransform();
		double minLength = NOTE_WIDTH_PX / xform.getScaleX();
		double height = Math.max(getMinNoteHeightPx(), Math.abs(NOTE_HEIGHT_PX / xform.getScaleY()));

		long[] clip = computeClipBounds(g2, xform);
		long clipPosStart = clip[0];
		long clipPosEnd = clip[1];

		boolean showNotesOn = isShowingNotesOn() && songPos >= 0;
		long minSongPos = songPos;

		if (showNotesOn) {
			// Highlight notes that are on, or were on since we last painted (up to 2 frames ago)
			if (lastPaintedSongPos >= 0 && lastPaintedSongPos < songPos) {
				minSongPos = Math.max(lastPaintedSongPos, songPos - 2 * SequencerWrapper.UPDATE_FREQUENCY_MICROS);
			}
		}

		lastPaintedMinSongPos = minSongPos;
		lastPaintedSongPos = songPos;

		if (!isShowingNoteVelocity()) {
			if (noteCacheDirty) rebuildNoteCache();

			if (bitmapRebuildTimer != null) {
				// Debounce timer pending (burst in progress) — render directly to avoid allocation churn.
				// The timer will null itself and queue one repaint once the burst settles.
				g2.transform(xform);
				paintBarLines(g2, xform, clipPosStart, clipPosEnd);
				paintOctaveLines(g2, xform);
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				paintNotesNormal(g2, xform, minLength, height, clipPosStart, clipPosEnd, showNotesOn, minSongPos);
			} else {
				// Burst has settled — use the cached bitmap for fast blitting.
				if (bitmapDirty || staticNotesImage == null
						|| staticNotesImage.getWidth()  != getWidth()
						|| staticNotesImage.getHeight() != getHeight()) {
					boolean built = renderStaticNotesToImage(xform, minLength, height);
					if (built) bitmapDirty = false;
				}

				if (staticNotesImage != null) {
					// Bitmap always matches current dimensions; blit without scaling.
					g2.drawImage(staticNotesImage, 0, 0, null);
				} else {
					// Size guard triggered — fall back to direct rendering.
					g2.transform(xform);
					paintBarLines(g2, xform, clipPosStart, clipPosEnd);
					paintOctaveLines(g2, xform);
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					paintNotesNormal(g2, xform, minLength, height, clipPosStart, clipPosEnd, showNotesOn, minSongPos);
				}

				if (staticNotesImage != null) {
					// Draw note-on highlights on top of the bitmap — only the few currently-playing notes.
					g2.transform(xform);
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

					List<CachedNote> notesOnList = null;
					Set<CachedNote> notesOnSeen = null;
					if (showNotesOn) {
						int startIdx = binarySearchStartMicros(noteCache, minSongPos - LONG_NOTE_THRESHOLD_MICROS);
						for (int i = startIdx; i < noteCache.size(); i++) {
							CachedNote cn = noteCache.get(i);
							if (cn.startMicros > clipPosEnd) break;
							if (cn.endMicros < clipPosStart) continue;
							if (songPos >= cn.startMicros && minSongPos <= cn.endMicros) {
								if (notesOnList == null) {
									notesOnList = new ArrayList<>();
									notesOnSeen = Collections.newSetFromMap(new IdentityHashMap<>());
								}
								if (notesOnSeen.add(cn)) {
									notesOnList.add(cn);
								}
							}
						}
						// Also check long-held notes that binary search may have skipped
						for (CachedNote cn : cacheLongHeld) {
							if (cn.startMicros > clipPosEnd) break;
							if (cn.endMicros < clipPosStart) continue;
							if (songPos >= cn.startMicros && minSongPos <= cn.endMicros) {
								if (notesOnList == null) {
									notesOnList = new ArrayList<>();
									notesOnSeen = Collections.newSetFromMap(new IdentityHashMap<>());
								}
								if (notesOnSeen.add(cn)) {
									notesOnList.add(cn);
								}
							}
						}
					}
					paintNoteOnHighlights(g2, xform, minLength, height, notesOnList);
				}
			}

		} else {
			// Velocity mode: direct rendering, no bitmap (only active while dragging the volume bar)
			g2.transform(xform);
			paintBarLines(g2, xform, clipPosStart, clipPosEnd);
			paintOctaveLines(g2, xform);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			paintNotesVelocity(g2, clipPosStart, clipPosEnd, showNotesOn, minSongPos, getEvents());
		}

		g2.setTransform(xformSav);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, hintAntialiasSav);
	}

	/**
	 * Converts the Graphics2D clip rectangle from screen space to song-coordinate space.
	 * Returns {clipPosStart, clipPosEnd} in microseconds, or {Long.MIN_VALUE, Long.MAX_VALUE} if unclipped.
	 */
	private long[] computeClipBounds(Graphics2D g2, AffineTransform xform) {
		long clipPosStart = Long.MIN_VALUE;
		long clipPosEnd = Long.MAX_VALUE;

		Rectangle clipRect = g2.getClipBounds();
		if (clipRect != null) {
			// Add +/- 2 to account for antialiasing (1 would probably be enough)
			Point2D.Double leftPoint = new Point2D.Double(Math.max(0, clipRect.getMinX() - 2), clipRect.getMinY());
			Point2D.Double rightPoint = new Point2D.Double(clipRect.getMaxX() + 2, clipRect.getMaxY());
			try {
				xform.inverseTransform(leftPoint, leftPoint);
				xform.inverseTransform(rightPoint, rightPoint);

				clipPosStart = (long) Math.floor(Math.min(leftPoint.x, rightPoint.x));
				clipPosEnd = (long) Math.ceil(Math.max(leftPoint.x, rightPoint.x));
			} catch (NoninvertibleTransformException e) {
				log.log(Level.SEVERE, "Notegraph transform could not be inverted (clipbounds)", e);
			}
		}
		//System.out.println(" clipPosStart="+Util.formatDuration(clipPosStart)+" clipPosEnd="+Util.formatDuration(clipPosEnd));

		return new long[]{ clipPosStart, clipPosEnd };
	}

	/** Draws bar lines, section backgrounds, and first/last bar silencing regions. */
	private void paintBarLines(Graphics2D g2, AffineTransform xform, long clipPosStart, long clipPosEnd) {
		if (sequenceInfo == null)
			return;

		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		double lineWidth = Math.abs(1.0 / xform.getScaleX());

		SequenceDataCache data = sequenceInfo.getDataCache();
		long barLengthTicks = data.getBarLengthTicks();

		long firstBarTick = (data.microsToTick(clipPosStart) / barLengthTicks) * barLengthTicks;
		long lastBarTick = (data.microsToTick(clipPosEnd) / barLengthTicks) * barLengthTicks;

		boolean[] sectionArray = getSectionsModified();
		long barCount = data.microsToTick(clipPosStart) / barLengthTicks - 1;
		long barMicros = clipPosStart;
		boolean barEdited = false;
		boolean barBothEdited = false;
		long loopcounter = 0; // max 2500 bars
		for (long barTick = firstBarTick; barTick <= lastBarTick + barLengthTicks && loopcounter < 2500; barTick += barLengthTicks) {
			loopcounter++;
			barEdited = false;
			long barTempMicros = data.tickToMicros(barTick);
			boolean barTouched = sectionArray != null && barCount < sectionArray.length && barCount > -1 && sectionArray[(int) barCount];
			List<Pair<Long,Long>> modi = null;
			if (barTouched) {
				modi = getMicrosModified(barMicros, barTempMicros);
			}
			if (modi != null) {
				double start = (barMicros + lineWidth);
				double finish = (barTempMicros);
				for (Pair<Long,Long> pair : modi) {
					double x = Math.max(start, pair.first);
					double w = Math.min(finish, pair.second) - x;
					if (w > 0.0d) {
						rectTmp.setRect(x, MIN_RENDERED - 1, w, MAX_RENDERED - MIN_RENDERED + 2);
						g2.setColor(ColorTable.GRAPH_BACKGROUND_EDITED.get());
						g2.fill(rectTmp);
						barEdited = true;
						start = x + w;
					}
				}
			}
			if (getFirstBar() != null && barCount < Math.floor(getFirstBar())) {
				// whole bar is red
				rectTmp.setRect(barMicros + lineWidth, MIN_RENDERED - 1, barTempMicros - barMicros - lineWidth,
						MAX_RENDERED - MIN_RENDERED + 2);
				g2.setColor(ColorTable.GRAPH_SILENCED.get());
				g2.fill(rectTmp);
			} else if (getLastBar() != null && barCount >= Math.ceil(getLastBar())) {
				// whole bar is red
				rectTmp.setRect(barMicros + lineWidth, MIN_RENDERED - 1, barTempMicros - barMicros - lineWidth,
						MAX_RENDERED - MIN_RENDERED + 2);
				g2.setColor(ColorTable.GRAPH_SILENCED.get());
				g2.fill(rectTmp);
			} else {
				if (getFirstBar() != null && barCount < Math.ceil(getFirstBar())) {
					// partial bar is red
					assert getFirstBarTick() != null && getFirstBarTick() >= 0L;
					long lateStart = data.tickToMicros(getFirstBarTick());
					rectTmp.setRect(barMicros + lineWidth, MIN_RENDERED - 1, Math.min(lateStart, barTempMicros) - barMicros - lineWidth,
							MAX_RENDERED - MIN_RENDERED + 2);
					g2.setColor(ColorTable.GRAPH_SILENCED.get());
					g2.fill(rectTmp);
				}
				if (getLastBar() != null && barCount >= Math.floor(getLastBar())) {
					// partial bar is red
					assert getLastBarTick() != null && getLastBarTick() >= 0L;
					long earlyEnd = data.tickToMicros(getLastBarTick());
					rectTmp.setRect(Math.max(earlyEnd, barMicros + lineWidth), MIN_RENDERED - 1, barTempMicros - barMicros - lineWidth,
							MAX_RENDERED - MIN_RENDERED + 2);
					g2.setColor(ColorTable.GRAPH_SILENCED.get());
					g2.fill(rectTmp);
				}
			}

			barCount++;
			barMicros = barTempMicros;
			rectTmp.setRect(barMicros, MIN_RENDERED - 1, lineWidth, MAX_RENDERED - MIN_RENDERED + 2);
			barBothEdited = false;
			if (barEdited && sectionArray != null && barCount < sectionArray.length && barCount > -1) {
				// TODO: This could be refined a bit now that floats are used
				if (sectionArray[(int) barCount]) {
					barBothEdited = true;
				}
			}
			g2.setColor(barBothEdited ? ColorTable.BAR_LINE_EDITED.get() : ColorTable.BAR_LINE.get());
			g2.fill(rectTmp);
		}
	}

	/** Draws octave reference lines or histogram threshold lines. */
	private void paintOctaveLines(Graphics2D g2, AffineTransform xform) {
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

			double y1 = Util.clamp(getLowThreshold(), MIN_RENDERED, MAX_RENDERED);
			double y2 = Util.clamp(getHighThreshold(), MIN_RENDERED, MAX_RENDERED);
			rectTmp.setRect(0, y1, sequencer.getLength(), lineHeight);
			g2.fill(rectTmp);
			rectTmp.setRect(0, y2, sequencer.getLength(), lineHeight);
			g2.fill(rectTmp);
		}
	}

	/**
	 * Draws all notes in normal (pitch-based) rendering mode using the pre-built note cache.
	 * Notes are drawn in four layered passes so that bad notes always appear on top of normal
	 * notes, and note-on highlights appear on top of everything.
	 *
	 * <p>The cache must be valid (i.e. {@link #noteCacheDirty} == false) before this is called.
	 */
	private void paintNotesNormal(Graphics2D g2, AffineTransform xform, double minLength, double height,
			long clipPosStart, long clipPosEnd, boolean showNotesOn, long minSongPos) {

		List<CachedNote> notesOnList = null;

		// Pass 1: Draw normal (playable) primary notes and all normal (playable) doublings.
		// noteCache is sorted by startMicros ascending (events come from getEvents() sorted by tick).
		for (CachedNote cn : noteCache) {
			if (cn.startMicros > clipPosEnd) break;
			if (cn.endMicros < clipPosStart) continue;

			boolean isOn = showNotesOn && songPos >= cn.startMicros && minSongPos <= cn.endMicros;

			if (isOn) {
				// Defer to note-on highlight pass (takes priority over bad/extraBad too)
				if (notesOnList == null) notesOnList = new ArrayList<>();
				notesOnList.add(cn);
			} else if (!cn.bad && !cn.extraBad) {
				g2.setColor(cn.color);
				fillNote(g2, cn.source, cn.noteId, minLength, height);
			}

			// Normal (non-bad, non-ool) doublings are drawn in pass 1 for all primaries,
			// regardless of primary category, to match original rendering order.
			if (cn.doubIds != null) {
				boolean activeNote = isOn && sequencer.isNoteActive(cn.source.note.id);
				for (int d = 0; d < cn.doubIds.length; d++) {
					if (activeNote || cn.doubOutOfLimit[d] || cn.doubBad[d]) continue;
					g2.setColor(cn.doubColors[d]);
					fillNote(g2, cn.doubSrc[d], cn.doubIds[d], minLength, height);
				}
			}
		}

		// Pass 2: Draw bad primary notes on top of normal notes.
		for (CachedNote cn : cacheBad) {
			if (cn.startMicros > clipPosEnd) break;
			if (cn.endMicros < clipPosStart) continue;
			if (showNotesOn && songPos >= cn.startMicros && minSongPos <= cn.endMicros) continue;
			g2.setColor(cn.color);
			fillNote(g2, cn.source, cn.noteId, minLength, height);
		}

		// Pass 3: Draw extra-bad primary notes (e.g., polyphony overflow) on top of bad notes.
		for (CachedNote cn : cacheExtraBad) {
			if (cn.startMicros > clipPosEnd) break;
			if (cn.endMicros < clipPosStart) continue;
			if (showNotesOn && songPos >= cn.startMicros && minSongPos <= cn.endMicros) continue;
			g2.setColor(cn.color);
			fillNote(g2, cn.source, cn.noteId, minLength, height);
		}

		// Pass 4: Draw bad (out-of-range) doubling notes.
		for (CachedNote cn : cacheWithDoublings) {
			if (cn.startMicros > clipPosEnd) break;
			if (cn.endMicros < clipPosStart) continue;
			boolean isOn = showNotesOn && songPos >= cn.startMicros && minSongPos <= cn.endMicros;
			boolean activeNote = isOn && sequencer.isNoteActive(cn.source.note.id);
			for (int d = 0; d < cn.doubIds.length; d++) {
				if (!cn.doubBad[d] || cn.doubOutOfLimit[d] || activeNote) continue;
				g2.setColor(cn.doubColors[d]);
				fillNote(g2, cn.doubSrc[d], cn.doubIds[d], minLength, height);
			}
		}

		// Pass 5: Note-on highlights (border + bright fill) on top of everything.
		paintNoteOnHighlights(g2, xform, minLength, height, notesOnList);
	}

	/**
	 * Draws the "note on" highlight border and fill for currently-playing notes.
	 * Uses the pre-collected {@code notesOnList} built during {@link #paintNotesNormal}.
	 *
	 * <p>All active doublings (including out-of-limit ones) are drawn in highlight mode,
	 * matching the behaviour of the original code which did not apply an isOutOfLimit filter
	 * in the highlight pass.
	 */
	private void paintNoteOnHighlights(Graphics2D g2, AffineTransform xform, double minLength, double height,
			List<CachedNote> notesOnList) {
		if (notesOnList == null) return;

		double noteOnOutlineWidthX = noteOnOutlineWidthPix / xform.getScaleX();
		double noteOnOutlineWidthY = Math.abs(noteOnOutlineWidthPix / xform.getScaleY());
		double noteOnExtraHeightY = Math.abs(noteOnExtraHeightPix / xform.getScaleY());

		// Border (outline) pass
		g2.setColor(noteOnBorder.get());
		for (CachedNote cn : notesOnList) {
			fillNote(g2, cn.source, cn.noteId, minLength, height,
					noteOnOutlineWidthX, noteOnExtraHeightY + noteOnOutlineWidthY);
			if (cn.doubIds != null) {
				for (int d = 0; d < cn.doubIds.length; d++) {
					fillNote(g2, cn.source, cn.doubIds[d], minLength, height,
							noteOnOutlineWidthX, noteOnExtraHeightY + noteOnOutlineWidthY);
				}
			}
		}

		// Fill pass
		g2.setColor(noteOnColor.get());
		for (CachedNote cn : notesOnList) {
			fillNote(g2, cn.source, cn.noteId, minLength, height, 0, noteOnExtraHeightY);
			if (cn.doubIds != null) {
				for (int d = 0; d < cn.doubIds.length; d++) {
					fillNote(g2, cn.source, cn.doubIds[d], minLength, height, 0, noteOnExtraHeightY);
				}
			}
		}
	}

	/** Draws all notes in velocity (dynamics) display mode. */
	private void paintNotesVelocity(Graphics2D g2, long clipPosStart, long clipPosEnd, boolean showNotesOn,
			long minSongPos, List<NoteEvent> noteEvents) {
		// Render the volume of each note instead of its note value
		Dynamics[] dynamicsValues = Dynamics.values();

		// Render from highest dynamics to lowest.
		// Out of range notes are rendered with (d == dynamicsValues.length) and (d ==
		// -1)
		for (int d = dynamicsValues.length; d >= -1; --d) {
			for (NoteEvent ne : noteEvents) {
				if (ne.getEndMicros() < clipPosStart || ne.getStartMicros() > clipPosEnd || !audibleNote(ne))
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

	protected List<Pair<Long, Long>> getMicrosModified(long from, long to) {
		return null;
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
				log.log(Level.SEVERE, "Notegraph transform could not be inverted (mouse)", e1);
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
				barLabel = new JLabel(UIText.get("maestro.bar.0", BarNumberLabel.getBarStringFloat(sequencer, sequenceInfo.getDataCache())));
				barLabel.setFocusable(false);
				barLabel.setBorder(BorderFactory.createEmptyBorder(5, 25, 5, 5));
		        barIndicator = new JPopupMenu();
		        barIndicator.add(barLabel);
		        barIndicator.show(NoteGraph.this, e.getX(), e.getY());
		        barIndicator.setVisible(true);
			}
			getRootPane().requestFocus();
		}

		@Override
		public void mouseDragged(MouseEvent e) {
			if ((e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0) {
				if (!isDragCanceled(e)) {
					sequencer.setDragging(true);
					sequencer.setDragPosition(positionFromEvent(e));
					barLabel.setText(UIText.get("maestro.bar.0", BarNumberLabel.getBarStringFloat(sequencer, sequenceInfo.getDataCache())));
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

	protected Float getLastBar() {
		return null;
	}

	protected Float getFirstBar() {
		return null;
	}

	protected Long getLastBarTick() {
		return null;
	}

	protected Long getFirstBarTick() {
		return null;
	}

	protected boolean isActiveTrack() {
		return false;
	}

    protected boolean isBars() {
        return false;
    }

	protected double getMinNoteHeightPx() {
		return 1.0;
	}
}
