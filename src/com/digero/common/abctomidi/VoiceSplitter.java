package com.digero.common.abctomidi;

import com.digero.common.midi.MidiConstants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the voices of a tune (ABC 2.1, 7: V: and [V:]) into parts, one X: per voice, which AbcToMidi plays together as
 * it plays a Lotro file's parts. Each part keeps its voice's own repeats, lyrics, key changes and program; a text
 * transform, so the result is ordinary ABC.
 * <p>
 * Per tune (from X: to the empty line that ends it, ABC 2.1, 2.2.1); a tune without voices is left as it is:
 * <ul>
 * <li>Voices in order: the V: definitions in the header, then new ones in the body.</li>
 * <li>Body lines go to the current voice. A V: line switches voice, and so does [V:x] anywhere in a line: the line is
 * then shared, each voice keeping its own stretch and the others' blanked with spaces, so columns stay where they
 * were. Music before the first V: belongs to the first voice of the header, or without one to voice 1 (abcm2ps's and
 * abc2midi's default voice), which then comes first; other lines before it (%% directives, fields) go to every voice.
 * w: lyrics follow the notes above them; W: (words after the tune) go to the first voice.</li>
 * <li>P: section labels go to every voice, so a part order (P:AABB, A16) plays the same sections in every part. But
 * a label in a voice's music (after its V:, followed by notes) is that voice's own: in a tune written voice after
 * voice (V:1 P:A ... P:B ..., then V:2 P:A ... P:B ...), each voice has its own sections.</li>
 * <li>A voice's header: the first voice gets the tune header; the others its title, timing, key, rhythm, instructions
 * and directives (not the composer, notes and history, which would show once per voice). Each part gets its voice's
 * V: definition (its name= names the part, partName, and is a clue to the program, MidiProgramGuess), %%MIDI channel
 * with abc2midi's channel of the voice (voices take channels 1, 2, ... skipping 10, the drums),
 * so %%MIDI program C N reaches the voice it was written for, and the voice's clef=, transpose= and octave= on its K:
 * line, which setKey reads.</li>
 * <li>ABC 2.1's %%MIDI voice [ID] instrument=N (11.2), in the header or the body: to the header of every part, with the
 * ID of the voice it was written for when it has none (the V: above it, the voice of the body line it is in, else the
 * first voice). The part whose voice has that ID takes it (MidiProgramGuess), wherever it was written.</li>
 * <li>A tune with one voice with music (FolkWiki writes V:1 above every tune) gets no V: line in its part, unless the
 * voice has a name=: the part keeps the tune's title instead of "Voice 1". Its clef=, transpose= and octave= still
 * reach K:, and a %%MIDI voice for it the part (without the ID).</li>
 * <li>Parts are numbered 1, 2, 3 ... in the file, when any tune in it has voices.</li>
 * </ul>
 * Not yet: &amp; overlays (two voices in one bar) and %%score grouping (layout only).
 */
public final class VoiceSplitter {
	private VoiceSplitter() {
	}

	/** Lines and, for each, the number of the source line it comes from (counted from 1). */
	public record Result(List<String> lines, int[] sourceLineNumbers) {
	}

	private static final Pattern FIELD = Pattern.compile("^([A-Za-z]):");
	private static final Pattern VOICE_LINE = Pattern.compile("^V:\\s*(\\S+)\\s*(.*)$");
	private static final Pattern INLINE_VOICE = Pattern.compile("\\[V:\\s*([^\\]\\s]+)[^\\]]*\\]");
	/** Header fields a voice after the first gets: title, timing, key, rhythm, group, instructions, part order. */
	private static final String HEADER_FIELDS_FOR_EVERY_VOICE = "TMLQKRGIP";
	/** The voice of the music before the first V:, when the header defines none (abcm2ps, abc2midi). */
	private static final String DEFAULT_VOICE = "1";
	/** Voice properties that change what is played, for its K: line (setKey). */
	private static final Pattern PLAYED_PROPERTY = Pattern
			.compile("(?i)^(clef=\\S+|(treble|alto|tenor|bass|perc|none)\\d?([+-]8)?|transpose=\\S+|t=\\S+|octave=\\S+)$");
	private static final Pattern NAME = Pattern.compile("(?i)\\b(?:name|nm)\\s*=\\s*(?:\"([^\"]*)\"|(\\S+))");
	/** %%MIDI voice or I:MIDI voice (ABC 2.1, 11.2); the words after voice in group 1. */
	private static final Pattern MIDI_VOICE = Pattern.compile("(?i)^(?:%%|I:)\\s*MIDI(?:\\s*=)?\\s+voice\\b(.*)$");


	/** A voice: its id, the properties of its definition, and its body lines (with their source line numbers). */
	private static final class Voice {
		final String id;
		String properties = "";
		final List<String> lines = new ArrayList<>();
		final List<Integer> sources = new ArrayList<>();

		Voice(String id) {
			this.id = id;
		}

		void add(String line, int source) {
			lines.add(line);
			sources.add(source);
		}
	}

	/** The file with each tune's voices as parts; null if no tune in it has voices (the file stays as it is). */
	public static Result split(List<String> lines) {
		if (lines.stream().noneMatch(l -> VOICE_LINE.matcher(l).matches() || INLINE_VOICE.matcher(l).find()))
			return null;
		List<String> out = new ArrayList<>();
		List<Integer> sources = new ArrayList<>();
		int partNumber = 0;
		int i = 0;
		while (i < lines.size()) {
			if (!lines.get(i).startsWith("X:")) {
				// Before the first X:, and free text between tunes
				out.add(lines.get(i));
				sources.add(i + 1);
				i++;
				continue;
			}
			// The tune: from its X: to the empty line that ends it (or the next X:)
			int end = i + 1;
			while (end < lines.size() && !lines.get(end).isBlank() && !lines.get(end).startsWith("X:"))
				end++;
			partNumber = splitTune(lines, i, end, partNumber, out, sources);
			i = end;
		}
		return new Result(out, sources.stream().mapToInt(Integer::intValue).toArray());
	}

	/** Writes one tune (lines start to end) as parts; returns the last part number used. */
	private static int splitTune(List<String> lines, int start, int end, int partNumber, List<String> out,
			List<Integer> sources) {
		// The header: from X: to its K: (ABC 2.1: the last field of the header)
		int bodyStart = start + 1;
		while (bodyStart < end && !lines.get(bodyStart).startsWith("K:"))
			bodyStart++;
		if (bodyStart < end)
			bodyStart++;
		else
			bodyStart = start + 1; // No K:: every line is body

		// The voices, in order: the header's definitions, then new ones in the body
		Map<String, Voice> voices = new LinkedHashMap<>();
		String headerVoice = null; // The last V: in the header: a %%MIDI voice without an ID below it is for that voice
		List<String> midiVoiceLines = new ArrayList<>(); // %%MIDI voice, with the voice's ID, for every part's header
		List<Integer> midiVoiceSources = new ArrayList<>();
		for (int h = start + 1; h < bodyStart; h++) {
			Matcher v = VOICE_LINE.matcher(lines.get(h));
			if (v.matches()) {
				voices.computeIfAbsent(v.group(1), Voice::new).properties = v.group(2).trim();
				headerVoice = v.group(1);
			}
			Matcher midiVoice = MIDI_VOICE.matcher(lines.get(h));
			if (midiVoice.matches()) {
				midiVoiceLines.add(midiVoiceWithId(midiVoice.group(1), headerVoice));
				midiVoiceSources.add(h + 1);
			}
		}
		boolean headerVoices = !voices.isEmpty();
		boolean musicBeforeVoices = false;
		for (int b = bodyStart; b < end; b++) {
			String line = lines.get(b);
			Matcher v = VOICE_LINE.matcher(line);
			Matcher inline = INLINE_VOICE.matcher(line);
			boolean hasInline = inline.find();
			if (voices.isEmpty() && !v.matches()
					&& (hasInline ? !line.substring(0, inline.start()).isBlank() : isMusic(line)))
				musicBeforeVoices = true;
			if (v.matches()) {
				Voice voice = voices.computeIfAbsent(v.group(1), Voice::new);
				if (voice.properties.isEmpty())
					voice.properties = v.group(2).trim();
			}
			if (hasInline) {
				do {
					voices.computeIfAbsent(inline.group(1), Voice::new);
				} while (inline.find());
			}
		}
		if (musicBeforeVoices && !headerVoices && !voices.isEmpty()) {
			// The default voice 1 starts the tune: it comes first
			Map<String, Voice> reordered = new LinkedHashMap<>();
			reordered.put(DEFAULT_VOICE, voices.getOrDefault(DEFAULT_VOICE, new Voice(DEFAULT_VOICE)));
			reordered.putAll(voices);
			voices = reordered;
		}
		if (voices.isEmpty()) {
			// No voices: the tune as it is, numbered as a part
			out.add("X:" + (++partNumber));
			sources.add(start + 1);
			for (int k = start + 1; k < end; k++) {
				out.add(lines.get(k));
				sources.add(k + 1);
			}
			return partNumber;
		}

		// The body, voice by voice
		List<Voice> order = new ArrayList<>(voices.values());
		Voice first = order.get(0);
		Voice current = null;
		for (int b = bodyStart; b < end; b++) {
			String line = lines.get(b);
			Matcher v = VOICE_LINE.matcher(line);
			if (v.matches()) {
				current = voices.get(v.group(1));
				continue;
			}
			if (line.startsWith("P:")) {
				if (current != null && !voiceLineFollows(lines, b + 1, end)) {
					current.add(line, b + 1); // In this voice's music
				} else {
					for (Voice voice : order)
						voice.add(line, b + 1);
				}
				continue;
			}
			if (line.startsWith("W:")) {
				first.add(line, b + 1);
				continue;
			}
			Matcher midiVoice = MIDI_VOICE.matcher(line);
			if (midiVoice.matches()) {
				midiVoiceLines.add(midiVoiceWithId(midiVoice.group(1), (current != null) ? current.id : first.id));
				midiVoiceSources.add(b + 1);
				continue;
			}
			Matcher inline = INLINE_VOICE.matcher(line);
			if (inline.find()) {
				// Each voice keeps its own stretch of the line; the rest is blanked, so columns stay
				int from = 0;
				Voice owner = current;
				do {
					if (owner == null && !line.substring(from, inline.start()).isBlank())
						owner = first;
					if (owner != null)
						owner.add(keepOnly(line, from, inline.start()), b + 1);
					owner = voices.get(inline.group(1));
					from = inline.end();
				} while (inline.find());
				owner.add(keepOnly(line, from, line.length()), b + 1);
				current = owner;
				continue;
			}
			if (current == null) {
				// Before the first V:: music belongs to the first voice, other lines to every voice
				if (isMusic(line)) {
					current = first;
				} else {
					for (Voice voice : order)
						voice.add(line, b + 1);
					continue;
				}
			}
			current.add(line, b + 1);
		}

		// Each voice as a part; a voice without music (a V: line at the end) has none. Its channel stays taken, as
		// %%MIDI program C N counts it.
		boolean alone = order.stream().filter(v -> v.lines.stream().anyMatch(VoiceSplitter::isMusic)).count() == 1;
		boolean firstPart = true;
		for (int n = 0; n < order.size(); n++) {
			Voice voice = order.get(n);
			if (voice.lines.stream().noneMatch(VoiceSplitter::isMusic))
				continue;
			out.add("X:" + (++partNumber));
			sources.add(start + 1);
			for (int h = start + 1; h < bodyStart; h++) {
				String line = lines.get(h);
				if (VOICE_LINE.matcher(line).matches() || MIDI_VOICE.matcher(line).matches())
					continue;
				Matcher field = FIELD.matcher(line);
				boolean keep = firstPart || line.startsWith("%%")
						|| (field.find() && HEADER_FIELDS_FOR_EVERY_VOICE.indexOf(field.group(1).charAt(0)) >= 0);
				if (!keep)
					continue;
				if (line.startsWith("K:")) {
					// The voice's definition and name before K:, which ends the header
					addVoiceHeader(voice, n, alone, h + 1, out, sources);
					addAll(alone ? withoutVoiceId(midiVoiceLines, voice.id) : midiVoiceLines, midiVoiceSources, out,
							sources);
					line = withPlayedProperties(line, playedProperties(voice.properties));
				}
				out.add(line);
				sources.add(h + 1);
			}
			if (bodyStart == start + 1) {
				addVoiceHeader(voice, n, alone, start + 1, out, sources); // No K: in the header
				addAll(alone ? withoutVoiceId(midiVoiceLines, voice.id) : midiVoiceLines, midiVoiceSources, out, sources);
			}
			for (int k = 0; k < voice.lines.size(); k++) {
				if (voice.lines.get(k).isBlank())
					continue;
				out.add(voice.lines.get(k));
				sources.add(voice.sources.get(k));
			}
			firstPart = false;
		}
		return partNumber;
	}

	/**
	 * The voice's V: line (which names the part, partName) and its %%MIDI channel.
	 *
	 * @param alone The tune's only voice with music: no V: line without a name=, so the part keeps the tune's title
	 */
	private static void addVoiceHeader(Voice voice, int index, boolean alone, int source, List<String> out,
									   List<Integer> sources) {
		if (!alone || NAME.matcher(voice.properties).find()) {
			out.add(("V:" + voice.id + " " + voice.properties).trim());
			sources.add(source);
		}
		if (!voice.properties.matches("(?i).*\\bchannel\\s*=.*")) {
			out.add("%%MIDI channel " + abc2midiChannel(index));
			sources.add(source);
		}
	}

	/** The %%MIDI voice lines for the tune's only voice: its own ID left out, so they reach a part without a V: line. */
	private static List<String> withoutVoiceId(List<String> midiVoiceLines, String id) {
		List<String> lines = new ArrayList<>();
		for (String line : midiVoiceLines) {
			String[] words = line.split("\\s+", 4); // %%MIDI voice ID rest
			lines.add((words.length >= 3 && words[2].equals(id)) ? ("%%MIDI voice " + (words.length > 3 ? words[3] : "")).trim()
					: line);
		}
		return lines;
	}

	private static void addAll(List<String> lines, List<Integer> lineSources, List<String> out, List<Integer> sources) {
		out.addAll(lines);
		sources.addAll(lineSources);
	}

	/**
	 * A %%MIDI voice directive with the ID of its voice: as written if it has one (the first word that isn't
	 * instrument=, bank= or mute), else with the given ID (none if null).
	 *
	 * @param words The words after voice
	 */
	private static String midiVoiceWithId(String words, String id) {
		String first = words.replaceAll("%.*", "").trim().split("\\s+")[0];
		boolean hasId = !first.isEmpty() && !first.contains("=") && !first.equalsIgnoreCase("mute");
		return ("%%MIDI voice " + ((hasId || id == null) ? words.trim() : id + " " + words.trim())).trim();
	}

	/**
	 * The name of the part a V: field starts (its value: "1 name=\"Violin\""): the voice's name= or nm=, else "Voice"
	 * and its id. Not %%part-name, which names a Lotro instrument and marks a file made for Lotro.
	 */
	public static String partName(String voiceField) {
		Matcher v = VOICE_LINE.matcher("V:" + voiceField.trim());
		if (!v.matches())
			return null;
		Matcher m = NAME.matcher(v.group(2));
		if (m.find()) {
			String name = (m.group(1) != null) ? m.group(1) : m.group(2);
			if (!name.isBlank())
				return name.trim();
		}
		return "Voice " + v.group(1);
	}

	/** abc2midi's channel of the voice at this index (from 0): 1, 2, ... 9, 11, ... (10 is the drums). */
	static int abc2midiChannel(int index) {
		// Counted from 1 as in %%MIDI channel, which only MidiProgramGuess reads (never a MIDI message's channel)
		int channel = index + 1;
		if (channel >= MidiConstants.DRUM_CHANNEL+1)
			channel++;
		return (channel > MidiConstants.CHANNEL_COUNT) ? MidiConstants.CHANNEL_COUNT : channel;
	}

	/** The voice's clef, transpose= and octave= words, for its K: line (" clef=bass octave=-1"), or "". */
	private static String playedProperties(String properties) {
		StringBuilder words = new StringBuilder();
		// Quoted names can hold spaces: they go first
		for (String word : properties.replaceAll("\"[^\"]*\"", " ").trim().split("\\s+")) {
			if (PLAYED_PROPERTY.matcher(word).matches())
				words.append(' ').append(word);
		}
		return words.toString();
	}

	/** The K: line with the played properties before its % comment (after it they'd be part of the comment). */
	private static String withPlayedProperties(String keyLine, String properties) {
		if (properties.isEmpty())
			return keyLine;
		for (int k = 0; k < keyLine.length(); k++) {
			if (keyLine.charAt(k) == '%' && (k == 0 || keyLine.charAt(k - 1) != '\\'))
				return keyLine.substring(0, k).stripTrailing() + properties + " " + keyLine.substring(k);
		}
		return keyLine + properties;
	}

	/** A line with only the stretch from to end kept, the rest blanked with spaces. */
	private static String keepOnly(String line, int from, int end) {
		return " ".repeat(from) + line.substring(from, end);
	}

	/** Whether the next line from from (comments skipped) switches voice: a V: line, or a line starting with [V:. */
	private static boolean voiceLineFollows(List<String> lines, int from, int end) {
		for (int k = from; k < end; k++) {
			String line = lines.get(k).stripLeading();
			if (line.startsWith("%"))
				continue;
			return VOICE_LINE.matcher(line).matches() || line.startsWith("[V:");
		}
		return false;
	}

	/** A line of notes: not empty, not a field, a comment or a directive. */
	private static boolean isMusic(String line) {
		String trimmed = line.stripLeading();
		return !trimmed.isEmpty() && !trimmed.startsWith("%") && !FIELD.matcher(trimmed).find();
	}
}