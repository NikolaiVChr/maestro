package com.digero.common.midi;

import java.util.Arrays;
import java.util.regex.Pattern;

import com.digero.common.abc.LotroInstrument;

public class PanGenerator {
	public static final int CENTER = 64;
	
	public static final int MAX_NARROW = 15;
	public static final int VERY_NARROW = 20;
	public static final int NARROW = 25;
	public static final int MID_NARROW = 30;
	public static final int MID_WIDE = 35;
	public static final int SOMEWHAT_WIDE = 40;
	public static final int VERY_WIDE = 45;
	public static final int MAX_WIDE = 50;
	

	private final int[] count;
	private int sum;

	public PanGenerator() {
		count = new int[LotroInstrument.values().length];
		sum = 0;
	}

	public void reset() {
		Arrays.fill(count, 0);
		sum = 0;
	}

	public static final Pattern leftRegex = Pattern.compile("\\b(left|links|gauche)\\b");
    public static final Pattern rightRegex = Pattern.compile("\\b(right|rechts|droite)\\b");
    public static final Pattern centerRegex = Pattern.compile("\\b(middle|center|zentrum|mitte|centre)\\b");

	public int get(LotroInstrument instrument, String partTitle) {
		int pan = get(instrument);

		String titleLower = partTitle.toLowerCase();
		if (leftRegex.matcher(titleLower).find())
			pan = CENTER - MAX_WIDE;// Math.abs(pan - CENTER);
		else if (rightRegex.matcher(titleLower).find())
			pan = CENTER + MAX_WIDE;// Math.abs(pan - CENTER);
		else if (centerRegex.matcher(titleLower).find())
			pan = CENTER;

		sum += pan - CENTER;
		return pan;
	}

	public int get(LotroInstrument instrument, String partTitle, int panModifier) {
		int pan = get(instrument);

		if (panModifier != 100) {
			pan = pan - CENTER;
			pan = (int) (pan * (float) panModifier * 0.01f);
			pan = pan + CENTER;
		}

		String titleLower = partTitle.toLowerCase();
		if (leftRegex.matcher(titleLower).find())
			pan = CENTER - (int) (MAX_WIDE * (float) panModifier * 0.01f);// Math.abs(pan - CENTER);
		else if (rightRegex.matcher(titleLower).find())
			pan = CENTER + (int) (MAX_WIDE * (float) panModifier * 0.01f);// Math.abs(pan - CENTER);
		else if (centerRegex.matcher(titleLower).find())
			pan = CENTER;

		return pan;
	}

	public int get(LotroInstrument instrument) {
		switch (instrument) {
		case LUTE_OF_AGES:
		case TRAVELLERS_TRUSTY_FIDDLE:
		case BASIC_LUTE:
			instrument = LotroInstrument.LUTE_OF_AGES;
			break;
		case BASIC_HARP:
		case SPRIGHTLY_FIDDLE:
        case JAUNTY_HAND_KNELLS:
		case MISTY_MOUNTAIN_HARP:
			instrument = LotroInstrument.BASIC_HARP;
			break;
		case BASIC_COWBELL:
		case MOOR_COWBELL:
			instrument = LotroInstrument.BASIC_COWBELL;
			break;
		case BASIC_FIDDLE:
		case STUDENT_FIDDLE:
		case LONELY_MOUNTAIN_FIDDLE:
		case BARDIC_FIDDLE:
			instrument = LotroInstrument.BASIC_FIDDLE;
			break;
		case BASIC_BASSOON:
		case LONELY_MOUNTAIN_BASSOON:
		case BRUSQUE_BASSOON:
			instrument = LotroInstrument.BASIC_BASSOON;
			break;
		case BASIC_BAGPIPE:
		case BASIC_CLARINET:
		case BASIC_DRUM:
		case BASIC_FLUTE:
		case BASIC_HORN:
		case BASIC_PIBGORN:
		case BASIC_THEORBO:
			break;
		}

		int sign;
		int c = count[instrument.ordinal()]++;

        sign = switch (c % 3) {
            case 0 -> 1;
            case 1 -> -1;
            default -> 0;
        };
		
		int result = 0;
		switch (instrument) {
			case BASIC_FIDDLE:
				result = sign * -MAX_WIDE;
				break;
			case BASIC_HARP:
				result = sign * -VERY_WIDE;
				break;
			case BASIC_FLUTE:
				result = sign * -SOMEWHAT_WIDE;
				break;
			case BASIC_BAGPIPE:
				result = sign * -MID_NARROW;
				break;
			case BASIC_HORN:
				result = sign * -NARROW;
				break;
			case BASIC_COWBELL:
				result = sign * -MAX_NARROW;
				break;
			case BASIC_DRUM:
				result = sign * MAX_NARROW;
				break;
			case BASIC_PIBGORN:
				result = sign * VERY_NARROW;
				break;
			case BASIC_THEORBO:
				result = sign * NARROW;
				break;
			case LUTE_OF_AGES:
				result = sign * MID_WIDE;
				break;
			case BASIC_CLARINET:
				result = sign * VERY_WIDE;
				break;
			case BASIC_BASSOON:
				result = sign * MAX_WIDE;
				break;
			default:
				assert false : "Should not happen";
		}
		
		// The offset system prevent inbalance in stereo panning
		int offset = 0;
		if (result >= MID_NARROW || result <= -MID_NARROW) {
			// Only the instruments that we normally pan a lot will be considered for this
			if (sum < -MID_NARROW * 2) {
				offset = -sum;
			} else if (sum > MID_NARROW * 2) {
				offset = -sum;
			}
		}
		//result += offset; this needs more consideration
		//result = Math.min(result, MAX_WIDE);
		//result = Math.max(result, -MAX_WIDE);
		
		return CENTER + result;
	}
}
