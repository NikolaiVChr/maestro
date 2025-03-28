package com.digero.common.abc;

import com.digero.common.midi.Note;

public interface AbcConstants {
	// Chord
	int MAX_CHORD_NOTES = 6;

	// TimingInfo
	long ONE_SECOND_MICROS = 1000000L;
	long ONE_MINUTE_MICROS = 60L * ONE_SECOND_MICROS;
	long SHORTEST_NOTE_MICROS = ONE_MINUTE_MICROS / 1000L;// Some times LOTRO will play this short a note, sometimes not..
	long LONGEST_NOTE_MICROS = 8L * ONE_SECOND_MICROS;
	double SHORTEST_NOTE_SECONDS = 0.06d;// LOTRO will accept this short note duration except at 30, 60, 90 and 120 bpm.
	double LONGEST_NOTE_SECONDS = 8.0d;// This limits goes for rests also
	long LONGEST_NOTE_MICROS_WORST_CASE = (2L * SHORTEST_NOTE_MICROS - 1L)
			* (LONGEST_NOTE_MICROS / (2L * SHORTEST_NOTE_MICROS - 1L));
	int MAX_TEMPO = (int)(ONE_MINUTE_MICROS / SHORTEST_NOTE_MICROS);
	int MIN_TEMPO = (int)((ONE_MINUTE_MICROS + LONGEST_NOTE_MICROS / 2) / LONGEST_NOTE_MICROS); // Round up

	// Modifications to the ABC note lengths to sound more like the instruments in the game
	double NON_SUSTAINED_NOTE_HOLD_SECONDS = 1.5d;
	double SUSTAINED_NOTE_HOLD_SECONDS = 0.075d;// A little hold to get the release to sound more like lotros linear.
												// Lowered to 0.075s from 0.1s in
												// 2.5.0
	double NOTE_RELEASE_SECONDS = 0.625d;// Sadly this is linear 0.5s dB release, not linear 0.2s power release like in
										// lotro. In Gervill the release ends at -60 dB, assuming java8 kept same: 
										// So choosing 75 ms hold and 625 ms will give
										// -2.5 dB at 100 ms, -7.2 dB at 150 ms, -12 dB at 200 ms.
										// Lotro: -3 dB at 100 ms, -6 dB at 150 ms, -infinite at 200 ms.
	double STUDENT_FX_MIN_SECONDS = 1.5d;

	// MIDI Preview controller values
	int MIDI_REVERB = 0;// Changed to 0 from 3 in 2.5.0
	int MIDI_CHORUS = 0;

	/** Note ID used in ABC files for Cowbells. Somewhat arbitrary */
	int COWBELL_NOTE_ID = 71;

	/** The highest Note ID for bagpipe drones */
	int BAGPIPE_LAST_DRONE_NOTE_ID = Note.B2.id;

	/** The highest Note ID for the student fiddle "flub" notes */
	int STUDENT_FIDDLE_LAST_FLUB_NOTE_ID = Note.Fs2.id;

	static long getShortestNoteMicros(int bpm) {
		int[] strangeBPM = {9, 11, 13, 15, 18, 22, 26, 30, 36, 37, 43, 44, 45, 51, 52, 60, 72, 74, 86, 88, 90, 102, 104, 120, 144, 148, 172, 176, 180, 204, 208, 240, 288, 296, 344, 352, 360, 408, 416, 480, 576, 592, 688, 704, 720};
		for (int strange : strangeBPM) {
			if (strange == bpm) {
				return 60001L;
			}
		}
		
		// The strange tempos are an odd 'bug' in lotros music system
		// Its eight series starting with 9, 11, 13, 15, 37, 43, 45, 51
		// Each series is continued by multiplying with 2 all the time
		// Have only included up to 800 bpm
		
		return 60000L;
	}
}
