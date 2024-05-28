package com.digero.common.abctomidi;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import com.digero.common.abc.AbcConstants;
import com.digero.common.abc.AbcField;
import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.IBarNumberCache;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.TimeSignature;
import com.digero.common.util.Util;

public class AbcInfo implements AbcConstants, IBarNumberCache {
	private static class PartInfo {
		private int number = 1;
		private LotroInstrument instrument = LotroInstrument.DEFAULT_INSTRUMENT;
		private String name = null;
		private String rawName = null;
		private boolean nameIsFromExtendedInfo = false;
		private int startLine = 0;
		private int endLine = 0;
	}

	private boolean empty = true;
	private String titlePrefix;
	private Map<Character, String> metadata = new HashMap<>();
	private NavigableMap<Long, Integer> bars = new TreeMap<>();
	private Map<Integer, AbcInfo.PartInfo> partInfoByIndex = new HashMap<>();
	private NavigableSet<AbcRegion> regions;
	private int primaryTempoBPM = 120;
	private boolean hasTriplets = false;
	private boolean hasTripletsSet = false;
	private boolean hasMixTimings = true;

	private String songTitle = null;
	private String songComposer = null;
	private String songTranscriber = null;
	private String genre = null;
	private String mood = null;
	private TimeSignature timeSignature = TimeSignature.FOUR_FOUR;
	private KeySignature keySignature = KeySignature.C_MAJOR;

	void reset() {
		empty = true;
		titlePrefix = null;
		metadata.clear();
		bars.clear();
		primaryTempoBPM = 120;
		songTitle = null;
		songComposer = null;
		songTranscriber = null;
		hasTriplets = false;
		hasTripletsSet = false;
		hasMixTimings = true;
		timeSignature = TimeSignature.FOUR_FOUR;
		keySignature = KeySignature.C_MAJOR;
	}

	public String getComposer() {
		return Util.emptyIfNull(getComposer_MaybeNull());
	}

	public String getComposer_MaybeNull() {
		return (songComposer != null) ? songComposer : getMetadata_MaybeNull('C');
	}

	public String getTitle() {
		return Util.emptyIfNull(getTitle_MaybeNull());
	}

	public String getTitle_MaybeNull() {
		return (songTitle != null) ? songTitle : getTitlePrefix();
	}

	public String getTranscriber() {
		return Util.emptyIfNull(getTranscriber_MaybeNull());
	}

	public String getTranscriber_MaybeNull() {
		if (songTranscriber != null)
			return songTranscriber;

		String z = getMetadata_MaybeNull('Z');
		if (z != null) {
			String lcase = z.toLowerCase();

			if (lcase.startsWith("transcribed by"))
				z = z.substring("transcribed by".length()).trim();
			else if (lcase.startsWith("transcribed using"))
				z = z.substring("transcribed using".length()).trim();

			if (z.equals("LotRO MIDI Player: http://lotro.acasylum.com/midi"))
				return null;
		}
		return z;
	}

	public String getGenre() {
		return Util.emptyIfNull(genre);
	}

	public String getMood() {
		return Util.emptyIfNull(mood);
	}

	@Override
	public int tickToBarNumber(long tick) {
		Entry<Long, Integer> e = bars.floorEntry(tick);
		if (e == null)
			return 0;
		return e.getValue();
	}

	public int getBarCount() {
		return bars.size();
	}

	public int getPrimaryTempoBPM() {
		return primaryTempoBPM;
	}

	public boolean isEmpty() {
		return empty;
	}

	public boolean hasTriplets() {
		return hasTriplets;
	}

	public boolean hasMixTimings() {
		return hasMixTimings;
	}

	public int getPartNumber(int trackIndex) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null)
			return 1;
		return info.number;
	}

	public LotroInstrument getPartInstrument(int trackIndex) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null)
			return LotroInstrument.DEFAULT_INSTRUMENT;
		return info.instrument;
	}

	public String getPartName(int trackIndex) {
		return Util.emptyIfNull(getPartName_MaybeNull(trackIndex));
	}

	public String getPartName_MaybeNull(int trackIndex) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null || info.name == null)
			return "Track " + trackIndex;

		if (info.nameIsFromExtendedInfo || titlePrefix == null || titlePrefix.length() == 0
				|| titlePrefix.length() == info.name.length())
			return info.name;

		return info.name.substring(titlePrefix.length()).trim();
	}

	public String getPartFullName(int trackIndex) {
		return Util.emptyIfNull(getPartFullName_MaybeNull(trackIndex));
	}

	public String getPartFullName_MaybeNull(int trackIndex) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null || info.rawName == null) {
			if (info.name != null)
				return info.name;
			return "Track " + trackIndex;
		}

		return info.rawName;
	}

	public TimeSignature getTimeSignature() {
		return timeSignature;
	}

	public KeySignature getKeySignature() {
		return keySignature;
	}

	private String getMetadata_MaybeNull(char key) {
		return metadata.get(Character.toUpperCase(key));
	}

	public int getPartStartLine(int trackIndex) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null)
			return 0;

		return info.startLine;
	}

	public int getPartEndLine(int trackIndex) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null)
			return 0;

		return info.endLine;
	}

	void setMetadata(char key, String value) {
		this.empty = false;

		key = Character.toUpperCase(key);
		if (!metadata.containsKey(key))
			metadata.put(key, value);

		if (key == 'T') {
			if (titlePrefix == null)
				titlePrefix = value;
			else
				titlePrefix = longestCommonPrefix(titlePrefix, value);
		} else if (key == 'N') {
			value = value.toLowerCase().trim();
			if (genre == null) {
				if (value.startsWith("genre:")) {
					genre = value.substring(6).trim();
				}
			}
			if (mood == null) {
				if (value.startsWith("mood:")) {
					mood = value.substring(5).trim();
				}
			}
		}
	}

	void setExtendedMetadata(AbcField field, String value) {
		switch (field) {
			case SONG_TITLE:
				songTitle = value.trim();
				break;
			case SONG_COMPOSER:
				songComposer = value.trim();
				break;
			case SONG_TRANSCRIBER:
				songTranscriber = value.trim();
				break;
			case SWING_RHYTHM:
				hasTriplets = Boolean.parseBoolean(value.trim());
				hasTripletsSet = true;
				break;
			case MIX_TIMINGS:
				hasMixTimings = Boolean.parseBoolean(value.trim());
				break;
			case ABC_CREATOR:
			case ABC_VERSION:
			case PART_NAME:
			case MADE_FOR:
			case EXPORT_TIMESTAMP:
			case SONG_DURATION:
			case TEMPO:
			case DELETE_MINIMAL_NOTES:
			case KEEP_ZERO_DURATION_NOTES:
			case SKIP_SILENCE_AT_START:
			// Ignore
			break;
		}
	}

	void setPartNumber(int trackIndex, int partNumber) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null)
			partInfoByIndex.put(trackIndex, info = new PartInfo());

		info.number = partNumber;
	}

	void setPartInstrument(int trackIndex, LotroInstrument partInstrument) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null)
			partInfoByIndex.put(trackIndex, info = new PartInfo());

		info.instrument = partInstrument;
	}

	void setPartName(int trackIndex, String partName, boolean fromExtendedInfo) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null)
			partInfoByIndex.put(trackIndex, info = new PartInfo());

		if (fromExtendedInfo || !info.nameIsFromExtendedInfo) {
			info.name = partName;
			info.nameIsFromExtendedInfo = fromExtendedInfo;
		}

		if (!fromExtendedInfo) {
			info.rawName = partName;
		}
	}

	void setPartStartLine(int trackIndex, int startLine) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null)
			partInfoByIndex.put(trackIndex, info = new PartInfo());

		info.startLine = startLine;
	}

	void setPartEndLine(int trackIndex, int endLine) {
		AbcInfo.PartInfo info = partInfoByIndex.get(trackIndex);
		if (info == null)
			partInfoByIndex.put(trackIndex, info = new PartInfo());

		info.endLine = endLine;
	}

	void addBar(long chordStartTick) {
		if (!bars.containsKey(chordStartTick)) {
			this.empty = false;
			bars.put(chordStartTick, bars.size() + 1);
		}
	}

	void setPrimaryTempoBPM(int tempoBPM) {
		this.primaryTempoBPM = tempoBPM;
		this.empty = false;
	}

	void setHasTriplets(boolean hasTriplets) {
		if (!hasTripletsSet)
			this.hasTriplets = hasTriplets;
	}

	void setHasMixTimings(boolean hasMixTimings) {
		this.hasMixTimings = hasMixTimings;
	}

	void setTimeSignature(TimeSignature timeSignature) {
		this.timeSignature = timeSignature;
	}

	void setKeySignature(KeySignature keySignature) {
		this.keySignature = keySignature;
	}

	void addRegion(AbcRegion region) {
		if (regions == null)
			regions = new TreeSet<>();

		regions.add(region);
	}

	public NavigableSet<AbcRegion> getRegions() {
		return regions;
	}

	private static final String openPunct = "[-:;\\(\\[\\{\\s]*";
	private static final Pattern trailingPunct = Pattern
			.compile(openPunct + "([\\(\\[\\{]\\d{1,2}:\\d{2}[\\)\\]\\}])?" + openPunct + "$");

	private String getTitlePrefix() {
		if (titlePrefix == null || titlePrefix.length() == 0) {
			if (metadata.containsKey('T'))
				return metadata.get('T');
			return "(Untitled)";
		}

		String ret = titlePrefix;
		ret = trailingPunct.matcher(ret).replaceFirst("");
		return ret;
	}

	private static String longestCommonPrefix(String a, String b) {
		if (a.length() > b.length())
			a = a.substring(0, b.length());

		for (int j = 0; j < a.length(); j++) {
			if (a.charAt(j) != b.charAt(j)) {
				a = a.substring(0, j);
				break;
			}
		}
		return a;
	}
}