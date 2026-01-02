package com.digero.common.midi;

@SuppressWarnings("HardCodedStringLiteral")
public enum MidiStandard {
// @formatter:off
	// MIDI 1.0:
	GM     ("GM",   "General Midi",            "MMA",      "MIDI Manufacturers Association"),
	GS     ("GS",   "General Sound",           "Roland",   "Roland Corporation"),
	XG     ("XG",   "Extended General MIDI",   "Yamaha",   "Yamaha Corporation"),
	GM2    ("GM2",  "General Midi Level 2",    "MMA",      "MIDI Manufacturers Association"),
	GM_PLUS("GM+",  "General Midi with Ports", "Cakewalk", "BandLab"),// Plus many other MIDI editors, this is only used in ProjectFrame for displaying Original source type	
	// ABC
	ABC    ("ABC",  "ABC Notation",            "Walshaw",  "Chris Walshaw"),	
	// MIDI 2.0:
	MIDI2("MIDI2",  "MIDI 2.0",                "MMA",      "MIDI Manufacturers Association"),// Maestro does not support this yet	
	// Preview
	PREVIEW("GM_PV","General Midi Preview",    "MMA",      "MIDI Manufacturers Association");
// @formatter:on

	public final String shortName;
	public final String longName;
	public final String shortBrand;
	public final String longBrand;

	MidiStandard(String shortName, String longName, String shortBrand, String longBrand) {
		this.shortName = shortName;
		this.longName = longName;
		this.shortBrand = shortBrand;
		this.longBrand = longBrand;
	}

	public int id() {
		return ordinal();
	}

	@Override
	public String toString() {
		return shortName;
	}
}