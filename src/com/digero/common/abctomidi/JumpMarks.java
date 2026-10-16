package com.digero.common.abctomidi;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The marks of a piece's form that Repeats plays (user's rules, 2026-10-05): segno, coda, To Coda, Fine, D.C. and D.S.
 * (also al Fine and al Coda). As decorations (!segno! !coda! !fine! !D.C.! !D.S.! !dacapo! !dacoda!, ABC 2.1 4.14;
 * abcm2ps's !D.C.alfine! !D.S.alcoda! ...), as the letters S and O, and LENIENT as a text that is exactly one of them
 * ("Fine", "D.S. al Coda", "^To Coda"), as older tune books write them. A name is read without case, spaces and
 * punctuation: D.C., DC and Da Capo; al Fine and alfine.
 */
final class JumpMarks {
	private JumpMarks() {
	}

	enum Mark {
		SEGNO,
		CODA,
		TO_CODA,
		FINE, 
		DA_CAPO,
		DA_CAPO_AL_FINE,
		DA_CAPO_AL_CODA,
		DAL_SEGNO, 
		DAL_SEGNO_AL_FINE,
		DAL_SEGNO_AL_CODA;

		/** D.C. or D.S., with or without al Fine or al Coda. */
		boolean jumpsBack() {
			return this == DA_CAPO || this == DA_CAPO_AL_FINE || this == DA_CAPO_AL_CODA || this == DAL_SEGNO || this == DAL_SEGNO_AL_FINE || this == DAL_SEGNO_AL_CODA;
		}

		/** D.S., with or without al Fine or al Coda. */
		boolean toSegno() {
			return this == DAL_SEGNO || this == DAL_SEGNO_AL_FINE || this == DAL_SEGNO_AL_CODA;
		}

		/** After this D.C. or D.S., Fine ends the part (not after an al Coda). */
		boolean stopsAtFine() {
			return this != DA_CAPO_AL_CODA && this != DAL_SEGNO_AL_CODA;
		}

		/** After this D.C. or D.S., To Coda jumps to the coda (not after an al Fine). */
		boolean jumpsToCoda() {
			return this != DA_CAPO_AL_FINE && this != DAL_SEGNO_AL_FINE;
		}
	}

	/** The names, in lower case without spaces and punctuation. */
	private static final Map<String, Mark> NAMES = Map.ofEntries(
			Map.entry("segno", Mark.SEGNO),
			Map.entry("coda", Mark.CODA), 
			Map.entry("tocoda", Mark.TO_CODA), 
			Map.entry("alcoda", Mark.TO_CODA),
			Map.entry("dacoda", Mark.TO_CODA),
			Map.entry("fine", Mark.FINE),
			Map.entry("end", Mark.FINE),
			Map.entry("dc", Mark.DA_CAPO),
			Map.entry("dacapo", Mark.DA_CAPO),
			Map.entry("dcalfine", Mark.DA_CAPO_AL_FINE),
			Map.entry("dacapoalfine", Mark.DA_CAPO_AL_FINE),
			Map.entry("dcalcoda", Mark.DA_CAPO_AL_CODA),
			Map.entry("dacapoalcoda", Mark.DA_CAPO_AL_CODA),
			Map.entry("ds", Mark.DAL_SEGNO),
			Map.entry("dalsegno", Mark.DAL_SEGNO),
			Map.entry("dsalfine", Mark.DAL_SEGNO_AL_FINE),
			Map.entry("dalsegnoalfine", Mark.DAL_SEGNO_AL_FINE),
			Map.entry("dsalcoda", Mark.DAL_SEGNO_AL_CODA),
			Map.entry("dalsegnoalcoda", Mark.DAL_SEGNO_AL_CODA));

	/**
	 * A text's placement (ABC 2.1, 4.19: ^ _ < > @), and the spaces and punctuation a name is read without. Not / + #,
	 * which chord names have ("D/C+" is no D.C.).
	 */
	private static final Pattern PLACEMENT = Pattern.compile("^[_^<>@]");
	private static final Pattern PUNCTUATION = Pattern.compile("[\\s.,'\u2019-]");

	/** The mark a decoration's name or a text is, or null. */
	static Mark of(String name) {
		String key = PUNCTUATION.matcher(PLACEMENT.matcher(name.trim()).replaceFirst("")).replaceAll("");
		return NAMES.get(key.toLowerCase(Locale.ROOT));
	}

	/** The letters S and O (ABC 2.1, 4.14), or null. */
	static Mark ofLetter(char letter) {
		return (letter == 'S') ? Mark.SEGNO : (letter == 'O') ? Mark.CODA : null;
	}
	
	/**
	 * The mark from start to end in the line stands right after a bar line, with nothing after it on its line but
	 * spaces and a comment (Norbeck's ":| !fine!"): it belongs to that bar line. Before it only spaces, or nothing at
	 * the line's start. A ] ends a bar line only in |] (fourth opinion pass 2, BUG-14: "[ceg] !fine!" stopped there).
	 */
	static boolean endsBarBefore(String line, int start, int end) {
		String before = line.substring(0, start).stripTrailing();
		String after = line.substring(end).strip();
		boolean barBefore = before.isEmpty() || before.endsWith("|") || before.endsWith(":") || before.endsWith("|]");
		return barBefore && (after.isEmpty() || after.startsWith("%"));
	}
	
	/** A field (T:, w: ...) or a field going on (+:): no music. */
	private static final Pattern FIELD = Pattern.compile("[A-Za-z+]:");

	/**
	 * The first coda mark from the place (the start of a mark, or of the music), where the coda starts: {line index,
	 * column}, or null up to the part's end (the next X: or empty line). In the music only: not in fields, comments or
	 * inline fields.
	 */
	static int[] nextCoda(List<String> lines, int lineIndex, int column) {
		return find(Set.of(Mark.CODA), false, lines, lineIndex, column);
	}

	/**
	 * Whether the ending that starts at the place has one of these marks, up to its end (:| || |] [| or the next
	 * ending). After a D.C. or D.S. the ending with the Fine or To Coda it stops at is played, not the last (a "Fine"
	 * over the first ending of a waltz whose second leads on; fourth opinion BUG-03).
	 */
	static boolean endingHas(Set<Mark> marks, List<String> lines, int lineIndex, int column) {
		return !marks.isEmpty() && find(marks, true, lines, lineIndex, column) != null;
	}

	/**
	 * The first of the marks from the place: {line index, column}, or null up to the part's end, or with
	 * inEnding up to the ending's end. In the music only: not in fields, comments or inline fields.
	 */
	private static int[] find(Set<Mark> wanted, boolean inEnding, List<String> lines, int lineIndex, int column) {
		for (int l = lineIndex; l < lines.size(); l++) {
			String line = lines.get(l);
			if (l > lineIndex && (line.startsWith("X:") || line.isBlank()))
				return null;
			String field = line.stripLeading();
			if (field.startsWith("%") || FIELD.matcher(field).lookingAt())
				continue;
			int i = (l == lineIndex) ? column : 0;
			while (i < line.length()) {
				char c = line.charAt(i);
				// A text, decoration, inline field or comment; not a lone ! (a line break, fourth opinion BUG-05)
				int end = Macros.notMusicEnd(line, i);
				if (end > i) {
					boolean mark = (c == '!' || c == '+' || (c == '"' && AbcToMidi.LENIENT));
					if (mark && wanted.contains(of(line.substring(i + 1, end - 1))))
						return new int[] { l, i };
					i = end;
					continue;
				}
				if (c == 'O' && wanted.contains(Mark.CODA))
					return new int[] { l, i };
				if (inEnding && endsEnding(line, i))
					return null;
				i++;
			}
		}
		return null;
	}

	/** At the index an ending ends: :| || |] [| or the next ending (|2 [2). */
	private static boolean endsEnding(String line, int i) {
		char next = (i + 1 < line.length()) ? line.charAt(i + 1) : ' ';
		return switch (line.charAt(i)) {
			case ':' -> next == '|';
			case '|' -> next == '|' || next == ']' || Character.isDigit(next);
			case '[' -> next == '|' || Character.isDigit(next);
			default -> false;
		};
	}
}