package com.digero.common.abc;

import com.digero.common.abctomidi.AbcToMidi;

/**
 * The {@link AbcToMidi.Params} combinations a test case is converted with. Every snapshot contains one section per
 * profile, so a refactoring that only breaks e.g. the LotRO sample-length path is still caught.
 */
enum Profile {
	/** LotRO instruments and sample lengths, LotRO errors off (like Maestro reading an ABC file). */
	LOTRO(true, false),
	/** LotRO instruments and sample lengths, LotRO errors on (like the ABC Player). */
	LOTRO_STRICT(true, true),
	/** Plain MIDI: no sample-length adjustment, octave deltas applied, no LotRO errors. */
	PLAIN_MIDI(false, false);

	final boolean useLotroInstruments;
	final boolean enableLotroErrors;

	Profile(boolean useLotroInstruments, boolean enableLotroErrors) {
		this.useLotroInstruments = useLotroInstruments;
		this.enableLotroErrors = enableLotroErrors;
	}

	void applyTo(AbcToMidi.Params params) {
		params.useLotroInstruments = useLotroInstruments;
		params.enableLotroErrors = enableLotroErrors;
	}

	String describe() {
		return "useLotroInstruments=" + useLotroInstruments + " enableLotroErrors=" + enableLotroErrors;
	}
}