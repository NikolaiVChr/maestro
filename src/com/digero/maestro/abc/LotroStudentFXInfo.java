package com.digero.maestro.abc;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.digero.common.midi.Note;

public class LotroStudentFXInfo implements Comparable<LotroStudentFXInfo> {
	private static Map<Integer, LotroStudentFXInfo> byId = new HashMap<>();
	private static SortedMap<String, SortedSet<LotroStudentFXInfo>> byCategory = new TreeMap<>();

	public static final LotroStudentFXInfo DISABLED = new LotroStudentFXInfo(Note.REST, "None", "#None");
	public static final List<LotroStudentFXInfo> ALL_FX;

	static {
		byCategory.put(DISABLED.category, new TreeSet<>());
		byCategory.get(DISABLED.category).add(DISABLED);
		byId.put(DISABLED.note.id, DISABLED);

		add(Note.C2, "Ricochet");
		add(Note.Cs2, "Knock wood");
		add(Note.D2, "Heavy Staccato");

		int noteCount = 3 + 1;
		if (byId.size() < noteCount) {
			List<Integer> unassigned = new ArrayList<>(noteCount);
			for (int id = Note.MIN_PLAYABLE.id; id <= Note.MAX_PLAYABLE.id; id++) {
				unassigned.add(id);
			}
			unassigned.removeAll(byId.keySet());
			for (int id : unassigned) {
				add(Note.fromId(id), "Unassigned");
			}
		}

		ALL_FX = Collections.unmodifiableList(new ArrayList<>(new AbstractCollection<LotroStudentFXInfo>() {
			@Override
			public Iterator<LotroStudentFXInfo> iterator() {
				return new FXInfoIterator();
			}

			@Override
			public int size() {
				return byId.size();
			}
		}));
	}

//	private static final Comparator<Note> noteComparator = new Comparator<Note>() {
//		public int compare(Note o1, Note o2) {
//			return o1.id - o2.id;
//		}
//	};

//	private static void makeCategory(String category, Note... notes) {
//		Arrays.sort(notes, noteComparator);
//		for (Note note : notes) {
//			add(category, note);
//		}
//	}

	private static void add(Note note, String category) {
		SortedSet<LotroStudentFXInfo> categorySet = byCategory.get(category);
		if (categorySet == null) {
			byCategory.put(category, categorySet = new TreeSet<>());
		} else if (categorySet.size() == 1) {
			// We're about to add a second one to the category...
			// add the "1" to the name of the existing element
			Note prevNote = categorySet.first().note;
			String prevName = category + " 1 (" + prevNote.abc + ")";
			LotroStudentFXInfo prevInfo = new LotroStudentFXInfo(prevNote, prevName, category);
			categorySet.clear();
			categorySet.add(prevInfo);
			byId.put(prevNote.id, prevInfo);
		}

		String name;
		if (categorySet.isEmpty()) {
			// If this is the first item in the category, don't add its number to the list
			name = category + " (" + note.abc + ")";
		} else {
			name = category + " " + (categorySet.size() + 1) + " (" + note.abc + ")";
		}
		LotroStudentFXInfo info = new LotroStudentFXInfo(note, name, category);

		categorySet.add(info);
		byId.put(note.id, info);
	}

	public static LotroStudentFXInfo getById(int noteId) {
		return byId.get(noteId);
	}

	private static class FXInfoIterator implements Iterator<LotroStudentFXInfo> {
		private Iterator<SortedSet<LotroStudentFXInfo>> outerIter;
		private Iterator<LotroStudentFXInfo> innerIter;

		public FXInfoIterator() {
			outerIter = byCategory.values().iterator();
		}

		@Override
		public boolean hasNext() {
			return outerIter.hasNext() || (innerIter != null && innerIter.hasNext());
		}

		@Override
		public LotroStudentFXInfo next() {
			while (innerIter == null || !innerIter.hasNext())
				innerIter = outerIter.next().iterator();

			return innerIter.next();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	public final Note note;
	public final String name;
	public final String category;

	private LotroStudentFXInfo(Note note, String name, String category) {
		this.note = note;
		this.name = name;
		this.category = category;
	}

	@Override
	public String toString() {
		return name;
	}

	@Override
	public int compareTo(LotroStudentFXInfo that) {
		if (that == null)
			return 1;

		return this.note.id - that.note.id;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != this.getClass())
			return false;

		return this.note.id == ((LotroStudentFXInfo) obj).note.id;
	}

	@Override
	public int hashCode() {
		return this.note.id;
	}
}
