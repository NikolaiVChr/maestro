package com.digero;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import javax.sound.midi.*;

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
    private Method testMethod;
    private FakeQTM qtm;

    // We need to inject a fake QTM that acts like 1 tick = 10 ms
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
        System.out.println("Starting test: " + displayName);

        // 1. Create a Dummy MIDI File to satisfy SequenceInfo
        Sequence seq = new Sequence(Sequence.PPQ, 1000);
        Track t = seq.createTrack();
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 100), 0));
        t.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1000));

        // 2. Create SequenceInfo
        SequenceInfo seqInfo = SequenceInfo.fromSequence(seq, new MiscSettings(null,true));
        
        // 3. Create our FakeQTM
        qtm = new FakeQTM(seqInfo);

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

        // 6. Unlock method
        testMethod = AbcExporter.class.getDeclaredMethod("expandPitchBendsOrganicImproved", AbcNoteEvent.class);
        testMethod.setAccessible(true);
    }

    // --- Helpers ---
    private BentAbcNoteEvent createNote(long durationMs, int startBend, int... changes) {
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
        System.out.println("Result Segments:");
        for (AbcNoteEvent e : events) {
            System.out.printf("[%d - %d] Pitch: %d (Bend %+d)%n",
                    e.getStartTick(), e.getEndTick(), e.note.id, e.note.id - Note.C4.id);
        }
        System.out.println("---");
    }

    // --- Tests ---

    @Test
    @DisplayName("Scenario 1: Short Survival (45ms stretched to 65ms)")
    void testScenario1() throws Exception {
        // 0(+1), 220(+2), 265(+1), 400(End)
        BentAbcNoteEvent note = createNote(400, 1, 220, 2, 265, 1);
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
        BentAbcNoteEvent note = createNote(400, 1, 220, 2, 230, 1);
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
        BentAbcNoteEvent note = createNote(150, 1, 2, 2, 60, -4);
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
        BentAbcNoteEvent note = createNote(150, -1, 65, 2, 75, 3, 85, 4);
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
        BentAbcNoteEvent note = createNote(200, 4, 100, 5, 105, 4);
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
        BentAbcNoteEvent note = createNote(300, 1, 20, 2, 40, 3, 80, 4, 200, 3);
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
        BentAbcNoteEvent note = createNote(300, 4, 100, 5, 101, 6, 102, 5, 105, 4, 200, 2);
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
        BentAbcNoteEvent note = createNote(130, 1, 65, 2);
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
        BentAbcNoteEvent note = createNote(130, 1, 5, 2, 65, 3);
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
        // Tail = 20ms. Threshold 65ms.
        BentAbcNoteEvent note = createNote(1000, 1, 940, 2);
        List<AbcNoteEvent> result = runTest(note);
        printSegments(result);

        // Tail < 65ms should be ignored.
        assertEquals(1, result.size());
        assertSegment(result.get(0), 0, 1000, 1);
    }
}