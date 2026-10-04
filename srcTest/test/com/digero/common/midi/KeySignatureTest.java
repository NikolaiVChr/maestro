package com.digero.common.midi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class KeySignatureTest {
	@Test
	void aKeyFromAbcIsAMidiKey() {
		// MIDI has major and minor keys only. A mode from ABC (K:Ddor) is the major key with its sharps or flats, so
		// notation software shows its key signature; aeolian is minor, ionian major
		assertEquals(new KeySignature(0, true), KeySignature.fromAbc(new KeySignature("Ddor")));
		assertEquals(new KeySignature(1, true), KeySignature.fromAbc(new KeySignature("Dmix")));
		assertEquals(new KeySignature(-1, true), KeySignature.fromAbc(new KeySignature("Gdor")));
		assertEquals(new KeySignature(0, false), KeySignature.fromAbc(new KeySignature("Aaeo")));
		assertEquals(new KeySignature(2, true), KeySignature.fromAbc(new KeySignature("Dion")));
		// Major and minor stay as they are
		KeySignature minor = new KeySignature("Em");
		assertSame(minor, KeySignature.fromAbc(minor));
		KeySignature major = new KeySignature("G");
		assertSame(major, KeySignature.fromAbc(major));
	}
}