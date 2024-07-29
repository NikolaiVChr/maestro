package com.digero.test;

import static com.digero.common.abc.LotroInstrument.BARDIC_FIDDLE;
import static com.digero.common.abc.LotroInstrument.BASIC_BAGPIPE;
import static com.digero.common.abc.LotroInstrument.BASIC_BASSOON;
import static com.digero.common.abc.LotroInstrument.BASIC_CLARINET;
import static com.digero.common.abc.LotroInstrument.BASIC_COWBELL;
import static com.digero.common.abc.LotroInstrument.BASIC_DRUM;
import static com.digero.common.abc.LotroInstrument.BASIC_FIDDLE;
import static com.digero.common.abc.LotroInstrument.BASIC_FLUTE;
import static com.digero.common.abc.LotroInstrument.BASIC_HARP;
import static com.digero.common.abc.LotroInstrument.BASIC_HORN;
import static com.digero.common.abc.LotroInstrument.BASIC_LUTE;
import static com.digero.common.abc.LotroInstrument.BASIC_PIBGORN;
import static com.digero.common.abc.LotroInstrument.BASIC_THEORBO;
import static com.digero.common.abc.LotroInstrument.BRUSQUE_BASSOON;
import static com.digero.common.abc.LotroInstrument.LONELY_MOUNTAIN_BASSOON;
import static com.digero.common.abc.LotroInstrument.LONELY_MOUNTAIN_FIDDLE;
import static com.digero.common.abc.LotroInstrument.LUTE_OF_AGES;
import static com.digero.common.abc.LotroInstrument.MISTY_MOUNTAIN_HARP;
import static com.digero.common.abc.LotroInstrument.MOOR_COWBELL;
import static com.digero.common.abc.LotroInstrument.SPRIGHTLY_FIDDLE;
import static com.digero.common.abc.LotroInstrument.STUDENT_FIDDLE;
import static com.digero.common.abc.LotroInstrument.TRAVELLERS_TRUSTY_FIDDLE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentNick;

public class LotroInstrumentTest {
	@Test
	public void runTest() {
		StringBuilder allInstruments = new StringBuilder();
		for (LotroInstrument instrument : LotroInstrument.values())
			allInstruments.append(instrument).append(" ");

		for (LotroInstrument instrument : LotroInstrument.values()) {
			test(instrument, instrument.name());
			test(instrument, instrument.toString());
			test(instrument, allInstruments.toString() + instrument); // Last match wins
			for (String nick : LotroInstrumentNick.getNicks(instrument)) {
				test(instrument, nick);
			}
		}

		test(LUTE_OF_AGES, "Lute");
		test(LUTE_OF_AGES, "the lute part");
		test(LUTE_OF_AGES, "Song - Guitar");
		test(LUTE_OF_AGES, " Lute of  Ages");
		test(LUTE_OF_AGES, "X AgeLute X");
		test(LUTE_OF_AGES, "LuteA");
		test(LUTE_OF_AGES, "loa 123");
		test(LUTE_OF_AGES, "Basic Lute Song - Lute of Ages");
		test(LUTE_OF_AGES, "Fiddle Song - Lute of Ages");
		test(LUTE_OF_AGES, "Bassoon Song - Lute");
		test(LUTE_OF_AGES, "Harp Song - Lute");
		test(LUTE_OF_AGES, "Clarinet Song - Lute");
		test(LUTE_OF_AGES, "Basic Horn Song - Lute");

		test(BASIC_LUTE, "Lute Song - basiclute 2");
		test(BASIC_LUTE, "Song - New Lute 3");
		test(BASIC_LUTE, "LuteB");
		test(BASIC_LUTE, "Song - banjo 1");
		test(BASIC_LUTE, "Basic Bassoon Song - Basic Lute");

		test(BASIC_HARP, "Song - Harp 2");
		test(BASIC_HARP, "Song - basic harp 3");
		test(BASIC_HARP, "Song - BasicHarp 4");

		test(MISTY_MOUNTAIN_HARP, "Harp - Misty  Mountain   Harp");
		test(MISTY_MOUNTAIN_HARP, "Song - MM Harp");
		test(MISTY_MOUNTAIN_HARP, "Song - MistyMountainHarp");
		test(MISTY_MOUNTAIN_HARP, "Song - Misty Harp");
		test(MISTY_MOUNTAIN_HARP, "Song - mm harp 2");
		test(MISTY_MOUNTAIN_HARP, "Song - mmh");
		test(MISTY_MOUNTAIN_HARP, "misty_harp");

		// test(BARDIC_FIDDLE, "Fiddle");
		test(BARDIC_FIDDLE, "Violin");
		test(BARDIC_FIDDLE, "Song - Bardic Fiddle");
		// test(BARDIC_FIDDLE, "Song - B Fiddle 2");

		test(BASIC_FIDDLE, "BasicFiddle");
		test(BASIC_FIDDLE, "basic_fiddle");
		test(BASIC_FIDDLE, "A basic fiddle");

		test(LONELY_MOUNTAIN_FIDDLE, "a lonelyfiddle");
		test(LONELY_MOUNTAIN_FIDDLE, "Song - LM Fiddle");
		test(LONELY_MOUNTAIN_FIDDLE, "lonely_fiddle");

		test(SPRIGHTLY_FIDDLE, "Song - Sprightly Fiddle");
		test(SPRIGHTLY_FIDDLE, "SprightlyFiddle");

		test(STUDENT_FIDDLE, "Student's Fiddle");
		test(STUDENT_FIDDLE, "Students Fiddle");
		test(STUDENT_FIDDLE, "StudentFiddle");

		test(STUDENT_FIDDLE, "StudentFXFiddle");
		test(STUDENT_FIDDLE, "StudentFX");
		test(STUDENT_FIDDLE, "Student FX");
		test(STUDENT_FIDDLE, "StudentsFX");
		test(STUDENT_FIDDLE, "Student'sFX");
		test(STUDENT_FIDDLE, "StudentFX");

		test(TRAVELLERS_TRUSTY_FIDDLE, "Traveler's Trusty Fiddle 2");
		test(TRAVELLERS_TRUSTY_FIDDLE, "Travellers Trusty Fiddle 3");
		test(TRAVELLERS_TRUSTY_FIDDLE, "travelerfiddle 4");
		test(TRAVELLERS_TRUSTY_FIDDLE, "trusty fiddle 5");
		test(TRAVELLERS_TRUSTY_FIDDLE, "TT fiddle 5");
		test(TRAVELLERS_TRUSTY_FIDDLE, "travellers_fiddle");

		test(BASIC_THEORBO, "Song - THEORBO");
		test(BASIC_THEORBO, "Song - basic  theorbo");
		test(BASIC_THEORBO, "Theo");
		test(BASIC_THEORBO, "The bass part");

		test(BASIC_FLUTE, "Song - BasicFlute 3");
		test(BASIC_FLUTE, "Song - a flute part");

		test(BASIC_CLARINET, "Song - Basic Clarinet");
		test(BASIC_CLARINET, "Song - the clarinet");

		test(BASIC_HORN, "Song - Basic horn");

		test(BASIC_BASSOON, "Bassoon");
		test(BASIC_BASSOON, "Song - Basic bassoon");
		test(BASIC_BASSOON, "Alonely bassoon");

		test(LONELY_MOUNTAIN_BASSOON, "Song - lonely mountain bassoon");
		test(LONELY_MOUNTAIN_BASSOON, "Lonely bassoon");
		test(LONELY_MOUNTAIN_BASSOON, "lonely_bassoon");

		test(BRUSQUE_BASSOON, "1 Brusque Bassoon");
		test(BRUSQUE_BASSOON, "2 Brusk  bassoon");

		test(BASIC_BAGPIPE, "Bagpipe 2");
		test(BASIC_BAGPIPE, "A BasicBagpipe");
		test(BASIC_BAGPIPE, "A Bag pipe");
		test(BASIC_BAGPIPE, "The bag pipe");
		test(BASIC_BAGPIPE, "pipes");

		test(BASIC_PIBGORN, "a basic pibgorn");
		test(BASIC_PIBGORN, "The Pibgorn 2");

		test(BASIC_DRUM, "Song - Drum");
		test(BASIC_DRUM, "Song - Drums 2");
		test(BASIC_DRUM, "Song - Basic Drums 2");

		test(MOOR_COWBELL, "Song - Moor Cowbell");
		test(MOOR_COWBELL, "Song - More Cowbell");

		test(BASIC_COWBELL, "Song - BasicCowbell 2");
		test(BASIC_COWBELL, "Song - Cowbell");

		test(null, "Dilute");
		test(null, "Sharp");
		test(null, "Basic Fiddlesticks");
		test(null, "Theorboat");
		test(null, "Fluted");
		test(null, "Eclarinet");
		test(null, "Shorn");
		test(null, "Lonely Mountain Bassoonet");
		test(null, "Bagpiped");
		test(null, "bag");
	}

	private static void test(LotroInstrument expected, String text) {
		LotroInstrument actual = LotroInstrument.findInstrumentName(text, null);
		assertEquals(expected, actual, text);
	}
}
