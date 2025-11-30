package com.digero.maestro.midi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * notes that have a big pitch range.
     * Samples the pitch curve at ~25ms intervals, selecting the dominant pitch for each segment.
     */
    public List<MidiNoteEvent> split() {
        List<MidiNoteEvent> splits = new ArrayList<>();

        // Filters out high-frequency noise but preserves slides/runs for tempo slow-down.
        long MIN_SEGMENT_MICROS = 25_000L;

        long startTick = getStartTick();
        long endTick = getEndTick();

        // Initialize state
        long segmentStartTick = startTick;
        long segmentStartMicros = getTempoCache().tickToMicros(segmentStartTick);

        // Iterate through the note's duration looking for split points
        // We use the bends map keys to find potential split points
        Long nextEventTick = bends.higherKey(segmentStartTick);

        while (segmentStartTick < endTick) {
            // Determine the end of the current candidate segment
            // We want to find the first event that pushes us OVER the limit
            long targetEndTick = endTick;

            // Scan forward until we find a tick that satisfies the minimum duration
            Long scanTick = nextEventTick;
            while (scanTick != null && scanTick < endTick) {
                long scanMicros = getTempoCache().tickToMicros(scanTick);
                if (scanMicros - segmentStartMicros >= MIN_SEGMENT_MICROS) {
                    targetEndTick = scanTick;
                    break;
                }
                scanTick = bends.higherKey(scanTick);
            }

            // Calculate the dominant pitch for the window [segmentStartTick, targetEndTick]
            int dominantBend = getDominantBend(segmentStartTick, targetEndTick);
            Note currNote = Note.fromId(note.id + dominantBend);

            if (currNote != null) { // Filter out-of-range notes
                // Optimization: Merge with previous if pitch is same
                if (!splits.isEmpty() && splits.getLast().note.id == currNote.id
                        && splits.getLast().getEndTick() == segmentStartTick) {
                    splits.getLast().setEndTick(targetEndTick);
                } else {
                    MidiNoteEvent segment = new MidiNoteEvent(currNote, velocity, segmentStartTick, targetEndTick, getTempoCache(), midiPan);
                    splits.add(segment);
                }
            } else {
                // TODO: can happen with the midi WonderousStories.mid
                // possible solution: allow negative note ids for bent midi notes
                // but its really a midi issue, so maybe its best we drop this entire bent note..
                return new ArrayList<>();
            }

            // Advance
            segmentStartTick = targetEndTick;
            segmentStartMicros = getTempoCache().tickToMicros(segmentStartTick);

            // Reset iterator for next segment
            if (scanTick != null) {
                nextEventTick = bends.higherKey(scanTick);
            } else {
                nextEventTick = null;
            }
        }

        return splits;
    }

    private int getDominantBend(long startTick, long endTick) {
        if (startTick >= endTick) {
            Entry<Long, Integer> entry = bends.floorEntry(startTick);
            return (entry != null) ? entry.getValue() : 0;
        }

        //linked hash map preserves adding order.
        //we want that cause we want to prioritize the first bend in case of equal durations.
        Map<Integer, Long> durationMap = new LinkedHashMap<>();
        long currTick = startTick;

        // Get initial bend at start
        Entry<Long, Integer> floor = bends.floorEntry(startTick);
        int currentBend = (floor != null) ? floor.getValue() : 0;

        // Iterate through changes inside the window
        Long nextChangeTick = bends.higherKey(currTick);

        while (currTick < endTick) {
            long segmentEndTick = (nextChangeTick != null && nextChangeTick < endTick) ? nextChangeTick : endTick;

            // Calculate duration (approximate based on ticks is risky if tempo changes,
            // but converting to micros every time is slow.
            // Let's stick to ticks for dominant calc if we assume constant tempo within limit,
            // OR use tempo cache if precision matters. Given limit is short, Ticks is likely fine for weighting).
            // Let's use Ticks for speed/simplicity, it weights by "musical time" which is arguably better.
            // Disregard, micros are better, what if it's a long slide..
            long durMicros = getTempoCache().tickToMicros(segmentEndTick) - getTempoCache().tickToMicros(currTick);

            durationMap.merge(currentBend, durMicros, Long::sum);

            currTick = segmentEndTick;
            if (currTick < endTick) {
                currentBend = bends.get(nextChangeTick);
                nextChangeTick = bends.higherKey(nextChangeTick);
            }
        }

        int bestBend = currentBend;
        long maxDurMicros = -1;

        for (Entry<Integer, Long> entry : durationMap.entrySet()) {
            if (entry.getValue() > maxDurMicros) {
                maxDurMicros = entry.getValue();
                bestBend = entry.getKey();
            }
        }
        return bestBend;
    }
}