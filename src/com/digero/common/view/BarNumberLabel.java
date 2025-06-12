package com.digero.common.view;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.UIManager;

import com.digero.common.midi.IBarNumberCache;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;

@SuppressWarnings("serial")
public class BarNumberLabel extends JComponent implements Listener<SequencerEvent>, IDiscardable {
	private IBarNumberCache barNumberCache;
	private SequencerWrapper sequencer;
	private long initialOffsetTick = 0L;
	private boolean floatingPoint = false;
	

	public BarNumberLabel(SequencerWrapper sequencer, IBarNumberCache barNumberCache, boolean floatingPoint) {
		this.sequencer = sequencer;
		this.barNumberCache = barNumberCache;
		this.floatingPoint  = floatingPoint;
		
		sequencer.addChangeListener(this);
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
	
	// BEGIN custom jlabel to avoid revalidate all the time:
	
	private String text = "";
	private final Insets insets = new Insets(0, 4, 0, 4);
	
	public void setText(String t) {
        if (!t.equals(text)) {
            text = t;
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.stringWidth(text) + insets.left + insets.right;
        int h = fm.getHeight()   + insets.top  + insets.bottom;
        return new Dimension(w, h);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        Graphics2D g2 = (Graphics2D) g.create();
        
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2.setFont(UIManager.getFont("Label.font"));
		g2.setColor(isEnabled()
                ? UIManager.getColor("Label.foreground")
                : UIManager.getColor("Label.disabledForeground"));
		
        // draw background:
        // g.setColor(getBackground());
        // g.fillRect(0, 0, getWidth(), getHeight());

        FontMetrics fm = g2.getFontMetrics(getFont());
        int x = insets.left;
        int y = insets.top + fm.getAscent(); 
        g2.drawString(text, x, y);
        g2.dispose();
    }
}
