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
    private final long[] plainMergeDist = new long[BUCKETS];

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
        if (delta >= 0) { crushBack += notes; addDisplacement(crushBackDist, delta, notes); }
        else { crushFwd += notes; addDisplacement(crushFwdDist, delta, notes); }
        conflictExits += notes;
    }

    synchronized void collapse(int notes, long delta) {
        collapseMerge += notes;
        addDisplacement(collapseDist, delta, notes);
        conflictExits += notes;
    }

    synchronized void plainMerge(int notes, long delta) {
        plainMerge += notes;
        addDisplacement(plainMergeDist, delta, notes);
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

        out.add("-- displacement without a bounce --");
        out.add(String.format(Locale.ROOT, "crush back to floor=%d  >=30ms: %s", crushBack, over(crushBackDist, 6)));
        out.add("    " + hist(crushBackDist));
        out.add(String.format(Locale.ROOT, "crush fwd (leapfrog)=%d  >=30ms: %s", crushFwd, over(crushFwdDist, 6)));
        out.add("    " + hist(crushFwdDist));
        out.add(String.format(Locale.ROOT, "group collapse=%d  >=30ms: %s  (out-of-order rejected: %d)",
                collapseMerge, over(collapseDist, 6), collapseOutOfOrder));
        out.add(String.format(Locale.ROOT, "plain merge=%d  >=30ms: %s", plainMerge, over(plainMergeDist, 6)));
        out.add("    " + hist(plainMergeDist));

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

        out.add("-- conflict block reconciliation --");
        out.add(String.format(Locale.ROOT, "entered=%d  exits=%d  unaccounted=%d  <-- expect 0",
                conflictEntered, conflictExits, conflictEntered - conflictExits));
        out.add(String.format(Locale.ROOT, "exact match=%d  new anchor=%d  end candidates=%d  UNHANDLED=%d",
                exitExactMatch, exitNewAnchor, exitEndCandidate, exitUnhandled));

        out.add("-- ungoverned displacement by instrument --");
        out.add("   (crush to floor, group collapse, plain merge)");
        instrumentTally.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        (double) b.getValue()[1] / b.getValue()[0],
                        (double) a.getValue()[1] / a.getValue()[0]))
                .forEach(e -> out.add(String.format(Locale.ROOT, "  %6s  %8d/%-9d  %s",
                        pct(e.getValue()[1], e.getValue()[0]),
                        e.getValue()[1], e.getValue()[0], e.getKey())));

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
        Arrays.fill(plainMergeDist, 0);
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
        occupiedLines = notesOnLines = duplicatePitchNotes = 0;
    }
}
