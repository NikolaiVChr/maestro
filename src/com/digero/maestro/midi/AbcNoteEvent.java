package com.digero.maestro.midi;

import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;
import com.digero.maestro.abc.QuantizedTimingInfo;

public class AbcNoteEvent extends NoteEvent {
	
	public AbcNoteEvent tiesFrom = null;
	public AbcNoteEvent tiesTo = null;
	
	public final MidiNoteEvent origNote;	
	public int origPitch = 0;// TODO: replace by origNote 
	
	public long continues = 0;// Tick length that this continues as in seperate split note(s). Without ties.

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
		assert splitPointTick > startTick && splitPointTick < endTick;

		AbcNoteEvent next = new AbcNoteEvent(note, velocity, splitPointTick, endTick, tempoCache, this.origNote);
		setEndTick(splitPointTick);
		next.origPitch = origPitch;

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
		return getClass().getName()+": " + note.toString() + " duraTicks=" + getFullLengthTicks() + " tick:"+startTick+"-"+endTick+" vol="+velocity+" TiesIsNull: "+(tiesFrom==null)+" "+(tiesTo == null)+" time: "+(getStartMicros()/1000000.0)+" to "+(getEndMicros()/1000000.0);
	}
}
