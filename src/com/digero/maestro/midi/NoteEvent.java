/* Copyright (c) 2010 Ben Howell
 * This software is licensed under the MIT License
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"), 
 * to deal in the Software without restriction, including without limitation 
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, 
 * and/or sell copies of the Software, and to permit persons to whom the 
 * Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING 
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER 
 * DEALINGS IN THE SOFTWARE.
 */

package com.digero.maestro.midi;

import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;

public class NoteEvent implements Comparable<NoteEvent> {
	private final ITempoCache tempoCache;

	public final Note note;
	public final int velocity;
	public int midiPan = -1;

	private long startTick;
	private long endTick;
	private long startMicrosCached;
	private long endMicrosCached;

	public NoteEvent tiesFrom = null;
	public NoteEvent tiesTo = null;
	public long continues = 0;

	// public List<NoteEvent> origEvent;
	public boolean alreadyMapped = false;

	// private Map<AbcPart, Boolean> pruneMap = null;

	public int origPitch = 0;
	public boolean doubledNote = false;
	public int combinePrioritiesScoreMultiplier = 1;// This is a temp variable used by QuantizedTimingInfo

	public NoteEvent(Note note, int velocity, long startTick, long endTick, ITempoCache tempoCache) {
		assert note != null;
		this.note = note;
		this.velocity = velocity;
		this.tempoCache = tempoCache;
		setStartTick(startTick);
		setEndTick(endTick);
	}

	public ITempoCache getTempoCache() {
		return tempoCache;
	}

	public long getStartTick() {
		return startTick;
	}

	public long getEndTick() {
		return endTick;
	}

	public void setStartTick(long startTick) {
		this.startTick = startTick;
		this.startMicrosCached = -1;
	}

	public void setEndTick(long endTick) {
		this.endTick = endTick;
		this.endMicrosCached = -1;
	}

	public void setLengthTicks(long tickLength) {
		setEndTick(startTick + tickLength);
	}

	public long getLengthTicks() {
		return endTick - startTick;
	}

	public long getFullLengthTicks() {
		long fullEndTick = endTick;
		for (NoteEvent neTie = tiesTo; neTie != null; neTie = neTie.tiesTo) {
			fullEndTick = neTie.endTick;
		}
		return fullEndTick - startTick;
	}

	public long getStartMicros() {
		if (startMicrosCached == -1)
			startMicrosCached = tempoCache.tickToMicros(startTick);

		return startMicrosCached;
	}

	public long getEndMicros() {
		if (endMicrosCached == -1)
			endMicrosCached = tempoCache.tickToMicros(endTick);

		return endMicrosCached;
	}

	public long getLengthMicros() {
		return getEndMicros() - getStartMicros();
	}

	public NoteEvent getTieStart() {
		if (tiesFrom == null)
			return this;
		assert tiesFrom.startTick < this.startTick;
		return tiesFrom.getTieStart();
	}

	public NoteEvent getTieEnd() {
		if (tiesTo == null)
			return this;
		assert tiesTo.endTick > this.endTick;
		return tiesTo.getTieEnd();
	}

	/**
	 * Splits the NoteEvent into two events with a tie between them.
	 * 
	 * @param splitPointTick The tick index to split the NoteEvent.
	 * @return The new NoteEvent that was created starting at splitPointTick.
	 */
	public NoteEvent splitWithTieAtTick(long splitPointTick) {
		assert splitPointTick > startTick && splitPointTick < endTick;

		NoteEvent next = new NoteEvent(note, velocity, splitPointTick, endTick, tempoCache);
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

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof NoteEvent) {
			NoteEvent that = (NoteEvent) obj;
			return (this.startTick == that.startTick) && (this.endTick == that.endTick)
					&& (this.note.id == that.note.id) && this.velocity == that.velocity
					&& ((this.tiesFrom == null && that.tiesFrom == null) || (this.tiesFrom != null && that.tiesFrom != null))
					&& ((this.tiesTo == null && that.tiesTo == null) || (this.tiesTo != null && that.tiesTo != null))
					&& this.tempoCache == that.getTempoCache()
					&& this.midiPan == that.midiPan && ((this instanceof BentNoteEvent) == (that instanceof BentNoteEvent));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return ((int) startTick) ^ ((int) endTick) ^ note.id ^ (velocity+1) ^ (midiPan+1);
	}

	@Override
	public int compareTo(NoteEvent that) {
		if (that == null)
			return 1;

		if (this.startTick != that.startTick)
			return (this.startTick > that.startTick) ? 1 : -1;

		if (this.note.id != that.note.id)
			return this.note.id - that.note.id;

		if (this.endTick != that.endTick)
			return (this.endTick > that.endTick) ? 1 : -1;

		return 0;
	}

	public String printout() {
		return "Note " + note.id + " dura " + getFullLengthTicks() + " |";
	}
	
	@Override
	public String toString() {
		return "Note " + note.toString() + " duraTicks=" + getFullLengthTicks() + " tick:"+startTick+"-"+endTick+" pan="+midiPan+" vol="+velocity+" TiesIsNull: "+(tiesFrom==null)+" "+(tiesTo == null)+" time: "+(getStartMicros()/1000000.0)+" to "+(getEndMicros()/1000000.0);
	}

	public void setMidiPan(int pan) {
		midiPan = pan;
	}

	/*
	 * public boolean isPruned(AbcPart abcPart) { if (abcPart == null || pruneMap == null) { return false; } return
	 * pruneMap.get(abcPart) != null && pruneMap.get(abcPart) == true; }
	 * 
	 * public void prune(AbcPart part) { if (pruneMap == null) { pruneMap = new HashMap<AbcPart, Boolean>(); }
	 * pruneMap.put(part, true); }
	 * 
	 * public void resetPruned(AbcPart part) { if (pruneMap == null) { return; } pruneMap.remove(part); }
	 * 
	 * public void resetAllPruned() { pruneMap = null; }
	 */
}