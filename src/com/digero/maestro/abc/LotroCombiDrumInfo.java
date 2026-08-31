package com.digero.maestro.abc;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.Note;

public class LotroCombiDrumInfo {
	protected static final Logger log = Logger.getLogger("drumCombiHits");
	private static final String PREFS_NODE = "LotroCombiDrumInfo";
	protected final boolean usePrefs;// Auto-exporter wont use prefs, so it wont save or load library of combis.

	public record CombiDrumHit(Note firstNote, Note secondNote, String name, boolean locked) {
	}

	// preview generation can access library from another thread. Just to be safe we use ConcurrentHashMap.
	private final Map<Note, CombiDrumHit> library = new ConcurrentHashMap<>();

	// Keep this as it is for backwards compatibility:
	public static final Preferences drumPrefs = Preferences.userNodeForPackage(AbcPart.class).node("drums");

	private final List<Runnable> libraryListeners = new CopyOnWriteArrayList<>();
	public void addLibraryListener(Runnable r) {
		libraryListeners.add(r);
	}
	public void removeLibraryListener(Runnable r) {
		libraryListeners.remove(r);
	}
	public void removeAllListeners() {
		libraryListeners.clear();
	}
	void fireLibraryChanged() {
		libraryListeners.forEach(Runnable::run);
	}

	/**
	 * Seed locked builtin combis.
	 */
	private void seedLockedBuiltins() {
		// Added Rock Bass (Jersiel)
		library.put(Note.Cs5, new CombiDrumHit(Note.As3, Note.D3, "Xtra Bass Rock", true));

		// Added Rock Snare (Jersiel)
		library.put(Note.D5, new CombiDrumHit(Note.E3, Note.C5, "Xtra Snare Rock", true));

		// Added Crash Cymbal (Jersiel)
		library.put(Note.Ds5, new CombiDrumHit(Note.A3, Note.Cs2, "Xtra Crash Cymbal", true));

		// Added march snare 1: Slap 7 (c') + Rim Shot 1 (^D) (Jersiel)
		library.put(Note.E5, new CombiDrumHit(Note.C5, Note.Ds3, "Xtra Snare March 1", true));

		// Concert bass: Bass Open (^A) + Bass (^G) (Jersiel)
		library.put(Note.F5, new CombiDrumHit(Note.As3, Note.Gs3, "Xtra Bass Concert", true));

		// Metal Bass: Muted 2 (^c) + Bass Slap 2 (D) (Jersiel)
		library.put(Note.Fs5, new CombiDrumHit(Note.Cs4, Note.D3, "Xtra Bass Metal", true));

		// March snare 2: Slap 3 (E) + Rattle Short 3 (^G,) (Jersiel)
		library.put(Note.G5, new CombiDrumHit(Note.E3, Note.Gs2, "Xtra Snare March 2", true));

		// Added Xtra Bass March (Aifel)
		library.put(Note.Gs5, new CombiDrumHit(Note.C3, Note.Gs3, "Xtra Bass March", true));
		
		// Added Xtra Bass Boomy (Aifel)
		library.put(Note.A5, new CombiDrumHit(Note.As3, Note.Cs3, "Xtra Bass Boomy", true));
		
		// Added Xtra Snare Tribal (Aifel)
		library.put(Note.As5, new CombiDrumHit(Note.C5, Note.Cs3, "Xtra Snare Tribal", true));

		// Added Xtra Reverse Cymbal: Long Rattle (A) + Tambourine (^A,) (Aifel)
		// firstNotes.put(Note.A6, Note.A4);
		// secondNotes.put(Note.A6, Note.As2);
		
		// Added Xtra Snare Clap (Elamond)
		library.put(Note.B5, new CombiDrumHit(Note.E2, Note.Ds3, "Xtra Clap", true));
	}

	/**
	 * Return true if its a locked Xtra drum hit.
	 */
	public static boolean noteIdIsFixed(int noteId) {
		// hardcoded, so don't change seedLockedBuiltins() ever.
		// we need this as a static also, and static doesn't have access to library.
		// hence why its hardcoded here. Only used by LotroInstrument.
		return noteId >= Note.Cs5.id && noteId <= Note.B5.id;
	}

	/**
	 * Create instance. usePrefs: true to enable load/save library from prefs.
	 */
	public LotroCombiDrumInfo(boolean usePrefs) {
		seedLockedBuiltins();
		this.usePrefs = usePrefs;
		if (this.usePrefs) loadLibrary(drumPrefs);
	}

	/**
	 *  Copy - own library, shared immutable entries, never persists (abc preview use).
	 */
	public LotroCombiDrumInfo(LotroCombiDrumInfo other) {
		this.usePrefs = false;                        // a copy is a throwaway; never write prefs
		this.library.putAll(other.library);           // ConcurrentHashMap.putAll - entries are immutable records
		// listeners: no copy - a preview copy has no UI observers
	}

	/**
	 * Return an available key slot. Or null is library is full.
	 */
	private Note allocateLibraryKey() {
		for (int id = LotroInstrument.BASIC_DRUM.highestPlayable.id + 1; id <= Note.MAX.id; id++) {
			Note n = Note.fromId(id);
			if (n != null && !library.containsKey(n)) return n;   // LOCKED lives in library too, so this covers 73-83
		}
		for (int id = 1; id < LotroInstrument.BASIC_DRUM.lowestPlayable.id; id++) {
			Note n = Note.fromId(id);
			if (n != null && !library.containsKey(n)) return n;  // 1 to 35
		}
		return null;   // full: ~90 combos reached
	}

	/**
	 * Return true if its a note playable by lotro drum.
	 */
	private static boolean isPlayableHit(int n) {
		return n >= LotroInstrument.BASIC_DRUM.lowestPlayable.id
				&& n <= LotroInstrument.BASIC_DRUM.highestPlayable.id;
	}

	/**
	 * Return true if the pair is the same as the combi hit.
	 */
	public static boolean samePair(CombiDrumHit c, Note a, Note b) {
		return (c.firstNote() == a && c.secondNote() == b)
				|| (c.firstNote() == b && c.secondNote() == a);
	}

	/**
	 * Return the combi hit, or null if its not in library.
	 */
	public CombiDrumHit get(int key) {
		Note n = Note.fromId(key);
		return n == null ? null : library.get(n);
	}

	/**
	 * Return true if it a key slot. Does not mean the key exist in the library.
	 */
	public static boolean isValidKeyId(Integer key) {
		if (key == null) return false;
		if (key == Note.REST.id) return false;
		return !isPlayableHit(key);
	}

	/**
	 * Return the key, or null if its not in library.
	 */
	public Note libraryKeyForPair(Note n1, Note n2) {
		if (n1 == null || n2 == null) return null;
		for (Map.Entry<Note, CombiDrumHit> e : library.entrySet())
			if (samePair(e.getValue(), n1, n2))
				return e.getKey();
		return null;
	}

	/**
	 * Xtra notes are the locked ones
	 */
	public boolean noteIdIsLocked(int noteId) {
		if (isPlayableHit(noteId)) return false;
		Note n = Note.fromId(noteId);
		return n != null && library.containsKey(n) && library.get(n).locked();   // locked only, the old Xtra ones
	}

	/**
	 * entry pair with key/value of all the combis.
	 *
	 */
	public Set<Map.Entry<Note, LotroCombiDrumInfo.CombiDrumHit>> libraryEntries() {   // for the picker UI
		return Collections.unmodifiableMap(library).entrySet();
	}

	/**
	 *  Add/replace a user library entry. Never overwrites a locked built-in.
	 *  Return the key, or null if there was no room.
	 */
	public Note addToLibrary(Note n1, Note n2, String name) {
		if (n1 == null || n2 == null || n1 == n2) return null;
		if (!isPlayableHit(n1.id) || !isPlayableHit(n2.id)) return null;
		Note existing = libraryKeyForPair(n1, n2);
		if (existing != null) return existing;
		Note key = allocateLibraryKey();
		if (key == null) {
			log.warning("Library full, combo not added: " + n1.id + "+" + n2.id
					+ (name != null ? " (" + name + ")" : ""));
			return null;
		}
		library.put(key, new CombiDrumHit(n1, n2, name, false));
		log.info("Library added id=" + key.id + " pair=" + n1.id + "+" + n2.id
				+ " name=" + (name == null ? "" : name));
		saveLibrary();
		fireLibraryChanged();
		return key;
	}

	/**
	 * Add n1 and n2 as a combi to the library, if they're not already there. Return the key.
	 * If they are, return the existing key.
	 * Return null if there was no room.
	 * Does not fire listeners or persist library.
	 */
	public Note mergeQuiet(Note n1, Note n2, String name) {
		if (n1 == null || n2 == null || n1 == n2) return null;
		if (!isPlayableHit(n1.id) || !isPlayableHit(n2.id)) return null;
		Note existing = libraryKeyForPair(n1, n2);
		if (existing != null) return existing;
		Note key = allocateLibraryKey();
		if (key == null) return null;
		library.put(key, new CombiDrumHit(n1, n2, name, false));
		log.info("Library merged id=" + key.id + " pair=" + n1.id + "+" + n2.id
				+ " name=" + (name == null ? "" : name) + " (from file load)");
		return key;  // no saveLibrary, no fireLibraryChanged
	}

	/**
	 *  Remove a user library entry. Never removes a locked built-in.
	 *  Return true if it was removed.
	 */
	public boolean removeFromLibrary(Note key) {
		if (key == null) return false;
		if (isPlayableHit(key.id)) return false;
		CombiDrumHit ex = library.get(key);
		if (ex == null || ex.locked()) return false;
		CombiDrumHit existing = library.remove(key);
		if (existing == null) return false;
		log.info("Library removed id=" + key.id + " pair=" + existing.firstNote().id + "+" + existing.secondNote().id
				+ " name=" + (existing.name() == null ? "" : existing.name()));
		saveLibrary();
		fireLibraryChanged();
		return true;
	}

	/**
	 * Save combi hits.
	 */
	public void saveLibrary() {
		if (!usePrefs) return;
        try {
            saveLibrary(drumPrefs);
        } catch (BackingStoreException ignored) {
        }
    }

	private static final String noteSeperator = "\u001f";// Unit Separator (US)

	/**
	 * Save combi hits to prefs.
	 */
	private void saveLibrary(Preferences root) throws BackingStoreException {
		Preferences node = root.node(PREFS_NODE);
		node.clear(); // rewrite from scratch; locked ones are never written
		int i = 0;
		for (var e : library.entrySet()) {
			CombiDrumHit c = e.getValue();
			if (c.locked()) continue;
			// key id + pair + name, one record per index
			node.put("combi." + i,
					e.getKey().id + noteSeperator + c.firstNote().id + noteSeperator + c.secondNote().id + noteSeperator +
							(c.name() == null ? "" : c.name()));
			i++;
		}
	}

	/**
	 * Load combi hits from prefs.
	 */
	public void loadLibrary(Preferences root) {
		Preferences node = root.node(PREFS_NODE);
		try {
			for (String k : node.keys()) {
				if (!k.startsWith("combi.")) continue;
				String[] p = node.get(k, "").split(noteSeperator, -1);   // -1 keeps trailing empty name

				if (p.length < 3) continue;
				Note key = Note.fromId(Integer.parseInt(p[0]));
				Note n1  = Note.fromId(Integer.parseInt(p[1]));
				Note n2  = Note.fromId(Integer.parseInt(p[2]));
				String name = p.length == 4 ? p[3] : null;
				if (key == null || n1 == null || n2 == null) continue;
				if (n1 == n2) continue;
				if (noteIdIsLocked(key.id)) continue;        // never shadow a built-in id.
				if (libraryKeyForPair(n1, n2) != null) continue;  // pair is a built-in: don't duplicate into library
				if (!library.containsKey(key) && isValidKeyId(key.id)) {
					library.put(key, new CombiDrumHit(n1, n2, name, false));
				} else {
					Note j = allocateLibraryKey();          // direct, no save/fire
					if (j != null) library.put(j, new CombiDrumHit(n1, n2, name, false));
				}
			}
		} catch (BackingStoreException | NumberFormatException ex) {
			// corrupt palette prefs: fall back to built-ins only, don't fail startup
		}
	}
}
