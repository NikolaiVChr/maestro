package com.digero.common.abctomidi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.digero.common.abctomidi.VoiceSplitter;

/** VoiceSplitter: a tune's voices as parts (one X: each), as text. */
class VoiceSplitterTest {

	@Test
	void aFileWithoutVoicesStaysAsItIs() {
		assertNull(VoiceSplitter.split(List.of("X:1", "T:t", "K:C", "c d|", "", "X:2", "T:u", "K:G", "e f|")));
	}

	@Test
	void voicesInBlocksBecomeParts() {
		VoiceSplitter.Result r = VoiceSplitter.split(List.of( //
				"X:1", // 1
				"T:Ice And Fire", // 2
				"C:Trad.", // 3
				"M:6/8", // 4
				"L:1/8", // 5
				"V:1 name=\"Melody\"", // 6
				"V:2 clef=bass name=\"Bass\" octave=-1", // 7
				"K:Em", // 8
				"V:1", // 9
				"B6 E6|", // 10
				"w:la la", // 11
				"V:2", // 12
				"E,6 B,,6|", // 13
				"V:1", // 14
				"G6 A6|", // 15
				"V:2", // 16
				"E,6 A,,6|")); // 17
		assertEquals(List.of( //
				"X:1", "T:Ice And Fire", "C:Trad.", "M:6/8", "L:1/8", "V:1 name=\"Melody\"", "%%MIDI channel 1", "K:Em",
				"B6 E6|", "w:la la", "G6 A6|", //
				"X:2", "T:Ice And Fire", "M:6/8", "L:1/8", "V:2 clef=bass name=\"Bass\" octave=-1", "%%MIDI channel 2",
				"K:Em clef=bass octave=-1", "E,6 B,,6|", "E,6 A,,6|"), r.lines());
		// Each line from its source line (the voice's own header lines from the K:), for error messages
		assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 8, 8, 8, 10, 11, 15, //
				1, 2, 4, 5, 8, 8, 8, 13, 17 }, r.sourceLineNumbers());
	}

	@Test
	void inlineVoicesShareALineWithColumnsKept() {
		// Canzonetta's style: [V: 1] at the start of a line; also mid-line
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "K:C", //
				"[V: 1] c d|", "w:one two", "[V: 2] E F|", //
				"[V:1] e f| [V:2] G A|"));
		assertEquals(List.of("X:1", "T:t", "V:1", "%%MIDI channel 1", "K:C", //
				"       c d|", "w:one two", "      e f| ", //
				"X:2", "T:t", "V:2", "%%MIDI channel 2", "K:C", //
				"       E F|", "                 G A|"), r.lines());
		assertArrayEquals(new int[] { 1, 2, 3, 3, 3, 4, 5, 7, 1, 2, 3, 3, 3, 6, 7 }, r.sourceLineNumbers());
	}

	@Test
	void sectionsGoToEveryVoiceAndMusicBeforeTheFirstVoiceToTheFirst() {
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "P:AB", "K:C", //
				"%%MIDI program 73", "c c|", "P:A", "V:1", "c d|", "V:2", "E F|", "P:B", "V:1", "e f|", "V:2", "G A|",
				"W:Words after the tune"));
		assertEquals(List.of("X:1", "T:t", "P:AB", "V:1", "%%MIDI channel 1", "K:C", //
				"%%MIDI program 73", "c c|", "P:A", "c d|", "P:B", "e f|", "W:Words after the tune", //
				"X:2", "T:t", "P:AB", "V:2", "%%MIDI channel 2", "K:C", //
				"%%MIDI program 73", "P:A", "E F|", "P:B", "G A|"), r.lines());
	}

	@Test
	void sectionsInAVoicesMusicAreItsOwn() {
		// A tune written voice after voice, each with its sections: a P: after a V: and before notes is that voice's
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "P:BA", "K:C", //
				"V:1", "P:A", "c d|", "P:B", "e f|", "V:2", "P:A", "E F|", "P:B", "G A|"));
		assertEquals(List.of("X:1", "T:t", "P:BA", "V:1", "%%MIDI channel 1", "K:C", //
				"P:A", "c d|", "P:B", "e f|", //
				"X:2", "T:t", "P:BA", "V:2", "%%MIDI channel 2", "K:C", //
				"P:A", "E F|", "P:B", "G A|"), r.lines());
	}

	@Test
	void musicBeforeTheFirstVoiceIsVoiceOne() {
		// The Session's "Give Us An A": the melody starts before V:2, and goes on in V:1
		assertEquals(List.of("X:1", "T:t", "V:1", "%%MIDI channel 1", "K:G", "g d|", "B G|", //
				"X:2", "T:t", "V:2", "%%MIDI channel 2", "K:G", "G B|", "D G|"),
				VoiceSplitter.split(List.of("X:1", "T:t", "K:G", "g d|", "V:2", "G B|", "V:1", "B G|", "V:2", "D G|"))
						.lines());
		// "Vals à Lulu": the melody, then V:2 and V:3; the melody is voice 1
		assertEquals(List.of("X:1", "T:t", "V:1", "%%MIDI channel 1", "K:G", "g d|", //
				"X:2", "T:t", "V:2", "%%MIDI channel 2", "K:G", "G B|", //
						"X:3", "T:t", "V:3", "%%MIDI channel 3", "K:G", "D G|"),
				VoiceSplitter.split(List.of("X:1", "T:t", "K:G", "g d|", "V:2", "G B|", "V:3", "D G|")).lines());
		// A voice without music is no part (The Session's "Celestial" ends with a V:2 line); the one left is alone, so
		// no V: line (aLoneVoiceKeepsTheTunesName)
		assertEquals(List.of("X:1", "T:t", "%%MIDI channel 1", "K:G", "g d|"),
				VoiceSplitter.split(List.of("X:1", "T:t", "K:G", "g d|", "V:2")).lines());
		// With voices in the header, it is the header's first
		assertEquals(List.of("X:1", "T:t", "V:A", "%%MIDI channel 1", "K:G", "g d|", "B G|", //
				"X:2", "T:t", "V:B", "%%MIDI channel 2", "K:G", "G B|"),
				VoiceSplitter.split(List.of("X:1", "T:t", "V:A", "V:B", "K:G", "g d|", "V:B", "G B|", "V:A", "B G|"))
						.lines());
	}

	@Test
	void freeTextAndTunesWithoutVoicesStayAndPartsAreNumbered() {
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("A songbook.", "", "X:5", "T:plain", "K:C", "c|", "",
				"Notes between.", "X:9", "T:voiced", "K:C", "V:a", "c|", "V:b", "e|"));
		assertEquals(List.of("A songbook.", "", "X:1", "T:plain", "K:C", "c|", "", "Notes between.", //
				"X:2", "T:voiced", "V:a", "%%MIDI channel 1", "K:C", "c|", //
				"X:3", "T:voiced", "V:b", "%%MIDI channel 2", "K:C", "e|"), r.lines());
	}

	@Test
	void aLoneVoiceKeepsTheTunesName() {
		// One voice with music (FolkWiki writes V:1 above every tune): no V: line in the part, so the part keeps the
		// tune's title instead of "Voice 1"; its clef still reaches K:, and a %%MIDI voice for it the part
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "K:C", "V:1 clef=bass", "%%MIDI voice 1 instrument=43",
				"C, D,|"));
		assertEquals(List.of("X:1", "T:t", "%%MIDI channel 1", "%%MIDI voice instrument=43", "K:C clef=bass", "C, D,|"),
				r.lines());
		// With a name= the V: line stays, and names the part
		assertEquals(List.of("X:1", "T:t", "V:1 name=\"Cello\"", "%%MIDI channel 1", "K:C", "C, D,|"),
				VoiceSplitter.split(List.of("X:1", "T:t", "K:C", "V:1 name=\"Cello\"", "C, D,|")).lines());
		// Two voices defined, one with music: alone too
		assertEquals(List.of("X:1", "T:t", "%%MIDI channel 1", "K:C", "c d|"),
				VoiceSplitter.split(List.of("X:1", "T:t", "V:1", "V:2", "K:C", "V:1", "c d|")).lines());
	}

	@Test
	void thePartIsNamedByTheVoice() {
		assertEquals("Violin", VoiceSplitter.partName("1 name=\"Violin\""));
		assertEquals("Solo Flute", VoiceSplitter.partName(" T1 clef=treble nm=\"Solo Flute\" snm=\"Fl.\""));
		assertEquals("Bass", VoiceSplitter.partName("low name=Bass"));
		assertEquals("Voice 2", VoiceSplitter.partName("2 clef=bass"));
		assertNull(VoiceSplitter.partName(""));
	}

	@Test
	void abc2midiChannelsSkipTheDrums() {
		// Voices take channels 1, 2 ... in order; 10 is the drum channel
		List<String> tune = new java.util.ArrayList<>(List.of("X:1", "T:t", "K:C"));
		for (int v = 1; v <= 11; v++)
			tune.addAll(List.of("V:" + v, "c|"));
		List<String> channels = VoiceSplitter.split(tune).lines().stream().filter(l -> l.startsWith("%%MIDI channel"))
				.map(l -> l.substring(15)).toList();
		assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "11", "12"), channels);
	}

	@Test
	void playedPropertiesGoBeforeTheKeysComment() {
		// BUG1010: after the comment, octave=-1 was part of it, and the voice's octave was lost
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "V:1", "V:2 octave=-1", "K:G % key", "V:1",
				"c|", "V:2", "C|"));
		assertEquals(List.of("X:1", "T:t", "V:1", "%%MIDI channel 1", "K:G % key", "c|", //
				"X:2", "T:t", "V:2 octave=-1", "%%MIDI channel 2", "K:G octave=-1 % key", "C|"), r.lines());
	}

	@Test
	void voiceSwitchesInCommentsAndQuotesArentVoices() {
		// Fourth opinion (BUG-07, 2026-10-14): "% [V:2]" and "\"[V:2]\"" made a voice 2, which took the lines after
		// them
		assertNull(VoiceSplitter.split(List.of("X:1", "T:t", "K:C", "C D E F % [V:2]", "\"[V:2]\" G A B c")));
	}

	@Test
	void fieldsAfterASectionForEveryVoiceAreForEveryVoice() {
		// Fourth opinion (BUG-09): a P: with another field after it before the V: went to the voice before only, so
		// the part order left that section out of the other voices
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "P:AB", "K:C", "P:A", "V:1", "c d|", "V:2",
				"E F|", "P:B", "M:3/4", "V:1", "c d e|", "V:2", "E F G|"));
		assertEquals(List.of("X:1", "T:t", "P:AB", "V:1", "%%MIDI channel 1", "K:C", //
				"P:A", "c d|", "P:B", "M:3/4", "c d e|", //
				"X:2", "T:t", "P:AB", "V:2", "%%MIDI channel 2", "K:C", //
				"P:A", "E F|", "P:B", "M:3/4", "E F G|"), r.lines());
	}

	@Test
	void withoutAKeyInTheHeaderMusicStartsTheBody() {
		// Fourth opinion (BUG-10): the K: further on ended the header, so the music before it went into the first
		// part's header and the second part lost it. Now the K: is a key change, for the voice it is written in
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "V:1", "c d|", "V:2", "E F|", "K:G", "V:1",
				"f g|", "V:2", "G A|"));
		assertEquals(List.of("X:1", "V:1", "%%MIDI channel 1", "T:t", "c d|", "f g|", //
				"X:2", "V:2", "%%MIDI channel 2", "T:t", "E F|", "K:G", "G A|"), r.lines());
	}

	@Test
	void aFieldGoingOnIsNoMusic() {
		// Fourth opinion pass 2 (BUG-22, 2026-10-14): "+:" (a field going on, ABC 2.1, 2.3.4) was music, so it went to
		// the first voice only
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "K:C", "N:A note", "+: going on", "V:1",
				"c d|", "V:2", "E F|"));
		assertEquals(List.of("X:1", "T:t", "V:1", "%%MIDI channel 1", "K:C", "N:A note", "+: going on", "c d|", //
				"X:2", "T:t", "V:2", "%%MIDI channel 2", "K:C", "N:A note", "+: going on", "E F|"), r.lines());
	}

	@Test
	void aVoiceLinesCommentIsNoProperty() {
		// Fourth opinion (BUG-11): "% was transpose=2" reached K: and transposed the part; a second V:1 line in the
		// header replaced the first, so its name was lost
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "V:1 name=\"Flute\"",
				"V:1 clef=treble % was transpose=2", "V:2", "K:C", "V:1", "c d|", "V:2", "E F|"));
		assertEquals(List.of("X:1", "T:t", "V:1 name=\"Flute\" clef=treble", "%%MIDI channel 1", "K:C clef=treble",
				"c d|", //
				"X:2", "T:t", "V:2", "%%MIDI channel 2", "K:C", "E F|"), r.lines());
	}

	@Test
	void aVoiceTwiceOnALineGetsOneLine() {
		// Fourth opinion (BUG-08): voice 1 got two lines from one, so the w: under it followed only the second
		VoiceSplitter.Result r = VoiceSplitter.split(List.of("X:1", "T:t", "K:C", "[V:1] C D [V:2] E F [V:1] G A |",
				"w: x y z w"));
		assertEquals(List.of("X:1", "T:t", "V:1", "%%MIDI channel 1", "K:C", //
				"      C D " + " ".repeat(16) + "G A |", "w: x y z w", //
				"X:2", "T:t", "V:2", "%%MIDI channel 2", "K:C", " ".repeat(16) + "E F "), r.lines());
	}
}