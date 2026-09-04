package com.digero.maestro.abc;

import static com.digero.maestro.abc.AbcHelper.matchNick;
import static java.awt.Frame.getFrames;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.midi.MidiEvent;
import javax.swing.JOptionPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.xml.xpath.XPathExpressionException;

import com.digero.common.midi.PanGenerator;
import com.digero.common.util.*;
import com.digero.common.view.UIText;
import com.digero.maestro.view.CountIn;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.*;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.Dynamics;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.LotroInstrumentSampleDuration;
import com.digero.common.midi.MidiConstants;
import com.digero.common.midi.MidiDrum;
import com.digero.common.midi.Note;
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
    protected static final Logger log = Logger.getLogger("song");

	private int partNumber = 1;

    /**
     * Whether the part-number for this part have been modified by the user.
     * If true, the part-number has been explicitly assigned, and automatic assignment
     * should be bypassed. Defaults to false unless explicitly changed.
     * Right after loading from XML it can be null until abcSong makes sure it's a boolean.
     */
    private Boolean partNumberManuallyModified = false;

	public boolean suppressSpinnerUpdate = false;
	private String title;
	private LotroInstrument instrument;
	private final int[] trackTranspose;
	private final boolean[] trackEnabled;
	private List<String> trackNames;//used only by autoexporter
	private final boolean[] trackPriority;
	public boolean[] playLeft;
	public boolean[] playCenter;
	public boolean[] playRight;
    private Integer userPan = null;//null = no user pan
    private MidiEvent panEvent = null;
	private final int[] trackVolumeAdjust;
	private final DrumNoteMap[] drumNoteMap;
	private final StudentFXNoteMap[] studentFxNoteMap;
    private final JauntyHandKnellsFXNoteMap[] jauntyHandKnellsFXNoteMap;
	private BitSet[] drumsEnabled;
	private BitSet[] cowbellsEnabled;
	private BitSet[] fxEnabled;//If specific FX sound is enabled. TODO: not sure if good idea that this is shared with jaunty
	private final boolean[] fx;//If FX checkbox is enabled
    /**
     * If this is enabled, the lowest student note allowable is including fx
     */
	private boolean studentFromABC = false;
	
	public static final int badgerPrioStep = 1;
	public static final int badgerPrioHighest = 1;
	public static final int badgerPrioLowest = 6;
	private int badgerPrio = badgerPrioHighest;
	
	protected int firstNumber;
	private final AbcSong abcSong;
	private int enabledTrackCount = 0;
	private int previewSequenceTrackNumber = -1;
	private final ListenerList<AbcPartEvent> listeners;


	private int noteMax = AbcConstants.MAX_CHORD_NOTES;
	public List<TreeMap<Float, PartSection>> sections;
	public List<TreeMap<Long, PartSection>> sectionsTicked = null;
	public List<PartSection> nonSection;
	public List<boolean[]> sectionsModified;
	private int delay = 0;// ms, -1000 to 1000
	public int conclusionFermata = 0;// ms
	private int typeNumber = 0;// -1 for when instr do not match or string dont start with instr, 0 when instr
								// match but no number, positive number when it has number.
	private final InstrNameSettings instrNameSettings;
	private boolean muted = false;
	private boolean soloed = false;

    // stats:
	public int numberOfExportedNotes = 0;
	public int numberOfRemovedNotesForSafety = 0;
    public int numberOfRemovedNotesFromPruning = 0;
    public int numberOfRemovedNotesFromFitting = 0;
    public int numberOfRemovedNotesZeros = 0;
	private int maxPoly = 0;
	
	public boolean discarded = false;
	
	public static final Note minDefault = Note.C0;//limit

    public final long uniqueID;//shared with any copies

    public AbcPart(AbcSong abcSong) {
        this(abcSong, System.nanoTime());
    }

    private AbcPart(AbcSong abcSong, long uniqueID) {
        this.uniqueID = uniqueID;
		this.abcSong = abcSong;
        listeners = new ListenerList<>();
        if (abcSong != null) {
            // can be null if unit testing
            abcSong.addSongListener(songListener);
        }
		this.instrument = LotroInstrument.DEFAULT_INSTRUMENT;
		this.instrNameSettings = abcSong == null?null:abcSong.getInstrNameSettings();
		this.title = abcSong == null?"myUnitTestPart":this.instrNameSettings.getInstrNick(instrument);

		int trackCount = abcSong == null?1:getTrackCount();
		this.trackTranspose = new int[trackCount];
		this.trackEnabled = new boolean[trackCount];
		this.trackPriority = new boolean[trackCount];
		this.playLeft = new boolean[trackCount];
		this.playCenter = new boolean[trackCount];
		this.playRight = new boolean[trackCount];
		this.fx = new boolean[trackCount];

		this.trackVolumeAdjust = new int[trackCount];
		this.drumNoteMap = new DrumNoteMap[trackCount];
		this.studentFxNoteMap = new StudentFXNoteMap[trackCount];
        this.jauntyHandKnellsFXNoteMap = new JauntyHandKnellsFXNoteMap[trackCount];
		this.sections = new ArrayList<>();
		this.nonSection = new ArrayList<>();
		this.sectionsModified = new ArrayList<>();
		for (int track = 0; track < trackCount; track++) {
			this.sections.add(null);
			this.nonSection.add(null);
			this.sectionsModified.add(null);
			this.playLeft[track] = true;
			this.playCenter[track] = true;
			this.playRight[track] = true;
			this.fx[track] = abcSong == null?false:isDrumTrack(track);
		}
	}

	@Override
	public void discard() {
		if (abcSong != null)
			abcSong.removeSongListener(songListener);
		listeners.discard();
		for (int i = 0; i < drumNoteMap.length; i++) {
			if (drumNoteMap[i] != null) {
				drumNoteMap[i].removeChangeListener(drumMapChangeListener);
				drumNoteMap[i] = null;
			}
		}
		for (int i = 0; i < studentFxNoteMap.length; i++) {
			if (studentFxNoteMap[i] != null) {
				studentFxNoteMap[i].removeChangeListener(drumMapChangeListener);
				studentFxNoteMap[i] = null;
			}
		}
        for (int i = 0; i < jauntyHandKnellsFXNoteMap.length; i++) {
            if (jauntyHandKnellsFXNoteMap[i] != null) {
                jauntyHandKnellsFXNoteMap[i].removeChangeListener(drumMapChangeListener);
                jauntyHandKnellsFXNoteMap[i] = null;
            }
        }
		sections = null;
		sectionsTicked = null;
		sectionsModified = null;
		//nonSection = null; never expected to be null
		delay = 0;
		conclusionFermata = 0;
		discarded = true;
	}
	
	public void convertSectionsToLongTrees () {
		SequenceInfo se = getSequenceInfo();
		if (se == null) {
			throw new RuntimeException("Error in floating point section");
		}
		SequenceDataCache data = se.getDataCache();
		List<TreeMap<Long, PartSection>> longsections = new ArrayList<>();
		for (TreeMap<Float, PartSection> section : sections) {
			if (section == null) {
				longsections.add(null);
				continue;
			}
			TreeMap<Long, PartSection> longtree = new TreeMap<>();
			for (Entry<Float, PartSection> entry : section.entrySet()) {
				PartSection ps = entry.getValue();

				ps.startTick = data.barFloatToTick(ps.startBar);
				ps.endTick   = data.barFloatToTick(ps.endBar);
				
				PartSection prev = longtree.put(ps.startTick, ps);
				assert prev == null;
			}
			longsections.add(longtree);
		}
		sectionsTicked = longsections;
	}

	@SuppressWarnings("HardCodedStringLiteral")
	public void saveToXml(Element ele) {
		Document doc = ele.getOwnerDocument();

		ele.setAttribute("id", String.valueOf(partNumber));
        ele.setAttribute("userAssignedId", String.valueOf(partNumberManuallyModified));
		ele.setAttribute("badgerPriority", String.valueOf(badgerPrio));
        if (userPan != null) ele.setAttribute("pan", String.valueOf(userPan));
		SaveUtil.appendChildTextElement(ele, "title", String.valueOf(title));
		SaveUtil.appendChildTextElement(ele, "instrument", String.valueOf(instrument));
		if (delay != 0) {
			SaveUtil.appendChildTextElement(ele, "delay", String.valueOf(delay));
		}
        CountIn countIn = abcSong.getCountIn();
        if (countIn != null && countIn.part == this) {
            Element countInNode = doc.createElement("countIn");
            countInNode.setAttribute("pattern", countIn.pattern.name());
            countInNode.setAttribute("barCount", String.valueOf(countIn.barCount));
            countInNode.setAttribute("hitId", String.valueOf(countIn.hit.note.id));
            ele.appendChild(countInNode);
        }
		if (conclusionFermata != 0) {
			SaveUtil.appendChildTextElement(ele, "conclusionFermata", String.valueOf(conclusionFermata));
		}
		if (noteMax != AbcConstants.MAX_CHORD_NOTES) {
			ele.setAttribute("noteMax", String.valueOf(noteMax));
		}
		for (int track = 0; track < getTrackCount(); track++) {
			if (!isTrackEnabled(track))
				continue;

			TrackInfo trackInfo = abcSong.getSequenceInfo().getTrackInfo(track);

			Element trackEle = (Element) ele.appendChild(doc.createElement("track"));
			trackEle.setAttribute("id", String.valueOf(track));
			if (trackInfo.hasName()) {
				trackEle.setAttribute("name", XmlUtil.sanitizeStringForXMLSaving(trackInfo.getName()));
			} else if (abcSong.ignoreMidiText && trackNames != null && trackNames.size() > track && trackNames.get(track) != null) {
				//used by autoexporter
				trackEle.setAttribute("name", XmlUtil.sanitizeStringForXMLSaving(trackNames.get(track)));
			}
				
			if (trackTranspose[track] != 0)
				SaveUtil.appendChildTextElement(trackEle, "transpose", String.valueOf(trackTranspose[track]));
			if (trackVolumeAdjust[track] != 0)
				SaveUtil.appendChildTextElement(trackEle, "volumeAdjust", String.valueOf(trackVolumeAdjust[track]));
			if (abcSong.isMixTiming() && abcSong.isPriorityActive() && trackPriority[track])
                SaveUtil.appendChildTextElement(trackEle, "combinePriority",
                        String.valueOf(QuantizedTimingInfo.COMBINE_PRIORITY_MULTIPLIER));
			if (!playLeft[track])
				trackEle.setAttribute("playLeft", String.valueOf(playLeft[track]));
			if (!playCenter[track])
				trackEle.setAttribute("playCenter", String.valueOf(playCenter[track]));
			if (!playRight[track])
				trackEle.setAttribute("playRight", String.valueOf(playRight[track]));

            boolean isFx = isFX(track);
            boolean studentFX = isFx && isStudentPart();

			TreeMap<Float, PartSection> tree = sections.get(track);
			if (tree != null) {
				for (Entry<Float, PartSection> entry : tree.entrySet()) {
					PartSection ps = entry.getValue();
					Element sectionEle = (Element) trackEle.appendChild(doc.createElement("section"));
					SaveUtil.appendChildTextElement(sectionEle, "startBar", String.valueOf(ps.startBar));
					SaveUtil.appendChildTextElement(sectionEle, "endBar", String.valueOf(ps.endBar));
					if (!instrument.isPercussion && !studentFX) {
						SaveUtil.appendChildTextElement(sectionEle, "octaveStep", String.valueOf(ps.octaveStep));
					}
					SaveUtil.appendChildTextElement(sectionEle, "volumeStep", String.valueOf(ps.volumeStep));
					SaveUtil.appendChildTextElement(sectionEle, "silence", String.valueOf(ps.silence));
                    if (instrument.sustainable && !studentFX) SaveUtil.appendChildTextElement(sectionEle, "legato", String.valueOf(ps.legato));
					SaveUtil.appendChildTextElement(sectionEle, "fade", String.valueOf(ps.fade));
					SaveUtil.appendChildTextElement(sectionEle, "dialogLine", String.valueOf(ps.dialogLine));
					SaveUtil.appendChildTextElement(sectionEle, "resetVelocities", String.valueOf(ps.resetVelocities));
					AbcHelper.saveDoublingToXML(ps, sectionEle, instrument.isPercussion || studentFX);
					if (!isFx && !instrument.isPercussion && (ps.fromPitch != minDefault || ps.toPitch != Note.MAX)) {
                        SaveUtil.appendChildTextElement(sectionEle, "fromPitch", String.valueOf(ps.fromPitch.id));
						SaveUtil.appendChildTextElement(sectionEle, "toPitch", String.valueOf(ps.toPitch.id));
					}
				}
			}

			if (nonSection.get(track) != null) {
				PartSection ps = nonSection.get(track);
				Element sectionEle = (Element) trackEle.appendChild(doc.createElement("nonSection"));
				SaveUtil.appendChildTextElement(sectionEle, "silence", String.valueOf(ps.silence));
				if (instrument.sustainable && !studentFX) SaveUtil.appendChildTextElement(sectionEle, "legato", String.valueOf(ps.legato));
				SaveUtil.appendChildTextElement(sectionEle, "resetVelocities", String.valueOf(ps.resetVelocities));
				AbcHelper.saveDoublingToXML(ps, sectionEle, instrument.isPercussion || studentFX);
				if (!isFx && !instrument.isPercussion && (ps.fromPitch != minDefault || ps.toPitch != Note.MAX)) {
					SaveUtil.appendChildTextElement(sectionEle, "fromPitch", String.valueOf(ps.fromPitch.id));
					SaveUtil.appendChildTextElement(sectionEle, "toPitch", String.valueOf(ps.toPitch.id));
				}
			}
			
			if (isStudentPart()) {
				trackEle.setAttribute("fx", String.valueOf(isFx));
				trackEle.setAttribute("studentOverride", String.valueOf(isStudentFromABC()));
			} else if (isJauntyHandKnellsPart()) {
                trackEle.setAttribute("fx", String.valueOf(isFx));
            }
            if (instrument.isPercussion || ((isStudentPart() || isJauntyHandKnellsPart()) && isFx)) {
				saveDrumHitsToXML(ele, doc, track, trackEle);
			}
		}
	}

    /**
     * Save the list of enabled drum hits and drummap to xml
     */
	@SuppressWarnings("HardCodedStringLiteral")
	private void saveDrumHitsToXML(Element ele, Document doc, int t, Element trackEle) {
		BitSet[] enabledSetByTrack = isCowbellPart() ? cowbellsEnabled : (isStudentPart() || isJauntyHandKnellsPart()) ? fxEnabled : drumsEnabled;
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
			} else if (isStudentPart() || isJauntyHandKnellsPart()) {
				storeDisabledDrums(ele, enabledSet, drumsEnabledEle);
			} else {
				storeDisabledDrums(ele, enabledSet, drumsEnabledEle);
			}
		}

		if (!isCowbellPart()) {
			if (isDrumPart() && drumNoteMap[t] != null)
				drumNoteMap[t]
						.saveToXml((Element) trackEle.appendChild(doc.createElement(DrumNoteMap.getXmlName())));
			if (isStudentPart() && studentFxNoteMap[t] != null)
				studentFxNoteMap[t].saveToXml((Element) trackEle.appendChild(doc.createElement(StudentFXNoteMap.getXmlName())));
            if (isJauntyHandKnellsPart() && jauntyHandKnellsFXNoteMap[t] != null)
                jauntyHandKnellsFXNoteMap[t].saveToXml((Element) trackEle.appendChild(doc.createElement(JauntyHandKnellsFXNoteMap.getXmlName())));
		}
	}

	@SuppressWarnings("HardCodedStringLiteral")
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

	public static AbcPart loadFromXml(AbcSong abcSong, Element ele, Version fileVersion, WarningHandler warningHandler) throws FileParseException {
		AbcPart part = new AbcPart(abcSong);
		part.initFromXml(ele, fileVersion, warningHandler);
		return part;
	}

	@SuppressWarnings("HardCodedStringLiteral")
	private void initFromXml(Element ele, Version fileVersion, WarningHandler warningHandler) throws FileParseException {
		try {
			partNumber = SaveUtil.parseValue(ele, "@id", partNumber);
			if (partNumber == 0) partNumber = 999;
            String lock = SaveUtil.parseValue(ele, "@userAssignedId", "null");
            if (lock.equals("null")) {
                partNumberManuallyModified = null;
            } else {
                partNumberManuallyModified = Boolean.valueOf(lock);
            }
			badgerPrio = SaveUtil.parseValue(ele, "@badgerPrio", -1);// backward compat with 4.1.3
			if (badgerPrio == -1) {
				badgerPrio = SaveUtil.parseValue(ele, "@badgerPriority", badgerPrioHighest);
			} else {
				// backward compat with 4.1.3
				badgerPrio = 10-badgerPrio;
			}
			title = SaveUtil.parseValue(ele, "title", title);

            userPan = null;
            if (new Version(4, 5, 15, 300).compareTo(fileVersion) > 0) {
                //old backwards compat for pan:
                String titleLower = title.toLowerCase();
                if (PanGenerator.leftRegex.matcher(titleLower).find())
                    userPan = 0;
                else if (PanGenerator.rightRegex.matcher(titleLower).find())
                    userPan = 127;
                else if (PanGenerator.centerRegex.matcher(titleLower).find())
                    userPan = 64;
            }
            //new pan data:
            int xmlPan = SaveUtil.parseValue(ele, "@pan", Integer.MAX_VALUE);
            if (xmlPan != Integer.MAX_VALUE) {
                userPan = xmlPan;
            }
			instrument = SaveUtil.parseValue(ele, "instrument", instrument);
			typeNumber = getTypeNumberMatchingTitle();// must be after instr and title
			delay = SaveUtil.parseValue(ele, "delay", 0);
			conclusionFermata = SaveUtil.parseValue(ele, "conclusionFermata", 0);
			noteMax = SaveUtil.parseValue(ele, "@noteMax", AbcConstants.MAX_CHORD_NOTES);
            NodeList countInElems = ele.getElementsByTagName("countIn");
            if (countInElems.getLength() > 0) {
                Node countInEle = countInElems.item(0);
                NamedNodeMap attr = countInEle.getAttributes();
                Node patternAttr = attr.getNamedItem("pattern");
                Node barCountAttr = attr.getNamedItem("barCount");
                Node hitIdAttr = attr.getNamedItem("hitId");
                if (patternAttr != null && barCountAttr != null && hitIdAttr != null) {
                    String pattern = patternAttr.getNodeValue();
                    String barCount = barCountAttr.getNodeValue();
                    String hitId = hitIdAttr.getNodeValue();
                    try {
                        CountIn countIn = new CountIn(pattern, barCount, this, hitId);
                        getAbcSong().setCountIn(countIn);
                        CountIn.setLastCountIn(countIn);
                    } catch (NullPointerException | IllegalArgumentException e) {
                        log.warning("Count-in in MSX Project is corrupt. Skipping.");
                    }
                }
            }
			for (Element trackEle : XmlUtil.selectElements(ele, "track")) {

				// Try to find the specified track in the midi sequence by name, in case it
				// moved
				String xmlTrackName = SaveUtil.parseValue(trackEle, "@name", "");
				
				int t = findTrackNumberByName(xmlTrackName);
				// Fall back to the track ID if that didn't work
				if (t == -1)
					t = SaveUtil.parseValue(trackEle, "@id", -1);

				if (t < 0 || t >= getTrackCount()) {
					String optionalName = xmlTrackName;

					if (!optionalName.isEmpty()) {
						optionalName = " (" + optionalName + ")";
					}

					throw SaveUtil.invalidTrackException(trackEle,
							"Could not find track number " + t + optionalName + " in original MIDI file");
				}
				if (trackNames == null) {
					trackNames = new ArrayList<>();
				}
				while(trackNames.size() <= t) {
					trackNames.add(null);
				}
				trackNames.set(t, xmlTrackName);
				if (!abcSong.getSequenceInfo().getTrackInfo(t).hasEvents()) {
					if (warningHandler != null) {
						// Abc Tools just get a log
						log.warning(UIText.get("maestro.0.has.a.midi.track.track.1.selected.that.has.no.notes", title, t));
					} else {
						Component parent = (getFrames().length > 0) ? getFrames()[0] : null;
						JOptionPane.showMessageDialog(parent,
								UIText.get("maestro.0.has.a.midi.track.track.1.selected.that.has.no.notes", title, t),
								UIText.get("maestro.warning.for.0", abcSong.getTitle()), JOptionPane.WARNING_MESSAGE);
					}
				}

				TreeMap<Float, PartSection> tree = sections.get(t);
				float lastEnd = 0.0f;
				for (Element sectionEle : XmlUtil.selectElements(trackEle, "section")) {
					PartSection ps = AbcHelper.loadPartSectionFromXML(sectionEle, fileVersion);
					if (ps.startBar >= 0.0f && ps.endBar > ps.startBar) {
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
                if (lastEnd > 200_000f) { // Limit to 200k bars to prevent OOM
                    log.warning("Section endBar too large: " + lastEnd + ". Clamping to 200,000.");
                    lastEnd = 200_000f;
                }
				boolean[] booleanArray = new boolean[(int)(lastEnd) + 1];
				if (tree != null) {
					for (int i = 0; i < (int)(lastEnd) + 1; i++) {
						Entry<Float, PartSection> entry = tree.lowerEntry(i+1.0f);
						booleanArray[i] = entry != null && entry.getValue().startBar < i + 1.0f
								&& entry.getValue().endBar > i;
					}
					
					sectionsModified.set(t, booleanArray);
				}

				Element nonSectionEle = XmlUtil.selectSingleElement(trackEle, "nonSection");
				if (nonSectionEle != null) {
					PartSection ps = new PartSection();
					ps.silence = SaveUtil.parseValue(nonSectionEle, "silence", false);
					ps.legato = SaveUtil.parseValue(nonSectionEle, "legato", false);
					ps.resetVelocities = SaveUtil.parseValue(nonSectionEle, "resetVelocities", false);
					ps.doubling[0] = SaveUtil.parseValue(nonSectionEle, "double2OctDown", false);
					ps.doubling[1] = SaveUtil.parseValue(nonSectionEle, "double1OctDown", false);
					ps.doubling[2] = SaveUtil.parseValue(nonSectionEle, "double1OctUp", false);
					ps.doubling[3] = SaveUtil.parseValue(nonSectionEle, "double2OctUp", false);
                    ps.fromPitch = Note.fromId(SaveUtil.parseValue(nonSectionEle, "fromPitch", minDefault.id));
                    if (ps.fromPitch == null) ps.fromPitch = minDefault;
					ps.toPitch = Note.fromId(SaveUtil.parseValue(nonSectionEle, "toPitch", Note.MAX.id));
                    if (ps.toPitch == null) ps.toPitch = Note.MAX;
					nonSection.set(t, ps);
				}
				
				// Now set the track info
				trackEnabled[t] = true;
				enabledTrackCount++;
				boolean isFx = SaveUtil.parseValue(trackEle, "@fx", false);
				studentFromABC = SaveUtil.parseValue(trackEle, "@studentOverride", false);
				trackTranspose[t] = SaveUtil.parseValue(trackEle, "transpose", trackTranspose[t]);
                /*
                if (trackTranspose[t] < 0 && (instrument == LotroInstrument.BASIC_FLUTE || instrument == LotroInstrument.TRAVELLERS_TRUSTY_FIDDLE || (instrument == LotroInstrument.BASIC_LUTE && trackTranspose[t] < -12))) {
                    abcSong.highCandidate = true;
                }
                 */
				trackVolumeAdjust[t] = SaveUtil.parseValue(trackEle, "volumeAdjust", trackVolumeAdjust[t]);
				int prio = SaveUtil.parseValue(trackEle, "combinePriority", 1);
				if (prio == QuantizedTimingInfo.COMBINE_PRIORITY_MULTIPLIER) {
					// Hardcoded to 4 for now, change QTM and UI if messing with this
					trackPriority[t] = true;
				}
				playLeft[t] = SaveUtil.parseValue(trackEle, "@playLeft", true);
				playCenter[t] = SaveUtil.parseValue(trackEle, "@playCenter", true);
				playRight[t] = SaveUtil.parseValue(trackEle, "@playRight", true);
				
				if (instrument.isPercussion) {
					loadDrumHitsFromXML(fileVersion, trackEle, t);
				} else if (new Version(3, 2, 9, 300).compareTo(fileVersion) > 0 && isStudentPart()) {
					// compat handling
					loadDrumHitsFromXML(fileVersion, trackEle, t);
					isFx = studentFxNoteMap[t] != null;
					setFX(t, isFx);
				} else if (isStudentPart()) {
					if (isFx) loadDrumHitsFromXML(fileVersion, trackEle, t);
					setFX(t, isFx);
				} else if (isJauntyHandKnellsPart()) {
                    if (isFx) loadDrumHitsFromXML(fileVersion, trackEle, t);
                    setFX(t, isFx);
                }
			}
		} catch (XPathExpressionException e) {
			throw new FileParseException("XPath error: " + e.getMessage(), null);
		}
	}

    /**
     * Load from xml the drummap and list of enabled drum hits
     */
	private void loadDrumHitsFromXML(Version fileVersion, Element trackEle, int t)
			throws XPathExpressionException, FileParseException {
		Element drumsEle = XmlUtil.selectSingleElement(trackEle, "drumsEnabled");
		if (drumsEle != null) {
			loadEnabledSetFromXML(t, drumsEle);
		}

		Element drumMapEle = XmlUtil.selectSingleElement(trackEle, DrumNoteMap.getXmlName());
		if (drumMapEle != null) {
			drumNoteMap[t] = DrumNoteMap.loadFromXml(drumMapEle, fileVersion, abcSong.getCombiInfo());
			if (drumNoteMap[t] != null)
				drumNoteMap[t].addChangeListener(drumMapChangeListener);
		}
		drumMapEle = XmlUtil.selectSingleElement(trackEle, StudentFXNoteMap.getXmlName());
		if (drumMapEle != null) {
			studentFxNoteMap[t] = StudentFXNoteMap.loadFromXml(drumMapEle, fileVersion);
			if (studentFxNoteMap[t] != null)
				studentFxNoteMap[t].addChangeListener(drumMapChangeListener);
		}
        drumMapEle = XmlUtil.selectSingleElement(trackEle, JauntyHandKnellsFXNoteMap.getXmlName());
        if (drumMapEle != null) {
            jauntyHandKnellsFXNoteMap[t] = JauntyHandKnellsFXNoteMap.loadFromXml(drumMapEle, fileVersion);
            if (jauntyHandKnellsFXNoteMap[t] != null)
                jauntyHandKnellsFXNoteMap[t].addChangeListener(drumMapChangeListener);
        }
	}

    /**
     * Load enabled drum hits from XML
     */
	@SuppressWarnings("HardCodedStringLiteral")
	private void loadEnabledSetFromXML(int t, Element drumsEle) throws FileParseException, XPathExpressionException {
		boolean defaultEnabled = SaveUtil.parseValue(drumsEle, "@defaultEnabled", !isCowbellPart());

		BitSet[] enabledSet;
		if (isCowbellPart()) {
			if (cowbellsEnabled == null)
				cowbellsEnabled = new BitSet[getTrackCount()];
			enabledSet = cowbellsEnabled;
		} else if (isStudentPart() || isJauntyHandKnellsPart()) {
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
		if (trackName.isEmpty())
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

	private final Listener<AbcSongEvent> songListener = e -> {
		if (e.getProperty() == AbcSongProperty.TRANSPOSE) {
            // the reason for false is that abcSong own event will already trigger
            // preview generation, do not want to trigger one for each part.
			fireChangeEvent(AbcPartProperty.BASE_TRANSPOSE, false);
		}
		if (e.getProperty() == AbcSongProperty.MIX_TIMING_COMBINE_PRIORITIES
				|| e.getProperty() == AbcSongProperty.MIX_TIMING) {
            // TODO: Perhaps we should consider deleting this call
            //       I might be missing something, but I do not think
            //       it is needed. For example when open new project,
            //       this gets fired once per part, even though the
            //       track priorities themselves don't change.
            //       For now I just set previewRelated to false.
			fireChangeEvent(AbcPartProperty.TRACK_PRIORITY, false);
		}
	};

	public List<MidiNoteEvent> getTrackEvents(int track) {
		return abcSong.getSequenceInfo().getTrackInfo(track).getMidiEvents();
	}

	/**
	 * Maps from a MIDI note to an ABC note. If no mapping is available, returns <code>null</code>.
	 * 
	 * Notice this method does not work for bent notes, use mapNoteEvent for those.
	 */
	public Note mapNote(int track, int noteId, long tickStart) {
		if (!getAudible(track, tickStart)) {
			return null;
		}
		if (noteId < Note.MIN.id || noteId > Note.MAX.id) {
			// extra check for invalid noteid that can make drum-map throw exception.
			return null;
		}
		if (!isChromatic(track)) {
			if (!isPercussionNoteEnabled(track, noteId))
				return null;

			int dstNote;
			if (instrument == LotroInstrument.BASIC_COWBELL)
				dstNote = Note.G2.id; // "Tom High 1"
			else if (instrument == LotroInstrument.MOOR_COWBELL)
				dstNote = Note.A2.id; // "Tom High 2"
			else if (isStudentPart())
				dstNote = getFXMap(track).get(noteId);
            else if (isJauntyHandKnellsPart()) {
                dstNote = getJauntyHandKnellsFXMap(track).get(noteId);
                if (dstNote == LotroChromaticFXInfo.DISABLED.note.id) {
                    return null;
                }
                dstNote += getFXTranspose(track, tickStart);
                while (dstNote < instrument.lowestPlayable.id)
                    dstNote += 12;
                while (dstNote > instrument.highestPlayable.id)
                    dstNote -= 12;
            } else
				dstNote = getDrumMap(track).get(noteId);

			return (dstNote == LotroDrumInfo.DISABLED.note.id) ? null : Note.fromId(dstNote);
		} else {
			noteId += getTranspose(track, tickStart);
			Pair<Integer,Integer> limits = getSectionPitchLimits(track, tickStart);

			if (noteId + getInstrument().octaveDelta * 12 > limits.second || noteId + getInstrument().octaveDelta * 12 < limits.first) {
				return null;
			}
			int lowest = instrument.lowestPlayable.id;
			if (isStudentPart() && !isStudentFromABC())
				lowest = LotroInstrument.STUDENT_CHROMATIC_LOWEST.id;
			
			while (noteId < lowest)
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
     */
	public Note mapNoteEvent(int track, NoteEvent ne) {
		return mapNoteEvent(track, ne, ne.note.id);
	}
	
	public Note mapNoteEvent(int track, NoteEvent ne, int noteId) {
		return mapNoteEvent(track, ne, noteId, false);
	}
	
	public Note mapNoteEvent(int track, NoteEvent ne, boolean skipAudibleCheck) {
		return mapNoteEvent(track, ne, ne.note.id, skipAudibleCheck);
	}
	
	/**
	 * Maps from a MIDI note to an ABC note. If no mapping is available, returns <code>null</code>.
	 * 
	 * This method will also handle bent notes.
	 * 
	 * @param track track
	 * @param ne noteevent
	 * @param noteId use a custom note id
     */
	public Note mapNoteEvent(int track, NoteEvent ne, int noteId, boolean skipAudibleCheck) {
		long tickStart = ne.getStartTick();
		if (!skipAudibleCheck && !getAudible(track, tickStart)) {
			return null;
		}
		if (noteId < 0 || noteId > 127) {
			// extra check for invalid noteid that can make drum-map throw exception.
			return null;
		}
		
		if (!isChromatic(track)) {
			if (!isPercussionNoteEnabled(track, noteId))
				return null;

			int dstNote;
			if (instrument == LotroInstrument.BASIC_COWBELL)
				dstNote = Note.G2.id; // "Tom High 1"
			else if (instrument == LotroInstrument.MOOR_COWBELL)
				dstNote = Note.A2.id; // "Tom High 2"
			else if (isStudentPart())
				dstNote = getFXMap(track).get(noteId);
            else if (isJauntyHandKnellsPart()) {
                dstNote = getJauntyHandKnellsFXMap(track).get(noteId);
                if (dstNote == LotroChromaticFXInfo.DISABLED.note.id) {
                    return null;
                }
                dstNote += getFXTranspose(track, tickStart);
                while (dstNote < instrument.lowestPlayable.id)
                    dstNote += 12;
                while (dstNote > instrument.highestPlayable.id)
                    dstNote -= 12;
            } else
				dstNote = getDrumMap(track).get(noteId);

			return (dstNote == LotroDrumInfo.DISABLED.note.id) ? null : Note.fromId(dstNote);
		} else if (ne instanceof BentMidiNoteEvent be) {

            int minBend = be.getMinBend();
			int maxBend = be.getMaxBend();
			int transpose = getTranspose(track, tickStart);
			Pair<Integer,Integer> limits = getSectionPitchLimits(track, tickStart);
			noteId += transpose;
			minBend += noteId;
			maxBend += noteId;
			
			if (minBend + getInstrument().octaveDelta * 12 > limits.second || minBend + getInstrument().octaveDelta * 12 < limits.first) {
				// For testing bent notes against section-ediotr note limits, we consider only the lowest pitch (not remember why)
				return null;
			}
			
			int lowest = instrument.lowestPlayable.id;
			if (isStudentPart() && !isStudentFromABC())
				lowest = LotroInstrument.STUDENT_CHROMATIC_LOWEST.id;

			int octaveFittingMin = 0;
			while (minBend < lowest) {
				minBend += 12;
				octaveFittingMin += 12;
			}
			while (minBend > instrument.highestPlayable.id) {
				minBend -= 12;
				octaveFittingMin -= 12;
			}

			int octaveFittingMax = 0;
			while (maxBend < lowest) {
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
				noteId += octaveFittingMax;
			} else if (octaveFittingMin > 0) {
				noteId += octaveFittingMin;
			}
			
			if (isStudentPart() && octaveFittingMax != 0 && octaveFittingMin != 0) {
				//System.out.println("\n"+noteId+": octaveFittingMax:"+ octaveFittingMax+" octaveFittingMin:"+octaveFittingMin+" minBend:"+be.getMinBend()+" maxBend:"+be.getMaxBend());
				//System.out.println("final absolute: "+(be.getMinBend()+noteId)+" to "+(be.getMaxBend()+noteId)+"  instrument limits is "+lowest+" to "+instrument.highestPlayable.id);
			}
			
			if (noteId < Note.MIN.id || noteId > Note.MAX.id) {
				/*
				 * 
				 * TODO: Not ideal to drop the entire bent note. Sigh.
				 * 
				 */
				return null;
			}

			return Note.fromId(noteId);
		} else {
			noteId += getTranspose(track, tickStart);
			Pair<Integer,Integer> limits = getSectionPitchLimits(track, tickStart);
			
			if (noteId + getInstrument().octaveDelta * 12 > limits.second || noteId + getInstrument().octaveDelta * 12 < limits.first) {
				return null;
			}
			
			int lowest = instrument.lowestPlayable.id;
			if (isStudentPart() && !isStudentFromABC())
				lowest = LotroInstrument.STUDENT_CHROMATIC_LOWEST.id;
			
			while (noteId < lowest)
				noteId += 12;
			while (noteId > instrument.highestPlayable.id)
				noteId -= 12;
			return Note.fromId(noteId);
		}
	}

	public boolean shouldPlay(NoteEvent ne, int track) {
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
						if (ne.getStartTick() < startTick) {
							startTick = ne.getStartTick();
						}
						break;
					}
				}
			}
		}

		return startTick;
	}

	public long lastNoteEndTick(boolean accountForSustain, QuantizedTimingInfo qtm, boolean organic) {
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
							if (conclusionFermata != 0 && instrument.isSustainable(tone.id)) {
								// we need to do this so duration in part names and metadata includes fermata
								if (organic) {
									noteEndTick = qtm.microsToTickABCOrganic(qtm.tickToMicrosABCOrganic(noteEndTick)+1000L*conclusionFermata);
								} else {
									noteEndTick = qtm.microsToTickABC(qtm.tickToMicrosABC(noteEndTick)+1000L*conclusionFermata);
								}
							}
						} else {
							long dura = AbcConstants.ONE_SECOND_MICROS;
							try {
								int pitch = tone.id;
								if (getInstrument() == LotroInstrument.BASIC_COWBELL || getInstrument() == LotroInstrument.MOOR_COWBELL) {
									pitch = AbcConstants.COWBELL_NOTE_ID;
								}
								dura = LotroInstrumentSampleDuration.getDura(getInstrument().friendlyName, pitch);								
							} catch (IOException | NullPointerException e) {
								// will give null pointer if tone is not contained in the map, in conversion from Double to double.
							}
							if (organic) {
								noteEndTick = qtm.microsToTickABCOrganic( qtm.tickToMicrosABCOrganic(ne.getStartTick()) + dura );
							} else {
								noteEndTick = qtm.microsToTickABC( qtm.tickToMicrosABC(ne.getStartTick()) + dura );
							}
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

    @NotNull
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

    /**
     * Type number is when there are multiple parts with the same instrument
     * and they have a number assigned after the instrument name.
     *
     * Parts with no tracks normally have assigned 0 as a type number
     * Parts whose title does not start with instr name or nickname
     * usually get typenumber of -1
     */
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
			String typeString = " " + typeNumberNew;
			if (typeNumberNew == 0) {
				typeString = "";
			}

			// The title matches either the full instrument name or its nickname; we got here
			// only because getTypeNumberMatchingTitle() found one of the two at offset 0.
			String instrPart = null;
			Pair<LotroInstrument, MatchResult> result = LotroInstrument.matchInstrument(title);
			if (result != null && result.second != null && result.second.group() != null) {
				instrPart = result.second.group();
			} else {
				Integer[] nick = matchNick(instrNameSettings.getInstrNick(instrument), title);
				if (nick != null && nick[0] == 0) {
					instrPart = title.substring(0, nick[1]);
				}
			}

			if (instrPart == null) {
				// Should be unreachable; leave both title and typeNumber untouched and report failure.
				log.severe("setTypeNumber: no instrument name or nick in title \"" + title + "\"");
				return false;
			}

			// System.out.println(" "+"Setting: "+instrPart+typeString);
			typeNumber = typeNumberNew;
			setTitle(instrPart + typeString);
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

				if (ending.isEmpty())
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

			if (ending.isEmpty())
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
			setStudentFromABC(false);
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
            // false because the line next will recompute preview anyway.
            // If changing instrument will change the order,
            // it will also produce yet another preview generation (in addition to the fermata one) after sorting:
			fireChangeEvent(AbcPartProperty.INSTRUMENT, false);
			fireChangeEvent(AbcPartProperty.CONCLUSION_FERMATA_EDIT);// to make sure song end tick get recalculated in case we switch between sustain and not.
		}
	}

    @Override
    public int getFirstNumber() {
        return firstNumber;
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
		return getTranspose(track, tickStart, true);
	}

	public int getTranspose(int track, long tickStart, boolean includeEditorChanges) {
		if (isPercussionPart())
			return 0;
		int temp = abcSong.getTranspose() + trackTranspose[track] - getInstrument().octaveDelta * 12;
		if (includeEditorChanges) {
			temp += abcSong.getTuneTranspose(tickStart) + getSectionTranspose(tickStart, track);
		}
		return temp;
	}

    /**
     * Only includes section-editor and tune-editor transpose.
     * Used only by Jaunty for now
     */
    public int getFXTranspose(int track, long tickStart) {
        int temp = getSectionTranspose(tickStart, track);
        if (!isDrumTrack(track)) {
            temp += getAbcSong().getTuneTranspose(tickStart);
        }
        return temp;
    }
	
	public Pair<Integer, Integer> getSectionPitchLimits(int track, long tickStart) {
		Pair<Integer, Integer> secLimits = new Pair<>(minDefault.id,Note.MAX.id);
		if (isPercussionPart())
			return secLimits;
		if (!isTrackEnabled(track))
			return secLimits;
		
		if (sectionsTicked != null) {
			TreeMap<Long, PartSection> tree = sectionsTicked.get(track);
			
			if (tree != null) {	
				Entry<Long, PartSection> entry = tree.floorEntry(tickStart);
				if (entry != null) {
					if (tickStart < entry.getValue().endTick) {
						secLimits.first = entry.getValue().fromPitch.id;
						secLimits.second = entry.getValue().toPitch.id;
						return secLimits;
					}
				}
			}
		}
		
		if (nonSection.get(track) != null) {
			return new Pair<>(nonSection.get(track).fromPitch.id, nonSection.get(track).toPitch.id);
		}

		return secLimits;
	}

	public int getSectionTranspose(long tickStart, int track) {
		int secTrans = 0;
		if (!isTrackEnabled(track))
			return secTrans;
		if (isPercussionPart())
			return secTrans;
		
		if (sectionsTicked != null) {
			TreeMap<Long, PartSection> tree = sectionsTicked.get(track);
			if (tree != null) {
				Entry<Long, PartSection> entry = tree.floorEntry(tickStart);
				if (entry != null) {
					if (tickStart < entry.getValue().endTick) {
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
		if (isPercussionPart())
			return secDoubling;
		
		if (sectionsTicked != null) {
			TreeMap<Long, PartSection> tree = sectionsTicked.get(track);
			
			if (tree != null) {				
				Entry<Long, PartSection> entry = tree.floorEntry(tickStart);
				if (entry != null) {
					if (tickStart < entry.getValue().endTick) {
						secDoubling = entry.getValue().doubling;
						return secDoubling;
					}
				}			
			}
		}
		if (nonSection.get(track) != null) {
			secDoubling = nonSection.get(track).doubling;
		}

		return secDoubling;
	}

	/**
	 * @return velocity of the noteEvent, or is reset velocities active, then mezzoforte
	 */
	public int getSectionNoteVelocity(int track, NoteEvent ne) {
		
		if (sectionsTicked != null) {
			TreeMap<Long, PartSection> tree = sectionsTicked.get(track);
			
			if (tree != null) {
					Entry<Long, PartSection> entry = tree.floorEntry(ne.getStartTick());
					if (entry != null) {
						if (ne.getStartTick() < entry.getValue().endTick) {
			
							return entry.getValue().resetVelocities ? Dynamics.DEFAULT.midiVol : ne.velocity;
						}
					}
			}
		}
		if (nonSection.get(track) != null) {
			return nonSection.get(track).resetVelocities ? Dynamics.DEFAULT.midiVol : ne.velocity;
		}

		return ne.velocity;
	}
	
	public boolean getSectionLegato(int track, long tick) {
		if (sectionsTicked != null) {
			TreeMap<Long, PartSection> tree = sectionsTicked.get(track);
			
			if (tree != null) {
				Entry<Long, PartSection> entry = tree.floorEntry(tick);
				if (entry != null) {
					if (tick < entry.getValue().endTick) {
						return entry.getValue().legato;
					}
				}
			}
		}
		if (nonSection.get(track) != null) {
			return nonSection.get(track).legato;
		}

		return false;
	}

	public int[] getSectionVolumeAdjust(int track, NoteEvent ne) {
		int delta = 0;// volume offset
		int factor = 100;// current fade-out volume factor
		int factorTune = 100;// current fade-out volume factor (for tuneeditor)
		long tick = ne.getStartTick();
		
		TreeMap<Long, PartSection> tree = null;
		if (sectionsTicked != null) {
			tree = sectionsTicked.get(track);
		}
		if (tree != null) {
			Entry<Long, PartSection> entry = tree.floorEntry(tick);
			if (entry != null) {
				if (tick < entry.getValue().endTick) {
					delta = entry.getValue().volumeStep;
					factor = computeFadeFactor(tick, entry.getValue().startTick, entry.getValue().endTick, entry.getValue().fade);
				}
			}
		}
		
		NavigableMap<Float, TuneLine> tuneMap = abcSong.tuneBars;
		if (tuneMap != null) {
			for (TuneLine value : tuneMap.values()) {
				if (tick < value.endTick && tick >= value.startTick) {
					factorTune = computeFadeFactor(tick, value.startTick, value.endTick, value.fade);
					break;
				}
			}
		}
		return new int[] {delta, factor, factorTune};
	}
	
	private int computeFadeFactor(long tick, long startTick, long endTick, int fade) {
	    if (fade > 0) {
	        return Util.mapBig(tick, startTick, endTick, 100, 100 - fade);
	    } else if (fade < 0) {
	        return Util.mapBig(tick, startTick, endTick, 100 + fade, 100);
	    }
	    return 100;
	}
	
	public boolean getAudible(int track, long tickStart) {
		return getAudible(track, tickStart, true);
	}

	/**
	 * Check if a note at certain tick should not be silenced by tune or section editor.
	 * 
	 * @param active if false then ignore section-editor silence and only consider tune-editor silence.
	 * @return false if silenced
	 */
	public boolean getAudible(int track, long tickStart, boolean active) {
		long firstBarTick = abcSong.getFirstBarTick();
		long lastBarTick  = abcSong.getLastBarTick();
				
		if (abcSong.getFirstBar() != null && tickStart < firstBarTick) {
			return false;
		}
		if (abcSong.getLastBar() != null && tickStart >= lastBarTick) {
			return false;
		}
		
		if (sectionsTicked != null) {
			TreeMap<Long, PartSection> tree = sectionsTicked.get(track);
	
			if (tree != null && active) {
				Entry<Long, PartSection> entry = tree.floorEntry(tickStart);
				if (entry != null) {
					if (tickStart < entry.getValue().endTick) {
						return !entry.getValue().silence;
					}
				}
			}
		}

		if (nonSection.get(track) != null && active) {
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
	    if (this.previewSequenceTrackNumber != previewSequenceTrackNumber) {
		    this.previewSequenceTrackNumber = previewSequenceTrackNumber;
            fireChangeEvent(AbcPartProperty.PREVIEW_TRACK_NUMBER);
        }
	}

	public int getPreviewSequenceTrackNumber() {
		return previewSequenceTrackNumber;
	}

	@Override
	public int getPartNumber() {
		return partNumber;
	}

    @Override
    public Boolean isPartNumberManuallyAssigned() {
        return partNumberManuallyModified;
    }

    @Override
    public void setPartNumberManuallyAssigned(boolean manuallyAssigned, boolean notifyListeners) {
        partNumberManuallyModified = manuallyAssigned;
        if (notifyListeners) fireChangeEvent(AbcPartProperty.PART_NUMBER_MANUAL);
    }

    @Override
    public void notifyPartNumberManuallyAssigned() {
        fireChangeEvent(AbcPartProperty.PART_NUMBER_MANUAL);
    }

    @Override
	public void setPartNumber(int partNumber) {
		if (this.partNumber != partNumber) {
            log.finer(getTitle()+": setPartNumber: "+this.partNumber+" -> "+partNumber);
			this.partNumber = partNumber;
			fireChangeEvent(AbcPartProperty.PART_NUMBER);
		}
	}

	public boolean isMuted() {
		return muted;
	}

	public void setMuted(boolean muted) {
		if (this.muted != muted) {
			this.muted = muted;
			fireChangeEvent(AbcPartProperty.EXCLUSION);
		}
	}

	public boolean isSoloed() {
		return soloed;
	}

	public void setSoloed(boolean soloed) {
		if (this.soloed != soloed) {
			this.soloed = soloed;
			fireChangeEvent(AbcPartProperty.EXCLUSION);
		}
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

    /**
     * If drum/fx hit panels should be hidden
     */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isChromatic(int track) {
		if (isStudentPart() || isJauntyHandKnellsPart()) {
			return !fx[track] || isStudentFromABC();
		}
		return !isPercussionPart();
	}

    /**
     * If the FX checkbox is checked for student or jaunty.
     * Will always return false for student loaded from ABC source.
     */
	public boolean isFX(int track) {
		if (isStudentFromABC()) return false;
		if (!isStudentPart() && !isJauntyHandKnellsPart()) {
			return false;
		}
		return fx[track];
	}

	public void setFX(int track, boolean enabled) {
		if (fx[track] != enabled) {
			fx[track] = enabled;
			abcSong.setMixDirty(true);
            fireChangeEvent(AbcPartProperty.FX, track);
		}
	}
	
	public void setStudentFromABC(boolean studentFromABCSource) {
        // student parts from ABC source will have gotten this enabled, else false
		studentFromABC = studentFromABCSource;
	}

    /**
     * if it is a student part and loaded from ABC source
     */
	public boolean isStudentFromABC() {
		if (!isStudentPart()) studentFromABC = false;
		return studentFromABC;
	}
	
	public boolean isDrumPart() {
		return instrument == LotroInstrument.BASIC_DRUM;
	}

	public boolean isCowbellPart() {
		return instrument == LotroInstrument.BASIC_COWBELL || instrument == LotroInstrument.MOOR_COWBELL;
	}

	public boolean isStudentPart() {
		return instrument == LotroInstrument.STUDENT_FIDDLE;
	}

    public boolean isJauntyHandKnellsPart() {
        return instrument == LotroInstrument.JAUNTY_HAND_KNELLS;
    }

	public boolean isDrumTrack(int track) {
		return abcSong.getSequenceInfo().getTrackInfo(track).isDrumTrack();
	}

	/**
	 * Returns the DrumNoteMap for the given track. If the track is not a drum track, returns null.
	 * If the map does not exist, then one is created, to avoid that use peekDrumMap() instead.
	 */
	public DrumNoteMap getDrumMap(int track) {
        if (discarded) return new PassThroughDrumNoteMap(abcSong.getCombiInfo());
		if (drumNoteMap[track] == null) {
			if (!abcSong.getSequenceInfo().getTrackInfo(track).isDrumTrack()) {
				// For non-drum tracks, just use a straight pass-through
				drumNoteMap[track] = new PassThroughDrumNoteMap(abcSong.getCombiInfo());
			} else {
				drumNoteMap[track] = new DrumNoteMap(abcSong.getCombiInfo());
				if (abcSong.getCombiInfo() != null && abcSong.getCombiInfo().usePrefs) {
					drumNoteMap[track].loadTemplate();
				}
			}
			drumNoteMap[track].addChangeListener(drumMapChangeListener);
		}
		return drumNoteMap[track];
	}

	/**
	 * Returns the DrumNoteMap for the given track. If the track is not a drum track, returns null.
	 * Will return null, if the map does not exist.
	 */
	public DrumNoteMap peekDrumMap(int track) {
		if (discarded) return null;
		return drumNoteMap[track];
	}

	public StudentFXNoteMap getFXMap(int track) {
        if (discarded) return new PassThroughFXNoteMap();
		if (studentFxNoteMap[track] == null) {
			// For non-drum tracks, just use a straight pass-through
			// if (!abcSong.getSequenceInfo().getTrackInfo(track).isDrumTrack())
			// {
			studentFxNoteMap[track] = new PassThroughFXNoteMap();
			// }
			// else
			// {
			// drumNoteMap[track] = new StudentFXNoteMap();
			// drumNoteMap[track].load(drumPrefs);
			// }
			studentFxNoteMap[track].addChangeListener(drumMapChangeListener);
		}
		return studentFxNoteMap[track];
	}

    public JauntyHandKnellsFXNoteMap getJauntyHandKnellsFXMap(int track) {
        if (discarded) return new JauntyHandKnellsFXNoteMap();
        if (jauntyHandKnellsFXNoteMap[track] == null) {
            jauntyHandKnellsFXNoteMap[track] = new JauntyHandKnellsFXNoteMap();
			if (abcSong.getCombiInfo() != null && abcSong.getCombiInfo().usePrefs) {
				jauntyHandKnellsFXNoteMap[track].loadTemplate();
			}
            jauntyHandKnellsFXNoteMap[track].addChangeListener(drumMapChangeListener);
        }
        return jauntyHandKnellsFXNoteMap[track];
    }

	private final ChangeListener drumMapChangeListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			if (e.getSource() instanceof DrumNoteMap map) {

                // Don't write pass-through drum maps to the prefs node
				// these are used for non-drum tracks and their mapping
				// isn't desirable to save.
				if (!(map instanceof PassThroughDrumNoteMap) && !(map instanceof StudentFXNoteMap)) {
					// consider when the map is a JauntyHandKnellsFXNoteMap if that should save template..
					if (abcSong.getCombiInfo() != null && abcSong.getCombiInfo().usePrefs) {
						map.saveTemplate();
					}
				}

				abcSong.setMixDirty(true);// Some drum sounds might have been toggled, so need to recompute mixTimings
				fireChangeEvent(AbcPartProperty.DRUM_MAPPING);
			}
		}
	};

	public boolean isDrumPlayable(int track, int drumId) {
		if (isCowbellPart())
			return true;

		if (isStudentPart())
			return getFXMap(track).get(drumId) != LotroStudentFXInfo.DISABLED.note.id;

        if (isJauntyHandKnellsPart()) {
            return getJauntyHandKnellsFXMap(track).get(drumId) != LotroChromaticFXInfo.DISABLED.note.id;
        }

		return getDrumMap(track).get(drumId) != LotroDrumInfo.DISABLED.note.id;
	}

	public boolean isPercussionNoteEnabled(int track, int drumId) {
		BitSet[] enabledSet = isCowbellPart() ? cowbellsEnabled : ((isStudentPart() || isJauntyHandKnellsPart()) && isFX(track)) ? fxEnabled : drumsEnabled;

		if (enabledSet == null || enabledSet[track] == null) {
			return !isCowbellPart() || (drumId == MidiDrum.COWBELL.id())
					|| !abcSong.getSequenceInfo().getTrackInfo(track).isDrumTrack();
		}

		return enabledSet[track].get(drumId);
	}

	public void setDrumEnabled(int track, int drumId, boolean enabled) {
		if (isPercussionNoteEnabled(track, drumId) != enabled) {
			BitSet[] enabledSet;
			if (isCowbellPart()) {
				if (cowbellsEnabled == null)
					cowbellsEnabled = new BitSet[getTrackCount()];
				enabledSet = cowbellsEnabled;
			} else if (isStudentPart() || isJauntyHandKnellsPart()) {
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

    /**
     * If any section-editors have been edited on active tracks.
     */
    public boolean isSectionsEdited() {
        for (int i = 0; i < getTrackCount(); i++) {
            if (!isTrackEnabled(i)) continue;
            if (!playCenter[i] || !playRight[i] || !playLeft[i]) return true;
            if (nonSection.get(i) != null && nonSection.get(i).isEdited()) return true;
            if (sections.get(i) != null) {
                Collection<PartSection> sects = sections.get(i).values();
                for (PartSection ps : sects) {
                    if (ps.isEdited()) return true;
                }
            }
        }
        return false;
    }

	public void sectionEdited(int track) {
		convertSectionsToLongTrees();
		abcSong.setMixDirty(true); // Some notes might have gotten silenced in which case the mixTimings need to be
									// recomputed
		fireChangeEvent(AbcPartProperty.TRACK_SECTION_EDIT, track);
	}

	public void delayEdited() {
		fireChangeEvent(AbcPartProperty.DELAY_EDIT);
	}
	
	public void conclusionFermataEdited() {
		fireChangeEvent(AbcPartProperty.CONCLUSION_FERMATA_EDIT);
	}
	
	public void maxEdited() {
		fireChangeEvent(AbcPartProperty.MAX_EDIT);
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

	public int getNoteMax() {
		return noteMax;
	}

	public void setNoteMax(int noteMax) {
		if (noteMax <= AbcConstants.MAX_CHORD_NOTES && noteMax > 0)
			this.noteMax = noteMax;
	}

	public int getBadgerPrio() {
		return badgerPrio;
	}

	public void setBadgerPrio(int badgerPrio) {
		this.badgerPrio = badgerPrio;
	}

	public void setMaxPoly(int i) {
		maxPoly = i;		
	}
	
	/**
	 * Only used by abc tools
	 */
	public int getMaxPoly() {
		return maxPoly;		
	}

    public Integer getUserPan() {
        return userPan;
    }

    public void setUserPan(Integer pan) {
        if (!Objects.equals(pan, userPan)) {
            userPan = pan;
            fireChangeEvent(AbcPartProperty.USER_PAN);
        }
    }

    public AbcPart origPart = this;//copy constructor will set this.

    /**
     * Copy constructor for threaded worker.
     */
    public AbcPart(AbcPart orig, AbcSong abcSongCopy) {
        this(abcSongCopy, orig.uniqueID);

        // Primitive and Immutable Fields
        this.partNumber = orig.partNumber;
        this.partNumberManuallyModified = orig.partNumberManuallyModified;
        this.suppressSpinnerUpdate = orig.suppressSpinnerUpdate;
        this.title = orig.title;
        this.instrument = orig.instrument;
        this.studentFromABC = orig.studentFromABC;
        this.badgerPrio = orig.badgerPrio;
        this.firstNumber = orig.firstNumber;
        this.enabledTrackCount = orig.enabledTrackCount;
        this.previewSequenceTrackNumber = orig.previewSequenceTrackNumber;
        this.noteMax = orig.noteMax;
        this.delay = orig.delay;
        this.conclusionFermata = orig.conclusionFermata;
        this.typeNumber = orig.typeNumber;
        this.muted = orig.muted;
        this.soloed = orig.soloed;
        this.numberOfExportedNotes = orig.numberOfExportedNotes;
        this.numberOfRemovedNotesFromPruning = orig.numberOfRemovedNotesFromPruning;
        this.numberOfRemovedNotesZeros = orig.numberOfRemovedNotesZeros;
        this.numberOfRemovedNotesFromFitting = orig.numberOfRemovedNotesFromFitting;
        this.numberOfRemovedNotesForSafety = orig.numberOfRemovedNotesForSafety;
        this.maxPoly = orig.maxPoly;
        this.userPan = orig.userPan;
        // discarded remains false
        // abcSong, instrNameSettings, songListener, drumPrefs,
        // listeners and drumMapChangeListener are set by this(abcSongCopy).

        int origTrackCount = orig.getTrackCount();

        // Copy arrays
        System.arraycopy(orig.trackTranspose, 0, this.trackTranspose, 0, origTrackCount);
        System.arraycopy(orig.trackEnabled, 0, this.trackEnabled, 0, origTrackCount);
        System.arraycopy(orig.trackPriority, 0, this.trackPriority, 0, origTrackCount);
        System.arraycopy(orig.playLeft, 0, this.playLeft, 0, origTrackCount);
        System.arraycopy(orig.playCenter, 0, this.playCenter, 0, origTrackCount);
        System.arraycopy(orig.playRight, 0, this.playRight, 0, origTrackCount);
        System.arraycopy(orig.trackVolumeAdjust, 0, this.trackVolumeAdjust, 0, origTrackCount);
        System.arraycopy(orig.fx, 0, this.fx, 0, origTrackCount);
        if (orig.trackNames != null) {
            this.trackNames = new ArrayList<>(orig.trackNames);
        }

        // Deep copies

        // BitSet[] arrays
        if (orig.drumsEnabled != null) {
            this.drumsEnabled = new BitSet[origTrackCount];
            for (int i = 0; i < origTrackCount; i++) {
                if (orig.drumsEnabled[i] != null) {
                    this.drumsEnabled[i] = (BitSet) orig.drumsEnabled[i].clone();
                }
            }
        }
        if (orig.cowbellsEnabled != null) {
            this.cowbellsEnabled = new BitSet[origTrackCount];
            for (int i = 0; i < origTrackCount; i++) {
                if (orig.cowbellsEnabled[i] != null) {
                    this.cowbellsEnabled[i] = (BitSet) orig.cowbellsEnabled[i].clone();
                }
            }
        }
        if (orig.fxEnabled != null) {
            this.fxEnabled = new BitSet[origTrackCount];
            for (int i = 0; i < origTrackCount; i++) {
                if (orig.fxEnabled[i] != null) {
                    this.fxEnabled[i] = (BitSet) orig.fxEnabled[i].clone();
                }
            }
        }

        // DrumNoteMap[] arrays
        for (int i = 0; i < origTrackCount; i++) {
            if (orig.drumNoteMap[i] != null) {
                this.drumNoteMap[i] = orig.drumNoteMap[i].copy();
            }
            if (orig.studentFxNoteMap[i] != null) {
                this.studentFxNoteMap[i] = orig.studentFxNoteMap[i].copy();
            }
            if (orig.jauntyHandKnellsFXNoteMap[i] != null) {
                this.jauntyHandKnellsFXNoteMap[i] = orig.jauntyHandKnellsFXNoteMap[i].copy();
            }
        }

        // List<TreeMap<Float, PartSection>> sections
        // this(abcSongCopy) already initialized 'this.sections' with nulls.
        for (int i = 0; i < origTrackCount; i++) {
            TreeMap<Float, PartSection> origMap = orig.sections.get(i);
            if (origMap != null) {
                TreeMap<Float, PartSection> newMap = new TreeMap<>();
                for (Entry<Float, PartSection> entry : origMap.entrySet()) {
                    newMap.put(entry.getKey(), new PartSection(entry.getValue()));
                }
                this.sections.set(i, newMap);
            }
        }

        // List<TreeMap<Long, PartSection>> sectionsTicked
        if (orig.sectionsTicked != null) {
            this.sectionsTicked = new ArrayList<>();
            for (int i = 0; i < origTrackCount; i++) {
                this.sectionsTicked.add(null);
            }

            for (int i = 0; i < origTrackCount; i++) {
                TreeMap<Long, PartSection> origMap = orig.sectionsTicked.get(i);
                if (origMap != null) {
                    TreeMap<Long, PartSection> newMap = new TreeMap<>();
                    for (Entry<Long, PartSection> entry : origMap.entrySet()) {
                        newMap.put(entry.getKey(), new PartSection(entry.getValue()));
                    }
                    this.sectionsTicked.set(i, newMap);
                }
            }
        }

        // List<PartSection> nonSection
        // this(abcSongCopy) already initialized 'this.nonSection' with nulls.
        for (int i = 0; i < origTrackCount; i++) {
            PartSection origSection = orig.nonSection.get(i);
            if (origSection != null) {
                this.nonSection.set(i, new PartSection(origSection));
            }
        }

        // List<boolean[]> sectionsModified
        // this(abcSongCopy) already initialized 'this.sectionsModified' with nulls.
        for (int i = 0; i < origTrackCount; i++) {
            boolean[] origArray = orig.sectionsModified.get(i);
            if (origArray != null) {
                this.sectionsModified.set(i, origArray.clone());
            }
        }
        this.origPart = orig;
    }

    /**
     * Store the pan event used in the preview sequence, so we can replace it without rebuilding the entire sequence.
     */
    public void setPanEvent(MidiEvent newPanEvent) {
        this.panEvent = newPanEvent;
    }

    /**
     * Get the pan event used in the preview sequence.
     */
    public MidiEvent getPanEvent() {
        return panEvent;
    }

	/**
	 *
	 * @return delay in milliconds. Will be in -1000 to 1000 range.
	 */
	public int getDelay() {
		return delay;
	}

	/**
	 * Remember to call delayEdited() to fire the listener after setting this.
	 * @param delay delay in milliconds. Should be in -1000 to 1000 range.
	 */
	public void setDelay(int delay) {
		this.delay = Math.clamp(delay, -1000, 1000);
	}
}