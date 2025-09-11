package com.digero.maestro.abc;

import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.digero.common.abc.LotroInstrument;
import com.digero.maestro.util.ListModelWrapper;

public class PartAutoNumberer {
    protected static final Logger log = Logger.getLogger("song");

    public static final PartAutoNumberer.OrderOption orderOptionDefault = PartAutoNumberer.OrderOption.CLUSTER_SIMILAR;

    public Comparator<? super AbcPart> getComparator() {
        if (settings.orderOption == OrderOption.CLUSTER_SIMILAR) {
            return partSimilarComparator;
        }
        return partNumberComparator;
    }

    /**
     *
     * 1st sort according to instrument base number
     * 2nd sort according to part number
     *
     * Dont use this without setting firstNumber on parts first
     *
     */
    private final Comparator<NumberedAbcPart> partSimilarComparator = new Comparator<>() {
        @Override
        public int compare(NumberedAbcPart p1, NumberedAbcPart p2) {
            if (p1.getFirstNumber() != p2.getFirstNumber())
                return Integer.compare(p1.getFirstNumber(), p2.getFirstNumber());
            return Integer.compare(p1.getPartNumber(), p2.getPartNumber());
        }
    };

    /**
     *
     * sort according to part number
     *
     *
     */
    private final Comparator<NumberedAbcPart> partNumberComparator = new Comparator<>() {
        @Override
        public int compare(NumberedAbcPart p1, NumberedAbcPart p2) {
            return Integer.compare(p1.getPartNumber(), p2.getPartNumber());
        }
    };

    public static class Settings {

		private Map<LotroInstrument, Integer> firstNumber = new EnumMap<>(LotroInstrument.class);
		private boolean incrementByTen;
		private final Preferences prefs;
        public PartAutoNumberer.OrderOption orderOption;

		private Settings(Preferences prefs) {
			this.prefs = prefs;
			incrementByTen = prefs.getBoolean("incrementByTen", true);
			int x10 = incrementByTen ? 1 : 10;

            try {
                orderOption = OrderOption.fromString(prefs.get("orderOption", orderOptionDefault.name()));
            } catch (IllegalArgumentException e) {
                orderOption = orderOptionDefault;
            }

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
            init(LotroInstrument.JAUNTY_HAND_KNELLS, LotroInstrument.BASIC_HARP);

			assert (firstNumber.size() == LotroInstrument.values().length);
		}

		/**
         * @return the original name of the instrument before it was renamed, which can be used a stable prefs key even
         * if the instrument is renamed.
         */
		public String prefsKey(LotroInstrument instrument) {
			// @formatter:off
			// Missing case statement
			return switch (instrument)
			{
                case LUTE_OF_AGES->"Lute of Ages";
                case BASIC_LUTE->"Basic Lute";
                case BASIC_HARP->"Harp";
                case MISTY_MOUNTAIN_HARP->"Misty Mountain Harp";
                case BARDIC_FIDDLE->"Bardic Fiddle";
                case BASIC_FIDDLE->"Basic Fiddle";
                case LONELY_MOUNTAIN_FIDDLE->"Lonely Mountain Fiddle";
                case SPRIGHTLY_FIDDLE->"Sprightly Fiddle";
                case STUDENT_FIDDLE->"Student's Fiddle";
                case TRAVELLERS_TRUSTY_FIDDLE->"Traveller's Trusty Fiddle";
                case JAUNTY_HAND_KNELLS->"Jaunty Hand-Knells";
                case BASIC_THEORBO->"Theorbo";
                case BASIC_FLUTE->"Flute";
                case BASIC_CLARINET->"Clarinet";
                case BASIC_HORN->"Horn";
                case BASIC_BASSOON->"Basic Bassoon";
                case BRUSQUE_BASSOON->"Brusque Bassoon";
                case LONELY_MOUNTAIN_BASSOON->"Lonely Mountain Bassoon";
                case BASIC_BAGPIPE->"Bagpipe";
                case BASIC_PIBGORN->"Pibgorn";
                case BASIC_DRUM->"Drums";
                case BASIC_COWBELL->"Cowbell";
                case MOOR_COWBELL->"Moor Cowbell";
                default->instrument.toString();
            };
			// @formatter:on
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
            prefs.put("orderOption", orderOption.name());
		}

		public Settings(Settings source) {
			prefs = source.prefs;
			copyFrom(source);
		}

		public void copyFrom(Settings source) {
			firstNumber = new EnumMap<>(source.firstNumber);
			incrementByTen = source.incrementByTen;
            orderOption = source.orderOption;
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

    /**
     * Make sure the abc parts have a 'part number manually assigned' that is not null.
     */
    public void assignManualPartNumber(ListModelWrapper<AbcPart> parts) {
        Boolean manualAssigned = null;
        for (AbcPart part : parts) {
            if (part.isPartNumberManuallyAssigned() == null) {
                // sort of a hack, since scheme can be changed between saving and loading project.
                if (!isFittingInAutoNumberingScheme(part, -1, -1)) {
                    manualAssigned = true;
                } else if (manualAssigned == null) {
                    manualAssigned = false;
                }
            }
        }
        if (manualAssigned != null) {
            for (AbcPart part : parts) {
                if (part.isPartNumberManuallyAssigned() == null) part.setPartNumberManuallyAssigned(manualAssigned, false);
            }
            // instead of notifying listeners many times, we do it once here
            // We don't have to worry about which abc-part that send out notify,
            // since PartPanel.setAbcPart() will get called after this has ran.
            // So the UI will be uptodate.
            parts.getFirst().notifyPartNumberManuallyAssigned();
        }
    }

    /**
     * Renumbers all parts, ensuring that each part is assigned a
     * unique, sequential part number starting from a specific base value.
     *
     * The method uses an increment value to
     * derive new part numbers, ensuring no duplicates exist among the assigned numbers.
     *
     * The renumbering process considers the instrument associated with each part to calculate the
     * starting number, then increments as necessary to avoid conflicts with numbers already in use.
     *
     */
	public void renumberAllParts() {

		if (parts == null)
			return;

		Set<Integer> numbersInUse = new HashSet<>(parts.size());

		List<? extends NumberedAbcPart> partsCopy = new ArrayList<>(parts);// This is to prevent a reordering of parts
																			// while iterating through it.

        NumberedAbcPart part1 = partsCopy.getFirst();
        if (part1 != null && ((AbcPart)part1).getAbcSong().sorted) {
            ((AbcPart)part1).getAbcSong().suppressPartSort = true;
        }
        for (NumberedAbcPart part : partsCopy) {
            if (part.isPartNumberManuallyAssigned() == true) {
                numbersInUse.add(part.getPartNumber());
            }
        }
		for (NumberedAbcPart part : partsCopy) {
            if (part.isPartNumberManuallyAssigned()) {
                continue;
            }
			int partNumber = getFirstNumber(part.getInstrument());
			while (numbersInUse.contains(partNumber)) {
				partNumber += getIncrement();
			}
			numbersInUse.add(partNumber);
			part.setPartNumber(partNumber);
		}
        if (part1 != null && ((AbcPart)part1).getAbcSong().sorted) {
            ((AbcPart)part1).getAbcSong().suppressPartSort = false;
            ((AbcPart)part1).getAbcSong().sortParts(null);
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
        partAdded.setPartNumberManuallyAssigned(false, true);
	}

	public void onPartDeleted(NumberedAbcPart partDeleted) {
		// System.out.println(partDeleted.getPartNumber()+" deleted");
		if (parts == null)
			return;

		int deletedNumber = partDeleted.getPartNumber();
		int deletedFirstNumber = getFirstNumber(partDeleted.getInstrument());

		if (!isFittingInAutoNumberingScheme(partDeleted, -1, -1)) {
			log.fine(partDeleted.getInstrument().toString()+" deleted and did not fit");
			return;
		}

        List<? extends NumberedAbcPart> partsCopy = new ArrayList<>(parts);
        partsCopy.sort(partNumberComparator);

		for (NumberedAbcPart part : partsCopy) {
			int partNumber = part.getPartNumber();
			int partFirstNumber = getFirstNumber(part.getInstrument());

			boolean autoTest = isFittingInAutoNumberingScheme(part, deletedNumber, deletedFirstNumber);
            if (!autoTest || part.isPartNumberManuallyAssigned() == true) {
                continue;
            }
			if (part != partDeleted && partNumber > deletedNumber && partNumber > partFirstNumber
					&& partFirstNumber == deletedFirstNumber) {
                part.setPartNumber(deletedNumber);
                deletedNumber = partNumber;
                // the deleted spot was filled out, the one that filled it out is now
                // considered deleted
			}
		}
	}

    /**
     * Return true if testPart fit into the auto numbering scheme.
     * If it does not or a part with lower part number has a different firstNumber,
     * but seemingly fit into this parts numbering scheme
     * it will also return false.
     *
     * Since this method can be called after a part has been removed,
     * the caller can optionally supply a deleted partNumber and its firstNumber.
     * Else supply -1 for both.
     *
     */
	private boolean isFittingInAutoNumberingScheme(NumberedAbcPart testPart, int deletedNumber, int deletedFirstNumber) {

		int testNumber = testPart.getPartNumber();
		int testFirstNumber = getFirstNumber(testPart.getInstrument());
		if (testNumber == testFirstNumber) {
            // it fits
            return true;
        }
		if (getIncrement() == 10 && Math.abs(testNumber) % 10 != testFirstNumber) {
            // increment is 10, but the number does not fit into the scheme
			return false;
		}
		if (testNumber < testFirstNumber) {
            // the part number is lower than the first number, so it does not fit into the scheme
            return false;
        }
		boolean cohesive = true;
		int checkNumber = testFirstNumber;
		if (testFirstNumber != deletedFirstNumber) {
            // testPart is not sharing firstNumber with the deleted part,
            // so deletedNumber is set to -1, which means it's ignored.
			deletedNumber = -1;
		}
		outer: while (cohesive && checkNumber < testNumber) {
			if (checkNumber == deletedNumber) {
				// checks out (deleted), we pretend it still there
                // and continue looping.
				checkNumber += getIncrement();
				continue outer;
			}
			for (NumberedAbcPart part : parts) {
				int partNumber = part.getPartNumber();

				if (checkNumber == partNumber) {
					if (testFirstNumber != getFirstNumber(part.getInstrument())) {
                        // a part, which has a part-number that fits with testPart firstNumber,
                        // but is lower than testPart's part-number.
                        // However the part-number does not match its own firstNumber.
                        // So since testPart is placed after this part,
                        // but the number order is broken, we return false.
						return false;
					}
					// It checks out
					checkNumber += getIncrement();
					continue outer;
				}
			}
			cohesive = false;
			// System.out.println(testNumber+" not cohesive");
		}
		return cohesive;
	}

    /**
     * Called directly from UI
     * So both this part and the part that potentially have the number we want,
     * will be marked as manually assigned.
     */
	public void setPartNumber(NumberedAbcPart partToChange, int newPartNumber) {

		if (parts == null)
			return;

        NumberedAbcPart replace = null;

		for (NumberedAbcPart part : parts) {
			if (part != partToChange && part.getPartNumber() == newPartNumber) {
				part.setPartNumber(partToChange.getPartNumber());
                replace = part;
				break;
			}
		}
		partToChange.setPartNumber(newPartNumber);

        // we do these after both setpartnumber so that the assigning gets done properly.
        if (replace != null) {
            // We don't notify listeners here,
            // we do it after setting the second part also.
            replace.setPartNumberManuallyAssigned(true, false);
        }
        partToChange.setPartNumberManuallyAssigned(true, true);
	}

	public void setInstrument(NumberedAbcPart partToChange, LotroInstrument newInstrument) {

		if (newInstrument != partToChange.getInstrument()) {
			if (partToChange.isPartNumberManuallyAssigned() || getFirstNumber(partToChange.getInstrument()) == getFirstNumber(newInstrument)) {
				// Lets keep the part number, since it either has the same first number, or is locked
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

    public enum OrderOption {
        // name() is saved in maestro options
        // label is shown in UI

        CLUSTER_SIMILAR("Cluster instr. with similar numbers"),
        PART_NUMBER("Sort purely by part-numbers");

        private final String label;

        OrderOption(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }

        public static PartAutoNumberer.OrderOption fromString(String name) {
            for (PartAutoNumberer.OrderOption c : values()) {
                if (c.label.equalsIgnoreCase(name) || c.name().equalsIgnoreCase(name)) {
                    return c;
                }
            }
            throw new IllegalArgumentException("Unknown OrderOption: " + name);
        }
    }
}
