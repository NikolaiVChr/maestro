package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.Note;

public class BentMidiNoteEvent extends MidiNoteEvent {

	private int cacheMin = -1;// These fields will only be used after all bends have been added
	private int cacheMax = -1;// So its fine to cache them here.

	public NavigableMap<Long, Integer> bends = new TreeMap<>();// tick -> relative seminote bend

	public BentMidiNoteEvent(Note note, int velocity, long startTick, long endTick, ITempoCache tempoCache, int pan) {
		super(note, velocity, startTick, endTick, tempoCache, pan);
	}

	/**
	 * 
	 * @param tick absolute midi tick
	 * @param bend seminote relative bend
	 */
	public void addBend(long tick, int bend) {
		bends.put(tick, bend);
		cacheMin = -1;
		cacheMax = -1;
	}

	public void setBends(NavigableMap<Long, Integer> bends) {
		this.bends = bends;
		cacheMin = -1;
		cacheMax = -1;
	}

	/**
	 * 
	 * @param tick Absolute tick in source midi
	 * @return relative semi-step bend
	 */
	public Integer getBend(long tick) {
		Entry<Long, Integer> entry = bends.floorEntry(tick);
		if (entry == null)
			return null;
		return entry.getValue();
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

	/**
	 * Split this bent note into smaller note events. Will not take actual grid into consideration, so is only for bent
	 * notes that has a big pitch range.
	 * 
	 * @return list of MidiNoteEvent
	 */
	public List<MidiNoteEvent> split() {
		List<MidiNoteEvent> splits = new ArrayList<>();
		Entry<Long, Integer> entry = bends.floorEntry(getStartTick());
		Note currNote = Note.fromId(note.id + entry.getValue());
		if (currNote == null)
			return new ArrayList<>();// Too bad, lets cancel
		MidiNoteEvent curr = new MidiNoteEvent(currNote, velocity, getStartTick(), getEndTick(), getTempoCache(), midiPan);
		for (Long tick : bends.keySet()) {
			if (tick == getStartTick())
				continue;
			curr.setEndTick(tick);
			splits.add(curr);
			currNote = Note.fromId(note.id + bends.get(tick));
			if (currNote == null)
				return new ArrayList<>();// Too bad, lets cancel
			curr = new MidiNoteEvent(currNote, velocity, tick, getEndTick(), getTempoCache(), midiPan);
		}
		splits.add(curr);
		return splits;
	}
}