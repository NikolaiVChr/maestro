package com.digero.common.abctomidi;

import static com.digero.common.abctomidi.AbcCases.header;
import static com.digero.common.abctomidi.AbcCases.tune;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;

import javax.sound.midi.*;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentSampleDuration;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiUtils;
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
	 * Cases that deliberately use notes outside Lotro's range C2..C5, to test the range checks. Every other case
	 * must stay inside it, so that the strict profile doesn't fail early on a note that has nothing to do with what
	 * the case tests.
	 */
	static final Set<String> OUT_OF_RANGE_CASES = Set.of("notes_octaves_extreme", "lotro_note_too_low",
			"lotro_note_too_high",
			// With Params.standard2011 an accidental reaches every octave: ^c c' plays c' sharp, above Lotro's c'
			"accidentals_per_octave_std2011", "accidentals_double_sharp_other_octave_std2011", "standard_pitch_and_2011");

	static Stream<AbcCase> casesInRange() {
		return AbcCases.all().stream().filter(c -> !OUT_OF_RANGE_CASES.contains(c.name()));
	}

	static Stream<AbcCase> casesWithLotroAndPlainMidi() {
		return AbcCases.all().stream()
				.filter(c -> c.profiles().contains(Profile.ABC_PLAYER) && c.profiles().contains(Profile.MAESTRO_LEGACY));
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
		@MethodSource("com.digero.common.abctomidi.AbcToMidiBehaviourTest#casesInRange")
		void notesStayWithinLotroRange(AbcCase abcCase) {
			AbcInfo info = new AbcInfo();
			ConversionDump.run(abcCase, Profile.ABC_PLAYER, true, info);

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

		/**
		 * The Lotro profile plays the same notes at the same times as plain MIDI. The only difference allowed: a plucked
		 * note may end earlier (cut where its sample runs out), never later (it isn't lengthened to its sample).
		 */
		@ParameterizedTest(name = "{0}")
		@MethodSource("com.digero.common.abctomidi.AbcToMidiBehaviourTest#casesWithLotroAndPlainMidi")
		void lotroInstrumentsOnlyCutPluckedNotes(AbcCase abcCase) throws Exception {
			ConversionDump.Result lotro = ConversionDump.run(abcCase, Profile.ABC_PLAYER, false, new AbcInfo());
			ConversionDump.Result plain = ConversionDump.run(abcCase, Profile.MAESTRO_LEGACY, false, new AbcInfo());
			assertEquals(lotro.error(), plain.error());
			if (lotro.error() != null)
				return;
			List<long[]> lotroNotes = notes(ConversionDump.convert(abcCase, Profile.ABC_PLAYER));
			List<long[]> plainNotes = notes(ConversionDump.convert(abcCase, Profile.MAESTRO_LEGACY));
			assertEquals(plainNotes.size(), lotroNotes.size(), "number of notes");
			for (int i = 0; i < plainNotes.size(); i++) {
				long[] l = lotroNotes.get(i), p = plainNotes.get(i);
				assertEquals(p[1], l[1], "start of note " + i + " in track " + p[0]);
				assertTrue(l[2] <= p[2], "note " + i + " in track " + p[0] + " ends at " + l[2]
						+ " with Lotro instruments, later than its written end " + p[2]);
			}
		}

		/**
		 * {track, start, end} of every note, sorted by track, start, pitch. Pitch itself is left out: octave deltas and
		 * cowbells change it between the profiles, but not the order of notes that start together.
		 */
		private static List<long[]> notes(Sequence sequence) {
			List<long[]> notes = new ArrayList<>();
			Track[] tracks = sequence.getTracks();
			for (int t = 1; t < tracks.length; t++) {
				Map<Integer, Deque<Long>> started = new HashMap<>();
				List<long[]> trackNotes = new ArrayList<>(); // {track, start, end, pitch}
				for (int i = 0; i < tracks[t].size(); i++) {
					MidiEvent event = tracks[t].get(i);
					if (event.getMessage() instanceof ShortMessage sm) {
						if (sm.getCommand() == ShortMessage.NOTE_ON)
							started.computeIfAbsent(sm.getData1(), k -> new ArrayDeque<>()).add(event.getTick());
						else if (sm.getCommand() == ShortMessage.NOTE_OFF)
							trackNotes.add(new long[] { t, started.get(sm.getData1()).poll(), event.getTick(), sm.getData1() });
					}
				}
				trackNotes.sort(Comparator.<long[]>comparingLong(n -> n[1]).thenComparingLong(n -> n[3]));
				for (long[] n : trackNotes)
					notes.add(new long[] { n[0], n[1], n[2] });
			}
			return notes;
		}

		/** A renamed or removed case must not silently drop out of the exception list. */
		@Test
		void outOfRangeCasesExist() {
			Set<String> names = AbcCases.all().stream().map(AbcCase::name).collect(java.util.stream.Collectors.toSet());
			for (String name : OUT_OF_RANGE_CASES)
				assertEquals(true, names.contains(name), "OUT_OF_RANGE_CASES names an unknown case: " + name);
		}

		/** The song converted into the reused AbcInfo before each case in everyConversionIsTheSame. */
		private static final AbcCase PREVIOUS_SONG = AbcCases.all().stream()
				.filter(c -> c.name().equals("instruments_by_title")).findFirst().orElseThrow();

		/**
		 * A case gives the same result every way it is converted: again (deterministic), into a reused AbcInfo after
		 * another song (AbcToMidi resets it, nothing from the previous song may leak), and without note regions (the
		 * MIDI, AbcInfo and log don't depend on Params.generateRegions). One test, so each conversion is done once.
		 */
		@ParameterizedTest(name = "{0} {1}")
		@MethodSource("com.digero.common.abctomidi.AbcToMidiBehaviourTest#caseProfiles")
		void everyConversionIsTheSame(AbcCase abcCase, Profile profile) {
			ConversionDump.Result fresh = ConversionDump.run(abcCase, profile, true, new AbcInfo());

			AbcInfo reused = new AbcInfo();
			ConversionDump.run(PREVIOUS_SONG, Profile.MAESTRO_LEGACY, true, reused);
			TextDiff.assertSameText(fresh.text(), ConversionDump.run(abcCase, profile, true, reused).text(),
					"A second conversion, into a reused AbcInfo, differs from the first");

			ConversionDump.Result without = ConversionDump.run(abcCase, profile, false, new AbcInfo());
			assertEquals(fresh.error(), without.error());
			TextDiff.assertSameText(fresh.sequence(), without.sequence(), "MIDI differs without regions");
			TextDiff.assertSameText(fresh.abcInfo(), without.abcInfo(), "AbcInfo differs without regions");
			TextDiff.assertSameText(fresh.log(), without.log(), "Log differs without regions");
		}
	}

	/**
	 * Hand-computed expectations. All use the default header (M:4/4, L:1/8, Q:120, K:C), PLAIN_MIDI (no sample-length
	 * note-offs) and the default instrument (Lute of Ages, octave delta 0, so MIDI pitch = ABC pitch, c = 60). Ticks
	 * are expressed via the sequence resolution q (one quarter note), so they don't depend on the PPQN choice.
	 */
	@Nested
	class Semantics {

		@Test
		void fileThatIsNeitherUtf8NorWindows1252Opens() throws Exception {
			// The Essen collection's Chinese songs (shanxi.abc): ü as 0x81 (DOS code page 437), which Windows-1252
			// leaves undefined. It failed with "Input length = 1"; now that byte is U+FFFD and the rest reads as usual
			java.nio.file.Path file = java.nio.file.Files.createTempFile("cp437", ".abc");
			try {
				java.nio.file.Files.write(file, new byte[] { 'T', ':', 'L', (byte) 0x81, '\r', '\n', 'K', ':', 'C', '\n',
					'C', (byte) 0xE9, '|' });
				assertEquals(List.of("T:L�", "K:C", "Cé|"), AbcToMidi.readLines(file.toFile()));
			} finally {
				java.nio.file.Files.delete(file);
			}
		}

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
			return ConversionDump.convert(abcCase, Profile.MAESTRO_LEGACY);
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

		/**
		 * Params.standard2011: where Lotro plays ABC otherwise than ABC 2.1 (2011) says, or doesn't take ABC 2.1 syntax,
		 * the flag plays the standard. Maestro sets it like standardPitch: for standard ABC (folk tunes), not for files
		 * made for Lotro, existing projects or the ABC Player. Each test shows the reading without the flag (Lotro's,
		 * as today) and with it (ABC 2.1). See section F of the task list.
		 */
		@Nested
		class Standard2011 {

			/** For the tests of what isn't implemented yet: each loses it when its item is done. */
			private static final String NOT_YET = "Params.standard2011: not implemented yet";

			private static AbcCase standard(AbcCase abcCase) {
				return abcCase.with(p -> p.standard2011 = true);
			}

			/** The note-on velocities of track 1, in order. */
			private static List<Integer> velocities(Sequence sequence) {
				List<Integer> velocities = new ArrayList<>();
				Track track = sequence.getTracks()[1];
				for (int i = 0; i < track.size(); i++) {
					if (track.get(i).getMessage() instanceof ShortMessage sm && sm.getCommand() == ShortMessage.NOTE_ON)
						velocities.add(sm.getData2());
				}
				return velocities;
			}

			/** "tick:microseconds per quarter" of each tempo event in track 0. */
			private static List<String> tempos(Sequence sequence) {
				List<String> tempos = new ArrayList<>();
				Track track = sequence.getTracks()[0];
				for (int i = 0; i < track.size(); i++) {
					if (track.get(i).getMessage() instanceof MetaMessage mm && mm.getType() == 0x51) {
						byte[] d = mm.getData();
						tempos.add(track.get(i).getTick() + ":" + (((d[0] & 0xFF) << 16) | ((d[1] & 0xFF) << 8) | (d[2] & 0xFF)));
					}
				}
				return tempos;
			}

			@Test
			void chordLastsAsLongAsItsFirstNote() throws Exception {
				// Lotro (tested, B31): a chord lasts as long as its shortest note, so g follows the e
				Sequence s = convert(tune("semantic", "[c2e] g|"));
				long q = s.getResolution();
				assertEquals(List.of(on(0, 60), on(0, 64), on(q / 2, 67)), noteOns(s));
				// ABC 2.1 (4.17): "the chord duration is that of the first note", so g follows the c2. Each note still
				// sounds for its own length.
				s = convert(standard(tune("semantic", "[c2e] g|")));
				assertEquals(List.of(on(0, 60), on(0, 64), off(q / 2, 64), off(q, 60), on(q, 67), off(3 * q / 2, 67)),
						noteEvents(s));
				// The first note, not the longest: [ce2] lasts as long as its c
				assertEquals(List.of(on(0, 60), on(0, 64), on(q / 2, 67)),
						noteOns(convert(standard(tune("semantic", "[ce2] g|")))));
			}

			@Test
			void unisonPlaysTheLongerNote() throws Exception {
				// Lotro (tested): the same pitch twice in a chord plays only the first, for its own length
				long q = convert(tune("semantic", "c|")).getResolution();
				assertEquals(List.of(on(0, 60), off(q / 2, 60)), noteEvents(convert(tune("semantic", "[cc2] z2|"))));
				// ABC 2.1 (4.17): a unison, both notes sound. One MIDI channel can't sound the same pitch twice, so the
				// longer one plays; the chord's length is still its first note's (the rest starts after the c)
				assertEquals(List.of(on(0, 60), off(q, 60)), noteEvents(convert(standard(tune("semantic", "[cc2] z2|")))));
				assertEquals(List.of(on(0, 60), off(q, 60)), noteEvents(convert(standard(tune("semantic", "[c2c] z2|")))));
				assertEquals(List.of(on(0, 61), off(q, 61)),
						noteEvents(convert(standard(tune("semantic", "[^c_d2] z2|")))));
			}

			@Test
			void tieJoinsTheNextNoteOnly() throws Exception {
				// Lotro joins a tie to the next note of its pitch wherever it is (tested: c- d c is one c over the d).
				// ABC 2.1 (4.11): "Ties connect two successive notes of the same pitch". O'Neill's collections write -
				// as a slur (F-G): with Lotro's reading the F sounds on to the next F. So with the flag the next note
				// (or chord, or rest) must have the tied pitch, else an error at the tie
				Sequence s = convert(tune("semantic", "c- d c|"));
				long q = s.getResolution();
				assertEquals(List.of(on(0, 60), on(q / 2, 62), off(q, 60), off(q, 62)), noteEvents(s)); // The sum of the c's
				for (String body : List.of("c- d c|", "F-G F|", "c- z c|", "[ce]- [cg] e|", "[ce]- c e|")) {
					FileParseException e = assertThrows(FileParseException.class,
							() -> convert(standard(tune("semantic", body))), body);
					assertTrue(e.getMessage().contains("line 7,"), e.getMessage()); // At the tie's line
				}
				// The next note continues it: across a bar line or a line break, in a chord
				assertEquals(noteEvents(convert(tune("semantic", "c4- | c4 d|"))),
						noteEvents(convert(standard(tune("semantic", "c4- | c4 d|")))));
				assertEquals(noteEvents(convert(tune("semantic", "[ce]- [ce] d|"))),
						noteEvents(convert(standard(tune("semantic", "[ce]- [ce] d|")))));
				// After a repeat sign or an ending the next note played may be another: there the tie just ends
				s = convert(standard(tune("semantic", "|: c |1 d- :|2 d e|]").with(p -> p.expandRepeats = true)));
				assertEquals(List.of(on(0, 60), off(q / 2, 60), on(q / 2, 62), off(q, 62), on(q, 60), off(3 * q / 2, 60),
						on(3 * q / 2, 62), off(2 * q, 62), on(2 * q, 64), off(5 * q / 2, 64)), noteEvents(s));
			}

			@Test
			void tieNeedsTheSamePitchAfterTheBarLine() throws Exception {
				// ABC 2.1 (4.11): a tie joins "two notes of the same pitch", within or between bars, and the bar line
				// ends an accidental as always. So ^c-|c ties C# to C, which doesn't connect: an error, in both readings
				// and as in Lotro (tested). The continuation repeats the sharp: ^c-|^c. (Staff notation carries the
				// accidental over a tie; ABC doesn't say so, and Lotro doesn't: standard2011 changes nothing here.)
				assertThrows(FileParseException.class, () -> convert(tune("semantic", "^c-|c d|")));
				assertThrows(FileParseException.class, () -> convert(standard(tune("semantic", "^c-|c d|"))));
				Sequence s = convert(standard(tune("semantic", "^c-|^c d|")));
				long q = s.getResolution();
				assertEquals(List.of(on(0, 61), off(q, 61), on(q, 62), off(3 * q / 2, 62)), noteEvents(s));
				assertEquals(noteEvents(convert(tune("semantic", "^c-|^c d|"))), noteEvents(s));
				// In the same bar the sharp still holds, so both ^c-c and ^c-^c are one C# (Lotro plays both)
				for (String body : List.of("^c-c d|", "^c-^c d|")) {
					assertEquals(List.of(on(0, 61), off(q, 61), on(q, 62), off(3 * q / 2, 62)),
							noteEvents(convert(tune("semantic", body))), body);
					assertEquals(noteEvents(convert(tune("semantic", body))),
							noteEvents(convert(standard(tune("semantic", body)))), body);
				}
			}

			@Test
			void accidentalAppliesInEveryOctave() throws Exception {
				// Lotro (tested, B66): an accidental applies in its own octave, to the bar line
				assertEquals(List.of(61, 72, 48, 61), pitches(tune("semantic", "^c c' C c|")));
				// ABC 2.1 (11.3, %%propagate-accidentals, default "pitch"): to the same note in every octave, to the
				// bar line
				assertEquals(List.of(61, 73, 49, 61), pitches(standard(tune("semantic", "^c c' C c|"))));
				assertEquals(List.of(61, 72), pitches(standard(tune("semantic", "^c|c'|"))));
				// A later accidental, in any octave, replaces it in every octave
				assertEquals(List.of(61, 71, 59), pitches(standard(tune("semantic", "^c _c' c|"))));
			}

			@Test
			void propagateAccidentalsDirective() throws Exception {
				// %%propagate-accidentals (ABC 2.1, 11.3), also as I: in the header or inline: octave (as Lotro plays it)
				// or not (the accidental only on its own note)
				assertEquals(List.of(61, 72, 61),
						pitches(standard(tune("semantic", "%%propagate-accidentals octave", "^c c' c|"))));
				assertEquals(List.of(61, 72, 61), pitches(standard(tune("semantic",
						AbcCase.concat(header(), new String[] { "I:propagate-accidentals octave" }), "^c c' c|"))));
				assertEquals(List.of(61, 72, 60),
						pitches(standard(tune("semantic", "[I:propagate-accidentals not] ^c c' c|"))));
				// From where it is: before it the default (pitch)
				assertEquals(List.of(61, 73, 61, 60),
						pitches(standard(tune("semantic", "^c c'|", "I:propagate-accidentals not", "^c c|"))));
				// Without the flag, or with Lotro errors, Lotro's reading: octave, whatever the file says
				assertEquals(List.of(61, 72, 61),
						pitches(tune("semantic", "%%propagate-accidentals pitch", "^c c' c|")));
				assertEquals(List.of(61, 72, 61), noteOns(ConversionDump.convert(standard(tune("semantic",
						"%%propagate-accidentals not", "^c c' c|")), Profile.ABC_PLAYER_STRICT)).stream().map(NoteEvent::pitch)
						.toList());
				// The file header's directive applies to every tune
				AbcCase book = AbcCase.of("semantic", AbcCase.concat(new String[] { "%%propagate-accidentals not" },
						AbcCases.part(1, "One", "^c c|"), AbcCases.part(2, "Two", "^c c|")));
				Sequence s = convert(standard(book));
				assertEquals(List.of(61, 60), noteOns(s, 1).stream().map(NoteEvent::pitch).toList());
				assertEquals(List.of(61, 60), noteOns(s, 2).stream().map(NoteEvent::pitch).toList());
			}

			/** The pitches of the notes of track 1, in order. */
			private List<Integer> pitches(AbcCase abcCase) throws Exception {
				return noteOns(convert(abcCase)).stream().map(NoteEvent::pitch).toList();
			}

			@Test
			void tempoWithoutNoteLengthCountsUnitNotes() throws Exception {
				// Lotro and every Lotro file: Q:120 is 120 beats of the meter's denominator. M:4/4 L:1/8 Q:120 c8
				// (a whole note) = 4 quarters at 120 a minute = 2 s.
				assertEquals(2_000_000L, convert(tune("semantic", "c8|")).getMicrosecondLength());
				// ABC 2.1 (10.1): Q:120 and Q:C=120 are deprecated forms of "120 unit note-lengths (L:) per minute",
				// and programs should accept them: 8 eighths at 120 a minute = 4 s. By Params.specTempo (the Q: note
				// length counts), not standard2011.
				assertEquals(4_000_000L, convert(specTempo(tune("semantic", "c8|"))).getMicrosecondLength());
				assertEquals(4_000_000L,
						convert(specTempo(tune("semantic", header("Q:C=120"), "c8|"))).getMicrosecondLength());
				assertEquals(2_000_000L, convert(standard(tune("semantic", "c8|"))).getMicrosecondLength());
				// The header's L:, also one after the Q:; without L: the default (1/16 in 2/4: 16 sixteenths at 120 = 8 s)
				assertEquals(8_000_000L, convert(specTempo(tune("semantic", AbcCase.concat(header("-L"),
						new String[] { "L:1/16" }), "c16|"))).getMicrosecondLength());
				assertEquals(8_000_000L,
						convert(specTempo(tune("semantic", header("M:2/4", "-L"), "c16|"))).getMicrosecondLength());
				// With L:1/4 the two readings are the same; a note length in Q: counts as before
				assertEquals(2_000_000L,
						convert(specTempo(tune("semantic", header("L:1/4"), "c4|"))).getMicrosecondLength());
				assertEquals(2_000_000L,
						convert(specTempo(tune("semantic", header("Q:1/4=120"), "c8|"))).getMicrosecondLength());
				// Maestro's own %%Q: counts the meter's beats, as before
				assertEquals(List.of("0:1000000", "11520:500000"),
						tempos(convert(specTempo(tune("semantic", "c d|", "%%Q: 120", "e f|")))));
				// With Lotro errors: the meter's beats (Lotro's reading), the same as without specTempo
				assertEquals(tempos(ConversionDump.convert(tune("semantic", "c8|"), Profile.ABC_PLAYER_STRICT)),
						tempos(ConversionDump.convert(specTempo(tune("semantic", "c8|")), Profile.ABC_PLAYER_STRICT)));
			}

			@Test
			void blankLineEndsTheTune() throws Exception {
				// Lotro (tested, B14): it plays on after a blank line, so both lines play
				assertEquals(4, noteOns(convert(tune("semantic", "c d|", "", "e f|"))).size());
				// ABC 2.1 (2.2.1): a tune is "terminated by an empty line"; what follows up to the next X: is free
				// text, not music
				assertEquals(2, noteOns(convert(standard(tune("semantic", "c d|", "", "e f|")))).size());
				assertEquals(2, noteOns(convert(standard(tune("semantic", "c d|", "", "Notes: play it slowly.")))).size());
				assertThrows(FileParseException.class,
						() -> convert(tune("semantic", "c d|", "", "Notes: play it slowly.")));
				// Free text before the first X: too (ABC 2.1, 2.2): tune books start with a note and a copyright
				AbcCase book = AbcCase.of("semantic", "These are my tunes.", "(c) 2026 Me", "", "X:1", "T:t", "M:4/4",
						"L:1/8", "Q:120", "K:C", "c d|");
				assertEquals(2, noteOns(convert(standard(book))).size());
				assertThrows(FileParseException.class, () -> convert(book));
			}

			@Test
			void bangAsLineBreak() throws Exception {
				// Older ABC (pipe collections, 2.0): ! at the end of a line is a score line break. Without the flag, and
				// with Lotro errors, a lone ! is an error
				AbcCase old = tune("semantic", "c d|  !", "e f|");
				assertThrows(FileParseException.class, () -> convert(old));
				assertThrows(FileParseException.class, () -> ConversionDump.convert(standard(old), Profile.ABC_PLAYER_STRICT));
				// ABC 2.1 (12): a file without %abc-2.1 is read loosely: a ! with no ! after it before | [ : or the line's
				// end is a line break (skipped); with one it's a decoration, also with spaces in it (Norbeck's
				// !D.C. al fine!)
				assertEquals(4, noteOns(convert(standard(old))).size());
				assertEquals(noteEvents(convert(standard(tune("semantic", "!trill!c d|")))),
						noteEvents(convert(standard(tune("semantic", "!trill!c d| !")))));
				assertEquals(4, noteOns(convert(standard(tune("semantic", "c d|", "!D.C. al fine!", "e f|")))).size());
				// A file of ABC 2.1 or later is strict: a lone ! is an error, unless I:linebreak ! says it's a line break
				String[] head = { "%abc-2.1", "X:1", "T:t", "M:4/4", "L:1/8", "Q:120", "K:C" };
				assertThrows(FileParseException.class,
						() -> convert(standard(AbcCase.of("semantic", AbcCase.concat(head, new String[] { "c d|  !", "e f|" })))));
				assertEquals(4, noteOns(convert(standard(AbcCase.of("semantic",
						AbcCase.concat(head, new String[] { "I:linebreak !", "c d|  !", "e f|" }))))).size());
			}

			@Test
			void aTuneWithoutNotesIsAnEmptyTrack() throws Exception {
				// A tune (X:) with only a header, at the end or between others: an empty track (Maestro hides it), so
				// each part keeps the track of its number. (It was an ArrayIndexOutOfBoundsException, in every reading.)
				for (boolean std : new boolean[] { false, true }) {
					String[] one = { "X:1", "T:One", "M:4/4", "L:1/8", "K:C", "c d|", "" };
					String[] empty = { "X:2", "T:Empty", "K:C", "" };
					String[] three = { "X:3", "T:Three", "K:C", "e f|" };
					AbcCase first = AbcCase.of("semantic", AbcCase.concat(empty, three));
					AbcCase last = AbcCase.of("semantic", AbcCase.concat(one, empty));
					AbcCase middle = AbcCase.of("semantic", AbcCase.concat(AbcCase.concat(one, empty), three));
					if (std) {
						first = standard(first);
						last = standard(last);
						middle = standard(middle);
					}
					Sequence s = convert(middle);
					assertEquals(4, s.getTracks().length);
					assertEquals(List.of(), noteOns(s, 2));
					assertEquals(List.of(64, 65), noteOns(s, 3).stream().map(NoteEvent::pitch).toList());
					AbcInfo info = abcInfoOf(middle);
					assertEquals("Empty", info.getPartName(2));
					assertEquals("Three", info.getPartName(3));
					assertEquals(3, convert(last).getTracks().length);
					assertEquals(List.of(), noteOns(convert(last), 2));
					Sequence firstEmpty = convert(first);
					assertEquals(3, firstEmpty.getTracks().length);
					assertEquals(List.of(), noteOns(firstEmpty, 1));
					assertEquals(2, noteOns(firstEmpty, 2).size());
				}
			}

			@Test
			void voicesPlayTogether() throws Exception {
				AbcCase voices = tune("semantic", "V:1", "c d|", "V:2 name=\"Bass\"", "C D|");
				// Lotro (B42): V: changes nothing, the voices play one after another in one part
				Sequence lotro = convert(voices);
				long q = lotro.getResolution();
				assertEquals(2, lotro.getTracks().length);
				assertEquals(List.of(on(0, 60), on(q / 2, 62), on(q, 48), on(3 * q / 2, 50)), noteOns(lotro));
				// ABC 2.1 (7): each voice is a part, all from the tune's start, named by the voice (else "Voice" and
				// its id)
				AbcToMidi.Params params = new AbcToMidi.Params(voices.filesData());
				Profile.MAESTRO_LEGACY.applyTo(params);
				standard(voices).tweak().accept(params);
				params.generateRegions = true;
				AbcInfo info = params.abcInfo = new AbcInfo();
				Sequence s = AbcToMidi.convert(params);
				assertEquals(3, s.getTracks().length);
				assertEquals(List.of(on(0, 60), on(q / 2, 62)), noteOns(s, 1));
				assertEquals(List.of(on(0, 48), on(q / 2, 50)), noteOns(s, 2));
				assertEquals(List.of("Test", "Voice 1", "Bass"),
						List.of(info.getPartName(0), info.getPartName(1), info.getPartName(2)));
				// The notes point to the lines of the file (regions count lines from 0: line 10 is the V:2 voice's)
				List<Integer> bassLines = info.getRegions().stream().filter(r -> r.getNote() != null)
						.filter(r -> r.getNote().id < 60).map(AbcRegion::getLine).distinct().toList();
				assertEquals(List.of(9), bassLines);
				// [V:] in a line: each voice gets its stretch
				assertEquals(noteEvents(s, 2), noteEvents(convert(standard(tune("semantic", "[V:1] c d| [V:2] C D|"))), 2));
			}

			@Test
			void sectionsPlayInTheHeadersOrder() throws Exception {
				AbcCase ordered = tune("semantic", AbcCases.partOrderHeader("P:ABA"), "P:A", "c d|", "P:B", "e f|");
				// Lotro (B52): the header P: is ignored, the sections play as written
				Sequence lotro = convert(ordered);
				long q = lotro.getResolution();
				assertEquals(List.of(on(0, 60), on(q / 2, 62), on(q, 64), on(3 * q / 2, 65)), noteOns(lotro));
				// ABC 2.1 (3.1.9): A, B, then A again
				assertEquals(List.of(on(0, 60), on(q / 2, 62), on(q, 64), on(3 * q / 2, 65), on(2 * q, 60),
						on(5 * q / 2, 62)), noteOns(convert(standard(ordered))));
				// A :| without |: goes back to its section's start, not into the section before
				AbcCase repeat = tune("semantic", AbcCases.partOrderHeader("P:AB"), "P:A", "c d|", "P:B", "e f :|");
				assertEquals(List.of(on(0, 60), on(q / 2, 62), on(q, 64), on(3 * q / 2, 65), on(2 * q, 64),
						on(5 * q / 2, 65)), noteOns(convert(standard(repeat).with(p -> p.expandRepeats = true))));
			}

			@Test
			void brokenRhythmWithChords() throws Exception {
				long q = convert(tune("semantic", "c|")).getResolution();
				// Lotro (tested, B6 and B31): in c>[ce] only the chord's first note is halved, e keeps its length and
				// sounds on after the chord; the next note follows the halved c
				assertEquals(List.of(on(0, 60), off(3 * q / 4, 60), on(3 * q / 4, 60), on(3 * q / 4, 64), off(q, 60),
						on(q, 62), off(5 * q / 4, 64), off(3 * q / 2, 62)), noteEvents(convert(tune("semantic", "c>[ce] d|"))));
				// ... and it refuses [ce]>d: an error without the flag too
				assertThrows(FileParseException.class, () -> convert(tune("semantic", "[ce]>d f|")));
				// ABC 2.1 (4.4 and 4.17: a chord takes a broken rhythm like a note): the whole chord is halved ...
				Sequence s = convert(standard(tune("semantic", "c>[ce] d|")));
				assertEquals(List.of(on(0, 60), off(3 * q / 4, 60), on(3 * q / 4, 60), on(3 * q / 4, 64), off(q, 60),
						off(q, 64), on(q, 62), off(3 * q / 2, 62)), noteEvents(s));
				// ... or dotted, the note after it halved
				s = convert(standard(tune("semantic", "[ce]>d f|")));
				assertEquals(List.of(on(0, 60), on(0, 64), on(3 * q / 4, 62), on(q, 65)), noteOns(s));
				// With Lotro errors both are errors, with or without the flag
				assertThrows(FileParseException.class, () -> ConversionDump.convert(standard(tune("semantic", "[ce]>d f|")),
						Profile.ABC_PLAYER_STRICT));
				assertThrows(LotroFileParseException.class, () -> ConversionDump.convert(
						standard(tune("semantic", "c>[ce] d|")), Profile.ABC_PLAYER_STRICT));
			}

			@Test
			void dynamicsMarksSetTheVolume() throws Exception {
				// Lotro: only the ABC 2.0 form +p+ +f+ sets the volume; !p! !f! (ABC 2.1) are skipped, so every note is
				// at the default mf
				List<Integer> plusForm = velocities(convert(tune("semantic", "+p+c +f+d|")));
				List<Integer> mf = velocities(convert(tune("semantic", "c d|")));
				assertNotEquals(mf, plusForm);
				assertEquals(mf, velocities(convert(tune("semantic", "!p!c !f!d|"))));
				// ABC 2.1 (4.14): players "may be expected to implement the dynamics marks": !p! !f! as +p+ +f+,
				// from pppp to ffff; the volume stays until the next mark
				assertEquals(plusForm, velocities(convert(standard(tune("semantic", "!p!c !f!d|")))));
				assertEquals(velocities(convert(tune("semantic", "+pppp+c +ffff+d|"))),
						velocities(convert(standard(tune("semantic", "!pppp!c !ffff!d|")))));
				assertEquals(velocities(convert(tune("semantic", "+ppp+c d +fff+e|"))),
						velocities(convert(standard(tune("semantic", "!ppp!c d !fff!e|")))));
				// A mark Dynamics doesn't have (!sfz!) sets no volume
				assertEquals(mf, velocities(convert(standard(tune("semantic", "!sfz!c d|")))));
			}

			@Test
			void accentAndStaccato() throws Exception {
				// Lotro: an accent (L, !accent!, !>!) and a staccato dot (.) change nothing
				Sequence plain = convert(tune("semantic", "c d e|"));
				long q = plain.getResolution();
				assertEquals(noteEvents(plain), noteEvents(convert(tune("semantic", "Lc .d e|"))));
				// ABC 2.1 (4.14): players "may be expected to implement ... the accent mark and the staccato dot".
				// How much is ours to choose: an accent AbcToMidi.ACCENT_DYNAMICS_STEPS louder than the notes around
				// it (2: mf as ff, like MuseScore 4), a staccato note sounding AbcToMidi.STACCATO_LENGTH of its length
				// (half; the next note starts on time)
				List<Integer> accented = velocities(convert(standard(tune("semantic", "!accent!c d e|"))));
				assertTrue(accented.get(0) > accented.get(1), accented.toString());
				assertEquals(velocities(convert(tune("semantic", "+ff+c +mf+d e|"))), accented);
				assertEquals(accented, velocities(convert(standard(tune("semantic", "Lc d e|")))));
				assertEquals(accented, velocities(convert(standard(tune("semantic", "!>!c d e|")))));
				assertEquals(accented, velocities(convert(standard(tune("semantic", "!emphasis!c d e|")))));
				// At most the loudest: ffff stays ffff; an accent before a chord is for all of its notes
				assertEquals(velocities(convert(tune("semantic", "+ffff+c d|"))),
						velocities(convert(standard(tune("semantic", "+fff+Lc +ffff+d|")))));
				List<Integer> chord = velocities(convert(standard(tune("semantic", "L[ce] d|"))));
				assertEquals(List.of(accented.get(0), accented.get(0), accented.get(1)), chord);
				assertEquals(List.of(on(0, 60), off(q / 2, 60), on(q / 2, 62), off(3 * q / 4, 62), on(q, 64),
						off(3 * q / 2, 64)), noteEvents(convert(standard(tune("semantic", "c .d e|")))));
				// A staccato before a chord is for all of its notes; the dotted bar line .| is no staccato
				assertEquals(List.of(on(0, 60), on(0, 64), off(q / 4, 60), off(q / 4, 64), on(q / 2, 62),
						off(q, 62)), noteEvents(convert(standard(tune("semantic", ".[ce] d|")))));
				assertEquals(noteEvents(convert(standard(tune("semantic", "c d | e|")))),
						noteEvents(convert(standard(tune("semantic", "c d .| e|")))));
			}

			@Test
			void beatGroupsAreAccented() throws Exception {
				// A meter of beat groups (M:2+2+3/8; 5/8, 7/8, 11/8 ... as 2s and a 3 at the end): the note at each
				// group's start plays AbcToMidi.BEAT_GROUP_ACCENT_STEPS (1) louder, so the bar is heard as its groups
				List<Integer> volumes = velocities(convert(tune("semantic", "+mf+c +f+d +ff+e|")));
				int mf = volumes.get(0), f = volumes.get(1), ff = volumes.get(2);
				List<Integer> groups = List.of(f, mf, f, mf, f, mf, mf, f);
				assertEquals(groups, velocities(convert(standard(tune("semantic", header("M:2+2+3/8"), "c d e f g a b|c|")))));
				assertEquals(groups, velocities(convert(standard(tune("semantic", header("M:7/8"), "c d e f g a b|c|")))));
				// The groups as written: 3+2+2
				assertEquals(List.of(f, mf, mf, f, mf, f, mf), velocities(convert(standard(tune("semantic",
						header("M:3+2+2/8"), "c d e f g a b|")))));
				// A chord: every note; an accented note keeps its accent (2 steps), not more
				assertEquals(List.of(f, f, mf, f, mf, mf), velocities(convert(standard(tune("semantic", header("M:5/8"),
						"[ce] d e f g|")))));
				assertEquals(List.of(ff, mf, f, mf, mf), velocities(convert(standard(tune("semantic", header("M:5/8"),
						"Lc d e f g|")))));
				// A pickup: the first bar ends at its bar line (here the last three eighths of a 7/8 bar)
				assertEquals(List.of(f, mf, mf, f, mf, f, mf, f, mf, mf), velocities(convert(standard(tune("semantic",
						header("M:7/8"), "g a b|c d e f g a b|")))));
				// Not in meters of equal beats, and not without the flag (Lotro plays every note the same)
				assertEquals(List.of(mf, mf, mf, mf, mf, mf), velocities(convert(standard(tune("semantic", header("M:6/8"),
						"c d e f g a|")))));
				assertEquals(List.of(mf, mf, mf, mf, mf, mf, mf), velocities(convert(tune("semantic", header("M:7/8"),
						"c d e f g a b|"))));
			}

			@Disabled(NOT_YET)
			@Test
			void tempoChangeInThePart() throws Exception {
				// Without the flag Q: can't change the tempo after the part's notes started (an error)
				assertThrows(FileParseException.class, () -> convert(tune("semantic", "c d|", "Q:60", "e f|")));
				assertThrows(FileParseException.class, () -> convert(tune("semantic", "c d [Q:60] e f|")));
				// ABC 2.1 (3.1.8, 3.2): Q: may change the tempo mid-tune, on a line or inline. The notes keep their
				// ticks; a tempo event makes them slower from there.
				for (AbcCase abcCase : List.of(tune("semantic", "c d|", "Q:60", "e f|"), tune("semantic", "c d [Q:60] e f|"))) {
					Sequence s = convert(standard(abcCase));
					long q = s.getResolution();
					assertEquals(List.of("0:500000", q + ":1000000"), tempos(s));
					assertEquals(List.of(on(0, 60), on(q / 2, 62), on(q, 64), on(3 * q / 2, 65)), noteOns(s));
				}
			}

			@Disabled(NOT_YET)
			@Test
			void meterWithAnotherDenominatorInThePart() throws Exception {
				// Without the flag the meter's denominator must stay the same in the song (an error)
				assertThrows(FileParseException.class, () -> convert(tune("semantic", "c d|", "M:6/8", "e f|")));
				// ABC 2.1 (3.2): the meter may change mid-tune. With the flag Q:120 counts unit notes, so the notes keep
				// their lengths.
				Sequence s = convert(standard(tune("semantic", "c d|", "M:6/8", "e f|")));
				long q = s.getResolution();
				assertEquals(List.of(on(0, 60), on(q / 2, 62), on(q, 64), on(3 * q / 2, 65)), noteOns(s));
			}

			@Test
			void keyWithExplicitAccidentals() throws Exception {
				// Without the flag: an error (A12; Lotro refuses it, B23)
				assertThrows(FileParseException.class, () -> convert(tune("semantic", header("K:G ^c"), "c f|")));
				// ABC 2.1 (3.1.14): K:G ^c is G major with a C sharp added; K:D exp _b has only the accidentals listed
				assertEquals(List.of(61, 66), pitches(standard(tune("semantic", header("K:G ^c"), "c f|"))));
				assertEquals(List.of(65, 60, 58), pitches(standard(tune("semantic", header("K:D exp _b"), "f c B|"))));
				// One replaces the key's (Norbeck: K:Dm =b, D minor with B natural), in every octave
				assertEquals(List.of(59, 71, 70), pitches(standard(tune("semantic", header("K:Dm =b"), "B b _b|"))));
				// A written accidental still wins, to the end of the bar
				assertEquals(List.of(60, 60, 61), pitches(standard(tune("semantic", header("K:G ^c"), "=c c|c|"))));
				// A K: with a key starts from its own accidentals; one with only a clef keeps them
				assertEquals(List.of(61, 60), pitches(standard(tune("semantic", header("K:G ^c"), "c [K:G] c|"))));
				assertEquals(List.of(61, 61), pitches(standard(tune("semantic", header("K:G ^c"), "c [K:treble] c|"))));
				// Every part starts from the file header's
				Sequence s = convert(standard(AbcCase.of("semantic", "K:F ^c", "", "X:1", "T:a", "M:4/4", "L:1/8", "Q:120",
						"c B|", "X:2", "T:b", "M:4/4", "L:1/8", "Q:120", "c B|")));
				assertEquals(List.of(61, 58), noteOns(s, 1).stream().map(NoteEvent::pitch).toList());
				assertEquals(List.of(61, 58), noteOns(s, 2).stream().map(NoteEvent::pitch).toList());
				// With Lotro errors: the Lotro error, with or without the flag
				assertThrows(LotroFileParseException.class, () -> ConversionDump.convert(
						standard(tune("semantic", header("K:G ^c"), "c f|")), Profile.ABC_PLAYER_STRICT));
			}

			@Disabled(NOT_YET)
			@Test
			void graceNotesBetweenANoteAndItsBrokenRhythm() throws Exception {
				// Without the flag c{g}<d is an error (only c<{g}d works)
				assertThrows(FileParseException.class, () -> convert(tune("semantic", "c{g}<d e|")));
				// ABC 2.1 (4.12): "A<{g}A and A{g}<A are legal and equivalent"
				assertEquals(noteEvents(convert(standard(tune("semantic", "c<{g}d e|")))),
						noteEvents(convert(standard(tune("semantic", "c{g}<d e|")))));
			}

			@Test
			void lotroErrorsWin() throws Exception {
				// With Lotro errors on (the ABC Player) the flag changes nothing: Lotro's reading, or a Lotro error
				assertEquals(noteEvents(ConversionDump.convert(tune("semantic", "[c2e] g|"), Profile.ABC_PLAYER_STRICT)),
						noteEvents(ConversionDump.convert(standard(tune("semantic", "[c2e] g|")), Profile.ABC_PLAYER_STRICT)));
				assertEquals(noteEvents(ConversionDump.convert(tune("semantic", "^c c' C c|"), Profile.ABC_PLAYER_STRICT)),
						noteEvents(ConversionDump.convert(standard(tune("semantic", "^c c' C c|")), Profile.ABC_PLAYER_STRICT)));
			}
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
					() -> ConversionDump.convert(tune("semantic", "[ceg]3/4 c|"), Profile.ABC_PLAYER_STRICT));
			assertEquals(true, e.getMessage().contains("3/4"), e.getMessage());
		}

		@Test
		void chordWithoutLengthSuffixIsFineForLotro() throws Exception {
			Sequence s = ConversionDump.convert(tune("semantic", "[c3/4e3/4g3/4] c|"), Profile.ABC_PLAYER_STRICT);
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
			// Tested in Lotro: c- d c sounds like c-[cd]. The c lasts 2 eighths from its start; the last c is silent.
			Sequence s = convert(tune("semantic", "c- d c|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(q / 2, 62), off(q, 60), off(q, 62)), noteEvents(s));
		}

		@Test
		void tieOverRestAlsoLastsTheSumOfTheLengths() throws Exception {
			// Tested in Lotro: c- z c e sounds like c for 2 eighths, then silence; the last c makes no sound
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
			// Tested in Lotro: ^c-|c doesn't play. The bar line resets the sharp, so C# and C don't connect.
			assertThrows(FileParseException.class, () -> convert(tune("semantic", "^c-|c d|")));
		}

		@Test
		void noteRestartedAtAnotherVolumeWhileItSoundsIsALotroError() throws Exception {
			// Tested in Lotro: the c2 still sounds when c starts again. With +ff+ in between the part plays nothing.
			LotroFileParseException e = assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "[c2z] +ff+ c d|"), Profile.ABC_PLAYER_STRICT));
			assertTrue(e.getMessage().contains("+ff+"), e.getMessage());
			// ... but it plays without a volume change, with +mf+ (already the volume), or with +ff+ before both
			for (String body : List.of("[c2z] c d|", "[c2z] +mf+ c d|", "+ff+ [c2z] c d|"))
				ConversionDump.convert(tune("semantic", body), Profile.ABC_PLAYER_STRICT);
			// ... and a tie continuation is no new attack, so a volume change before it is fine (TD4-TD6)
			for (String body : List.of("[c2-z] +ff+ c d|", "c- +ff+ c d|", "c- d +ff+ c|"))
				ConversionDump.convert(tune("semantic", body), Profile.ABC_PLAYER_STRICT);
		}

		/**
		 * "tick:text" of every lyric event in a track, the tick in eighths (Q:120, L:1/8). Without the header lines
		 * (@T title, @I information fields), see {@link #headerLines}.
		 */
		private static List<String> lyrics(Sequence sequence, int trackIndex) {
			List<String> lyrics = new ArrayList<>();
			long eighth = sequence.getResolution() / 2;
			Track track = sequence.getTracks()[trackIndex];
			for (int i = 0; i < track.size(); i++) {
				if (track.get(i).getMessage() instanceof javax.sound.midi.MetaMessage mm && mm.getType() == 0x05) {
					String text = new String(mm.getData(), java.nio.charset.StandardCharsets.UTF_8);
					if (!text.startsWith("@"))
						lyrics.add(track.get(i).getTick() / eighth + ":" + text);
				}
			}
			return lyrics;
		}

		/**
		 * The header lines (@T title, @I information fields) in track 0, in tick order. Checks that every lyric event
		 * in track 0 (header lines and W: lines) has a tick of its own.
		 */
		private static List<String> headerLines(Sequence sequence) {
			List<String> lines = new ArrayList<>();
			List<Long> ticks = new ArrayList<>();
			List<Long> allTicks = new ArrayList<>();
			Track track = sequence.getTracks()[0];
			for (int i = 0; i < track.size(); i++) {
				if (track.get(i).getMessage() instanceof javax.sound.midi.MetaMessage mm && mm.getType() == 0x05) {
					String text = new String(mm.getData(), java.nio.charset.StandardCharsets.UTF_8);
					allTicks.add(track.get(i).getTick());
					if (text.startsWith("@")) {
						lines.add(text);
						ticks.add(track.get(i).getTick());
					}
				}
			}
			assertEquals(ticks.stream().sorted().toList(), ticks, "header lines in order");
			assertEquals((long) allTicks.size(), allTicks.stream().distinct().count(), "one tick per lyric event: " + allTicks);
			return lines;
		}

		@Test
		void informationFieldsBecomeHeaderLines() throws Exception {
			// Maestro shows them above the lyrics: "Title: ..." and "Info: Composer: ..." (MidiText)
			Sequence s = convert(AbcCase.of("semantic", "X:1", "T:Down the Hill", "T:An Cnoc", "R:air", "C:Anon", "C:Trad",
					"H:Originally in Gdor", "H:and in 6/8.", "N:TS 1, 1", "N:genre: folk", "S:O'Neill's", "+:1903",
					"Z:Me \\'e", "M:4/4", "L:1/8", "Q:120", "K:C", "c d|", "N:Played slowly", "X:2", "T:Down the Hill",
					"C:Anon", "M:4/4", "L:1/8", "Q:120", "K:C", "e f|"));
			// A second T: is another title, C: lines are one each, H: lines in a row are one text, +: joins, N:
			// lines of Maestro's own are left out, and a line that comes again (C: in the second part) is written once
			assertEquals(List.of("@TDown the Hill", "@IAlso known as: An Cnoc", "@IRhythm: air", "@IComposer: Anon",
					"@IComposer: Trad", "@IHistory: Originally in Gdor and in 6/8.", "@ISource: O'Neill's 1903",
					"@ITranscription: Me \u00e9", "@INotes: Played slowly"), headerLines(s));
			// A repeat going back doesn't write a field in the tune again
			s = convert(tune("semantic", "|: c |", "N:Twice?", "d :|").with(p -> p.expandRepeats = true));
			assertEquals(List.of("@TTest", "@INotes: Twice?"), headerLines(s));
			// W: lines before the notes take ticks 0 and 1, so the header lines go on 2 and 3
			s = convert(tune("semantic", header(), "C:Anon", "W:one", "W:two", "c d|"));
			assertEquals(List.of("@TTest", "@IComposer: Anon"), headerLines(s));
			assertEquals(List.of("0:<one", "0:<two"), lyrics(s, 0));
		}

		@Test
		void lyricsAreSungToTheNotesAbove() throws Exception {
			// One syllable per note; - splits a word, a space ends it (Maestro's MidiText reads them like karaoke)
			assertEquals(List.of("0:hel", "1:lo ", "2:world ", "3:wide "),
					lyrics(convert(tune("semantic", "c d e f|", "w:hel-lo world wide")), 1));
			// Rests get no syllable, a chord gets one. Tied notes are separate notes (ABC 2.1, 5.1): _ holds a syllable
			// over the tied note
			assertEquals(List.of("0:a ", "2:b ", "4:c "),
					lyrics(convert(tune("semantic", "c z d- d [ceg] z|", "w:a b_ c")), 1));
			assertEquals(List.of("0:a ", "2:b ", "3:c "),
					lyrics(convert(tune("semantic", "c z d- d [ceg] z|", "w:a b c")), 1));
			// _ holds a syllable over the next note, * skips a note, ~ joins words, \- is a hyphen
			assertEquals(List.of("0:A", "2:le ", "4:1. Ti ", "5:e-mail "),
					lyrics(convert(tune("semantic", "c d e f g a|", "w:A_le * 1.~Ti e\\-mail")), 1));
			// | goes on at the next bar
			assertEquals(List.of("0:one ", "4:two "), lyrics(convert(tune("semantic", "c d e f|g a|", "w:one | two")), 1));
		}

		@Test
		void onlyTheFirstVerseIsSung() throws Exception {
			// More w: lines under the same notes are later verses; Lotro plays no repeats, so only verse 1 fits.
			// A w: line after earlier lyrics starts a new line (/). The verses that aren't sung follow as lines of
			// text, verse by verse, after the last note started (so Maestro shows them)
			assertEquals(List.of("0:one ", "1:two ", "2:/three ", "3:four ", "3:/uno dos", "3:/tres cuatro"),
					lyrics(convert(tune("semantic", "c d|", "w:one two", "w:uno dos", "e f|", "w:three four",
							"w:tres cuatro")), 1));
			// As words: - and _ join syllables, ~ is a space, \- a hyphen
			assertEquals(List.of("0:a ", "1:b ", "1:/1. Jesus Cristo de-mais"), lyrics(convert(tune("semantic", "c d|",
					"w:a b", "w:1.~Je-sus Cris_to | de\\-mais")), 1));
			// A word that runs on to the next w: line (Cris-) keeps its hyphen, so MidiText joins the next syllable to it
			// instead of starting a new line there
			assertEquals(List.of("0:one ", "1:Cris-", "2:/t\u00e3o ", "3:two "), lyrics(convert(tune("semantic",
					"c d|", "w:one Cris-", "e f|", "w:t\u00e3o two")), 1));
			// Syllables beyond the notes are dropped
			assertEquals(List.of("0:a ", "1:b "), lyrics(convert(tune("semantic", "c d|", "w:a b c d")), 1));
		}

		@Deprecated
		@Disabled("Not relevant any more")
		@Test
		void brokenRhythmWorksWithChords() throws Exception {
			// [ce]>d : the chord is dotted, the note after it halved
			Sequence s = convert(tune("semantic", "[ce]>d f|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(0, 64), on(3 * q / 4, 62), on(q, 65)), noteOns(s));
			// c>[ce] : the other way round, the whole chord halved (ABC 2.1)
			s = convert(tune("semantic", "c>[ce] d|"));
			assertEquals(List.of(on(0, 60), on(3 * q / 4, 60), on(3 * q / 4, 64), on(q, 62)), noteOns(s));
			assertTrue(noteEvents(s).contains(off(q, 64)), noteEvents(s).toString());
			// Lotro refuses [ce]>d, and halves only the chord's first note in c>[ce] (both tested): errors
			assertThrows(FileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "[ce]>d f|"), Profile.ABC_PLAYER_STRICT));
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "c>[ce] d|"), Profile.ABC_PLAYER_STRICT));
		}

		@Test
		void inlineFieldsApplyFromWhereTheyAre() throws Exception {
			// [K:G] sharpens every f from there on; [L:1/4] doubles the length of the notes after it
			Sequence s = convert(tune("semantic", "f [K:G] f [L:1/4] c d|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 65), on(q / 2, 66), on(q, 60), on(2 * q, 62)), noteOns(s));
			// Fields that play no part are skipped
			assertEquals(List.of(60, 62), noteOns(convert(tune("semantic", "c [P:A] [I:foo] d|"))).stream()
					.map(NoteEvent::pitch).toList());
			// Q: can't change the tempo in the middle of a part, inline or not
			assertThrows(FileParseException.class, () -> convert(tune("semantic", "c [Q:200] d|")));
			// Tested in Lotro: it refuses a part with an inline field
			for (String body : List.of("f [K:G] f|", "c [L:1/4] d|", "c d|[M:3/4] e f g|", "c [P:A] d|", "c [I:x] d|"))
				assertThrows(LotroFileParseException.class,
						() -> ConversionDump.convert(tune("semantic", body), Profile.ABC_PLAYER_STRICT), body);
			// Tested in Lotro (B40): an L: line after the first notes changes nothing there, so it's a Lotro error; in
			// the header it's fine
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "c d|", "L:1/4", "e f|"), Profile.ABC_PLAYER_STRICT));
			// ... nor after the notes when it keeps the length (files made for Lotro repeat it after a mid-song M:)
			ConversionDump.convert(tune("semantic", "c d|", "M:2/4", "L:1/8", "e f|"), Profile.ABC_PLAYER_STRICT);
			assertEquals(List.of(on(0, 60), on(q / 2, 62), on(q, 64), on(2 * q, 65)),
					noteOns(convert(tune("semantic", "c d|", "L:1/4", "e f|"))));
		}

		@Test
		void chordLimitCountsDifferentNotes() throws Exception {
			// Tested in Lotro (B72): a chord of 7 different notes is too many, but a doubled note doesn't count (Lotro
			// ignores it): 7 notes with 6 or 5 different play (the user's old files: Dead.abc, cure3-5.abc). Rests count
			// as one (B73): 6 notes and a rest are too many, 5 notes and two rests play
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "[CEGcegb] c|"), Profile.ABC_PLAYER_STRICT));
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "[CEGcegz] c|"), Profile.ABC_PLAYER_STRICT));
			ConversionDump.convert(tune("semantic", "[C2E2G2c2e2zz] c|"), Profile.ABC_PLAYER_STRICT);
			ConversionDump.convert(tune("semantic", "[CEGcegg] c|"), Profile.ABC_PLAYER_STRICT);
			ConversionDump.convert(tune("semantic", "[=F2C2A,2A,2F,2F,2C,2] c|"), Profile.ABC_PLAYER_STRICT);
		}

		/** The notes played with Params.expandRepeats, in ABC (c d e ...). */
		private String playedWithRepeats(String... body) throws Exception {
			Sequence s = convert(tune("semantic", body).with(p -> p.expandRepeats = true));
			StringBuilder notes = new StringBuilder();
			for (NoteEvent note : noteOns(s))
				notes.append(Note.fromId(note.pitch()).abc);
			return notes.toString();
		}

		@Test
		void repeatsPlayOnceWithoutExpandRepeats() throws Exception {
			// As in Lotro, which plays no repeats: every ending plays, one after the other
			Sequence s = convert(tune("semantic", "|: c d |1 e :|2 f |]"));
			assertEquals(List.of(60, 62, 64, 65), noteOns(s).stream().map(NoteEvent::pitch).toList());
		}

		@Test
		void repeatsArePlayedWithExpandRepeats() throws Exception {
			assertEquals("cdcde", playedWithRepeats("|: c d :| e |"));
			// Without |: from the part's start, or from the last double bar or :| (ABC 2.1, 4.8)
			assertEquals("cdcde", playedWithRepeats("c d :| e |"));
			assertEquals("cdedef", playedWithRepeats("c || d e :| f |"));
			assertEquals("cdcdeef", playedWithRepeats("c d :| e :| f |"));
			// :: :|: and :||: end one repeat and start the next
			assertEquals("ccdd", playedWithRepeats("|: c :: d :|"));
			assertEquals("ccdd", playedWithRepeats("|: c :|: d :|"));
			assertEquals("ccdd", playedWithRepeats("|: c :||: d :|"));
			// Over several lines, and the section is played again from mid-line
			assertEquals("cdefdefg", playedWithRepeats("c |: d e |", "f :| g |]"));
		}

		@Test
		void endingsArePlayedOnTheirPass() throws Exception {
			assertEquals("cdecdf", playedWithRepeats("|: c d |1 e :|2 f |]"));
			assertEquals("cdce", playedWithRepeats("|: c [1 d :| [2 e |]"));
			assertEquals("cdcecf", playedWithRepeats("|: c [1 d :| [2 e :| [3 f |]"));
			// [1,3 and [2-3: an ending for more passes (ABC 2.1, 4.10)
			assertEquals("cdcecdf", playedWithRepeats("|: c [1,3 d :| [2 e :| f |]"));
			assertEquals("cdcece", playedWithRepeats("|: c [1 d :| [2-3 e :| |]"));
			// Endings without a repeat play once, one after the other
			assertEquals("cde", playedWithRepeats("c [1 d | [2 e |]"));
			// Tested in Lotro: :: and :||: play, :|: and :|] are refused
			for (String body : List.of("|: c d :: e f :|", "|: c d :||: e f :|"))
				ConversionDump.convert(tune("semantic", body), Profile.ABC_PLAYER_STRICT);
			for (String body : List.of("|: c d :|: e f :|", "|: c d :|] e f|"))
				assertThrows(FileParseException.class,
						() -> ConversionDump.convert(tune("semantic", body), Profile.ABC_PLAYER_STRICT), body);
			// Tested in Lotro: it plays nothing of a part with an ending for several passes; [1 [2 play on
			for (String body : List.of("c d [1,3 e f | [2 g a |] b c'|", "c d [1-2 e f | g a b c'|]", "c |1,2 d :|"))
				assertThrows(LotroFileParseException.class,
						() -> ConversionDump.convert(tune("semantic", body), Profile.ABC_PLAYER_STRICT), body);
			ConversionDump.convert(tune("semantic", "c [1 d | [2 e |]"), Profile.ABC_PLAYER_STRICT);
		}

		@Test
		void verseOneIsSungOnEveryPass() throws Exception {
			// Later verses are for the times the part is played again (ABC 2.1, 5.2), not for repeats in it: they follow as
			// text after the last note started
			Sequence s = convert(tune("semantic", "|: c d :|", "w:one two", "w:three four").with(p -> p.expandRepeats = true));
			assertEquals(List.of("0:one ", "1:two ", "2:/one ", "3:two ", "3:/three four"), lyrics(s, 1));
			// The syllables go to the notes as written; a note the pass doesn't play drops its syllable
			s = convert(tune("semantic", "|: c d |", "w:a b", "w:e f", "[1 e :| [2 f |]", "w:c *", "w:* g")
					.with(p -> p.expandRepeats = true));
			assertEquals(List.of("0:a ", "1:b ", "2:/c ", "3:/a ", "4:b ", "5:/e f", "5:/g"), lyrics(s, 1));
			// A hymn with a repeated second half: verse 1 on both passes, verse 2 as text
			s = convert(tune("semantic", "c |: d e :|", "w:1.~a b c", "w:2.~x y z").with(p -> p.expandRepeats = true));
			assertEquals(List.of("0:1. a ", "1:b ", "2:c ", "3:/b ", "4:c ", "4:/2. x y z"), lyrics(s, 1));
			// A W: line in a repeated section is written once
			s = convert(tune("semantic", header(), "|: c |", "W:Verse", "d :|").with(p -> p.expandRepeats = true));
			assertEquals(List.of("0:<Verse"), lyrics(s, 0));
		}

		@Test
		void tempoWithTextIsALotroError() throws Exception {
			// Q:"Allegro" 1/4=120 : the text is skipped. Tested in Lotro: it refuses the part.
			assertEquals(90, abcInfoOf(tune("semantic", header("Q:\"Allegro\" 1/4=90"), "c d|")).getPrimaryTempoBPM());
			assertThrows(LotroFileParseException.class, () -> ConversionDump
					.convert(tune("semantic", header("Q:\"Allegro\" 1/4=90"), "c d|"), Profile.ABC_PLAYER_STRICT));
		}

		@Test
		void layoutAndDecorationsAreSkipped() throws Exception {
			// $ line break, ` in a beam, [|] invisible bar, +fermata+ (ABC 2.0 decoration): nothing that's played. (+trill+
			// is played: ornamentsArePlayed)
			for (String body : List.of("c d|$ e f|", "c`d e f|", "c d[|]e f|", "+accent+c d e f|", "c +fermata+d e f|")) {
				Sequence s = convert(tune("semantic", body));
				assertEquals(List.of(60, 62, 64, 65), noteOns(s).stream().map(NoteEvent::pitch).toList(), body);
				// With Lotro errors they're errors (tested): Lotro refuses [|], plays nothing with +trill+ (and any
				// other +decoration+ that isn't a volume), and stops playing at $ and `
				assertThrows(LotroFileParseException.class,
						() -> ConversionDump.convert(tune("semantic", body), Profile.ABC_PLAYER_STRICT), body);
			}
			// [|] is a bar line: it ends an accidental
			assertEquals(List.of(61, 60), noteOns(convert(tune("semantic", "^c[|]c|"))).stream().map(NoteEvent::pitch)
					.toList());
			// +ceg+ (a chord in ABC 1.6) stays an error
			assertThrows(FileParseException.class, () -> convert(tune("semantic", "+ceg+ d|")));
		}

		@Test
		void multiMeasureRestLastsWholeBars() throws Exception {
			// Z2 : 2 bars of rest (4/4 here), Z : 1 bar
			Sequence s = convert(tune("semantic", "c8|Z2|c8|Z|c8|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(12 * q, 60), on(20 * q, 60)), noteOns(s));
			// Tested in Lotro: it refuses the part
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "c8|Z2|c8|"), Profile.ABC_PLAYER_STRICT));
		}

		@Test
		void letterDecorationsAreSkipped() throws Exception {
			// H L O S u v : decorations in short form (ABC 2.1, 4.14) that change nothing. (T M P are ornaments that are
			// played: ornamentsArePlayed)
			for (String body : List.of("Hc Ld ue vf|", "Oc d Se f|")) {
				Sequence s = convert(tune("semantic", body));
				assertEquals(List.of(60, 62, 64, 65), noteOns(s).stream().map(NoteEvent::pitch).toList(), body);
			}
			// Tested in Lotro (T H u v): it refuses the part
			for (String body : List.of("Tc Hd ue vf|", "Lc Md Oe Pf|Sc d e f|"))
				assertThrows(LotroFileParseException.class,
						() -> ConversionDump.convert(tune("semantic", body), Profile.ABC_PLAYER_STRICT), body);
		}

		@Test
		void tempoWordSetsTheTempo() throws Exception {
			// Q:"Allegro" : a tempo word without a tempo (ABC 2.1, 3.1.8) sets a typical tempo; an unknown text keeps it
			assertEquals(130, abcInfoOf(tune("semantic", header("Q:\"Allegro\""), "c d|")).getPrimaryTempoBPM());
			assertEquals(90, abcInfoOf(tune("semantic", header("Q:\"Andante con moto\""), "c d|")).getPrimaryTempoBPM());
			assertEquals(120, abcInfoOf(tune("semantic", header("Q:\"Swing!\""), "c d|")).getPrimaryTempoBPM());
			// With a tempo, the tempo counts
			assertEquals(100, abcInfoOf(tune("semantic", header("Q:\"Allegro\" 1/4=100"), "c d|")).getPrimaryTempoBPM());
		}

		@Test
		void tempoNoteLengthIsTheBeat() throws Exception {
			// Q:3/8=120 is 120 dotted quarters a minute (ABC 2.1, 3.1.8), with Params.specTempo (a Maestro project
			// setting). The tempo played is in beats of the meter's denominator (the MIDI's quarter notes), so in 6/8
			// that's 360 eighths.
			assertEquals(360, tempo("M:6/8", "Q:3/8=120"));
			assertEquals(60, tempo("M:2/2", "Q:1/4=120"));
			assertEquals(120, tempo("M:4/4", "Q:1/4=120"));
			assertEquals(200, tempo("M:5/4", "Q:1/4 3/8 1/4 3/8=40")); // A beat of several lengths: 5/4
			// Without a note length, unit notes (ABC 2.1, 10.1): 120 eighths, in 4/4 60 quarters
			assertEquals(120, tempo("M:6/8", "Q:120"));
			assertEquals(60, tempo("M:4/4", "Q:120"));
			// Without Q: 120 beats, a dotted quarter in 6/8 9/8 12/8; a tempo word likewise
			assertEquals(120, tempo("M:6/8", "Q:120"));
			assertEquals(60, tempo("M:4/4", "Q:120"));
			// Without Q: abc2midi's 120 quarters in every meter (ABC 2.1 gives no tempo), in 6/8 240 eighths, 80 dotted
			// quarters
			assertEquals(240, tempo("M:6/8", "-Q"));
			assertEquals(120, tempo("M:3/4", "-Q"));
			assertEquals(120, tempo("M:4/4", "-Q"));
			assertEquals(60, tempo("M:2/2", "-Q"));
			// A tempo word's beat is the felt one, a dotted quarter in 6/8 9/8 12/8
			assertEquals(390, tempo("M:6/8", "Q:\"Allegro\""));
			// M: may come after Q: in the header
			AbcCase late = AbcCase.of("semantic", "X:1", "T:Test", "Q:3/8=120", "M:6/8", "L:1/8", "K:C", "c d|");
			assertEquals(360, abcInfoOf(specTempo(late)).getPrimaryTempoBPM());
			// What's played: a bar of 6/8 at Q:3/8=120 lasts as long as at Q:360
			long bar = convert(tune("semantic", header("M:6/8", "Q:360"), "c6|")).getMicrosecondLength();
			assertEquals(bar, convert(specTempo(tune("semantic", header("M:6/8", "Q:3/8=120"), "c6|"))).getMicrosecondLength());
			assertEquals(convert(tune("semantic", header("M:6/8", "Q:240"), "c6|")).getMicrosecondLength(),
					convert(specTempo(tune("semantic", header("M:6/8", "-Q"), "c6|"))).getMicrosecondLength());
			// Parts must still have the same tempo
			assertThrows(FileParseException.class, () -> convert(specTempo(AbcCase.of("semantic", AbcCase.concat(
					AbcCases.part(1, "One", "c d|"), new String[] { "X:2", "T:Two", "M:4/4", "L:1/8", "Q:1/4=120", "K:C", "c d|" })))));
			// Without specTempo (the default, and existing projects), the meter's denominator, as in Lotro
			assertEquals(120, abcInfoOf(tune("semantic", header("M:6/8", "Q:3/8=120"), "c d|")).getPrimaryTempoBPM());
			assertEquals(120, abcInfoOf(tune("semantic", header("M:6/8", "-Q"), "c d|")).getPrimaryTempoBPM());
		}

		@Test
		void tempoNoteLengthIsALotroErrorUnlessItIsTheBeat() throws Exception {
			// Tested in Lotro (B15): it plays Q:3/8=120 in 6/8 as 120 eighths a minute; that's an error saying so
			LotroFileParseException e = assertThrows(LotroFileParseException.class, () -> ConversionDump
					.convert(tune("semantic", header("M:6/8", "Q:3/8=120"), "c d|"), Profile.ABC_PLAYER_STRICT));
			assertTrue(e.getMessage().contains("Q:1/8=360"), e.getMessage());
			assertThrows(LotroFileParseException.class, () -> ConversionDump
					.convert(tune("semantic", header("M:2/2", "Q:1/4=120"), "c d|"), Profile.ABC_PLAYER_STRICT));
			// The meter's beat is fine; without Q: or without a note length, Lotro's reading (no error)
			for (String[] fields : List.of(new String[] { "M:6/8", "Q:1/8=120" }, new String[] { "M:4/4", "Q:1/4=120" },
					new String[] { "M:6/8", "Q:120" }, new String[] { "M:6/8", "-Q" })) {
				AbcInfo info = new AbcInfo();
				ConversionDump.run(tune("semantic", header(fields), "c d|"), Profile.ABC_PLAYER_STRICT, false, info);
				assertEquals(120, info.getPrimaryTempoBPM(), String.join(" ", fields));
			}
		}

		@Test
		void tuneTypeSetsTheTempoWithoutQ() throws Exception {
			// Without Q: the tune's type in R: gives the tempo, counted in its own beat (RhythmTempo). The tempo played
			// is in beats of the meter's denominator: a reel's 100 half notes are 200 quarters in 4/4
			assertEquals(200, typeTempo("R:reel", "M:4/4", "-Q"));
			assertEquals(324, typeTempo("R:jig", "M:6/8", "-Q")); // 108 dotted quarters
			assertEquals(339, typeTempo("R:slip jig", "M:9/8", "-Q")); // 113 dotted quarters, not the jig's
			assertEquals(108, typeTempo("R:waltz", "M:3/4", "-Q"));
			// Its own beat keeps the bars: a waltz in 6/8 (The Session's Ice And Fire), a slide in 6/8 or 12/8
			assertEquals(216, typeTempo("R:waltz", "M:6/8", "-Q")); // 36 bars a minute, as in 3/4
			assertEquals(typeTempo("R:slide", "M:12/8", "-Q"), typeTempo("R:slide", "M:6/8", "-Q"));
			// Encoded text (Norbeck's sl\"angpolska), and an unknown type: abc2midi's Q:1/4=120
			assertEquals(112, typeTempo("R:sl\\\"angpolska", "M:3/4", "-Q"));
			assertEquals(240, typeTempo("R:hora", "M:6/8", "-Q"));
			// Balkan dances in their quick beat: a rachenitsa 220 eighths, 440 sixteenths in 7/16
			assertEquals(440, typeTempo("R:rachenitsa", "M:2+2+3/16", "-Q"));
			// A Q: wins; without specTempo (Lotro files, existing projects) R: changes nothing
			assertEquals(60, typeTempo("R:reel", "M:4/4", "Q:1/4=60"));
			assertEquals(120, abcInfoOf(AbcCase.of("semantic", "X:1", "T:t", "R:reel", "M:4/4", "L:1/8", "K:C", "c d|"))
					.getPrimaryTempoBPM());
			// The file header's type, for its parts; the song has one tempo: the first part's
			assertEquals(324, abcInfoOf(specTempo(AbcCase.of("semantic", "R:jig", "M:6/8", "L:1/8", "X:1", "T:t", "K:C",
					"c d|"))).getPrimaryTempoBPM());
			assertEquals(200, abcInfoOf(specTempo(AbcCase.of("semantic", AbcCase.concat(new String[] { "X:1", "T:a",
					"R:reel", "M:4/4", "L:1/8", "K:C", "c d|", "" }, new String[] { "X:2", "T:b", "R:polka", "M:4/4",
					"L:1/8", "K:C", "e f|" })))).getPrimaryTempoBPM());

			// A type danced in bars of 4/4, written in 2/4 (O'Neill's reels and hornpipes, a 4/4 bar's notes in each
			// 2/4 bar): its beat is halved, a reel's 1/2=100 is 1/4=100, a hornpipe's 1/4=160 is 1/8=160 (80 quarters),
			// whatever the L:, also with an M: after the R:. A polka's 2/4 is its own: 120 quarters
			assertEquals(100, headerTempo("R:Reel", "M:2/4", "L:1/16"));
			assertEquals(80, headerTempo("R:Hornpipe", "M:2/4", "L:1/16"));
			assertEquals(80, headerTempo("R:Hornpipe", "M:2/4", "L:1/8"));
			assertEquals(80, headerTempo("R:Hornpipe", "M:2/4"));
			assertEquals(80, headerTempo("M:C|", "R:hornpipe", "M:2/4", "L:1/16"));
			assertEquals(120, headerTempo("R:polka", "M:2/4", "L:1/8"));
			assertEquals(200, headerTempo("R:reel", "M:4/4", "L:1/16"));
		}

		/** The tempo played for a tune without Q: with these header fields, with specTempo. */
		private int headerTempo(String... fields) throws Exception {
			List<String> lines = new ArrayList<>(List.of("X:1", "T:t"));
			lines.addAll(List.of(fields));
			lines.addAll(List.of("K:C", "c d|"));
			return abcInfoOf(specTempo(AbcCase.of("semantic", lines.toArray(String[]::new)))).getPrimaryTempoBPM();
		}

		/** The tempo played for a tune with this R:, M: and Q: ("-Q" for none), with specTempo. */
		private int typeTempo(String rhythm, String meter, String tempo) throws Exception {
			List<String> lines = new ArrayList<>(List.of("X:1", "T:t", rhythm, meter, "L:1/8"));
			if (!tempo.equals("-Q"))
				lines.add(tempo);
			lines.addAll(List.of("K:C", "c d|"));
			return abcInfoOf(specTempo(AbcCase.of("semantic", lines.toArray(String[]::new)))).getPrimaryTempoBPM();
		}

		private int tempo(String meter, String tempo) throws Exception {
			return abcInfoOf(specTempo(tune("semantic", header(meter, tempo), "c d|"))).getPrimaryTempoBPM();
		}

		private static AbcCase specTempo(AbcCase abcCase) {
			return abcCase.with(p -> p.specTempo = true);
		}

		@Test
		void standardPitchIsTheWrittenPitch() throws Exception {
			// Lotro's reading: C is C3, and T: picks the instrument, which shifts the octave (a flute two up)
			AbcCase flute = tune("semantic", header("T:The Flute Player"), "C c|");
			assertEquals(List.of(72, 84), notePitches(flute));
			// Params.standardPitch (standard ABC, e.g. folk tunes): C is middle C (ABC 2.1), and T: is just a title
			assertEquals(List.of(60, 72), notePitches(standardPitch(flute)));
			assertEquals(LotroInstrument.DEFAULT_INSTRUMENT, abcInfoOf(standardPitch(flute)).getPartInstrument(1));
			// %%made-for still picks the instrument, but not the octave
			AbcCase madeFor = tune("semantic", AbcCases.extended("%%made-for Basic Flute"), "C c|");
			assertEquals(LotroInstrument.BASIC_FLUTE, abcInfoOf(standardPitch(madeFor)).getPartInstrument(1));
			assertEquals(List.of(60, 72), notePitches(standardPitch(madeFor)));
			// K: transposition still counts (ABC 2.1, 4.6)
			assertEquals(List.of(48), notePitches(standardPitch(tune("semantic", header("K:C octave=-1"), "C|"))));
			// With Lotro errors (the ABC Player), always Lotro's reading
			assertEquals(noteEvents(ConversionDump.convert(flute, Profile.ABC_PLAYER_STRICT)),
					noteEvents(ConversionDump.convert(standardPitch(flute), Profile.ABC_PLAYER_STRICT)));
		}

		private List<Integer> notePitches(AbcCase abcCase) throws Exception {
			return noteOns(convert(abcCase)).stream().map(NoteEvent::pitch).toList();
		}

		private static AbcCase standardPitch(AbcCase abcCase) {
			return abcCase.with(p -> p.standardPitch = true);
		}

		@Test
		void filesMadeForLotroAreRecognised() {
			// TRUE: any sure sign of Lotro: Maestro's extended fields, BruTE, a Lotro instrument in the title
			List<String[]> lotro = List.of(new String[] { "%%song-title Song" }, new String[] { "%%part-name Lute" },
					new String[] { "%%abc-creator Maestro v2.5.0" }, new String[] { "%%made-for Basic Flute" },
					new String[] { "% Produced with Bruzo's Transcoding Environment 2.0 alpha" },
					new String[] { "X:1", "T: test1  1/14 [flute] 0:10", "Z: Transcribed with BruTE 64 300 1" },
					// LotRO MIDI Player (Maestro's predecessor): no other sign, chords shortened by a rest inside
					new String[] { "X: 1", "T: Song (0:16)",
								   "Z: Transcribed using LotRO MIDI Player: http://lotro.acasylum.com/midi", "%  Transpose: 0",
						 		   "L: 1/4", "Q: 120", "K: C", "", "A/2 [^c/4 e/2 z/4] a/4" },
					new String[] { "X:1", "T: test1  1/14 [flute] 0:10" }, new String[] { "X:1", "T:Song [Lute]" },
					new String[] { "X:1", "T: Concert-Rachmaninoff[Basic Lute](10:04)" },
					new String[] { "X:1", "T:Lute of Ages solo" }, new String[] { "X:1", "T:Song - Basic Fiddle" },
					// ... wins over signs of standard ABC
					new String[] { "X:1", "T:Song [Lute]", "R:reel", "K:C", "\"Am\"c d|" },
					new String[] { "X:1", "T:Song [Lute]", "M:6/8", "Q:3/8=120", "K:C", "Tc d|" });
			for (String[] lines : lotro)
				assertEquals(Boolean.TRUE, madeForLotro(lines), String.join(" / ", lines));

			// FALSE: else any sign of standard ABC: chord symbols, voices, background fields, a note Lotro can't play ...
			List<String[]> standard = List.of(new String[] { "X:1", "T:Tune", "K:G", "\"G\"G2 B d \"D7\"c2 A F|" },
					new String[] { "X:1", "T:Tune", "K:C", "V:1", "c d|", "V:2", "C D|" },
					new String[] { "X:1", "T:Haste to the Wedding (jig)", "R:jig", "K:D", "d2f fed|" },
					new String[] { "X:1", "T:The Kesh (fiddle tune)", "O:Ireland", "K:G", "c d|" },
					new String[] { "X:1", "T:Tune", "S:Played by someone", "K:C", "c d|" },
					new String[] { "X:1", "T:Bass Reeves", "K:C", "c d e' f|" }, // e' is above Lotro's c'
					new String[] { "X:1", "T:Reel [Bass line]", "K:C", "C,, D|" }, // Below Lotro's C,
					new String[] { "X:1", "T:Hornpipe [for Horn]", "K:C", "c ^c' d|" },
					// ... or ABC that Lotro refuses or plays otherwise (tested in Lotro). In the header: a Q: note length
					// that isn't the meter's beat (B15; tune_001364: Q:3/8=120 in 6/8), also with Q: before M:, or with
					// the file header's M:; several beats; a tempo word (B20)
					new String[] { "X:1", "T:Tune", "M:6/8", "Q:3/8=120", "K:G", "G2 G GFG|" },
					new String[] { "X:1", "T:Tune", "Q:3/8=120", "M:6/8", "K:G", "G2 G GFG|" },
					new String[] { "M:6/8", "X:1", "T:Tune", "Q:3/8=120", "K:G", "G2 G GFG|" },
					new String[] { "X:1", "T:Tune", "M:4/4", "Q:1/8=240", "K:C", "c d|" },
					new String[] { "X:1", "T:Tune", "M:5/4", "Q:1/4 3/8=40", "K:C", "c d|" },
					new String[] { "X:1", "T:Tune", "Q:\"Allegro\"", "K:C", "c d|" },
					// ... K: with more than the key and mode (B23, B49), or empty (B38); M:none (B21); +: (B22); an L:
					// after the notes (B40)
					new String[] { "X:1", "T:Tune", "K:C clef=bass", "c d|" }, new String[] { "X:1", "K:HP", "c d|" },
					new String[] { "X:1", "K:none", "c d|" }, new String[] { "X:1", "K:G ^c", "c d|" },
					new String[] { "X:1", "K:", "c d|" }, new String[] { "X:1", "M:none", "K:C", "c d|" },
					new String[] { "X:1", "K:C", "c d|", "w:one", "+:two" },
					new String[] { "X:1", "L:1/8", "K:C", "c d|", "L:1/4", "e f|" },
					// ... in the notes: :|: :|] (B2, B4), decorations (B12, B17, B50, B56), grace notes (B55), y (B58),
					// Z (B16), $ (B36), ` (B37), [|] (B18), inline fields (B7-B10), a length or tie after a chord (B11,
					// B65), a broken rhythm next to a chord (B5, B6)
					new String[] { "X:1", "K:C", "|: c d :|: e f :|" }, new String[] { "X:1", "K:C", "|: c d ::|: e f :|" },
					new String[] { "X:1", "K:C", "|: c d :|] e f|" }, new String[] { "X:1", "K:C", "!trill!c d|" },
					new String[] { "X:1", "K:C", "!f!c d|" }, new String[] { "X:1", "K:C", "+trill+c d|" },
					new String[] { "X:1", "K:C", "{g}c d|" }, new String[] { "X:1", "K:C", "Tc d|" },
					new String[] { "X:1", "K:C", "c Hd|" }, new String[] { "X:1", "K:C", "uc vd|" },
					new String[] { "X:1", "K:C", "c y d|" }, new String[] { "X:1", "K:C", "Z|c d|" },
					new String[] { "X:1", "K:C", "c d $ e|" }, new String[] { "X:1", "K:C", "c`d e|" },
					new String[] { "X:1", "K:C", "c [|] d|" }, new String[] { "X:1", "K:C", "c [K:G] f|" },
					new String[] { "X:1", "K:C", "[ce]2 d|" }, new String[] { "X:1", "K:C", "[ce]- [ce] d|" },
					new String[] { "X:1", "K:C", "[ce]>d e|" }, new String[] { "X:1", "K:C", "c>[ce] d|" },
					// ... also in a file without X: (no free text there), and after free text
					new String[] { "K:C", "Tc d|" },
					new String[] { "My Tunes (c) 2026", "", "X:1", "R:reel", "K:C", "c d|" });
			for (String[] lines : standard)
				assertEquals(Boolean.FALSE, madeForLotro(lines), String.join(" / ", lines));

			// FALSE first of all: more X: than a song made for Lotro has parts (24) is a tune book, whatever else it has
			List<String> book = new ArrayList<>(List.of("%%part-name Lute"));
			for (int x = 1; x <= 25; x++)
				book.addAll(List.of("X:" + x, "T:Song [Lute]", "K:C", "c d|", ""));
			assertEquals(Boolean.FALSE, madeForLotro(book.toArray(String[]::new)));
			assertEquals(Boolean.TRUE, madeForLotro(book.subList(0, 1 + 24 * 5).toArray(String[]::new)));

			// null, unsure, with LotRO MIDI Player's "% Transpose:" but another Z: line (a player changed it, maybe
			// the notes too): then no sign of standard ABC counts, here a voice and a chord symbol
			assertNull(madeForLotro("X:1", "T:Song", "Z:Transcribed by Aifel", "% Transpose: -12", "K:C", "V:1",
					"\"Am\"c d|", "V:2", "C D|"));

			// null, unsure, with LotRO MIDI Player's "% Transpose:" but another Z: line (a player changed it, maybe
			// the notes too): then no sign of standard ABC counts, here a voice and a chord symbol
			assertNull(madeForLotro("X:1", "T:Song", "Z:Transcribed by Aifel", "% Transpose: -12", "K:C", "V:1",
					"\"Am\"c d|", "V:2", "C D|"));
			// ... but a sure sign of Lotro still decides
			assertEquals(Boolean.TRUE, madeForLotro("X:1", "T:Song [Lute]", "Z:Transcribed by Aifel", "% Transpose: -12",
					"K:C", "\"Am\"c d|"));

			// null: else nothing tells: the caller asks the user. Instrument words in a folk title and volume marks
			// (ABC too) are no signs either way, nor is what Lotro plays as ABC 2.1 says, or plays plain
			List<String[]> noSigns = List.of(new String[] { "X:1", "T:The Piper's Farewell", "K:C", "c d|" },
					new String[] { "X:1", "T:Morpeth Rant (Fiddle)", "K:D", "c d|" },
					new String[] { "X:1", "K:C", "+fff+ c d +p+ e +pppp+f +ffff+g|" },
					new String[] { "X:1", "K:C", "+p+c+f+d|" }, new String[] { "X:1", "T:Tune", "K:C", "c d|" },
					// Lotro's range: C, to c' (quoted text isn't notes)
					new String[] { "X:1", "K:C", "C, c' \"^e''\" d|" },
					// Repeat signs Lotro plays (B1, B3, B59), and ending lists, which it plays silently (B28)
					new String[] { "X:1", "K:C", "|: c d :| e f :: g a :||: b c' :|" },
					new String[] { "X:1", "K:C", "|: c |1 d :|2 e |] [1 f | [2 g |]" },
					new String[] { "X:1", "K:C", "c [1,3 d | [2 e |] c [1-2 d | e |]" },
					// Only in the notes: not in quoted text, comments or lyrics
					new String[] { "X:1", "K:C", "\"^:|: Tempo\"c d| % Z $ !f!", "w:Ma'am y T H" },
					// Q: with the meter's beat (B15, B69: Q:1/4=60 plays), also in 2/2 and C|; the tempo alone
					new String[] { "X:1", "M:4/4", "Q:1/4=120", "K:C", "c d|" },
					new String[] { "X:1", "M:2/2", "Q:1/2=60", "K:C", "c d|" },
					new String[] { "X:1", "M:C|", "Q:1/2=60", "K:C", "c d|" },
					new String[] { "X:1", "M:6/8", "Q:1/8=240", "K:C", "c d|" }, new String[] { "X:1", "Q:120", "K:C", "c d|" },
					// The key and its mode (B33), an L: in the header, ~ and . (played plain, B54), a tie, a chord
					new String[] { "X:1", "L:1/4", "K: C maj", "c d|" }, new String[] { "X:1", "K:D mix", "c d|" },
					new String[] { "X:1", "K:Am", "c d|" }, new String[] { "X:1", "K:F#m", "c d|" },
					new String[] { "X:1", "K:Bb", "~c .d e-e [ceg]|" },
					// Maestro's export without its %% lines (a hand-made file looks the same)
					new String[] { "X: 1", "T: Song", "M: 4/4", "Q: 60", "K: C maj", "L: 1/4000000",
							"+mf+ c1000000 z500000 [c1000000e1000000] |]" },
					// Free text: before the first X: and after a blank line (a tune book's notes and copyright; its
					// letters aren't decorations)
					new String[] { "These are my Tunes. Hornpipes, Marches (c) 2026 Me $5", "", "X:1", "T:Tune", "K:C",
							"c d|", "", "Played every Tuesday: Thanks to Mary!", "X:2", "T:Tune two", "K:C", "e f|" },
					new String[] { "X:1", "K:C", "c d|", "", "Tc d|" },
					// One voice (hand-made files for Lotro: V:1), an L: after the notes that keeps the unit note length
					// ("M:2/4", "L:1/8" mid-song)
					new String[] { "X:1", "M:4/4", "L:1/8", "K:G", "V:1", "c d|" },
					new String[] { "X:1", "L:1/8", "K:C", "c d|", "M:2/4", "L: 1/8", "e f|" },
					// Other tools' %%abc-version and %%abc-creator (hum2abc, Essen's Chinese songs)
					new String[] { "X:1", "T:Tiqi gege zou xikou", "%%abc-version 2.0", "%%abc-creator hum2abc beta",
							"K:G", "c d|" });
			for (String[] lines : noSigns)
				assertNull(madeForLotro(lines), String.join(" / ", lines));
		}

		private static Boolean madeForLotro(String... lines) {
			return AbcToMidi.isMadeForLotro(AbcCase.of("detect", lines).filesData());
		}

		@Test
		void chordSymbolsBecomeAnAccompaniment() throws Exception {
			// Params.chordAccompaniment (Maestro): a bass track (theorbo) with the chord's root on the first beat of each
			// bar and at each chord, and a chords track (lute) with the chord on the other beats. Chords from C3, bass C2.
			// 3/4: bass, then the chord once, held to the end of the bar
			AbcCase waltz = chords(tune("semantic", header("M:3/4", "L:1/4"), "\"G\"G B d|\"D7\"c A F|"));
			Sequence s = convert(waltz);
			long q = s.getResolution();
			assertEquals(4, s.getTracks().length); // Track 0, the melody, the bass, the chords
			assertEquals(List.of(on(0, 43), on(3 * q, 38)), noteOns(s, 2));
			assertEquals(List.of(on(q, 55), off(3 * q, 55)), noteEvents(s, 3).stream().filter(e -> e.pitch() == 55).toList());
			assertEquals(List.of(), pitchesAt(noteOns(s, 3), 2 * q)); // Held, not struck again
			assertEquals(List.of(50, 54, 57, 60), pitchesAt(noteOns(s, 3), 4 * q));
			assertEquals(List.of(), pitchesAt(noteOns(s, 3), 3 * q)); // The bass's beat
			// 4/4: root, chord, the chord's fifth in the bass, chord (Cdim: its fifth is Gb)
			Sequence four = convert(chords(tune("semantic", header("L:1/4"), "\"G\"G B d B|\"Cdim\"c4|")));
			assertEquals(List.of(on(0, 43), on(2 * q, 38), on(4 * q, 36), on(6 * q, 42)), noteOns(four, 2));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(four, 3), q));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(four, 3), 3 * q));
			AbcInfo info = abcInfoOf(waltz);
			assertEquals("Test - Bass", info.getPartName(2));
			assertEquals(LotroInstrument.BASIC_THEORBO, info.getPartInstrument(2));
			assertEquals("Test - Chords", info.getPartName(3));
			assertEquals(LotroInstrument.LUTE_OF_AGES, info.getPartInstrument(3));
			// In 6/8 the beat is a dotted quarter
			Sequence jig = convert(chords(tune("semantic", header("M:6/8"), "\"D\"d2f fed|")));
			assertEquals(List.of(on(0, 38)), noteOns(jig, 2));
			assertEquals(List.of(50, 54, 57), pitchesAt(noteOns(jig, 3), 3 * q / 2));
			// A chord in mid-bar gets the bass; /B is the bass note (and beat 3 the fifth, D)
			Sequence mid = convert(chords(tune("semantic", header("L:1/4"), "\"C\"c \"G/B\"d e f|")));
			assertEquals(List.of(on(0, 36), on(q, 47), on(2 * q, 38)), noteOns(mid, 2));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(mid, 3), 3 * q));
			// A chord of a beat or less has no beat of its own: it's struck with its bass (C here), a longer one isn't
			assertEquals(List.of(48, 52, 55), pitchesAt(noteOns(mid, 3), 0));
			assertEquals(List.of(), pitchesAt(noteOns(mid, 3), q)); // G/B's bass beat
			// A hymn ("hymn" anywhere in the file): the bass and the full chord together, held; in 4/4 also on beat 3
			Sequence hymn = convert(chords(tune("semantic", header("T:Evening Hymn", "L:1/4"), "\"G\"G B d B|\"C\"c2 \"D7\"d2|")));
			assertEquals(List.of(on(0, 43), on(2 * q, 43), on(4 * q, 36), on(6 * q, 38)), noteOns(hymn, 2));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(hymn, 3), 0));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(hymn, 3), 2 * q));
			assertEquals(List.of(), pitchesAt(noteOns(hymn, 3), q));
			// Held from each strike to the next (B, 59, is only in the G chord)
			assertEquals(List.of(on(0, 59), off(2 * q, 59), on(2 * q, 59), off(4 * q, 59)),
					noteEvents(hymn, 3).stream().filter(e -> e.pitch() == 59).toList());
			// K: transposition moves the chords too: C is played as D (root D, fifth A)
			assertEquals(List.of(on(0, 38), on(2 * q, 45)),
					noteOns(convert(chords(tune("semantic", header("K:C transpose=2"), "\"C\"c8|"))), 2));
			// Each pass of a repeat
			AbcCase repeated = chords(tune("semantic", header("L:1/4"), "|:\"G\"G4:|")).with(p -> p.expandRepeats = true);
			assertEquals(List.of(on(0, 43), on(2 * q, 38), on(4 * q, 43), on(6 * q, 38)), noteOns(convert(repeated), 2));
			// Text that isn't a chord name makes no accompaniment
			assertEquals(2, convert(chords(tune("semantic", "\"a.\"c \"^text\"d \"Fine\"e f|"))).getTracks().length);
			// Without the flag, or with Lotro errors (Lotro plays no chords), none
			assertEquals(2, convert(tune("semantic", "\"G\"G B d|")).getTracks().length);
			assertEquals(2, ConversionDump.convert(chords(tune("semantic", "\"G\"G B d|")), Profile.ABC_PLAYER_STRICT)
					.getTracks().length);
		}

		@Test
		void balkanMetersInBeatGroups() throws Exception {
			// ABC 2.1 (3.1.6): M:2+2+3/8 is 7/8 in beats of 2, 2 and 3 eighths; also written (2+2+3)/8. The notes play
			// as in 7/8
			List<NoteEvent> seven = noteEvents(convert(tune("semantic", header("M:7/8"), "c2 d2 e3|")));
			assertEquals(seven, noteEvents(convert(tune("semantic", header("M:2+2+3/8"), "c2 d2 e3|"))));
			assertEquals(seven, noteEvents(convert(tune("semantic", header("M:(2+2+3)/8"), "c2 d2 e3|"))));
			// Untested in Lotro: with Lotro errors, an error saying to write the total
			assertThrows(LotroFileParseException.class, () -> ConversionDump
					.convert(tune("semantic", header("M:2+2+3/8"), "c2 d2 e3|"), Profile.ABC_PLAYER_STRICT));
			// The accompaniment follows the groups: the root on the first, the chord on each other, held to its end. (The
			// MIDI's quarter note is the meter's beat here: e is an eighth)
			Sequence s = convert(chords(tune("semantic", header("M:2+2+3/8"), "\"G\"G2 B2 d3|\"D\"A2 F2 D3|")));
			long e = s.getResolution();
			assertEquals(List.of(on(0, 43), on(7 * e, 38)), noteOns(s, 2));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(s, 3), 2 * e));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(s, 3), 4 * e));
			assertEquals(List.of(on(2 * e, 55), off(4 * e, 55), on(4 * e, 55), off(7 * e, 55)),
					noteEvents(s, 3).stream().filter(n -> n.pitch() == 55).toList());
			assertEquals(List.of(50, 54, 57), pitchesAt(noteOns(s, 3), 9 * e));
			// Plain 7/8 is counted 2+2+3 too (as a rachenitsa), 5/8 2+3; the sum may put the 3 elsewhere (3+2+2)
			assertEquals(noteEvents(s, 3), noteEvents(convert(chords(tune("semantic", header("M:7/8"),
					"\"G\"G2 B2 d3|\"D\"A2 F2 D3|"))), 3));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(convert(chords(tune("semantic", header("M:5/8"),
					"\"G\"G2 B3|"))), 3), 2 * e));
			Sequence kalamatiano = convert(chords(tune("semantic", header("M:3+2+2/8"), "\"G\"G3 B2 d2|")));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(kalamatiano, 3), 3 * e));
			assertEquals(List.of(55, 59, 62), pitchesAt(noteOns(kalamatiano, 3), 5 * e));
			// 2+2+2+3 (9 eighths) isn't compound: four groups, not three dotted quarters as 9/8
			Sequence nine = convert(chords(tune("semantic", header("M:2+2+2+3/8"), "\"G\"G2 B2 d2 g3|")));
			assertEquals(List.of(2 * e, 4 * e, 6 * e), noteOns(nine, 3).stream().filter(n -> n.pitch() == 55)
					.map(NoteEvent::tick).toList());
		}

		private static AbcCase chords(AbcCase abcCase) {
			return abcCase.with(p -> p.chordAccompaniment = true);
		}

		private static List<Integer> pitchesAt(List<NoteEvent> events, long tick) {
			return events.stream().filter(e -> e.tick() == tick).map(NoteEvent::pitch).toList();
		}

		@Test
		void freeMeterIsTimedAs4_4() throws Exception {
			// M:none : free meter (ABC 2.1, 3.1.6), timed as 4/4 with the default L:1/8
			Sequence s = convert(tune("semantic", header("M:none", "-L"), "c d e f|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(q / 2, 62), on(q, 64), on(3 * q / 2, 65)), noteOns(s));
			// Tested in Lotro: it refuses the part
			assertThrows(LotroFileParseException.class, () -> ConversionDump
					.convert(tune("semantic", header("M:none"), "c d e f|"), Profile.ABC_PLAYER_STRICT));
		}

		@Test
		void fieldContinuationJoinsTheLines() throws Exception {
			// +: continues the field before it, with a space between (ABC 2.1, 3.3)
			Sequence s = convert(tune("semantic", header(), "c d e f|", "w:one two", "+:three four", "W:A long", "+:line"));
			assertEquals(List.of("0:one ", "1:two ", "2:three ", "3:four "), lyrics(s, 1));
			assertEquals(List.of("3:<A long line"), lyrics(s, 0));
			// Tested in Lotro: it refuses the part
			assertThrows(LotroFileParseException.class, () -> ConversionDump
					.convert(tune("semantic", "c d e f|", "w:one two", "+:three four"), Profile.ABC_PLAYER_STRICT));
		}

		@Test
		void keyFieldTakesClefAndTransposition() throws Exception {
			// The clef and middle= change only the print; K:none has no key signature; HP / Hp play like D (ABC 2.1)
			for (String key : List.of("K:C clef=bass", "K:C treble", "K:C bass middle=d", "K:none", "K:", "K:C stafflines=5"))
				assertEquals(List.of(65, 67), pitches(key, "f g|"), key);
			assertEquals(List.of(66, 61, 67), pitches("K:HP", "f c g|"));
			assertEquals(List.of(66, 61, 67), pitches("K:Hp", "f c g|"));
			// transpose=, octave= and a clef with +8 or -8 change what's played
			assertEquals(List.of(62), pitches("K:C transpose=2", "c|"));
			assertEquals(List.of(48), pitches("K:C octave=-1", "c|"));
			assertEquals(List.of(48), pitches("K:C treble-8", "c|"));
			assertEquals(List.of(67), pitches("K:G clef=bass t=-5 octave=1", "c|"));
			// A mode may follow the key, also after a space
			assertEquals(List.of(66, 72), pitches("K:D mix clef=treble", "f c'|"));
			// Tested in Lotro: it refuses anything more than the key and its mode
			for (String key : List.of("K:C clef=bass", "K:C treble", "K:none", "K:G transpose=2"))
				assertThrows(LotroFileParseException.class,
						() -> ConversionDump.convert(tune("semantic", header(key), "c|"), Profile.ABC_PLAYER_STRICT), key);
			// Tested in Lotro (B38): an empty K:, in the header or the tune, plays nothing
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", header("K:"), "c|"), Profile.ABC_PLAYER_STRICT));
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", header("K:G"), "f|", "K:", "f|"), Profile.ABC_PLAYER_STRICT));
			ConversionDump.convert(tune("semantic", header("K:D mix"), "c|"), Profile.ABC_PLAYER_STRICT);
			// Unknown words and explicit accidentals (not supported yet) are errors. Tested in Lotro (B23, B38): it
			// refuses them too
			for (String key : List.of("K:C foo", "K:G ^c", "K:C exp ^f")) {
				assertThrows(FileParseException.class, () -> convert(tune("semantic", header(key), "c|")), key);
				LotroFileParseException e = assertThrows(LotroFileParseException.class,
						() -> ConversionDump.convert(tune("semantic", header(key), "c|"), Profile.ABC_PLAYER_STRICT), key);
				assertTrue(e.getMessage().contains("\"" + key.substring(key.indexOf(' ') + 1) + "\" in K:"), e.getMessage());
			}
		}

		@Test
		void laterKeyFieldChangesOnlyWhatItNames() throws Exception {
			// A clef never changes the key signature: a K: with only a clef or transposition keeps the key
			for (String change : List.of("[K:clef=bass]", "[K:bass]", "[K:treble middle=B]"))
				assertEquals(List.of(66, 66), pitches("K:G", "f " + change + " f|"), change);
			assertEquals(List.of(66, 54), pitches("K:G", "f [K:octave=-1] f|"));
			// An empty K: or K:none has no key signature
			assertEquals(List.of(66, 65), pitches("K:G", "f [K:none] f|"));
			assertEquals(List.of(66, 65), pitches("K:G", "f|", "K:", "f|"));
			// Clef, transpose= and octave= each stay until a K: names them again
			assertEquals(List.of(54, 54), pitches("K:G octave=-1", "f [K:D] f|"));
			assertEquals(List.of(54, 66), pitches("K:G treble-8", "f [K:treble] f|"));
			assertEquals(List.of(54, 66), pitches("K:G treble-8", "f [K:octave=1] f|"));
			assertEquals(List.of(68, 68), pitches("K:G transpose=2", "f [K:clef=bass] f|"));
		}

		private List<Integer> pitches(String key, String... body) throws Exception {
			return noteOns(convert(tune("semantic", header(key), body))).stream().map(NoteEvent::pitch).toList();
		}

		@Test
		void symbolLinesAreSkipped() throws Exception {
			// s: lines hold decorations for the notes above, like w: for lyrics. Tested in Lotro: it plays on.
			for (Profile profile : Profile.values()) {
				Sequence s = ConversionDump.convert(tune("semantic", "c d e f|", "s:!f! * * *"), profile);
				assertEquals(4, noteOns(s).size(), profile.toString());
			}
		}

		@Test
		void lyricsDontChangeTheNotes() throws Exception {
			// w: lines are lyrics under the notes above them, W: lines are lyrics after the tune
			Sequence withLyrics = convert(tune("semantic", header(), "c d-|", "w: la la~la", "d e|", "w:la_ la", "W:1. La la la"));
			Sequence without = convert(tune("semantic", header(), "c d-|", "d e|"));
			assertEquals(noteEvents(without), noteEvents(withLyrics));
		}

		@Test
		void verseLinesKeepTheirOrder() throws Exception {
			// On an equal tick Maestro's MidiText's order of lyric lines is not defined, so each W: line gets a tick of its own
			Sequence s = convert(tune("semantic", header(), "W:b", "W:a", "c d|", "W:d", "W:c"));
			List<Long> ticks = new ArrayList<>();
			List<String> lines = new ArrayList<>();
			for (int i = 0; i < s.getTracks()[0].size(); i++) {
				MidiEvent e = s.getTracks()[0].get(i);
				if (e.getMessage() instanceof javax.sound.midi.MetaMessage mm && mm.getType() == MidiConstants.META_LYRIC
						&& mm.getData()[0] == '<') {
					ticks.add(e.getTick());
					lines.add(new String(mm.getData(), java.nio.charset.StandardCharsets.UTF_8));
				}
			}
			assertEquals(List.of("<b", "<a", "<d", "<c"), lines);
			assertEquals(ticks.stream().distinct().sorted().toList(), ticks, "one tick per line, in file order");
		}

		@Test
		void textEscapesAreDecoded() throws Exception {
			// ABC 2.1 text strings in lyrics: mnemonics, HTML entities and unicode. \~ is a tilde (not a space), \\ a
			// backslash before the - that splits a word
			assertEquals(List.of("0:N\u00f3s ", "1:\u00f1u ", "2:a\\", "3:b "),
					lyrics(convert(tune("semantic", "c d e f|", "w:N\\'os \\~nu a\\\\-b")), 1));
		}

		@Test
		void syntaxLotroPlaysOnChangesNoNotes() throws Exception {
			// Tested in Lotro: chord symbols, grace notes, ~ . , endings, V: and a T: in the body all play
			List<NoteEvent> plain = noteEvents(convert(tune("semantic", "c d e f g a b c'|")));
			for (String body : List.of("\"C\"c d e f \"G7\"g a b c'|", "!f!c d e f !fermata!g a b c'|",
					"~c d .e f g a b c'|", "[1 c d e f :|[2 g a b c'|]", "|: c d e f |1 g a b c' :|2 |]", "c d e f [|g a b c'|"))
				assertEquals(plain, noteEvents(convert(tune("semantic", body))), body);
			assertEquals(plain, noteEvents(convert(tune("semantic", "c d e f|", "T:Second section", "V:1 treble",
					"g a b c'|"))));
		}

		@Test
		void graceNotesTakeBrokenRhythmAndNothingButNotes() throws Exception {
			// {g>a}: legal in ABC 2.1 (4.12). g gets 3/2 and a 1/2 of the written length, so g lasts 3 times a
			Sequence s = convert(tune("semantic", "{g>a}c4|"));
			double shortest = s.getResolution() * 2 * AbcToMidi.GRACE_NOTE_SECONDS; // a, at Q:120
			assertEquals(List.of(on(0, 67), on(Math.round(3 * shortest), 69), on(Math.round(4 * shortest), 60)),
					noteOns(s));
			// Spaces and a tie to the note change nothing
			assertEquals(noteOns(convert(tune("semantic", "{ga}c4|"))), noteOns(convert(tune("semantic", "{g a-}c4|"))));
			// Nor does a slur over them (Village Music Project), also with the slur going on out of the braces; with
			// Lotro errors it's an error (untested in Lotro)
			assertEquals(noteEvents(convert(tune("semantic", "{gfga}c4|"))),
					noteEvents(convert(tune("semantic", "{(gf)(ga)}c4|"))));
			assertEquals(noteEvents(convert(tune("semantic", "{/ga}c4 d|"))),
					noteEvents(convert(tune("semantic", "{/(ga}c4) d|"))));
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "{(ga)}c4|"), Profile.ABC_PLAYER_STRICT));
			// Anything else in the braces is an error, in every mode (Z, a rest, a sign, nothing)
			for (String body : List.of("{Z}c|", "{z}c|", "{x}c|", "{}c|", "{/}c|", "{g!}c|", "{H}c|", "{g>}c|")) {
				for (Profile profile : Profile.values()) {
					assertThrows(FileParseException.class,
							() -> ConversionDump.convert(tune("semantic", body), profile), body + " " + profile);
				}
			}
		}

		@Test
		void graceNotesArePlayedBeforeTheNote() throws Exception {
			// {g}c : on the beat, GRACE_NOTE_SECONDS long whatever the note's length; the note starts after it
			Sequence s = convert(tune("semantic", "{g}c d|"));
			long q = s.getResolution(); // Q:120, so a quarter note is 0.5 s
			double graceTicks = q * 2 * AbcToMidi.GRACE_NOTE_SECONDS;
			long g = Math.round(graceTicks);
			assertEquals(List.of(on(0, 67), off(g, 67), on(g, 60), off(q / 2, 60), on(q / 2, 62), off(q, 62)),
					noteEvents(s));
			assertEquals(List.of(on(0, 67), on(g, 60)), noteOns(convert(tune("semantic", "{g}c8|"))));
			assertEquals(List.of(on(0, 67), on(g, 60)), noteOns(convert(tune("semantic", "{/g}c|"))));
			// Several: the shortest lasts GRACE_NOTE_SECONDS, the others by their written lengths
			assertEquals(List.of(on(0, 67), on(g, 65), on(Math.round(2 * graceTicks), 60)),
					noteOns(convert(tune("semantic", "{gf}c2|"))));
			assertEquals(List.of(on(0, 67), on(Math.round(2 * graceTicks), 69), on(Math.round(3 * graceTicks), 60)),
					noteOns(convert(tune("semantic", "{g2a}c4|"))));
			// Never more than half the note
			assertEquals(List.of(on(0, 67), on(q / 16, 60), on(q / 8, 62)), noteOns(convert(tune("semantic", "{g}c/4 d|"))));
			// Before a chord they come before all of it; before a rest they aren't played
			assertEquals(List.of(on(0, 67), on(g, 60), on(g, 64)), noteOns(convert(tune("semantic", "{g}[ce]|"))));
			assertEquals(List.of(on(q, 60)), noteOns(convert(tune("semantic", "{g}z2 c|"))));
			// A grace note's accidental isn't kept for the notes after it
			assertEquals(List.of(on(0, 66), on(g, 65)), noteOns(convert(tune("semantic", "{^f}f|"))));
			// Tested in Lotro: it plays on without them, so with Lotro errors they're not played
			assertEquals(noteEvents(ConversionDump.convert(tune("semantic", "c d e f|"), Profile.ABC_PLAYER_STRICT)),
					noteEvents(ConversionDump.convert(tune("semantic", "{g}c d e f|"), Profile.ABC_PLAYER_STRICT)));
		}

		@Test
		void ornamentsArePlayed() throws Exception {
			// In steps of GRACE_NOTE_SECONDS from the note's start, folk style (starting on the note); the note itself
			// sounds after them. Q:120, L:1/8: an eighth is q/2.
			long q = convert(tune("semantic", "c|")).getResolution();
			double g = q * 2 * AbcToMidi.GRACE_NOTE_SECONDS;
			// Trill: note, upper, note, upper ... for the whole note (a half note is 15 steps: 14 quick ones, then c)
			List<NoteEvent> trill = new ArrayList<>();
			for (int k = 0; k < 14; k++)
				trill.add(on(Math.round(k * g), (k % 2 == 0) ? 60 : 62));
			trill.add(on(Math.round(14 * g), 60));
			trill.add(on(2 * q, 62));
			for (String body : List.of("Tc4 d|", "!trill!c4 d|", "+trill+c4 d|"))
				assertEquals(trill, noteOns(convert(tune("semantic", body))), body);
			// Mordents and turns
			assertEquals(List.of(on(0, 60), on(Math.round(g), 59), on(Math.round(2 * g), 60)),
					noteOns(convert(tune("semantic", "Mc2|"))));
			assertEquals(List.of(on(0, 60), on(Math.round(g), 62), on(Math.round(2 * g), 60)),
					noteOns(convert(tune("semantic", "!pralltriller!c2|"))));
			assertEquals(List.of(on(0, 62), on(Math.round(g), 60), on(Math.round(2 * g), 59), on(Math.round(3 * g), 60)),
					noteOns(convert(tune("semantic", "!turn!c2|"))));
			// Irish roll: the note in three parts, a cut (above) starts the second, a tap (below) the third
			assertEquals(List.of(on(0, 60), on(q / 2, 62), on(Math.round(q / 2.0 + g), 60), on(q, 59),
					on(Math.round(q + g), 60)), noteOns(convert(tune("semantic", "~c3|"))));
			// The neighbours follow the key and the bar's accidentals: above B in K:F is c, below c after _B is _B
			assertEquals(List.of(on(0, 58), on(Math.round(g), 60), on(Math.round(2 * g), 58)),
					noteOns(convert(tune("semantic", header("K:F"), "PB2|"))));
			assertEquals(List.of(on(0, 58), on(q / 2, 60), on(Math.round(q / 2.0 + g), 58), on(Math.round(q / 2.0 + 2 * g), 60)),
					noteOns(convert(tune("semantic", "_B Mc2|"))));
			// Not on a chord or a rest, and left out when the note is too short for it
			assertEquals(List.of(on(0, 60), on(0, 64)), noteOns(convert(tune("semantic", "T[ce]2|"))));
			assertEquals(List.of(on(q / 2, 60)), noteOns(convert(tune("semantic", "Tz c|"))));
			assertEquals(List.of(on(0, 60), on(q / 8, 62)), noteOns(convert(tune("semantic", "Tc/4 d|"))));
			// The syllable goes where the note starts, with its ornament
			assertEquals(List.of("0:la ", "4:la "), lyrics(convert(tune("semantic", "Tc4 d|", "w:la la")), 1));
			// With Lotro errors: ~ plays the note plain, as in Lotro (tested)
			assertEquals(noteEvents(ConversionDump.convert(tune("semantic", "c3 d|"), Profile.ABC_PLAYER_STRICT)),
					noteEvents(ConversionDump.convert(tune("semantic", "~c3 d|"), Profile.ABC_PLAYER_STRICT)));
		}

		/** The program (MIDI patch) of each part's track, in track order. */
		private static List<Integer> programs(Sequence sequence) {
			List<Integer> programs = new ArrayList<>();
			for (int t = 1; t < sequence.getTracks().length; t++) {
				Track track = sequence.getTracks()[t];
				for (int i = 0; i < track.size(); i++) {
					if (track.get(i).getMessage() instanceof ShortMessage sm
							&& sm.getCommand() == ShortMessage.PROGRAM_CHANGE) {
						programs.add(sm.getData1());
						break;
					}
				}
			}
			return programs;
		}

		/** The first part's program with standard pitch; the fields go in its header (T:t and K:C unless given). */
		private int standardProgram(String... fields) throws Exception {
			return programs(convert(part(true, fields))).getFirst();
		}

		/** One part with the given header fields (T:t and K:C unless given), then c d. */
		private static AbcCase part(boolean standardPitch, String... fields) {
			List<String> lines = new ArrayList<>(List.of("X:1"));
			if (Arrays.stream(fields).noneMatch(f -> f.startsWith("T:")))
				lines.add("T:t");
			lines.addAll(List.of(fields));
			lines.addAll(List.of("M:4/4", "L:1/8", "Q:120"));
			if (Arrays.stream(fields).noneMatch(f -> f.startsWith("K:")))
				lines.add("K:C");
			lines.add("c d|");
			return AbcCase.of("semantic", lines.toArray(String[]::new)).with(p -> p.standardPitch = standardPitch);
		}

		@Test
		void standardAbcGetsAMidiProgramFromClues() throws Exception {
			// %%MIDI program N or I:MIDI program N (abc2midi); program C N is channel C's, the part's channel is its
			// %%MIDI channel, else 1
			assertEquals(73, standardProgram("%%MIDI program 73"));
			assertEquals(24, standardProgram("%%MIDI program 2 71"));
			assertEquals(71, standardProgram("%%MIDI program 1 71"));
			assertEquals(71, standardProgram("%%MIDI program 2 71", "%%MIDI channel 2"));
			assertEquals(22, standardProgram("I:MIDI program 22"));
			// K:HP, Highland pipes: bag pipe
			assertEquals(109, standardProgram("K:HP"));
			// An instrument's name in V: name=, G:, or a title after "for"; the first name wins
			assertEquals(40, standardProgram("V:1 clef=bass name=\"Violin\""));
			assertEquals(73, standardProgram("G:flute"));
			assertEquals(40, standardProgram("G:fiddle and flute"));
			assertEquals(66, standardProgram("G:tenor sax"));
			assertEquals(40, standardProgram("T:Reel for fiddle"));
			assertEquals(46, standardProgram("T:Air for the harp"));
			// Elsewhere in a title a name is no clue; no clue: Nylon Guitar, as Lute of Ages gave before
			assertEquals(24, standardProgram("T:The Flute Player"));
			assertEquals(24, standardProgram());
			// The tune's type in R: (the weakest clue): what usually plays it in sessions
			assertEquals(40, standardProgram("R:reel"));
			assertEquals(40, standardProgram("R:Strathspey"));
			assertEquals(40, standardProgram("R:sl\\\"angpolska")); // Norbeck: its ABC escape decoded
			assertEquals(73, standardProgram("R:jig"));
			assertEquals(73, standardProgram("R:Slip Jig"));
			assertEquals(73, standardProgram("R:slow air"));
			assertEquals(21, standardProgram("R:hornpipe"));
			assertEquals(21, standardProgram("R:Waltz"));
			assertEquals(40, standardProgram("R:polon\\\"as")); // The Swedish polonäs, a fiddle tune
			assertEquals(19, standardProgram("R:hymn"));
			assertEquals(24, standardProgram("R:song"));
			// An instrument's name beats it
			assertEquals(46, standardProgram("R:reel", "G:harp"));
			// %%MIDI beats the pipes, the pipes beat a name
			assertEquals(73, standardProgram("G:fiddle", "%%MIDI program 73", "K:HP"));
			assertEquals(109, standardProgram("G:fiddle", "K:HP"));
			// The file header's clues are every part's, a part's own clue wins
			Sequence s = convert(AbcCase.of("semantic", "G:flute", "M:4/4", "L:1/8", "Q:120", "K:C", "", "X:1", "T:a", "c|",
					"X:2", "T:b", "G:fiddle", "d|").with(p -> p.standardPitch = true));
			assertEquals(List.of(73, 40), programs(s));
			// A Lotro instrument that was set gives its own program
			assertEquals(71, standardProgram("G:fiddle", "%%made-for Basic Clarinet"));
			// Lotro files (no standard pitch): the program of the Lotro instrument, clues don't count
			assertEquals(List.of(24), programs(convert(part(false, "%%MIDI program 73", "G:flute"))));
			assertEquals(List.of(73), programs(convert(part(false, "T:Flute"))));
		}

		@Test
		void midiChannelTenIsDrums() throws Exception {
			// abc2midi: %%MIDI channel 10 is the drums, the notes General MIDI percussion (C,, bass drum, D,, snare)
			Sequence s = convert(AbcCase.of("semantic", "X:1", "T:t", "%%MIDI channel 10", "M:4/4", "L:1/8", "Q:120", "K:C",
					"C,, D,, TC,,2|").with(p -> p.standardPitch = true));
			List<Integer> channels = new ArrayList<>();
			List<Integer> pitches = new ArrayList<>();
			for (int i = 0; i < s.getTracks()[1].size(); i++) {
				if (s.getTracks()[1].get(i).getMessage() instanceof ShortMessage sm) {
					channels.add(sm.getChannel());
					if (sm.getCommand() == ShortMessage.NOTE_ON)
						pitches.add(sm.getData1());
				}
			}
			assertEquals(Set.of(9), new HashSet<>(channels), "every event on the drum channel");
			assertEquals(List.of(36, 38, 36), pitches, "no ornament on a drum");
			assertEquals(List.of(0), programs(s), "the standard kit");
			// Another kit with program 10 N or program N
			assertEquals(25, standardProgram("%%MIDI channel 10", "%%MIDI program 10 25"));
			// Without standard pitch (Lotro files) nothing changes
			s = convert(part(false, "%%MIDI channel 10"));
			assertTrue(((ShortMessage) s.getTracks()[1].get(0).getMessage()).getChannel() != 9);
		}

		@Test
		void accompanimentTakesBassprogAndChordprog() throws Exception {
			// %%MIDI bassprog N [octave=N] and chordprog N (abc2midi); the melody keeps its own program
			AbcCase song = AbcCase.of("semantic", "X:1", "T:t", "%%MIDI bassprog 45 octave=1", "%%MIDI chordprog 0", "M:4/4",
					"L:1/8", "Q:120", "K:C", "\"G\"c d e f|").with(p -> {
				p.standardPitch = true;
				p.chordAccompaniment = true;
			});
			assertEquals(List.of(24, 45, 0), programs(convert(song)));
			// Without them: Acoustic Bass and Nylon Guitar
			song = AbcCase.of("semantic", "X:1", "T:t", "M:4/4", "L:1/8", "Q:120", "K:C", "\"G\"c d e f|").with(p -> {
				p.standardPitch = true;
				p.chordAccompaniment = true;
			});
			assertEquals(List.of(24, 32, 24), programs(convert(song)));
		}

		@Test
		void midiVoiceSetsTheInstrument() throws Exception {
			// ABC 2.1 (11.2): %%MIDI voice [ID] instrument=N [bank=B], N counted from 1 (instrument=59 is the tuba,
			// program 58); it beats %%MIDI program
			assertEquals(58, standardProgram("%%MIDI voice instrument=59"));
			assertEquals(58, standardProgram("%%MIDI voice instrument=59 bank=1 % tuba"));
			assertEquals(58, standardProgram("%%MIDI program 73", "%%MIDI voice instrument=59"));
			// Only bank 1 (General MIDI): another keeps the guess. mute is ignored: the part keeps its notes
			assertEquals(73, standardProgram("G:flute", "%%MIDI voice instrument=59 bank=2"));
			assertEquals(58, standardProgram("%%MIDI voice instrument=59 mute"));
			// With an ID the voice with that ID, wherever it is written; without, the voice it is written for (the V:
			// above it in the header, or the voice of its body line)
			AbcCase header = AbcCase.of("semantic", "X:1", "T:t", "%%MIDI voice Tb instrument=59", "V:Vl name=\"Violin\"",
							"V:Tb", "%%MIDI voice instrument=33", "M:4/4", "L:1/8", "Q:120", "K:C", "V:Vl", "c d|", "V:Tb", "C D|")
					.with(p -> {
						p.standardPitch = true;
						p.standard2011 = true;
					});
			assertEquals(List.of(40, 32), programs(convert(header)));
			AbcCase body = AbcCase.of("semantic", "X:1", "T:t", "M:4/4", "L:1/8", "Q:120", "K:C", "V:1",
					"%%MIDI voice instrument=22", "c d|", "V:2", "C D|", "V:1", "%%MIDI voice 2 instrument=74", "e f|", "V:2",
					"E F|").with(p -> {
				p.standardPitch = true;
				p.standard2011 = true;
			});
			assertEquals(List.of(21, 73), programs(convert(body)));
		}

		/** A tune of standard ABC with the accompaniment (and so drones): X: T: M:4/4 L:1/8 Q:120, then the lines. */
		private static AbcCase accompanied(String... lines) {
			List<String> all = new ArrayList<>(List.of("X:1", "T:t", "M:4/4", "L:1/8", "Q:120"));
			all.addAll(List.of(lines));
			return AbcCase.of("semantic", all.toArray(String[]::new)).with(p -> {
				p.standardPitch = true;
				p.standard2011 = true;
				p.expandRepeats = true;
				p.chordAccompaniment = true;
			});
		}

		@Test
		void bagpipeDrones() throws Exception {
			// abc2midi's %%MIDI droneon and droneoff: a track after the parts, from where it is turned on to where it is
			// turned off. Without %%MIDI drone, the pipes' drone where Lotro's bagpipe plays it (Drone.
			// IN_LOTRO_BAGPIPE_RANGE): the tenor drone A3 on Bag Pipe, the bass drone A2 an octave up on it. It starts
			// more than 60 ms after a note that starts with it (merged into one bagpipe, notes that start together share
			// a velocity): at Q:120, just over 0.12 q
			Sequence s = convert(accompanied("K:C", "c d|", "%%MIDI droneon", "e f|", "%%MIDI droneoff", "g a|"));
			long q = s.getResolution();
			long gap = 120 * q * 60 / 60000 + 1;
			assertEquals(3, s.getTracks().length);
			assertEquals(List.of(on(q + gap, 57), off(2 * q, 57)), noteEvents(s, 2));
			assertEquals(List.of(24, 109), programs(s));
			// %%MIDI drone sets the program (from 0), the pitches and their velocities (0 keeps one); each pitch by whole
			// octaves into the bagpipe's range, C3 to C6 (D2 as D3, E2 as E3; F#6 as F#5, C7 as C6)
			s = convert(accompanied("%%MIDI drone 20 38 40 0 0", "%%MIDI droneon", "K:C", "c d|"));
			assertEquals(List.of(on(gap, 50), on(gap, 52), off(q, 50), off(q, 52)), noteEvents(s, 2));
			assertEquals(List.of(24, 20), programs(s));
			s = convert(accompanied("%%MIDI drone 0 90 96", "%%MIDI droneon", "K:C", "c d|"));
			assertEquals(List.of(on(gap, 78), on(gap, 84), off(q, 78), off(q, 84)), noteEvents(s, 2));
			// As often as a repeat plays it (inline [I:MIDI ...])
			s = convert(accompanied("K:C", "|: c [I:MIDI droneon] d [I:MIDI droneoff] :|"));
			assertEquals(List.of(on(q / 2 + gap, 57), off(q, 57), on(3 * q / 2 + gap, 57), off(2 * q, 57)),
					noteEvents(s, 2));
			// Past quick notes: after the last one that is too close
			s = convert(accompanied("K:HP", "c/8 d/8 e/8 f/8 g4|"));
			assertEquals(List.of(on(q / 4 + gap, 57), off(q / 4 + 2 * q, 57)), noteEvents(s, 2));
			// Without directives: Highland pipes, or a part set to Bag Pipe, have the pipes' drone on Bag Pipe, the whole
			// part
			List<NoteEvent> pipes = List.of(on(gap, 57), off(q, 57));
			s = convert(accompanied("K:HP", "c d|"));
			assertEquals(pipes, noteEvents(s, 2));
			assertEquals(List.of(109, 109), programs(s));
			assertEquals(pipes, noteEvents(convert(accompanied("%%MIDI program 109", "K:C", "c d|")), 2));
			assertEquals(pipes, noteEvents(convert(accompanied("%%MIDI voice instrument=110", "K:C", "c d|")), 2));
			// Only a program the file sets: a name is no sign
			assertEquals(2, convert(accompanied("G:pipes", "K:C", "c d|")).getTracks().length);
			// A drone directive decides: K:HP with droneoff has none
			assertEquals(2, convert(accompanied("%%MIDI droneoff", "K:HP", "c d|")).getTracks().length);
			// Two voices of pipes are two sets of pipes: a drone each
			s = convert(accompanied("K:HP", "V:1", "c d|", "V:2", "A, B,|"));
			assertEquals(5, s.getTracks().length);
			assertEquals(pipes, noteEvents(s, 3));
			assertEquals(pipes, noteEvents(s, 4));
			// Only with the accompaniment (Maestro, standard ABC); never in a file made for Lotro
			assertEquals(2, convert(accompanied("K:HP", "c d|").with(p -> p.chordAccompaniment = false)).getTracks().length);
			assertEquals(2, convert(accompanied("%%MIDI program 109", "%%MIDI droneon", "K:C", "c d|")
					.with(p -> p.standardPitch = false)).getTracks().length);
		}

		@Test
		void tieAfterAChordTiesEveryNote() throws Exception {
			// [ce]- (ABC 2.1, 4.11 and 4.17): c and e each tie to the next c and e, like [c-e-]
			Sequence s = convert(tune("semantic", "[ce]2- [ce]2 d2 z2|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), on(0, 64), off(2 * q, 60), off(2 * q, 64), on(2 * q, 62), off(3 * q, 62)),
					noteEvents(s));
			assertEquals(noteEvents(convert(tune("semantic", "[c-e-] [ce]|"))), noteEvents(convert(tune("semantic",
					"[ce]- [ce]|"))));
			// With a length and a broken rhythm after it (a broken rhythm after a chord: ABC 2.1, Params.standard2011)
			assertEquals(noteEvents(convert(tune("semantic", "[c2-e2-]>[ce] d|").with(p -> p.standard2011 = true))),
					noteEvents(convert(tune("semantic", "[ce]2->[ce] d|").with(p -> p.standard2011 = true))));
			// Tested in Lotro (B65): it refuses the part
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "[ce]- [ce]|"), Profile.ABC_PLAYER_STRICT));
		}

		@Test
		void sectionTitleNamesNothing() throws Exception {
			// A T: after the notes (a section title) must not rename the part, change its instrument or the song title
			AbcCase song = tune("semantic", header("T:Song - Harp"), "c d|", "T:Song - Flute section", "e f|");
			AbcInfo converted = abcInfoOf(song);
			AbcInfo playlist = AbcToMidi.parseAbcMetadata(song.filesData());
			for (AbcInfo info : List.of(converted, playlist)) {
				assertEquals("Song - Harp", info.getTitle());
				assertEquals(LotroInstrument.BASIC_HARP, info.getPartInstrument(1));
			}
		}

		@Test
		void spacerYIsALotroError() throws Exception {
			// Tested in Lotro: a part with y plays nothing
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "c d y e f|"), Profile.ABC_PLAYER_STRICT));
			assertEquals(noteEvents(convert(tune("semantic", "c d e f|"))), noteEvents(convert(tune("semantic", "c d y e f|"))));
		}

		@Test
		void decorationIsALotroError() throws Exception {
			// Tested in Lotro: from a !decoration! on, the part is silent, also on later lines and after +mf+
			for (String body : List.of("!f!c d e f|", "c d e f !trill!g a b c'|"))
				assertThrows(LotroFileParseException.class, () -> ConversionDump.convert(tune("semantic", body),
						Profile.ABC_PLAYER_STRICT), body);
			// Without Lotro errors the decorations that aren't ornaments are skipped and the notes play
			assertEquals(noteEvents(convert(tune("semantic", "c d e f g a b c'|"))),
					noteEvents(convert(tune("semantic", "!f!c d e f !fermata!g a b c'|"))));
		}

		@Test
		void sameNoteTwiceInAChordPlaysOnlyTheFirst() throws Exception {
			// Tested in Lotro: [c2c4], [c4c2] and [^c2_d4] each play only the first note, for its own length
			long q = convert(tune("semantic", "c|")).getResolution();
			assertEquals(List.of(on(0, 60), off(q, 60)), noteEvents(convert(tune("semantic", "[c2c] z2|"))));
			assertEquals(List.of(on(0, 60), off(q / 2, 60)), noteEvents(convert(tune("semantic", "[cc2] z2|"))));
			assertEquals(List.of(on(0, 61), off(q, 61)), noteEvents(convert(tune("semantic", "[^c2_d] z2|"))));
		}

		@Test
		void ignoredSameNoteDoesNotShortenTheChord() throws Exception {
			// Tested in Lotro: in [c4c2] d4 the d starts when the c4 ends; the ignored c2 doesn't end the chord
			Sequence s = convert(tune("semantic", "[c2c] d|"));
			long q = s.getResolution();
			assertEquals(List.of(on(0, 60), off(q, 60), on(q, 62), off(3 * q / 2, 62)), noteEvents(s));
		}

		@Test
		void pluckedNoteKeepsItsWrittenLength() throws Exception {
			// Harp is non-sustained. A long note isn't cut to the harp sample, a short one isn't lengthened to it.
			Sequence s = ConversionDump.convert(tune("semantic", header("T:Test Harp"), "c8 d|"), Profile.ABC_PLAYER);
			long q = s.getResolution(); // c8 = 8 eighths = 4 quarters
			assertEquals(List.of(on(0, 60), off(4 * q, 60), on(4 * q, 62), off(4 * q + q / 2, 62)), noteEvents(s));
		}

		@Test
		void lastPluckedNoteIsCutWhereItsSampleRunsOut() throws Exception {
			// c32 (8 s) is written longer than its harp sample, and nothing sounds after it. So the song ends where c's
			// sample runs out, and c's note-off is moved there. d/ ends before that and keeps its written length.
			Sequence s = ConversionDump.convert(tune("semantic", header("T:Test Harp"), "d/ c32|"), Profile.ABC_PLAYER);
			long q = s.getResolution(); // d/ = q/4, c32 = 16 quarters
			long cSoundEnd = q / 4 + harpSampleTicks(60, q);
			assertTrue(cSoundEnd < q / 4 + 16 * q, "the test needs c's sample < 8 s");
			assertEquals(List.of(on(0, 62), off(q / 4, 62), on(q / 4, 60), off(cSoundEnd, 60)), noteEvents(s));
			assertEquals(cSoundEnd, s.getTickLength());
		}

		@Test
		void withoutLotroInstrumentsTheSongEndsWithTheLastNote() throws Exception {
			Sequence s = convert(tune("semantic", header("T:Test Harp"), "c32 d/|"));
			long q = s.getResolution();
			assertEquals(16 * q + q / 4, s.getTickLength());
		}

		/** Ticks of the harp's sample for a note, at Q:120 (as AbcToMidi computes it). */
		private static long harpSampleTicks(int noteId, long q) {
			long micros;
			try {
				micros = LotroInstrumentSampleDuration.getDura(LotroInstrument.BASIC_HARP.friendlyName, noteId);
			} catch (Exception npe) {
				micros = AbcConstants.getNonSustainedNoteHoldMicros(LotroInstrument.BASIC_HARP);
			}
			return Math.round(micros * q / MidiUtils.convertTempo(120));
		}

		@Test
		void noteRangeIsOnlyCheckedWithLotroErrors() throws Exception {
			// C,,, and c''' are outside Lotro's C2..C5. Only the strict profile (Lotro errors on) may complain.
			AbcCase outOfRange = tune("semantic", "C,,, c'''|");
			assertEquals(List.of(12, 96), noteOns(ConversionDump.convert(outOfRange, Profile.ABC_PLAYER)).stream()
					.map(NoteEvent::pitch).toList());
			assertEquals(List.of(12, 96), noteOns(convert(outOfRange)).stream().map(NoteEvent::pitch).toList());
			assertThrows(LotroFileParseException.class, () -> ConversionDump.convert(outOfRange, Profile.ABC_PLAYER_STRICT));
		}

		@Test
		void playlistReadsTitlesLikeTheConversion() throws Exception {
			// parseAbcMetadata (the ABC Player's playlist) must handle % comments and \% like convert() does
			for (String title : List.of("T:100\\% Harp", "T:100% Harp", "T:Song % a comment")) {
				AbcCase abcCase = tune("semantic", header(title), "c|");
				AbcInfo converted = new AbcInfo();
				ConversionDump.run(abcCase, Profile.MAESTRO_LEGACY, false, converted);
				assertEquals(converted.getTitle(), AbcToMidi.parseAbcMetadata(abcCase.filesData()).getTitle(), title);
			}
		}

		@Test
		void largeLengthNumbersGiveTheSameResultAsSmallOnes() throws Exception {
			// The same music twice: L:1/2834674 with c1417337 is L:1/2 with c. Used to overflow int with >>>.
			Sequence large = convert(tune("semantic", header("L:1/2834674"), "c1417337>>>d1417337 e1417337|"));
			Sequence small = convert(tune("semantic", header("L:1/2"), "c>>>d e|"));
			assertEquals(noteEvents(small), noteEvents(large));
		}

		@Test
		void largeLDenominatorWithFastTempo() throws Exception {
			// Q:1000 with L:1/2834674 used to overflow int in the Lotro length check (it reported -0.932 s)
			Sequence large = ConversionDump.convert(tune("semantic", header("L:1/2834674", "Q:1000"), "c5669348 d5669348|"),
					Profile.ABC_PLAYER_STRICT);
			Sequence small = ConversionDump.convert(tune("semantic", header("L:1/2", "Q:1000"), "c4 d4|"),
					Profile.ABC_PLAYER_STRICT);
			assertEquals(noteEvents(small), noteEvents(large));
		}

		@Test
		void escapedPercentIsKeptInTitle() throws Exception {
			AbcInfo info = new AbcInfo();
			ConversionDump.run(tune("semantic", header("T:100\\% Harp"), "c|"), Profile.MAESTRO_LEGACY, false, info);
			assertEquals("100% Harp", info.getTitle());
			assertEquals(com.digero.common.abc.LotroInstrument.BASIC_HARP, info.getPartInstrument(1));
		}

		@Test
		void lotroLengthLimitUsesTheWrittenLength() throws Exception {
			// Tested in Lotro: (3c/4d/4e/4 plays, although each note lasts only 0.042 s. Lotro checks the written
			// c/4 (0.0625 s), not the length after the tuplet.
			ConversionDump.convert(tune("semantic", "(3c/4d/4e/4 c|"), Profile.ABC_PLAYER_STRICT);
		}

		@Test
		void partWithoutLUsesTheDefaultNoteLength() throws Exception {
			// Tested in Lotro: c64 in a part without L: fails (16 s at the default L:1/8), both after a part with
			// L:1/64 and with L:1/64 before the first X:. It would be 2 s with L:1/64.
			Sequence afterPart = convert(AbcCase.of("semantic", "X:1", "T:One", "M:4/4", "Q:120", "L:1/64", "K:C", "c64|",
					"X:2", "T:Two", "M:4/4", "Q:120", "K:C", "c64|"));
			Sequence afterFileHeader = convert(AbcCase.of("semantic", "L:1/64", "X:1", "T:One", "M:4/4", "Q:120", "K:C",
					"c64|"));
			long q = afterPart.getResolution(); // c64 at L:1/8 = 8 whole notes = 32 quarters
			assertEquals(List.of(on(0, 60), off(32 * q, 60)), noteEvents(afterPart, 2));
			assertEquals(List.of(on(0, 60), off(32 * q, 60)), noteEvents(afterFileHeader, 1));
		}

		@Test
		void partWithoutLOrMUsesTheFileHeaderNotThePreviousPart() throws Exception {
			// Tested in Lotro. L:1/64 before the first X: applies to a part without M: and L: (c64 = 2 s, not 16 s)
			Sequence length = convert(AbcCase.of("semantic", "M:4/4", "L:1/64", "X:1", "T:One", "M:4/4", "Q:120", "L:1/8",
					"K:C", "c64|", "X:2", "T:Two", "Q:120", "K:C", "c64|"));
			long q = length.getResolution(); // c64 at L:1/64 = 1 whole note = 4 quarters
			assertEquals(List.of(on(0, 60), off(4 * q, 60)), noteEvents(length, 2));
			// M:2/4 before the first X: applies to a part without M:, so its default length is 1/16 (c40 = 5 s)
			Sequence fromFile = convert(AbcCase.of("semantic", "M:2/4", "X:1", "T:One", "M:4/4", "Q:120", "K:C", "c40|",
					"X:2", "T:Two", "Q:120", "K:C", "c40|"));
			assertEquals(List.of(on(0, 60), off(10 * q, 60)), noteEvents(fromFile, 2));
			// ... but the previous part's M:2/4 doesn't carry over: default M:4/4, length 1/8 (c40 = 10 s)
			Sequence fromPart = convert(AbcCase.of("semantic", "X:1", "T:One", "M:2/4", "Q:120", "K:C", "c40|",
					"X:2", "T:Two", "Q:120", "K:C", "c40|"));
			assertEquals(List.of(on(0, 60), off(20 * q, 60)), noteEvents(fromPart, 2));
		}

		@Test
		void partStartsFromTheFileHeader() throws Exception {
			// File header K:D; part 2 sets K:C; part 3 has no K: and must be back in D, not C
			Sequence s = convert(AbcCase.of("semantic", "K:D", "X:1", "T:One", "f|", "X:2", "T:Two", "K:C", "f|",
					"X:3", "T:Three", "f|"));
			assertEquals(66, noteOns(s, 3).get(0).pitch());
		}

		@Test
		void ringOutFollowsATempoChange() throws Exception {
			// d/ ends (as written) after 0.125 s; there the tempo drops to 60, so the rest of its sample takes twice as
			// many ticks per second as before
			Sequence s = ConversionDump.convert(tune("semantic", header("T:Test Harp"), "c32 d/|", "%%Q: 60"),
					Profile.ABC_PLAYER);
			long q = s.getResolution();
			double dSampleSeconds = LotroInstrumentSampleDuration.getDura(LotroInstrument.BASIC_HARP.friendlyName, 62)
					/ 1_000_000.0;
			assertTrue(dSampleSeconds > 0.125, "the test needs d's sample > 0.125 s");
			// Q:60 -> one quarter (q ticks) per second
			assertEquals(16 * q + q / 4 + Math.round((dSampleSeconds - 0.125) * q), s.getTickLength());
		}

		@Test
		void abcInfoHasTheSongLengthIncludingTheRingOut() throws Exception {
			AbcInfo info = new AbcInfo();
			AbcToMidi.Params params = new AbcToMidi.Params(tune("semantic", header("T:Test Harp"), "c32 d/|").filesData());
			params.abcInfo = info;
			Sequence s = AbcToMidi.convert(params); // Default params: Lotro instruments on
			assertEquals(s.getMicrosecondLength(), info.getSongLengthMicros());
			// c32 is 8 s at Q:120; the song ends where d's sample runs out (1 tick is about 43 microseconds here)
			long dSampleMicros = LotroInstrumentSampleDuration.getDura(LotroInstrument.BASIC_HARP.friendlyName, 62);
			assertTrue(Math.abs(info.getSongLengthMicros() - (8_000_000 + dSampleMicros)) <= 50,
					"song length " + info.getSongLengthMicros());
		}

		// ------------------------------------------------------------ extended %% fields

		/** Converts with PLAIN_MIDI and returns the AbcInfo that convert() filled in. */
		private static AbcInfo abcInfoOf(AbcCase abcCase) throws Exception {
			AbcToMidi.Params params = new AbcToMidi.Params(abcCase.filesData());
			Profile.MAESTRO_LEGACY.applyTo(params);
			abcCase.tweak().accept(params);
			params.abcInfo = new AbcInfo();
			AbcToMidi.convert(params);
			return params.abcInfo;
		}

		@Test
		void extendedTempoChangesTheTicksButNotTheRealTime() throws Exception {
			// Notes after %%Q: 60 are scaled by 60/120 in ticks, and the tempo event halves the ticks per second
			Sequence changed = convert(tune("semantic", "c d|", "%%Q: 60", "e f|"));
			Sequence plain = convert(tune("semantic", "c d e f|"));
			long q = changed.getResolution();
			assertEquals(List.of(on(0, 60), on(q / 2, 62), on(q, 64), on(q + q / 4, 65)), noteOns(changed));
			assertEquals(plain.getMicrosecondLength(), changed.getMicrosecondLength());
		}

		@Test
		void extendedFieldNamesIgnoreCase() throws Exception {
			assertEquals("Loud Title", abcInfoOf(tune("semantic", AbcCases.extended("%%SONG-TITLE Loud Title"), "c|"))
					.getTitle());
			assertEquals(noteEvents(convert(tune("semantic", "c d|", "%%Q: 60", "e f|"))),
					noteEvents(convert(tune("semantic", "c d|", "%%q: 60", "e f|"))));
		}

		@Test
		void partNameSetsTheNameInstrumentAndPan() throws Exception {
			AbcInfo left = abcInfoOf(tune("semantic", AbcCases.extended("%%part-name Lead Harp left"), "c|"));
			assertEquals("Lead Harp left", left.getPartName(1));
			assertEquals(LotroInstrument.BASIC_HARP, left.getPartInstrument(1));
			assertEquals(14, left.getUserPan(1)); // 0 + 14 and 127 - 13: kept for old songs
			AbcInfo right = abcInfoOf(tune("semantic", AbcCases.extended("%%part-name Lute right"), "c|"));
			assertEquals(114, right.getUserPan(1));
		}

		@Test
		void partNameWinsOverTheTitleInEitherOrder() throws Exception {
			AbcInfo titleFirst = abcInfoOf(tune("semantic", header("T:Test Flute"), "%%part-name Harp", "c|"));
			assertEquals("Harp", titleFirst.getPartName(1));
			assertEquals(LotroInstrument.BASIC_HARP, titleFirst.getPartInstrument(1));
			AbcInfo partNameFirst = abcInfoOf(AbcCase.of("semantic", "X:1", "%%part-name Harp", "T:Test Flute", "K:C",
					"c|"));
			assertEquals("Harp", partNameFirst.getPartName(1));
			assertEquals(LotroInstrument.BASIC_HARP, partNameFirst.getPartInstrument(1));
		}

		@Test
		void headerTrackNameWorksWhenPartNameIsShorterThanTheTitle() throws Exception {
			// Track 0 gets the tune's title, here "Harp" from %%part-name, while the T: prefix is "Test Flute".
			// getPartName(0) used to cut the prefix off anyway: StringIndexOutOfBoundsException.
			AbcInfo info = abcInfoOf(AbcCase.of("semantic", "X:1", "%%part-name Harp", "T:Test Flute", "K:C", "c|"));
			assertEquals("Harp", info.getPartName(0));
			// An empty %%part-name and no T: either: the file's name, as for a part without a T:
			AbcInfo empty = abcInfoOf(tune("semantic", AbcCases.extended("%%part-name"), "c|"));
			assertEquals("semantic", empty.getPartName(0));
		}

		@Test
		void playlistPicksTheSameInstrumentAsTheConversion() throws Exception {
			// The rule: %%made-for wins, then %%part-name, then the first T: that names an instrument, else the default.
			// The playlist (parseAbcMetadata) must follow it too, and both must say whether it came from %%made-for.
			record Expect(AbcCase abcCase, LotroInstrument instrument, boolean madeFor) {
			}
			List<Expect> expectations = List.of(
					new Expect(tune("title", header("T:Test Harp"), "c|"), LotroInstrument.BASIC_HARP, false),
					new Expect(tune("no_instrument", "c|"), LotroInstrument.DEFAULT_INSTRUMENT, false),
					new Expect(tune("part_name", AbcCases.extended("%%part-name Harp"), "c|"),
							LotroInstrument.BASIC_HARP, false),
					new Expect(tune("made_for", AbcCases.extended("%%made-for Basic Flute"), "c|"),
							LotroInstrument.BASIC_FLUTE, true),
					new Expect(tune("made_for_then_part_name",
							AbcCases.extended("%%made-for Basic Flute", "%%part-name Harp"), "c|"),
							LotroInstrument.BASIC_FLUTE, true),
					new Expect(tune("part_name_then_made_for",
							AbcCases.extended("%%part-name Harp", "%%made-for Basic Flute"), "c|"),
							LotroInstrument.BASIC_FLUTE, true),
					new Expect(tune("title_then_made_for", header("T:Test Harp"), "%%made-for Basic Flute", "c|"),
							LotroInstrument.BASIC_FLUTE, true),
					new Expect(tune("title_then_part_name", header("T:Test Flute"), "%%part-name Harp", "c|"),
							LotroInstrument.BASIC_HARP, false),
					new Expect(AbcCase.of("part_name_then_title", "X:1", "%%part-name Harp", "T:Test Flute", "K:C", "c|"),
							LotroInstrument.BASIC_HARP, false),
					new Expect(AbcCase.of("two_titles", "X:1", "T:Test Harp", "T:Test Flute", "K:C", "c|"),
							LotroInstrument.BASIC_HARP, false),
					new Expect(AbcCase.of("made_for_then_title", "X:1", "%%made-for Basic Horn", "T:Test Harp", "K:C",
							"c|"), LotroInstrument.BASIC_HORN, true));

			for (Expect e : expectations) {
				String what = e.abcCase().name();
				AbcInfo converted = abcInfoOf(e.abcCase());
				AbcInfo playlist = AbcToMidi.parseAbcMetadata(e.abcCase().filesData());
				assertEquals(e.instrument(), converted.getPartInstrument(1), what + ": conversion");
				assertEquals(e.instrument(), playlist.getPartInstrument(1), what + ": playlist");
				assertEquals(e.madeFor(), converted.getPartInstrumentFromMadeFor(1), what + ": conversion made-for");
				assertEquals(e.madeFor(), playlist.getPartInstrumentFromMadeFor(1), what + ": playlist made-for");
			}
		}

		@Test
		void playlistPicksTheInstrumentOfEveryPart() throws Exception {
			// Each X: starts over: part 2's T: must not be blocked by part 1's %%made-for
			AbcCase song = AbcCase.of("semantic", AbcCase.concat(
					AbcCases.part(1, "Song - One", "%%made-for Basic Horn", "c|"),
					AbcCases.part(2, "Song - Harp", "c|"),
					AbcCases.part(3, "Song - Three", "c|")));
			AbcInfo converted = abcInfoOf(song);
			AbcInfo playlist = AbcToMidi.parseAbcMetadata(song.filesData());
			for (int part = 1; part <= 3; part++) {
				assertEquals(converted.getPartInstrument(part), playlist.getPartInstrument(part), "part " + part);
				assertEquals(converted.getPartInstrumentFromMadeFor(part), playlist.getPartInstrumentFromMadeFor(part),
						"part " + part);
			}
			assertEquals(List.of(LotroInstrument.BASIC_HORN, LotroInstrument.BASIC_HARP,
					LotroInstrument.DEFAULT_INSTRUMENT), List.of(playlist.getPartInstrument(1),
					playlist.getPartInstrument(2), playlist.getPartInstrument(3)));
		}

		@Test
		void madeForWinsOverPartNameInEitherOrder() throws Exception {
			for (String[] fields : List.of(new String[] { "%%made-for Basic Flute", "%%part-name Harp" },
					new String[] { "%%part-name Harp", "%%made-for Basic Flute" })) {
				AbcInfo info = abcInfoOf(tune("semantic", AbcCases.extended(fields), "c|"));
				assertEquals(LotroInstrument.BASIC_FLUTE, info.getPartInstrument(1), String.join(", ", fields));
				assertEquals("Harp", info.getPartName(1), String.join(", ", fields));
			}
		}

		@Test
		void userPanIsClampedAndAutoOrGarbageMeansNone() throws Exception {
			assertEquals(30, userPan("%%user-pan 30"));
			assertEquals(127, userPan("%%user-pan 200"));
			assertEquals(0, userPan("%%user-pan -5"));
			assertEquals(null, userPan("%%user-pan abc"));
			assertEquals(null, userPan("%%user-pan 64.5"));
			assertEquals(null, userPan("%%part-name Lute left", "%%user-pan auto"));
		}

		@Test
		void userPanWinsOverThePanFromPartName() throws Exception {
			assertEquals(100, userPan("%%user-pan 100", "%%part-name Lute left"));
		}

		private static Integer userPan(String... fields) throws Exception {
			return abcInfoOf(tune("semantic", AbcCases.extended(fields), "c|")).getUserPan(1);
		}

		@Test
		void swingRhythmOverridesTheTripletGuess() throws Exception {
			assertEquals(true, abcInfoOf(tune("semantic", AbcCases.extended("%%swing-rhythm true"), "c d e f|"))
					.hasTriplets());
			assertEquals(false, abcInfoOf(tune("semantic", AbcCases.extended("%%swing-rhythm false"), "(3cde f|"))
					.hasTriplets());
		}

		@Test
		void oneTripletAmongManyRegularNotesIsNotATripletSong() throws Exception {
			// hasTriplets makes Maestro export with a triplet grid, which is worse for regular notes
			String regular = "c d e f g a b c' ".repeat(10) + "|"; // 80 regular notes
			assertEquals(false, abcInfoOf(tune("semantic", "(3cde " + regular)).hasTriplets());
			// ... but a song that is mostly triplets is
			assertEquals(true, abcInfoOf(tune("semantic", "(3cde (3cde (3cde c d|")).hasTriplets());
		}

		@Test
		void tripletGuessDoesNotDependOnTheTempo() throws Exception {
			// The guess looked for a 3 in the note length after the tempo scaling, so Q:90 and Q:120 hid it
			for (String tempo : List.of("Q:90", "Q:100", "Q:120", "Q:125"))
				assertEquals(true, abcInfoOf(tune("semantic", header(tempo), "(3cde f|")).hasTriplets(), tempo);
			// ... and a tempo change to 100 in a Q:120 tune looked like a triplet (100/120 = 5/6)
			assertEquals(false, abcInfoOf(tune("semantic", "c d|", "%%Q: 100", "e f|")).hasTriplets());
		}

		@Test
		void mixTimingsAndSongDurationAreRead() throws Exception {
			AbcInfo info = abcInfoOf(
					tune("semantic", AbcCases.extended("%%mix-timings true", "%%song-duration 3:14"), "c|"));
			assertEquals(true, info.hasMixTimings());
			assertEquals("3:14", info.getSongDurationStr());
		}

		@Test
		void badOrganicVersionDoesNotStopTheSong() throws Exception {
			// Used to throw NumberFormatException out of convert(), which isn't a FileParseException
			AbcInfo info = abcInfoOf(tune("semantic", AbcCases.extended("%%organic-version two"), "c|"));
			assertEquals(false, info.isOrganicV2());
			assertEquals(true, abcInfoOf(tune("semantic", AbcCases.extended("%%organic-version 2"), "c|"))
					.isOrganicV2());
		}
	}
}