package com.digero.maestro.midi;

import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;
import com.digero.common.util.Util;
import com.digero.maestro.abc.QuantizedTimingInfo;

public class AbcNoteEvent extends NoteEvent {
	
	public AbcNoteEvent tiesFrom = null;
	public AbcNoteEvent tiesTo = null;
	
	// These 3 is used by organic v2:
	public long startABCMicros;
	public long endABCMicros;
	
	// These fields are used by the pruning:
	// Note that if several midi notes contributed to one abc note,
	// then only one of the midi notes will be in origNote, as we do atm. not need to know about all of them.
	@Deprecated
	public boolean doubledNote = false;// Only used in Chord comparator.
	public final MidiNoteEvent origNote;// Beware this can be null if note-event is a rest. Beside that, its guaranteed to be non-null.
	public long continues = 0;// Tick length that this continues as in seperate split note(s). Beyond ties.
	private Integer origBend = null;// The bend that was in effect when this noteEvent was 'born'. Its used only by pruning algorithm.
	//public float fromHowManyTracks = 1.0f;// Let pruning system know this note originate from multiple tracks, so it can be prioritized.
	

	public AbcNoteEvent(Note note, int velocity, long startTick, long endTick, ITempoCache tempoCache, MidiNoteEvent origNote) {
		super(note, velocity, startTick, endTick, tempoCache);
		this.origNote = origNote;
		assert tempoCache instanceof QuantizedTimingInfo;
	}
	
	public AbcNoteEvent getTieStart() {
		if (tiesFrom == null)
			return this;
		assert tiesFrom.startTick < this.startTick;
		return tiesFrom.getTieStart();
	}

	public AbcNoteEvent getTieEnd() {
		if (tiesTo == null)
			return this;
		assert tiesTo.endTick > this.endTick;
		return tiesTo.getTieEnd();
	}
	
	public long getFullLengthTicks() {
		long fullEndTick = endTick;
		for (AbcNoteEvent neTie = tiesTo; neTie != null; neTie = neTie.tiesTo) {
			fullEndTick = neTie.endTick;
		}
		return fullEndTick - startTick;
	}

	/**
	 * Splits the NoteEvent into two events with a tie between them.
	 * 
	 * @param splitPointTick The tick index to split the NoteEvent.
	 * @return The new NoteEvent that was created starting at splitPointTick.
	 */
	public AbcNoteEvent splitWithTieAtTick(long splitPointTick) {
		return splitWithTieAtTick(splitPointTick, -1);
	}
	
	/**
	 * Only called directly by multi-stage organic
	 */
	public AbcNoteEvent splitWithTieAtTick(long splitPointTick, long splitPointMicros) {
		assert splitPointTick >= startTick:"split before beginning ("+splitPointTick+","+Util.formatDurationM(splitPointMicros)+") "+ this;
		assert splitPointTick != startTick:"split at beginning ("+splitPointTick+","+Util.formatDurationM(splitPointMicros)+") "+ this;
		assert splitPointTick < endTick:"split after end";

		AbcNoteEvent next = new AbcNoteEvent(note, velocity, splitPointTick, endTick, tempoCache, this.origNote);
		setEndTick(splitPointTick);
		
		if (splitPointMicros != -1) {
			next.startABCMicros = splitPointMicros;
			next.endABCMicros = endABCMicros;
			endABCMicros = splitPointMicros;
		}

		if (note != Note.REST) {
			if (this.tiesTo != null) {
				next.tiesTo = this.tiesTo;
				this.tiesTo.tiesFrom = next;
			}
			next.tiesFrom = this;
			this.tiesTo = next;
		}
		next.continues = this.continues;
		return next;
	}
	
	/*@Override
	public boolean equals(Object obj) {
		if (obj instanceof AbcNoteEvent) {
			AbcNoteEvent that = (AbcNoteEvent) obj;
			return this.getStartTick() == that.getStartTick()
					&& this.getEndTick() == that.getEndTick()
					&& (this.note.id == that.note.id) && this.velocity == that.velocity
					&& this.getTempoCache() == that.getTempoCache()
					&& ((this.tiesFrom == null && that.tiesFrom == null) || (this.tiesFrom != null && that.tiesFrom != null))
					&& ((this.tiesTo == null && that.tiesTo == null) || (this.tiesTo != null && that.tiesTo != null))
					&& this.getClass() == that.getClass();
		}
		return false;
	}*/
	
	public String printout() {
		return "Note " + note.id + " dura " + getFullLengthTicks() + " |";
	}
	
	@Override
	public String toString() {
		String post = "";
		post = " time: "+Util.formatDurationM(startABCMicros)+" to "+Util.formatDurationM(endABCMicros); 
		return getClass().getName()+": " + note.toString() + " duraTicks=" + getFullLengthTicks() + " tick:"+startTick+"-"+endTick+" vol="+velocity+" TiesIsNull: "+(tiesFrom==null)+" "+(tiesTo == null)+post;
	}
	
	public String toStringMicros() {
		return Util.formatDurationM(startABCMicros)+" -> "+Util.formatDurationM(endABCMicros)+" "+note;
	}

	/**
	 * 
	 * @param bend The bend the midi note was affected by to create this abc note.
	 */
	public void setOrigBend(int bend) {
		if (bend != 0) this.origBend  = bend;
	}

	final public Integer getOrigBend() {
		return origBend;
	}
	
	public AbcNoteEvent copy() {
		if (this instanceof BentAbcNoteEvent) {
			BentAbcNoteEvent c = new BentAbcNoteEvent(note, velocity, startTick, endTick, tempoCache, (BentMidiNoteEvent)(this.origNote));
			if (origBend != null) c.setOrigBend(origBend);
			c.continues = this.continues;
			c.startABCMicros = this.startABCMicros;
			c.endABCMicros = this.endABCMicros;
			return c;
		} else {
			AbcNoteEvent c = new AbcNoteEvent(note, velocity, startTick, endTick, tempoCache, origNote);
			if (origBend != null) c.setOrigBend(origBend);
			c.continues = this.continues;
			c.startABCMicros = this.startABCMicros;
			c.endABCMicros = this.endABCMicros;
			return c;
		}
	}
}
