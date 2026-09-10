package com.digero.maestro.abc;

import com.digero.common.abc.Dynamics;
import com.digero.common.abc.LotroInstrument;
import com.digero.maestro.midi.AbcNoteEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

public class AbcMerger {
    private static final Logger log = Logger.getLogger("export.notes.merge");

    // v1: pre 4.6.26
    // v2: 4.6.26 and later
    private final int mergeVersion;

    // If prioritizeLongNotes is true, then notes that are subset of the other but lower or equal value
    // will just be deleted if sustained.
    // If false, then the 2 notes will become 2 or 3 notes,
    // where the middle (subset) will have the volume of the loudest.
    // Some listening tests convinced me that false is the way to go.
    private boolean prioritizeUninteruptedLongNotes = false;

    public AbcMerger (int mergeVersion) {
        this.mergeVersion = mergeVersion;
    }

    public int getMergeVersion() {
        return mergeVersion;
    }

    /**
     * Remove duplicate notes that play at the same time (comes from combining multiple tracks into the same part)
     *
     * @param events All the notes from all the combined tracks
     */
    void removeDuplicateNotes(List<AbcNoteEvent> events, LotroInstrument instrument) {
        /*
            Version 1:
            The reason we insert a third not in the middle of a note we nest in, which means
            we get total of 3 attacks instead of 2, is that if the nested note can be melody.
            The melody is lost if we don't hear the attack of the nested note.
            And the 3rd attack is due to the nested note might be much shorter than the outer note,
            so just carrying the nested notes volume for a long time after the nested note ends,
            can be very bad sounding.
            Reinforced this scheme by listening tests.

            Version 2:
            Does the same, but is more consistent with volume handling.
            Also it considers overlap amount (> 75%) to decide if the note we keep's volume should be max() or untouched.

         */
        List<AbcNoteEvent> notesOn = new ArrayList<>();
        List<AbcNoteEvent> thirds = new ArrayList<>();
        List<AbcNoteEvent> trash = new ArrayList<>();
        Iterator<AbcNoteEvent> neIter = events.iterator();
        dupLoop: while (neIter.hasNext()) {
            AbcNoteEvent second = neIter.next();
            List<AbcNoteEvent> thirdsOn = new ArrayList<>();
            Iterator<AbcNoteEvent> onIter = notesOn.iterator();
            while (onIter.hasNext()) {
                AbcNoteEvent first = onIter.next();

                if (first.getEndTick() <= second.getStartTick()
                        && (first.getLengthTicks() > 0 || first.getStartTick() < second.getStartTick())) {
                    onIter.remove();// already turned off
                    continue;
                }
                if (first.note.id != second.note.id) continue;

                // A zero-length arrival is dropped in every geometry.
                // A zero-length `first` can only survive to here at a shared start tick - any earlier
                // one is already gone via the turned-off test above.
                if (second.getLengthTicks() == 0) {
                    neIter.remove();
                    continue dupLoop;
                }
                if (first.getLengthTicks() == 0) {
                    onIter.remove();
                    trash.add(first);
                    continue;
                }

                switch (classify(first, second)) {
                    case SAME_START -> {
                        mergeSameStart(first, second, instrument);
                        neIter.remove();
                        continue dupLoop;
                    }
                    case NESTED -> {
                        if (mergeNested(first, second, instrument, thirds, thirdsOn)) {
                            neIter.remove();
                            continue dupLoop;
                        }
                        onIter.remove();
                    }
                    case EXTENDS_BEYOND -> {
                        if (mergeExtendsBeyond(first, second, instrument)) {
                            neIter.remove();
                            continue dupLoop;
                        }
                        onIter.remove();
                    }
                    case AFTER_THIRD -> {
                        if (first.getStartTick() < second.getEndTick()
                                && mergeAfterThird(first, second, instrument)) {
                            neIter.remove();
                            continue dupLoop;
                        }
                    }
                }
            }
            notesOn.addAll(thirdsOn);
            notesOn.add(second);
        }
        events.addAll(thirds);
        events.removeAll(trash);
    }

    /** How two same-pitch notes overlap. `first` is already sounding, `second` is arriving. */
    private enum Overlap {
        SAME_START,      // identical start tick
        NESTED,          // first started earlier, second ends within first
        EXTENDS_BEYOND,  // first started earlier, second ends after first
        AFTER_THIRD      // second started earlier, so first must be an inserted third
    }

    private static Overlap classify(AbcNoteEvent first, AbcNoteEvent second) {
        if (first.getStartTick() == second.getStartTick()) return Overlap.SAME_START;
        if (first.getStartTick() < second.getStartTick()) {
            return second.getEndTick() <= first.getEndTick() ? Overlap.NESTED : Overlap.EXTENDS_BEYOND;
        }
        return Overlap.AFTER_THIRD;
    }


    /**
     * The dynamic that survives when two same-pitch notes collapse into one.
     *
     * v1 has no single rule - it differs per geometry:
     *   SAME_START: the louder wins, but only when keep was the shorter note or the
     *               instrument does not sustain. Otherwise the accent is discarded.
     *   NESTED:     the louder always wins. Note that keep and drop are SWAPPED at this call
     *               site: here the surviving note is pushed UP by the note passing through,
     *               rather than the dropped note's dynamic being discarded.
     *   others:     never called - the notes end up non-overlapping and keep their own.
     *
     * v2 applies one rule everywhere: the louder wins only when the two are of comparable
     * length. A short FF accent over a long PPP bed is an accent, not a loud note.
     *
     * keepWasShorter must be captured BEFORE any setEndTick call - v1 evaluated it first.
     */
    private int mergedVelocity(AbcNoteEvent keep, AbcNoteEvent drop, Overlap overlap,
                               LotroInstrument instrument, boolean keepWasShorter) {

        if (mergeVersion >= 2 && overlap == Overlap.SAME_START) {
            // Only SAME_START collapses two notes into one, so only there does a single dynamic
            // have to be chosen. NESTED keeps `second` as its own note between a head and a
            // third, so its dynamic is its own - v1 only ever raised it, never lowered it, and
            // v2 must not either.
            //
            // Which note is `keep` follows from sort order, not length: compareTo breaks a
            // start-tick tie on end tick, so the SHORTER note arrives as `first`. Decide on
            // durations instead. Comparable lengths mean both dynamics belong to the merged
            // note and the louder wins; otherwise the longer note owns the result, because a
            // short FF accent over a long PPP bed is an accent, not a loud note.
            long a = keep.getLengthTicks(), b = drop.getLengthTicks();
            if (Math.min(a, b) * 4L/3L >= Math.max(a, b)) {
                return Math.max(keep.velocity, drop.velocity);
            }
            return a >= b ? keep.velocity : drop.velocity;
        }

        if (drop.velocity <= keep.velocity) return keep.velocity;
        if (overlap == Overlap.NESTED) return drop.velocity;
        if (overlap == Overlap.SAME_START && (keepWasShorter || !instrument.isSustainable(keep.note.id))) {
            return drop.velocity;
        }
        return keep.velocity;
    }

    /** first and second start on the same tick. Always consumes second. */
    private void mergeSameStart(AbcNoteEvent first, AbcNoteEvent second, LotroInstrument instrument) {
        boolean firstWasShorter = first.getEndTick() <= second.getEndTick();
        first.velocity = mergedVelocity(first, second, Overlap.SAME_START, instrument, firstWasShorter);
        if (firstWasShorter) first.setEndTick(second.getEndTick());
    }

    /**
     * second lies inside first. Splits first into head + second + optional third, so that
     * second's attack survives - it is the only trace of that voice left after the merge.
     *
     * @return true if second was consumed (only when prioritizeUninteruptedLongNotes wins)
     */
    private boolean mergeNested(AbcNoteEvent first, AbcNoteEvent second, LotroInstrument instrument,
                                List<AbcNoteEvent> thirds, List<AbcNoteEvent> thirdsOn) {

        if (!instrument.isSustainable(first.note.id)) {
            // Nothing to sustain, so no third is needed: just stop first where second starts.
            first.setEndTick(second.getStartTick());
            return false;
        }

        if (prioritizeUninteruptedLongNotes
                && Dynamics.fromMidiVelocity(second.velocity).abcVol <= Dynamics.fromMidiVelocity(first.velocity).abcVol) {
            // Only when second is no louder - a louder inner note is an accent and keeps its attack.
            return true;
        }

        // Stop first, let second through, and finish first afterwards with a third if any of it remains.
        long thirdEnd = first.getEndTick();
        // Computed before truncating first: v2 compares durations, and the head that
        // setEndTick leaves behind is not the length this note had.

        // Mirror image of SAME_START: here the SURVIVING note is pushed up rather than the
        // dropped one's dynamic being discarded, so keep and drop swap places.
        int mergedVel = mergedVelocity(second, first, Overlap.NESTED, instrument, false);
        first.setEndTick(second.getStartTick());
        second.velocity = mergedVel;

        if (thirdEnd > second.getEndTick()) {
            AbcNoteEvent third = new AbcNoteEvent(first.note, first.velocity, second.getEndTick(), thirdEnd, second.getTempoCache(), first.origNote);
            thirds.add(third);
            thirdsOn.add(third);
        }
        return false;
    }

    /**
     * second starts after first and ends after it too.
     *
     * @return true if second was consumed (absorbed into first)
     */
    private boolean mergeExtendsBeyond(AbcNoteEvent first, AbcNoteEvent second, LotroInstrument instrument) {
        if (!instrument.isSustainable(first.note.id)
                || !equalAbcDynamics(first, second, mergeVersion > 1)) {
            // Different dynamic, or nothing to sustain: break first and let second re-attack.
            first.setEndTick(second.getStartTick());
            return false;
        }
        // Sustained and identical abcVol - indistinguishable, so run them together as one note.
        first.setEndTick(second.getEndTick());
        return true;
    }

    /**
     * Do these two notes sound equally loud in game?
     * To keep the unit-test numbers down, this method is not tested, so be careful about modifying it.
     *
     * @param byPlayedVolume v2: compare abcVol, what LOTRO actually plays. Dynamics has two
     *        pairs that collide there - pppp/ppp both play at 61, fff/ffff both at 127 - so
     *        enum identity reports a difference the listener cannot hear, and the notes get
     *        needlessly broken apart and re-attacked. False reproduces v1's enum comparison.
     */
    private boolean equalAbcDynamics(AbcNoteEvent first, AbcNoteEvent second, boolean byPlayedVolume) {
        if (byPlayedVolume) {
            return Dynamics.fromMidiVelocity(first.velocity).abcVol == Dynamics.fromMidiVelocity(second.velocity).abcVol;
        }
        return Dynamics.fromMidiVelocity(first.velocity) == Dynamics.fromMidiVelocity(second.velocity);
    }

    /**
     * first starts after second, which can only happen when first is a third inserted by
     * mergeNested - events are sorted by start, so nothing else can be later than the
     * arriving note. Thirds are never zero length (mergeNested guards thirdEnd > second end),
     * which is what makes hoisting the zero-length checks out of the geometries safe.
     *
     * @return true if second was consumed
     */
    private boolean mergeAfterThird(AbcNoteEvent first, AbcNoteEvent second, LotroInstrument instrument) {
        assert first.getLengthTicks() > 0 : "AFTER_THIRD reached with a zero-length first";

        if (first.getStartTick() >= second.getEndTick()) {
            return false;// no overlap, nothing to do
        }

        if (second.getEndTick() > first.getEndTick()) {
            first.setEndTick(second.getEndTick());
        }

        // The subset note that mergeNested left at second's start is still to be processed, so
        // second's own start needs no care here. Safe to drop second entirely when sustained;
        // otherwise shorten it so it does not run into the third.
        if (instrument.isSustainable(first.note.id)) {
            return true;
        }
        if (second.getEndTick() > first.getStartTick()) {
            second.setEndTick(first.getStartTick());
        }
        return false;
    }

    @Deprecated
    void removeDuplicateNotesVerify(List<AbcNoteEvent> events, LotroInstrument instrument) {
        List<AbcNoteEvent> notesOn = new ArrayList<>();
        //second
        for (AbcNoteEvent ne : events) {
            Iterator<AbcNoteEvent> onIter = notesOn.iterator();
            while (onIter.hasNext()) {
                AbcNoteEvent on = onIter.next();//first
                if (on.getEndTick() <= ne.getStartTick() && (on.getLengthTicks() > 0 || on.getStartTick() < ne.getStartTick())) {
                    // First note has already been turned off
                    onIter.remove();
                } else if (on.note.id == ne.note.id) {
                    log.severe("OOPSIE ");
                    System.exit(0);
                }
            }
            notesOn.add(ne);
        }
    }
}
