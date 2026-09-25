package com.digero;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.digero.maestro.abc.AbcMerger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.Note;
import com.digero.common.midi.TimeSignature;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.QuantizedTimingInfo;
import com.digero.maestro.midi.AbcNoteEvent;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.view.MiscSettings;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Golden-master characterisation test for the old removeDuplicateNotes (v1).
 *
 * This does not assert that the method is CORRECT. It asserts that it is UNCHANGED. Every
 * input combination is run and its exact output recorded; any behavioral difference at all
 * makes the test fail, including flipping a single relational operator.
 *
 * The tick and velocity grids are chosen so that every comparison in the method is exercised
 * with operands that are equal, less-than, and greater-than. That is what makes a > to >=
 * change detectable: an operator only reveals itself at the boundary where its operands are
 * equal, so each boundary must appear in the matrix.
 *
 * FIRST RUN
 *   mvn test -Dtest=RemoveDuplicateNotesCharacterisationTest -Dcharacterise.record=true
 * writes the golden file. Inspect it, commit it, then never record again - re-recording after
 * a change silently blesses whatever the change did, which defeats the point.
 */
class RemoveDuplicateNotesCharacterisationTest {

    /** Directory holding the golden files, one per mergeVersion. Override with -DcharacteriseGolden=<dir>. */
    private static final String GOLDEN_DIR = System.getProperty("characteriseGolden",
            "srcTest/resources/com/digero");

    /** Version to record, e.g. -DcharacteriseRecord=2. Never records more than one, so adding
     *  a v3 later cannot silently re-bless v1 and v2 in the same run. 0 means compare only. */
    private static final int RECORD_VERSION = Integer.getInteger("characteriseRecord", 0);

    /** Recording over an existing golden file blesses whatever changed, so it needs saying twice. */
    private static final boolean FORCE = Boolean.getBoolean("characteriseForce");

    /** How many mismatches to print before truncating, so a wholesale change stays readable. */
    private static final int MAX_REPORTED = Integer.getInteger("characteriseMaxReported", 400);

    private static Path goldenFor(int mergeVersion) {
        return Path.of(GOLDEN_DIR, "removeDuplicateNotes.v" + mergeVersion + ".golden.txt");
    }

    private AbcMerger merger;
    private Method removeDuplicateNotes;
    private Field prioritizeField;
    private boolean prio;
    private static FakeQTM qtm;

    /**
     * Note LENGTHS. The comparable test is floor(4*min/3) >= max, so exact boundaries occur
     * where min is divisible by 3:
     *
     *      (3,4)   floor(4)  >= 4   comparable, exactly  <- catches >= flipped to >
     *      (6,8)   floor(8)  >= 8   comparable, exactly
     *      (4,6)   floor(5)  >= 6   NOT, one step outside
     *      (6,12)  floor(8)  >= 12  NOT, far outside
     *      (4,4)   ratio 1          always comparable
     *
     * Four non-zero values is enough: 3 and 6 supply the two exact boundaries, 4 pairs with
     * both of them from either side, and 12 sits clear of any plausible threshold so it must
     * never reclassify. 0 keeps both zero-length branches firing.
     */
    private static final long[] LENGTHS = { 0, 3, 4, 6, 12 };

    /**
     * Where second starts, swept across first's span. Every value that also appears in LENGTHS
     * gives first.getEndTick() == second.getStartTick() exactly - the equality separating <=
     * from < in the "already turned off" test - and 0 gives every SAME_START case.
     */
    private static final long[] STARTS = { 0, 3, 4, 6, 12 };

    // Real Dynamics.midiVol steps, so Dynamics.fromMidiVelocity() lands on distinct abcVol
    // values. 6258 compares abcVol rather than raw velocity, so equal-abcVol pairs matter too.
    private static final int[] VELOCITIES = { 32, 64, 96 };

    private static final LotroInstrument[] INSTRUMENTS = {
            LotroInstrument.BASIC_FLUTE,     // sustainable
            LotroInstrument.LUTE_OF_AGES,    // not sustainable
    };

    private static class FakeQTM extends QuantizedTimingInfo {
        public FakeQTM(SequenceInfo source) throws Exception {
            super(source, 125, 125, TimeSignature.FOUR_FOUR, false, null, false, 1, true);
        }

        @Override public int getPrimaryExportTempoBPM() { return 125; }
        @Override public long tickToMicrosABCOrganic(long tick) { return tick * 1000L; }
        @Override public long microsToTickABCOrganic(long micros) { return micros / 1000L; }
        @Override public long microsToTickABCOrganicRoundUp(long micros) { return (long) Math.ceil(micros / 1000.0); }
        @Override public long getGridSizeTicks(long tick, AbcPart part) { return 60L; }
        @Override public long quantizeFloor(long tick, AbcPart part) { return (tick / 60) * 60; }
    }

    @BeforeEach
    void setUp() throws Exception {
        Sequence seq = new Sequence(Sequence.PPQ, 1000);
        Track t = seq.createTrack();
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0));
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1000));

        SequenceInfo seqInfo = SequenceInfo.fromSequence(seq, new MiscSettings(null, true));
        qtm = new FakeQTM(seqInfo);

        removeDuplicateNotes = AbcMerger.class.getDeclaredMethod(
                "removeDuplicateNotes", List.class, LotroInstrument.class);
        removeDuplicateNotes.setAccessible(true);

        prioritizeField = AbcMerger.class.getDeclaredField("prioritizeUninteruptedLongNotes");
        prioritizeField.setAccessible(true);
    }

    // ==================================================================================
    //                                    THE TEST
    // ==================================================================================

    @ParameterizedTest(name = "mergeVersion={0}")
    @ValueSource(ints = { 1, 2 })
    @DisplayName("removeDuplicateNotes: behaviour is byte-for-byte unchanged")
    void characterise(int mergeVersion) throws Exception {
        merger = new AbcMerger(mergeVersion);
        Map<String, String> actual = new LinkedHashMap<>();

        for (boolean p : new boolean[] { false, true }) {
            prio = p;
            prioritizeField.setBoolean(merger, p);
            for (LotroInstrument instrument : INSTRUMENTS) {
                addPairCases(actual, instrument);
                addTripleCases(actual, instrument);
                addDifferentPitchCases(actual, instrument);
            }
        }

        if (RECORD_VERSION != 0) {
            if (RECORD_VERSION == mergeVersion) {
                record(actual, mergeVersion);
            }
            // Recording one version: the others must not be compared against files that are
            // about to be replaced, or a record run always reports a spurious failure.
            return;
        }
        compare(actual, mergeVersion);
    }

    /**
     * Two same-pitch notes, every geometry and velocity combination.
     *
     * first is always anchored at tick 0 so that firstEnd alone decides zero-length, subset,
     * extends-beyond and same-end. secondStart sweeps across firstEnd so the "already turned
     * off" test at the top of the loop is hit both at and either side of its boundary.
     */
    private void addPairCases(Map<String, String> out, LotroInstrument instrument) throws Exception {
        for (long firstEnd : LENGTHS) {
            for (long secondStart : STARTS) {
                for (long secondLen : LENGTHS) {
                    for (int v1 : VELOCITIES) {
                        for (int v2 : VELOCITIES) {
                            List<AbcNoteEvent> events = new ArrayList<>();
                            events.add(note(Note.C4, v1, 0, firstEnd));
                            events.add(note(Note.C4, v2, secondStart, secondStart + secondLen));
                            String key = String.format(
                                    "pair|%s|first=0-%d,v%d|second=%d-%d,v%d",
                                    instrument.name(), firstEnd, v1,
                                    secondStart, secondStart + secondLen, v2);
                            out.put("prio=" + prio + "|" + key, run(events, instrument));
                        }
                    }
                }
            }
        }
    }

    /**
     * Three same-pitch notes. The AFTER_THIRD branch is unreachable with two notes: it needs a
     * "third" to have been inserted by the subset branch, which then sits in notesOn with a
     * start LATER than the next arriving note. A(long) + B(nested) inserts a third at B's end,
     * and C starting between B's start and that third's start reaches it.
     */
    private void addTripleCases(Map<String, String> out, LotroInstrument instrument) throws Exception {
        long[][] triples = {
                // {aStart,aEnd, bStart,bEnd, cStart,cEnd}
                { 0, 32,  8, 16,  12, 20 },   // C overlaps the inserted third
                { 0, 32,  8, 16,  12, 40 },   // C extends past the third
                { 0, 32,  8, 16,  16, 24 },   // C starts exactly at the third's start
                { 0, 32,  8, 16,  12, 16 },   // C ends exactly at the third's start
                { 0, 32,  8, 32,  12, 20 },   // B ends with A, so no third is created
                { 0, 32,  0, 16,  16, 32 },   // B shares A's start
                { 0, 16, 16, 32,  32, 48 },   // strictly sequential, nothing should merge
                { 0, 12,  0,  9,   9, 21 },   // SAME_START at (9,12): floor(12) >= 12, exactly comparable
        };
        for (long[] tr : triples) {
            for (int v1 : VELOCITIES) {
                for (int v2 : VELOCITIES) {
                    List<AbcNoteEvent> events = new ArrayList<>();
                    events.add(note(Note.C4, v1, tr[0], tr[1]));
                    events.add(note(Note.C4, v2, tr[2], tr[3]));
                    events.add(note(Note.C4, v1, tr[4], tr[5]));
                    String key = String.format(
                            "triple|%s|%d-%d,v%d|%d-%d,v%d|%d-%d,v%d",
                            instrument.name(), tr[0], tr[1], v1, tr[2], tr[3], v2, tr[4], tr[5], v1);
                    out.put("prio=" + prio + "|" + key, run(events, instrument));
                }
            }
        }
    }

    /** Different pitches must pass through completely untouched - the control group. */
    private void addDifferentPitchCases(Map<String, String> out, LotroInstrument instrument) throws Exception {
        for (long firstEnd : LENGTHS) {
            for (long secondStart : STARTS) {
                List<AbcNoteEvent> events = new ArrayList<>();
                events.add(note(Note.C4, 64, 0, firstEnd));
                events.add(note(Note.D4, 96, secondStart, secondStart + 8));
                String key = String.format("otherPitch|%s|first=0-%d|second=%d-%d",
                        instrument.name(), firstEnd, secondStart, secondStart + 8);
                out.put("prio=" + prio + "|" + key, run(events, instrument));
            }
        }
    }

    // ==================================================================================
    //                                    PLUMBING
    // ==================================================================================

    private static AbcNoteEvent note(Note n, int velocity, long start, long end) {
        return new AbcNoteEvent(n, velocity, start, end, qtm, null);
    }

    /**
     * Runs the method and serialises the result.
     *
     * List order is preserved rather than re-sorted: thirds are appended at the end and trash
     * removed, so the ordering is itself part of the behavior being locked down.
     */
    private String run(List<AbcNoteEvent> events, LotroInstrument instrument) throws Exception {
        Collections.sort(events);// production sorts before calling
        removeDuplicateNotes.invoke(merger, events, instrument);
        if (events.isEmpty()) return "<empty>";
        StringBuilder sb = new StringBuilder();
        for (AbcNoteEvent e : events) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(e.note.id).append('@')
              .append(e.getStartTick()).append('-').append(e.getEndTick())
              .append(",v").append(e.velocity);
        }
        return sb.toString();
    }

    private void record(Map<String, String> actual, int mergeVersion) throws IOException {
        Path golden = goldenFor(mergeVersion);
        assertTrue(!Files.exists(golden) || FORCE,
                golden + " already exists. Re-recording blesses whatever changed, which defeats "
                        + "the purpose of this test. If you really mean it, add -DcharacteriseForce=true");

        List<String> lines = new ArrayList<>(actual.size() + 4);
        lines.add("# Recorded behaviour of removeDuplicateNotes at mergeVersion=" + mergeVersion + ".");
        lines.add("# Do not edit by hand, and do not regenerate after a change - if this test");
        lines.add("# fails, the method behaves differently and that is the finding.");
        lines.add("# cases=" + actual.size());
        actual.forEach((k, v) -> lines.add(k + " => " + v));
        Files.createDirectories(golden.getParent());
        Files.write(golden, lines, StandardCharsets.UTF_8);
        System.err.println("Recorded " + actual.size() + " cases to " + golden.toAbsolutePath());
    }

    private void compare(Map<String, String> actual, int mergeVersion) throws IOException {
        Path golden = goldenFor(mergeVersion);
        assertTrue(Files.exists(golden),
                "Golden file missing: " + golden.toAbsolutePath()
                        + "\nRecord it first with -DcharacteriseRecord=" + mergeVersion);

        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(golden, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            int at = line.indexOf(" => ");
            assertTrue(at > 0, "Malformed golden line: " + line);
            expected.put(line.substring(0, at), line.substring(at + 4));
        }

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : actual.entrySet()) {
            String want = expected.get(e.getKey());
            if (want == null) {
                problems.add("NEW CASE (not in golden): " + e.getKey());
            } else if (!want.equals(e.getValue())) {
                problems.add("CHANGED: " + e.getKey()
                        + "\n    was: " + want
                        + "\n    now: " + e.getValue());
            }
        }
        for (String key : expected.keySet()) {
            if (!actual.containsKey(key)) problems.add("MISSING CASE: " + key);
        }

        if (!problems.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(problems.size()).append(" of ").append(actual.size())
                    .append(" cases differ from the recorded behaviour at mergeVersion=")
                    .append(mergeVersion).append(".\n");
            problems.stream().limit(MAX_REPORTED).forEach(p -> sb.append("  ").append(p).append('\n'));
            if (problems.size() > MAX_REPORTED) {
                sb.append("  ... and ").append(problems.size() - MAX_REPORTED).append(" more\n");
            }
            fail(sb.toString());
        }
    }
}