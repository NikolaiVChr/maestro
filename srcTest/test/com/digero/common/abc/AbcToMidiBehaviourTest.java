package com.digero.common.abc;

import static com.digero.common.abc.AbcCases.header;
import static com.digero.common.abc.AbcCases.tune;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;

import javax.sound.midi.*;

import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcRegion;
import com.digero.common.abctomidi.AbcToMidi;
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
	 * Cases that deliberately use notes outside LotRO's range C2..C5, to test the range checks. Every other case
	 * must stay inside it, so that the strict profile doesn't fail early on a note that has nothing to do with what
	 * the case tests.
	 */
	static final Set<String> OUT_OF_RANGE_CASES = Set.of("notes_octaves_extreme", "lotro_note_too_low",
			"lotro_note_too_high");

	static Stream<AbcCase> casesInRange() {
		return AbcCases.all().stream().filter(c -> !OUT_OF_RANGE_CASES.contains(c.name()));
	}

	static Stream<AbcCase> casesWithLotroAndPlainMidi() {
		return AbcCases.all().stream()
				.filter(c -> c.profiles().contains(Profile.LOTRO) && c.profiles().contains(Profile.PLAIN_MIDI));
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

		/**
		 * The LotRO profile plays the same notes at the same times as plain MIDI. The only difference allowed: a plucked
		 * note may end earlier (cut where its sample runs out), never later (it isn't lengthened to its sample).
		 */
		@ParameterizedTest(name = "{0}")
		@MethodSource("com.digero.common.abc.AbcToMidiBehaviourTest#casesWithLotroAndPlainMidi")
		void lotroInstrumentsOnlyCutPluckedNotes(AbcCase abcCase) throws Exception {
			ConversionDump.Result lotro = ConversionDump.run(abcCase, Profile.LOTRO, false, new AbcInfo());
			ConversionDump.Result plain = ConversionDump.run(abcCase, Profile.PLAIN_MIDI, false, new AbcInfo());
			assertEquals(lotro.error(), plain.error());
			if (lotro.error() != null)
				return;
			List<long[]> lotroNotes = notes(ConversionDump.convert(abcCase, Profile.LOTRO));
			List<long[]> plainNotes = notes(ConversionDump.convert(abcCase, Profile.PLAIN_MIDI));
			assertEquals(plainNotes.size(), lotroNotes.size(), "number of notes");
			for (int i = 0; i < plainNotes.size(); i++) {
				long[] l = lotroNotes.get(i), p = plainNotes.get(i);
				assertEquals(p[1], l[1], "start of note " + i + " in track " + p[0]);
				assertTrue(l[2] <= p[2], "note " + i + " in track " + p[0] + " ends at " + l[2]
						+ " with LotRO instruments, later than its written end " + p[2]);
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
		void noteRestartedAtAnotherVolumeWhileItSoundsIsALotroError() throws Exception {
			// Tested in LotRO: the c2 still sounds when c starts again. With +ff+ in between the part plays nothing.
			LotroFileParseException e = assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "[c2z] +ff+ c d|"), Profile.LOTRO_STRICT));
			assertTrue(e.getMessage().contains("+ff+"), e.getMessage());
			// ... but it plays without a volume change, with +mf+ (already the volume), or with +ff+ before both
			for (String body : List.of("[c2z] c d|", "[c2z] +mf+ c d|", "+ff+ [c2z] c d|"))
				ConversionDump.convert(tune("semantic", body), Profile.LOTRO_STRICT);
			// ... and a tie continuation is no new attack, so a volume change before it is fine (TD4-TD6)
			for (String body : List.of("[c2-z] +ff+ c d|", "c- +ff+ c d|", "c- d +ff+ c|"))
				ConversionDump.convert(tune("semantic", body), Profile.LOTRO_STRICT);
		}

		/** "tick:text" of every lyric event in a track, the tick in eighths (Q:120, L:1/8). */
		private static List<String> lyrics(Sequence sequence, int trackIndex) {
			List<String> lyrics = new ArrayList<>();
			long eighth = sequence.getResolution() / 2;
			Track track = sequence.getTracks()[trackIndex];
			for (int i = 0; i < track.size(); i++) {
				if (track.get(i).getMessage() instanceof javax.sound.midi.MetaMessage mm && mm.getType() == 0x05)
					lyrics.add(track.get(i).getTick() / eighth + ":" + new String(mm.getData(),
							java.nio.charset.StandardCharsets.UTF_8));
			}
			return lyrics;
		}

		@Test
		void lyricsAreSungToTheNotesAbove() throws Exception {
			// One syllable per note; - splits a word, a space ends it (Maestro's MidiText reads them like karaoke)
			assertEquals(List.of("0:hel", "1:lo ", "2:world ", "3:wide "),
					lyrics(convert(tune("semantic", "c d e f|", "w:hel-lo world wide")), 1));
			// Rests and tie continuations get no syllable, a chord gets one
			assertEquals(List.of("0:a ", "2:b ", "4:c "),
					lyrics(convert(tune("semantic", "c z d- d [ceg] z|", "w:a b c")), 1));
			// _ holds a syllable over the next note, * skips a note, ~ joins words, \- is a hyphen
			assertEquals(List.of("0:A", "2:le ", "4:1. Ti ", "5:e-mail "),
					lyrics(convert(tune("semantic", "c d e f g a|", "w:A_le * 1.~Ti e\\-mail")), 1));
			// | goes on at the next bar
			assertEquals(List.of("0:one ", "4:two "), lyrics(convert(tune("semantic", "c d e f|g a|", "w:one | two")), 1));
		}

		@Test
		void onlyTheFirstVerseIsSung() throws Exception {
			// More w: lines under the same notes are later verses; LotRO plays no repeats, so only verse 1 fits.
			// A w: line after earlier lyrics starts a new line (/)
			assertEquals(List.of("0:one ", "1:two ", "2:/three ", "3:four "), lyrics(convert(tune("semantic",
					"c d|", "w:one two", "w:uno dos", "e f|", "w:three four", "w:tres cuatro")), 1));
			// Syllables beyond the notes are dropped
			assertEquals(List.of("0:a ", "1:b "), lyrics(convert(tune("semantic", "c d|", "w:a b c d")), 1));
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
			// On one tick Maestro's MidiText sorts lyric lines by their text, so each W: line gets a tick of its own
			Sequence s = convert(tune("semantic", header(), "W:b", "W:a", "c d|", "W:d", "W:c"));
			List<Long> ticks = new ArrayList<>();
			List<String> lines = new ArrayList<>();
			for (int i = 0; i < s.getTracks()[0].size(); i++) {
				MidiEvent e = s.getTracks()[0].get(i);
				if (e.getMessage() instanceof javax.sound.midi.MetaMessage mm && mm.getType() == 0x05) {
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
			// Tested in LotRO: chord symbols, grace notes, ~ . , endings, V: and a T: in the body all play
			List<NoteEvent> plain = noteEvents(convert(tune("semantic", "c d e f g a b c'|")));
			for (String body : List.of("\"C\"c d e f \"G7\"g a b c'|", "{g}c d e f g a b c'|",
					"~c d .e f g a b c'|", "[1 c d e f :|[2 g a b c'|]", "|: c d e f |1 g a b c' :|2 |]", "c d e f [|g a b c'|"))
				assertEquals(plain, noteEvents(convert(tune("semantic", body))), body);
			assertEquals(plain, noteEvents(convert(tune("semantic", "c d e f|", "T:Second section", "V:1 treble",
					"g a b c'|"))));
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
			// Tested in LotRO: a part with y plays nothing
			assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(tune("semantic", "c d y e f|"), Profile.LOTRO_STRICT));
			assertEquals(noteEvents(convert(tune("semantic", "c d e f|"))), noteEvents(convert(tune("semantic", "c d y e f|"))));
		}

		@Test
		void decorationIsALotroError() throws Exception {
			// Tested in LotRO: from a !decoration! on, the part is silent, also on later lines and after +mf+
			for (String body : List.of("!f!c d e f|", "c d e f !trill!g a b c'|"))
				assertThrows(LotroFileParseException.class, () -> ConversionDump.convert(tune("semantic", body),
						Profile.LOTRO_STRICT), body);
			// Without LotRO errors the decorations are skipped and the notes play
			assertEquals(noteEvents(convert(tune("semantic", "c d e f g a b c'|"))),
					noteEvents(convert(tune("semantic", "!f!c d e f !trill!g a b c'|"))));
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
		void pluckedNoteKeepsItsWrittenLength() throws Exception {
			// Harp is non-sustained. A long note isn't cut to the harp sample, a short one isn't lengthened to it.
			Sequence s = ConversionDump.convert(tune("semantic", header("T:Test Harp"), "c8 d|"), Profile.LOTRO);
			long q = s.getResolution(); // c8 = 8 eighths = 4 quarters
			assertEquals(List.of(on(0, 60), off(4 * q, 60), on(4 * q, 62), off(4 * q + q / 2, 62)), noteEvents(s));
		}

		@Test
		void lastPluckedNoteIsCutWhereItsSampleRunsOut() throws Exception {
			// c32 (8 s) is written longer than its harp sample, and nothing sounds after it. So the song ends where c's
			// sample runs out, and c's note-off is moved there. d/ ends before that and keeps its written length.
			Sequence s = ConversionDump.convert(tune("semantic", header("T:Test Harp"), "d/ c32|"), Profile.LOTRO);
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
			// C,,, and c''' are outside LotRO's C2..C5. Only the strict profile (LotRO errors on) may complain.
			AbcCase outOfRange = tune("semantic", "C,,, c'''|");
			assertEquals(List.of(12, 96), noteOns(ConversionDump.convert(outOfRange, Profile.LOTRO)).stream()
					.map(NoteEvent::pitch).toList());
			assertEquals(List.of(12, 96), noteOns(convert(outOfRange)).stream().map(NoteEvent::pitch).toList());
			assertThrows(LotroFileParseException.class, () -> ConversionDump.convert(outOfRange, Profile.LOTRO_STRICT));
		}

		@Test
		void playlistReadsTitlesLikeTheConversion() throws Exception {
			// parseAbcMetadata (the ABC Player's playlist) must handle % comments and \% like convert() does
			for (String title : List.of("T:100\\% Harp", "T:100% Harp", "T:Song % a comment")) {
				AbcCase abcCase = tune("semantic", header(title), "c|");
				AbcInfo converted = new AbcInfo();
				ConversionDump.run(abcCase, Profile.PLAIN_MIDI, false, converted);
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
			// Q:1000 with L:1/2834674 used to overflow int in the LotRO length check (it reported -0.932 s)
			Sequence large = ConversionDump.convert(tune("semantic", header("L:1/2834674", "Q:1000"), "c5669348 d5669348|"),
					Profile.LOTRO_STRICT);
			Sequence small = ConversionDump.convert(tune("semantic", header("L:1/2", "Q:1000"), "c4 d4|"),
					Profile.LOTRO_STRICT);
			assertEquals(noteEvents(small), noteEvents(large));
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

		@Test
		void partWithoutLUsesTheDefaultNoteLength() throws Exception {
			// Tested in LotRO: c64 in a part without L: fails (16 s at the default L:1/8), both after a part with
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
			// Tested in LotRO. L:1/64 before the first X: applies to a part without M: and L: (c64 = 2 s, not 16 s)
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
					Profile.LOTRO);
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
			Sequence s = AbcToMidi.convert(params); // Default params: LotRO instruments on
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
			Profile.PLAIN_MIDI.applyTo(params);
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
			AbcInfo empty = abcInfoOf(tune("semantic", AbcCases.extended("%%part-name"), "c|"));
			assertEquals("", empty.getPartName(0));
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