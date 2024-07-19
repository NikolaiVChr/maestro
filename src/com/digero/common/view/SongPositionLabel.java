package com.digero.common.view;

import javax.swing.JLabel;

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

	public SongPositionLabel(SequencerWrapper sequencer) {
		this(sequencer, false);
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
		long tickLength = Math.max(0, sequencer.getTickLength() - initialOffsetTick);
		long tick = Math.max(0, Math.min(tickLength, sequencer.getThumbTick() - initialOffsetTick));

		long micros = sequencer.tickToMicros(tick);
		long length = sequencer.tickToMicros(tickLength);

		// No longer needed after 3.0.2 - sequencer is already scaled by the tempo factor during refresh
//		if (adjustForTempo) {
//			micros = Math.round(micros / (double) sequencer.getTempoFactor());
//			length = Math.round(length / (double) sequencer.getTempoFactor());
//		}

		if (micros != lastPrintedMicros || length != lastPrintedLength) {
			lastPrintedMicros = micros;
			lastPrintedLength = length;
			setText(Util.formatDuration(micros, length) + "/" + Util.formatDuration(length, length));
		}
	}
}
