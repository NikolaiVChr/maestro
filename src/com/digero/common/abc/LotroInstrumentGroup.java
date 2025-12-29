package com.digero.common.abc;

import com.digero.common.view.UIText;

public enum LotroInstrumentGroup {
	PLUCKED_STRINGS(UIText.get("common.plucked.strings")), //
	BOWED_STRINGS(UIText.get("common.bowed.strings")), //
	WOODWINDS(UIText.get("common.woodwinds")), //
	PERCUSSION(UIText.get("common.percussion")); //

	private final String label;

	LotroInstrumentGroup(String label) {
		this.label = label;
	}

	@Override
	public String toString() {
		return label;
	}

	public static LotroInstrumentGroup groupOf(LotroInstrument instrument) {
		switch (instrument) {
			case BARDIC_FIDDLE:
			case BASIC_FIDDLE:
			case LONELY_MOUNTAIN_FIDDLE:
			case SPRIGHTLY_FIDDLE:
			case STUDENT_FIDDLE:
				return LotroInstrumentGroup.BOWED_STRINGS;

			case BASIC_FLUTE:
			case BASIC_CLARINET:
			case BASIC_HORN:
			case BASIC_BASSOON:
			case BRUSQUE_BASSOON:
			case LONELY_MOUNTAIN_BASSOON:
			case BASIC_BAGPIPE:
			case BASIC_PIBGORN:
				return LotroInstrumentGroup.WOODWINDS;

			case BASIC_DRUM:
			case BASIC_COWBELL:
			case MOOR_COWBELL:
            case JAUNTY_HAND_KNELLS:
				return LotroInstrumentGroup.PERCUSSION;

			case LUTE_OF_AGES:
			case BASIC_LUTE:
			case BASIC_HARP:
			case MISTY_MOUNTAIN_HARP:
			case BASIC_THEORBO:
			case TRAVELLERS_TRUSTY_FIDDLE:
			default:
				return LotroInstrumentGroup.PLUCKED_STRINGS;
		}
	}
}