package com.digero.common.abctomidi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Test;

/**
 * Maestro's round trip of a MIDI's tempo changes: a MIDI with tempo changes, Maestro's ABC export of it (%%Q: at each
 * change), and that ABC read back. The first two steps are a fixture: My Immortal, the MIDI and Maestro 4.7.3's export
 * of it (2026-10-02), in the resources folder roundtrip. This test is the third step, read as Maestro reads its own
 * export (Lotro's reading, made for Lotro). The ABC must give back the MIDI's tempo at every moment, and its notes where
 * the MIDI has them.
 */
class AbcToMidiRoundTripTest {

	private static final String DIR = "/com/digero/abctomidi/roundtrip/";

	private static File resource(String name) throws Exception {
		return Path.of(Objects.requireNonNull(AbcToMidiRoundTripTest.class.getResource(DIR + name), name).toURI())
				.toFile();
	}

	private static Sequence readAsMaestroDoes(File abcFile) throws Exception {
		AbcToMidi.Params params = new AbcToMidi.Params(abcFile);
		Profile.MAESTRO_NEW_LOTRO.applyTo(params);
		params.abcInfo = new AbcInfo();
		return AbcToMidi.convert(params);
	}

	/** Tick => microseconds per quarter, of every tempo event in the sequence. */
	private static TreeMap<Long, Integer> tempoEvents(Sequence sequence) {
		TreeMap<Long, Integer> tempos = new TreeMap<>();
		for (Track track : sequence.getTracks()) {
			for (int i = 0; i < track.size(); i++) {
				if (track.get(i).getMessage() instanceof MetaMessage mm && mm.getType() == 0x51) {
					byte[] d = mm.getData();
					tempos.put(track.get(i).getTick(), ((d[0] & 0xFF) << 16) | ((d[1] & 0xFF) << 8) | (d[2] & 0xFF));
				}
			}
		}
		return tempos;
	}

	/** The seconds at a tick, by the tempo events (120 a minute before the first). */
	private static double seconds(Sequence sequence, TreeMap<Long, Integer> tempos, long tick) {
		double seconds = 0;
		long from = 0;
		int mpq = 500000;
		for (Map.Entry<Long, Integer> tempo : tempos.headMap(tick, true).entrySet()) {
			seconds += (tempo.getKey() - from) * (double) mpq / 1e6 / sequence.getResolution();
			from = tempo.getKey();
			mpq = tempo.getValue();
		}
		return seconds + (tick - from) * (double) mpq / 1e6 / sequence.getResolution();
	}

	/**
	 * The tempo changes as {seconds, beats per minute}, without events that keep the tempo (the MIDI repeats its 78 at
	 * 82.4 s; the export leaves that out).
	 */
	private static List<double[]> tempoChanges(Sequence sequence) {
		TreeMap<Long, Integer> tempos = tempoEvents(sequence);
		List<double[]> changes = new ArrayList<>();
		Integer last = null;
		for (Map.Entry<Long, Integer> tempo : tempos.entrySet()) {
			if (!tempo.getValue().equals(last))
				changes.add(new double[] { seconds(sequence, tempos, tempo.getKey()), 60e6 / tempo.getValue() });
			last = tempo.getValue();
		}
		return changes;
	}

	/** Every note start, in milliseconds. */
	private static TreeSet<Long> noteStarts(Sequence sequence) {
		TreeMap<Long, Integer> tempos = tempoEvents(sequence);
		TreeSet<Long> starts = new TreeSet<>();
		for (Track track : sequence.getTracks()) {
			for (int i = 0; i < track.size(); i++) {
				if (track.get(i).getMessage() instanceof ShortMessage sm && sm.getCommand() == ShortMessage.NOTE_ON
						&& sm.getData2() > 0)
					starts.add(Math.round(seconds(sequence, tempos, track.get(i).getTick()) * 1000));
			}
		}
		return starts;
	}

	@Test
	void tempoChangesComeBack() throws Exception {
		List<double[]> midi = tempoChanges(MidiSystem.getSequence(resource("MyImmortal.mid")));
		List<double[]> abc = tempoChanges(readAsMaestroDoes(resource("MyImmortal.abc")));
		assertEquals(155, midi.size());
		assertEquals(midi.size(), abc.size(), "tempo changes");
		for (int i = 0; i < midi.size(); i++) {
			String where = "tempo change " + i + " at " + midi.get(i)[0] + " s";
			assertTrue(Math.abs(midi.get(i)[0] - abc.get(i)[0]) < 0.001, where + ": at " + abc.get(i)[0] + " s");
			assertTrue(Math.abs(midi.get(i)[1] - abc.get(i)[1]) < 0.01, where + ": " + abc.get(i)[1] + " instead of "
					+ midi.get(i)[1]);
		}
	}

	@Test
	void notesComeBackWhereTheMidiHasThem() throws Exception {
		TreeSet<Long> midi = noteStarts(MidiSystem.getSequence(resource("MyImmortal.mid")));
		TreeSet<Long> abc = noteStarts(readAsMaestroDoes(resource("MyImmortal.abc")));
		assertEquals(midi.size(), abc.size(), "note starts");
		// The export puts notes on its grid: 2 of the 836 (bar 11, 36.5 s) are 35 ms from the MIDI's. Reading the ABC
		// back adds nothing to that
		int exact = 0;
		for (long start : abc) {
			Long before = midi.floor(start);
			Long after = midi.ceiling(start);
			long distance = Math.min((before == null) ? Long.MAX_VALUE : start - before,
					(after == null) ? Long.MAX_VALUE : after - start);
			assertTrue(distance <= 40, "a note at " + start + " ms is " + distance + " ms from the MIDI's");
			if (distance == 0)
				exact++;
		}
		assertEquals(abc.size() - 2, exact, "notes exactly where the MIDI has them");
	}
}