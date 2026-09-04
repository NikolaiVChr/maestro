package com.digero.maestro.abc;

import static java.awt.Frame.getFrames;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.NavigableMap;

import javax.sound.midi.InvalidMidiDataException;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.xml.xpath.XPathExpressionException;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.VersionsWithIssues;
import com.digero.common.util.*;
import com.digero.common.view.UIText;
import com.digero.maestro.view.*;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.abc.StringCleaner;
import com.digero.common.abctomidi.AbcInfo;
import com.digero.common.abctomidi.AbcToMidi;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.TimeSignature;
import com.digero.maestro.MaestroMain;
import com.digero.maestro.abc.AbcPartEvent.AbcPartProperty;
import com.digero.maestro.abc.AbcSongEvent.AbcSongProperty;
import com.digero.maestro.abc.QuantizedTimingInfo.TimingInfoEvent;
import com.digero.maestro.midi.Chord;
import com.digero.maestro.midi.Chord.CalcDynamics;
import com.digero.maestro.midi.SequenceDataCache;
import com.digero.maestro.midi.SequenceInfo;
import com.digero.maestro.midi.TrackInfo;
import com.digero.maestro.util.FileResolver;
import com.digero.maestro.util.ListModelWrapper;
import com.digero.maestro.util.SaveUtil;
import com.digero.maestro.util.XmlUtil;
import com.digero.maestro.view.TimingMode;

public class AbcSong implements IDiscardable, AbcMetadataSource {
	protected static final Logger log = Logger.getLogger("song");
	
	public static final String MSX_FILE_DESCRIPTION = UIText.get("maestro.0.project", MaestroMain.APP_NAME);
	public static final String MSX_FILE_DESCRIPTION_PLURAL = UIText.get("maestro.0.projects", MaestroMain.APP_NAME);
	public static final Version SONG_FILE_VERSION = new Version(4, 6, 23, 300);// Keep build above 117 to make earlier
																				// Maestro releases know msx is
																				// made by newer version.

    public static final String NEWER_VERSION_WARNING_ID = "Never Version";
    public static final String KNOWN_ISSUE_WARNING_ID = "Known Issue";
    public static final String TEMPO_ISSUE_WARNING_ID = "Tempo Issue";
	public static final String COMBO_ISSUE_WARNING_ID = "Too many drum combos";

    private String title = "";
	private String composer = "";
	private String transcriber = "";
	private String genre = "";
	private String mood = "";
	private String note = "";// not continuously updated
    private String lyrics = "";// not continuously updated
	private List<LyricLine> lyricLines = null;// not continuously updated
	private boolean badger = false;
	private float tempoFactor = 1.0f;
	private int newTempo = 120;
	private int origTempo = 120;
	private int transpose = 0;
	private KeySignature keySignature = KeySignature.C_MAJOR;
	private TimeSignature timeSignature = TimeSignature.FOUR_FOUR;
	private boolean tripletTiming = false;
	private boolean mixTiming = true;
	private boolean organic = false;
	private boolean organic2 = false;
    private boolean upgraded = false;
	private int mixVersion = 2;// TODO: make UI?
	private boolean priorityActive = false;
	private boolean skipSilenceAtStart = true;
	private boolean deleteMinimalNotes = false;
    private boolean useRestsInChords = false;
    private boolean reducedFilesize = true;
	// private boolean showPruned = false;
	public NavigableMap<Float, TuneLine> tuneBars = null;
	public boolean[] tuneBarsModified = null;
	private Float firstBar = null;
	private Float lastBar = null;
	private long firstBarTick = -1L;
	private long lastBarTick = -1L;

	private final boolean fromAbcFile;
	private final boolean fromXmlFile;
	private SequenceInfo sequenceInfo;// TODO: Refactor name to sourceSequenceInfo
	private final PartAutoNumberer partAutoNumberer;
	private final PartNameTemplate partNameTemplate;
	private final ExportFilenameTemplate exportFilenameTemplate;
	private final InstrNameSettings instrNameSettings;
    private final MiscSettings miscSettings;
	private QuantizedTimingInfo timingInfo;
	private AbcExporter abcExporter;
	private File sourceFile; // The MIDI or ABC file that this song was loaded from
	private File newSourceFile = null;
	public final static String errorString = "ERROR";
	private File exportFile; // The ABC export file
	private File projectFile; // The XML Maestro song file
	private boolean usingOldVelocities = false;
	private boolean usingOldTempos = false;
	private int usingNewMidiLayout = 1;
    private boolean temposWereFixed = false;// If tempos were fixed in v4.3.9 or later. Future use.
	private boolean hideEdits = false;
	public final static Chord.CalcDynamics dynamicsMethodDefault = CalcDynamics.LOUDEST;
	public Chord.CalcDynamics dynamicsMethod = dynamicsMethodDefault;
	
	private boolean ignoreZeroChannelVolume = false;

	private final ListModelWrapper<AbcPart> parts;
	public boolean sorted = true;
	public boolean ignoreMidiText = false;

	private ListenerList<AbcSongEvent> listeners = new ListenerList<>();
	boolean mixDirty = true;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
            .withZone(ZoneId.of("GMT"));
	private Date firstExportTime = null;// UTC date and time for the first time this project was exported to abc.

	public boolean storeNewSourceFile = true;
	public boolean storeNewExportFile = true;
	private String copyright = "";
	private final SaveAndExportSettings saveAndExportSettings;
    private CountIn countIn = null;

	private final LotroCombiDrumInfo combiInfo;

    public AbcSong(File file, PartAutoNumberer partAutoNumberer, PartNameTemplate partNameTemplate,
			ExportFilenameTemplate exportFilenameTemplate, InstrNameSettings instrNameSettings,
			FileResolver fileResolver, MiscSettings miscSettings, SaveAndExportSettings saveAndExportSettings)
			throws IOException, InvalidMidiDataException, FileParseException, SAXException {
		this(file, partAutoNumberer, partNameTemplate, exportFilenameTemplate, instrNameSettings,
				fileResolver, miscSettings, true, saveAndExportSettings, false, null);
	}
	
	public AbcSong(File file, PartAutoNumberer partAutoNumberer, PartNameTemplate partNameTemplate,
                   ExportFilenameTemplate exportFilenameTemplate, InstrNameSettings instrNameSettings,
                   FileResolver fileResolver, MiscSettings miscSettings, boolean saveMSXwhenSourceChange,
                   SaveAndExportSettings saveAndExportSettings, boolean ignoreMidiText, WarningHandler warningHandler)
			throws IOException, InvalidMidiDataException, FileParseException, SAXException {

		combiInfo = new LotroCombiDrumInfo(!ignoreMidiText);//only load prefs when not in auto-export mode.


        parts = new ListModelWrapper<>(new DefaultListModel<>());

		storeNewSourceFile = saveMSXwhenSourceChange;
		this.partAutoNumberer = partAutoNumberer;

		this.partNameTemplate = partNameTemplate;

		this.exportFilenameTemplate = exportFilenameTemplate;

		this.instrNameSettings = instrNameSettings;

        this.miscSettings = miscSettings;
		
		this.saveAndExportSettings = saveAndExportSettings;
		
		this.ignoreMidiText = ignoreMidiText;

		String fileName = file.getName().toLowerCase();
		fromXmlFile = fileName.endsWith(Util.MSX_FILE_EXTENSION);
		fromAbcFile = fileName.endsWith(Util.ABC_FILE_EXTENSION) || fileName.endsWith(Util.TXT_FILE_EXTENSION);

		if (fromXmlFile)
			initFromXml(file, fileResolver, miscSettings, ignoreMidiText, warningHandler);
		else if (fromAbcFile)
			initFromAbc(file, miscSettings);
		else
			initFromMidi(file, miscSettings, saveAndExportSettings);
	}

	

	@Override
	public void discard() {
		fireChangeEvent(AbcSongProperty.SONG_CLOSING);

		listeners.discard();

		for (AbcPart part : parts) {
			if (part != null)
				part.discard();
		}
		parts.clear();

		tuneBarsModified = null;
		tuneBars = null;
		firstBar = null;
		lastBar = null;
		
		hideEdits = false;
		ignoreMidiText = false;

		lyricLines = null;

        CountIn.setLastCountIn(null);

		if (combiInfo != null) {
			combiInfo.removeAllListeners();
		}

		/*
		 * if (sequenceInfo != null) { // Make life easier for Garbage Collector for (TrackInfo ti :
		 * sequenceInfo.getTrackList()) { for (NoteEvent ne : ti.getEvents()) { ne.resetAllPruned(); } } }
		 */
	}

	private void initFromMidi(File file, MiscSettings miscSettings, SaveAndExportSettings saveSettings)
			throws IOException, InvalidMidiDataException, FileParseException {
		sourceFile = file;
		usingOldVelocities = miscSettings.ignoreExpressionMessages;
		TimingMode mode = TimingMode.getFromSettings(saveSettings.defaultTiming);
		setTimings(mode.organic, mode.multistage, mode.mixTimings, mode.swing, mode.priority, mode.upgraded);
		sequenceInfo = SequenceInfo.fromMidi(file, miscSettings, usingOldVelocities, usingOldTempos, false, false, usingNewMidiLayout);
		title = sequenceInfo.getTitle();
		composer = sequenceInfo.getComposer();
		if (sequenceInfo.getDataCache() != null) {
			copyright = sequenceInfo.getDataCache().getCopyright();
			lyrics = sequenceInfo.getDataCache().getLyrics();
			// lyricLines = sequenceInfo.getDataCache().getLyricLines(); done in ProjectFrame
			genre = sequenceInfo.getDataCache().getGenre();
			if (composer == null || composer.isBlank()) composer = sequenceInfo.getDataCache().getComposer(); 
		} else {
			lyrics = "";
			//lyricLines = null;
		}
        note = "";
		keySignature = (ICompileConstants.SHOW_KEY_FIELD) ? sequenceInfo.getKeySignature() : KeySignature.C_MAJOR;
		timeSignature = sequenceInfo.getTimeSignature();
		setTempoFactor(sequenceInfo.getPrimaryTempoBPM(), sequenceInfo.getPrimaryTempoBPM());
	}

	private void initFromAbc(File file, MiscSettings miscSettings)
			throws IOException, InvalidMidiDataException, FileParseException {
		AbcInfo abcInfo = new AbcInfo();
		sourceFile = file;
		AbcToMidi.Params params = new AbcToMidi.Params(file);
		params.abcInfo = abcInfo;
		params.useLotroInstruments = false;
		// params.stereo = false;
		usingOldVelocities = true;// The abc volumes are tuned to old volume scheme
		usingOldTempos = true;
		usingNewMidiLayout = 1;
		sequenceInfo = SequenceInfo.fromAbc(params, miscSettings, usingOldVelocities, ignoreMidiText, usingNewMidiLayout);
		exportFile = file;

		title = sequenceInfo.getTitle();
		composer = sequenceInfo.getComposer();
		keySignature = (ICompileConstants.SHOW_KEY_FIELD) ? abcInfo.getKeySignature() : KeySignature.C_MAJOR;
		timeSignature = abcInfo.getTimeSignature();

		int t = 0;
		// Since parts with zero part numbers will be assigned 999,
		// and 999 could be assigned already, we iterate till we find a free number:
		Set<Integer> pNumbers = new HashSet<>();
		for (TrackInfo trackInfo : sequenceInfo.getTrackList()) {
			if (!trackInfo.hasEvents()) {
				t++;
				continue;
			}

			AbcPart newPart = new AbcPart(this);

			newPart.setTitle(abcInfo.getPartName(t));
			int pNumber = abcInfo.getPartNumber(t);
			if (pNumber == 0) pNumber = 999;
			while (pNumbers.contains(pNumber)) {
				pNumber--;
				if (pNumber < 1) {
					throw new RuntimeException("Part number error");
				}
			}
			pNumbers.add(pNumber);
			newPart.setPartNumber(pNumber);
            newPart.setPartNumberManuallyAssigned(true, true);// what is loaded from abc we consider manually assigned numbers
			newPart.setTrackEnabled(t, true);
			newPart.setUserPan(abcInfo.getUserPan(t));

			Set<Integer> midiInstruments = trackInfo.getInstruments();
			for (LotroInstrument lotroInst : LotroInstrument.values()) {
				if (midiInstruments.contains(lotroInst.midi.id())) {
					newPart.setInstrument(lotroInst);
					break;
				}
			}
			if (newPart.getInstrument() == LotroInstrument.STUDENT_FIDDLE) {
				newPart.setStudentFromABC(true);
			}
			populateFirstNumbers();
			newPart.firstNumber = partAutoNumberer.getFirstNumber(newPart.getInstrument());
			int ins = Collections.binarySearch(parts, newPart, partAutoNumberer.getComparator());
			if (ins < 0)
				ins = -ins - 1;
			parts.add(ins, newPart);
			
			newPart.addAbcListener(abcPartListener);
			t++;
		}


		tripletTiming = abcInfo.hasTriplets();
		mixTiming = abcInfo.hasMixTimings();
		organic = abcInfo.isOrganic();
		organic2 = abcInfo.isOrganic2();
        upgraded = abcInfo.isOrganicV2();
		priorityActive = false;
		transcriber = abcInfo.getTranscriber();
		genre = abcInfo.getGenre();
		mood = abcInfo.getMood();
		dynamicsMethod = Chord.CalcDynamics.LOUDEST;
		setTempoFactor(abcInfo.getPrimaryTempoBPM(), abcInfo.getPrimaryTempoBPM());
		lyrics = "";
		lyricLines = null;
        note = "";
	}

	@SuppressWarnings("HardCodedStringLiteral")
	private void initFromXml(File file, FileResolver fileResolver, MiscSettings miscSettings, boolean calledFromTools,
                             WarningHandler warningHandler)
			throws SAXException, IOException, FileParseException {
		try {
			projectFile = file;
			Document doc = XmlUtil.openDocument(projectFile);
			Element songEle = XmlUtil.selectSingleElement(doc, "song");
			if (songEle == null) {
				throw new FileParseException("Does not appear to be a valid Maestro file. Missing <song> root element.",
						projectFile.getName());
			}
			Version fileVersion = SaveUtil.parseValue(songEle, "@fileVersion", SONG_FILE_VERSION);

			if (isFileNewer(fileVersion)) {
                if (warningHandler != null) {
                    String message = UIText.get("maestro.project.0.was.saved.with.a.newer.version", projectFile.getName());

                    WarningHandler.WarningAction action = warningHandler.handleWarning(
                            NEWER_VERSION_WARNING_ID, UIText.get("maestro.newer.project.version"), message);

                    if (action == WarningHandler.WarningAction.SKIP_FILE) {
                        throw new FileParseException("Skipped file (newer version) by user request.", projectFile.getName());
                    }
                } else if (getFrames().length > 0) {
                    JOptionPane.showMessageDialog(getFrames()[0],
							UIText.get("maestro.this.project.may.contain.new.features.that.this.maestro.cannot.use"),
							UIText.get("maestro.warning"), JOptionPane.WARNING_MESSAGE);
                }
			}

			dynamicsMethod = Chord.CalcDynamics.fromString(SaveUtil.parseValue(songEle, "exportSettings/@calcDynamics", Chord.CalcDynamics.LOUDEST.name()));
			usingOldVelocities = SaveUtil.parseValue(songEle, "importSettings/@useOldVelocities", true);// must be
			usingOldTempos     = SaveUtil.parseValue(songEle, "importSettings/@useOldTempos", true);    // before
			usingNewMidiLayout = SaveUtil.parseValue(songEle, "importSettings/@useNewMidiLayout", 0);    // tryToLoadFromFile
			ignoreZeroChannelVolume = SaveUtil.parseValue(songEle, "importSettings/@ignoreZeroChannelVolume", false);
			
			sourceFile = SaveUtil.parseValue(songEle, "sourceFile", (File) null);
			if (sourceFile == null) {
				throw SaveUtil.missingValueException(songEle, "<sourceFile>");
			}
			File origSourceFile = sourceFile;

			exportFile = SaveUtil.parseValue(songEle, "exportFile", exportFile);

			sequenceInfo = null;
			String name = sourceFile.getName().toLowerCase();
			boolean isAbc = name.endsWith(Util.ABC_FILE_EXTENSION) || name.endsWith(Util.TXT_FILE_EXTENSION);
			int attempts = 0;
			while (sequenceInfo == null) {
				if (++attempts > 20) {
					throw new FileParseException("Gave up loading source file after " + (attempts - 1) + " attempts", name);
				}
				tryToLoadFromFile(fileResolver, isAbc, miscSettings, warningHandler);

				if (newSourceFile == null)
					throw new FileParseException("Failed to load file", name);
			}

			if (!sourceFile.equals(origSourceFile)) {
				MaestroMain.setMIDIFileResolved();
			}
            if (!calledFromTools) {
                lyrics = sequenceInfo.getDataCache().getLyrics();
				//lyricLines = sequenceInfo.getDataCache().getLyricLines();
            }
			title = SaveUtil.parseValue(songEle, "title", sequenceInfo.getTitle());
			composer = SaveUtil.parseValue(songEle, "composer", sequenceInfo.getComposer());
			transcriber = SaveUtil.parseValue(songEle, "transcriber", transcriber);
			genre = SaveUtil.parseValue(songEle, "genre", genre);
			mood = SaveUtil.parseValue(songEle, "mood", mood);
			note = SaveUtil.parseValue(songEle, "note", "");
			sorted = SaveUtil.parseValue(songEle, "autoSortedParts", true);
            temposWereFixed = SaveUtil.parseValue(songEle, "temposWereFixed", false);
			
			String exportTimeStr = SaveUtil.parseValue(songEle, "firstExportTime", "");
			if (!exportTimeStr.isEmpty()) {
               try {
                    Instant instant = Instant.from(DATE_TIME_FORMATTER.parse(exportTimeStr));
                    firstExportTime = Date.from(instant);
                } catch (java.time.format.DateTimeParseException ignored) {
                }
			} else if (exportFile != null) {
				// Project has been saved before, but we don't know when.
				firstExportTime = new Date(0);// 1970-01-01-00:00:00
			}
			
			if (fileVersion.compareTo(new Version(3, 3, 7, 300)) < 0) {
				int lastBarI = SaveUtil.parseValue(songEle, "lastBar", -1);
				if (lastBarI < 1) lastBar = null;
				else lastBar = (float)lastBarI;
				int firstBarI = SaveUtil.parseValue(songEle, "firstBar", -1);
				if (firstBarI < 1) firstBar = null;
				else firstBar = (float)(firstBarI - 1);
			} else {
				lastBar = SaveUtil.parseValue(songEle, "lastBar", -1.0f);
				if (lastBar < 0.0f) lastBar = null;
				firstBar = SaveUtil.parseValue(songEle, "firstBar", -1.0f);
				if (firstBar < 0.0f) firstBar = null;
			}

			float factor = SaveUtil.parseValue(songEle, "exportSettings/@tempoFactor", tempoFactor);
			
			setTempoFactor(Math.round(factor*sequenceInfo.getPrimaryTempoBPM()), sequenceInfo.getPrimaryTempoBPM());
			
			transpose = SaveUtil.parseValue(songEle, "exportSettings/@transpose", transpose);
			if (ICompileConstants.SHOW_KEY_FIELD)
				keySignature = SaveUtil.parseValue(songEle, "exportSettings/@keySignature", keySignature);
			timeSignature = SaveUtil.parseValue(songEle, "exportSettings/@timeSignature", timeSignature);
			
			organic = SaveUtil.parseValue(songEle, "exportSettings/@organic", false);
			organic2 = SaveUtil.parseValue(songEle, "exportSettings/@organic-multi-stage", false);
            int orgVersion = SaveUtil.parseValue(songEle, "exportSettings/@organic-version", 1);
            if (organic && organic2) {
                if (orgVersion == 2) upgraded = true;
                else upgraded = false;
            } else {
                upgraded = false;
            }
			tripletTiming = SaveUtil.parseValue(songEle, "exportSettings/@tripletTiming", tripletTiming);

			mixTiming = SaveUtil.parseValue(songEle, "exportSettings/@mixTiming", false);// default false as old
																							// projects did not have
																							// that available. This
																							// means for old project
																							// with source abc that was
																							// exported with mix
																							// timings, the project will
																							// decide and it will be
																							// false.

			// if (mixTiming)
			// mixVersion = SaveUtil.parseValue(songEle, "exportSettings/@mixVersion", 1);// default 1 as old projects
			// did not have that available.

			priorityActive = SaveUtil.parseValue(songEle, "exportSettings/@combinePriorities", false);

			handleTuneSections(songEle, fileVersion);

			loadPartsFromXML(songEle, fileVersion, sorted, warningHandler);

			Version def = new Version(0,0,0);
			Version maestroVersion = SaveUtil.parseValue(songEle, "@maestroVersion", def);
			if (!def.equals(maestroVersion)) {
				String issue = VersionsWithIssues.checkProject(maestroVersion);
                if (issue != null) {
                    if (warningHandler != null) {
                        String message = UIText.get("maestro.project.0.was.saved.with.maestro.version.1.which.had.this.issue.2", file.getName(), maestroVersion, issue);
                        WarningHandler.WarningAction action = warningHandler.handleWarning(
                                KNOWN_ISSUE_WARNING_ID, UIText.get("maestro.known.issue.version"), message);
                        if (action == WarningHandler.WarningAction.SKIP_FILE) {
                            throw new FileParseException("Skipped file (known issue version) by user request.", projectFile.getName());
                        }
                    } else {
                        JOptionPane.showMessageDialog(null,
								UIText.get("maestro.project.was.saved.with.maestro.version.0.which.had.this.issue.1", maestroVersion, issue),
								UIText.get("maestro.warning.for.0", file.getName()), JOptionPane.WARNING_MESSAGE);
                    }
                }
			}
            if (sequenceInfo.getDataCache().isTempoInHigherTracks()
                    && !usingOldTempos && !maestroVersion.equals(def)
                    && maestroVersion.compareTo(new Version(4, 3, 9)) < 0) {
                log.warning(UIText.get("maestro.warning.tempos.in.0.project.has.been.fixed", file.getName()));
                temposWereFixed = true;
                if (warningHandler != null) {
                    String message = UIText.get("maestro.warning.tempos.in.0.project.should.be.fixed", file.getName());
                    WarningHandler.WarningAction action = warningHandler.handleWarning(
                            TEMPO_ISSUE_WARNING_ID, UIText.get("maestro.important.question"), message);
                    if (action == WarningHandler.WarningAction.SKIP_FILE) {
                        throw new FileParseException("Skipped file (tempo issue) by user request. Project needs to be reviewed in Maestro.", projectFile.getName());
                    }
                } else {
                    JOptionPane.showMessageDialog(null,
							UIText.get("maestro.warning.tempos.in.0.project.has.been.fixed.for.potential.problems", file.getName()),
							UIText.get("maestro.warning.for.0", file.getName()), JOptionPane.WARNING_MESSAGE);
                }
            }
			Element lyricsContainer = XmlUtil.selectSingleElement(songEle, "lyrics");
			if (lyricsContainer != null) {
				List<LyricLine> loadedLyrics = new ArrayList<>();
				SequenceDataCache data = sequenceInfo.getDataCache();

				for (Element lineEle : XmlUtil.selectElements(lyricsContainer, "line")) {
					float bar = SaveUtil.parseValue(lineEle, "@bar", 0.0f);
					float barEnd = SaveUtil.parseValue(lineEle, "@barEnd", bar);
					String text = lineEle.getTextContent();

					long tick = data.barFloatToTick(bar);
					long tickEnd = data.barFloatToTick(barEnd);

					loadedLyrics.add(new LyricLine(tick, text, tickEnd));
				}
				if (!loadedLyrics.isEmpty()) lyricLines = loadedLyrics;
			}

			List<String> combiWarnings = new ArrayList<>();
			for (AbcPart part : parts) {
				for (int t = 0; t < part.getTrackCount(); t++) {
					DrumNoteMap dm = part.peekDrumMap(t);
					if (dm != null) combiWarnings.addAll(dm.getLastLoadCombiWarnings());
				}
			}
			if (!combiWarnings.isEmpty()) {
				String message = UIText.get("maestro.warning.combi.degraded.0.1",
						file.getName(), String.join(", ", combiWarnings));
				log.warning("Combi library full while loading " + file.getName()
						+ " - degraded: " + String.join(", ", combiWarnings));   // always logged, batch-visible
				if (warningHandler != null) {
					WarningHandler.WarningAction action = warningHandler.handleWarning(COMBO_ISSUE_WARNING_ID, UIText.get("maestro.warning.combi.degraded.full"), message);
					if (action == WarningHandler.WarningAction.SKIP_FILE) {
						throw new FileParseException("Skipped file (combo issue) by user request. Project needs to be reviewed in Maestro.", projectFile.getName());
					}
				} else {
					JOptionPane.showMessageDialog(null, message,
							UIText.get("maestro.warning.combi.degraded.full"), JOptionPane.WARNING_MESSAGE);
				}
			}
		} catch (XPathExpressionException e) {
			log.log(Level.SEVERE, "XPath error", e);
			throw new FileParseException("XPath error: " + e.getMessage(), file == null?null:file.getName());
		}
	}

	private boolean isFileNewer(Version fileVersion) {
		if (fileVersion.getMajor() == 1 && fileVersion.getMinor() == 0) {
			// Convert the msx 1.0.X format into Maestro version format.
			fileVersion = new Version(2, 5, 0, fileVersion.getRevision());
		}
        return fileVersion.compareTo(SONG_FILE_VERSION) > 0;
    }

    /**
     * Load the source file of a project
     */
	private void tryToLoadFromFile(FileResolver fileResolver, boolean isAbc, MiscSettings miscSettings, WarningHandler warningHandler) {
		if (newSourceFile == null) newSourceFile = sourceFile;
		try {
			File sourceInCurrentDir = new File(projectFile.getParentFile(), newSourceFile.getName());
			if (!newSourceFile.exists() && sourceInCurrentDir.exists()) {
				newSourceFile = sourceInCurrentDir;
			}
			
			if (isAbc) {
				AbcInfo abcInfo = new AbcInfo();

				AbcToMidi.Params params = new AbcToMidi.Params(newSourceFile);
				params.abcInfo = abcInfo;
				params.useLotroInstruments = false;
                params.warningHandler = warningHandler;
				// params.stereo = false;
				usingOldVelocities = true;// The abc volumes are tuned to old volume scheme
				usingOldTempos = true;
				usingNewMidiLayout = 1;
				sequenceInfo = SequenceInfo.fromAbc(params, miscSettings, usingOldVelocities, ignoreMidiText, usingNewMidiLayout);

				organic = abcInfo.isOrganic();
				organic2 = abcInfo.isOrganic2();
                upgraded = abcInfo.isOrganicV2();
				tripletTiming = abcInfo.hasTriplets();
				mixTiming = abcInfo.hasMixTimings();
				priorityActive = false;
				transcriber = abcInfo.getTranscriber();
			} else {
				sequenceInfo = SequenceInfo.fromMidi(newSourceFile, miscSettings, usingOldVelocities, usingOldTempos, ignoreZeroChannelVolume, ignoreMidiText, usingNewMidiLayout);
			}

			title = sequenceInfo.getTitle();
			composer = sequenceInfo.getComposer();
			if (sequenceInfo.getDataCache() != null) {
				copyright = sequenceInfo.getDataCache().getCopyright();
			}
			keySignature = (ICompileConstants.SHOW_KEY_FIELD) ? sequenceInfo.getKeySignature() : KeySignature.C_MAJOR;
			timeSignature = sequenceInfo.getTimeSignature();
		} catch (FileNotFoundException e) {
			String msg = UIText.get("maestro.could.not.find.the.file.used.to.create.this.song.0", newSourceFile);
			newSourceFile = fileResolver.locateFile(newSourceFile, msg);
		} catch (InvalidMidiDataException | IOException | FileParseException e) {
			String msg = UIText.get("maestro.could.not.load.the.file.used.to.create.this.song.0.1", newSourceFile, e.getMessage());
			newSourceFile = fileResolver.resolveFile(newSourceFile, msg);
		}
		if (storeNewSourceFile) {
			sourceFile = newSourceFile;
		}
	}
	
	public void convertTunelinesToLongs () {
		SequenceInfo se = getSequenceInfo();
		
		if (se == null) {
			throw new RuntimeException("Error in floating point tuneline");
		}
		
		SequenceDataCache data = se.getDataCache();
		
		if (tuneBars != null) {
			for (TuneLine tuneLine : tuneBars.values()) {
				assert tuneLine.startTick == -1L;
				assert tuneLine.endTick == -1L;
				
				tuneLine.startTick = data.barFloatToTick(tuneLine.startBar);
				tuneLine.endTick   = data.barFloatToTick(tuneLine.endBar);// don't use ceil() here
			}
		}
		if (firstBar != null) {
			firstBarTick = data.barFloatToTick(firstBar);
		} else {
			firstBarTick = -1L;
		}
		if (lastBar != null) {
			lastBarTick = data.barFloatToTick(lastBar);
		} else {
			lastBarTick = -1L;
		}
	}

	/**
	 * Loading tuneline from xml
	 *
     */
	@SuppressWarnings("HardCodedStringLiteral")
	private void handleTuneSections(Element songElement, Version fileVersion) throws XPathExpressionException, FileParseException {
		float lastEnd = 0;
		for (Element tuneEle : XmlUtil.selectElements(songElement, "tuneSection")) {
			TuneLine tl = new TuneLine();
			if (fileVersion.compareTo(new Version(3, 3, 4, 300)) < 0) {
				tl.startBar = SaveUtil.parseValue(tuneEle, "startBar", 0);
				tl.endBar = SaveUtil.parseValue(tuneEle, "endBar", 0);
				tl.startBar -= 1.0f;
			} else {
				tl.startBar = SaveUtil.parseValue(tuneEle, "startBar", 0.0f);
				tl.endBar = SaveUtil.parseValue(tuneEle, "endBar", 0.0f);
			}
			tl.seminoteStep = SaveUtil.parseValue(tuneEle, "seminoteStep", 0);
			tl.tempo = SaveUtil.parseValue(tuneEle, "tempoChange", 0);
			tl.accelerando = SaveUtil.parseValue(tuneEle, "tempoAccelerando", 0);
			int fade = SaveUtil.parseValue(tuneEle, "fade", 0);
			if (fade != 0) {
				tl.fade = fade;
			}
			tl.dialogLine = SaveUtil.parseValue(tuneEle, "dialogLine", -1);
			if (tl.startBar >= 0.0f && tl.endBar > tl.startBar) {
				if (tuneBars == null) {
					tuneBars = new TreeMap<>();
				}
				if (tl.endBar > lastEnd) {
					lastEnd = tl.endBar;
				}
				tuneBars.put(tl.startBar, tl);
			}
		}
        if (lastEnd > 200_000f) { // Limit to 200k bars to prevent OOM
            log.warning("Tune section endBar too large: " + lastEnd + ". Clamping to 200,000.");
            lastEnd = 200_000f;
        }
		boolean[] booleanArray = new boolean[(int)(lastEnd) + 1];
		if (tuneBars != null) {
			for (int i = 0; i < (int)(lastEnd) + 1; i++) {
				Entry<Float, TuneLine> entry = tuneBars.lowerEntry(i + 1.0f);
				booleanArray[i] = entry != null && entry.getValue().startBar < i+1
						&& entry.getValue().endBar > i;
			}
			tuneBarsModified = booleanArray;
		}
		convertTunelinesToLongs();
	}

	@SuppressWarnings("HardCodedStringLiteral")
	private void loadPartsFromXML(Element songEle, Version fileVersion, boolean autoSorted, WarningHandler warningHandler)
			throws XPathExpressionException, FileParseException {
		for (Element ele : XmlUtil.selectElements(songEle, "part")) {
			AbcPart part = AbcPart.loadFromXml(this, ele, fileVersion, warningHandler);
			
			parts.add(part);
			part.convertSectionsToLongTrees();
			part.addAbcListener(abcPartListener);
		}
		// Since parts with zero part numbers will be assigned 999,
		// and 999 could be assigned already, we iterate till we find a free number:
		suppressPartSort = true;
		try {
			Set<Integer> pNumbers = new HashSet<>();
			for (AbcPart part : parts) {
				int pN = part.getPartNumber();
				while (pNumbers.contains(pN)) {
					pN--;
					if (pN < 1) {
						throw new RuntimeException("Part number error");
					}
					part.setPartNumber(pN);
				}
				pNumbers.add(pN);
			}
		} finally {
			suppressPartSort = false;
		}
        partAutoNumberer.assignManualPartNumber(parts);// convert all null values to booleans.
		if (autoSorted) {
            populateFirstNumbers();
            parts.sort(partAutoNumberer.getComparator());
		}
	}

	public String getLyrics() {
		// Only call this just after loading a midi.
		return lyrics;
	}

	public void notifyLyricLinesModified() {
		fireChangeEvent(AbcSongProperty.USER_LYRICS);
	}

	public List<LyricLine> getLyricLines() {
		// Only call this just after loading a midi.
		return lyricLines;
	}

	public void setLyricLines(List<LyricLine> lines, boolean fireListener) {
		// Only call this just after loading a midi.
		lyricLines = lines;
		if (fireListener) {
			fireChangeEvent(AbcSongProperty.USER_LYRICS);
		}
	}

    public String getNote() {
        // Only call this just after loading a msx file.
        return note;
    }

	public void setNote(String note, boolean fireListener) {
		this.note = note;
        if (fireListener) {
            fireChangeEvent(AbcSongProperty.USER_NOTE);
        }
	}

	@SuppressWarnings("HardCodedStringLiteral")
	public Document saveToXml() {
		Document doc = XmlUtil.createDocument();
		doc.setXmlVersion("1.1");// This will allow project files with numerical chars to later be loaded fine.
									// Like "&#11;".
		Element songEle = (Element) doc.appendChild(doc.createElement("song"));
		songEle.setAttribute("fileVersion", String.valueOf(SONG_FILE_VERSION));
		songEle.setAttribute("maestroVersion", String.valueOf(MaestroMain.APP_VERSION));

		SaveUtil.appendChildTextElement(songEle, "sourceFile", String.valueOf(sourceFile));
		//if (!copyright.isEmpty()) SaveUtil.appendChildTextElement(songEle, "midi-copyright", copyright);
		if (exportFile != null)
			SaveUtil.appendChildTextElement(songEle, "exportFile", String.valueOf(exportFile));

		SaveUtil.appendChildTextElement(songEle, "title", title);
		SaveUtil.appendChildTextElement(songEle, "composer", composer);
		SaveUtil.appendChildTextElement(songEle, "transcriber", transcriber);
		if (!genre.isEmpty())
			SaveUtil.appendChildTextElement(songEle, "genre", genre);
		if (!mood.isEmpty())
			SaveUtil.appendChildTextElement(songEle, "mood", mood);
		if (!note.isEmpty())
			SaveUtil.appendChildTextElement(songEle, "note", note);
		if (firstExportTime != null && firstExportTime.getTime() != 0L) {
			SaveUtil.appendChildTextElement(songEle, "firstExportTime", DATE_TIME_FORMATTER.format(firstExportTime.toInstant()));
		}
		SaveUtil.appendChildTextElement(songEle, "autoSortedParts", String.valueOf(sorted));

        if (temposWereFixed) {
            SaveUtil.appendChildTextElement(songEle, "temposWereFixed", String.valueOf(temposWereFixed));
        }

		appendImportSettings(doc, songEle);
		appendExportSettings(doc, songEle);

		if (tuneBars != null && tuneBarsModified != null) {
			appendTuneSections(doc, songEle);
		}
		if (lastBar != null) {
			SaveUtil.appendChildTextElement(songEle, "lastBar", String.valueOf(lastBar));
		}
		if (firstBar != null) {
			SaveUtil.appendChildTextElement(songEle, "firstBar", String.valueOf(firstBar));
		}

		for (AbcPart part : parts) {
			part.saveToXml((Element) songEle.appendChild(doc.createElement("part")));
		}

		if (lyricLines != null && !lyricLines.isEmpty()) {
			Element lyricsEle = (Element) songEle.appendChild(doc.createElement("lyrics"));
			SequenceDataCache data = sequenceInfo.getDataCache();

			for (LyricLine line : lyricLines) {
				Element lineEle = (Element) lyricsEle.appendChild(doc.createElement("line"));
				float bar = data.tickToBarNumberFloat(line.tick());
				float barEnd = data.tickToBarNumberFloat(line.endTick());
				lineEle.setAttribute("bar", String.valueOf(bar));
				lineEle.setAttribute("barEnd", String.valueOf(barEnd));
				lineEle.setTextContent(XmlUtil.sanitizeStringForXMLSaving(line.text()));
			}
		}

		return doc;
	}

	@SuppressWarnings("HardCodedStringLiteral")
	private void appendExportSettings(Document doc, Element songEle) {
		Element exportSettingsEle = doc.createElement("exportSettings");
		if (tempoFactor != 1.0f)
			exportSettingsEle.setAttribute("tempoFactor", String.valueOf(tempoFactor));
		if (transpose != 0)
			exportSettingsEle.setAttribute("transpose", String.valueOf(transpose));
		if (ICompileConstants.SHOW_KEY_FIELD) {
			if (!keySignature.equals(sequenceInfo.getKeySignature()))
				exportSettingsEle.setAttribute("keySignature", String.valueOf(keySignature));
		}
		if (!timeSignature.equals(sequenceInfo.getTimeSignature()))
			exportSettingsEle.setAttribute("timeSignature", String.valueOf(timeSignature));
		if (tripletTiming)
			exportSettingsEle.setAttribute("tripletTiming", String.valueOf(tripletTiming));
		exportSettingsEle.setAttribute("mixTiming", String.valueOf(mixTiming));
		exportSettingsEle.setAttribute("organic", String.valueOf(organic));
		exportSettingsEle.setAttribute("organic-multi-stage", String.valueOf(organic2));
        if (organic && organic2 && upgraded) {
            exportSettingsEle.setAttribute("organic-version", String.valueOf(2));
        }
		if (mixTiming) {
			exportSettingsEle.setAttribute("combinePriorities", String.valueOf(priorityActive));
			// exportSettingsEle.setAttribute("mixVersion", String.valueOf(mixVersion));
		}
		exportSettingsEle.setAttribute("calcDynamics", dynamicsMethod.name());

		if (exportSettingsEle.getAttributes().getLength() > 0 || exportSettingsEle.getChildNodes().getLength() > 0)
			songEle.appendChild(exportSettingsEle);
	}

	@SuppressWarnings("HardCodedStringLiteral")
	private void appendImportSettings(Document doc, Element songEle) {
		Element importSettingsEle = doc.createElement("importSettings");
		importSettingsEle.setAttribute("useOldVelocities", String.valueOf(usingOldVelocities));
		importSettingsEle.setAttribute("useOldTempos", String.valueOf(usingOldTempos));
		importSettingsEle.setAttribute("useNewMidiLayout", String.valueOf(usingNewMidiLayout));
		if (ignoreZeroChannelVolume) importSettingsEle.setAttribute("ignoreZeroChannelVolume", String.valueOf(ignoreZeroChannelVolume)); 
		if (importSettingsEle.getAttributes().getLength() > 0 || importSettingsEle.getChildNodes().getLength() > 0)
			songEle.appendChild(importSettingsEle);
	}

	@SuppressWarnings("HardCodedStringLiteral")
	private void appendTuneSections(Document doc, Element songEle) {
		for (TuneLine tuneLine : tuneBars.values()) {
			Element tuneEle = (Element) songEle.appendChild(doc.createElement("tuneSection"));
			SaveUtil.appendChildTextElement(tuneEle, "startBar", String.valueOf(tuneLine.startBar));
			SaveUtil.appendChildTextElement(tuneEle, "endBar", String.valueOf(tuneLine.endBar));
			SaveUtil.appendChildTextElement(tuneEle, "seminoteStep", String.valueOf(tuneLine.seminoteStep));
			SaveUtil.appendChildTextElement(tuneEle, "tempoChange", String.valueOf(tuneLine.tempo));
			SaveUtil.appendChildTextElement(tuneEle, "tempoAccelerando", String.valueOf(tuneLine.accelerando));
			SaveUtil.appendChildTextElement(tuneEle, "fade", String.valueOf(tuneLine.fade));
			SaveUtil.appendChildTextElement(tuneEle, "dialogLine", String.valueOf(tuneLine.dialogLine));
		}
	}

	public void exportAbc(File exportFile, String appName) throws IOException, AbcConversionException {
		boolean delayEnabled = false;
		int minDelay = 0;
		for (AbcPart part : parts) {
			if (part.getDelay() != 0) {
				delayEnabled = true;
				if (part.getDelay() < minDelay) {
					minDelay = part.getDelay();
				}
			}
		}
		try (FileOutputStream out = new FileOutputStream(exportFile)) {
			getAbcExporter().exportToAbc(out, delayEnabled, appName, minDelay);
			if (firstExportTime == null) firstExportTime = new Date();
		}
        setFileMetadata(exportFile.toPath(), appName);
	}

    /**
     * Write metainfo about the file to NTFS/Linux filesystems.
     * This info cannot be seen by looking at file properties.
     *
     * It can be revealed by CMD:
     * more < "test.abc":Artist
     *
     * Or Powershell:
     * Get-Content -Path .\test.abc -Stream Artist
     *
     * But more importantly, it can be read by Java, so in the future
     * don't have to read all files content in Abc Playlist
     * to get this info.
     *
     * Since NTFS native encoding is UTF_16LE, we use that.
     */
	@SuppressWarnings("HardCodedStringLiteral")
    private void setFileMetadata(Path file, String appName) {
        try {
            UserDefinedFileAttributeView view = Files.getFileAttributeView(
                    file, UserDefinedFileAttributeView.class);

            if (view != null) {
                view.write("Artist", StandardCharsets.UTF_16LE.encode(getComposer()));
                view.write("Title", StandardCharsets.UTF_16LE.encode(getTitle()));
                view.write("Transcriber", StandardCharsets.UTF_16LE.encode(getTranscriber()));
                view.write("Genre", StandardCharsets.UTF_16LE.encode(getGenre()));
                view.write("Mood", StandardCharsets.UTF_16LE.encode(getMood()));
                view.write("Number of parts", StandardCharsets.UTF_16LE.encode(Integer.toString(getActivePartCount())));
                view.write("Tempo", StandardCharsets.UTF_16LE.encode(getTempoBPM() + " BPM"));
                view.write("Duration", StandardCharsets.UTF_16LE.encode(Util.formatDurationM(getSongLengthMicros())));
                view.write("Export Tool", StandardCharsets.UTF_16LE.encode(appName+" v"+MaestroMain.APP_VERSION));
                view.write("Export Date", StandardCharsets.UTF_16LE.encode(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())));
                Pair<Integer, Integer> pair = getBadgerMaximum();
                if (pair != null) {
                    view.write("Minimum parts", StandardCharsets.UTF_16LE.encode(Integer.toString(pair.first)));
                    view.write("Maximum parts", StandardCharsets.UTF_16LE.encode(Integer.toString(pair.second)));
                }
                log.info("Successfully wrote metadata!");
            } else {
                log.warning("User defined attributes not supported by this file system.");
            }
        } catch (IOException e) {
            log.warning("Failed to write metadata: " + e.getMessage());
        }
    }

	public AbcPart createNewPart() {
		AbcPart newPart = new AbcPart(this);
		newPart.addAbcListener(abcPartListener);
		partAutoNumberer.onPartAdded(newPart, parts);
		populateFirstNumbers();
		newPart.firstNumber = partAutoNumberer.getFirstNumber(newPart.getInstrument());
		int idx = Collections.binarySearch(parts, newPart, partAutoNumberer.getComparator());
		if (idx < 0)
			idx = (-idx - 1);
		parts.add(idx, newPart);

		setMixDirty(true);
		fireChangeEvent(AbcSongProperty.PART_ADDED, newPart);
		return newPart;
	}

	public void deletePart(AbcPart part) {
		if (part == null || !parts.contains(part))
			return;
		
		setMixDirty(true);
		fireChangeEvent(AbcSongProperty.BEFORE_PART_REMOVED, part);
		parts.remove(part);
        boolean removedCountIn = false;
        if (countIn != null && countIn.part == part) {
            countIn = null;
            removedCountIn = true;
        }
		suppressPartSort = true;
		partAutoNumberer.onPartDeleted(part, parts);
		suppressPartSort = false;
		part.discard();
		//since we suppressed sorting we do it now:
		populateFirstNumbers();
		if (sorted) parts.sort(partAutoNumberer.getComparator());
		fireChangeEvent(AbcSongProperty.PART_LIST_ORDER, part);
        if (removedCountIn) fireChangeEvent(AbcSongProperty.COUNT_IN);
        fireChangeEvent(AbcSongProperty.AFTER_PART_REMOVED, part);
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		title = Util.emptyIfNull(title);
		if (!this.title.equals(title)) {
			this.title = title;
			fireChangeEvent(AbcSongProperty.TITLE);
		}
	}

	@Override
	public String getGenre() {
		return genre;
	}

	public void setGenre(String genre) {
		genre = Util.emptyIfNull(genre);
		if (!this.genre.equals(genre)) {
			this.genre = genre;
			fireChangeEvent(AbcSongProperty.GENRE);
		}
	}

	@Override
	public String getMood() {
		return mood;
	}

	public void setMood(String mood) {
		mood = Util.emptyIfNull(mood);
		if (!this.mood.equals(mood)) {
			this.mood = mood;
			fireChangeEvent(AbcSongProperty.MOOD);
		}
	}

	@Override
	public String getComposer() {
		return composer;
	}

	public void setComposer(String composer) {
		composer = Util.emptyIfNull(composer);
		if (!this.composer.equals(composer)) {
			this.composer = composer;
			fireChangeEvent(AbcSongProperty.COMPOSER);
		}
	}

	@Override
	public String getPartSetup() {
		if (!badger) {
			return null;
		}
		StringBuilder str = new StringBuilder();
		for (int i = AbcPart.badgerPrioHighest; i <= AbcPart.badgerPrioLowest; i++) {
			StringBuilder str2 = new StringBuilder();
			ListModelWrapper<AbcPart> prts = getParts();
			int count = 0;
			int onCount = 0;
			for (AbcPart prt : prts) {
				if (prt.getEnabledTrackCount() > 0 && prt.getBadgerPrio() <= i) {
					count += 1;
					if (prt.getBadgerPrio() == i) onCount++;
					str2.append(String.format(" %2d", prt.getPartNumber()));
				}
			}
			if (onCount == 0) {
				continue;
			}
			str.append(String.format("N: TS %2d, %s\n", count, str2));
		}
		return str.toString();
	}

    private Pair<Integer, Integer> getBadgerMaximum() {
        Integer min = null;
        Integer max = null;
        if (badger) {
            for (int i = AbcPart.badgerPrioHighest; i <= AbcPart.badgerPrioLowest; i++) {
                ListModelWrapper<AbcPart> prts = getParts();
                int count = 0;
                int onCount = 0;
                for (AbcPart prt : prts) {
                    if (prt.getEnabledTrackCount() > 0 && prt.getBadgerPrio() <= i) {
                        count += 1;
                        if (prt.getBadgerPrio() == i) onCount++;
                    }
                }
                if (onCount == 0) {
                    continue;
                }
                if (min == null || count < min) {
                    min = count;
                }
                if (max == null || count > max) {
                    max = count;
                }
            }
        }
        if (min == null || max == null) {
            return null;
        }
        return new Pair<>(min, max);
    }

	@Override
	public int getActivePartCount() {
		// TODO: Cache this to not have to recalculate
		ListModelWrapper<AbcPart> prts = getParts();
		int counter = 0;
		for (AbcPart part : prts) {
			if (part.getEnabledTrackCount() > 0) {
				counter++;
			}
		}
		return counter;
	}
	
	public boolean isAnyPartsSoloed() {
		for (AbcPart part : getParts()) {
			if (part.isSoloed()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public String getTranscriber() {
		return transcriber;
	}

	public void setTranscriber(String transcriber) {
		transcriber = Util.emptyIfNull(transcriber);
		if (!this.transcriber.equals(transcriber)) {
			this.transcriber = transcriber;
			fireChangeEvent(AbcSongProperty.TRANSCRIBER);
		}
	}

	public float getTempoFactor() {
		return tempoFactor;
	}

    /**
     * ProjectFrame spinner sets new tempo here.
     * When loading a file, tempo also get set here.
     * The tempos are in BPM units.
     */
	public void setTempoFactor(int newTempo, int origTempo) {
		float tempoFactor = (float) newTempo/origTempo;
		if (this.newTempo != newTempo || this.origTempo != origTempo) {
			this.tempoFactor = tempoFactor;
			this.newTempo = newTempo;
			this.origTempo = origTempo;
			fireChangeEvent(AbcSongProperty.TEMPO_FACTOR);
		}
	}

	/**
	 * Get the main export tempo.
	 * 
	 * @return bpm
	 */
	public int getTempoBPM() {
		return newTempo;
	}

	/**
	 * Set the the main tempo for export and preview.
	 * Will call setTempoFactor()
	 * 
	 * @param tempoBPM new tempo
	 */
	public void setTempoBPM(int tempoBPM) {
		setTempoFactor(tempoBPM, sequenceInfo.getPrimaryTempoBPM());
	}

	public int getTranspose() {
		return transpose;
	}

	public void setTranspose(int transpose) {
		if (this.transpose != transpose) {
			this.transpose = transpose;
			fireChangeEvent(AbcSongProperty.TRANSPOSE);
		}
	}

	public KeySignature getKeySignature() {
		if (ICompileConstants.SHOW_KEY_FIELD)
			return keySignature;
		else
			return KeySignature.C_MAJOR;
	}

	public void setKeySignature(KeySignature keySignature) {
		if (!ICompileConstants.SHOW_KEY_FIELD)
			keySignature = KeySignature.C_MAJOR;

		if (!this.keySignature.equals(keySignature)) {
			this.keySignature = keySignature;
			fireChangeEvent(AbcSongProperty.KEY_SIGNATURE);
		}
	}

	public TimeSignature getTimeSignature() {
		return timeSignature;
	}

	public void setTimeSignature(TimeSignature timeSignature) {
		if (!this.timeSignature.equals(timeSignature)) {
			this.timeSignature = timeSignature;
			fireChangeEvent(AbcSongProperty.TIME_SIGNATURE);
		}
	}

	public boolean isTripletTiming() {
		return tripletTiming;
	}

	public boolean isMixTiming() {
		return mixTiming;
	}

	public int getMixVersion() {
		return mixVersion;
	}

	public void setMixTiming(boolean mixTiming) {
		if (this.mixTiming != mixTiming) {
			this.mixTiming = mixTiming;
			fireChangeEvent(AbcSongProperty.MIX_TIMING);
            fireChangeEvent(AbcSongProperty.TIMINGS_MULTI);
		}
	}

	public void setMixVersion(int mixVersion) {
		if (this.mixVersion != mixVersion) {
			this.mixVersion = mixVersion;
			fireChangeEvent(AbcSongProperty.MIX_TIMING);// We can use same event as for mixtiming
            fireChangeEvent(AbcSongProperty.TIMINGS_MULTI);
		}
	}

	public void setTripletTiming(boolean tripletTiming) {
		if (this.tripletTiming != tripletTiming) {
			this.tripletTiming = tripletTiming;
			fireChangeEvent(AbcSongProperty.TRIPLET_TIMING);
            fireChangeEvent(AbcSongProperty.TIMINGS_MULTI);
		}
	}

    /**
     * Set multiple timing settings at once
     * This will decrease number of preview generations done.
     * And also less property change events in some cases.
     */
    public void setTimings(boolean org, boolean org2, boolean mix, boolean swing, boolean prio, boolean upgr) {
        boolean changed = false;
        if (this.tripletTiming != swing) {
            this.tripletTiming = swing;
            fireChangeEvent(AbcSongProperty.TRIPLET_TIMING);
            changed = true;
        }
        if (this.mixTiming != mix) {
            this.mixTiming = mix;
            fireChangeEvent(AbcSongProperty.MIX_TIMING);
            changed = true;
        }
        if (this.priorityActive != prio) {
            setMixDirty(true);
            this.priorityActive = prio;
            fireChangeEvent(AbcSongProperty.MIX_TIMING_COMBINE_PRIORITIES);
            changed = true;
        }
        boolean orgChanged = false;
        if (organic != org) {
            organic = org;
            orgChanged = true;
            changed = true;
        }
        if (organic2 != org2) {
            organic2 = org2;
            orgChanged = true;
            changed = true;
        }
        if (upgraded != upgr) {
            upgraded = upgr;
            orgChanged = true;
            changed = true;
        }
        if (orgChanged) fireChangeEvent(AbcSongProperty.ORGANIC);
        if (changed) fireChangeEvent(AbcSongProperty.TIMINGS_MULTI);
    }

	public boolean isSkipSilenceAtStart() {
		return skipSilenceAtStart;
	}

	public void setSkipSilenceAtStart(boolean skipSilenceAtStart) {
		if (this.skipSilenceAtStart != skipSilenceAtStart) {
			this.skipSilenceAtStart = skipSilenceAtStart;
			fireChangeEvent(AbcSongProperty.SKIP_SILENCE_AT_START);
		}
	}

    public boolean isUseRestsInChords() {
        return useRestsInChords;
    }

    public void setUseRestsInChords(boolean use) {
        this.useRestsInChords = use;
    }

    public boolean isReducedFilesize() {
        return reducedFilesize;
    }

    public void setReducedFilesize(boolean reduced) {
        // do not need change event as it's a setting, not a song property
        this.reducedFilesize = reduced;
    }
	
	public boolean isDeleteMinimalNotes() {
		return deleteMinimalNotes;
	}

	public void setDeleteMinimalNotes(boolean deleteMinimalNotes) {
		if (this.deleteMinimalNotes != deleteMinimalNotes) {
			this.deleteMinimalNotes = deleteMinimalNotes;
			fireChangeEvent(AbcSongProperty.DELETE_MINIMAL_NOTES);
		}
	}

	/*
	 * public void setShowPruned(boolean showPruned) { if (this.showPruned != showPruned) { this.showPruned =
	 * showPruned; //fireChangeEvent(AbcSongProperty.SHOW_PRUNED); } }
	 * 
	 * public boolean isShowPruned() { return showPruned; }
	 */

	public void setBadger(boolean badger) {
		if (this.badger != badger) {
			this.badger = badger;
			fireChangeEvent(AbcSongProperty.BADGER);
		}
	}

	public SequenceInfo getSequenceInfo() {
		return sequenceInfo;
	}

	public boolean isFromAbcFile() {
		return fromAbcFile;
	}

	public boolean isFromXmlFile() {
		return fromXmlFile;
	}

	@Override
	public String getPartName(AbcPartMetadataSource abcPart) {
		return partNameTemplate.formatName(this, abcPart);
	}

	public File getSourceFile() {
		return sourceFile;
	}
	
	public void setSourceFile(File sourceFile) {
		this.sourceFile = sourceFile;
	}

	@Override
	public String getSourceFilename() {
		String ret = errorString;
		if (sourceFile != null) {
			ret = sourceFile.getName();
		}
		return ret;
	}

	public File getProjectFile() {
		return projectFile;
	}

	public void setProjectFile(File projectFile) {
		this.projectFile = projectFile;
	}

	@Override
	public File getExportFile() {
		return exportFile;
	}

	public void setExportFile(File exportFile) {
		if (this.exportFile == null && exportFile == null)
			return;
		if (this.exportFile != null && this.exportFile.equals(exportFile))
			return;
		if (storeNewExportFile) {
			this.exportFile = exportFile;
			fireChangeEvent(AbcSongProperty.EXPORT_FILE);
		}
	}

	@Override
	public String getSongTitle() {
		return getTitle();
	}

	@Override
	public long getSongLengthMicros() {
		if (parts.isEmpty() || sequenceInfo == null)
			return 0L;

		try {
			AbcExporter exporter = getAbcExporter();

			return timingInfo.divideByExportTempoFactor(exporter.getExportEndMicros() - exporter.getExportStartMicros());
		} catch (AbcConversionException e) {
			return 0L;
		}
	}

	/**
	 * Careful! This method WILL recalculate start and end tick in abcExporter!
	 */
	public long getSongStartMicrosABC() {
		if (parts.isEmpty() || sequenceInfo == null)
			return 0L;

		try {
			AbcExporter exporter = getAbcExporter();
			exporter.calcSongStartEndTicks();
			return timingInfo.divideByExportTempoFactor(exporter.getExportStartMicros());
		} catch (AbcConversionException e) {
			return 0L;
		}
	}

	public ListModelWrapper<AbcPart> getParts() {
		return parts;
	}

	public void addSongListener(Listener<AbcSongEvent> l) {
		listeners.add(l);
	}

	public void removeSongListener(Listener<AbcSongEvent> l) {
		listeners.remove(l);
	}

	private void fireChangeEvent(AbcSongProperty property) {
		fireChangeEvent(property, null);
	}

	private void fireChangeEvent(AbcSongProperty property, AbcPart part) {
		if (listeners.size() > 0)
			listeners.fire(new AbcSongEvent(this, property, part));
	}

	public LotroCombiDrumInfo getCombiInfo() {
		return combiInfo;
	}

	public QuantizedTimingInfo getAbcTimingInfo() throws AbcConversionException {
		if (timingInfo == null //
				|| timingInfo.getExportTempoFactord() != getTempoFactor() //
				|| timingInfo.getMeter() != getTimeSignature() //
				|| timingInfo.isTripletTiming() != isTripletTiming() //
				|| timingInfo.isMixTiming() != isMixTiming() //
				|| timingInfo.getMixVersion() != getMixVersion() //
				|| timingInfo.isOrganic() != isOrganic() //
				|| isMixDirty()) {
			setMixDirty(false);
			timingInfo = new QuantizedTimingInfo(sequenceInfo, newTempo, origTempo, getTimeSignature(), isTripletTiming(), this, isMixTiming(), getMixVersion(), isOrganic());
		}

		return timingInfo;
	}

    /**
     * Get abc exporter and make sure its properties are up to date.
     */
	public AbcExporter getAbcExporter() throws AbcConversionException {
		QuantizedTimingInfo qtm = getAbcTimingInfo();
		KeySignature key = getKeySignature();

		if (abcExporter == null) {
			abcExporter = new AbcExporter(parts, qtm, key, this, skipSilenceAtStart, organic);
		}

        // from song:

		if (abcExporter.getTimingInfo() != qtm) {
			abcExporter.setTimingInfo(qtm);
		}

		if (abcExporter.getKeySignature() != key)
			abcExporter.setKeySignature(key);

		if (abcExporter.isOrganic() != organic)
			abcExporter.setOrganic(organic);

		if (abcExporter.isOrganic2() != organic2)
			abcExporter.setOrganic2(organic2);

        if (abcExporter.isUpgraded() != upgraded)
            abcExporter.setUpgraded(upgraded);

        // from settings:

        if (abcExporter.isSkipSilenceAtStart() != skipSilenceAtStart)
            abcExporter.setSkipSilenceAtStart(skipSilenceAtStart);

        if (abcExporter.isDeleteMinimalNotes() != deleteMinimalNotes)
            abcExporter.setDeleteMinimalNotes(deleteMinimalNotes);

		if (abcExporter.isUseRestsInChords() != useRestsInChords)
			abcExporter.setUseRestsInChords(useRestsInChords);

        abcExporter.dissonancePrefs = miscSettings;
        abcExporter.reducedFilesize = reducedFilesize;

        /*
        Current:
          it gets parts directly in constructor, since that pointer never changes
          it gets mixTimings, swing, meter, main-tempo from QTM
          it get bar info from part->seqinfo->datacache
          transpose stuff is hidden from it, only used inside part methods
          same for section-edits and tune-edits
          part-edits it gets from parts
          it gets dynamics method from part->song
          it does not have knowledge of mix timings priorities, only QTM and abc-parts has that.

        Goal:
          No changes in parts, song or qtm should affect it once its started.
          Done: QTM is a final copy, datacache same
          TODO: Song, Part should be final copies too.
                Memory should be okay.
                CPU time to copy them worries me a bit.
                As for auto-export ABC, that should be able to be multi-threaded so
                that 5 or so songs can be exported at once. That's a later project.
         */

		return abcExporter;
	}

	private void populateFirstNumbers() {
		for (AbcPart part : parts) {
			part.firstNumber = partAutoNumberer.getFirstNumber(part.getInstrument());
		}
	}

    public boolean suppressPartSort = false;// beside this class, also used from PartAutoNumberer

	private final Listener<AbcPartEvent> abcPartListener = e -> {
        //log.warning(this.getClass().getTypeName()+" AbcPartEvent: "+e.getProperty());
		if (e.getProperty() == AbcPartProperty.PART_NUMBER && !suppressPartSort && sorted) {
            sortParts(e.getSource());
        }
	};

    public void sortParts(AbcPart source) {
        populateFirstNumbers();
        parts.sort(partAutoNumberer.getComparator());
        fireChangeEvent(AbcSongProperty.PART_LIST_ORDER, source);
    }

    /**
     * disable auto sorting of parts
     */
    public void rearrangedParts() {
        sorted = false;
        fireChangeEvent(AbcSongProperty.PART_LIST_ORDER);
    }

    /**
     * enable auto sorting of parts
     */
    public void autoSortParts() {
        sorted = true;
        populateFirstNumbers();
        parts.sort(partAutoNumberer.getComparator());
        fireChangeEvent(AbcSongProperty.PART_LIST_ORDER);
    }

    @Override
	public String getBadgerTitle() {
		if (!badger)
			return null;
		return "N: Title: " + StringCleaner.cleanForABC(getComposer()) + " - "
				+ StringCleaner.cleanForABC(getSongTitle());
	}

	public void assignNumbersToSimilarPartTypes() {
		for (LotroInstrument instr : LotroInstrument.values()) {
			List<AbcPart> instrParts = new ArrayList<>();
			for (AbcPart part : parts) {
				if (part.getInstrument().equals(instr)) {
					instrParts.add(part);
				}
			}
			if (instrParts.size() > 1) {
                // This is not super tight, as one or more of them might not have assigned any track. TODO.
				AbcHelper.setTypeNumbers(instrParts);
			} else if (instrParts.size() == 1) {
				instrParts.getFirst().setTypeNumber(0);
			}
		}
	}

	public boolean isPriorityActive() {
		return priorityActive;
	}

	public void setPriorityActive(boolean priorityActive) {
		if (this.priorityActive != priorityActive) {
			setMixDirty(true);
			this.priorityActive = priorityActive;
			fireChangeEvent(AbcSongProperty.MIX_TIMING_COMBINE_PRIORITIES);
            fireChangeEvent(AbcSongProperty.TIMINGS_MULTI);
		}
	}
	
	public void setOrganic(boolean org) {
		if (organic != org) {
			organic = org;
			fireChangeEvent(AbcSongProperty.ORGANIC);
            fireChangeEvent(AbcSongProperty.TIMINGS_MULTI);
		}
	}
	
	/**
	 * Set if multistage should be used when organic is enabled
	 * 
	 * @param multistage boolean for multistage
	 */
	public void setOrganic2(boolean multistage) {
		if (organic2 != multistage) {
			organic2 = multistage;
			fireChangeEvent(AbcSongProperty.ORGANIC);
            fireChangeEvent(AbcSongProperty.TIMINGS_MULTI);
		}
	}
	
	public boolean isOrganic() {
		return organic;		
	}
	
	/**
	 * 
	 * @return true if multistage enabled
	 */
	public boolean isOrganic2() {
		return organic2;		
	}

    public boolean isUpgraded() {
        return upgraded;
    }

    public void setUpgraded(boolean upgr) {
        if (upgraded != upgr) {
            upgraded = upgr;
            fireChangeEvent(AbcSongProperty.ORGANIC);
            fireChangeEvent(AbcSongProperty.TIMINGS_MULTI);
        }
    }

    public String getStats() {
        String str = "";
        if (firstExportTime != null && firstExportTime.getTime() != 0L) {
            // note that auto-exporting without saving the project won't set this
            str += "Project first exported by Maestro:\n" + firstExportTime + "\n";
        } else if (firstExportTime != null) {
            str += "Project first exported by Maestro:\nA long time ago (date not available)\n";
        }
        if(tuneBars != null || getFirstBar() != null || getLastBar() != null) {
            str += "\nTune-editor changes in effect.\n";
        }
        if(isPartEdited()) {
            str += "\nPart-editor changes in effect.\n";
        }
        if(isSectionsEdited()) {
            str += "\nSection-editor changes in effect.\n";
        }
        str += "\n";
        return str;
    }

	public void tuneEdited() {
		convertTunelinesToLongs();
		setMixDirty(true); // Tempo might have changed, in which case the mixTimings need to be recomputed
		fireChangeEvent(AbcSongProperty.TUNE_EDIT);
	}

	public int getTuneTranspose(long tickStart) {
		if (tuneBars == null || tuneBars.isEmpty()) return 0;
		
		for (TuneLine value : tuneBars.values()) {
			if (tickStart < value.endTick && tickStart >= value.startTick) {
				return value.seminoteStep;
			}
		}

		return 0;
	}
	
	public NavigableMap<Long, Integer> getTuneTempoChanges() {
		SortedMap<Float, TuneLine> tree = tuneBars;
		TreeMap<Long, Integer> treeChanges = new TreeMap<>();
		if (tree != null) {
			Collection<TuneLine> lines = tree.values();
			for (TuneLine line : lines) {
				if (line.accelerando != 0) {
					int steps = Math.abs(line.accelerando);
					int step = line.accelerando < 0?-1:1;
					long domain = line.endTick - line.startTick;
					long domainStep = domain/steps;
					while (domainStep == 0L && steps > 1) {
						// very short number of ticks compared to number of tempo steps
						steps /= 2;
						domainStep = domain/steps;
						step *= 2;
					}
					if (domainStep > 0L) {
						for (int i = 0; Math.abs(step)*(i+1) <= steps; i++) {
							long distance = i*domainStep;
							int newTempoOffset = step*(i+1)+line.tempo;
							treeChanges.put(line.startTick+distance, newTempoOffset);
							assert line.startTick+distance < line.endTick : "steps="+steps+" step="+step+"\n domainStep="+domainStep+" domain="+domain;
						}
						if(treeChanges.get(line.endTick) == null || treeChanges.get(line.endTick) == 0) {
							treeChanges.put(line.endTick, 0);
						}
					} else {
						System.err.println("Tune-editor accelerando so short that it was skipped.");
					}
					
					/* example:
					
					bar 30 to 40 at 4 accelerando and 10 offset
					
					steps=4
					step=1
					domain=10
					domain/steps=2.5
					
					forloop 1 to 3:
					30.0 at 1 +10
					32.5 at 2 +10
					35.0 at 3 +10
					37.5 at 4 +10
					
					final:
					40 at 0 unless another tempochange or accelerando start here, then that will take precedence
					 
					*/
				} else if (line.tempo != 0) {
					treeChanges.put(line.startTick, line.tempo);
					if(treeChanges.get(line.endTick) == null || treeChanges.get(line.endTick) == 0) {
						treeChanges.put(line.endTick, 0);
					}
				}
			}
		}
		return treeChanges;
	}
	
	public void setLastBar(Float lastBar) {
		this.lastBar = lastBar;
	}
	
	public Float getLastBar() {
		return lastBar;
	}
	
	public void setFirstBar(Float firstBar) {
		this.firstBar = firstBar;
	}
	
	public Float getFirstBar() {
		return firstBar;
	}
	
	public long getLastBarTick() {
		return lastBarTick;
	}
	
	public long getFirstBarTick() {
		return firstBarTick;
	}

	public InstrNameSettings getInstrNameSettings() {
		return instrNameSettings;
	}

	public boolean isMixDirty() {
		return mixDirty;
	}

	public void setMixDirty(boolean mixDirty) {
		this.mixDirty = mixDirty;
	}

	public boolean isUsingOldVelocities() {
		return usingOldVelocities;
	}
	
	public boolean isUsingOldTempos() {
		return usingOldTempos;
	}

    public void setUsingOldTempos(boolean onlyFirstTrackTempos) {
        usingOldTempos = onlyFirstTrackTempos;
    }
	
	public QuantizedTimingInfo getQTM() {
		try {
			if (getAbcTimingInfo() == null) return null;
		} catch (AbcConversionException e) {
			timingInfo = null;// To make sure at some point the user will see the exception.
			return null;
		}
		return timingInfo;
	}

	public void setHideEdits(boolean selected) {
		this.hideEdits = selected;
		fireChangeEvent(AbcSongProperty.HIDE_EDITS_UPDATE);
	}
	
	public boolean isHideEdits() {
		return hideEdits; 
	}

	public int getAbcTempoMPQ(long thumbTick) {
		int mpq;
		try {
			if (getAbcTimingInfo() == null) return 0;
			mpq = getAbcTimingInfo().getAbcTempoMPQForTick(thumbTick);
		} catch (AbcConversionException e) {
			timingInfo = null;// To make sure at some point the user will see the exception.
			return 0;
		}
		return mpq;
	}

	public Collection<TimingInfoEvent> getTimingInfoByTick() {
		try {
			if (getAbcTimingInfo() == null) return null;
		} catch (AbcConversionException e) {
			timingInfo = null;// To make sure at some point the user will see the exception.
			return null;
		}
		if (organic) {
			return timingInfo.getTimingInfoByTickOrganic().values();
		} else {
			return timingInfo.getTimingInfoByTick().values();
		}
	}

	/**
	 * Only used by abc tools
	 */
	public int getMaxPartPoly() {
		int poly = AbcConstants.MAX_CHORD_NOTES;
		for (AbcPart part : parts) {
			if (part.getMaxPoly() > poly) {
				poly = part.getMaxPoly();
			}
		}
		return poly;
	}

    /**
     * If part-editor has been modified
     */
    public boolean isPartEdited() {
        boolean edited = false;
        if (countIn != null && countIn.pattern != CountIn.CountInPattern.OFF) {
            return true;
        }
        for(AbcPart part : parts) {
            if (part.getEnabledTrackCount() == 0) continue;
            if (part.getDelay() != 0) return true;
            if (part.getNoteMax() != AbcConstants.MAX_CHORD_NOTES) return true;
            if (badger && part.getBadgerPrio() != AbcPart.badgerPrioHighest) return true;
            if (part.conclusionFermata != 0) return true;
        }
        return edited;
    }

    /**
     * If any section-editors has been modified
     */
    public boolean isSectionsEdited() {
        boolean edited = false;
        for(AbcPart part : parts) {
            if (part.isSectionsEdited()) return true;
        }
        return edited;
    }

    @NotNull
    public List<String> getExportWarnings(PolyphonyHistogram histogram) {
        List<String> warns = new ArrayList<>();
        if (histogram != null && histogram.maxAll() > 64) {
            // There is growing concerns that 64+ polyphony can make audience lag (stutter),
            // so this warning is not optional.
            warns.add(UIText.get("maestro.more.notes.0.64.playing.at.same.time.than.lotro.can.handle", histogram.maxAll()));
        }
        if (saveAndExportSettings.warnOnExportOfSamePartNames && isPartsTitlesSimilar()) {
            warns.add(UIText.get("maestro.two.or.more.parts.has.same.name"));
        }
        return warns;
    }

    private boolean isPartsTitlesSimilar() {
        if (parts.size() <= 1) {
            return false;
        }

        Set<String> titles = new HashSet<>();
        for (AbcPart part : parts) {
            if (part.getEnabledTrackCount() == 0) continue;

            if (!titles.add(part.getTitle())) {
                return true;
            }
        }
        return false;
    }

    public CountIn getCountIn() {
        return countIn;
    }

    public void setCountIn(CountIn countin) {
        boolean changed = true;
        if (this.countIn != null) {
            changed = !this.countIn.equals(countin);
        } else if (countin == null) {
            // both null
            changed = false;
        }
        this.countIn = countin;
        if (changed) fireChangeEvent(AbcSongProperty.COUNT_IN);
    }

    public AbcSong origSong = this;

    /**
     * Copy constructor for creating a thread-safe snapshot for worker threads.
     */
    public AbcSong(AbcSong other) {
        // Immutable/Shared Fields
        this.title = other.title;
        this.composer = other.composer;
        this.transcriber = other.transcriber;
        this.genre = other.genre;
        this.mood = other.mood;
        this.note = other.note;
        this.lyrics = other.lyrics;
		this.lyricLines = other.lyricLines;
        this.badger = other.badger;
        this.tempoFactor = other.tempoFactor;
        this.newTempo = other.newTempo;
        this.origTempo = other.origTempo;
        this.transpose = other.transpose;
        this.keySignature = other.keySignature;
        this.timeSignature = other.timeSignature;
        this.tripletTiming = other.tripletTiming;
        this.mixTiming = other.mixTiming;
        this.organic = other.organic;
        this.organic2 = other.organic2;
        this.mixVersion = other.mixVersion;
        this.priorityActive = other.priorityActive;
        this.skipSilenceAtStart = other.skipSilenceAtStart;
        this.deleteMinimalNotes = other.deleteMinimalNotes;
        this.useRestsInChords = other.useRestsInChords;
        this.reducedFilesize = other.reducedFilesize;
        this.firstBar = other.firstBar;
        this.lastBar = other.lastBar;
        this.firstBarTick = other.firstBarTick;
        this.lastBarTick = other.lastBarTick;
        this.fromAbcFile = other.fromAbcFile;
        this.fromXmlFile = other.fromXmlFile;
        this.usingOldVelocities = other.usingOldVelocities;
        this.usingOldTempos = other.usingOldTempos;
		this.usingNewMidiLayout = other.usingNewMidiLayout;
        this.temposWereFixed = other.temposWereFixed;
        this.hideEdits = other.hideEdits;
        this.dynamicsMethod = other.dynamicsMethod;
        this.ignoreZeroChannelVolume = other.ignoreZeroChannelVolume;
        this.sorted = other.sorted;
        this.ignoreMidiText = other.ignoreMidiText;
        this.copyright = other.copyright;
        this.sourceFile = other.sourceFile;
        this.firstExportTime = other.firstExportTime==null?null:(new Date(other.firstExportTime.getTime()));
        this.storeNewSourceFile = other.storeNewSourceFile;
        this.storeNewExportFile = other.storeNewExportFile;
        this.suppressPartSort = other.suppressPartSort;
        this.newSourceFile = other.newSourceFile;
        this.allPans = other.allPans;//pointer copy
        this.upgraded = other.upgraded;

        // read-only/shared services.
        this.sequenceInfo = other.sequenceInfo;// lets assume the midi don't change while we work, then this is immutable
        this.timingInfo = other.timingInfo;// would be time-consuming to deep copy, plus it's kinda immutable

        // settings classes
        this.partAutoNumberer = new PartAutoNumberer(other.partAutoNumberer);
        this.partNameTemplate = new PartNameTemplate(other.partNameTemplate);
        this.exportFilenameTemplate = new ExportFilenameTemplate(other.exportFilenameTemplate);
        this.instrNameSettings = new InstrNameSettings(other.instrNameSettings);
        this.saveAndExportSettings = new SaveAndExportSettings(other.saveAndExportSettings);
        this.miscSettings = new MiscSettings(other.miscSettings);

        // objects that needs to be generated by worker or here
        this.listeners = new ListenerList<>();
        this.abcExporter = null; // Will be regenerated by the worker
        this.mixDirty = true; // Force regeneration

        // Deep Copies
		this.combiInfo = new LotroCombiDrumInfo(other.combiInfo);

        if (other.tuneBars != null) {
            this.tuneBars = new TreeMap<>();
            for (Entry<Float, TuneLine> entry : other.tuneBars.entrySet()) {
                this.tuneBars.put(entry.getKey(), new TuneLine(entry.getValue()));
            }
        }
        if (other.tuneBarsModified != null) {
            this.tuneBarsModified = java.util.Arrays.copyOf(other.tuneBarsModified, other.tuneBarsModified.length);
        }

        this.parts = new ListModelWrapper<>(new DefaultListModel<>());
        AbcPart countInPart = null;
        for (AbcPart origPart : other.parts) {
            AbcPart newPart = new AbcPart(origPart, this);
            this.parts.add(newPart);
            if (other.countIn != null && other.countIn.part == origPart) {
                countInPart = newPart;
            }
        }

        if (other.countIn != null) {
            this.countIn = new CountIn(other.countIn.pattern, other.countIn.barCount, countInPart, other.countIn.hit);
        }

        // Fields not needed by worker
        this.projectFile = null;
        this.exportFile = null;

        this.origSong = other;
    }

    public AbcPart getPartFromID(long ID) {
        for(AbcPart part : parts) {
            if (part.uniqueID == ID) {
                return part;
            }
        }
        return null;
    }

    public List<PanVisualizerPanel.PartInfo> allPans = new ArrayList<>();
}
