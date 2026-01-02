package com.digero.common.midi;

@SuppressWarnings("HardCodedStringLiteral")
public enum MidiDrum {
	/*
	 * Standard Drum Kit
	 * 35 to 81: MIDI 1.0 GM v96.1 third edition
	 * 27 to 34 and 82 to 87: MIDI 1.0 GM2 1.2a
	 * 
	 * See also kitSounds.txt
	 * 
	 */
	SYNTH_ZAP("High Q"), // 27 
	UNKNOWN_28("Slap"), // 28 
	SCRATCH_1("Scratch Push"), // 29 
	SCRATCH_2("Scratch Pull"), // 30 
	DRUM_STICKS("Sticks"), // 31 
	UNKNOWN_32("Square Click"), // 32 
	METR_CLICK("Metronome Click"), // 33 
	METR_BELL("Metronome Bell"), // 34 
	ACOU_BASS("Acou. Bass Drum"), // 35 
	BASS_DRUM("Bass Drum"), // 36
	RIM_SHOT("Side Stick"), // 37 
	ACOU_SNARE("Acou. Snare"), // 38
	HAND_CLAP("Hand Clap"), // 39
	ELEC_SNARE("Elec. Snare"), // 40
	LOW_TOM_A("Low Floor Tom"), // 41 
	CLOSED_HI_HAT("Closed Hi-Hat"), // 42
	LOW_TOM_B("High Floor Tom"), // 43
	PEDAL_HI_HAT("Pedal Hi-Hat"), // 44
	MID_TOM_A("Low Tom"), // 45
	OPEN_HI_HAT("Open Hi-Hat"), // 46
	MID_TOM_B("Low-Mid Tom"), // 47 
	HIGH_TOM_A("Hi Mid Tom"), // 48 
	CRASH_CYM_1("Crash Cym. 1"), // 49
	HIGH_TOM_B("High Tom"), // 50 
	RIDE_CYM_1("Ride Cym. 1"), // 51
	CHINESE_CYM("Chinese Cym."), // 52
	RIDE_BELL("Ride Bell"), // 53
	TAMBOURINE("Tambourine"), // 54
	SPLASH_CYM("Splash Cym."), // 55
	COWBELL("Cowbell"), // 56
	CRASH_CYM_2("Crash Cym. 2"), // 57
	VIBRASLAP("Vibraslap"), // 58
	RIDE_CYM_2("Ride Cym. 2"), // 59
	HI_BONGO("Hi Bongo"), // 60
	LOW_BONGO("Low Bongo"), // 61
	MUTE_HI_CONGA("Mute Hi Conga"), // 62
	OPEN_HI_CONGA("Open Hi Conga"), // 63
	LOW_CONGA("Low Conga"), // 64
	HIGH_TIMBALE("High Timbale"), // 65
	LOW_TIMBALE("Low Timbale"), // 66
	HIGH_AGOGO("High Agogo"), // 67
	LOW_AGOGO("Low Agogo"), // 68
	CABASA("Cabasa"), // 69
	MARACAS("Maracas"), // 70
	SHORT_WHISTLE("Short Whistle"), // 71
	LONG_WHISTLE("Long Whistle"), // 72
	SHORT_GUIRO("Short Guiro"), // 73
	LONG_GUIRO("Long Guiro"), // 74
	CLAVES("Claves"), // 75
	HIGH_BLOCK("High Wood Block"), // 76
	LOW_BLOCK("Low Wood Block"), // 77 
	MUTE_CUICA("Mute Cuica"), // 78
	OPEN_CUICA("Open Cuica"), // 79
	MUTE_TRIANGLE("Mute Triangle"), // 80
	OPEN_TRIANGLE("Open Triangle"), // 81
	CABASA_2("Shaker"), // 82 
	BELLS("Jingle Bell"), // 83 
	CHIMES("Bell Tree"), // 84 
	CASTANET("Castanet"), // 85 
	MUTED_LARGE_DRUM("Mute Surdo"), // 86
	LARGE_DRUM("Open Surdo"), // 87
	INVALID("Unknown");

	private static final MidiDrum[] values = values();
	public static final int DRUM_ID_OFFSET = 27;

	public static MidiDrum fromId(int id) {
		id -= DRUM_ID_OFFSET;
		if (id < 0 || id >= values.length)
			return INVALID;

		return values[id];
	}

	public final String name;

	MidiDrum(String name) {
		this.name = name;
	}

	public int id() {
		return ordinal() + DRUM_ID_OFFSET;
	}

	@Override
	public String toString() {
		return name;
	}
}
