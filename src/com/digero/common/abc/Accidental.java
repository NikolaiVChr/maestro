package com.digero.common.abc;

public enum Accidental {
	NONE("", 0), DOUBLE_FLAT("__", -2), FLAT("_", -1), NATURAL("=", 0), SHARP("^", 1), DOUBLE_SHARP("^^", 2);

	public final String abc;
	public final int deltaNoteId;

	Accidental(String abc, int deltaNoteId) {
		this.abc = abc;
		this.deltaNoteId = deltaNoteId;
	}

	@Override
	public String toString() {
		return abc;
	}

	public static Accidental fromDeltaId(int deltaNoteId) {
        return switch (deltaNoteId) {
            case -2 -> DOUBLE_FLAT;
            case -1 -> FLAT;
            case 0 -> NATURAL;
            case 1 -> SHARP;
            case 2 -> DOUBLE_SHARP;
            default -> NONE;
        };
	}
}
