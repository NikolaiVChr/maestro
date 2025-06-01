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

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentSampleDuration;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.maestro.midi.AbcNoteEvent;
import com.digero.maestro.midi.Chord;

public class PolyphonyHistogram   {

	private static Map<AbcPart, TreeMap<Long, Pair<Long,Integer>>> histogramData = new HashMap<>();
	private static TreeMap<Long, Pair<Long,Integer>> sum = new TreeMap<>();// <micros,numberOfNotes>
	private static boolean dirty = false;
	private static int max = 0;
	public static boolean enabled = true;// set to true to enable this system, set to false to save cpu power.
	private static Listener<SequencerEvent> listener = new MyListener();
	private static LotroSequencerWrapper abcSeq = null;
	
	public static volatile int successes = 0;//debug for abctools (organic1=118 organic2=44) approx factor 3

	public static void setSequencer(LotroSequencerWrapper abcSequencer) {
		if (abcSeq != null) abcSeq.removeChangeListener(listener);
		if (abcSequencer != null) abcSequencer.addChangeListener(listener);
		abcSeq = abcSequencer;
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
	 * @param chords
	 * @throws IOException 
	 */
	public static void count(AbcPart part, List<Chord> chords, boolean organic, QuantizedTimingInfo qtm) throws IOException {
		if (!enabled) return;
		
		TreeMap<Long, Pair<Long,Integer>> partMap = new TreeMap<>();
		List<AbcNoteEvent> done = new ArrayList<>();
		for (Chord chord : chords) {
			for (AbcNoteEvent event : chord.getNotes()) {
				if (event.note.id == Note.REST.id || done.contains(event)) {
					continue;
				}
				assert event.tiesFrom == null;
				
				AbcNoteEvent check = event;
				while (check.tiesTo != null) {
					// The reason we do this is that non-sustained instr.
					// might have ties-to which often should not count for anything
					// as the sample is short.
					check = check.tiesTo;
					done.add(check);
				}
				long endTick = check.getEndTick();
				long startMicros; 
				long endMicros;
				if (organic) {
					startMicros = qtm.tickToMicrosABCOrganic(event.getStartTick());// delay is already in the start/end tick at this point 
					endMicros   = qtm.tickToMicrosABCOrganic(endTick);
				} else {
					startMicros = qtm.tickToMicrosABC(event.getStartTick(), part);// delay is already in the start/end tick at this point 
					endMicros   = qtm.tickToMicrosABC(endTick, part);
				}
				if (part.getInstrument().isSustainable(event.note.id)) {
					endMicros += 200000L;// 200ms
					Long duraMicros = LotroInstrumentSampleDuration.getDura(part.getInstrument().friendlyName, event.note.id);
					if (duraMicros != null) {
						long endMax = startMicros + duraMicros;
						endMicros = Math.min(endMax, endMicros);
					}
					if (organic) {
						endTick   = qtm.microsToTickABCOrganic(endMicros);
					} else {
						endTick   = qtm.microsToTickABC(endMicros);
					}
				} else {
					int pitch = event.note.id;
					if (part.getInstrument() == LotroInstrument.BASIC_COWBELL || part.getInstrument() == LotroInstrument.MOOR_COWBELL) {
						pitch = AbcConstants.COWBELL_NOTE_ID;
					}
					Long duraMicros = LotroInstrumentSampleDuration.getDura(part.getInstrument().friendlyName, pitch);
					if (duraMicros == null) {
						System.err.println("Error: LotroInstrumentSampleDuration has no "+part.getInstrument().friendlyName+" with note "+event.note.id);
						duraMicros = AbcConstants.ONE_SECOND_MICROS;
					}
					endMicros = startMicros + duraMicros;
					if (organic) {
						endTick   = qtm.microsToTickABCOrganic(endMicros);
					} else {
						endTick   = qtm.microsToTickABC(endMicros);
					}
				}
				if (endMicros == startMicros) continue;
								
				Pair<Long,Integer> oldStart = partMap.get(startMicros);
				if (oldStart == null) {
					oldStart = new Pair<>(event.getStartTick(), 0);
				}
				oldStart.second += 1;
				partMap.put(startMicros, oldStart);
				
				Pair<Long,Integer> oldEnd = partMap.get(endMicros);
				if (oldEnd == null) {
					oldEnd = new Pair<>(endTick, 0);
				}
				oldEnd.second -= 1;
				partMap.put(endMicros, oldEnd);
				
				assert endMicros - startMicros > 0L;
			}
		}
		histogramData.put(part, partMap);
		dirty = true;
	}
	
	public static void clearAll() {
		histogramData.clear();
	}
	
	/**
	 * 
	 * Debug method to check whether inserting rests in chord to make them shorter and allow
	 * for more part polyphony actually has an effect. Will print to sysout if more than 6 notes
	 * playing in part.
	 * 
	 * This method does NOT take decay in consideration.
	 * @return 
	 *  
	 */
	public static int maxPolyInPart(AbcPart part, List<Chord> chords, boolean organic, QuantizedTimingInfo qtm) throws IOException {
	
		TreeMap<Long, Pair<Long,Integer>> partMap = new TreeMap<>();
		List<AbcNoteEvent> done = new ArrayList<>();
		for (Chord chord : chords) {
			for (AbcNoteEvent event : chord.getNotes()) {
				if (event.note.id == Note.REST.id || done.contains(event)) {
					continue;
				}
				assert event.tiesFrom == null;
				
				AbcNoteEvent check = event;
				while (check.tiesTo != null) {
					// The reason we do this is that non-sustained instr.
					// might have ties-to which often should not count for anything
					// as the sample is short.
					check = check.tiesTo;
					done.add(check);
				}
				long endTick = check.getEndTick();
				long startMicros; 
				long endMicros;
				if (organic) {
					startMicros = qtm.tickToMicrosABCOrganic(event.getStartTick());// delay is already in the start/end tick at this point 
					endMicros   = qtm.tickToMicrosABCOrganic(endTick);
				} else {
					startMicros = qtm.tickToMicrosABC(event.getStartTick(), part);// delay is already in the start/end tick at this point 
					endMicros   = qtm.tickToMicrosABC(endTick, part);
				}
				if (part.getInstrument().isSustainable(event.note.id)) {
					endMicros += 0;//200000L;// 200ms
					Long duraMicros = LotroInstrumentSampleDuration.getDura(part.getInstrument().friendlyName, event.note.id);
					if (duraMicros != null) {
						long endMax = startMicros + duraMicros;
						endMicros = Math.min(endMax, endMicros);
					}
					if (organic) {
						endTick   = qtm.microsToTickABCOrganic(endMicros);
					} else {
						endTick   = qtm.microsToTickABC(endMicros);
					}
				} else {
					int pitch = event.note.id;
					if (part.getInstrument() == LotroInstrument.BASIC_COWBELL || part.getInstrument() == LotroInstrument.MOOR_COWBELL) {
						pitch = AbcConstants.COWBELL_NOTE_ID;
					}
					/*
					Long duraMicros = LotroInstrumentSampleDuration.getDura(part.getInstrument().friendlyName, pitch);
					if (duraMicros == null) {
						System.err.println("Error: LotroInstrumentSampleDuration has no "+part.getInstrument().friendlyName+" with note "+event.note.id);
						duraMicros = AbcConstants.ONE_SECOND_MICROS;
					}
					long endMax = startMicros + duraMicros;
					endMicros = Math.min(endMax, endMicros);
					*/
					if (organic) {
						endTick   = qtm.microsToTickABCOrganic(endMicros);
					} else {
						endTick   = qtm.microsToTickABC(endMicros);
					}
				}
				if (endMicros == startMicros) continue;
								
				Pair<Long,Integer> oldStart = partMap.get(startMicros);
				if (oldStart == null) {
					oldStart = new Pair<>(event.getStartTick(), 0);
				}
				oldStart.second += 1;
				partMap.put(startMicros, oldStart);
				
				Pair<Long,Integer> oldEnd = partMap.get(endMicros);
				if (oldEnd == null) {
					oldEnd = new Pair<>(endTick, 0);
				}
				oldEnd.second -= 1;
				partMap.put(endMicros, oldEnd);
				
				assert endMicros - startMicros > 0L;
			}
		}
			
		TreeMap<Long, Pair<Long,Integer>> songMap = new TreeMap<>();

		Set<Entry<Long, Pair<Long,Integer>>> entrySet = partMap.entrySet();
		long lastTick = -1L;
		for (Entry<Long, Pair<Long,Integer>> entry : entrySet) {
			long micros = entry.getKey();//micros
			int noteStarts = entry.getValue().second;//number of notes
			long tick = entry.getValue().first;
			//assert tick >= lastTick:"HISTO OOPS 3";
			lastTick = tick;
					
			Pair<Long,Integer> oldValue = songMap.get(micros);
			if (oldValue == null) {
				oldValue = new Pair<>(tick, 0);
			}
			oldValue.second += noteStarts;
			songMap.put(micros, oldValue);
		}
			
		int polyphony = 0;
		Set<Entry<Long, Pair<Long,Integer>>> entrySongSet = songMap.entrySet();
		lastTick = -1L;
		long lastMicro = -1L;
		int maximum = 0;
		for (Entry<Long, Pair<Long,Integer>> entry : entrySongSet) {
			// this assert can happen due to convertin back and forth is not sure to output original tick, rounding I reckon
			//assert entry.getValue().first >= lastTick:" CAN HAPPEN at "+Util.formatDuration(entry.getKey())+"="+entry.getValue().first+"  "+Util.formatDuration(lastMicro)+"="+lastTick;
			lastTick = entry.getValue().first;
			lastMicro = entry.getKey();
			polyphony += entry.getValue().second;
			if (polyphony > maximum) {
				maximum = polyphony;
			}
		}
		assert polyphony == 0;
		if (maximum > 6) {
			//System.out.println(" ++++ "+part.getAbcSong().getTitle()+" ("+part.getTitle()+"): "+maximum+" poly");
			successes++;
		}
		return maximum;
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
		List<TreeMap<Long, Pair<Long,Integer>>> treeList = new ArrayList<TreeMap<Long, Pair<Long,Integer>>>();
		for (AbcPart part : partSet) {
			if (part.discarded) {
				histogramData.remove(part);
			} else if (part.isActive()){
				treeList.add(histogramData.get(part));
			}
		}
		TreeMap<Long, Pair<Long,Integer>> songMap = new TreeMap<>();
		for (TreeMap<Long, Pair<Long,Integer>> partMap : treeList) {
			Set<Entry<Long, Pair<Long,Integer>>> entrySet = partMap.entrySet();
			long lastTick = -1L;
			for (Entry<Long, Pair<Long,Integer>> entry : entrySet) {
				long micros = entry.getKey();//micros
				int noteStarts = entry.getValue().second;//number of notes
				long tick = entry.getValue().first;
				//assert tick >= lastTick:"HISTO OOPS 3";
				lastTick = tick;
						
				Pair<Long,Integer> oldValue = songMap.get(micros);
				if (oldValue == null) {
					oldValue = new Pair<>(tick, 0);
				}
				oldValue.second += noteStarts;
				songMap.put(micros, oldValue);
			}
		}
		int polyphony = 0;
		Set<Entry<Long, Pair<Long,Integer>>> entrySongSet = songMap.entrySet();
		long lastTick = -1L;
		long lastMicro = -1L;
		for (Entry<Long, Pair<Long,Integer>> entry : entrySongSet) {
			// thsi assert can happen due to convertin back and forth is not sure to output original tick, rounding I reckon
			//assert entry.getValue().first >= lastTick:" CAN HAPPEN at "+Util.formatDuration(entry.getKey())+"="+entry.getValue().first+"  "+Util.formatDuration(lastMicro)+"="+lastTick;
			lastTick = entry.getValue().first;
			lastMicro = entry.getKey();
			polyphony += entry.getValue().second;
			sum.put(entry.getKey(), new Pair<>(entry.getValue().first, polyphony));
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
		return sum.get(key).second;
	}
	
	/**
	 * Request the number of concurrently playing notes.
	 * Be sure to call sumUp first if is dirty.
	 * 
	 * @return Number of notes being played
	 */
	public static Set<Entry<Long, Pair<Long,Integer>>> getAll() {
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