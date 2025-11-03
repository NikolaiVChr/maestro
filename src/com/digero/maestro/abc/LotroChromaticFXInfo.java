package com.digero.maestro.abc;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.Note;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LotroChromaticFXInfo extends LotroEventInfo<LotroChromaticFXInfo> {
    private static final List<Note> notes = new ArrayList<>();
	public static final LotroChromaticFXInfo DISABLED = new LotroChromaticFXInfo(Note.REST, noneName);
    private static final List<LotroChromaticFXInfo> JAUNTY_FX;
    private static final Map<Integer, LotroChromaticFXInfo> JAUNTY_BY_ID;

	static {

        for (Note note : Note.values()) {
            if (note == Note.REST) continue;
            if (note.isFlat()) continue;
            add(note);
        }

        SortedMap<String, LotroChromaticFXInfo> byName = new TreeMap<>();
        JAUNTY_BY_ID = new HashMap<>();

        byName.put(DISABLED.name, DISABLED);
        JAUNTY_BY_ID.put(DISABLED.note.id, DISABLED);
        for (Note note : notes) {
            int octaveDisplacement = LotroInstrument.JAUNTY_HAND_KNELLS.octaveDelta;
            if (note.id >= LotroInstrument.JAUNTY_HAND_KNELLS.lowestPlayable.id
                    && note.id <= LotroInstrument.JAUNTY_HAND_KNELLS.highestPlayable.id) {
                String newName = "Octave " + (note.octave + octaveDisplacement) + " - " + note.name().charAt(0) + (note.isSharp()?"#":"");
                LotroChromaticFXInfo info = new LotroChromaticFXInfo(note, newName);
                //System.out.println(note+" Adding as: "+newName);
                byName.put(newName, info);
                JAUNTY_BY_ID.put(note.id, info);
            }
        }

        JAUNTY_FX = Collections.unmodifiableList(new ArrayList<>(new AbstractCollection<>() {
            @Override
            @NotNull
            public Iterator<LotroChromaticFXInfo> iterator() {
                return new ChromaticFXInfoIterator(byName);
            }

            @Override
            public int size() {
                return byName.size();
            }
        }));
	}

    public static List<LotroChromaticFXInfo> getRange(LotroInstrument lotroInstrument) {
        return switch (lotroInstrument) {
            case JAUNTY_HAND_KNELLS -> JAUNTY_FX;
            default -> null;
        };
    }

	private static void add(Note note) {
		notes.add(note);
	}

	public static LotroChromaticFXInfo getById(LotroInstrument instrument, int noteId) {
		return switch (instrument) {
            case JAUNTY_HAND_KNELLS -> JAUNTY_BY_ID.get(noteId);
            default -> null;
        };
	}

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Note getNote() {
        return note;
    }

    private static class ChromaticFXInfoIterator implements Iterator<LotroChromaticFXInfo> {
		private final Iterator<LotroChromaticFXInfo> outerIter;

		public ChromaticFXInfoIterator(SortedMap<String, LotroChromaticFXInfo> byName) {
            List<LotroChromaticFXInfo> list = new ArrayList<>(byName.values());
            Collections.sort(list);

			outerIter = list.iterator();
		}

		@Override
		public boolean hasNext() {
			return outerIter.hasNext();
		}

		@Override
		public LotroChromaticFXInfo next() {
			return outerIter.next();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

    public final Note note;
    public final String name;

	private LotroChromaticFXInfo(Note note, String name) {
		this.note = note;
		this.name = name;
	}
}
