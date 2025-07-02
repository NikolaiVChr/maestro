package com.digero.common.view;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

import javax.swing.JLabel;
import javax.swing.UIManager;

import com.digero.common.midi.SequencerEvent;
import com.digero.common.midi.SequencerEvent.SequencerProperty;
import com.digero.common.midi.SequencerWrapper;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.Util;

@SuppressWarnings("serial")
public class SongPositionLabel extends JLabel implements Listener<SequencerEvent>, IDiscardable {
	private SequencerWrapper sequencer;
	private boolean adjustForTempo;
	private long initialOffsetTick = 0;
	public boolean countdown = false;
	private int maxTextWidth = 0;

	public SongPositionLabel(SequencerWrapper sequencer, String maxString) {
		this(sequencer, false, maxString);
	}

	public SongPositionLabel(SequencerWrapper sequencer, boolean adjustForTempo, String maxString) {
		this.sequencer = sequencer;
		this.adjustForTempo = adjustForTempo;
		sequencer.addChangeListener(this);
		maxTextWidth = getFontMetrics(getFont()).stringWidth(maxString);
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
		long tickLength = Math.max(0L, sequencer.getTickLength());
		long tick = Math.max(0L, Math.min(tickLength, sequencer.getThumbTick()));

		long initialOffsetMicros = sequencer.tickToMicros(initialOffsetTick);
		long micros = sequencer.tickToMicros(tick) - initialOffsetMicros;
		long length = sequencer.tickToMicros(tickLength) - initialOffsetMicros;

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
