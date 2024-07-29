package com.digero.maestro.abc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.digero.common.abc.LotroInstrument;

public class PartAutoNumberer {
	public static class Settings {
		private Map<LotroInstrument, Integer> firstNumber = new EnumMap<>(LotroInstrument.class);
		private boolean incrementByTen;
		private final Preferences prefs;

		private Settings(Preferences prefs) {
			this.prefs = prefs;
			incrementByTen = prefs.getBoolean("incrementByTen", true);
			int x10 = incrementByTen ? 1 : 10;

			if (!prefs.getBoolean("newCowbellDefaults", false)) {
				prefs.putBoolean("newCowbellDefaults", true);
				prefs.remove(prefsKey(LotroInstrument.BASIC_COWBELL));
				prefs.remove(prefsKey(LotroInstrument.MOOR_COWBELL));
			}

			init(LotroInstrument.LUTE_OF_AGES, prefs.getInt("Lute", 1 * x10)); // Lute was renamed to Lute of Ages
			init(LotroInstrument.BASIC_LUTE, LotroInstrument.LUTE_OF_AGES);
			init(LotroInstrument.BASIC_HARP, 2 * x10);
			init(LotroInstrument.MISTY_MOUNTAIN_HARP, LotroInstrument.BASIC_HARP);
			init(LotroInstrument.BASIC_THEORBO, 3 * x10);
			init(LotroInstrument.BASIC_FLUTE, 4 * x10);
			init(LotroInstrument.BASIC_CLARINET, 5 * x10);
			init(LotroInstrument.BASIC_HORN, 6 * x10);
			init(LotroInstrument.BASIC_BAGPIPE, 7 * x10);
			init(LotroInstrument.BASIC_PIBGORN, LotroInstrument.BASIC_BAGPIPE);
			init(LotroInstrument.BASIC_BASSOON, LotroInstrument.BASIC_BAGPIPE);
			init(LotroInstrument.LONELY_MOUNTAIN_BASSOON, LotroInstrument.BASIC_BAGPIPE);
			init(LotroInstrument.BRUSQUE_BASSOON, LotroInstrument.BASIC_BAGPIPE);
			init(LotroInstrument.BASIC_DRUM, 8 * x10);
			init(LotroInstrument.BASIC_COWBELL, LotroInstrument.BASIC_DRUM);
			init(LotroInstrument.MOOR_COWBELL, LotroInstrument.BASIC_DRUM);
			init(LotroInstrument.BASIC_FIDDLE, 9 * x10);
			init(LotroInstrument.BARDIC_FIDDLE, LotroInstrument.BASIC_FIDDLE);
			init(LotroInstrument.STUDENT_FIDDLE, LotroInstrument.BASIC_FIDDLE);
			init(LotroInstrument.LONELY_MOUNTAIN_FIDDLE, LotroInstrument.BASIC_FIDDLE);
			init(LotroInstrument.SPRIGHTLY_FIDDLE, LotroInstrument.BASIC_FIDDLE);
			init(LotroInstrument.TRAVELLERS_TRUSTY_FIDDLE, LotroInstrument.BASIC_FIDDLE);

			assert (firstNumber.size() == LotroInstrument.values().length);
		}

		/**
		 * @return the original name of the instrument before it was renamed, which can be used a stable prefs key even
		 *         if the instrument is renamed.
		 */
		public String prefsKey(LotroInstrument instrument) {
			// @formatter:off
			switch (instrument)
			{
				case LUTE_OF_AGES:             return "Lute of Ages";
				case BASIC_LUTE:               return "Basic Lute";
				case BASIC_HARP:               return "Harp";
				case MISTY_MOUNTAIN_HARP:      return "Misty Mountain Harp";
				case BARDIC_FIDDLE:            return "Bardic Fiddle";
				case BASIC_FIDDLE:             return "Basic Fiddle";
				case LONELY_MOUNTAIN_FIDDLE:   return "Lonely Mountain Fiddle";
				case SPRIGHTLY_FIDDLE:         return "Sprightly Fiddle";
				case STUDENT_FIDDLE:           return "Student's Fiddle";
				case TRAVELLERS_TRUSTY_FIDDLE: return "Traveller's Trusty Fiddle";
				case BASIC_THEORBO:            return "Theorbo";
				case BASIC_FLUTE:              return "Flute";
				case BASIC_CLARINET:           return "Clarinet";
				case BASIC_HORN:               return "Horn";
				case BASIC_BASSOON:            return "Basic Bassoon";
				case BRUSQUE_BASSOON:          return "Brusque Bassoon";
				case LONELY_MOUNTAIN_BASSOON:  return "Lonely Mountain Bassoon";
				case BASIC_BAGPIPE:            return "Bagpipe";
				case BASIC_PIBGORN:            return "Pibgorn";
				case BASIC_DRUM:               return "Drums";
				case BASIC_COWBELL:            return "Cowbell";
				case MOOR_COWBELL:             return "Moor Cowbell";
			}
			// @formatter:on

			assert false; // Missing case statement
			return instrument.toString();
		}

		private void init(LotroInstrument instrument, int defaultValue) {
			firstNumber.put(instrument, prefs.getInt(prefsKey(instrument), defaultValue));
		}

		private void init(LotroInstrument instruments, LotroInstrument copyDefaultFrom) {
			init(instruments, firstNumber.get(copyDefaultFrom));
		}

		private void save() {
			for (Entry<LotroInstrument, Integer> entry : firstNumber.entrySet()) {
				prefs.putInt(prefsKey(entry.getKey()), entry.getValue());
			}
			prefs.putBoolean("incrementByTen", incrementByTen);
		}

		public Settings(Settings source) {
			prefs = source.prefs;
			copyFrom(source);
		}

		public void copyFrom(Settings source) {
			firstNumber = new EnumMap<>(source.firstNumber);
			incrementByTen = source.incrementByTen;
		}

		public int getIncrement() {
			return incrementByTen ? 10 : 1;
		}

		public boolean isIncrementByTen() {
			return incrementByTen;
		}

		public void setIncrementByTen(boolean incrementByTen) {
			this.incrementByTen = incrementByTen;
		}

		public void setFirstNumber(LotroInstrument instrument, int number) {
			firstNumber.put(instrument, number);
		}

		public int getFirstNumber(LotroInstrument instrument) {
			return firstNumber.get(instrument);
		}

		public void restoreDefaults() {
			try {
				prefs.clear();
			} catch (BackingStoreException e) {
				e.printStackTrace();
			}

			Settings fresh = new Settings(prefs);
			this.copyFrom(fresh);
		}
	}

	private Settings settings;
	private List<? extends NumberedAbcPart> parts = null;

	public PartAutoNumberer(Preferences prefsNode) {
		this.settings = new Settings(prefsNode);
	}

	public void restoreDefaultSettings() {
		settings.restoreDefaults();
	}

	public Settings getSettingsCopy() {
		return new Settings(settings);
	}

	public boolean isIncrementByTen() {
		return settings.isIncrementByTen();
	}

	public int getIncrement() {
		return settings.getIncrement();
	}

	public int getFirstNumber(LotroInstrument instrument) {
		return settings.getFirstNumber(instrument);
	}

	public void setSettings(Settings settings) {
		this.settings.copyFrom(settings);
		this.settings.save();
	}

	public void setParts(List<? extends NumberedAbcPart> parts) {
		this.parts = parts;
	}

	public void renumberAllParts() {

		if (parts == null)
			return;

		Set<Integer> numbersInUse = new HashSet<>(parts.size());

		List<? extends NumberedAbcPart> partsCopy = new ArrayList<>(parts);// This is to prevent a reordering of parts
																			// while iterating through it.

		for (NumberedAbcPart part : partsCopy) {
			int partNumber = getFirstNumber(part.getInstrument());
			while (numbersInUse.contains(partNumber)) {
				partNumber += getIncrement();
			}
			numbersInUse.add(partNumber);
			part.setPartNumber(partNumber);
		}

	}

	public void onPartAdded(NumberedAbcPart partAdded) {

		if (parts == null)
			return;

		int newPartNumber = settings.getFirstNumber(partAdded.getInstrument());

		boolean conflict;
		do {
			conflict = false;
			for (NumberedAbcPart part : parts) {
				if (part != partAdded && part.getPartNumber() == newPartNumber) {
					newPartNumber += getIncrement();
					conflict = true;
				}
			}
		} while (conflict);

		partAdded.setPartNumber(newPartNumber);
	}

	public void onPartDeleted(NumberedAbcPart partDeleted) {
		// System.out.println(partDeleted.getPartNumber()+" deleted");
		if (parts == null)
			return;

		int deletedNumber = partDeleted.getPartNumber();
		int deletedFirstNumber = getFirstNumber(partDeleted.getInstrument());// System.out.println(deletedFirstNumber+"
																				// is the first from the
																				// deleted");
		if (!isAutoAssigned(partDeleted, -1, deletedFirstNumber)) {
			// System.out.println(partDeleted.getInstrument().toString()+" deleted and did not fit");
			return;
		}

		for (NumberedAbcPart part : parts) {
			int partNumber = part.getPartNumber();
			int partFirstNumber = getFirstNumber(part.getInstrument());// System.out.println(partFirstNumber+" is the
																		// first");
			boolean autoTest = isAutoAssigned(part, deletedNumber, deletedFirstNumber);
			if (part != partDeleted && partNumber > deletedNumber && partNumber > partFirstNumber
					&& partFirstNumber == deletedFirstNumber && autoTest) {
				part.setPartNumber(partNumber - getIncrement());// System.out.println(partNumber+" decremented");
				if (part.getPartNumber() == deletedNumber)
					deletedNumber = partNumber;// the deleted spot was filled out, the one that filled it out is now
												// considered deleted
			} // else System.out.println(autoTest+" "+partNumber+" isAutoAssigned(part)");
		}
	}

	private boolean isAutoAssigned(NumberedAbcPart testPart, int deletedNumber, int deletedFirstNumber) {
		// Return true if this part fit into the auto numbering scheme.
		// If it does not or a part with lower part number has a different firstNumber,
		// but seemingly fit into this parts numbering scheme
		// it will also return false.
		int testNumber = testPart.getPartNumber();
		int testFirstNumber = getFirstNumber(testPart.getInstrument());
		if (testNumber == testFirstNumber)
			return true;
		if (getIncrement() == 10 && Math.abs(testNumber) % 10 != testFirstNumber) {
			return false;
		}
		if (testNumber < testFirstNumber)
			return false;
		boolean cohesive = true;
		int checkNumber = testFirstNumber;
		if (testFirstNumber != deletedFirstNumber) {
			deletedNumber = -1;// should not be considered
		}
		outer: while (cohesive && checkNumber < testNumber) {
			if (checkNumber == deletedNumber) {
				// System.out.println(checkNumber+" checks out (deleted)");
				checkNumber += getIncrement();
				continue outer;
			}
			for (NumberedAbcPart part : parts) {
				int partNumber = part.getPartNumber();

				if (checkNumber == partNumber) {
					if (testFirstNumber != getFirstNumber(part.getInstrument())) {
						// System.out.println(" testFirstNumber != getFirstNumber(part.getInstrument())");
						return false;
					}
					// System.out.println(checkNumber+" checks out");
					checkNumber += getIncrement();
					continue outer;
				}
			}
			cohesive = false;
			// System.out.println(testNumber+" not cohesive");
		}
		return cohesive;
	}

	public void setPartNumber(NumberedAbcPart partToChange, int newPartNumber) {

		if (parts == null)
			return;

		for (NumberedAbcPart part : parts) {
			if (part != partToChange && part.getPartNumber() == newPartNumber) {
				part.setPartNumber(partToChange.getPartNumber());
				break;
			}
		}
		partToChange.setPartNumber(newPartNumber);
	}

	public void setInstrument(NumberedAbcPart partToChange, LotroInstrument newInstrument) {

		if (newInstrument != partToChange.getInstrument()) {
			if (getFirstNumber(partToChange.getInstrument()) == getFirstNumber(newInstrument)) {
				// Lets keep the part number, since it has the same first number
				partToChange.setInstrument(newInstrument);
			} else {
				onPartDeleted(partToChange);
				partToChange.setInstrument(newInstrument);
				onPartAdded(partToChange);
			}
		}
	}

	public LotroInstrument[] getSortedInstrumentList() {
		LotroInstrument[] instruments = LotroInstrument.values();
		Arrays.sort(instruments, (a, b) -> {
			int diff = getFirstNumber(a) - getFirstNumber(b);
			if (diff != 0)
				return diff;

			return a.toString().compareTo(b.toString());
		});
		return instruments;
	}
}
