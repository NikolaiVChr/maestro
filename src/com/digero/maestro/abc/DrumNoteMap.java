package com.digero.maestro.abc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.*;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.xml.xpath.XPathExpressionException;

import com.digero.common.util.Util;
import org.w3c.dom.Element;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiDrum;
import com.digero.common.midi.Note;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.FileParseException;
import com.digero.common.util.Version;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.util.SaveUtil;
import com.digero.maestro.util.XmlUtil;

public class DrumNoteMap implements IDiscardable {
	protected static final Logger log = Logger.getLogger("drumHitMap");

	public static final String FILE_SUFFIX = Util.DRUMMAP_FILE_EXTENSION_NO_DOT;
	protected static final byte DISABLED_NOTE_ID = (byte) LotroDrumInfo.DISABLED.note.id;
	private static final String MAP_PREFS_KEY = "DrumNoteMap.map";
	private final LotroCombiDrumInfo combiInfo;

	protected byte[] map = null;
	private List<ChangeListener> listeners = null;

	private List<String> lastLoadCombiWarnings = new ArrayList<>();

    public DrumNoteMap(LotroCombiDrumInfo combiInfo) {
		this.combiInfo = combiInfo;
    }

    protected DrumNoteMap(DrumNoteMap orig) {
		if (orig.map != null) {
			map = Arrays.copyOf(orig.map, orig.map.length);
		}
		listeners = null;
		this.combiInfo = orig.combiInfo;
	}

    /**
     * Subclasses MUST implement this.
     */
    public DrumNoteMap copy() {
        return new DrumNoteMap(this);
    }

	public static String getXmlName() {
		return "drumMap";
	}

	/**
	 * Returns true if this map supports combis.
	 */
	protected boolean supportsCombis() {
		return true;
	}

	/**
	 *  The combo this map assigns to a marker id, or null if it isn't a combi here.
	 */
	public LotroCombiDrumInfo.CombiDrumHit resolveCombi(int lotroId) {
		if (!supportsCombis()) return null;
		Note n = Note.fromId(lotroId);
		if (n == null || !supportsCombis()) return null;
		return combiInfo.get(lotroId);
	}

	/**
	 * Returns true if the given lotroId is a combi.
	 */
	public boolean isCombiNote(int lotroId) {
		if (!supportsCombis()) return false;
		return resolveCombi(lotroId) != null;
	}

	/**
	 * Returns the lotroId for the given midiNoteId.
	 * The lotroId might be key to a combo.
	 */
	public byte get(int midiNoteId) {
		if (midiNoteId < 0 || midiNoteId > Byte.MAX_VALUE) {
			throw new IllegalArgumentException();
		}
		return get((byte) midiNoteId);
	}

	public byte get(byte midiNoteId) {
		// If map hasn't been initialized yet, use failback
		ensureMap();

		return map[midiNoteId];
	}

	/**
	 * Returns the midiId for the given lotroNoteId.
	 * The lotroNoteId might be key to a combo.
	 */
	public int getKeyFor(byte lotroId, int track, AbcPart part, AbcSong song) {
		if (map == null) return DISABLED_NOTE_ID;
		SortedSet<Integer> notesInUse = song.getSequenceInfo().getTrackInfo(track).getNotesInUse();
		for (byte key : map) {
			if (key == DISABLED_NOTE_ID) continue;
			if (map[key] == lotroId) {
				//if (!part.isPercussionNoteEnabled(track, (int)key)) continue;//we get preview error if it gets enabled after deletion even if combobox show 'None'. So dont uncomment.
				if (notesInUse.contains((int)key)) return key;
			}
		}
		return DISABLED_NOTE_ID;
	}

	public void set(int midiNoteId, int value) {
		if ((midiNoteId < Byte.MIN_VALUE || midiNoteId > Byte.MAX_VALUE)
				|| (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE)) {
			throw new IllegalArgumentException();
		}
		set((byte) midiNoteId, (byte) value);
	}

	public void set(byte midiNoteId, byte value) {
		if (get(midiNoteId) != value) {
			ensureMap();
			map[midiNoteId] = value;
			fireChangeEvent();
		}
	}

	protected byte getDefaultMapping(byte noteId) {
		return DISABLED_NOTE_ID;
	}

	protected LotroInstrument getLotroInstrument() {
		return LotroInstrument.BASIC_DRUM;
	}

	protected void ensureMap() {
		if (map == null)
			map = getFailsafeDefault();
	}

	public void addChangeListener(ChangeListener listener) {
		if (listeners == null)
			listeners = new ArrayList<>(2);

		if (!listeners.contains(listener))
			listeners.add(listener);
	}

	public void removeChangeListener(ChangeListener listener) {
		if (listeners != null)
			listeners.remove(listener);
	}

	protected void fireChangeEvent() {
		if (listeners != null) {
			ChangeEvent e = new ChangeEvent(this);
			for (ChangeListener l : listeners) {
				l.stateChanged(e);
			}
		}
	}

	@Override
	public void discard() {
		listeners = null;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || obj.getClass() != this.getClass()) return false;
		DrumNoteMap o = (DrumNoteMap) obj;
		return Arrays.equals(map, o.map);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(map);
	}

	/**
	 * Not default map.
	 */
	public boolean isModified() {
		return map != null;
	}

	/** Persist this map's assignments as the default for future new drum tracks. */
	public void saveTemplate() {
		save(LotroCombiDrumInfo.drumPrefs);
	}

	/** Load the saved new-track default into this map, if one exists and is valid. */
	public void loadTemplate() {
		load(LotroCombiDrumInfo.drumPrefs);
	}

	/**
	 * Save drummap to prefs.
	 */
	public void save(Preferences prefs) {
		ensureMap();
		prefs.putByteArray(MAP_PREFS_KEY, map);
	}

	/**
	 * Load drummap from prefs.
	 */
	public void load(Preferences prefs) {
		setLoadedByteArray(prefs.getByteArray(MAP_PREFS_KEY, null), getLotroInstrument());
	}

	/**
	 * Install a loaded drum-map byte array, replacing any library id that is neither a
	 * playable drum nor a known combo marker with the failsafe default.
	 */
	protected void setLoadedByteArray(byte[] bytes, LotroInstrument lotroInstrument) {
		if (bytes != null && bytes.length == MidiConstants.NOTE_COUNT) {
			map = bytes;
			byte[] failsafe = null;
			for (int i = 0; i < map.length; i++) {
				if (map[i] != DISABLED_NOTE_ID
						&& !lotroInstrument.isPlayable(map[i], false)
						&& !isCombiNote(map[i])) {          // keep combo markers too
					if (failsafe == null) failsafe = getFailsafeDefault();
					map[i] = failsafe[i];
				}
			}
		}
	}

	/**
	 * Save a drummap belonging to a specific instrument on specific track to txt file.
	 */
	public void save(File outputFile) throws IOException {
		try (PrintStream outStream = new PrintStream(outputFile)) {
			save(outStream);
		}
	}

	/**
	 * Save a drummap belonging to a specific instrument on specific track to txt printstream.
	 */
	public void save(PrintStream out) {
		ensureMap();

		out.println("% LOTRO Drum Map");
		out.println("% Created using " + MaestroMain.APP_NAME + " v" + MaestroMain.APP_VERSION);
		out.println("%");
		out.println("% Format is: [MIDI Drum ID] => [LOTRO Drum ID]");
		out.println("% MIDI GM Drum IDs are in the range 35 to 81");
		out.println("% More MIDI Drum IDs is sometimes used though, like from 27 to 87");
		out.println("% Only MIDI drum notes between 0 and 127 will be accepted");
		out.format("%% LOTRO Drum IDs are in the range %d (%s) to %d (%s)", //
				Note.MIN_PLAYABLE.id, Note.MIN_PLAYABLE.abc, //
				Note.MAX_PLAYABLE.id, Note.MAX_PLAYABLE.abc);
		out.println();
		if (supportsCombis()) {
			ArrayList<Map.Entry<Note, LotroCombiDrumInfo.CombiDrumHit>> combiKeys = new ArrayList<>(combiInfo.libraryEntries());
			combiKeys.sort(java.util.Comparator.comparingInt(n -> n.getKey().id));
			if (!combiKeys.isEmpty()) {
				out.println();
				out.println("% Combi (Xtra) drums - each expands to two drum hits when played");
				out.println("% Format: %combi <markerId> = <drumId> + <drumId> : <name>");
				out.println("% Do not edit [builtin] combis, they cannot be changed anyway");
				out.println("% Older Maestro versions ignore %combi lines");
				out.println();
				for (Map.Entry<Note, LotroCombiDrumInfo.CombiDrumHit> e : combiKeys) {
					LotroCombiDrumInfo.CombiDrumHit c = e.getValue();
					String tag = c.locked() ? "  % [builtin]" : "";
					out.format("%%combi %d = %d + %d : %s%s%n",
							e.getKey().id, c.firstNote().id, c.secondNote().id,
							c.name() == null ? "" : c.name(), tag);
				}
				out.println();
			}
		}
		out.println("% A LOTRO Drum ID of -1 indicates that the drum is not mapped");
		out.println("% Comments begin with %, but %combi are not comments for Maestro v4.6.23 and later");
		out.println();

		int maxDrumLen = MidiDrum.INVALID.name.length();
		for (MidiDrum drum : MidiDrum.values()) {
			if (maxDrumLen < drum.name.length())
				maxDrumLen = drum.name.length();
		}

		for (int midiNoteId = 0; midiNoteId < map.length; midiNoteId++) {
			MidiDrum drum = MidiDrum.fromId(midiNoteId);

			// Only write non-drum IDs if they actually have a mapping
			if (drum == MidiDrum.INVALID && map[midiNoteId] == DISABLED_NOTE_ID)
				continue;

			Note note = Note.fromId(map[midiNoteId]);
			if (note == null)
				note = LotroDrumInfo.DISABLED.note;

			String drumName = drum.name;
			if (drumName.equals(MidiDrum.INVALID.name))
				drumName = "(" + drumName + ")";

			String label;
			LotroCombiDrumInfo.CombiDrumHit c = supportsCombis() ? resolveCombi(note.id) : null;
			if (c != null) {
				label = (c.name() != null && !c.name().isEmpty())
						? c.name() : ("combi " + c.firstNote().id + "+" + c.secondNote().id);
			} else {
				LotroDrumInfo d = LotroDrumInfo.getById(note.id);
				label = String.valueOf(d == null ? LotroDrumInfo.DISABLED : d);
			}
			out.format("%2d => %2d  %% %-" + maxDrumLen + "s => %s", midiNoteId, note.id, drumName, label);
			out.println();
		}
	}

	/**
	 * Load a drummap belonging to a specific instrument on specific track from txt file.
	 */
	public void load(File inputFile) throws IOException, FileParseException {
		try (FileInputStream inputStream = new FileInputStream(inputFile)) {
			load(inputStream, inputFile.getName());
		}
	}

	/**
	 * Load a drummap belonging to a specific instrument on specific track from txt inputstream.
	 */
	public void load(InputStream inputStream) throws IOException, FileParseException {
		load(inputStream, null);
	}

	/**
	 * Parse a single line from txt that contain a combi note.
	 */
	private void parseCombiDirective(String line, String fileName, int lineNumber, Map<Integer,Integer> fileMarkerRemap) throws FileParseException {
		// %combi <markerId> = <id1> + <id2> [ : name ]
		String body = line.substring(6).trim();
		String name = null;
		int colon = body.indexOf(':');
		if (colon >= 0) {
			name = body.substring(colon + 1).trim();
			int hash = name.indexOf('%');            // drop any trailing comment from the name
			if (hash >= 0) name = name.substring(0, hash).trim();
			body = body.substring(0, colon).trim();
		}
		int markerId, id1, id2;
		try {
			StringTokenizer t = new StringTokenizer(body, " \t=+");
			markerId = Integer.parseInt(t.nextToken());
			id1 = Integer.parseInt(t.nextToken());
			id2 = Integer.parseInt(t.nextToken());
			if (t.hasMoreTokens())
				throw new FileParseException("Invalid %combi (too many tokens)", fileName, lineNumber);
		} catch (NoSuchElementException nse) {
			throw new FileParseException("Invalid %combi (too few tokens)", fileName, lineNumber);
		} catch (NumberFormatException nfe) {
			throw new FileParseException("Invalid %combi note ID", fileName, lineNumber);
		}

		Note n1 = Note.fromId(id1);
		Note n2 = Note.fromId(id2);
		if (n1 == null || n2 == null)
			throw new FileParseException("Invalid %combi component note", fileName, lineNumber);


		Note marker = Note.fromId(markerId);
		if (marker == null || !LotroCombiDrumInfo.isValidKeyId(markerId))
			throw new FileParseException("Invalid %combi marker id", fileName, lineNumber);

		// Merge the pair into the library (dedups by content), get the library id it lives at,
		// and remap the file's marker onto it.
		Note builtin = combiInfo.libraryKeyForPair(n1, n2);   // built-ins are in library, locked
		Note libId = (builtin != null) ? builtin : combiInfo.mergeQuiet(n1, n2, name);
		if (libId != null) {
			fileMarkerRemap.put(markerId, libId.id);
		} else {
			// library full: degrade this assignment to the first component
			fileMarkerRemap.put(markerId, LotroInstrument.BASIC_DRUM.isPlayable((byte) id1) ? id1 : DISABLED_NOTE_ID);
			lastLoadCombiWarnings.add(name != null && !name.isEmpty() ? name : (id1 + "+" + id2));
		}
	}

	private void load(InputStream inputStream, String inputFileName) throws IOException, FileParseException {
		if (map == null)
			map = new byte[MidiConstants.NOTE_COUNT];
		Arrays.fill(map, DISABLED_NOTE_ID);
		Map<Integer,Integer> fileMarkerRemap = new HashMap<>();
		List<String> lines = new ArrayList<>();
		try (BufferedReader rdr = new BufferedReader(new InputStreamReader(inputStream))) {
			String l;
			while ((l = rdr.readLine()) != null)
				lines.add(l);
		}

		lastLoadCombiWarnings = new ArrayList<>();
		int lineNumber = 0;
		if (supportsCombis()) {
			for (String raw : lines) {
				lineNumber++;
				String t = raw.trim();
				if (t.length() >= 6 && t.regionMatches(true, 0, "%combi", 0, 6)) {
					parseCombiDirective(t, inputFileName, lineNumber, fileMarkerRemap);
				}
			}
		}

		// assignment lines.
		lineNumber = 0;
		for (String raw : lines) {
			lineNumber++;
			String line = raw;
			int commentIndex = line.indexOf('%');
			if (commentIndex >= 0)
				line = line.substring(0, commentIndex);
			line = line.trim();
			if (line.isEmpty())
				continue;

			byte midiNote;
			int lotroNote;
			try {
				StringTokenizer tokenizer = new StringTokenizer(line, " \t=>");
				midiNote = Byte.parseByte(tokenizer.nextToken());
				lotroNote = Integer.parseInt(tokenizer.nextToken());
				if (tokenizer.hasMoreTokens())
					throw new FileParseException("Invalid line (too many tokens)", inputFileName, lineNumber);
			} catch (NoSuchElementException nse) {
				throw new FileParseException("Invalid line (too few tokens)", inputFileName, lineNumber);
			} catch (NumberFormatException nfe) {
				throw new FileParseException("Invalid note ID", inputFileName, lineNumber);
			}

			Integer remapped = fileMarkerRemap.get(lotroNote);
			if (remapped != null) lotroNote = remapped;

			if (midiNote < MidiConstants.LOWEST_NOTE_ID || midiNote > MidiConstants.HIGHEST_NOTE_ID)
				throw new FileParseException("MIDI note is invalid", inputFileName, lineNumber);

			if (lotroNote < -1 || lotroNote > Note.MAX.id) {
				throw new FileParseException("ABC note is invalid", inputFileName, lineNumber);
			}
			if (lotroNote != DISABLED_NOTE_ID
					&& !getLotroInstrument().isPlayable(lotroNote, false)
					&& !isCombiNote(lotroNote)) {
				map[midiNote] = DISABLED_NOTE_ID;
				log.info("Note " + lotroNote + " in drummap is not mappable");
				continue;
			}

			map[midiNote] = (byte) lotroNote;
		}
		if (supportsCombis()) {
			combiInfo.saveLibrary();        // persist anything mergeQuiet added
			combiInfo.fireLibraryChanged(); // refresh dropdowns once
		}
		fireChangeEvent();
	}

	/**
	 * Get the last warnings encountered when loading a drummap from XML.
	 * Not used yet.
	 */
	public List<String> getLastLoadCombiWarnings() {
		return lastLoadCombiWarnings;
	}

	/**
	 * Save a drummap belonging to a specific instrument on specific track to XML.
	 */
	public void saveToXml(Element ele) {
		if (map == null) return;

		for (int midiId = 0; midiId < MidiConstants.NOTE_COUNT; midiId++) {
			int lotroId = get(midiId);
			if (lotroId == DISABLED_NOTE_ID) continue;

			Element noteEle = ele.getOwnerDocument().createElement("note");
			ele.appendChild(noteEle);
			noteEle.setAttribute("id", String.valueOf(midiId));

			if (isCombiNote(lotroId)) {
				LotroCombiDrumInfo.CombiDrumHit c = resolveCombi(lotroId);
				if (c != null) {
					if (c.locked()) {
						noteEle.setAttribute("lotroId", String.valueOf(lotroId));
					} else {
						noteEle.setAttribute("lotroId", String.valueOf(c.firstNote().id));
						noteEle.setAttribute("lotroId2", String.valueOf(c.secondNote().id));
						if (c.name() != null && !c.name().isEmpty())
							noteEle.setAttribute("combiName", c.name());
					}
				} else {
					noteEle.setAttribute("lotroId", String.valueOf(lotroId));
				}
			} else {
				noteEle.setAttribute("lotroId", String.valueOf(lotroId));
			}
		}
	}

	/**
	 * Load a drummap belonging to a specific instrument on specific track from XML.
	 */
	public static DrumNoteMap loadFromXml(Element ele, Version fileVersion, LotroCombiDrumInfo combiInfo) throws FileParseException {
		try {
			boolean isPassthrough = SaveUtil.parseValue(ele, "@isPassthrough", false);
			DrumNoteMap retVal = isPassthrough ? new PassThroughDrumNoteMap(combiInfo) : new DrumNoteMap(combiInfo);
			retVal.loadFromXmlInternal(ele, fileVersion, LotroInstrument.BASIC_DRUM);
			return retVal;
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Load a drummap belonging to a specific instrument on specific track from XML.
	 */
	protected void loadFromXmlInternal(Element ele, Version fileVersion, LotroInstrument lotroInstrument)
			throws FileParseException, XPathExpressionException {
		assert lotroInstrument == getLotroInstrument():"loadFromXmlInternal instrument mismatch";
		if (map == null) map = new byte[MidiConstants.NOTE_COUNT];
		Arrays.fill(map, DISABLED_NOTE_ID);

		for (Element noteEle : XmlUtil.selectElements(ele, "note")) {
			int midiId = SaveUtil.parseValue(noteEle, "@id", (int) DISABLED_NOTE_ID);
			int lotroId = SaveUtil.parseValue(noteEle, "@lotroId", (int) DISABLED_NOTE_ID);
			int combi2  = SaveUtil.parseValue(noteEle, "@lotroId2", (int) DISABLED_NOTE_ID);

			if (midiId < 0 || midiId >= map.length) continue;

			if (supportsCombis() && combi2 != DISABLED_NOTE_ID) {
				Note n1 = Note.fromId(lotroId), n2 = Note.fromId(combi2);
				if (n1 != null && n2 != null
						&& getLotroInstrument().isPlayable((byte) lotroId, false)
						&& getLotroInstrument().isPlayable((byte) combi2, false)) {
					// allocate a local marker for this pair (dedup within this map by content)
					String name = SaveUtil.parseValue(noteEle, "@combiName", (String) null);
					Note marker = combiInfo.mergeQuiet(n1, n2, name);
					map[midiId] = (marker != null) ? (byte) marker.id : (byte) lotroId;
					if (marker == null) lastLoadCombiWarnings.add("(library full) " + (name != null ? name : n1.id + "+" + n2.id));
				}
			} else if (getLotroInstrument().isPlayable((byte) lotroId, false) || isCombiNote(lotroId)) {
				map[midiId] = (byte) lotroId;
			}
		}
		if (supportsCombis()) {
			combiInfo.saveLibrary();
			combiInfo.fireLibraryChanged();
		}
		// fireChangeEvent(); should not be needed as song is still setting up.
	}

	/**
	 * This can be used as a backup in the event that loading the drum map from a file fails.
	 */
	public byte[] getFailsafeDefault() {
		byte[] failsafe = new byte[MidiConstants.NOTE_COUNT];

		Arrays.fill(failsafe, DISABLED_NOTE_ID);

		failsafe[26] = 49;
		failsafe[27] = 72;
		failsafe[28] = 70;
		// failsafe[29] = DISABLED_NOTE_ID;
		// failsafe[30] = DISABLED_NOTE_ID;
		failsafe[31] = 51;
		failsafe[32] = 50;
		failsafe[33] = 39;
		// failsafe[34] = DISABLED_NOTE_ID;
		failsafe[35] = 49;
		failsafe[36] = 58;
		failsafe[37] = 51;
		failsafe[38] = 52;
		failsafe[39] = 53;
		failsafe[40] = 54;
		failsafe[41] = 49;
		failsafe[42] = 37;
		failsafe[43] = 69;
		failsafe[44] = 59;
		failsafe[45] = 47;
		failsafe[46] = 60;
		failsafe[47] = 63;
		failsafe[48] = 43;
		failsafe[49] = 57;
		failsafe[50] = 45;
		failsafe[51] = 55;
		failsafe[52] = 57;
		failsafe[53] = 43;
		failsafe[54] = 46;
		failsafe[55] = 57;
		failsafe[56] = 45;
		failsafe[57] = 57;
		failsafe[58] = 53;
		failsafe[59] = 60;
		failsafe[60] = 38;
		failsafe[61] = 69;
		failsafe[62] = 39;
		failsafe[63] = 70;
		failsafe[64] = 48;
		failsafe[65] = 65;
		failsafe[66] = 64;
		failsafe[67] = 43;
		failsafe[68] = 47;
		failsafe[69] = 37;
		failsafe[70] = 42;
		// failsafe[71] = DISABLED_NOTE_ID;
		// failsafe[72] = DISABLED_NOTE_ID;
		failsafe[73] = 64;
		failsafe[74] = 62;
		failsafe[75] = 43;
		failsafe[76] = 51;
		failsafe[77] = 67;
		failsafe[78] = 65;
		failsafe[79] = 64;
		failsafe[80] = 43;
		failsafe[81] = 43;
		failsafe[82] = 42;
		failsafe[83] = 44;
		// failsafe[84] = DISABLED_NOTE_ID;
		failsafe[85] = 72;
		failsafe[86] = 48;
		failsafe[87] = 58;

		return failsafe;
	}
}
