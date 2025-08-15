package com.digero.maestro.midi;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;

public class BentAbcNoteEvent extends AbcNoteEvent {
	
	private int cacheMin = -1;// These fields will only be used after all bends have been added
	private int cacheMax = -1;// So its fine to cache them here.

	public NavigableMap<Long, Integer> bends = new TreeMap<>();// tick -> relative seminote bend

	public BentAbcNoteEvent(Note note, int velocity, long startTick, long endTick, ITempoCache tempoCache, BentMidiNoteEvent origNote) {
		super(note, velocity, startTick, endTick, tempoCache, origNote);
		setBends(origNote.bends);
	}
	
	public void setBends(NavigableMap<Long, Integer> bends) {
		this.bends = bends;
		cacheMin = -1;
		cacheMax = -1;
	}

	public Integer getBend(long tick) {
		Entry<Long, Integer> entry = bends.floorEntry(tick);
		if (entry == null)
			return null;
		return entry.getValue();
	}

    /**
     * Returns the next bend change tick or endTick if no bend change is found.
     * If the supplied tick has a different bend than the lastBend, it returns the supplied tick.     *
     */
	public long getNextBend(long tick, int lastBend) {
		Entry<Long, Integer> entry_floor = bends.floorEntry(tick);
		
		if (entry_floor != null && entry_floor.getValue() != lastBend) return tick;

        Entry<Long, Integer> entry_ceil = bends.ceilingEntry(tick+1L);
		
		if (entry_ceil == null)
			return endTick;

		long nextTickChange = entry_ceil.getKey();

        return Math.min(nextTickChange, endTick);
    }
	
	/**
	 * 
	 * @return max seminote relative bend
	 */
	public int getMaxBend() {
		if (cacheMax != -1)
			return cacheMax;
		int maxBend = -128;
		for (int value : bends.values()) {
			if (value > maxBend) {
				maxBend = value;
			}
		}
		cacheMax = maxBend;
		return cacheMax;
	}

	/**
	 * 
	 * @return min seminote relative bend
	 */
	public int getMinBend() {
		if (cacheMin != -1)
			return cacheMin;
		int minBend = 128;
		for (int value : bends.values()) {
			if (value < minBend) {
				minBend = value;
			}
		}
		cacheMin = minBend;
		return cacheMin;
	}

	/**
	 * 
	 * @return min seminote absolute bend
	 */
	public int getMinNote() {
		return note.id + getMinBend();
	}

	/**
	 * 
	 * @return max seminote absolute bend
	 */
	public int getMaxNote() {
		return note.id + getMaxBend();
	}
	
	@Override
	public String toString() {
		return getClass().getName()+": " + note.id + "("+getMinNote()+"-"+getMaxNote()+ ") duraTicks=" + getFullLengthTicks() + " tick:"+startTick+"-"+endTick+" vol="+velocity+" TiesIsNull: "+(tiesFrom==null)+" "+(tiesTo == null)+" time: "+(getStartMicros()/1000000.0)+" to "+(getEndMicros()/1000000.0);
	}

	
}
