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
		// A voice without music is no part (The Session's "Celestial" ends with a V:2 line)
		assertEquals(List.of("X:1", "T:t", "V:1", "%%MIDI channel 1", "K:G", "g d|"),
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
}