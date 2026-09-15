package com.digero.maestro.abc;

import com.digero.maestro.midi.AbcNoteEvent;

import java.util.*;

/**
 * Aggregate instrumentation for AbcExporter.createGridV2()
 *
 * Built for batch runs: nothing is written per note, per candidate or per part.
 * Counters and 10ms histograms accumulate across every part in the process, plus a
 * bounded set of exemplars so an anomaly stays diagnosable without flooding the log.
 * Call AbcExporter.GRID_STATS.report() once when the batch finishes.
 */
public final class GridStats {
    /** 10ms buckets: 0-9, 10-19, 20-29, 30-39, 40-49, 50-59, 60+ */
    private static final int MAX_EXEMPLARS = 5;

    private long parts, startCands, notesSeen;
    private long graceSingle, graceChord;
    private final long[] graceChordSize = new long[8];

    private long classifyMismatch;
    private final List<String> mismatchExamples = new ArrayList<>();

    private long fwdBounce;
    private final long[] fwdDrift = new long[BUCKETS];
    private long refusedDrift, refusedPath, refusedTrackEnd, refusedValid;

    private long collapseMerge, collapseOutOfOrder;
    private final long[] collapseDist = new long[BUCKETS];

    private long crushBack, crushFwd;
    private final long[] crushBackDist = new long[BUCKETS];
    private final long[] crushFwdDist = new long[BUCKETS];

    private long plainMerge;
    private final long[] plainMergeDistFwd = new long[BUCKETS];
    private final long[] plainMergeDistBack = new long[BUCKETS];

    private long reliefBounce, relief, reliefCeilNotHeavier;
    private long reliefRefDrift, reliefRefNoRoom, reliefRefInvalid;
    private final long[] reliefDist = new long[BUCKETS];
    private final long[] reliefRefDriftDist = new long[BUCKETS];
    private final List<String> reliefExamples = new ArrayList<>();

    private long graceBounce, graceDeleted;
    private long conflictEntered, conflictExits;
    private long exitExactMatch, exitNewAnchor, exitEndCandidate, exitUnhandled;
    private final long[] intervalError = new long[BUCKETS];
    private final long[] notesPerLine = new long[9]; // 1, 2, ... 8+ (index 0 unused)
    private long occupiedLines, notesOnLines, duplicatePitchNotes;
    private final long[] onsetsPerLine = new long[6]; // 1, 2, ... 5+ distinct played onsets
    private long sweepDropped, sweepStarts, sweepEnds;
    private final long[] sweepDropGap = new long[BUCKETS];
    private final long[] sweepStartError = new long[BUCKETS];
    private final long[] sweepEndError = new long[BUCKETS];
    private final List<String> sweepExamples = new ArrayList<>();
    private long sweepSplitLines;
    private long fwdExactLanding, fwdExactLandingOverwrite, fwdExactLandingEndsChain;
    private final long[] fwdExactLandingWeight = new long[6]; // 1, 2, 3-5, 6-10, 11-20, 21+
    private long endDriftDeleted;
    private final long[] endDriftRaw = new long[BUCKETS];        // |candidateEnd - initEnd|
    private final long[] endDriftResidual = new long[BUCKETS];   // |candidateEnd - mandatoryEnd|
    private final long[] endDriftDuration = new long[BUCKETS];   // the note's own played duration
    private long endDriftShorterThanMin;
    private final List<String> endDriftExamples = new ArrayList<>();
    private long startDriftDeleted, startDriftBackward, startDriftOverMinimum;
    private final long[] startDriftShift = new long[BUCKETS];
    private final long[] startDriftDuration = new long[BUCKETS];
    private final long[] startDriftTier = new long[3]; // budget 36ms / 45ms / 60ms
    private final List<String> startDriftExamples = new ArrayList<>();
    private long endShiftedNotes, endShiftedCandidates, shiftAdjacent, shiftWithPrev;
    private final long[] endShiftedDist = new long[BUCKETS];
    private final long[] shiftGapToPrev = new long[BUCKETS];
    private final List<String> endShiftedExamples = new ArrayList<>();
    private long dissonanceClusters, dissonancePairs, dissonanceDropped;
    private long dissonanceKeptOverlap, dissonanceKeptBothLong, dissonanceSkippedBend;
    private final long[] dissonanceClusterSize = new long[9];   // 2, 3, ... 8+
    private final long[] dissonanceDroppedInterval = new long[3]; // unused, 1, 2 semitones
    private long tieOnSnappedDuration, tieOnOrigDuration;
    private long bothLongSnapped, bothLongOrig;
    private final List<String> dissonanceExamples = new ArrayList<>();
    private final Map<String, long[]> partInput = new HashMap<>();   // label -> {notes, candidates}
    // -- TYPE_END branch exits --
    private long endOverwrite, endMergeBlocker, endLastMerge, endLastAdded;
    private long endOverwriteFwd, endMergeBlockerFwd, endLastMergeFwd;
    private final long[] endOverwriteDist = new long[BUCKETS];
    private final long[] endMergeBlockerDist = new long[BUCKETS];
    private final long[] endLastMergeDist = new long[BUCKETS];
    private final List<String> endLastAddedExamples = new ArrayList<>();
    private long endSafetyLine, endSafetyExists;
    private long endMovedStarts, endMovedStartsGrace;
    private long endMovedByShift, endMovedByOverwrite;
    private final long[] endMovedStartDist = new long[BUCKETS];
    private final long[] endMovedStartDistGrace = new long[BUCKETS];
    private long safetyLineNonSustain, safetyLineMergeWasLegal, safetyLineMixedSustain;
    private long endSafetyCeil, endSafetyCeilRefusedPlucked, endSafetyCeilNoRoom;
    private final long[] endSafetyCeilDist = new long[BUCKETS];
    private final long[] endLastMergeDistFwd  = new long[BUCKETS];
    private final long[] endLastMergeDistBack = new long[BUCKETS];
    private long lrMergeBlocker, lrMergeCeil, lrNewLine, lrCeilFallback;

    synchronized void lastResortArm(int arm, int notes) {
        switch (arm) {
            case 1 -> lrMergeBlocker += notes;
            case 2 -> lrMergeCeil += notes;
            case 3 -> lrNewLine += notes;
            case 4 -> lrCeilFallback += notes;
        }
    }

    /**
     * A line laid at ceil - minimumMicros so an end could stop short of the ceiling instead of
     * being pushed up to it. The mirror of the floor-side safety line. Without it a ceiling
     * merge is the only option offered, and mergeIsNearer cannot reject it because the
     * distance is negative.
     */
    synchronized void endSafetyLineCeil(int notes, long saved) {
        endSafetyCeil += notes;
        endSafetyCeilDist[bucket(saved)] += notes;
    }

    /** Refused because the notes do not sustain, so the written length is not heard. */
    synchronized void endSafetyCeilRefusedPlucked(int notes) { endSafetyCeilRefusedPlucked += notes; }

    /** Refused because the slot is blocked or would leave the note under minimumMicros. */
    synchronized void endSafetyCeilNoRoom(int notes) { endSafetyCeilNoRoom += notes; }

    /**
     * A safety line laid for notes that do not sustain, where merging down into the blocker
     * would also have been legal. The line is created only to give the note a legal written
     * length - which a plucked sample does not need, since it plays out regardless - so every
     * one of these is a grid line that constrains later candidates for nothing.
     */
    synchronized void safetyLineNonSustain(int notes, boolean mergeWasLegal) {
        safetyLineNonSustain += notes;
        if (mergeWasLegal) safetyLineMergeWasLegal += notes;
    }

    /** An end candidate whose notes disagree about sustain - Student's Fiddle territory. */
    synchronized void safetyLineMixedSustain(int notes) { safetyLineMixedSustain += notes; }

    /**
     * A note whose onset was moved by end-candidate processing rather than by anything about
     * its own start: either the whole-note shift moving its line back, or an END overwrite
     * absorbing the line it sat on.
     */
    synchronized void endMovedStart(long delta, boolean grace, boolean byShift) {
        endMovedStarts++;
        endMovedStartDist[bucket(delta)]++;
        if (grace) {
            endMovedStartsGrace++;
            endMovedStartDistGrace[bucket(delta)]++;
        }
        if (byShift) endMovedByShift++; else endMovedByOverwrite++;
    }

    synchronized void endSafetyLine(int notes)   { endSafetyLine += notes; }
    synchronized void endSafetyExists(int notes) { endSafetyExists += notes; }

    /** The end candidate outweighed the blocking line and replaced it at its own position. */
    synchronized void endOverwriteWeakBlocker(int notes, long delta, String label, long micros) {
        endOverwrite += notes;
        endOverwriteDist[bucket(delta)] += notes;
        if (delta < 0) endOverwriteFwd += notes;
    }

    /** Merged into the blocker because that still left every note at least minimumMicros long
     *  and it was the nearer of the two legal ends. */
    synchronized void endMergeWithBlocker(int notes, long delta, String label, long micros) {
        endMergeBlocker += notes;
        endMergeBlockerDist[bucket(delta)] += notes;
        if (delta < 0) endMergeBlockerFwd += notes;
    }

    /**
     * No other branch took it: strapped to the blocker so it does not fall off the grid.
     * Unlike endMergeWithBlocker this is unconditional, so the move can be any size.
     * delta = time - blocker.micros(): positive pulls the end back, negative pushes it forward.
     */
    synchronized void endLastResortMerge(int notes, long delta, String label, long micros) {
        endLastMerge += notes;
        if (delta < 0) {
            endLastMergeFwd += notes;
            endLastMergeDistFwd[bucket(delta)] += notes;
        } else {
            endLastMergeDistBack[bucket(delta)] += notes;
        }
    }

    /** No blocker at all, so a fresh line was created at the candidate's own position.
     *  Rare by construction - entering the branch requires a conflict, and a conflict
     *  implies a blocker - so the exemplars are worth reading if this is ever non-zero. */
    synchronized void endLastResortAdded(int notes, String label, long micros) {
        endLastAdded += notes;
        if (endLastAddedExamples.size() < MAX_EXEMPLARS) {
            endLastAddedExamples.add(label + " @" + micros + "us notes=" + notes);
        }
    }

    synchronized void dissonanceCluster(int size) {
        dissonanceClusters++;
        dissonanceClusterSize[Math.min(size, dissonanceClusterSize.length - 1)]++;
    }

    synchronized void dissonanceSkippedBend(int notes) { dissonanceSkippedBend += notes; }

    /**
     * One candidate/survivor pair that reached the comparison. Records which escape fired,
     * and - for the two tests that read post-grid durations - whether they would have
     * answered differently on the notes as played.
     */
    synchronized void dissonancePair(boolean keptOverlap, boolean bothLongSnappedNow,
                                     boolean bothLongOrigNow, boolean dropped, int interval,
                                     String label, long micros) {
        dissonancePairs++;
        if (keptOverlap) dissonanceKeptOverlap++;
        if (bothLongSnappedNow) bothLongSnapped++;
        if (bothLongOrigNow) bothLongOrig++;
        if (bothLongSnappedNow && !keptOverlap) dissonanceKeptBothLong++;
        if (dropped) {
            dissonanceDropped++;
            if (interval >= 1 && interval <= 2) dissonanceDroppedInterval[interval]++;
            if (dissonanceExamples.size() < MAX_EXEMPLARS) {
                dissonanceExamples.add(label + " @" + micros + "us interval=" + interval);
            }
        }
    }

    /** Does the importance sort have a tie at the top under each duration source? */
    synchronized void dissonanceSortTie(boolean snappedTie, boolean origTie) {
        if (snappedTie) tieOnSnappedDuration++;
        if (origTie) tieOnOrigDuration++;
    }

    /**
     * A note whose required end could not become a line - it sat inside minimumMicros of
     * the ceiling - so the whole note was shifted back one slot instead of having its end
     * merged up into that ceiling.
     *
     * gapToPrevShift is the distance to the previous shifted note in the same part, or -1
     * for the first. Isolated shifts are harmless; consecutive ones alternate, because the
     * end line a shift creates sits exactly minimumMicros below the next start and denies
     * the following note the room it needs.
     */
    synchronized void endShiftedWholeNote(int notes, long shift, long gapToPrevShift,
                                          String label, long micros) {
        endShiftedCandidates++;
        endShiftedNotes += notes;
        endShiftedDist[bucket(shift)]++;
        if (gapToPrevShift >= 0) {
            shiftWithPrev++;
            shiftGapToPrev[bucket(gapToPrevShift)]++;
            if (gapToPrevShift < 60_000L) shiftAdjacent++;
        }
        if (endShiftedExamples.size() < MAX_EXEMPLARS) {
            endShiftedExamples.add(label + " @" + micros + "us shift=-" + shift
                    + "us gapToPrev=" + gapToPrevShift + " notes=" + notes);
        }
    }

    /**
     * A note deleted by the start-drift check. The check guards against notes dragged
     * across long rests, which the v2 grid cannot do - every path is bounded by
     * minimumMicros. startDriftOverMinimum should therefore be 0; anything else means
     * a path exists that I have not accounted for.
     */
    synchronized void startDriftDelete(long shift, boolean backward, long duration,
                                       long maxShift, long minimumMicros, String label, long micros) {
        startDriftDeleted++;
        startDriftShift[bucket(shift)]++;
        startDriftDuration[bucket(duration)]++;
        if (backward) startDriftBackward++;
        if (shift >= minimumMicros) startDriftOverMinimum++;
        startDriftTier[maxShift <= 36_000L ? 0 : (maxShift <= 45_000L ? 1 : 2)]++;
        if (startDriftExamples.size() < MAX_EXEMPLARS) {
            startDriftExamples.add(label + " @" + micros + "us dur=" + duration
                    + " shift=" + (backward ? "-" : "+") + shift + " budget=" + maxShift);
        }
    }

    /**
     * A note deleted by the end-drift check. mandatoryEnd is where the minimum-duration
     * rule already forced the end to, so residual is the drift the grid is actually
     * responsible for. If residual is small while raw is large, the check is punishing
     * notes for being short rather than for moving.
     */
    synchronized void endDriftDelete(long raw, long residual, long duration, long minimumMicros,
                                     String label, long micros) {
        endDriftDeleted++;
        endDriftRaw[bucket(raw)]++;
        endDriftResidual[bucket(residual)]++;
        endDriftDuration[bucket(duration)]++;
        if (duration < minimumMicros) endDriftShorterThanMin++;
        if (endDriftExamples.size() < MAX_EXEMPLARS) {
            endDriftExamples.add(label + " @" + micros + "us dur=" + duration
                    + " raw=" + raw + " residual=" + residual);
        }
    }

    /**
     * A forward bounce landed exactly on an existing line. The line's weight says whether
     * that is harmless (an end or safety marker) or a real chord whose voicing changes.
     * Its depth says whether a running chain quietly terminates here, since a merge keeps
     * the existing point's depth and discards the one applyBounce2 was handed.
     */
    synchronized void forwardExactLanding(int neighborWeight, int candidateWeight, int neighborDepth) {
        fwdExactLanding++;
        if (neighborWeight < candidateWeight) fwdExactLandingOverwrite++;
        if (neighborDepth == 0) fwdExactLandingEndsChain++;

        int b;
        if (neighborWeight <= 1) b = 0;
        else if (neighborWeight == 2) b = 1;
        else if (neighborWeight <= 5) b = 2;
        else if (neighborWeight <= 10) b = 3;
        else if (neighborWeight <= 20) b = 4;
        else b = 5;
        fwdExactLandingWeight[b]++;
    }

    /** Lines inserted to bridge a gap longer than the instrument's sample can hold. */
    synchronized void sweepSplit(int lines) { sweepSplitLines += lines; }

    synchronized void sweepDrop(long gap) {
        sweepDropped++;
        sweepDropGap[bucket(gap)]++;
    }

    /** delta = played onset minus where the sweep put it. Total error, not the last hop. */
    synchronized void sweepRebindStart(long delta, String label, long micros) {
        sweepStarts++;
        sweepStartError[bucket(delta)]++;
        if (Math.abs(delta) >= 60_000L && sweepExamples.size() < MAX_EXEMPLARS) {
            sweepExamples.add(label + " @" + micros + "us start moved " + delta + "us from played");
        }
    }

    synchronized void sweepRebindEnd(long delta) {
        sweepEnds++;
        sweepEndError[bucket(delta)]++;
    }

    /** How many separately-played onsets ended up on one line. 3+ is a collapsed figure. */
    synchronized void lineOnsets(int distinctOnsets) {
        onsetsPerLine[Math.min(distinctOnsets, onsetsPerLine.length - 1)]++;
    }
    /**
     * One onset line: how many notes start on it, and how many of those are pitch
     * duplicates of another note on the same line. Duplicates are the ones that
     * cannot be heard as separate events at all.
     */
    synchronized void lineOccupancy(int notes, int duplicates) {
        notesPerLine[Math.min(notes, notesPerLine.length - 1)]++;
        occupiedLines++;
        notesOnLines += notes;
        duplicatePitchNotes += duplicates;
    }
    /** How much the gap to the previous onset changed. This is what the ear tracks. */
    synchronized void intervalChange(int notes, long before, long after) {
        intervalError[bucket(after - before)] += notes;
    }

    synchronized void part(int startCandidateCount) {
        parts++;
        startCands += startCandidateCount;
    }

    /**
     * Classifies one start candidate and cross-checks graceOnly against a direct
     * duration test. A non-zero mismatch count means the two disagree and the
     * exemplars will say why.
     */
    synchronized void classifyStart(AbcExporter.Candidate2 sc, long graceThreshold, boolean percussion, String label) {
        notesSeen += sc.notes.size();

        long[] pi = partInput.computeIfAbsent(label, k -> new long[2]);
        pi[0] += sc.notes.size();
        pi[1]++;

        boolean allShort = true;
        long minDur = Long.MAX_VALUE, maxDur = Long.MIN_VALUE;
        for (AbcNoteEvent n : sc.notes) {
            long d = n.initEndABCMicros - n.initStartABCMicros;
            if (d >= graceThreshold) allShort = false;
            minDur = Math.min(minDur, d);
            maxDur = Math.max(maxDur, d);
        }

        if (sc.graceOnly) {
            if (sc.notes.size() == 1) graceSingle++;
            else {
                graceChord++;
                graceChordSize[Math.min(sc.notes.size(), graceChordSize.length - 1)]++;
            }
        }

        // percussion legitimately clears graceOnly while staying short, so it is not a mismatch
        if (allShort != sc.graceOnly && !(allShort && percussion)) {
            classifyMismatch++;
            if (mismatchExamples.size() < MAX_EXEMPLARS) {
                mismatchExamples.add(label + " @" + sc.micros + "us notes=" + sc.notes.size()
                        + " weight=" + sc.weight + " graceOnly=" + sc.graceOnly
                        + " allShort=" + allShort + " dur=" + minDur + ".." + maxDur
                        + " percussion=" + percussion);
            }
        }
    }

    synchronized void conflictEntered(int notes) { conflictEntered += notes; }
    synchronized void exitExactMatch(int notes)  { exitExactMatch += notes; }
    synchronized void exitNewAnchor(int notes)   { exitNewAnchor += notes; }
    synchronized void exitEndCandidate(int notes){ exitEndCandidate += notes; }
    synchronized void exitUnhandled(int notes)   { exitUnhandled += notes; }

    /** delta = time - target. Positive means the note was pulled backward. */
    synchronized void crush(int notes, long delta) {
        if (delta >= 0) {
            crushBack += notes; addDisplacement(crushBackDist, delta, notes);
        } else {
            crushFwd += notes; addDisplacement(crushFwdDist, delta, notes);
        }
        conflictExits += notes;
    }

    synchronized void collapse(int notes, long delta) {
        collapseMerge += notes;
        addDisplacement(collapseDist, delta, notes);
        conflictExits += notes;
    }

    /**
     * delta positive when back
     */
    synchronized void plainMerge(int notes, long delta) {
        plainMerge += notes;
        if (delta >= 0) {
            addDisplacement(plainMergeDistBack, delta, notes);
        } else {
            addDisplacement(plainMergeDistFwd, delta, notes);
        }
        conflictExits += notes;
    }

    synchronized void forwardBounce(int notes, long drift) {
        fwdBounce += notes; fwdDrift[bucket(drift)] += notes;
        conflictExits += notes;
    }

    synchronized void relief(int notes, long stepBack, boolean ceilHeavier, String label, long micros) {
        reliefBounce += notes;
        reliefDist[bucket(stepBack)] += notes;
        if (!ceilHeavier) {
            reliefCeilNotHeavier += notes;
            if (reliefExamples.size() < MAX_EXEMPLARS) {
                reliefExamples.add(label + " @" + micros + "us stepBack=" + stepBack + "us");
            }
        }
        conflictExits += notes;
    }

    /** Step back would have exceeded MAX_BOUNCE_DRIFT; the note merges forward instead. */
    synchronized void reliefRefusedDrift(int notes, long stepBack) {
        reliefRefDrift += notes;
        reliefRefDriftDist[bucket(stepBack)] += notes;
        conflictExits += notes;
    }

    /** Less than two slots between floor and ceiling, so there is nowhere to step back to. */
    synchronized void reliefRefusedNoRoom(int notes) { reliefRefNoRoom += notes; conflictExits += notes;}

    /** The slot exists but isValidBounce2 rejected it. */
    synchronized void reliefRefusedInvalid(int notes) { reliefRefInvalid += notes; conflictExits += notes;}

    synchronized void graceBounce(int notes)  { graceBounce += notes; conflictExits += notes;}
    synchronized void graceDeleted(int notes) { graceDeleted += notes; conflictExits += notes;}

    synchronized void collapseOutOfOrder(int notes) { collapseOutOfOrder += notes; }

    synchronized void refusedDrift(int notes)    { refusedDrift += notes; }
    synchronized void refusedPath(int notes)     { refusedPath += notes; }
    synchronized void refusedTrackEnd(int notes) { refusedTrackEnd += notes; }
    synchronized void refusedValid(int notes)    { refusedValid += notes; }

    /** 5ms buckets: 0-4, 5-9, ... 55-59, then 60+ */
    private static final int BUCKETS = 13;

    private static int bucket(long micros) {
        return (int) Math.min(Math.abs(micros) / 5_000L, BUCKETS - 1);
    }

    private static String hist(long[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) continue;
            if (sb.length() > 0) sb.append("  ");
            if (i == BUCKETS - 1) sb.append("60+");
            else sb.append(i * 5).append('-').append(i * 5 + 4);
            sb.append('=').append(a[i]);
        }
        return sb.length() == 0 ? "(none)" : sb.toString();
    }

    private static String pct(long n, long of) {
        return of == 0 ? "-" : String.format(Locale.ROOT, "%.2f%%", 100.0 * n / of);
    }

    private static String over(long[] a, int fromBucket) {
        long n = 0, tot = 0;
        for (int i = 0; i < a.length; i++) { tot += a[i]; if (i >= fromBucket) n += a[i]; }
        return n + " (" + pct(n, tot) + ")";
    }

    private final ThreadLocal<long[]> localDisplaced = ThreadLocal.withInitial(() -> new long[2]);

    private void addDisplacement(long[] hist, long delta, int notes) {
        hist[bucket(delta)] += notes;
        long[] local = localDisplaced.get();
        local[0] += notes;
        if (Math.abs(delta) >= 30_000L) { local[1] += notes; }
    }

    long[] mark() {
        long[] local = localDisplaced.get();
        return new long[] { local[0], local[1] };
    }

    private final Map<String, long[]> partTally = new HashMap<>();       // song/part -> {displaced, over30}
    private final Map<String, long[]> instrumentTally = new HashMap<>(); // instrument -> {displaced, over30}

    synchronized void endPart(String label, String instrument, long[] mark) {
        long[] local = localDisplaced.get();
        long d = local[0] - mark[0];
        if (d == 0) return;
        long over = local[1] - mark[1];

        long[] cur = partTally.computeIfAbsent(label, k -> new long[2]);
        cur[0] += d;
        cur[1] += over;

        long[] inst = instrumentTally.computeIfAbsent(instrument, k -> new long[2]);
        inst[0] += d;
        inst[1] += over;
    }

    /** Returns the report as individual lines, so callers can emit one log record each. */
    /** Returns the report as individual lines, so callers can emit one log record each. */
    public synchronized List<String> reportLines() {
        List<String> out = new ArrayList<>();
        out.add("===== createGridV2 statistics =====");
        out.add(String.format(Locale.ROOT, "parts=%d  startCandidates=%d  notes=%d", parts, startCands, notesSeen));

        out.add("-- candidate classification --");
        out.add(String.format(Locale.ROOT, "single grace notes : %d (%s of candidates)",
                graceSingle, pct(graceSingle, startCands)));
        StringBuilder sizes = new StringBuilder();
        for (int i = 2; i < graceChordSize.length; i++) {
            if (graceChordSize[i] > 0) sizes.append(' ').append(i).append("n=").append(graceChordSize[i]);
        }
        out.add(String.format(Locale.ROOT, "grace chords       : %d (%s)  by size:%s",
                graceChord, pct(graceChord, startCands), sizes));
        out.add(String.format(Locale.ROOT, "graceOnly/allShort mismatches: %d", classifyMismatch));
        for (String s : mismatchExamples) out.add("    " + s);

        out.add("-- forward bounce --");
        out.add(String.format(Locale.ROOT, "bounced=%d  drift(ms): %s", fwdBounce, hist(fwdDrift)));
        out.add(String.format(Locale.ROOT, "refused: drift=%d  path=%d  trackEnd=%d  invalid=%d",
                refusedDrift, refusedPath, refusedTrackEnd, refusedValid));

        String[] wNames = {"w1", "w2", "w3-5", "w6-10", "w11-20", "w21+"};
        StringBuilder wl = new StringBuilder();
        for (int i = 0; i < fwdExactLandingWeight.length; i++) {
            if (fwdExactLandingWeight[i] == 0) continue;
            wl.append("  ").append(wNames[i]).append('=').append(fwdExactLandingWeight[i]);
        }
        out.add(String.format(Locale.ROOT, "landed exactly (simulation) on an existing line: %d  (overwrote it: %d, ended a chain: %d)",
                fwdExactLanding, fwdExactLandingOverwrite, fwdExactLandingEndsChain));
        out.add("   line weight:" + (wl.length() == 0 ? " (none)" : wl));

        out.add("-- displacement without a bounce --");
        out.add(String.format(Locale.ROOT, "crush back to floor=%d  >=30ms: %s", crushBack, over(crushBackDist, 6)));
        out.add("    " + hist(crushBackDist));
        out.add(String.format(Locale.ROOT, "crush fwd (leapfrog)=%d  >=30ms: %s", crushFwd, over(crushFwdDist, 6)));
        out.add("    " + hist(crushFwdDist));
        out.add(String.format(Locale.ROOT, "group collapse=%d  >=30ms: %s  (out-of-order rejected: %d)",
                collapseMerge, over(collapseDist, 6), collapseOutOfOrder));
        out.add(String.format(Locale.ROOT, "plain merge=%d", plainMerge));
        out.add(String.format(Locale.ROOT, "  fwd  >=30ms: %s", over(plainMergeDistFwd, 6)));
        out.add("    " + hist(plainMergeDistFwd));
        out.add(String.format(Locale.ROOT, "  back >=30ms: %s", over(plainMergeDistBack, 6)));
        out.add("    " + hist(plainMergeDistBack));

        out.add("-- relief branch (crowded by a heavier chord just ahead in timeline) --");
        long reliefRefTotal = reliefRefDrift + reliefRefNoRoom + reliefRefInvalid;
        out.add(String.format(Locale.ROOT, "stepped back=%d  refused=%d", reliefBounce, reliefRefTotal));
        out.add("    stepBack(ms): " + hist(reliefDist));
        out.add(String.format(Locale.ROOT, "refused: overDrift=%d  noRoom=%d  invalid=%d",
                reliefRefDrift, reliefRefNoRoom, reliefRefInvalid));
        out.add("    rejected stepBack(ms): " + hist(reliefRefDriftDist));
        out.add(String.format(Locale.ROOT, "ceiling NOT heavier than candidate: %d  <-- expect 0", reliefCeilNotHeavier));
        for (String s : reliefExamples) out.add("    " + s);

        out.add("-- grace notes --");
        out.add(String.format(Locale.ROOT, "bounced back=%d  deleted=%d", graceBounce, graceDeleted));

        out.add("-- onset line occupancy --");
        StringBuilder occ = new StringBuilder();
        for (int i = 1; i < notesPerLine.length; i++) {
            if (notesPerLine[i] == 0) continue;
            occ.append("  ").append(i).append(i == notesPerLine.length - 1 ? "+" : "")
                    .append("n=").append(notesPerLine[i]);
        }
        out.add(String.format(Locale.ROOT, "lines=%d  notes=%d  avg=%.2f",
                occupiedLines, notesOnLines, occupiedLines == 0 ? 0.0 : (double) notesOnLines / occupiedLines));
        out.add("   " + occ);
        out.add(String.format(Locale.ROOT, "pitch duplicates on a shared line: %d (%s of notes)",
                duplicatePitchNotes, pct(duplicatePitchNotes, notesOnLines)));

        StringBuilder ons = new StringBuilder();
        long fused = 0, linesWithOnsets = 0;
        for (int i = 1; i < onsetsPerLine.length; i++) {
            if (onsetsPerLine[i] == 0) continue;
            ons.append("  ").append(i).append(i == onsetsPerLine.length - 1 ? "+" : "")
                    .append("x=").append(onsetsPerLine[i]);
            linesWithOnsets += onsetsPerLine[i];
            if (i >= 3) fused += onsetsPerLine[i];
        }
        out.add("distinct played onsets per line (1x = a chord, 3x+ = a flattened figure):");
        out.add("   " + (ons.length() == 0 ? "(none)" : ons));
        out.add(String.format(Locale.ROOT, "lines fusing 3+ separate onsets: %d (%s of lines)",
                fused, pct(fused, linesWithOnsets)));

        out.add("-- final sweep (grid repairing itself) --");
        out.add(String.format(Locale.ROOT, "lines dropped as too close: %d  <-- expect 0", sweepDropped));
        out.add("   dropped gap(ms): " + hist(sweepDropGap));
        out.add(String.format(Locale.ROOT, "starts rebound: %d  error from played(ms): %s",
                sweepStarts, hist(sweepStartError)));
        out.add(String.format(Locale.ROOT, "ends rebound:   %d  error from played(ms): %s",
                sweepEnds, hist(sweepEndError)));
        for (String s : sweepExamples) out.add("    " + s);
        out.add(String.format(Locale.ROOT, "lines inserted for sustain: %d", sweepSplitLines));

        out.add("-- conflict block reconciliation --");
        out.add(String.format(Locale.ROOT, "entered=%d  exits=%d  unaccounted=%d  <-- expect 0",
                conflictEntered, conflictExits, conflictEntered - conflictExits));
        out.add(String.format(Locale.ROOT, "exact match=%d  new anchor=%d  end candidates=%d  UNHANDLED=%d",
                exitExactMatch, exitNewAnchor, exitEndCandidate, exitUnhandled));

        out.add("== snap to grid ==");
        out.add("-- end-drift deletions --");
        out.add(String.format(Locale.ROOT, "deleted=%d  of which played shorter than minimumMicros: %d (%s)",
                endDriftDeleted, endDriftShorterThanMin, pct(endDriftShorterThanMin, endDriftDeleted)));
        out.add("   played duration(ms): " + hist(endDriftDuration));
        out.add("   drift from raw end(ms): " + hist(endDriftRaw));
        out.add("   drift from mandatory end(ms): " + hist(endDriftResidual));
        for (String s : endDriftExamples) out.add("    " + s);
        out.add("-- start-drift deletions --");
        out.add(String.format(Locale.ROOT, "deleted=%d  backward=%d (%s)  shift >= minimumMicros: %d  <-- expect 0",
                startDriftDeleted, startDriftBackward, pct(startDriftBackward, startDriftDeleted),
                startDriftOverMinimum));
        out.add(String.format(Locale.ROOT, "   budget tier: 36ms=%d  45ms=%d  60ms=%d",
                startDriftTier[0], startDriftTier[1], startDriftTier[2]));
        out.add("   shift(ms): " + hist(startDriftShift));
        out.add("   played duration(ms): " + hist(startDriftDuration));
        for (String s : startDriftExamples) out.add("    " + s);

        out.add("-- ungoverned displacement by instrument --");
        out.add("   (crush to floor, group collapse, plain merge)");
        instrumentTally.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        (double) b.getValue()[1] / b.getValue()[0],
                        (double) a.getValue()[1] / a.getValue()[0]))
                .forEach(e -> out.add(String.format(Locale.ROOT, "  %6s  %8d/%-9d  %s",
                        pct(e.getValue()[1], e.getValue()[0]),
                        e.getValue()[1], e.getValue()[0], e.getKey())));

        out.add("-- whole-note shift (end had nowhere legal to land) --");
        out.add(String.format(Locale.ROOT, "shifted=%d notes in %d candidates", endShiftedNotes, endShiftedCandidates));
        out.add("   onset moved back(ms): " + hist(endShiftedDist));
        out.add(String.format(Locale.ROOT, "   within 4 slots of the previous shift: %d of %d (%s)",
                shiftAdjacent, shiftWithPrev, pct(shiftAdjacent, shiftWithPrev)));
        out.add("   gap to previous shift(ms): " + hist(shiftGapToPrev));
        for (String s : endShiftedExamples) out.add("    " + s);

        out.add("-- end candidate exits --");
        out.add(String.format(Locale.ROOT, "overwrote weak blocker=%d (fwd %d)  merged with blocker=%d (fwd %d)",
                endOverwrite, endOverwriteFwd, endMergeBlocker, endMergeBlockerFwd));
        out.add("   overwrite dist(ms): " + hist(endOverwriteDist));
        out.add("   merge dist(ms): " + hist(endMergeBlockerDist));
        out.add(String.format(Locale.ROOT, "last resort merge=%d   last resort new line=%d  <-- expect 0",
                endLastMerge, endLastAdded));
        out.add(String.format(Locale.ROOT, "   fwd=%d  >=30ms: %s", endLastMergeFwd, over(endLastMergeDistFwd, 6)));
        out.add("     " + hist(endLastMergeDistFwd));
        out.add(String.format(Locale.ROOT, "   back=%d  >=30ms: %s", endLastMerge - endLastMergeFwd, over(endLastMergeDistBack, 6)));
        out.add("     " + hist(endLastMergeDistBack));
        out.add(String.format(Locale.ROOT, "   arms: blocker=%d  ceil(conflict)=%d  new line=%d  ceil(fallback)=%d",
                lrMergeBlocker, lrMergeCeil, lrNewLine, lrCeilFallback));
        for (String s : endLastAddedExamples) out.add("    " + s);
        out.add(String.format(Locale.ROOT, "safety line added=%d   merged onto existing safety position=%d",
                endSafetyLine, endSafetyExists));
        out.add(String.format(Locale.ROOT, "safety line (ceiling side)=%d   refused: plucked=%d  no room=%d",
                endSafetyCeil, endSafetyCeilRefusedPlucked, endSafetyCeilNoRoom));
        out.add("   stretch avoided(ms): " + hist(endSafetyCeilDist));
        out.add(String.format(Locale.ROOT, "whole-note shift=%d", endShiftedNotes));
        out.add(String.format(Locale.ROOT, "   accounted: %d of %d entering the end branch  <-- expect equal",
                endOverwrite + endMergeBlocker + endLastMerge + endLastAdded
                        + endSafetyLine + endSafetyCeil + endSafetyExists + endShiftedNotes,
                exitEndCandidate));
        out.add(String.format(Locale.ROOT, "safety line added=%d   merged onto existing safety position=%d",
                endSafetyLine, endSafetyExists));
        out.add(String.format(Locale.ROOT, "   of which all notes non-sustaining: %d (%s)  - merging down was legal for %d",
                safetyLineNonSustain, pct(safetyLineNonSustain, endSafetyLine), safetyLineMergeWasLegal));
        out.add(String.format(Locale.ROOT, "   candidates with mixed sustain: %d", safetyLineMixedSustain));

        out.add("-- onsets moved by end processing --");
        out.add(String.format(Locale.ROOT, "starts moved=%d  (whole-note shift=%d, END overwrite=%d)",
                endMovedStarts, endMovedByShift, endMovedByOverwrite));
        out.add(String.format(Locale.ROOT, "   of which grace-length: %d (%s)",
                endMovedStartsGrace, pct(endMovedStartsGrace, endMovedStarts)));
        out.add("   all(ms): " + hist(endMovedStartDist));
        out.add("   grace(ms): " + hist(endMovedStartDistGrace));

        out.add("-- collapsed dissonance --");
        out.add(String.format(Locale.ROOT, "clusters>=2: %d  dissonant pairs: %d  dropped: %d",
                dissonanceClusters, dissonancePairs, dissonanceDropped));
        StringBuilder cs = new StringBuilder();
        for (int i = 2; i < dissonanceClusterSize.length; i++) {
            if (dissonanceClusterSize[i] > 0) {
                cs.append("  ").append(i).append(i == dissonanceClusterSize.length - 1 ? "+" : "")
                        .append("n=").append(dissonanceClusterSize[i]);
            }
        }
        out.add("   cluster size:" + (cs.length() == 0 ? " (none)" : cs));
        out.add(String.format(Locale.ROOT, "   dropped by interval: minor2nd=%d  major2nd=%d",
                dissonanceDroppedInterval[1], dissonanceDroppedInterval[2]));
        out.add(String.format(Locale.ROOT, "   kept by original overlap: %d   kept by both-long: %d",
                dissonanceKeptOverlap, dissonanceKeptBothLong));
        out.add(String.format(Locale.ROOT, "   both-long on snapped durations: %d   on played durations: %d  <-- gap is the grid stretching them",
                bothLongSnapped, bothLongOrig));
        out.add(String.format(Locale.ROOT, "   importance sort tied at the top: snapped=%d  played=%d  (of %d clusters)",
                tieOnSnappedDuration, tieOnOrigDuration, dissonanceClusters));
        out.add(String.format(Locale.ROOT, "   notes exempted as bent/origNote-null: %d", dissonanceSkippedBend));
        for (String s : dissonanceExamples) out.add("    " + s);

        out.add("-- worst individual parts, ungoverned displacement >= 30ms --");
        partTally.entrySet().stream()
                .filter(e -> e.getValue()[0] >= 100)
                .sorted((a, b) -> Double.compare(
                        (double) b.getValue()[1] / b.getValue()[0],
                        (double) a.getValue()[1] / a.getValue()[0]))
                .limit(15)
                .forEach(e -> out.add(String.format(Locale.ROOT, "  %6s  %6d/%-6d  %s",
                        pct(e.getValue()[1], e.getValue()[0]),
                        e.getValue()[1], e.getValue()[0], e.getKey())));
        /*
        out.add("-- per-part input (notes, startCandidates) --");
        partInput.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.add(String.format(Locale.ROOT, "  %d\t%d\t%s",
                        e.getValue()[0], e.getValue()[1], e.getKey())));
        */
        out.add("===================================");
        return out;
    }

    public synchronized void reset() {
        parts = startCands = 0;
        graceSingle = graceChord = classifyMismatch = 0;
        Arrays.fill(graceChordSize, 0);
        mismatchExamples.clear();
        fwdBounce = refusedDrift = refusedPath = refusedTrackEnd = refusedValid = 0;
        Arrays.fill(fwdDrift, 0);
        collapseMerge = crushBack = crushFwd = plainMerge = 0;
        Arrays.fill(collapseDist, 0);
        Arrays.fill(crushBackDist, 0);
        Arrays.fill(crushFwdDist, 0);
        Arrays.fill(plainMergeDistBack, 0);
        Arrays.fill(plainMergeDistFwd, 0);
        reliefBounce = reliefCeilNotHeavier = 0;
        reliefRefDrift = reliefRefNoRoom = reliefRefInvalid = 0;
        Arrays.fill(reliefDist, 0);
        Arrays.fill(reliefRefDriftDist, 0);
        reliefExamples.clear();
        graceBounce = graceDeleted = 0;
        notesSeen = 0;
        partTally.clear();
        instrumentTally.clear();
        long[] local = localDisplaced.get();
        local[0] = local[1] = 0; // other threads' locals persist; harmless since endPart diffs
        Arrays.fill(notesPerLine, 0);
        Arrays.fill(onsetsPerLine, 0);
        occupiedLines = notesOnLines = duplicatePitchNotes = 0;
        sweepDropped = sweepStarts = sweepEnds = sweepSplitLines = 0;
        Arrays.fill(sweepDropGap, 0);
        Arrays.fill(sweepStartError, 0);
        Arrays.fill(sweepEndError, 0);
        sweepExamples.clear();
        fwdExactLanding = 0;
        fwdExactLandingOverwrite = 0;
        fwdExactLandingEndsChain = 0;
        Arrays.fill(fwdExactLandingWeight, 0);
        endDriftDeleted = endDriftShorterThanMin = 0;
        Arrays.fill(endDriftRaw, 0);
        Arrays.fill(endDriftResidual, 0);
        Arrays.fill(endDriftDuration, 0);
        endDriftExamples.clear();
        startDriftDeleted = startDriftBackward = startDriftOverMinimum = 0;
        Arrays.fill(startDriftShift, 0);
        Arrays.fill(startDriftDuration, 0);
        Arrays.fill(startDriftTier, 0);
        startDriftExamples.clear();
        endShiftedNotes = endShiftedCandidates = 0;
        Arrays.fill(endShiftedDist, 0);
        endShiftedExamples.clear();
        endShiftedNotes = endShiftedCandidates = shiftAdjacent = shiftWithPrev = 0;
        Arrays.fill(endShiftedDist, 0);
        Arrays.fill(shiftGapToPrev, 0);
        endShiftedExamples.clear();
        dissonanceClusters = dissonancePairs = dissonanceDropped = 0;
        dissonanceKeptOverlap = dissonanceKeptBothLong = dissonanceSkippedBend = 0;
        tieOnSnappedDuration = tieOnOrigDuration = 0;
        bothLongSnapped = bothLongOrig = 0;
        Arrays.fill(dissonanceClusterSize, 0);
        Arrays.fill(dissonanceDroppedInterval, 0);
        dissonanceExamples.clear();
        partInput.clear();
        endOverwrite = endMergeBlocker = endLastMerge = endLastAdded = 0;
        endOverwriteFwd = endMergeBlockerFwd = endLastMergeFwd = 0;
        endSafetyLine = endSafetyExists = 0;
        Arrays.fill(endOverwriteDist, 0);
        Arrays.fill(endMergeBlockerDist, 0);
        Arrays.fill(endLastMergeDist, 0);
        endLastAddedExamples.clear();
        endMovedStarts = endMovedStartsGrace = endMovedByShift = endMovedByOverwrite = 0;
        Arrays.fill(endMovedStartDist, 0);
        Arrays.fill(endMovedStartDistGrace, 0);
        safetyLineNonSustain = safetyLineMergeWasLegal = safetyLineMixedSustain = 0;
        endSafetyCeil = endSafetyCeilRefusedPlucked = endSafetyCeilNoRoom = 0;
        Arrays.fill(endSafetyCeilDist, 0);
        Arrays.fill(endLastMergeDistFwd, 0);
        Arrays.fill(endLastMergeDistBack, 0);
        collapseOutOfOrder = 0;
        conflictEntered = conflictExits = 0;
        exitExactMatch = exitNewAnchor = exitEndCandidate = exitUnhandled = 0;
        lrMergeBlocker = lrMergeCeil = lrNewLine = lrCeilFallback = 0;
        Arrays.fill(intervalError, 0);
    }
}
