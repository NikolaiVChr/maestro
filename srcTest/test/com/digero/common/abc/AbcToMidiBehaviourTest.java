package com.digero.common.abc;

import static com.digero.common.abc.AbcCases.header;
import static com.digero.common.abc.AbcCases.tune;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.stream.Stream;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcRegion;
import com.digero.common.midi.Note;
import com.digero.common.util.LotroFileParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.digero.common.util.FileParseException;

/**
 * Tests that complement the snapshots.
 * <ul>
 * <li>{@link Properties}: things that must hold for every case, whatever its output is.</li>
 * <li>{@link Semantics}: a few musical rules with hand-computed expected ticks. Unlike the snapshots these say what
 * is <em>correct</em>, so they stay meaningful even if someone re-records the snapshots carelessly.</li>
 * </ul>
 */
class AbcToMidiBehaviourTest {

	/**
	 * Cases that deliberately use notes outside LotRO's range C2..C5, to test the range checks. Every other case
	 * must stay inside it, so that the strict profile doesn't fail early on a note that has nothing to do with what
	 * the case tests.
	 */
	static final Set<String> OUT_OF_RANGE_CASES = Set.of("notes_octaves_extreme", "lotro_note_too_low",
			"lotro_note_too_high");

	static Stream<AbcCase> casesInRange() {
		return AbcCases.all().stream().filter(c -> !OUT_OF_RANGE_CASES.contains(c.name()));
	}

	static Stream<Arguments> caseProfiles() {
		return AbcCases.all().stream()
				.filter(c -> !c.name().equals("lotro_too_many_notes")) // slow, and covered by its snapshot
				.flatMap(c -> c.profiles().stream().map(p -> Arguments.of(c, p)));
	}

	@BeforeEach
	void setUp() {
		TestSetup.beforeEachTest();
	}

	@Nested
	class Properties {

		/**
		 * All notes are within C2..C5 (Note.MIN_PLAYABLE..MAX_PLAYABLE). Checked on the LOTRO profile, where the
		 * pitch is the ABC pitch (no instrument octave shift), using the note regions. If the case ends in an error,
		 * the notes before the error are checked.
		 */
		@ParameterizedTest(name = "{0}")
		@MethodSource("com.digero.common.abc.AbcToMidiBehaviourTest#casesInRange")
		void notesStayWithinLotroRange(AbcCase abcCase) {
			AbcInfo info = new AbcInfo();
			ConversionDump.run(abcCase, Profile.LOTRO, true, info);

			List<String> outside = new ArrayList<>();
			NavigableSet<AbcRegion> regions = info.getRegions();
			if (regions != null) {
				for (AbcRegion r : regions) {
					Note note = r.getNote();
					if (note != null && note != Note.REST
							&& (note.id < Note.MIN_PLAYABLE.id || note.id > Note.MAX_PLAYABLE.id))
						outside.add("line " + r.getLine() + " col " + r.getStartIndex() + ": note " + note.id);
				}
			}
			assertEquals(List.of(), outside, "Notes outside C2..C5 (" + Note.MIN_PLAYABLE.id + ".."
					+ Note.MAX_PLAYABLE.id + "); change the case, or add it to OUT_OF_RANGE_CASES if intended");
		}

		/** A renamed or removed case must not silently drop out of the exception list. */
		@Test
		void outOfRangeCasesExist() {
			Set<String> names = AbcCases.all().stream().map(AbcCase::name).collect(java.util.stream.Collectors.toSet());
			for (String name : OUT_OF_RANGE_CASES)
				assertEquals(true, names.contains(name), "OUT_OF_RANGE_CASES names an unknown case: " + name);
		}

		@ParameterizedTest(name = "{0} {1}")
		@MethodSource("com.digero.common.abc.AbcToMidiBehaviourTest#caseProfiles")
		void conversionIsDeterministic(AbcCase abcCase, Profile profile) {
			String first = ConversionDump.run(abcCase, profile, true, new AbcInfo()).text();
			String second = ConversionDump.run(abcCase, profile, true, new AbcInfo()).text();
			TextDiff.assertSameText(first, second, "Second conversion differs from the first");
		}

		@ParameterizedTest(name = "{0} {1}")
		@MethodSource("com.digero.common.abc.AbcToMidiBehaviourTest#caseProfiles")
		void generateRegionsDoesNotChangeTheMidi(AbcCase abcCase, Profile profile) {
			ConversionDump.Result with = ConversionDump.run(abcCase, profile, true, new AbcInfo());
			ConversionDump.Result without = ConversionDump.run(abcCase, profile, false, new AbcInfo());
			assertEquals(with.error(), without.error());
			TextDiff.assertSameText(with.sequence(), without.sequence(), "MIDI differs without regions");
			TextDiff.assertSameText(with.abcInfo(), without.abcInfo(), "AbcInfo differs without regions");
			TextDiff.assertSameText(with.log(), without.log(), "Log differs without regions");
		}

		/** Params.abcInfo can be reused (AbcToMidi resets it); nothing from the previous song may leak. */
		@ParameterizedTest(name = "{0} {1}")
		@MethodSource("com.digero.common.abc.AbcToMidiBehaviourTest#caseProfiles")
		void reusedAbcInfoGivesSameResultAsFreshOne(AbcCase abcCase, Profile profile) {
			AbcCase previousSong = AbcCases.all().stream().filter(c -> c.name().equals("instruments_by_title"))
					.findFirst().orElseThrow();
			AbcInfo reused = new AbcInfo();
			ConversionDump.run(previousSong, Profile.PLAIN_MIDI, true, reused);

			String fresh = ConversionDump.run(abcCase, profile, true, new AbcInfo()).text();
			String afterReuse = ConversionDump.run(abcCase, profile, true, reused).text();
			TextDiff.assertSameText(fresh, afterReuse, "Reused AbcInfo gives a different result");
		}
	}

	/**
	 * Hand-computed expectations. All use the default header (M:4/4, L:1/8, Q:120, K:C), PLAIN_MIDI (no sample-length
	 * note-offs) and the default instrument (Lute of Ages, octave delta 0, so MIDI pitch = ABC pitch, c = 60). Ticks
	 * are expressed via the sequence resolution q (one quarter note), so they don't depend on the PPQN choice.
	 */
	@Nested
	class Semantics {

		record NoteEvent(long tick, boolean on, int pitch) {
			@Override
			public String toString() {
				return (on ? "on " : "off ") + pitch + "@" + tick;
			}
		}

		private static NoteEvent on(long tick, int pitch) {
			return new NoteEvent(tick, true, pitch);
		}

		private static NoteEvent off(long tick, int pitch) {
			return new NoteEvent(tick, false, pitch);
		}

		private Sequence convert(AbcCase abcCase) throws Exception {
			return ConversionDump.convert(abcCase, Profile.PLAIN_MIDI);
		}

		/** Note events of track 1, sorted by tick, then note-offs before note-ons, then pitch. */
		private static List<NoteEvent> noteEvents(Sequence sequence) {
			return noteEvents(sequence, 1);
		}

		private static List<NoteEvent> noteEvents(Sequence sequence, int trackIndex) {
			Track track = sequence.getTracks()[trackIndex];
			List<NoteEvent> events = new ArrayList<>();
			for (int i = 0; i < track.size(); i++) {
				MidiEvent event = track.get(i);
				if (event.getMessage() instanceof ShortMessage sm) {
					if (sm.getCommand() == ShortMessage.NOTE_ON)
						events.add(on(event.getTick(), sm.getData1()));
					else if (sm.getCommand() == ShortMessage.NOTE_OFF)
						events.add(off(event.getTick(), sm.getData1()));
				}
			}
			events.sort((a, b) -> a.tick != b.tick ? Long.compare(a.tick, b.tick)
					: a.on != b.on ? Boolean.compare(a.on, b.on) : Integer.compare(a.pitch, b.pitch));
			return events;
		}

		private static List<NoteEvent> noteOns(Sequence sequence) {
			return noteEvents(sequence).stream().filter(NoteEvent::on).toList();
		}

		private static List<NoteEvent> noteOns(Sequence sequence, int trackIndex) {
			return noteEvents(sequence, trackIndex).stream().filter(NoteEvent::on).toList();
		}

		@Test
		void eighthNotesFollowEachOther() throws Exception {
			Sequence s = convert(tune("semantic", "c d e f|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), off(q / 2, 60), on(q / 2, 62), off(q, 62), on(q, 64), off(3 * q / 2, 64),
					on(3 * q / 2, 65), off(2 * q, 65)), noteEvents(s));
		}

		@Test
		void octaveMarks() throws Exception {
			Sequence s = convert(tune("semantic", "C, C c c'|"));
			assertEquals(List.of(36, 48, 60, 72), noteOns(s).stream().map(NoteEvent::pitch).toList());
		}

		@Test
		void restTakesTime() throws Exception {
			Sequence s = convert(tune("semantic", "c z d|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(q, 62)), noteOns(s));
		}

		@Test
		void defaultNoteLengthIsSixteenthBelowThreeQuarterMeter() throws Exception {
			Sequence s = convert(tune("semantic", header("M:2/4", "-L"), "c d|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(q / 4, 62)), noteOns(s));
		}

		@Test
		void explicitNoteLength() throws Exception {
			Sequence s = convert(tune("semantic", header("L:1/4"), "c d|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(q, 62)), noteOns(s));
		}

		@Test
		void tieMergesIntoOneNote() throws Exception {
			Sequence s = convert(tune("semantic", "c-c d|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), off(q, 60), on(q, 62), off(3 * q / 2, 62)), noteEvents(s));
		}

		@Test
		void chordLastsAsLongAsItsShortestNote() throws Exception {
			Sequence s = convert(tune("semantic", "[c2e] g|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(0, 64), off(q / 2, 64), on(q / 2, 67), off(q, 60), off(q, 67)),
					noteEvents(s));
		}

		@Test
		void accidentalLastsUntilBarLine() throws Exception {
			Sequence s = convert(tune("semantic", "^c c | c|"));
			assertEquals(List.of(61, 61, 60), noteOns(s).stream().map(NoteEvent::pitch).toList());
		}

		@Test
		void keySignatureAndNatural() throws Exception {
			Sequence s = convert(tune("semantic", header("K:D"), "f c =f f | f|"));
			assertEquals(List.of(66, 61, 65, 65, 66), noteOns(s).stream().map(NoteEvent::pitch).toList());
		}

		@Test
		void brokenRhythm() throws Exception {
			Sequence s = convert(tune("semantic", "c>d e|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(3 * q / 4, 62), on(q, 64)), noteOns(s));
		}

		@Test
		void triplet() throws Exception {
			Sequence s = convert(tune("semantic", "(3cde f|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(q / 3, 62), on(2 * q / 3, 64), on(q, 65)), noteOns(s));
		}

		@Test
		void unclosedChordIsAnError() {
			assertThrows(FileParseException.class, () -> convert(tune("semantic", "[ce")));
		}

		@Test
		void unconnectedTieIsAnError() {
			assertThrows(FileParseException.class, () -> convert(tune("semantic", "c-d|")));
		}

		@Test
		void chordLengthSuffixIsALotroError() {
			LotroFileParseException e = assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "[ceg]3/4 c|"), Profile.LOTRO_STRICT));
			assertEquals(true, e.getMessage().contains("3/4"), e.getMessage());
		}

		@Test
		void chordWithoutLengthSuffixIsFineForLotro() throws Exception {
			Sequence s = ConversionDump.convert(tune("semantic", "[c3/4e3/4g3/4] c|"), Profile.LOTRO_STRICT);
			assertEquals(4, noteOns(s).size());
		}

		@Test
		void chordLengthSuffixMultipliesEveryNote() throws Exception {
			Sequence s = convert(tune("semantic", "[c2e]3/4 g|"));
			long q = s.getResolution();
			// c = 2 * 3/4 eighths = 3q/4, e = 3/4 eighth = 3q/8; the chord ends after its shortest note
			assertEquals(List.of(on(0, 60), on(0, 64), off(3 * q / 8, 64), on(3 * q / 8, 67), off(3 * q / 4, 60),
					off(7 * q / 8, 67)), noteEvents(s));
		}

		@Test
		void tupletAppliesToEveryNoteOfItsLastChord() throws Exception {
			Sequence s = convert(tune("semantic", "(3c d[e2g] c|"));
			long q = s.getResolution(); // A triplet eighth is q/3
			assertEquals(List.of(on(0, 60), off(q / 3, 60), on(q / 3, 62), off(2 * q / 3, 62), on(2 * q / 3, 64),
					on(2 * q / 3, 67), off(q, 67), on(q, 60), off(4 * q / 3, 64), off(3 * q / 2, 60)), noteEvents(s));
		}

		@Disabled("lotro wont play this")
		@Test
		void tiedNoteKeepsItsAccidentalAcrossTheBarLine() throws Exception {
			Sequence s = convert(tune("semantic", "^c-|c c|"));
			long q = s.getResolution();
			// C# held for a quarter, then a plain C: the accidental carries only into the continuation
			assertEquals(List.of(on(0, 61), off(q, 61), on(q, 60), off(3 * q / 2, 60)), noteEvents(s));
		}

		@Test
		void tieJoinsTheNextSamePitchForTheSumOfTheirLengths() throws Exception {
			// Tested in LotRO: c- d c sounds like c-[cd]. The c lasts 2 eighths from its start; the last c is silent.
			Sequence s = convert(tune("semantic", "c- d c|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(q / 2, 62), off(q, 60), off(q, 62)), noteEvents(s));
		}

		@Test
		void tieOverRestAlsoLastsTheSumOfTheLengths() throws Exception {
			// Tested in LotRO: c- z c e sounds like c for 2 eighths, then silence; the last c makes no sound
			Sequence s = convert(tune("semantic", "c-z c d|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), off(q, 60), on(3 * q / 2, 62), off(2 * q, 62)), noteEvents(s));
		}

		@Test
		void tieMayContinueAfterItsChordEnds() throws Exception {
			// The tied c2 outlasts its chord ([c2-z] lasts z); the continuation starts where c2 ends
			Sequence s = convert(tune("semantic", "[c2-z]z c d|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(3 * q / 2, 62)), noteOns(s));
		}

		@Test
		void tieAcrossBarLineDoesNotKeepTheAccidental() {
			// Tested in LotRO: ^c-|c doesn't play. The bar line resets the sharp, so C# and C don't connect.
			assertThrows(FileParseException.class, () -> convert(tune("semantic", "^c-|c d|")));
		}

		@Test
		void sameNoteTwiceInAChordPlaysOnlyTheFirst() throws Exception {
			// Tested in LotRO: [c2c4], [c4c2] and [^c2_d4] each play only the first note, for its own length
			long q = convert(tune("semantic", "c|")).getResolution();
			assertEquals(List.of(on(0, 60), off(q, 60)), noteEvents(convert(tune("semantic", "[c2c] z2|"))));
			assertEquals(List.of(on(0, 60), off(q / 2, 60)), noteEvents(convert(tune("semantic", "[cc2] z2|"))));
			assertEquals(List.of(on(0, 61), off(q, 61)), noteEvents(convert(tune("semantic", "[^c2_d] z2|"))));
		}

		@Test
		void ignoredSameNoteDoesNotShortenTheChord() throws Exception {
			// Tested in LotRO: in [c4c2] d4 the d starts when the c4 ends; the ignored c2 doesn't end the chord
			Sequence s = convert(tune("semantic", "[c2c] d|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), off(q, 60), on(q, 62), off(3 * q / 2, 62)), noteEvents(s));
		}

		@Test
		void escapedPercentIsKeptInTitle() throws Exception {
			AbcInfo info = new AbcInfo();
			ConversionDump.run(tune("semantic", header("T:100\\% Harp"), "c|"), Profile.PLAIN_MIDI, false, info);
			assertEquals("100% Harp", info.getTitle());
			assertEquals(com.digero.common.abc.LotroInstrument.BASIC_HARP, info.getPartInstrument(1));
		}

		@Test
		void lotroLengthLimitUsesTheWrittenLength() throws Exception {
			// Tested in LotRO: (3c/4d/4e/4 plays, although each note lasts only 0.042 s. LotRO checks the written
			// c/4 (0.0625 s), not the length after the tuplet.
			ConversionDump.convert(tune("semantic", "(3c/4d/4e/4 c|"), Profile.LOTRO_STRICT);
		}

		@Disabled("Waits for fix: (TuneInfo.newPart resets to the file header) and a LotRO check of that behaviour")
		@Test
		void partStartsFromTheFileHeader() throws Exception {
			// File header K:D; part 2 sets K:C; part 3 has no K: and must be back in D, not C
			Sequence s = convert(AbcCase.of("semantic", "K:D", "X:1", "T:One", "f|", "X:2", "T:Two", "K:C", "f|",
					"X:3", "T:Three", "f|"));
			assertEquals(66, noteOns(s, 3).get(0).pitch());
		}
	}
}