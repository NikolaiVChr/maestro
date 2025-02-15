package com.digero.maestro.midi;

import java.util.HashMap;
import java.util.Map;

import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;
import com.digero.maestro.abc.AbcPart;

public class MidiNoteEvent extends NoteEvent {
	
	public final int midiPan;
	public boolean alreadyMapped = false;// Used by Xtra drum notes
	public int combinePrioritiesScoreMultiplier = 1;// This is a temp variable used by QuantizedTimingInfo
	private Map<AbcPart, Long> legatoEndTicks = new HashMap<>();

	public MidiNoteEvent(Note note, int velocity, long startTick, long endTick, ITempoCache tempoCache, int pan) {
		super(note, velocity, startTick, endTick, tempoCache);
		this.midiPan = pan;
		assert tempoCache instanceof SequenceDataCache;
	}
	
	@Override
	public void setStartTick(long startTick) {
		throw new RuntimeException("Trying to modify a midi note event!");
	}
	
	
	
	/*@Override
	public boolean equals(Object obj) {
		if (obj instanceof MidiNoteEvent) {
			MidiNoteEvent that = (MidiNoteEvent) obj;
			return this.getStartTick() == that.getStartTick()
					&& this.getEndTick() == that.getEndTick()
					&& (this.note.id == that.note.id) && this.velocity == that.velocity
					&& this.getTempoCache() == that.getTempoCache()
					&& this.midiPan == that.midiPan && this.getClass() == that.getClass();
		}
		return false;
	}*/
	
	@Override
	public String toString() {
		return getClass().getName()+": " + note.toString() + " duraTicks=" + getLengthTicks() + " tick:"+getStartTick()+"-"+getEndTick()+" pan="+midiPan+" vol="+velocity+" time: "+(getStartMicros()/1000000.0)+" to "+(getEndMicros()/1000000.0);
	}

	public void setLegatoEndTick(AbcPart part, Long tick) {
		if (tick == null) legatoEndTicks.remove(part);
		else legatoEndTicks.put(part, tick);
	}

	public Long getLegatoEndTick(AbcPart part) {
		return legatoEndTicks.get(part);
	}
}