package com.digero.common.abctomidi;

import com.digero.common.abctomidi.AbcToMidi;

/**
 * The {@link AbcToMidi.Params} combinations a test case is converted with. Every snapshot contains one section per
 * profile, so a refactoring that only breaks something is still caught.
 */
enum Profile {
	/** LotRO errors off. Last note truncated, lotro dynamics, (lotro) notes transposed in midi to their real pitch. Assumes the abc is made for lotro.*/
	ABC_PLAYER(true, false, false, true),
	/** LotRO errors on. Last note truncated, lotro dynamics, (lotro) note transposed in midi to their real pitch. Assumes the abc is made for lotro.*/
	ABC_PLAYER_STRICT(true, true, false, true),
	/** Should behave like v4.6.21 Maestro project from an ABC file, except for "K:D mix" bug fix. **/
	MAESTRO_LEGACY(false, false, true, true),
	/** A new v4.7.3 Maestro project from an ABC file made for Lotro: no repeats, no Q: note length, no chord accompaniment etc. */
	MAESTRO_NEW_LOTRO(false, false, false, true),
	/** A new v4.7.3 Maestro project from standard ABC: written pitch and ABC 2.1 semantics. */
	MAESTRO_NEW_STANDARD(false, false, false, false);

	final boolean useLotroInstruments;
	final boolean enableLotroErrors;
	final boolean legacy;
	final boolean lotroAbc;

	Profile(boolean useLotroInstruments, boolean enableLotroErrors, boolean legacy, boolean lotroAbc) {
		this.useLotroInstruments = useLotroInstruments;
		this.enableLotroErrors = enableLotroErrors;
		this.legacy = legacy;
		this.lotroAbc = lotroAbc;
	}

	void applyTo(AbcToMidi.Params params) {
		params.useLotroInstruments = useLotroInstruments;
		params.enableLotroErrors  = enableLotroErrors;
		params.standardPitch      = !lotroAbc;// also includes midi program guessing
		params.expandRepeats      = !lotroAbc && !legacy && !useLotroInstruments;
		params.specTempo          = !lotroAbc && !legacy && !useLotroInstruments;
		params.chordAccompaniment = !lotroAbc && !legacy && !useLotroInstruments;
		params.standard2011       = !lotroAbc && !legacy && !useLotroInstruments;
	}

	String describe() {
		return "useLotroInstruments=" + useLotroInstruments + ", enableLotroErrors=" + enableLotroErrors
				+ (legacy ? ", v4.6.21 compatibility" : "")
				+ (lotroAbc ? ", Lotro Abc" : "");
	}
}