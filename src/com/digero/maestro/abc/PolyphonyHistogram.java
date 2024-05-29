package com.digero.maestro.abc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.List;
import java.util.TreeMap;

import com.digero.common.abc.LotroInstrumentSampleDuration;
import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.util.Listener;
import com.digero.maestro.midi.AbcNoteEvent;

public class PolyphonyHistogram   {

	private static Map<AbcPart, TreeMap<Long, Integer>> histogramData = new HashMap<>();
	private static TreeMap<Long, Integer> sum = new TreeMap<>();// <micros,numberOfNotes>
	private static boolean dirty = false;
	private static int max = 0;
	public static boolean enabled = true;// set to true to enable this system, set to false to save cpu power.
	private static Listener<SequencerEvent> listener = new MyListener();

	public static void setSequencer(LotroSequencerWrapper abcSequencer) {
		abcSequencer.addChangeListener(listener);
	}
	
	static class MyListener implements Listener<SequencerEvent> {
		@Override
		public void onEvent(SequencerEvent e) {
			switch (e.getProperty()) {
				case TRACK_ACTIVE:
					dirty = true;
					break;
				case DRAG_POSITION:
				case IS_DRAGGING:
				case IS_LOADED:
				case IS_RUNNING:
				case LENGTH:
				case POSITION:
				case SEQUENCE:
				case TEMPO:
				default:
					break;
			}
		}
	}
	
	/**
	 * Called from AbcExporter.java
	 * 
	 * @param part
	 * @param events
	 * @throws IOException 
	 */
	public static void count(AbcPart part, List<AbcNoteEvent> events) throws IOException {
		if (!enabled) return;
		
		TreeMap<Long, Integer> partMap = new TreeMap<>();
		List<AbcNoteEvent> done = new ArrayList<>();
		for (AbcNoteEvent event : events) {
			if (event.note.id == Note.REST.id || done.contains(event)) {
				continue;
			}
			assert event.tiesFrom == null;
			ITempoCache tc = event.getTempoCache();
			QuantizedTimingInfo qtm = (QuantizedTimingInfo) tc;
			
			AbcNoteEvent check = event;
			while (check.tiesTo != null) {
				check = check.tiesTo;
				done.add(check);
			}
			long endTick = check.getEndTick();
			long start = qtm.tickToMicrosABC(event.getStartTick(), part);// delay is already in the start/end tick at this point 
			long end   = qtm.tickToMicrosABC(endTick, part);
			if (part.getInstrument().isSustainable(event.note.id)) {
				end += 200000L;// 200ms
				Double seconds = LotroInstrumentSampleDuration.getDura(part.getInstrument().friendlyName, event.note.id);
				if (seconds != null) {
					long dura = (long) (1000000L * seconds);
					long endMax = start + dura;
					end = Math.min(endMax, end);
				}				
			} else {
				Double seconds = LotroInstrumentSampleDuration.getDura(part.getInstrument().friendlyName, event.note.id);
				if (seconds == null) {
					System.err.println("Error: LotroInstrumentSampleDuration has no "+part.getInstrument().friendlyName+" with note "+event.note.id);
					seconds = 1.0d;
				}
				long dura = (long) (1000000L * seconds);
				end = start + dura;
			}
			if (end == start) continue;
			
			Integer oldStart = partMap.get(start);
			if (oldStart == null) {
				oldStart = 0;
			}
			oldStart += 1;
			partMap.put(start, oldStart);
			Integer oldEnd = partMap.get(end);
			if (oldEnd == null) {
				oldEnd = 0;
			}
			oldEnd -= 1;
			partMap.put(end, oldEnd);
			
			assert end - start > 0L;
		}
		histogramData.put(part, partMap);
		dirty = true;
	}
	
	/**
	 * Expensive method, so only run when needed.
	 * 
	 * @param song
	 */
	public static void sumUp(AbcSong song) {
		sum = new TreeMap<>();
		max = 0;
		Set<AbcPart> partSet = new HashSet<>(histogramData.keySet());
		Set<TreeMap<Long, Integer>> treeSet = new HashSet<TreeMap<Long, Integer>>();
		for (AbcPart part : partSet) {
			if (part.discarded) {
				histogramData.remove(part);
			} else if (part.isActive()){
				treeSet.add(histogramData.get(part));
			}
		}
		TreeMap<Long, Integer> songMap = new TreeMap<>();
		for (TreeMap<Long, Integer> partMap : treeSet) {
			Set<Entry<Long, Integer>> entrySet = partMap.entrySet();
			
			for (Entry<Long, Integer> entry : entrySet) {
				long key = entry.getKey();//micros
				int value = entry.getValue();//number of notes
				
				Integer oldValue = songMap.get(key);
				if (oldValue == null) {
					oldValue = 0;
				}
				oldValue += value;
				songMap.put(key, oldValue);
			}
		}
		int polyphony = 0;
		Set<Entry<Long, Integer>> entrySongSet = songMap.entrySet();
		for (Entry<Long, Integer> entry : entrySongSet) {
			polyphony += entry.getValue();
			sum.put(entry.getKey(), polyphony);
			if (polyphony > max) {
				max = polyphony;
			}
		}
		assert polyphony == 0;
	}
	
	/**
	 * Request the number of concurrently playing notes.
	 * Be sure to call sumUp first if is dirty.
	 * 
	 * @param microsecond Time of request
	 * @return Number of notes being played at this time
	 */
	public static int get(long microsecond) {
		Long key = sum.floorKey(microsecond);
		if (key == null) {
			return 0;
		}
		return sum.get(key);
	}
	
	/**
	 * Request the number of concurrently playing notes.
	 * Be sure to call sumUp first if is dirty.
	 * 
	 * @return Number of notes being played
	 */
	public static Set<Entry<Long, Integer>> getAll() {
		return sum.entrySet();
	}
	
	/**
	 * If the sum might need to be recalculated before result is reliable.
	 * 
	 * @return dirty boolean
	 */
	public static boolean isDirty() {
		return dirty;
	}

	/**
	 * Peak notes during song
	 * 
	 * @return peak
	 */
	public static int max() {
		return max;
	}

	public static void setClean() {
		dirty = false;
	}

	
}