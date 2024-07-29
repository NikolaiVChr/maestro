package com.digero.common.abc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LotroInstrumentNick {

	/**
	 * Just for printing all nicks out in BB code for Discord
	 */
	public static void main(String[] args) {
		for (LotroInstrument instrument : LotroInstrument.values()) {
			System.out.println("\n**" + instrument + "**");
			for (String nick : LotroInstrumentNick.getNicks(instrument)) {
				System.out.println(nick);
			}
		}
	}

	static String[] HARP = { "Harp", "Harfe", "Harpe" };
	static String[] MMH = { "MistyHarp", "MMHarp", "MMH", "HarpeMB", "HarfeDN" };
	static String[] BLUTE = { "LuteB", "BLute", "LuthB", "StLaute" };
	static String[] LOA = { "AgeLute", "LuteA", "LOA", "Laute", "Luth", "LuthSiecles", "LauteVZ" };
	static String[] THEO = { "Theorbo", "Theo", "Theorbe" };
	static String[] TTF = { "TravellerFiddle", "TrustyFiddle", "TTFiddle", "ViolonV", "TTF", "GeigeDR" };
	static String[] BARDIC = { "Bardic", "BardenGeige", "ViolonBarde", "BrdFiddle" };
	static String[] BASICF = { "ViolonBase", "StFiedel", "BscFiddle" };
	static String[] LMF = { "LMF", "LonelyFiddle", "LMFiddle", "GeigeEB", "ViolonMS" };
	static String[] SPF = { "MuntereGeige", "ViolonAlerte", "MuntereG", "ViolonA", "Sprightly", "SPFiddle" };
	static String[] STF = { "ViolonDE", "StudFiddle", "SchulFiedel" };
	static String[] PIPES = { "Bagpipe", "Pipes", "Dudelsack", "Sack", "Cornemuse" };
	static String[] BAS_B = { "StFagott", "BassonBase", "BscBassoon" };
	static String[] BRU_B = { "Schroffes", "Brusque" };
	static String[] LMB = { "LonelyBassoon", "LM Bassoon", "FagottEB", "BassonMS", "LMB" };
	static String[] CLA = { "Clarinet", "Clari", "Klarinette", "Clarinette" };
	static String[] FLUT = { "Flute", "Floete", "Flut" };
	static String[] HORN = { "Horn", "Cor" };
	static String[] PIB = { "Pibgorn", "Pib" };
	static String[] COWB = { "Cowbell", "Glocke", "Cloche", "Kuhglocke" };
	static String[] MOOR = { "Moor", "MoorBell" };
	static String[] DRUM = { "Drums", "Trommel", "Tambour" };

	public static List<String> getNicks(LotroInstrument instr) {
		String[] nicks = {};
		switch (instr) {
		case LONELY_MOUNTAIN_BASSOON:
			nicks = LMB;
			break;
		case BARDIC_FIDDLE:
			nicks = BARDIC;
			break;
		case BASIC_BAGPIPE:
			nicks = PIPES;
			break;
		case BASIC_BASSOON:
			nicks = BAS_B;
			break;
		case BASIC_CLARINET:
			nicks = CLA;
			break;
		case BASIC_COWBELL:
			nicks = COWB;
			break;
		case BASIC_DRUM:
			nicks = DRUM;
			break;
		case BASIC_FIDDLE:
			nicks = BASICF;
			break;
		case BASIC_FLUTE:
			nicks = FLUT;
			break;
		case BASIC_HARP:
			nicks = HARP;
			break;
		case BASIC_HORN:
			nicks = HORN;
			break;
		case BASIC_LUTE:
			nicks = BLUTE;
			break;
		case BASIC_PIBGORN:
			nicks = PIB;
			break;
		case BASIC_THEORBO:
			nicks = THEO;
			break;
		case BRUSQUE_BASSOON:
			nicks = BRU_B;
			break;
		case LONELY_MOUNTAIN_FIDDLE:
			nicks = LMF;
			break;
		case LUTE_OF_AGES:
			nicks = LOA;
			break;
		case MISTY_MOUNTAIN_HARP:
			nicks = MMH;
			break;
		case MOOR_COWBELL:
			nicks = MOOR;
			break;
		case SPRIGHTLY_FIDDLE:
			nicks = SPF;
			break;
		case STUDENT_FIDDLE:
			nicks = STF;
			break;
		case TRAVELLERS_TRUSTY_FIDDLE:
			nicks = TTF;
			break;
		default:
			break;
		}
		return new ArrayList<>(Arrays.asList(nicks));
	}

}