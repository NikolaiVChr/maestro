package com.digero.maestro.abc;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentSampleDuration;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.util.Listener;
import com.digero.common.util.Pair;
import com.digero.common.util.Triple;
import com.digero.maestro.midi.AbcNoteEvent;
import com.digero.maestro.midi.Chord;

public class PolyphonyHistogram   {

    /** partID -> abcMicros -> (tick, numberOfNotes) */
	private final Map<Long, TreeMap<Long, Triple<Long,Integer,Long>>> histogramData = new HashMap<>();
    /** abcMicros -> tick,numberOfNotes */
	private TreeMap<Long, Pair<Long,Integer>> sum = new TreeMap<>();
    /** partID -> midiMicros,numberOfNotes */
    private Map<Long, NavigableMap<Long, Integer>> partSum = new HashMap<>();

	private boolean dirty = false;
	private int max = 0;
    private double average = 0;
    private int maxAll = 0;

    private long peakTick = 0L;
	public static boolean enabled = true;// set to true to enable this system, set to false to save cpu power.
	private final Listener<SequencerEvent> listener = new MyListener();
	private LotroSequencerWrapper abcSeq = null;
	
	public static volatile AtomicInteger successes = new AtomicInteger(0);//debug for abctools (organic1=118 organic2=44) approx factor 3

	public void setSequencer(LotroSequencerWrapper abcSequencer) {
		if (abcSeq != null) abcSeq.removeChangeListener(listener);
		if (abcSequencer != null) abcSequencer.addChangeListener(listener);
		abcSeq = abcSequencer;
	}

    public String getStats() {
        String str = "";
        if (enabled) {
            str += "Max polyphony in active parts = " + max();
            str += "\nMax export polyphony = " + maxAll();
            str += "\nAverage export polyphony = %.1f".formatted(average).replace(",", ".") + "\n";
        }
        return str;
    }

    class MyListener implements Listener<SequencerEvent> {
		@Override
		public void onEvent(SequencerEvent e) {
			switch (e.getProperty()) {
				case TRACK_ACTIVE:
					setDirty();
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
     */
	public void count(AbcPart part, List<Chord> chords, boolean organic, QuantizedTimingInfo qtm) throws IOException {
		if (!enabled) return;
		
		TreeMap<Long, Triple<Long,Integer,Long>> partMap = new TreeMap<>();
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
                }
                if (organic) {
                    endTick   = qtm.microsToTickABCOrganic(endMicros);
                } else {
                    endTick   = qtm.microsToTickABC(endMicros);
                }
                if (endMicros == startMicros) continue;

                Triple<Long,Integer,Long> oldStart = partMap.get(startMicros);
				if (oldStart == null) {
					oldStart = new Triple<>(event.getStartTick(), 0, part.getAbcSong().getSequenceInfo().getDataCache().tickToMicros(event.getStartTick()));
				}
				oldStart.second += 1;
				partMap.put(startMicros, oldStart);
				
				Triple<Long,Integer,Long> oldEnd = partMap.get(endMicros);
				if (oldEnd == null) {
					oldEnd = new Triple<>(endTick, 0, part.getAbcSong().getSequenceInfo().getDataCache().tickToMicros(endTick));
				}
				oldEnd.second -= 1;
				partMap.put(endMicros, oldEnd);
				
				assert endMicros - startMicros > 0L;
			}
		}
		histogramData.put(part.uniqueID, partMap);
		dirty = true;
	}
	
	public void clearAll() {
		histogramData.clear();
	}
	
	/**
	 * 
	 * Debug method to check whether inserting rests in chord to make them shorter and allow
	 * for more part polyphony actually has an effect. Will print to sysout if more than 6 notes
	 * playing in part.
	 * 
	 * This method does NOT take decay in consideration.
     *
	 */
	public int maxPolyInPart(AbcPart part, List<Chord> chords, boolean organic, QuantizedTimingInfo qtm) throws IOException {
	
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
                }
                if (organic) {
                    endTick   = qtm.microsToTickABCOrganic(endMicros);
                } else {
                    endTick   = qtm.microsToTickABC(endMicros);
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
			
		int polyphony = 0;
		Set<Entry<Long, Pair<Long,Integer>>> entrySongSet = partMap.entrySet();
		long lastTick = -1L;
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
            successes.incrementAndGet();
		}
		return maximum;
	}
	
	/**
	 * Expensive method, so only run when needed.
	 *
     */
	public void sumUp(AbcSong song) {
		sum = new TreeMap<>();
		max = 0;
        average = 0.0d;
        maxAll = 0;
        peakTick = 0L;
		Set<Long> partSet = new HashSet<>(histogramData.keySet());
		List<TreeMap<Long, Triple<Long,Integer,Long>>> treeList = new ArrayList<>();
        List<TreeMap<Long, Triple<Long,Integer,Long>>> treeListMuted = new ArrayList<>();
		for (Long uniqueID : partSet) {
            AbcPart part = song.getPartFromID(uniqueID);
			if (part == null || part.discarded) {
				histogramData.remove(uniqueID);
			} else if (part.isActive()){
				treeList.add(histogramData.get(uniqueID));
			} else {
                treeListMuted.add(histogramData.get(uniqueID));
            }
		}
		TreeMap<Long, Pair<Long,Integer>> songMap = new TreeMap<>();
		for (TreeMap<Long, Triple<Long,Integer,Long>> partMap : treeList) {
			Set<Entry<Long, Triple<Long, Integer, Long>>> entrySet = partMap.entrySet();
			long lastTick = -1L;
			for (Entry<Long, Triple<Long, Integer, Long>> entry : entrySet) {
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
                peakTick = entry.getValue().first;
			}
		}
		assert polyphony == 0;

        // now add the muted parts
        for (TreeMap<Long, Triple<Long,Integer,Long>> partMap : treeListMuted) {
            Set<Entry<Long, Triple<Long, Integer, Long>>> entrySet = partMap.entrySet();
            lastTick = -1L;
            for (Entry<Long, Triple<Long, Integer, Long>> entry : entrySet) {
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
        entrySongSet = songMap.entrySet();
        lastTick = -1L;
        lastMicro = -1L;
        long sumMicros = 0L;
        for (Entry<Long, Pair<Long,Integer>> entry : entrySongSet) {
            // this assert can happen due to converting back and forth is not sure to output original tick, rounding I reckon
            //assert entry.getValue().first >= lastTick:" CAN HAPPEN at "+Util.formatDuration(entry.getKey())+"="+entry.getValue().first+"  "+Util.formatDuration(lastMicro)+"="+lastTick;
            long lastMicroSection = entry.getKey() - lastMicro;

            if (polyphony > 0 && lastMicro != -1L) {
                sumMicros += lastMicroSection;
                average += (double) (polyphony * lastMicroSection);
            }

            lastTick = entry.getValue().first;
            lastMicro = entry.getKey();
            polyphony += entry.getValue().second;
            if (polyphony > maxAll) {
                maxAll = polyphony;
                peakTick = entry.getValue().first;
            }
        }
        average = average / sumMicros;

        //part specific sum:
        partSum = new HashMap<>();// partID -> tick, part-polyphony, midi-micros

        for (Entry<Long, TreeMap<Long, Triple<Long, Integer,Long>>> entry : histogramData.entrySet()) {
            long partID = entry.getKey();
            NavigableMap<Long, Integer> partPolyMap = new TreeMap<>();
            partSum.put(partID, partPolyMap);
            polyphony = 0;
            lastTick = -1L;
            lastMicro = -1L;
            for (Entry<Long, Triple<Long, Integer,Long>> a: entry.getValue().entrySet()) {
                long tick = a.getValue().first;
                long microsABC = a.getKey();
                long microsMIDI = a.getValue().third;
                int noteStarts = a.getValue().second;

                polyphony += noteStarts;
                partPolyMap.put(microsMIDI, polyphony);

                lastTick = tick;
                lastMicro = microsABC;
            }
        }
	}
	
	/**
	 * Request the number of concurrently playing notes.
	 * Be sure to call sumUp first if is dirty.
	 * 
	 * @param microsecond Time of request in ABC time
	 * @return Number of notes being played at this time
	 */
	public int get(long microsecond) {
        if (!enabled) return 0;
		Long key = sum.floorKey(microsecond);
		if (key == null) {
			return 0;
		}
		return sum.get(key).second;
	}

    /**
     * Request the number of concurrently playing notes in a specific part.
     * Be sure to call sumUp first if is dirty.
     *
     * @param micros Micros of request in orig midi time
     * @return Number of notes being played at this time
     */
    public int get(long micros, AbcPart part) {
        if (!enabled) return 0;

        NavigableMap<Long, Integer> key = partSum.get(part.uniqueID);
        if (key == null) {
            return 0;
        }

        Entry<Long, Integer> entry = key.floorEntry(micros);
        if (entry == null) {
            return 0;
        }

        return entry.getValue();
    }
	
	/**
	 * Request the number of concurrently playing notes.
	 * Be sure to call sumUp first if is dirty.
	 * 
	 * @return Set with Number of notes being played at specific micros
	 */
	public Set<Entry<Long, Pair<Long,Integer>>> getAll() {
        if (!enabled) return new HashSet<>();
		return sum.entrySet();
	}
	
	/**
	 * If the sum might need to be recalculated before result is reliable.
	 * 
	 * @return dirty boolean
	 */
	public boolean isDirty() {
		return dirty;
	}

	/**
	 * Peak notes during song
	 * 
	 * @return peak
	 */
	public int max() {
        if (!enabled) return 0;
		return max;
	}

	public void setClean() {
		dirty = false;
	}

    public void setDirty() {
        dirty = true;
    }

    /**
     * Peak notes during song
     * ignores any mutes/soloed parts
     */
    public int maxAll() {
        if (!enabled) return 0;
        return maxAll;
    }

    public long getPeakTick() {
        if (!enabled) return 0L;
        return peakTick;
    }
}