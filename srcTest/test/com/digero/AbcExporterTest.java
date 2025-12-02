package com.digero;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;

import javax.sound.midi.*;

import com.digero.common.abc.LotroInstrument;
import com.digero.maestro.view.SettingsDialog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import com.digero.common.midi.KeySignature;
import com.digero.common.midi.Note;
import com.digero.common.midi.TimeSignature;
import com.digero.maestro.abc.AbcExporter;
import com.digero.maestro.abc.AbcPart;
import com.digero.maestro.abc.QuantizedTimingInfo;
import com.digero.maestro.midi.AbcNoteEvent;
import com.digero.maestro.midi.BentAbcNoteEvent;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.view.MiscSettings;
import org.junit.jupiter.api.TestInfo;

class AbcExporterTest {

    private AbcExporter exporter;
    private AbcPart part; // Can be null
    private Method testMethod;// expandPitchBendsOrganicImproved
    private Method createGridMethod; // createGridVersion2
    private Method createSnapMethod;// snap to grid
    private FakeQTM qtm;
    private long barTicks;

    // We need to inject a fake QTM that acts like 1 tick = 1 ms
    private static class FakeQTM extends QuantizedTimingInfo {
        // We need a constructor that compiles.
        // Since we can't easily call super(...) with valid args without a real file,
        // we have to use Reflection to forcefully create this object or use a valid one.
        
        // Use real qtm, but override the conversion methods.
        public FakeQTM(SequenceInfo source) throws Exception {
            super(source, 125, 125, TimeSignature.FOUR_FOUR, false, null, false, 1, true);
        }
        
        @Override public int getPrimaryExportTempoBPM() { return 125; }
        @Override public long tickToMicrosABCOrganic(long tick) { return tick * 1000L; }
        @Override public long microsToTickABCOrganic(long micros) { return micros / 1000L; }
        @Override public long microsToTickABCOrganicRoundUp(long micros) { return (long) Math.ceil(micros / 1000.0); }
        @Override public long getGridSizeTicks(long tick, AbcPart part) { return 60L; }
        @Override public long quantizeFloor(long tick, AbcPart part) {
            // Simple grid of 60ms
            return (tick / 60) * 60;
        }
    }

    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        String displayName = testInfo.getDisplayName();
        System.err.println("Starting test: " + displayName);

        // 1. Create a Dummy MIDI File to satisfy SequenceInfo
        Sequence seq = new Sequence(Sequence.PPQ, 1000);
        Track t = seq.createTrack();
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0));
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1000));

        // 2. Create SequenceInfo
        SequenceInfo seqInfo = SequenceInfo.fromSequence(seq, new MiscSettings(null,true));
        barTicks = 4000L;// 4 s
        
        // 3. Create our FakeQTM
        qtm = new FakeQTM(seqInfo);

        part = new AbcPart(null);
        part.setInstrument(LotroInstrument.LUTE_OF_AGES);

        // 4. Create Exporter with the Fake QTM
        exporter = new AbcExporter(
                Collections.emptyList(), 
                qtm, 
                KeySignature.C_MAJOR, 
                new SettingsDialog.MockMetadataSource(null),
                false, 
                true
        );
        
        // 5. Inject the QTM into exporter (just to be sure, though constructor set it)
        Field qtmField = AbcExporter.class.getDeclaredField("qtm");
        qtmField.setAccessible(true);
        qtmField.set(exporter, qtm);

        // 6. Unlock methods
        testMethod = AbcExporter.class.getDeclaredMethod("expandPitchBendsOrganicImproved", AbcNoteEvent.class);
        testMethod.setAccessible(true);

        createGridMethod = AbcExporter.class.getDeclaredMethod("createGridVersion2", List.class, long.class, AbcPart.class, long.class);
        createGridMethod.setAccessible(true);

        createSnapMethod = AbcExporter.class.getDeclaredMethod("snapNotesToGrid", List.class, NavigableSet.class, long.class, AbcPart.class);
        createSnapMethod.setAccessible(true);
    }

    public record NoteDef(long startTick, long endTick, Note note) {}

    public List<AbcNoteEvent> createNotes(NoteDef... definitions) {
        return Arrays.stream(definitions)
                .map(def -> new AbcNoteEvent(def.note(), 64, def.startTick(), def.endTick(), null, null))
                .toList();
    }

    // Helper to invoke the private createGridVersion2
    @SuppressWarnings("unchecked")
    private NavigableSet<Long> invokeCreateGrid(List<AbcNoteEvent> events, long minMicros, long barTicks) throws Exception {
        NavigableSet<Long> set = (NavigableSet<Long>) createGridMethod.invoke(exporter, events, minMicros, part, barTicks);
        System.err.flush();
        return set;
    }

    // Helper to invoke the private createGridVersion2
    @SuppressWarnings("unchecked")
    private List<AbcNoteEvent> invokeSnapGrid(List<AbcNoteEvent> notes, long minMicros, NavigableSet<Long> grid) throws Exception {
        return (List<AbcNoteEvent>) createSnapMethod.invoke(exporter, notes, grid, minMicros, part);
    }

    // --- Helpers ---
    private BentAbcNoteEvent createBentNote(long durationMs, int startBend, int... changes) {
        Note baseNote = Note.C4; 
        BentMidiNoteEvent midiEvent = new BentMidiNoteEvent(baseNote, 64, 0, durationMs, null, 0);
        
        midiEvent.addBend(0, startBend);
        for (int i = 0; i < changes.length; i += 2) {
            midiEvent.addBend((long)changes[i], changes[i+1]);
        }
        
        return new BentAbcNoteEvent(baseNote, 64, 0, durationMs, null, midiEvent);
    }

    @SuppressWarnings("unchecked")
    private List<AbcNoteEvent> runTest(BentAbcNoteEvent note) throws Exception {
        return (List<AbcNoteEvent>) testMethod.invoke(exporter, note);
    }

    private void assertSegment(AbcNoteEvent event, long start, long end, int bend) {
        assertEquals(start, event.getStartTick(), "Start Tick");
        assertEquals(end, event.getEndTick(), "End Tick");
        assertEquals(Note.C4.id + bend, event.note.id, "Pitch (Bend)");
    }

    private void printSegments(List<AbcNoteEvent> events) {
        System.err.println("Result Segments:");
        for (AbcNoteEvent e : events) {
            System.err.printf("[%d - %d] Pitch: %d (Bend %+d)%n",
                    e.getStartTick(), e.getEndTick(), e.note.id, e.note.id - Note.C4.id);
        }
        System.err.println("---");
    }

    // ==================================================================================
    //                                   GRID TESTS
    // ==================================================================================

    @Test
    @DisplayName("Grid 1: Simple Aligned Notes (No Bounce)")
    void testGridSnapSimple() throws Exception {
        // Note: 0 to 1000 ms (Perfectly aligned with 0)
        var events = createNotes(
                new NoteDef(0, 1000, Note.C4)
        );

        long minMicros = 60000; // 60ms

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);
        List<AbcNoteEvent> snapped = invokeSnapGrid(events, minMicros, grid);

        assertEquals(1, snapped.size());
        assertEquals(0, snapped.get(0).getStartTick());
        assertEquals(1000, snapped.get(0).getEndTick());
    }

    @Test
    @DisplayName("Grid: Forward Bounce (Start-Start Conflict)")
    void testForwardBounce_Arpeggio() throws Exception {
        long minMicros = 60000;

        // Note 1: Anchor Note (Strong). Starts at 1000.
        // Note 2: Bouncing Note. Starts at 1040 (40ms delay).
        // 40ms < 60ms (Min). Conflict!
        // 40ms > 30ms (Halfway). Bounce condition met!
        // Should bounce to 1000 + 60 = 1060.

        var events = createNotes(
                new NoteDef(1000, 2000, Note.C4),
                new NoteDef(1040, 1540, Note.D4)
        );

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);
        List<AbcNoteEvent> snapped = invokeSnapGrid(events, minMicros, grid);

        assertEquals(1000, snapped.get(0).getStartTick());
        assertEquals(1060, snapped.get(1).getStartTick());
    }

    @Test
    @DisplayName("Grid: Backward Bounce (Grace Note)")
    void testBackwardBounce_GraceNote() throws Exception {
        long minMicros = 60000;

        // Note 1: Grace note. MUST BE SHORT (<50ms) to get Grace Weight.
        // Note 2: Anchor. Strong. Starts 1000.
        var events = createNotes(
                new NoteDef(980, 1020, Note.C4),
                new NoteDef(1000, 2000, Note.D4)
        );

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);
        List<AbcNoteEvent> snapped = invokeSnapGrid(events, minMicros, grid);

        assertEquals(1000, snapped.get(1).getStartTick()); // Anchor
        assertEquals(940, snapped.get(0).getStartTick());  // Bounced Back (1000 - 60)
    }

    @Test
    @DisplayName("Grid: Arpeggio (Snap vs Bounce)")
    void testArpeggio() throws Exception {
        long minMicros = 60000;

        // N1: 1000. Anchor.
        // N2: 1020. 20ms gap. 20 < 30 (Halfway). Should SNAP to 1000.
        // N3: 1040. 40ms gap. 40 > 30 (Halfway). Should BOUNCE to 1060.

        var events = createNotes(
                new NoteDef(1000, 2000, Note.C4),
                new NoteDef(1020, 2020, Note.D4),
                new NoteDef(1040, 2040, Note.E4)
        );

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);
        List<AbcNoteEvent> snapped = invokeSnapGrid(events, minMicros, grid);

        assertEquals(1000, snapped.get(0).getStartTick());
        assertEquals(1000, snapped.get(1).getStartTick()); // Snapped (Block Chord)
        assertEquals(1060, snapped.get(2).getStartTick()); // Bounced (Arpeggio)
    }

    @Test
    @DisplayName("Grid: Arpeggio (Snap vs Bounce)")
    void testArpeggioExpansion() throws Exception {
        long minMicros = 60000;

        var events = createNotes(
                new NoteDef(1000, 2000, Note.C4),
                new NoteDef(1041, 2000, Note.D4),
                new NoteDef(1081, 2000, Note.C2),
                new NoteDef(1121, 2000, Note.D2),
                new NoteDef(1161, 2000, Note.E4)
        );

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);
        List<AbcNoteEvent> snapped = invokeSnapGrid(events, minMicros, grid);

        assertEquals(1000, snapped.get(0).getStartTick());
        assertEquals(1060, snapped.get(1).getStartTick()); // Bounced (Arpeggio)
        assertEquals(1120, snapped.get(2).getStartTick()); // Bounced (Arpeggio cascade)
        assertEquals(1180, snapped.get(3).getStartTick()); // Snapped
        assertEquals(1180, snapped.get(4).getStartTick()); // Snapped
    }

    @Test
    @DisplayName("Grid: Arpeggio (Snap vs Bounce)")
    void testArpeggioExpansionPlus() throws Exception {
        long minMicros = 60000;

        var events = createNotes(
                new NoteDef(1000, 2000, Note.C4),
                new NoteDef(1059, 2000, Note.D4),
                new NoteDef(1118, 2000, Note.C2),
                new NoteDef(1177, 2000, Note.D2),
                new NoteDef(1236, 2000, Note.E4)
        );

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);
        List<AbcNoteEvent> snapped = invokeSnapGrid(events, minMicros, grid);

        assertEquals(1000, snapped.get(0).getStartTick());
        assertEquals(1060, snapped.get(1).getStartTick()); //
        assertEquals(1120, snapped.get(2).getStartTick()); //
        assertEquals(1180, snapped.get(3).getStartTick()); //
        assertEquals(1240, snapped.get(4).getStartTick()); //
    }

    @Test
    @DisplayName("Grid: Chord Arpeggio (Snap vs Bounce)")
    void testChordArpeggio() throws Exception {
        long minMicros = 60000;

        // N1: 1000. Anchor.
        // N2: 1020. 20ms gap. 20 < 30 (Halfway). Should SNAP to 1000.
        // N3: 1040. 40ms gap. 40 > 30 (Halfway). Should BOUNCE to 1060.

        var events = createNotes(
                new NoteDef(1000, 2000, Note.C4),
                new NoteDef(1000, 2000, Note.D4),
                new NoteDef(1020, 2000, Note.C2),
                new NoteDef(1020, 2000, Note.C5),
                new NoteDef(1040, 2000, Note.D2),
                new NoteDef(1040, 2000, Note.E4)
        );

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);
        List<AbcNoteEvent> snapped = invokeSnapGrid(events, minMicros, grid);

        assertEquals(1000, snapped.get(0).getStartTick());
        assertEquals(1000, snapped.get(1).getStartTick());
        assertEquals(1000, snapped.get(2).getStartTick()); // Snapped (Block Chord)
        assertEquals(1000, snapped.get(3).getStartTick()); // Snapped (Block Chord)
        assertEquals(1060, snapped.get(4).getStartTick()); // Bounced (Arpeggio)
        assertEquals(1060, snapped.get(5).getStartTick()); // Bounced (Arpeggio)
    }

    @Test
    @DisplayName("Grid 5: Safety End (Short Note Preservation)")
    void testSafetyEnd_ShortNote() throws Exception {
        long minMicros = 60000;

        // Note: 0 to 40 ms.
        // Start at 0. End at 40.
        // 40ms is too close to 0 (< 60ms).
        // Grid generation rejects the candidate at 40.
        // Without Safety End, snapNotesToGrid would see grid lines at 0 and (maybe) 1000.
        // It would delete the note or stretch it massively.
        // With Safety End, it should insert a line at 60ms.

        var events = createNotes(
                new NoteDef(0, 40, Note.C4)
        );

        part.setInstrument(LotroInstrument.BASIC_FLUTE);

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);

        // Verify grid contains safety line
        assertTrue(grid.contains(60000L), "Grid should contain safety line at 60ms");

        List<AbcNoteEvent> snapped = invokeSnapGrid(events, minMicros, grid);

        // Should extend to 60ms (min duration)
        assertEquals(0, snapped.get(0).getStartTick());
        assertEquals(60, snapped.get(0).getEndTick());
    }

    @Test
    @DisplayName("Grid: Overwrite Weak Ghost End")
    void testOverwriteWeakGhostEnd() throws Exception {
        long minMicros = 60000;

        // Note A: Bouncer. 1040 -> 1060.
        // New End: 1060 + 2000 = 3060 (Weight 2).
        // Ghost End: 3040 (Weight 1).
        // Note B: Strong End. Ends at 3040. (Weight 10)
        // Cheat: make nB weight higher by making it end at a time that gets WEIGHT_END
        // Actually, standard END is weight 1.
        // We need a Note C starting at 3040 to give it weight 10?
        // YES. Add a Note C starting at 3040.
        var events = createNotes(
                new NoteDef(1040, 3040, Note.C4),
                new NoteDef(1040, 3040, Note.D4),
                new NoteDef(3040, 4040, Note.C2)
        );

        // Scenario:
        // nA bounces to 1060. Adds New End at 3060.
        // nC starts at 3040.
        // 3040 vs 3060. 20ms diff. Conflict.
        // nC (Start, 10) > nA New End (End, 2).
        // nC should win. 3040 stays. 3060 removed.


        part.setInstrument(LotroInstrument.BASIC_FLUTE);

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);

        assertTrue(grid.contains(3040000L), "Strong Start should win");
        assertFalse(grid.contains(3060000L), "Weak Bounced End should be overwritten");
    }

    @Test
    @DisplayName("Grid 7: Weight Priority (Solo vs Grace)")
    void testGridWeightPriority() throws Exception {
        long minMicros = 60000; // 60ms

        // Note 1: Strong "Solo" note (Length 200ms -> Weight 10)
        // Starts at 1000.
        // Note 2: Weak "Grace" note (Length 40ms -> Weight 5)
        // Starts at 1020.
        // 20ms gap < 60ms. Conflict.
        // Since Strong (10) > Weak (5), Strong should win.
        // Weak should be rejected (and since bouncing requires previous neighbor context or specific grace logic,
        // this test isolates just the grid placement logic: 1000 stays, 1020 goes).
        var events = createNotes(
                new NoteDef(1000, 1200, Note.C4),
                new NoteDef(1020, 1060, Note.C2)
        );

        part.setInstrument(LotroInstrument.BASIC_FLUTE);

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);

        // The Grid should contain 1000, but NOT 1020.
        assertTrue(grid.contains(1000000L), "Strong note should establish grid line");
        assertFalse(grid.contains(1020000L), "Weak note should be blocked by strong note");
    }

    @Test
    @DisplayName("Grid 8: Long Gap Splitting (Silence)")
    void testGridLongGapSplitting() throws Exception {
        long minMicros = 60000;
        // Max Sustain is approx 5 seconds (5,000,000 micros)
        // We create a gap of 12 seconds.

        // Note 1: Ends at 0.
        // Note 2: Starts at 12,000,000 (12 seconds).

        var events = createNotes(
                new NoteDef(0, 1000, Note.D4),
                new NoteDef(12000, 13000, Note.C4)
        );

        part.setInstrument(LotroInstrument.BASIC_FLUTE);

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);

        // We expect grid points at 0 and 12,000,000.
        // BUT we also expect intermediate points because the gap > 5s.
        // Bar length is 4s (4000ms).
        // It should likely split at 4s (4,000,000) and 8s (8,000,000).

        assertTrue(grid.contains(0L));
        assertTrue(grid.contains(12_000_000L));

        // Check for inserted splits
        // We filter for points between start and end
        long count = grid.stream().filter(t -> t > 1000_000 && t < 12_000_000).count();
        assertTrue(count >= 1, "Should have split the long silence");

        // Optional: Check if it aligned to a bar (4s)
        // Note: exact logic depends on closestBarMicrosABC, but 4s or 5s is expected.
        boolean hasIntermediate = grid.contains(4_000_000L) || grid.contains(8000000L);
        assertTrue(hasIntermediate, "Split should be around bar lines or max sustain");
    }

    @Test
    @DisplayName("Grid 9: Sustain Buffer (Split)")
    void testGridSustainBuffer() throws Exception {
        long minMicros = 60000;
        // Buffer is 2 * minMicros = 120ms.
        // Max Sustain ~5s.
        // Create a gap of 5.1s (5100ms).
        // 5100 < 5000 + 120.
        // Should split.

        var events = createNotes(
                new NoteDef(0, 100, Note.D4),
                new NoteDef(100, 5200, Note.C4)
        );

        part.setInstrument(LotroInstrument.BASIC_FLUTE);

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);

        long count = grid.stream().filter(t -> t > 100*1000 && t < 5200*1000).count();
        assertEquals(1, count, "Gap of 5.1s should be split");
    }

    @Test
    @DisplayName("Grid 9b: Sustain Buffer (Not Split)")
    void testGridSustainBufferB() throws Exception {
        long minMicros = 60000;
        // Buffer is 2 * minMicros = 120ms.
        // Max Sustain ~7.5s.
        // Create a gap of 5.1s (5100ms).
        // Should not split due to using LM Fiddle.

        var events = createNotes(
                new NoteDef(0, 5200, Note.C4)
        );
        part.setInstrument(LotroInstrument.LONELY_MOUNTAIN_FIDDLE);

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);

        long count = grid.stream().filter(t -> t > 0 && t < 5200*1000).count();
        assertEquals(0, count, "Gap of 5.1s should not be split");
    }

    @Test
    @DisplayName("Grid 10: Conflict Resolution (Start blocks End)")
    void testGridStartBlocksEnd() throws Exception {
        long minMicros = 60000;

        // Note A: Ends at 1000 (Weight 1).

        // Note B: Starts at 1010 (Weight 10).
        // 10ms gap. Conflict.
        // Start (10) > End (1).
        // Start (1010) should exist. End (1000) should be dropped.

        var events = createNotes(
                new NoteDef(0, 1000, Note.C4),
                new NoteDef(1010, 2000, Note.E4)
        );

        part.setInstrument(LotroInstrument.BASIC_FLUTE);

        NavigableSet<Long> grid = invokeCreateGrid(events, minMicros, barTicks);

        assertTrue(grid.contains(1010000L), "Strong Start should win");
        assertFalse(grid.contains(1000000L), "Weak End should be blocked");

        // Verification: Note A will likely snap its end to 1010
        List<AbcNoteEvent> snapped = invokeSnapGrid(events, minMicros, grid);
        assertEquals(1010, snapped.get(0).getEndTick(), "Note A should extend to meet Note B");
    }

    // ==================================================================================
    //                              ORGANIC PITCH BEND TESTS
    // ==================================================================================

    @Test
    @DisplayName("Scenario 1: Short Survival (45ms stretched to 65ms)")
    void testScenario1() throws Exception {
        // 0(+1), 220(+2), 265(+1), 400(End)
        BentAbcNoteEvent note = createBentNote(400, 1, 220, 2, 265, 1);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        assertEquals(3, result.size());
        assertSegment(result.get(0), 0, 220, 1);
        assertSegment(result.get(1), 220, 285, 2); // Stretched from 45ms to 65ms
        assertSegment(result.get(2), 285, 400, 1);
    }

    @Test
    @DisplayName("Scenario 2: Short Ignored (10ms transient deleted)")
    void testScenario2() throws Exception {
        // 0(+1), 220(+2), 230(+1), 400(End)
        BentAbcNoteEvent note = createBentNote(400, 1, 220, 2, 230, 1);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        assertEquals(1, result.size());
        assertSegment(result.get(0), 0, 400, 1); // Continuous +1
    }

    @Test
    @DisplayName("Scenario 3: Deletion of Initial Bend (Start Glitch)")
    void testScenario3() throws Exception {
        // 0(+1), 2(+2), 60(-4)
        // End assumed > 60, let's say 150
        BentAbcNoteEvent note = createBentNote(150, 1, 2, 2, 60, -4);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        // 0-65 should be +2 (Dominant win over +1)
        // 65-150 should be -4
        assertEquals(2, result.size());
        assertSegment(result.get(0), 0, 65, 2);
        assertSegment(result.get(1), 65, 150, -4);
    }

    @Test
    @DisplayName("Scenario 4: Short Chaos (Slide Approximation)")
    void testScenario4() throws Exception {
        // 0(-1), 65(+2), 75(+3), 85(+4). End 150.
        BentAbcNoteEvent note = createBentNote(150, -1, 65, 2, 75, 3, 85, 4);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        // 0-65: -1
        // 65-150: +4 (+4 dominates the 65-130 window)
        assertEquals(2, result.size());
        assertSegment(result.get(0), 0, 65, -1);
        assertSegment(result.get(1), 65, 150, 4);
    }

    @Test
    @DisplayName("Scenario 5: Start Chaos (Short cant survive)")
    void testScenario5() throws Exception {
        // 0(+4), 100(+5), 105(+4). End 200.
        BentAbcNoteEvent note = createBentNote(200, 4, 100, 5, 105, 4);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        // +5 is only 5ms. Should be merged.
        assertEquals(1, result.size());
        assertSegment(result.get(0), 0, 200, 4);
    }

    @Test
    @DisplayName("Scenario 6: The Slide Sample")
    void testScenario6() throws Exception {
        // 0(+1), 20(+2), 40(+3), 80(+4), 200(+3). End 300.
        BentAbcNoteEvent note = createBentNote(300, 1, 20, 2, 40, 3, 80, 4, 200, 3);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        // Window 0-65: +1(20), +2(20), +3(25). Winner +3.
        // Window 65-130: +3(15), +4(50). Winner +4.
        // Window 130-195: +4(65). Winner +4.
        // Window 195-260: +4(5), +3(60). Winner +3.

        // Expected:
        // 0-80: +3 (Dominant +3 extends to its natural end at 80)
        // 80-200: +4
        // 200-300: +3

        // Note: The split points might vary slightly depending on exact lookahead,
        // but 0->65->... is expected.
        assertSegment(result.get(0), 0, 80, 3);
        assertSegment(result.get(1), 80, 200, 4);
        assertSegment(result.get(2), 200, 300, 3);
    }

    @Test
    @DisplayName("Scenario 7: Start Chaos 2")
    void testScenario7() throws Exception {
        // 0(+4), 100(+5), 101(+6), 102(+5), 105(+4), 200(+2). End 300.
        BentAbcNoteEvent note = createBentNote(300, 4, 100, 5, 101, 6, 102, 5, 105, 4, 200, 2);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        // 100-105 is garbage. Should stay +4.
        // Switch to +2 at 200.
        assertEquals(2, result.size());
        assertSegment(result.get(0), 0, 200, 4);
        assertSegment(result.get(1), 200, 300, 2);
    }

    @Test
    @DisplayName("Scenario 8: Exact Boundary")
    void testScenario8() throws Exception {
        // 0(+1), 65(+2). End 130.
        BentAbcNoteEvent note = createBentNote(130, 1, 65, 2);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        assertEquals(2, result.size());
        assertSegment(result.get(0), 0, 65, 1);
        assertSegment(result.get(1), 65, 130, 2);
    }

    @Test
    @DisplayName("Scenario 9: Dominant vs Look Ahead")
    void testScenario9() throws Exception {
        // 0(+1)[5ms], 5(+2)[60ms], 65(+3). End 130.
        BentAbcNoteEvent note = createBentNote(130, 1, 5, 2, 65, 3);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        // 0-65: +2 wins (60ms duration vs 5ms).
        // 65-130: +3 wins.
        assertEquals(2, result.size());
        assertSegment(result.get(0), 0, 65, 2);
        assertSegment(result.get(1), 65, 130, 3);
    }

    @Test
    @DisplayName("Scenario 10: Tail Glitch")
    void testScenario10() throws Exception {
        // 0(+1), 940(+2), 1000(End).
        // Tail = 60ms. Threshold 65ms.
        BentAbcNoteEvent note = createBentNote(1000, 1, 940, 2);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        // Tail < 65ms should be ignored.
        assertEquals(1, result.size());
        assertSegment(result.get(0), 0, 1000, 1);
    }

    @Test
    @DisplayName("Scenario 11: Loop test")
    void testScenario11() throws Exception {
        BentAbcNoteEvent note = createBentNote(1000, 1,
                2,2, 65,3, 140,4, 141,5, 142,4, 206,7, 207,4, 272,6);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        assertEquals(4, result.size());
        assertSegment(result.get(0), 0, 65, 2);//65
        assertSegment(result.get(1), 65, 140, 3);//75
        assertSegment(result.get(2), 140, 272, 4);//132
        assertSegment(result.get(3), 272, 1000, 6);
    }
}