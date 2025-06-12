package com.digero.common.view;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JLabel;
import javax.swing.UIManager;

import com.digero.common.midi.IBarNumberCache;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;

@SuppressWarnings("serial")
public class BarNumberLabel extends JLabel implements Listener<SequencerEvent>, IDiscardable {
	private IBarNumberCache barNumberCache;
	private SequencerWrapper sequencer;
	private long initialOffsetTick = 0L;
	private boolean floatingPoint = false;
	private int maxTextWidth = 0;

	public BarNumberLabel(SequencerWrapper sequencer, IBarNumberCache barNumberCache, boolean floatingPoint, String maxString) {
		this.sequencer = sequencer;
		this.barNumberCache = barNumberCache;
		this.floatingPoint  = floatingPoint;
		
		sequencer.addChangeListener(this);
		
		maxTextWidth = getFontMetrics(getFont()).stringWidth(maxString);
	}

	@Override
	public void discard() {
		if (sequencer != null)
			sequencer.removeChangeListener(this);
	}

	public IBarNumberCache getBarNumberCache() {
		return barNumberCache;
	}

	public void setBarNumberCache(IBarNumberCache barNumberCache) {
		if (this.barNumberCache != barNumberCache) {
			this.barNumberCache = barNumberCache;
			update();
		}
	}

	public long getInitialOffsetTick() {
		return initialOffsetTick;
	}

	public void setInitialOffsetTick(long initialOffsetTick) {
		if (this.initialOffsetTick != initialOffsetTick) {
			this.initialOffsetTick = initialOffsetTick;
			update();
		}
	}

	@Override
	public void onEvent(SequencerEvent evt) {
		SequencerProperty p = evt.getProperty();
		if (p.isInMask(SequencerProperty.THUMB_POSITION_MASK | SequencerProperty.LENGTH.mask
				| SequencerProperty.TEMPO.mask | SequencerProperty.SEQUENCE.mask)) {
			update();
		}
	}

	private String lastPrintedBars = "-/-";
	
	public static String getBarString(SequencerWrapper sequencer, IBarNumberCache barNumberCache) {
		return getBarString(sequencer, barNumberCache, 0);
	}
	
	public static String getBarString(SequencerWrapper sequencer, IBarNumberCache barNumberCache, long initialOffsetTick) {
		if (barNumberCache == null || sequencer == null) {
			return "-/-";
		}
		
		long tickLength = Math.max(0, sequencer.getTickLength() - initialOffsetTick);
		long tick = Math.min(tickLength, sequencer.getThumbTick() - initialOffsetTick);

		int barNumber = (tick < 0) ? 0 : (barNumberCache.tickToBarNumber(tick) + 1);
		int barCount = barNumberCache.tickToBarNumber(tickLength) + 1;
		
		return barNumber + "/" +  barCount;
	}
	
	public static String getBarStringFloat(SequencerWrapper sequencer, IBarNumberCache barNumberCache) {
		if (barNumberCache == null || sequencer == null) {
			return "-/-";
		}
		
		long tickLength = Math.max(0, sequencer.getTickLength());
		long tick = Math.min(tickLength, sequencer.getThumbTick());

		int barNumber = (tick < 0) ? 0 : (barNumberCache.tickToBarNumber(tick));
		int barCount = barNumberCache.tickToBarNumber(tickLength) + 1;
		
		float barFloat = map(tick, barNumberCache.getBarToTick(barNumber+1), barNumberCache.getBarToTick(barNumber+2), barNumber, barNumber+1); 
		
		return String.format("%.2f/%d", barFloat, barCount);
	}
	
	static float map(long value, long leftMin, long leftMax, int rightMin, int rightMax) {
		// Figure out how 'wide' each range is
		long leftSpan = leftMax - leftMin;
		int rightSpan = rightMax - rightMin;

		// Convert the left range into a 0-1 range (float)
		double valueScaled = (value - leftMin) / (double) leftSpan;

		// Convert the 0-1 range into a value in the right range.
		return (float)(rightMin + (valueScaled * rightSpan));
	}

	private void update() {
		if (barNumberCache == null) {
			if (!lastPrintedBars.equals("-/-")) {
				lastPrintedBars = "-/-";
				setText(lastPrintedBars);
			}
			return;
		}
		
		String bars = "";
		
		if (floatingPoint) {
			bars = getBarStringFloat(sequencer, barNumberCache);
		} else {
			bars = getBarString(sequencer, barNumberCache, initialOffsetTick);
		}

		if (!bars.equals("-/-") && !lastPrintedBars.equals(bars)) {
			setText(bars);
			if (lastPrintedBars.length() < bars.length()) revalidate();
			lastPrintedBars = bars;
		}
	}
	
	@Override
    public void setText(String text) {
        super.setText(text);
        repaint();
    }

    @Override
    public void revalidate() {}

    /** 
     * If ever want to force a layout (e.g. after a font/locale change),
     * call this manually:
     */
    public void forceRevalidate() {
        super.revalidate();
    }

    @Override
    public Dimension getPreferredSize() {
        // Always report a width equal to the widest text we’ve seen
        Insets ins = getInsets();
        FontMetrics fm = getFontMetrics(getFont());
        int h = fm.getHeight() + ins.top + ins.bottom;
        return new Dimension(maxTextWidth + ins.left + ins.right, h);
    }
}
