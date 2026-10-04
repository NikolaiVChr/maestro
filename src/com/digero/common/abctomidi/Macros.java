package com.digero.common.abctomidi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ABC 2.1's macros (m:, section 9) and redefinable symbols (U:, 4.16) in the standard reading: the music's text with
 * them written out, before the rest of the reading (like VoiceSplitter and PartOrder). The macros first, then the
 * symbols, as ABC 2.1 (9) says.
 * <ul>
 * <li>Where: a definition in the file header holds for every tune of the file; one in a tune for the rest of that
 * tune. A later one with the same target replaces it. Fields, comments and the free text after a tune are left
 * alone.</li>
 * <li>A static macro, {@code m: ~G3 = G{A}G{F}G}: its target where it stands as a whole note (not in ~G3/2).</li>
 * <li>A transposing macro, {@code m: ~n2 = (3o/n/m/ n}: n is a note without an accidental, of the target's length; in
 * the replacement the letters h to z are the notes of the scale around it (o above, m below). LENIENT: a target
 * without a length ({@code Tn}, O'Neill's 1001) is a note written without one.</li>
 * <li>{@code U: T = !trill!}: H to W, h to w and ~ stand for a decoration (!trill!, +trill+) or a text ("^+");
 * !nil! and !none! for nothing.</li>
 * <li>Not in quoted text, decorations (!...! +...+), inline fields or comments. What a macro writes isn't expanded
 * again by the macros.</li>
 * </ul>
 * The parser checks the m: and U: lines (check), so a wrong one is an error with the tune's name. Not yet: the inline
 * fields [m:] and [U:]. A macro's notes are read as written, so w: lyrics count them.
 */
final class Macros {
	private Macros() {
	}

	/**
	 * The lines with the macros and symbols written out, and for each line changed (by its number, from 1) the column
	 * in the file's line of each of its columns, and of its end.
	 */
	record Result(List<String> lines, Map<Integer, int[]> sourceColumns) {
	}

	/** m: ~G3 = G{A}G{F}G; a transposing one has the target's text before its n (prefix) and its length. */
	record Macro(String target, String replacement, String prefix, String length) {
		boolean isTransposing() {
			return prefix != null;
		}
	}

	/** A field (T:, w: ...) or a field going on (+:): no music. */
	private static final Pattern FIELD = Pattern.compile("[A-Za-z+]:");
	/** A transposing macro's target: the text before n, and the note's length. */
	private static final Pattern TRANSPOSING = Pattern.compile("([^n]*)n([\\d/]*)");
	private static final String NOTE_LETTERS = "CDEFGAB";
	/** What makes a note longer or moves its octave: a target ending in a note matches only where the note ends. */
	private static final String NOTE_GOES_ON = "0123456789/,'";
	/** The letters U: can give a meaning (ABC 2.1, 4.16). */
	private static final Pattern SYMBOL_LETTER = Pattern.compile("[H-Wh-w~]");

	static final String MACRO_INVALID = "common.abctomidi.macro.invalid";
	static final String MACRO_TRANSPOSING = "common.abctomidi.macro.transposing";
	static final String SYMBOL_INVALID = "common.abctomidi.symbol.invalid";

	/** The macros and symbols in force. */
	private static final class Definitions {
		final Map<String, Macro> macros = new LinkedHashMap<>();
		final Map<Character, String> symbols = new HashMap<>();

		Definitions copy() {
			Definitions copy = new Definitions();
			copy.macros.putAll(macros);
			copy.symbols.putAll(symbols);
			return copy;
		}

		/** An m: or U: line's definition; a wrong one is skipped here (the parser says what's wrong). */
		void add(String field) {
			try {
				if (field.startsWith("m:")) {
					Macro macro = macro(field.substring(2));
					macros.put(macro.target(), macro);
				} else {
					String[] symbol = symbol(field.substring(2));
					symbols.put(symbol[0].charAt(0), symbol[1]);
				}
			} catch (IllegalArgumentException e) {
				// A wrong definition
			}
		}
	}

	/** Checks an m: or U: line; throws IllegalArgumentException with the UIText key of what's wrong. */
	static void check(String field) {
		if (field.startsWith("m:"))
			macro(field.substring(2));
		else
			symbol(field.substring(2));
	}

	/**
	 * An m: field's value: target = replacement. A = followed by a note is a natural in the target (m: T=c2 = ...): the
	 * first = that isn't one splits them, else the first =.
	 */
	static Macro macro(String value) {
		String text = withoutComment(value);
		int equals = -1;
		for (int i = text.indexOf('='); i >= 0; i = text.indexOf('=', i + 1)) {
			boolean natural = i + 1 < text.length() && NOTE_LETTERS.indexOf(Character.toUpperCase(text.charAt(i + 1))) >= 0;
			if (!natural) {
				equals = i;
				break;
			}
		}
		if (equals < 0)
			equals = text.indexOf('=');
		String target = (equals < 0) ? "" : text.substring(0, equals).trim();
		if (target.isEmpty())
			throw new IllegalArgumentException(MACRO_INVALID);
		String replacement = text.substring(equals + 1).trim();
		if (target.indexOf('n') < 0)
			return new Macro(target, replacement, null, null);
		Matcher transposing = TRANSPOSING.matcher(target);
		if (!transposing.matches() || (transposing.group(2).isEmpty() && !AbcToMidi.LENIENT))
			throw new IllegalArgumentException(MACRO_TRANSPOSING);
		return new Macro(target, replacement, transposing.group(1), transposing.group(2));
	}

	/** A U: field's value: { the letter, what it stands for ("" for !nil! and !none!) }. */
	static String[] symbol(String value) {
		String text = withoutComment(value);
		int equals = text.indexOf('=');
		String letter = (equals < 0) ? "" : text.substring(0, equals).trim();
		String meaning = (equals < 0) ? "" : text.substring(equals + 1).trim();
		if (!SYMBOL_LETTER.matcher(letter).matches() || meaning.length() < 2)
			throw new IllegalArgumentException(SYMBOL_INVALID);
		char first = meaning.charAt(0);
		if ((first != '!' && first != '+' && first != '"') || meaning.charAt(meaning.length() - 1) != first)
			throw new IllegalArgumentException(SYMBOL_INVALID);
		if (meaning.equals("!nil!") || meaning.equals("!none!"))
			meaning = "";
		return new String[] { letter, meaning };
	}

	/** The text before a comment (% not written as \%). */
	private static String withoutComment(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '%' && (i == 0 || text.charAt(i - 1) != '\\'))
				return text.substring(0, i);
		}
		return text;
	}

	/** The lines with the macros and symbols written out, or null when nothing changed. */
	static Result apply(List<String> lines) {
		if (lines.stream().map(String::stripLeading).noneMatch(l -> l.startsWith("m:") || l.startsWith("U:")))
			return null;
		// ABC 2.1 (2.2): in a file of X: tunes, the file header before the first X:, free text after a tune's empty line
		boolean tunes = lines.stream().anyMatch(l -> l.startsWith("X:"));
		Definitions file = new Definitions();
		Definitions definitions = file;
		boolean tuneSeen = false;
		boolean inTune = !tunes;
		List<String> written = new ArrayList<>(lines);
		Map<Integer, int[]> sourceColumns = new HashMap<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line.startsWith("X:")) {
				definitions = file.copy();
				tuneSeen = true;
				inTune = true;
				continue;
			}
			if (tunes && inTune && line.isBlank()) {
				inTune = false;
				continue;
			}
			if (!inTune && tuneSeen)
				continue; // Free text after a tune
			String field = line.stripLeading();
			if (field.startsWith("m:") || field.startsWith("U:")) {
				definitions.add(field);
				continue;
			}
			if (!inTune || field.startsWith("%") || FIELD.matcher(field).lookingAt())
				continue;
			Written expanded = expand(line, definitions);
			if (expanded != null) {
				written.set(i, expanded.text());
				sourceColumns.put(i + 1, expanded.sourceColumns());
			}
		}
		return sourceColumns.isEmpty() ? null : new Result(written, sourceColumns);
	}

	/** A line written out: its text, and the column in the line before of each of its columns and of its end. */
	private record Written(String text, int[] sourceColumns) {
	}

	/** What to write instead of the text at a place: the text, and how many characters it replaces. */
	private record Replacement(String text, int length) {
	}

	/** A rule of writing out: the replacement at a place in the line, or null. */
	private interface Rule {
		Replacement at(String line, int index);
	}

	/** A line of music with its macros, then its symbols, written out; null when nothing changed. */
	private static Written expand(String line, Definitions definitions) {
		Written macros = definitions.macros.isEmpty() ? null : rewrite(line, macroRule(definitions));
		String text = (macros != null) ? macros.text() : line;
		Written symbols = definitions.symbols.isEmpty() ? null : rewrite(text, (l, i) -> {
			String meaning = definitions.symbols.get(l.charAt(i));
			return (meaning != null) ? new Replacement(meaning, 1) : null;
		});
		if (symbols == null)
			return macros;
		if (macros == null)
			return symbols;
		int[] columns = symbols.sourceColumns().clone();
		for (int k = 0; k < columns.length; k++)
			columns[k] = macros.sourceColumns()[columns[k]];
		return new Written(symbols.text(), columns);
	}

	/** The macros at a place: static ones first, the longest target first. */
	private static Rule macroRule(Definitions definitions) {
		List<Macro> macros = new ArrayList<>(definitions.macros.values());
		macros.sort(Comparator.comparing(Macro::isTransposing).thenComparing(m -> -m.target().length()));
		return (line, i) -> {
			for (Macro macro : macros) {
				if (!macro.isTransposing()) {
					String target = macro.target();
					int end = i + target.length();
					if (line.startsWith(target, i) && !(endsInNote(target.charAt(target.length() - 1)) && end < line.length()
							&& NOTE_GOES_ON.indexOf(line.charAt(end)) >= 0)) {
						return new Replacement(macro.replacement(), target.length());
					}
					continue;
				}
				// The prefix, a note without an accidental, then exactly the target's length
				int k = i + macro.prefix().length();
				if (!line.startsWith(macro.prefix(), i) || k >= line.length()
						|| NOTE_LETTERS.indexOf(Character.toUpperCase(line.charAt(k))) < 0) {
					continue;
				}
				if (macro.prefix().isEmpty() && i > 0 && "^_=".indexOf(line.charAt(i - 1)) >= 0)
					continue;
				int octaveEnd = k + 1;
				while (octaveEnd < line.length() && (line.charAt(octaveEnd) == '\'' || line.charAt(octaveEnd) == ','))
					octaveEnd++;
				int end = octaveEnd + macro.length().length();
				if (!line.startsWith(macro.length(), octaveEnd) || (end < line.length()
						&& (Character.isDigit(line.charAt(end)) || line.charAt(end) == '/'))) {
					continue;
				}
				return new Replacement(transpose(macro.replacement(), line.substring(k, octaveEnd)), end - i);
			}
			return null;
		};
	}

	/** The target ends in a note or its length (else it ends anywhere). */
	private static boolean endsInNote(char c) {
		return NOTE_GOES_ON.indexOf(c) >= 0 || Character.isLetter(c);
	}

	/** The line with the rule's replacements, outside quoted text, decorations, inline fields and comments. */
	private static Written rewrite(String line, Rule rule) {
		StringBuilder text = new StringBuilder();
		List<Integer> columns = new ArrayList<>();
		boolean changed = false;
		int i = 0;
		while (i < line.length()) {
			Replacement replacement = rule.at(line, i);
			if (replacement != null) {
				text.append(replacement.text());
				for (int k = 0; k < replacement.text().length(); k++)
					columns.add(i);
				i += replacement.length();
				changed = true;
				continue;
			}
			int end = Math.max(i + 1, notMusicEnd(line, i));
			for (int k = i; k < end; k++)
				columns.add(k);
			text.append(line, i, end);
			i = end;
		}
		columns.add(line.length());
		return changed ? new Written(text.toString(), columns.stream().mapToInt(Integer::intValue).toArray()) : null;
	}

	/**
	 * The end of the quoted text, decoration (!trill! +trill+; not a lone ! line break), inline field or comment that
	 * starts at the index; else the index.
	 */
	private static int notMusicEnd(String text, int i) {
		char c = text.charAt(i);
		if (c == '%' && (i == 0 || text.charAt(i - 1) != '\\'))
			return text.length();
		if (c == '"' || c == '+') {
			int close = text.indexOf(c, i + 1);
			return (close < 0) ? i : close + 1;
		}
		if (c == '!') {
			int close = text.indexOf('!', i + 1);
			if (close < 0)
				return i;
			for (int k = i + 1; k < close; k++) {
				if ("|[: \t".indexOf(text.charAt(k)) >= 0)
					return i; // A line break (ABC 2.1, 12), not a decoration
			}
			return close + 1;
		}
		if (c == '[' && i + 2 < text.length() && Character.isLetter(text.charAt(i + 1)) && text.charAt(i + 2) == ':') {
			int close = text.indexOf(']', i + 3);
			return (close < 0) ? i : close + 1;
		}
		return i;
	}

	/** A transposing macro's replacement for the note: h to z as steps of the scale from n, with their own ' and ,. */
	private static String transpose(String replacement, String note) {
		int base = step(note);
		StringBuilder written = new StringBuilder();
		for (int i = 0; i < replacement.length(); i++) {
			int end = notMusicEnd(replacement, i);
			if (end > i) {
				written.append(replacement, i, end);
				i = end - 1;
				continue;
			}
			char c = replacement.charAt(i);
			if (c < 'h' || c > 'z') {
				written.append(c);
				continue;
			}
			int step = base + (c - 'n');
			while (i + 1 < replacement.length() && (replacement.charAt(i + 1) == '\'' || replacement.charAt(i + 1) == ','))
				step += (replacement.charAt(++i) == '\'') ? 7 : -7;
			written.append(noteName(step));
		}
		return written.toString();
	}

	/** The note's step of the scale: C 0, c 7, c' 14, C, -7. */
	private static int step(String note) {
		char letter = note.charAt(0);
		int step = NOTE_LETTERS.indexOf(Character.toUpperCase(letter)) + (Character.isLowerCase(letter) ? 7 : 0);
		for (int i = 1; i < note.length(); i++)
			step += (note.charAt(i) == '\'') ? 7 : -7;
		return step;
	}

	/** The note of a step of the scale, as ABC writes it. */
	private static String noteName(int step) {
		int octave = Math.floorDiv(step, 7);
		char letter = NOTE_LETTERS.charAt(Math.floorMod(step, 7));
		if (octave <= 0)
			return letter + ",".repeat(-octave);
		return Character.toLowerCase(letter) + "'".repeat(octave - 1);
	}
}