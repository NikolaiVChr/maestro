package com.digero.maestro.abc;

import java.util.*;

import com.digero.common.abc.Dynamics;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentSampleDuration;
import com.digero.common.i18n.UIText;
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
    List<DissNote> allNotes = new ArrayList<>();
    NavigableMap<Long, DissonanceEvent> results = new TreeMap<>();
    int peakTotal = 0;
    long peakTick = 0;
    boolean dirty = true;
    private final Listener<SequencerEvent> listener = new DissonanceDetector.MyListener();
    private LotroSequencerWrapper abcSeq = null;
    private final Map<Long, List<DissNote>> dissonanceData = new HashMap<>();
    private QuantizedTimingInfo qtm = null;

    public DissonanceDetector(MiscSettings dissonancePrefs) {
        this.prefs = dissonancePrefs;
    }

    /**
     *
     * @param part Copy of AbcPart
     * @param chords Chord ready as if were to be written to abc. Already transposed.
     */
    public void submitPart(AbcPart part, List<Chord> chords) {
        List<DissNote> partData = new ArrayList<>();
        if (part.getInstrument().isPercussion) {
            dissonanceData.put(part.uniqueID, partData);
            return;
        }

        if (part.getEnabledTrackCount() == 0) {
            dissonanceData.put(part.uniqueID, partData);
            return;
        }

        SequenceDataCache cache = part.getAbcSong().getSequenceInfo().getDataCache();
        String friendlyName = part.getInstrument().friendlyName;

        for (Chord chord : chords) {
            for (AbcNoteEvent evt : chord.getNotes()) {
                if (evt.note == Note.REST || evt.tiesFrom != null) continue;
                if (part.isStudentPart() && evt.note.id < LotroInstrument.STUDENT_CHROMATIC_LOWEST.id) continue;
                if (part.isCowbellPart() || part.isDrumPart()) continue;
                Note actualNote = Note.fromId(evt.note.id + part.getInstrument().octaveDelta * 12);// We do this since the notes we are passed are in C2-C6 domain, and we need the actual domain they are played in.
                if (actualNote != null) {
                    long noteEnd = evt.getTieEnd().getEndTick();
                    if (qtm == null) {
                        qtm = (QuantizedTimingInfo) evt.getTempoCache();
                    }
                    boolean sustained = part.getInstrument().isSustainable(evt.note.id);
                    long decay = 0L;
                    if (!sustained) {
                        decay = 450_000L;
                        try {
                            decay = LotroInstrumentSampleDuration.getDura(friendlyName, evt.note.id);
                        } catch (Throwable ignored) {
                        }
                        if (qtm != null) decay = qtm.multiplyByExportTempoFactor(decay);
                        noteEnd = cache.microsToTick(cache.tickToMicros(evt.getStartTick()) + decay);
                    }
                    partData.add(new DissNote(actualNote.id, evt.getStartTick(), noteEnd,
                            cache.tickToMicros(evt.getStartTick()), cache.tickToMicros(noteEnd), sustained, evt.getVelocity(), decay));
                }
            }
        }
        dissonanceData.put(part.uniqueID, partData);
    }

    public void analyze(AbcSong song) {
        allNotes.clear();
        for (Map.Entry<Long, List<DissNote>> partData : dissonanceData.entrySet()) {
            AbcPart part = song.getPartFromID(partData.getKey());

            if (part == null) {
                //System.out.println("  ANALYSE skip: id=" + partData.getKey() + " NOT IN SONG, had " + partData.getValue().size() + " notes");
                continue;
            }
            if (part.getEnabledTrackCount() == 0 || !part.isActive()) {
                //System.out.println("  ANALYSE skip: " + part.getTitle() + " active=" + part.isActive()
                //        + " soloed=" + part.isSoloed() + " tracks=" + part.getEnabledTrackCount()
                //        + " had " + partData.getValue().size() + " notes");
                continue;
            }
            //System.out.println("  ANALYSE take: " + part.getTitle() + " perc=" + part.getInstrument().isPercussion
            //        + " notes=" + partData.getValue().size());
            allNotes.addAll(partData.getValue());
        }

        // Convert notes to a list of Start/End events for the sweep-line
        List<SweepEvent> events = new ArrayList<>();
        for (DissNote note : allNotes) {
            events.add(new SweepEvent(note.startTick, true, note));
            events.add(new SweepEvent(note.endTick, false, note));
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

        // Position map so removal is O(1). ArrayList.remove(Object) scans and shifts, which
        // makes the sweep O(n^2) and defeats the sweep-line.
        List<DissNote> activeNotes = new ArrayList<>();
        Map<DissNote, Integer> activeIndex = new IdentityHashMap<>();// note -> active notes number
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
                activeIndex.put(sweepEvent.note, activeNotes.size());
                activeNotes.add(sweepEvent.note);
            } else {
                Integer at = activeIndex.remove(sweepEvent.note);
                if (at != null) {
                    int last = activeNotes.size() - 1;
                    DissNote moved = activeNotes.get(last);
                    activeNotes.set(at, moved);
                    activeNotes.remove(last);
                    if (moved != sweepEvent.note) activeIndex.put(moved, at);
                }
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

    private void calculateAndStoreDissonance(List<DissNote> notes, long tick, long tickEnd, AbcSong song) {
        SequenceDataCache cache = song.getSequenceInfo().getDataCache();
        if (notes.size() < 2) {
            // No dissonance possible with 0 or 1 note
            Map.Entry<Long, DissonanceEvent> entry = results.lowerEntry(tick);
            if (entry != null) {
                DissonanceEvent last = entry.getValue();
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

        long microsNow = (cache.tickToMicros(tick) + cache.tickToMicros(tickEnd)) / 2L;

        for (int i = 0; i < notes.size(); i++) {
            DissNote a = notes.get(i);
            double ampA = amplitudeAt(a, microsNow);
            if (ampA < MASK_FLOOR) continue;
            long startA = a.startMicros;
            long endA = a.endMicros;

            for (int j = i + 1; j < notes.size(); j++) {
                DissNote b = notes.get(j);
                double ampB = amplitudeAt(b, microsNow);
                if (ampB < MASK_FLOOR) continue;

                // A clash is only as strong as its quieter partner: a fresh attack against
                // a decayed tail is masked, and two pianissimo notes beat less than two forte ones.
                double w = Math.min(ampA, ampB);

                if (prefs.excludeShortestNotes) {
                    // Calculate how long these two notes
                    // overlap in orig midi time.

                    // because we used cache to calc micros, tune-editor tempo changes
                    // are not factored in, can live with that.
                    long startB = b.startMicros;
                    long endB = b.endMicros;
                    long trueDuration = Math.min(endA, endB) - Math.max(startA, startB);
                    if (qtm != null) trueDuration = qtm.divideByExportTempoFactor(trueDuration);
                    if (trueDuration <= 62_000L) continue;
                }

                int interval = Math.abs(a.noteId - b.noteId);
                int lowNote = Math.min(a.noteId, b.noteId);
                if (interval == 0 || interval > 13) continue; // Unison or far enough apart to not sound jarring
                int semitones = interval % 12;

                if (lowNote < Note.C3.id + MUD_LIMIT_FROM_C3[interval]) {
                    dissonanceEvent.bassMudCount += w;
                    hasDissonance = true;
                }

                if (semitones == 1) {
                    dissonanceEvent.minorSecondCount += (interval == 13 ? w * 0.5 : w);// m9 is softer than m2
                    hasDissonance = true;
                } else if (semitones == 6) {
                    dissonanceEvent.tritoneCount += w;
                    hasDissonance = true;
                } else if (semitones == 11) {
                    dissonanceEvent.majorSeventhCount += w;
                    hasDissonance = true;
                } else if (semitones == 2) {
                    dissonanceEvent.majorSecondCount += w;
                    hasDissonance = true;
                } else if (semitones == 10) {
                    dissonanceEvent.minorSeventhCount += w;
                    hasDissonance = true;
                }
            }
        }

        if (hasDissonance) {
            addOrMergeEvent(tick, dissonanceEvent);
        } else if (results.floorEntry(tick) != null) {
            // Record return to consonance
            Map.Entry<Long, DissonanceEvent> entry = results.lowerEntry(tick);
            if (entry != null) {
                DissonanceEvent last = entry.getValue();
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
        DissNote note;

        public SweepEvent(long tick, boolean isStart, DissNote note) {
            this.tick = tick;
            this.isStart = isStart;
            this.note = note;
        }
    }

    /** A note as the detector needs it: pitch, LOTRO playback amplitude, and enough
     *  information to model decay. */
    private static class DissNote {
        final int noteId;
        final long startTick;
        final long endTick;
        final long startMicros;      // source-midi micros, for decay maths
        final long endMicros;
        final boolean sustained;
        final double vol;            // 0..1, what LOTRO actually plays (Dynamics.abcVol)
        final long decayMicros;      // full sample length; only meaningful when !sustained

        DissNote(int noteId, long startTick, long endTick, long startMicros, long endMicros,
                 boolean sustained, int velocity, long decayMicros) {
            this.noteId = noteId;
            this.startTick = startTick;
            this.endTick = endTick;
            this.startMicros = startMicros;
            this.endMicros = endMicros;
            this.sustained = sustained;
            this.vol = Dynamics.fromMidiVelocity(velocity).abcVol / 127.0d;
            this.decayMicros = decayMicros;
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
            existing.bassMudCount      += newEvent.bassMudCount;
            existing.cache = -1;
        }
    }

    /** Below this a partner is masked by the other note and the pair is not worth counting. */
    private static final double MASK_FLOOR = 0.08;

    /**
     * Approximate amplitude at a moment, 0..1. Sustaining instruments hold at their
     * dynamic level until note-off. Plucked and struck ones decay from the attack no
     * matter what the notated length says.
     */
    private double amplitudeAt(DissNote n, long micros) {
        if (n.sustained || n.decayMicros <= 0L) return n.vol;
        long elapsed = micros - n.startMicros;
        if (elapsed <= 0L) return n.vol;
        if (elapsed >= n.decayMicros) return 0.0;
        double t = 1.0 - (double) elapsed / (double) n.decayMicros;
        return n.vol * t * t;// squared falloff, roughly exponential over the sample
    }

    /**
     * Low interval limits: the lowest acceptable bottom note for an interval before it
     * turns to mud, as semitones relative to C3. Narrow intervals go muddy much higher
     * than wide ones, which a single C3 cutoff could not express. These follow the usual
     * orchestration table.
     */
    private static final int[] MUD_LIMIT_FROM_C3 = {
            0,  // 0  unison, unused
            +4,  // 1  m2   -> E3
            +3,  // 2  M2   -> Eb3
            -3,  // 3  m3   -> A2
            -7,  // 4  M3   -> F2
            -9,  // 5  P4   -> Eb2
            -6,  // 6  tri  -> F#2
            -14,  // 7  P5   -> Bb1
            -8,  // 8  m6   -> E2
            -7,  // 9  M6   -> F2
            -10,  // 10 m7   -> D2
            -9,  // 11 M7   -> Eb2
            -20,  // 12 8ve  -> E1
            +2,  // 13 m9
    };

    public class DissonanceEvent {
        public long tick;

        public int totalActiveNotes = 0;

        public double minorSecondCount;
        //public double minorSecondCount13;
        public double majorSecondCount;
        //public double majorSecondCount14;
        public double tritoneCount;
        public double majorSeventhCount;
        public double minorSeventhCount;
        public double bassMudCount;

        private int cache = -1;

        public boolean isDissonant() {
            return getWeightedTotal() > 0.01;
        }

        private double getWeightedTotal() {
            return minorSecondCount + majorSecondCount + tritoneCount
                    + majorSeventhCount + minorSeventhCount + bassMudCount;
        }

        public int getTotalScore() {
            if (cache >= 0) return cache;
            if (prefs == null) return 0;
            double count = prefs.min2factor * minorSecondCount + prefs.maj2factor * majorSecondCount
                    + prefs.trifactor * tritoneCount + prefs.maj7factor * majorSeventhCount
                    + prefs.min7factor * minorSeventhCount + prefs.mudfactor * bassMudCount;
            double penalty = 0;
            if (minorSecondCount > prefs.min2threshold) {
                penalty = prefs.min2penalty * (minorSecondCount - prefs.min2threshold);
            }
            /*
            if (majorSecondCount > prefs.maj2threshold) {
                penalty += prefs.maj2penalty * (majorSecondCount - prefs.maj2threshold);
            }
            */
            cache = (int) Math.round(count + penalty);
            return cache;
        }

        /*
        private int getTotalCollisions() {
            // we don't count bass mud in this
            return minorSecondCount + majorSecondCount + tritoneCount + majorSeventhCount + minorSeventhCount;
        }
        */

        public String getTooltipHtml() {
            StringBuilder tooltip = new StringBuilder();
            tooltip.append(UIText.get("maestro.dissonance.tip.html.dissonance"));
            tooltip.append("<br>")
                    .append(UIText.get("maestro.dissonance.tip.minor.second"))
                    .append(":&nbsp;&nbsp;")
                    .append(String.format(Locale.US, "%.1f", minorSecondCount));
            tooltip.append("<br>")
                    .append(UIText.get("maestro.dissonance.tip.major.seventh"))
                    .append(":&nbsp;&nbsp;")
                    .append(String.format(Locale.US, "%.1f", majorSeventhCount));
            tooltip.append("<br>")
                    .append(UIText.get("maestro.dissonance.tip.tritone"))
                    .append(":&nbsp;&nbsp;")
                    .append(String.format(Locale.US, "%.1f", tritoneCount));
            tooltip.append("<br>")
                    .append(UIText.get("maestro.dissonance.tip.minor.seventh"))
                    .append(":&nbsp;&nbsp;")
                    .append(String.format(Locale.US, "%.1f", minorSeventhCount));
            tooltip.append("<br>")
                    .append(UIText.get("maestro.dissonance.tip.major.second"))
                    .append(":&nbsp;&nbsp;")
                    .append(String.format(Locale.US, "%.1f", majorSecondCount));
            //tooltip.append(UIText.get("maestro.dissonance.tip.br.total.nbsp.nbsp"));
            //tooltip.append(getTotalCollisions());
            tooltip.append(UIText.get("maestro.dissonance.tip.br.br.bass.mud.nbsp.nbsp"))
                    .append(String.format(Locale.US, "%.1f", bassMudCount));
            tooltip.append(UIText.get("maestro.dissonance.tip.br.br.b.value.nbsp.nbsp"));
            tooltip.append(String.format(Locale.US, "%d", getTotalScore()));
            tooltip.append("</b></html>");
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