package com.digero.common.view;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JComponent;
import javax.swing.UIManager;

import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Util;

@SuppressWarnings("serial")
public class SongPositionLabel extends JComponent implements Listener<SequencerEvent>, IDiscardable {
	private SequencerWrapper sequencer;
	private boolean adjustForTempo;
	private long initialOffsetTick = 0;
	public boolean countdown = false;

	public SongPositionLabel(SequencerWrapper sequencer) {
		this(sequencer, false);
		//setText("000:00/000:00");
	}

	public SongPositionLabel(SequencerWrapper sequencer, boolean adjustForTempo) {
		this.sequencer = sequencer;
		this.adjustForTempo = adjustForTempo;
		sequencer.addChangeListener(this);
		update();
	}

	@Override
	public void discard() {
		if (sequencer != null) {
			sequencer.removeChangeListener(this);
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
		if (p.isInMask(
				SequencerProperty.THUMB_POSITION_MASK | SequencerProperty.LENGTH.mask | SequencerProperty.TEMPO.mask)) {
			update();
		}
	}

	private long lastPrintedMicros = -1;
	private long lastPrintedLength = -1;

	private void update() {
		long tickLength = Math.max(0L, sequencer.getTickLength() - initialOffsetTick);
		long tick = Math.max(0L, Math.min(tickLength, sequencer.getThumbTick() - initialOffsetTick));

		long micros = sequencer.tickToMicros(tick);
		long length = sequencer.tickToMicros(tickLength);

		if (countdown) {
			micros = length - micros;
		}
	
		// No longer needed after 3.0.2 - sequencer is already scaled by the tempo factor during refresh
//		if (adjustForTempo) {
//			micros = Math.round(micros / (double) sequencer.getTempoFactor());
//			length = Math.round(length / (double) sequencer.getTempoFactor());
//		}

		if (micros != lastPrintedMicros || length != lastPrintedLength) {
			setText(Util.formatDuration(micros, length) + "/" + Util.formatDuration(length, length));
			if (lastPrintedLength == -1) {
				//setText("000:00/000:00");
				revalidate();
			}
			lastPrintedMicros = micros;
			lastPrintedLength = length;
		}
	}
	
	// BEGIN custom jlabel:
	
	private String text = "";
	private final Insets insets = new Insets(0, 4, 0, 4);
	private int max = 20;
	
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
        max = Math.max(w, max);
        return new Dimension(max, h);
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
