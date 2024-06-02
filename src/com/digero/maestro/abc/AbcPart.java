package com.digero.maestro.abc;

import static com.digero.maestro.abc.AbcHelper.map;
import static com.digero.maestro.abc.AbcHelper.matchNick;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.prefs.Preferences;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.digero.common.abc.Dynamics;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentSampleDuration;
import com.digero.common.midi.ITempoCache;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiDrum;
import com.digero.common.midi.Note;
import com.digero.common.util.IDiscardable;
import com.digero.common.util.Listener;
import com.digero.common.util.ListenerList;
import com.digero.common.util.Pair;
import com.digero.common.util.ParseException;
import com.digero.common.util.Version;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcSongEvent.AbcSongProperty;
import com.digero.maestro.midi.AbcNoteEvent;
import com.digero.maestro.midi.BentMidiNoteEvent;
import com.digero.maestro.midi.MidiNoteEvent;
import com.digero.maestro.midi.NoteEvent;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.util.SaveUtil;
import com.digero.maestro.util.XmlUtil;
import com.digero.maestro.view.InstrNameSettings;

public class AbcPart implements AbcPartMetadataSource, NumberedAbcPart, IDiscardable {
	private int partNumber = 1;
	private String title;
	private LotroInstrument instrument;
	private int[] trackTranspose;
	private boolean[] trackEnabled;
	private boolean[] trackPriority;
	public boolean[] playLeft;
	public boolean[] playCenter;
	public boolean[] playRight;
	public Note[] from;
	public Note[] to;
	private int[] trackVolumeAdjust;
	private DrumNoteMap[] drumNoteMap;
	private StudentFXNoteMap[] fxNoteMap;
	private BitSet[] drumsEnabled;
	private BitSet[] cowbellsEnabled;
	private BitSet[] fxEnabled;

	private final AbcSong abcSong;
	private int enabledTrackCount = 0;
	private int previewSequenceTrackNumber = -1;
	private final ListenerList<AbcPartEvent> listeners = new ListenerList<>();
	private Preferences drumPrefs = Preferences.userNodeForPackage(AbcPart.class).node("drums");

	public List<TreeMap<Integer, PartSection>> sections;
	public List<PartSection> nonSection;
	public List<boolean[]> sectionsModified;
	public int delay = 0;// ms
	private int typeNumber = 0;// -1 for when instr do not match or string dont start with instr, 0 when instr
								// match but no number, positive number when it has number.
	private final InstrNameSettings instrNameSettings;
	private boolean muted = false;
	private boolean soloed = false;
	
	public boolean discarded = false;
	
	private static final Note minDefault = Note.C0;//limit

	public AbcPart(AbcSong abcSong) {
		this.abcSong = abcSong;
		abcSong.addSongListener(songListener);
		this.instrument = LotroInstrument.DEFAULT_INSTRUMENT;
		this.instrNameSettings = abcSong.getInstrNameSettings();
		this.title = instrNameSettings.getInstrNick(instrument);

		int t = getTrackCount();
		this.trackTranspose = new int[t];
		this.trackEnabled = new boolean[t];
		this.trackPriority = new boolean[t];
		this.playLeft = new boolean[t];
		this.playCenter = new boolean[t];
		this.playRight = new boolean[t];

		this.trackVolumeAdjust = new int[t];
		this.drumNoteMap = new DrumNoteMap[t];
		this.fxNoteMap = new StudentFXNoteMap[t];
		this.sections = new ArrayList<>();
		this.nonSection = new ArrayList<>();
		this.sectionsModified = new ArrayList<>();
		this.from = new Note[t];
		this.to = new Note[t];
		for (int i = 0; i < t; i++) {
			this.sections.add(null);
			this.nonSection.add(null);
			this.sectionsModified.add(null);
			this.playLeft[i] = true;
			this.playCenter[i] = true;
			this.playRight[i] = true;
			this.from[i] = minDefault;
			this.to[i] = Note.MAX;
		}
	}

	public AbcPart(AbcSong abcSong, Element loadFrom) {
		this(abcSong);
	}

	@Override
	public void discard() {
		abcSong.removeSongListener(songListener);
		listeners.discard();
		for (int i = 0; i < drumNoteMap.length; i++) {
			if (drumNoteMap[i] != null) {
				drumNoteMap[i].removeChangeListener(drumMapChangeListener);
				drumNoteMap[i] = null;
			}
		}
		for (int i = 0; i < fxNoteMap.length; i++) {
			if (fxNoteMap[i] != null) {
				fxNoteMap[i].removeChangeListener(drumMapChangeListener);
				fxNoteMap[i] = null;
			}
		}
		sections = null;
		sectionsModified = null;
		delay = 0;
		discarded = true;
	}

	public void saveToXml(Element ele) {
		Document doc = ele.getOwnerDocument();

		ele.setAttribute("id", String.valueOf(partNumber));
		SaveUtil.appendChildTextElement(ele, "title", String.valueOf(title));
		SaveUtil.appendChildTextElement(ele, "instrument", String.valueOf(instrument));
		if (delay != 0) {
			SaveUtil.appendChildTextElement(ele, "delay", String.valueOf(delay));
		}
		for (int t = 0; t < getTrackCount(); t++) {
			if (!isTrackEnabled(t))
				continue;

			TrackInfo trackInfo = abcSong.getSequenceInfo().getTrackInfo(t);

			Element trackEle = (Element) ele.appendChild(doc.createElement("track"));
			trackEle.setAttribute("id", String.valueOf(t));
			if (trackInfo.hasName())
				trackEle.setAttribute("name", trackInfo.getName());

			if (trackTranspose[t] != 0)
				SaveUtil.appendChildTextElement(trackEle, "transpose", String.valueOf(trackTranspose[t]));
			if (trackVolumeAdjust[t] != 0)
				SaveUtil.appendChildTextElement(trackEle, "volumeAdjust", String.valueOf(trackVolumeAdjust[t]));
			if (abcSong.isMixTiming() && abcSong.isPriorityActive() && trackPriority[t])
				SaveUtil.appendChildTextElement(trackEle, "combinePriority",
						String.valueOf(QuantizedTimingInfo.COMBINE_PRIORITY_MULTIPLIER));// Hardcoded
																							// to
																							// 4
																							// for
																							// now,
																							// change
																							// QTM
																							// and
																							// UI
																							// if
																							// messing
																							// with
																							// this

			if (!playLeft[t])
				trackEle.setAttribute("playLeft", String.valueOf(playLeft[t]));
			if (!playCenter[t])
				trackEle.setAttribute("playCenter", String.valueOf(playCenter[t]));
			if (!playRight[t])
				trackEle.setAttribute("playRight", String.valueOf(playRight[t]));
			
			if (from[t] != minDefault)
				trackEle.setAttribute("from", String.valueOf(from[t].id));
			if (to[t] != Note.MAX)
				trackEle.setAttribute("to", String.valueOf(to[t].id));
			
			
			TreeMap<Integer, PartSection> tree = sections.get(t);
			if (tree != null) {
				for (Entry<Integer, PartSection> entry : tree.entrySet()) {
					PartSection ps = entry.getValue();
					Element sectionEle = (Element) trackEle.appendChild(doc.createElement("section"));
					SaveUtil.appendChildTextElement(sectionEle, "startBar", String.valueOf(ps.startBar));
					SaveUtil.appendChildTextElement(sectionEle, "endBar", String.valueOf(ps.endBar));
					if (!instrument.isPercussion) {
						SaveUtil.appendChildTextElement(sectionEle, "octaveStep", String.valueOf(ps.octaveStep));
					}
					SaveUtil.appendChildTextElement(sectionEle, "volumeStep", String.valueOf(ps.volumeStep));
					SaveUtil.appendChildTextElement(sectionEle, "silence", String.valueOf(ps.silence));
					SaveUtil.appendChildTextElement(sectionEle, "fade", String.valueOf(ps.fade));
					SaveUtil.appendChildTextElement(sectionEle, "dialogLine", String.valueOf(ps.dialogLine));
					SaveUtil.appendChildTextElement(sectionEle, "resetVelocities", String.valueOf(ps.resetVelocities));
					AbcHelper.appendIfNotPercussion(ps, sectionEle, instrument.isPercussion);
				}
			}

			if (nonSection.get(t) != null) {
				PartSection ps = nonSection.get(t);
				Element sectionEle = (Element) trackEle.appendChild(doc.createElement("nonSection"));
				SaveUtil.appendChildTextElement(sectionEle, "silence", String.valueOf(ps.silence));
				SaveUtil.appendChildTextElement(sectionEle, "resetVelocities", String.valueOf(ps.resetVelocities));
				AbcHelper.appendIfNotPercussion(ps, sectionEle, instrument.isPercussion);
			}

			if (instrument.isPercussion) {
				calculateEnabledSet(ele, doc, t, trackEle);
			}
		}
	}

	private void calculateEnabledSet(Element ele, Document doc, int t, Element trackEle) {
		BitSet[] enabledSetByTrack = isCowbellPart() ? cowbellsEnabled : isFXPart() ? fxEnabled : drumsEnabled;
		BitSet enabledSet = (enabledSetByTrack == null) ? null : enabledSetByTrack[t];
		if (enabledSet != null) {
			Element drumsEnabledEle = ele.getOwnerDocument().createElement("drumsEnabled");
			trackEle.appendChild(drumsEnabledEle);

			if (isCowbellPart()) {
				drumsEnabledEle.setAttribute("defaultEnabled", String.valueOf(false));

				// Only store the drums that are enabled
				for (int i = enabledSet.nextSetBit(0); i >= 0; i = enabledSet.nextSetBit(i + 1)) {
					Element drumEle = ele.getOwnerDocument().createElement("note");
					drumsEnabledEle.appendChild(drumEle);
					drumEle.setAttribute("id", String.valueOf(i));
					drumEle.setAttribute("isEnabled", String.valueOf(true));
				}
			} else if (isFXPart()) {
				storeDisabledDrums(ele, enabledSet, drumsEnabledEle);
			} else {
				storeDisabledDrums(ele, enabledSet, drumsEnabledEle);
			}
		}

		if (!isCowbellPart()) {
			if (!isFXPart() && drumNoteMap[t] != null)
				drumNoteMap[t]
						.saveToXml((Element) trackEle.appendChild(doc.createElement(drumNoteMap[t].getXmlName())));
			if (isFXPart() && fxNoteMap[t] != null)
				fxNoteMap[t].saveToXml((Element) trackEle.appendChild(doc.createElement(fxNoteMap[t].getXmlName())));
		}
	}

	private void storeDisabledDrums(Element ele, BitSet enabledSet, Element drumsEnabledEle) {
		drumsEnabledEle.setAttribute("defaultEnabled", String.valueOf(true));

		// Only store the drums that are disabled
		for (int i = enabledSet.nextClearBit(0); i >= 0; i = enabledSet.nextClearBit(i + 1)) {
			if (i >= MidiConstants.NOTE_COUNT)
				break;

			Element drumEle = ele.getOwnerDocument().createElement("note");
			drumsEnabledEle.appendChild(drumEle);
			drumEle.setAttribute("id", String.valueOf(i));
			drumEle.setAttribute("isEnabled", String.valueOf(false));
		}
	}

	public static AbcPart loadFromXml(AbcSong abcSong, Element ele, Version fileVersion) throws ParseException {
		AbcPart part = new AbcPart(abcSong);
		part.initFromXml(ele, fileVersion);
		return part;
	}

	private void initFromXml(Element ele, Version fileVersion) throws ParseException {
		try {
			partNumber = SaveUtil.parseValue(ele, "@id", partNumber);
			title = SaveUtil.parseValue(ele, "title", title);
			instrument = SaveUtil.parseValue(ele, "instrument", instrument);
			typeNumber = getTypeNumberMatchingTitle();// must be after instr and title
			delay = SaveUtil.parseValue(ele, "delay", 0);

			for (Element trackEle : XmlUtil.selectElements(ele, "track")) {

				// Try to find the specified track in the midi sequence by name, in case it
				// moved
				int t = findTrackNumberByName(SaveUtil.parseValue(trackEle, "@name", ""));
				// Fall back to the track ID if that didn't work
				if (t == -1)
					t = SaveUtil.parseValue(trackEle, "@id", -1);

				if (t < 0 || t >= getTrackCount()) {
					String optionalName = SaveUtil.parseValue(trackEle, "@name", "");

					if (optionalName.length() > 0) {
						optionalName = " (" + optionalName + ")";
					}

					throw SaveUtil.invalidTrackException(trackEle,
							"Could not find track number " + t + optionalName + " in original MIDI file");
				}

				TreeMap<Integer, PartSection> tree = sections.get(t);
				int lastEnd = 0;
				for (Element sectionEle : XmlUtil.selectElements(trackEle, "section")) {
					PartSection ps = AbcHelper.generatePartSection(sectionEle);
					if (ps.startBar > 0 && ps.endBar >= ps.startBar) {// && (ps.volumeStep != 0 || ps.octaveStep != 0 ||
																		// ps.silence || ps.fadeout)
						if (tree == null) {
							tree = new TreeMap<>();
							sections.set(t, tree);
						}
						if (ps.endBar > lastEnd) {
							lastEnd = ps.endBar;
						}
						tree.put(ps.startBar, ps);
					}
				}
				boolean[] booleanArray = new boolean[lastEnd + 1];
				if (tree != null) {
					for (int i = 0; i < lastEnd + 1; i++) {
						Entry<Integer, PartSection> entry = tree.floorEntry(i + 1);
						booleanArray[i] = entry != null && entry.getValue().startBar <= i + 1
								&& entry.getValue().endBar >= i + 1;
					}

					sectionsModified.set(t, booleanArray);
				}

				Element nonSectionEle = XmlUtil.selectSingleElement(trackEle, "nonSection");
				if (nonSectionEle != null) {
					PartSection ps = new PartSection();
					ps.silence = SaveUtil.parseValue(nonSectionEle, "silence", false);
					ps.resetVelocities = SaveUtil.parseValue(nonSectionEle, "resetVelocities", false);
					ps.doubling[0] = SaveUtil.parseValue(nonSectionEle, "double2OctDown", false);
					ps.doubling[1] = SaveUtil.parseValue(nonSectionEle, "double1OctDown", false);
					ps.doubling[2] = SaveUtil.parseValue(nonSectionEle, "double1OctUp", false);
					ps.doubling[3] = SaveUtil.parseValue(nonSectionEle, "double2OctUp", false);
					nonSection.set(t, ps);
				}

				// Now set the track info
				trackEnabled[t] = true;
				enabledTrackCount++;
				trackTranspose[t] = SaveUtil.parseValue(trackEle, "transpose", trackTranspose[t]);
				trackVolumeAdjust[t] = SaveUtil.parseValue(trackEle, "volumeAdjust", trackVolumeAdjust[t]);
				int prio = SaveUtil.parseValue(trackEle, "combinePriority", 1);
				if (prio == QuantizedTimingInfo.COMBINE_PRIORITY_MULTIPLIER) {
					// Hardcoded to 4 for now, change QTM and UI if messing with this
					trackPriority[t] = true;
				}
				playLeft[t] = SaveUtil.parseValue(trackEle, "@playLeft", true);
				playCenter[t] = SaveUtil.parseValue(trackEle, "@playCenter", true);
				playRight[t] = SaveUtil.parseValue(trackEle, "@playRight", true);

				from[t] = Note.fromId(SaveUtil.parseValue(trackEle, "@from", minDefault.id));
				to[t] = Note.fromId(SaveUtil.parseValue(trackEle, "@to", Note.MAX.id));
				
				if (instrument.isPercussion) {
					handlePercussion(fileVersion, trackEle, t);
				}
			}
		} catch (XPathExpressionException e) {
			throw new ParseException("XPath error: " + e.getMessage(), null);
		}
	}

	private void handlePercussion(Version fileVersion, Element trackEle, int t)
			throws XPathExpressionException, ParseException {
		Element drumsEle = XmlUtil.selectSingleElement(trackEle, "drumsEnabled");
		if (drumsEle != null) {
			calculateEnabledSet(t, drumsEle);
		}

		Element drumMapEle = XmlUtil.selectSingleElement(trackEle, "drumMap");
		if (drumMapEle != null) {
			drumNoteMap[t] = DrumNoteMap.loadFromXml(drumMapEle, fileVersion);
			if (drumNoteMap[t] != null)
				drumNoteMap[t].addChangeListener(drumMapChangeListener);
		}
		drumMapEle = XmlUtil.selectSingleElement(trackEle, "fxMap");
		if (drumMapEle != null) {
			fxNoteMap[t] = StudentFXNoteMap.loadFromXml(drumMapEle, fileVersion);
			if (fxNoteMap[t] != null)
				fxNoteMap[t].addChangeListener(drumMapChangeListener);
		}
	}

	private void calculateEnabledSet(int t, Element drumsEle) throws ParseException, XPathExpressionException {
		boolean defaultEnabled = SaveUtil.parseValue(drumsEle, "@defaultEnabled", !isCowbellPart());

		BitSet[] enabledSet;
		if (isCowbellPart()) {
			if (cowbellsEnabled == null)
				cowbellsEnabled = new BitSet[getTrackCount()];
			enabledSet = cowbellsEnabled;
		} else if (isFXPart()) {
			if (fxEnabled == null)
				fxEnabled = new BitSet[getTrackCount()];
			enabledSet = fxEnabled;
		} else {
			if (drumsEnabled == null)
				drumsEnabled = new BitSet[getTrackCount()];
			enabledSet = drumsEnabled;
		}

		enabledSet[t] = new BitSet(MidiConstants.NOTE_COUNT);
		if (defaultEnabled)
			enabledSet[t].set(0, MidiConstants.NOTE_COUNT, true);

		for (Element drumEle : XmlUtil.selectElements(drumsEle, "note")) {
			int id = SaveUtil.parseValue(drumEle, "@id", -1);
			if (id >= 0 && id < MidiConstants.NOTE_COUNT)
				enabledSet[t].set(id, SaveUtil.parseValue(drumEle, "@isEnabled", !defaultEnabled));
		}
	}

	private int findTrackNumberByName(String trackName) {
		if (trackName.equals(""))
			return -1;

		int namedTrackNumber = -1;
		for (TrackInfo trackInfo : abcSong.getSequenceInfo().getTrackList()) {
			if (trackInfo.hasName() && trackName.equals(trackInfo.getName())) {
				if (namedTrackNumber == -1) {
					namedTrackNumber = trackInfo.getTrackNumber();
				} else {
					// Found multiple tracks with the same name; don't know which one it could be
					return -1;
				}
			}
		}
		return namedTrackNumber;
	}

	private Listener<AbcSongEvent> songListener = e -> {
		if (e.getProperty() == AbcSongProperty.TRANSPOSE) {
			fireChangeEvent(AbcPartProperty.BASE_TRANSPOSE, !isPercussionPart() /* affectsAbcPreview */);
		}
		if (e.getProperty() == AbcSongProperty.MIX_TIMING_COMBINE_PRIORITIES
				|| e.getProperty() == AbcSongProperty.MIX_TIMING) {
			fireChangeEvent(AbcPartProperty.TRACK_PRIORITY);
		}
	};

	public List<MidiNoteEvent> getTrackEvents(int track) {
		return abcSong.getSequenceInfo().getTrackInfo(track).getEvents();
	}

	/**
	 * Maps from a MIDI note to an ABC note. If no mapping is available, returns <code>null</code>.
	 * 
	 * Notice this method does not work for bent notes, use mapNoteEvent for those.
	 */
	public Note mapNote(int track, int noteId, long tickStart) {
		if (!isTrackEnabled(track))
			return Note.fromId(noteId);
		if (!getAudible(track, tickStart)) {
			return null;
		}
		if (isPercussionPart()) {
			if (!isTrackEnabled(track) || !isDrumEnabled(track, noteId))
				return null;

			int dstNote;
			if (instrument == LotroInstrument.BASIC_COWBELL)
				dstNote = Note.G2.id; // "Tom High 1"
			else if (instrument == LotroInstrument.MOOR_COWBELL)
				dstNote = Note.A2.id; // "Tom High 2"
			else if (instrument == LotroInstrument.STUDENT_FX_FIDDLE)
				dstNote = getFXMap(track).get(noteId);
			else
				dstNote = getDrumMap(track).get(noteId);

			return (dstNote == LotroDrumInfo.DISABLED.note.id) ? null : Note.fromId(dstNote);
		} else {
			noteId += getTranspose(track, tickStart);
			if (noteId > to[track].id || noteId < from[track].id) {
				return null;
			}
			while (noteId < instrument.lowestPlayable.id)
				noteId += 12;
			while (noteId > instrument.highestPlayable.id)
				noteId -= 12;
			return Note.fromId(noteId);
		}
	}
	
	/**
	 * Maps from a MIDI note to an ABC note. If no mapping is available, returns <code>null</code>.
	 * 
	 * This method will also handle bent notes.
	 * 
	 * @param track track
	 * @param ne noteevent
	 * @return
	 */
	public Note mapNoteEvent(int track, NoteEvent ne) {
		return mapNoteEvent(track, ne, ne.note.id);
	}
	
	/**
	 * Maps from a MIDI note to an ABC note. If no mapping is available, returns <code>null</code>.
	 * 
	 * This method will also handle bent notes.
	 * 
	 * @param track track
	 * @param ne noteevent
	 * @param noteId use a custom note id
	 * @return
	 */
	public Note mapNoteEvent(int track, NoteEvent ne, int noteId) {
		if (!isTrackEnabled(track))
			return ne.note;
		long tickStart = ne.getStartTick();
		if (!getAudible(track, tickStart)) {
			return null;
		}
		if (isPercussionPart()) {
			if (!isTrackEnabled(track) || !isDrumEnabled(track, noteId))
				return null;

			int dstNote;
			if (instrument == LotroInstrument.BASIC_COWBELL)
				dstNote = Note.G2.id; // "Tom High 1"
			else if (instrument == LotroInstrument.MOOR_COWBELL)
				dstNote = Note.A2.id; // "Tom High 2"
			else if (instrument == LotroInstrument.STUDENT_FX_FIDDLE)
				dstNote = getFXMap(track).get(noteId);
			else
				dstNote = getDrumMap(track).get(noteId);

			return (dstNote == LotroDrumInfo.DISABLED.note.id) ? null : Note.fromId(dstNote);
		} else if (ne instanceof BentMidiNoteEvent) {
			BentMidiNoteEvent be = (BentMidiNoteEvent) ne;
			int minBend = be.getMinBend();
			int maxBend = be.getMaxBend();
			int transpose = getTranspose(track, tickStart);

			noteId += transpose;
			minBend += ne.note.id + transpose;
			maxBend += ne.note.id + transpose;
			
			if (minBend + getInstrument().octaveDelta * 12 > to[track].id || minBend + getInstrument().octaveDelta * 12 < from[track].id) {
				return null;
			}

			while (noteId < instrument.lowestPlayable.id) {
				noteId += 12;
			}
			while (noteId > instrument.highestPlayable.id) {
				noteId -= 12;
			}

			int octaveFittingMin = 0;
			while (minBend < instrument.lowestPlayable.id) {
				minBend += 12;
				octaveFittingMin += 12;
			}
			while (minBend > instrument.highestPlayable.id) {
				minBend -= 12;
				octaveFittingMin -= 12;
			}

			int octaveFittingMax = 0;
			while (maxBend < instrument.lowestPlayable.id) {
				maxBend += 12;
				octaveFittingMax += 12;
			}
			while (maxBend > instrument.highestPlayable.id) {
				maxBend -= 12;
				octaveFittingMax -= 12;
			}

			// We transpose the entire bent note into
			// the playable range as one coherent block of notes.

			if (octaveFittingMax < 0) {
				noteId = ne.note.id + transpose;
				noteId += octaveFittingMax;
			} else if (octaveFittingMin > 0) {
				noteId = ne.note.id + transpose;
				noteId += octaveFittingMin;
			}

			return Note.fromId(noteId);
		} else {
			noteId += getTranspose(track, tickStart);
			if (noteId + getInstrument().octaveDelta * 12 > to[track].id || noteId + getInstrument().octaveDelta * 12 < from[track].id) {
				return null;
			}
			while (noteId < instrument.lowestPlayable.id)
				noteId += 12;
			while (noteId > instrument.highestPlayable.id)
				noteId -= 12;
			return Note.fromId(noteId);
		}
	}

	public boolean shouldPlay(NoteEvent ne, int track) {
		if (!isTrackEnabled(track))
			return true;
		if (ne.note == Note.REST) return true;
		if (ne instanceof AbcNoteEvent) {
			ne = ((AbcNoteEvent) ne).origNote;
		}
		MidiNoteEvent mne = (MidiNoteEvent) ne;
		
		if (!playCenter[track] && mne.midiPan == MidiConstants.PAN_CENTER) {
			return false;
		}
		if (!playLeft[track] && mne.midiPan < MidiConstants.PAN_CENTER) {
			return false;
		}
		if (!playRight[track] && mne.midiPan > MidiConstants.PAN_CENTER) {
			return false;
		}
		
		return true;
	}

	/**
	 * 
	 * @param track
	 * @param noteId
	 * @param tickStart
	 * @return Return the note id the note would have had if the instrument did not a have range limit.
	 */
	public int mapNoteFullOctaves(int track, int noteId, long tickStart) {
		noteId += getTranspose(track, tickStart);
		return noteId;
	}

	public long firstNoteStartTick() {
		long startTick = Long.MAX_VALUE;

		for (int t = 0; t < getTrackCount(); t++) {
			if (isTrackEnabled(t)) {
				for (MidiNoteEvent ne : getTrackEvents(t)) {
					if (mapNoteEvent(t, ne) != null && shouldPlay(ne, t)) {
						if (ne.getStartTick() < startTick)
							startTick = ne.getStartTick();
						break;
					}
				}
			}
		}

		if (startTick == Long.MAX_VALUE)
			startTick = 0;

		return startTick;
	}

	public long lastNoteEndTick(boolean accountForSustain, float exportTempoFactor) {
		long endTick = Long.MIN_VALUE;

		// The last note to start playing isn't necessarily the last note to end.
		// Check the last several notes to find the one that ends last.

		for (int t = 0; t < getTrackCount(); t++) {
			if (isTrackEnabled(t)) {
				int notesToCheck = 500;
				List<MidiNoteEvent> evts = getTrackEvents(t);
				ListIterator<MidiNoteEvent> iter = evts.listIterator(evts.size());
				while (iter.hasPrevious()) {
					MidiNoteEvent ne = iter.previous();
					Note tone = mapNoteEvent(t, ne);
					if (tone != null && shouldPlay(ne, t)) {
						long noteEndTick;
						if (!accountForSustain || instrument.isSustainable(tone.id)) {
							noteEndTick = ne.getEndTick();
						} else {
							double dura = 1.0;
							try {
								dura = LotroInstrumentSampleDuration.getDura(getInstrument().friendlyName, tone.id);								
							} catch (IOException | NullPointerException e) {
								// will give null pointer if tone is not contained in the map, in conversion from Double to double.
							}
							ITempoCache tc = ne.getTempoCache();
							noteEndTick = tc
									.microsToTick(tc.tickToMicros(ne.getStartTick()) + (long)(TimingInfo.ONE_SECOND_MICROS*dura*exportTempoFactor));
						}

						if (noteEndTick > endTick)
							endTick = noteEndTick;

						if (--notesToCheck <= 0)
							break;
					}
				}
			}
		}

		return endTick;
	}

	public AbcSong getAbcSong() {
		return abcSong;
	}

	public SequenceInfo getSequenceInfo() {
		return abcSong.getSequenceInfo();
	}

	public int getTrackCount() {
		return abcSong.getSequenceInfo().getTrackCount();
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public String toString() {
		String val = getPartNumber() + ". " + getTitle();
		if (getEnabledTrackCount() == 0)
			val += "*";
		return val;
	}

	public void setTitle(String name) {
		if (name == null)
			throw new NullPointerException();

		if (!this.title.equals(name)) {
			this.title = name;
			if (!isTypeNumberMatchingTitle()) {
				typeNumber = getTypeNumberMatchingTitle();
			}
			fireChangeEvent(AbcPartProperty.TITLE);
		}
	}

	public void replaceTitleInstrument(LotroInstrument replacement, LotroInstrument previous) {
		stripTypeNumber();
		Pair<LotroInstrument, MatchResult> result = LotroInstrument.matchInstrument(title);
		String replacementName = instrNameSettings.getInstrNick(replacement);
		if (result == null) {
			Integer[] result2 = matchNick(instrNameSettings.getInstrNick(previous), title);
			if (result2 != null) {
				if (isTypeNumberMatchingTitle() && typeNumber != -1) {
					typeNumber = 0;
					setTitle(replacementName);
				} else {
					setTitle(title.substring(0, result2[0]) + replacementName + title.substring(result2[1]));
				}
				return;
			}
			// No instrument currently in title
			if (title.isEmpty())
				setTitle(replacementName);
			else {
				setTitle(replacementName + " " + title);
			}
		} else {
			MatchResult m = result.second;
			if (isTypeNumberMatchingTitle() && typeNumber != -1) {
				typeNumber = 0;
				setTitle(replacementName);
			} else {
				setTitle(title.substring(0, m.start()) + replacementName + title.substring(m.end()));
			}
		}
	}

	public int getTypeNumber() {
		return typeNumber;
	}

	public boolean setTypeNumber(int typeNumberNew) {
		if (!isTypeNumberMatchingTitle()) {
			int potentialOld = getTypeNumberMatchingTitle();
			typeNumber = potentialOld;
			if (potentialOld == -1) {
				// System.out.println(" "+"Modified, setting -1");
				return typeNumber == typeNumberNew;
			} else {
				// System.out.println(" "+"Potential Old is "+potentialOld);
			}
		} else if (typeNumber == -1) {
			// System.out.println(" "+"Modified, keeping -1");
			return typeNumber == typeNumberNew;
		} else {
			// System.out.println(" "+"matching old title at least: "+typeNumber);
		}
		if (typeNumberNew != typeNumber) {
			Pair<LotroInstrument, MatchResult> result = LotroInstrument.matchInstrument(title);

			String typeString = " " + typeNumberNew;
			if (typeNumberNew == 0) {
				typeString = "";
			}
			// System.out.println(" "+"Setting: "+result.second.group()+typeString);
			typeNumber = typeNumberNew;
			setTitle(result.second.group() + typeString);// no need to check for null, as that is done in
															// isTypeNumberMatchingTitle/getTypeNumberMatchingTitle
		} else {
			// System.out.println(" "+"Same, not setting "+typeNumber);
		}
		return true;
	}

	public void stripTypeNumber() {
		if (typeNumber != 0 && isTypeNumberMatchingTitle()) {
			StringBuilder regex = new StringBuilder();

			String typeString = " " + getTypeNumber();

			regex.append("\\b(?:");
			regex.append('(');
			regex.append((typeString).replace(" ", "[\\s_]*"));
			regex.append(')');
			regex.append(")\\b");

			Pattern typeRegex = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
			Matcher m = typeRegex.matcher(getTitle());
			MatchResult last = null;
			// Iterate through the matches to find the last one
			for (int i = 0; m.find(i); i = m.end())
				last = m.toMatchResult();

			if (last == null)
				return;
			setTitle(getTitle().substring(0, last.start()));
		}
	}

	/**
	 * 
	 * @return -1 for when instr do not match or string dont start with instr, 0 when instr match but no postfix,
	 *         positive number when it has number.
	 */
	public int getTypeNumberMatchingTitle() {
		Pair<LotroInstrument, MatchResult> result = LotroInstrument.matchInstrument(title);

		if (result == null) {
			Integer[] result2 = matchNick(instrNameSettings.getInstrNick(instrument), title);
			if (result2 != null) {
				if (result2[0] != 0)
					return -1;
				String ending = title.substring(result2[1]);

				if (ending.length() == 0)
					return 0;

				try {
					int endsWith = Integer.parseInt(ending.trim());
					return endsWith;
				} catch (NumberFormatException e) {
					return -1;
				}
			}
			return -1;
		} else if (result.first.equals(instrument)) {
			if (result.second.start() != 0)
				return -1;

			String ending = title.substring(result.second.end());

			if (ending.length() == 0)
				return 0;

			try {
				int endsWith = Integer.parseInt(ending.trim());
				return endsWith;
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		return -1;
	}

	public boolean isTypeNumberMatchingTitle() {
		return typeNumber == getTypeNumberMatchingTitle();
		/*
		 * Pair<LotroInstrument, MatchResult> result = LotroInstrument.matchInstrument(title);
		 * 
		 * if (result == null) { System.out.println("    "+getTitle()+" has no instr match"); return false; } else if
		 * (result.first.equals(instrument)) { StringBuilder regex = new StringBuilder(); String typeString =
		 * " "+getTypeNumber();
		 * 
		 * regex.append("\\b(?:"); regex.append('('); regex.append((result.second.group()+typeString).replace(" ",
		 * "[\\s_]*")); regex.append(')'); regex.append(")\\b");
		 * 
		 * Pattern typeRegex = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE); Matcher m =
		 * typeRegex.matcher(getTitle()); MatchResult last = null; // Iterate through the matches to find the last one
		 * for (int i = 0; m.find(i); i = m.end()) last = m.toMatchResult();
		 * 
		 * if (last == null) System.out.println("    "+getTitle()+"    last==null"); else
		 * System.out.println("    "+getTitle()+"    last.start():"+last.start()
		 * +" last.end():"+last.end()+" title.length:"+getTitle().length()); if (last != null && last.start() == 0 &&
		 * last.end() == getTitle().length()) { return true; } } return false;
		 */
	}

	@Override
	public LotroInstrument getInstrument() {
		return instrument;
	}

	@Override
	public void setInstrument(LotroInstrument instrument) {
		if (instrument == null)
			throw new NullPointerException();

		if (this.instrument != instrument) {
			this.instrument = instrument;
			boolean affectsPreview = false;
			for (boolean enabled : trackEnabled) {
				if (enabled) {
					affectsPreview = true;
					abcSong.setMixDirty(true);// Might have switched from sustained instr to non, or opposite, so lets
												// recompute mixTimings
					break;
				}
			}
			fireChangeEvent(AbcPartProperty.INSTRUMENT, affectsPreview);
		}
	}

	public int getTrackTranspose(int track) {
		return isPercussionPart() ? 0 : trackTranspose[track];
	}

	public void setTrackTranspose(int track, int transpose) {
		if (trackTranspose[track] != transpose) {
			trackTranspose[track] = transpose;
			fireChangeEvent(AbcPartProperty.TRACK_TRANSPOSE, isTrackEnabled(track) /* previewRelated */, track);
		}
	}

	public int getTranspose(int track, long tickStart) {
		if (isPercussionPart())
			return 0;
		return abcSong.getTranspose() + abcSong.getTuneTranspose(tickStart) + trackTranspose[track]
				- getInstrument().octaveDelta * 12 + getSectionTranspose(tickStart, track);
	}

	public int getSectionTranspose(long tickStart, int track) {
		int secTrans = 0;
		if (!isTrackEnabled(track))
			return secTrans;
		SequenceInfo se = getSequenceInfo();
		TreeMap<Integer, PartSection> tree = sections.get(track);
		if (se != null && tree != null) {
			SequenceDataCache data = se.getDataCache();
			long barLengthTicks = data.getBarLengthTicks();

			long startTick = barLengthTicks;
			long endTick = data.getSongLengthTicks();

			int bar = -1;
			int curBar = 1;
			for (long barTick = startTick; barTick <= endTick + barLengthTicks; barTick += barLengthTicks) {
				if (tickStart < barTick) {
					bar = curBar;
					break;
				}
				curBar += 1;
			}
			if (bar != -1) {
				Entry<Integer, PartSection> entry = tree.floorEntry(bar);
				if (entry != null) {
					if (bar <= entry.getValue().endBar) {
						secTrans = entry.getValue().octaveStep * 12;
					}
				}
			}
		}

		return secTrans;
	}

	public Boolean[] getSectionDoubling(long tickStart, int track) {
		Boolean[] secDoubling = { false, false, false, false };
		if (!isTrackEnabled(track))
			return secDoubling;
		SequenceInfo se = getSequenceInfo();
		TreeMap<Integer, PartSection> tree = sections.get(track);
		boolean isSection = false;
		if (se != null && tree != null) {
			SequenceDataCache data = se.getDataCache();
			long barLengthTicks = data.getBarLengthTicks();

			long startTick = barLengthTicks;
			long endTick = data.getSongLengthTicks();

			int bar = -1;
			int curBar = 1;
			for (long barTick = startTick; barTick <= endTick + barLengthTicks; barTick += barLengthTicks) {
				if (tickStart < barTick) {
					bar = curBar;
					break;
				}
				curBar += 1;
			}
			if (bar != -1) {
				Entry<Integer, PartSection> entry = tree.floorEntry(bar);
				if (entry != null) {
					if (bar <= entry.getValue().endBar) {
						isSection = true;
						secDoubling = entry.getValue().doubling;
					}
				}
			}
		}
		if (se != null && !isSection && nonSection.get(track) != null) {
			secDoubling = nonSection.get(track).doubling;
		}

		return secDoubling;
	}

	/**
	 * @param track
	 * @param ne
	 * @return velocity of the noteEvent, or is reset velocities active, then mezzoforte
	 */
	public int getSectionNoteVelocity(int track, NoteEvent ne) {
		SequenceInfo se = getSequenceInfo();
		TreeMap<Integer, PartSection> tree = sections.get(track);
		boolean isSection = false;
		if (se != null && tree != null) {
			SequenceDataCache data = se.getDataCache();
			long barLengthTicks = data.getBarLengthTicks();

			long startTick = barLengthTicks;
			long endTick = data.getSongLengthTicks();

			int bar = -1;
			int curBar = 1;
			for (long barTick = startTick; barTick <= endTick + barLengthTicks; barTick += barLengthTicks) {
				if (ne.getStartTick() < barTick) {
					bar = curBar;
					break;
				}
				curBar += 1;
			}
			if (bar != -1) {
				Entry<Integer, PartSection> entry = tree.floorEntry(bar);
				if (entry != null) {
					if (bar <= entry.getValue().endBar) {
						isSection = true;
						return entry.getValue().resetVelocities ? Dynamics.DEFAULT.midiVol : ne.velocity;
					}
				}
			}
		}
		if (se != null && !isSection && nonSection.get(track) != null) {
			return nonSection.get(track).resetVelocities ? Dynamics.DEFAULT.midiVol : ne.velocity;
		}

		return ne.velocity;
	}

	public int[] getSectionVolumeAdjust(int track, NoteEvent ne) {
		SequenceInfo se = getSequenceInfo();
		int delta = 0;// volume offset
		int factor = 100;// current fade-out volume factor
		int factorTune = 100;// current fade-out volume factor (for tuneeditor)
		NavigableMap<Integer, TuneLine> tuneMap = abcSong.tuneBars;
		TreeMap<Integer, PartSection> tree = sections.get(track);
		if (se != null && (tree != null || tuneMap != null)) {
			SequenceDataCache data = se.getDataCache();
			long barLengthTicks = data.getBarLengthTicks();

			long startTick = barLengthTicks;
			long endTick = data.getSongLengthTicks();

			int bar = -1;
			int curBar = 1;
			for (long barTick = startTick; barTick <= endTick + barLengthTicks; barTick += barLengthTicks) {
				// long barMicros = data.tickToMicros(barTick);
				if (ne.getStartTick() < barTick) {
					bar = curBar;
					break;
				}
				curBar += 1;
			}
			if (bar != -1) {
				if (tree != null) {
					Entry<Integer, PartSection> entry = tree.floorEntry(bar);
					if (entry != null) {
						if (bar <= entry.getValue().endBar) {
							delta = entry.getValue().volumeStep;
							if (entry.getValue().fade > 0) {
								factor = map(ne.getStartTick(), entry.getValue().startBar * barLengthTicks - barLengthTicks,
										entry.getValue().endBar * barLengthTicks, 100, 100 - entry.getValue().fade);
							} else if (entry.getValue().fade < 0) {
								factor = map(ne.getStartTick(), entry.getValue().startBar * barLengthTicks - barLengthTicks,
										entry.getValue().endBar * barLengthTicks, 100 + entry.getValue().fade, 100);
							}
						}
					}
				}
				if (tuneMap != null) {
					Entry<Integer, TuneLine> entry = tuneMap.floorEntry(bar);
					if (entry != null) {
						if (bar <= entry.getValue().endBar) {
							if (entry.getValue().fade > 0) {
								factorTune = map(ne.getStartTick(), entry.getValue().startBar * barLengthTicks - barLengthTicks,
										entry.getValue().endBar * barLengthTicks, 100, 100 - entry.getValue().fade);
							} else if (entry.getValue().fade < 0) {
								factorTune = map(ne.getStartTick(), entry.getValue().startBar * barLengthTicks - barLengthTicks,
										entry.getValue().endBar * barLengthTicks, 100 + entry.getValue().fade, 100);
							}
						}
					}
				}
			}
		}
		int[] retur = new int[3];
		retur[0] = delta;
		retur[1] = factor;
		retur[2] = factorTune;		
		return retur;
	}

	public boolean getAudible(int track, long tickStart) {
		if (!isTrackEnabled(track))
			return true;
		SequenceInfo se = getSequenceInfo();
		TreeMap<Integer, PartSection> tree = sections.get(track);
		boolean isSection = false;
		if (se != null && tree != null) {
			SequenceDataCache data = se.getDataCache();
			long barLengthTicks = data.getBarLengthTicks();

			long startTick = barLengthTicks;
			long endTick = data.getSongLengthTicks();

			int bar = -1;
			int curBar = 1;
			for (long barTick = startTick; barTick <= endTick + barLengthTicks; barTick += barLengthTicks) {
				if (tickStart < barTick) {
					bar = curBar;
					break;
				}
				curBar += 1;
			}
			if (bar != -1) {
				Entry<Integer, PartSection> entry = tree.floorEntry(bar);
				if (entry != null) {
					if (bar <= entry.getValue().endBar) {
						isSection = true;
						return !entry.getValue().silence;
					}
				}
			}
		}
		if (se != null && !isSection && nonSection.get(track) != null) {
			return !nonSection.get(track).silence;
		}

		return true;
	}

	public boolean isTrackEnabled(int track) {
		return trackEnabled[track];
	}

	public void setTrackEnabled(int track, boolean enabled) {
		if (trackEnabled[track] != enabled) {
			trackEnabled[track] = enabled;
			enabledTrackCount += enabled ? 1 : -1;
			abcSong.setMixDirty(true);
			fireChangeEvent(AbcPartProperty.TRACK_ENABLED, track);
		}
	}

	public int getTrackVolumeAdjust(int track) {
		return trackVolumeAdjust[track];
	}

	public void setTrackVolumeAdjust(int track, int volumeAdjust) {
		if (trackVolumeAdjust[track] != volumeAdjust) {
			trackVolumeAdjust[track] = volumeAdjust;
			fireChangeEvent(AbcPartProperty.VOLUME_ADJUST, track);
		}
	}

	public int getEnabledTrackCount() {
		return enabledTrackCount;
	}

	public void setPreviewSequenceTrackNumber(int previewSequenceTrackNumber) {
		this.previewSequenceTrackNumber = previewSequenceTrackNumber;
	}

	public int getPreviewSequenceTrackNumber() {
		return previewSequenceTrackNumber;
	}

	@Override
	public int getPartNumber() {
		return partNumber;
	}

	@Override
	public void setPartNumber(int partNumber) {
		if (this.partNumber != partNumber) {
			this.partNumber = partNumber;
			fireChangeEvent(AbcPartProperty.PART_NUMBER);
		}
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		this.muted = muted;
	}

	public boolean isSoloed() {
		return soloed;
	}

	public void setSoloed(boolean soloed) {
		this.soloed = soloed;
	}
	
	public boolean isActive() {
		if (soloed) return true;
		if (abcSong.isAnyPartsSoloed()) return false;
		return !muted;
	}

	public void addAbcListener(Listener<AbcPartEvent> l) {
		listeners.add(l);
	}

	public void removeAbcListener(Listener<AbcPartEvent> l) {
		listeners.remove(l);
	}

	protected void fireChangeEvent(AbcPartProperty property) {
		fireChangeEvent(property, property.isAbcPreviewRelated(), AbcPartEvent.NO_TRACK_NUMBER);
	}

	protected void fireChangeEvent(AbcPartProperty property, boolean abcPreviewRelated) {
		fireChangeEvent(property, abcPreviewRelated, AbcPartEvent.NO_TRACK_NUMBER);
	}

	protected void fireChangeEvent(AbcPartProperty property, int trackNumber) {
		fireChangeEvent(property, property.isAbcPreviewRelated(), trackNumber);
	}

	protected void fireChangeEvent(AbcPartProperty property, boolean abcPreviewRelated, int trackNumber) {
		if (listeners.size() == 0)
			return;

		listeners.fire(new AbcPartEvent(this, property, abcPreviewRelated, trackNumber));
	}

	//
	// DRUMS
	//

	public boolean isPercussionPart() {
		return instrument.isPercussion;
	}
	
	public boolean isDrumPart() {
		return instrument == LotroInstrument.BASIC_DRUM;
	}

	public boolean isCowbellPart() {
		return instrument == LotroInstrument.BASIC_COWBELL || instrument == LotroInstrument.MOOR_COWBELL;
	}

	public boolean isFXPart() {
		return instrument == LotroInstrument.STUDENT_FX_FIDDLE;
	}

	public boolean isDrumTrack(int track) {
		return abcSong.getSequenceInfo().getTrackInfo(track).isDrumTrack();
	}

	public DrumNoteMap getDrumMap(int track) {
		if (drumNoteMap[track] == null) {
			// For non-drum tracks, just use a straight pass-through
			if (!abcSong.getSequenceInfo().getTrackInfo(track).isDrumTrack()) {
				drumNoteMap[track] = new PassThroughDrumNoteMap();
			} else {
				drumNoteMap[track] = new DrumNoteMap();
				drumNoteMap[track].load(drumPrefs);
			}
			drumNoteMap[track].addChangeListener(drumMapChangeListener);
		}
		return drumNoteMap[track];
	}

	public StudentFXNoteMap getFXMap(int track) {
		if (fxNoteMap[track] == null) {
			// For non-drum tracks, just use a straight pass-through
			// if (!abcSong.getSequenceInfo().getTrackInfo(track).isDrumTrack())
			// {
			fxNoteMap[track] = new PassThroughFXNoteMap();
			// }
			// else
			// {
			// drumNoteMap[track] = new StudentFXNoteMap();
			// drumNoteMap[track].load(drumPrefs);
			// }
			fxNoteMap[track].addChangeListener(drumMapChangeListener);
		}
		return fxNoteMap[track];
	}

	private final ChangeListener drumMapChangeListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			if (e.getSource() instanceof DrumNoteMap) {
				DrumNoteMap map = (DrumNoteMap) e.getSource();

				// Don't write pass-through drum maps to the prefs node
				// these are used for non-drum tracks and their mapping
				// isn't desirable to save.
				if (!(map instanceof PassThroughDrumNoteMap) && !(map instanceof StudentFXNoteMap))
					map.save(drumPrefs);

				abcSong.setMixDirty(true);// Some drum sounds might have been toggled, so need to recompute mixTimings
				fireChangeEvent(AbcPartProperty.DRUM_MAPPING);
			}
		}
	};

	Preferences getDrumPrefs() {
		return drumPrefs;
	}

	public boolean isDrumPlayable(int track, int drumId) {
		if (isCowbellPart())
			return true;

		if (isFXPart())
			return getFXMap(track).get(drumId) != LotroStudentFXInfo.DISABLED.note.id;

		return getDrumMap(track).get(drumId) != LotroDrumInfo.DISABLED.note.id;
	}

	public boolean isDrumEnabled(int track, int drumId) {
		BitSet[] enabledSet = isCowbellPart() ? cowbellsEnabled : isFXPart() ? fxEnabled : drumsEnabled;

		if (enabledSet == null || enabledSet[track] == null) {
			return !isCowbellPart() || (drumId == MidiDrum.COWBELL.id())
					|| !abcSong.getSequenceInfo().getTrackInfo(track).isDrumTrack();
		}

		return enabledSet[track].get(drumId);
	}

	public void setDrumEnabled(int track, int drumId, boolean enabled) {
		if (isDrumEnabled(track, drumId) != enabled) {
			BitSet[] enabledSet;
			if (isCowbellPart()) {
				if (cowbellsEnabled == null)
					cowbellsEnabled = new BitSet[getTrackCount()];
				enabledSet = cowbellsEnabled;
			} else if (isFXPart()) {
				if (fxEnabled == null)
					fxEnabled = new BitSet[getTrackCount()];
				enabledSet = fxEnabled;
			} else {
				if (drumsEnabled == null)
					drumsEnabled = new BitSet[getTrackCount()];
				enabledSet = drumsEnabled;
			}

			if (enabledSet[track] == null) {
				enabledSet[track] = new BitSet(MidiConstants.NOTE_COUNT);
				if (isCowbellPart()) {
					SortedSet<Integer> notesInUse = abcSong.getSequenceInfo().getTrackInfo(track).getNotesInUse();
					if (notesInUse.contains(MidiDrum.COWBELL.id()))
						enabledSet[track].set(MidiDrum.COWBELL.id(), true);
				} else {
					enabledSet[track].set(0, MidiConstants.NOTE_COUNT, true);
				}
			}
			enabledSet[track].set(drumId, enabled);
			fireChangeEvent(AbcPartProperty.DRUM_ENABLED);
		}
	}

	public void sectionEdited(int track) {
		abcSong.setMixDirty(true); // Some notes might have gotten silenced in which case the mixTimings need to be
									// recomputed
		fireChangeEvent(AbcPartProperty.TRACK_SECTION_EDIT, track);
	}

	public void delayEdited() {
		fireChangeEvent(AbcPartProperty.DELAY_EDIT);
	}

	public void setTrackPriority(int track, boolean prio) {
		if (trackPriority[track] != prio) {
			trackPriority[track] = prio;
			abcSong.setMixDirty(true);
			fireChangeEvent(AbcPartProperty.TRACK_PRIORITY, true, track);
		}
	}

	public boolean isTrackPriority(int trackNumber) {
		return trackPriority[trackNumber];
	}
}