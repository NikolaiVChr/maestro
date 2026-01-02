package com.digero.common.midi;

import java.util.EventObject;

@SuppressWarnings("serial")
public class SequencerEvent extends EventObject {

	public enum SequencerProperty {
		POSITION, LENGTH, DRAG_POSITION, IS_DRAGGING, IS_RUNNING, IS_LOADED, TRACK_ACTIVE, TEMPO, SEQUENCE, SONG_ENDED;

		public static final int THUMB_POSITION_MASK = POSITION.mask | DRAG_POSITION.mask | IS_DRAGGING.mask;

		public final int mask;

		public static long makeMask(SequencerProperty[] props) {
			int mask = 0;
			for (SequencerProperty prop : props) {
				mask |= prop.mask;
			}
			return mask;
		}

		public boolean isInMask(int mask) {
			return (mask & this.mask) != 0;
		}
		
		// Debug
		public void printSetMasks() {
			if ((mask & POSITION.mask) != 0) {
				System.out.print(" POSITION");
			}
			if ((mask & LENGTH.mask) != 0) {
				System.out.print(" LENGTH");
			}
			if ((mask & DRAG_POSITION.mask) != 0) {
				System.out.print(" DRAG_POSITION");
			}
			if ((mask & IS_DRAGGING.mask) != 0) {
				System.out.print(" IS_DRAGGING");
			}
			if ((mask & IS_RUNNING.mask) != 0) {
				System.out.print(" IS_RUNNING");
			}
			if ((mask & IS_LOADED.mask) != 0) {
				System.out.print(" IS_LOADED");
			}
			if ((mask & TRACK_ACTIVE.mask) != 0) {
				System.out.print(" TRACK_ACTIVE");
			}
			if ((mask & TEMPO.mask) != 0) {
				System.out.print(" TEMPO");
			}
			if ((mask & SEQUENCE.mask) != 0) {
				System.out.print(" SEQUENCE");
			}
			if ((mask & SONG_ENDED.mask) != 0) {
				System.out.print(" SONG_ENDED");
			}
			System.out.println();
		}

		@SuppressWarnings("HardCodedStringLiteral")
		@Override
		public String toString() {
			String str = "";
			if ((mask & POSITION.mask) != 0) {
				str += " POSITION";
			}
			if ((mask & LENGTH.mask) != 0) {
				str += " LENGTH";
			}
			if ((mask & DRAG_POSITION.mask) != 0) {
				str += " DRAG_POSITION";
			}
			if ((mask & IS_DRAGGING.mask) != 0) {
				str += " IS_DRAGGING";
			}
			if ((mask & IS_RUNNING.mask) != 0) {
				str += " IS_RUNNING";
			}
			if ((mask & IS_LOADED.mask) != 0) {
				str += " IS_LOADED";
			}
			if ((mask & TRACK_ACTIVE.mask) != 0) {
				str += " TRACK_ACTIVE";
			}
			if ((mask & TEMPO.mask) != 0) {
				str += " TEMPO";
			}
			if ((mask & SEQUENCE.mask) != 0) {
				str += " SEQUENCE";
			}
			if ((mask & SONG_ENDED.mask) != 0) {
				str += " SONG_ENDED";
			}
			return str;
		}

		SequencerProperty() {
			mask = MaskMaker.getNextMask();
		}

		private static class MaskMaker {
			private static int nextMask = 1;

			public static int getNextMask() {
				if (nextMask < 0)
					throw new RuntimeException("Mask overflow; convert int to long");
				int mask = nextMask;
				nextMask <<= 1;
				return mask;
			}
		}
	}

	private SequencerProperty property;

	public SequencerEvent(SequencerWrapper sequencerWrapper, SequencerProperty property) {
		super(sequencerWrapper);
		this.property = property;
	}

	@Override
	public SequencerWrapper getSource() {
		return (SequencerWrapper) super.getSource();
	}

	public SequencerProperty getProperty() {
		return property;
	}
	
	public String toString() {
		String str = property.toString();		
		return str;
	}
}
