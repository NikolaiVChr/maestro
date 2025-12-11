package com.digero.maestro.abc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentSampleDuration;
import com.digero.common.midi.LotroSequencerWrapper;
import com.digero.common.midi.Note;
import com.digero.common.midi.SequencerEvent;
import com.digero.common.util.Listener;
import com.digero.maestro.midi.AbcNoteEvent;
import com.digero.maestro.midi.Chord;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.view.MiscSettings;

public class DissonanceDetector {
    private final MiscSettings prefs;
    List<AbcNoteEvent> allNotes = new ArrayList<>();
    NavigableMap<Long, DissonanceEvent> results = new TreeMap<>();
    int peakTotal = 0;
    long peakTick = 0;
    boolean dirty = true;
    private final Listener<SequencerEvent> listener = new DissonanceDetector.MyListener();
    private LotroSequencerWrapper abcSeq = null;
    private final Map<Long, List<AbcNoteEvent>> dissonanceData = new HashMap<>();
    private QuantizedTimingInfo qtm = null;

    public DissonanceDetector(MiscSettings dissonancePrefs) {
        this.prefs = dissonancePrefs;
    }

    public void submitPart(AbcPart part, List<Chord> chords) {
        List<AbcNoteEvent> partData = new ArrayList<>();
        if (part.getInstrument().isPercussion) {
            dissonanceData.put(part.uniqueID, partData);
            return;
        }

        if (part.getEnabledTrackCount() == 0 || !part.isActive()) {
            dissonanceData.put(part.uniqueID, partData);
            return;
        }

        SequenceDataCache cache = part.getAbcSong().getSequenceInfo().getDataCache();

        for (Chord chord : chords) {
            for (AbcNoteEvent evt : chord.getNotes()) {
                if (evt.note == Note.REST || evt.tiesFrom != null) continue;
                if (part.isStudentPart() && evt.note.id < LotroInstrument.STUDENT_CHROMATIC_LOWEST.id) continue;
                Note note = Note.fromId(evt.note.id + part.getInstrument().octaveDelta * 12);
                if (note != null) {
                    long noteEnd = evt.getTieEnd().getEndTick();
                    if (!part.getInstrument().isSustainable(evt.note.id)) {
                        long dura = 500_000L;
                        try {
                            dura = LotroInstrumentSampleDuration.getDura(part.getInstrument().friendlyName, evt.note.id);
                        } catch (Throwable ignore) {
                        }
                        dura /= 2L;// they decay fairly fast, no reason to check the almost silent tail.
                        if (qtm == null) {
                            qtm = (QuantizedTimingInfo) evt.getTempoCache();
                        }
                        if (qtm != null) dura = qtm.multiplyByExportTempoFactor(dura);
                        noteEnd = cache.microsToTick(cache.tickToMicros(evt.getStartTick()) + dura);
                    }
                    partData.add(new AbcNoteEvent(note, evt.getVelocity(), evt.getStartTick(), noteEnd, null, null));
                }
            }
        }
        dissonanceData.put(part.uniqueID, partData);
    }

    public void analyze(AbcSong song) {
        if (dirty) {
            allNotes.clear();
            for (Map.Entry<Long, List<AbcNoteEvent>> partData : dissonanceData.entrySet()) {
                AbcPart part = song.getPartFromID(partData.getKey());
                if (part == null) continue;
                if (part.getEnabledTrackCount() == 0 || !part.isActive()) continue;
                allNotes.addAll(partData.getValue());
            }
        }
        // Convert notes to a list of Start/End events for the sweep-line
        List<SweepEvent> events = new ArrayList<>();
        for (AbcNoteEvent note : allNotes) {
            events.add(new SweepEvent(note.getStartTick(), true, note));
            events.add(new SweepEvent(note.getEndTick(), false, note));
        }

        // Sort sweep events by time
        // If times are equal, process end events before start events
        events.sort((e1, e2) -> {
            int cmp = Long.compare(e1.tick, e2.tick);
            if (cmp != 0) return cmp;
            if (e1.isStart != e2.isStart) {
                return e1.isStart ? 1 : -1; // End comes before start
            }
            return 0;
        });

        // Sweep the song and calculate dissonance at every sweep time
        results = new TreeMap<>();
        List<AbcNoteEvent> activeNotes = new ArrayList<>();
        long prevTick = -1;
        if (!events.isEmpty()) {
            prevTick = events.getFirst().tick;
        }

        for (SweepEvent sweepEvent : events) {
            long currentTick = sweepEvent.tick;

            // If time has advanced, the set of active notes has not changed in the interval
            if (currentTick > prevTick) {
                calculateAndStoreDissonance(activeNotes, prevTick, currentTick, song);
            }

            if (sweepEvent.isStart) {
                activeNotes.add(sweepEvent.note);
            } else {
                activeNotes.remove(sweepEvent.note);
            }

            prevTick = currentTick;
        }
        results.computeIfAbsent(0L, k -> new DissonanceEvent());
        if (results.size() > 1) results.put(results.lastKey()+1L, new DissonanceEvent());

        peakTotal = 0;
        peakTick = 0L;
        for (Map.Entry<Long, DissonanceEvent> entry : results.entrySet()) {
            if (entry.getValue().getTotalScore() > peakTotal) {
                peakTotal = entry.getValue().getTotalScore();
                peakTick = entry.getKey();
            }
        }
        setClean();
    }

    private void calculateAndStoreDissonance(List<AbcNoteEvent> notes, long tick, long tickEnd, AbcSong song) {
        SequenceDataCache cache = song.getSequenceInfo().getDataCache();
        if (notes.size() < 2) {
            // No dissonance possible with 0 or 1 note
            if (results.lowerEntry(tick) != null) {
                DissonanceEvent last = results.lowerEntry(tick).getValue();
                // Check if previous had score, and ensure we haven't already added a zero here
                if (last.isDissonant() && !results.containsKey(tick)) {
                    DissonanceEvent zero = new DissonanceEvent();
                    zero.tick = tick;
                    results.put(tick, zero); // Direct put is safe here as key doesn't exist
                }
            }
            return;
        }

        DissonanceEvent dissonanceEvent = new DissonanceEvent();
        dissonanceEvent.tick = tick;
        dissonanceEvent.totalActiveNotes = notes.size();

        boolean hasDissonance = false;

        for (int i = 0; i < notes.size(); i++) {
            for (int j = i + 1; j < notes.size(); j++) {
                if (prefs.excludeShortestNotes) {
                    // Calculate how long these two notes
                    // overlap in orig midi time.
                    long startA = cache.tickToMicros(notes.get(i).getStartTick());
                    long endA = cache.tickToMicros(notes.get(i).getEndTick());
                    long startB = cache.tickToMicros(notes.get(j).getStartTick());
                    long endB = cache.tickToMicros(notes.get(j).getEndTick());

                    // because we use cache to calc micros, tune-editor tempo changes
                    // are not factored in, can live with that.

                    long overlapStart = Math.max(startA, startB);
                    long overlapEnd = Math.min(endA, endB);
                    long trueDuration = overlapEnd - overlapStart;
                    if (qtm != null) trueDuration = qtm.divideByExportTempoFactor(trueDuration);
                    if (trueDuration <= 62_000L) continue;
                }

                int id1 = notes.get(i).note.id;
                int id2 = notes.get(j).note.id;
                int interval = Math.abs(id1 - id2);

                if (interval == 0 || interval > 13) continue; // Unison or far enough apart to not sound jarring

                int semitones = interval % 12;

                if (semitones == 1) {
                    dissonanceEvent.minorSecondCount++;
                    hasDissonance = true;
                } else if (semitones == 6) {
                    dissonanceEvent.tritoneCount++;
                    hasDissonance = true;
                } else if (semitones == 11) {
                    dissonanceEvent.majorSeventhCount++;
                    hasDissonance = true;
                } else if (semitones == 2) {
                    dissonanceEvent.majorSecondCount++;
                    hasDissonance = true;
                } else if (semitones == 10) {
                    dissonanceEvent.minorSeventhCount++;
                    hasDissonance = true;
                }
            }
        }

        if (hasDissonance) {
            addOrMergeEvent(tick, dissonanceEvent);
        } else if (results.floorEntry(tick) != null) {
            // Record return to consonance
            if (results.lowerEntry(tick) != null) {
                DissonanceEvent last = results.lowerEntry(tick).getValue();
                if (last.isDissonant()) {
                    // Only put a zero if the slot is empty.
                    results.putIfAbsent(tick, dissonanceEvent);
                }
            }
        }
    }

    public long getPeakTick(AbcSong song) {
        if (dirty) analyze(song);
        return peakTick;
    }

    public int max(AbcSong song) {
        if (dirty) analyze(song);
        return peakTotal;
    }

    public DissonanceEvent get(long tick, AbcSong song) {
        if (dirty) analyze(song);
        Map.Entry<Long, DissonanceEvent> entry = results.floorEntry(tick);
        if (entry != null) return entry.getValue();
        return new DissonanceEvent();
    }

    private static class SweepEvent {
        long tick;
        boolean isStart;
        AbcNoteEvent note;

        public SweepEvent(long tick, boolean isStart, AbcNoteEvent note) {
            this.tick = tick;
            this.isStart = isStart;
            this.note = note;
        }
    }

    public NavigableMap<Long, DissonanceEvent> getResults(AbcSong song) {
        if (dirty) analyze(song);
        return results;
    }

    private void addOrMergeEvent(long tick, DissonanceEvent newEvent) {
        DissonanceEvent existing = results.get(tick);

        if (existing == null) {
            results.put(tick, newEvent);
        } else {
            assert false:"Should not have happened";
            existing.minorSecondCount += newEvent.minorSecondCount;
            //existing.minorSecondCount13 += newEvent.minorSecondCount13;
            existing.majorSecondCount += newEvent.majorSecondCount;
            //existing.majorSecondCount14 += newEvent.majorSecondCount14;
            existing.tritoneCount     += newEvent.tritoneCount;
            existing.majorSeventhCount += newEvent.majorSeventhCount;
            existing.minorSeventhCount += newEvent.minorSeventhCount;
        }
    }

    public class DissonanceEvent {
        public long tick;

        public int totalActiveNotes = 0;

        public int minorSecondCount;
        //public int minorSecondCount13;
        public int majorSecondCount;
        //public int majorSecondCount14;
        public int tritoneCount;
        public int majorSeventhCount;
        public int minorSeventhCount;

        private Integer cache = null;

        public boolean isDissonant() {
            return minorSecondCount > 0 || tritoneCount > 0 || majorSeventhCount > 0 || minorSeventhCount > 0 || majorSecondCount > 0;// || majorSecondCount14 > 0 || minorSecondCount13 > 0;
        }

        public int getTotalScore() {
            if (cache != null) return cache;
            if (prefs == null) return 0;
            int count = prefs.min2factor * minorSecondCount + prefs.maj2factor * majorSecondCount + prefs.trifactor * tritoneCount + prefs.maj7factor * majorSeventhCount + prefs.min7factor * minorSeventhCount;
            int penalty = 0;
            if (minorSecondCount > prefs.min2threshold) {
                penalty += prefs.min2penalty * (minorSecondCount - prefs.min2threshold);
            }
            if (majorSecondCount > prefs.maj2threshold) {
                penalty += prefs.maj2penalty * (majorSecondCount - prefs.maj2threshold);
            }
            cache = count + penalty;
            return cache;
        }

        private int getTotalCollisions() {
            return minorSecondCount + majorSecondCount + tritoneCount + majorSeventhCount + minorSeventhCount;
        }

        public String getTooltipHtml() {
            StringBuilder tooltip = new StringBuilder();
            tooltip.append("<html>Dissonance:");
            tooltip.append("<br>")
                    .append("Minor second")
                    .append(":&nbsp;&nbsp;")
                    .append(minorSecondCount);
            tooltip.append("<br>")
                    .append("Major seventh")
                    .append(":&nbsp;&nbsp;")
                    .append(majorSeventhCount);
            tooltip.append("<br>")
                    .append("Tritone")
                    .append(":&nbsp;&nbsp;")
                    .append(tritoneCount);
            tooltip.append("<br>")
                    .append("Major second")
                    .append(":&nbsp;&nbsp;")
                    .append(majorSecondCount);
            tooltip.append("<br>")
                    .append("Minor seventh")
                    .append(":&nbsp;&nbsp;")
                    .append(minorSeventhCount);

            tooltip.append("<br>Total:&nbsp;&nbsp;");
            tooltip.append(getTotalCollisions());
            tooltip.append("<br><br>Value:&nbsp;&nbsp;");
            tooltip.append(getTotalScore());
            tooltip.append("</html>");
            return tooltip.toString();
        }
    }

    public void setSequencer(LotroSequencerWrapper abcSequencer) {
        if (abcSeq != null) abcSeq.removeChangeListener(listener);
        if (abcSequencer != null) abcSequencer.addChangeListener(listener);
        abcSeq = abcSequencer;
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
     * If might need to be recalculated before results is reliable.
     *
     * @return dirty boolean
     */
    public boolean isDirty() {
        return dirty;
    }

    public void setClean() {
        dirty = false;
    }

    public void setDirty() {
        dirty = true;
    }
}