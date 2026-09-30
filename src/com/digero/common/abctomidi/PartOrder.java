package com.digero.common.abctomidi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plays a tune's sections in the order its header gives (ABC 2.1, 3.1.9): P:ABAC in the header, P:A, P:B ... in the
 * body at the start of each section. A text transform, like VoiceSplitter (which it runs after: each voice's part keeps
 * the header P: and the section labels): the body is written out in that order, so AbcToMidi reads ordinary lines.
 * <ul>
 * <li>The order: capital letters, a count after a letter or a group (P:A3 = AAA, P:(AB)3 = ABABAB, nested), dots and
 * spaces ignored. A header P: with anything else (P:Verse), with fewer than two sections (P:A), or naming none of the
 * body's sections (P:INTRO), is no order: the tune stays as it is.</li>
 * <li>Also an order: such a P: on the first line after K: (before any notes), in a tune without one in its header. The
 * Session writes the header from its own fields and the user's text after K:, so an order written there lands in the
 * body, where the standard would read it as a section label; a label is one letter, so it can't be mistaken.</li>
 * <li>A section runs from its P: line to the next body P: line or the tune's end. Music before the first body P: is
 * played once, first. A section in the order that the body doesn't have is skipped; a section the order doesn't name
 * isn't played (that's what the order says).</li>
 * </ul>
 * Per tune (from X: to the empty line that ends it, or the next X:). Not yet: inline [P:A].
 */
public final class PartOrder {
	private PartOrder() {
	}

	/** Lines and, for each, the number of the input line it comes from (counted from 1). */
	public record Result(List<String> lines, int[] sourceLineNumbers) {
	}

	private static final Pattern ORDER = Pattern.compile("[A-Z0-9().\\s]+");
	private static final Pattern LABEL = Pattern.compile("^P:\\s*([A-Z])\\s*(?:%.*)?$");

	/** The file with each tune's sections in its header's order; null if no tune has an order (it stays as it is). */
	public static Result apply(List<String> lines) {
		List<String> out = new ArrayList<>();
		List<Integer> sources = new ArrayList<>();
		boolean changed = false;
		int i = 0;
		while (i < lines.size()) {
			if (!lines.get(i).startsWith("X:")) {
				out.add(lines.get(i));
				sources.add(i + 1);
				i++;
				continue;
			}
			int end = i + 1;
			while (end < lines.size() && !lines.get(end).isBlank() && !lines.get(end).startsWith("X:"))
				end++;
			changed |= orderTune(lines, i, end, out, sources);
			i = end;
		}
		return changed ? new Result(out, sources.stream().mapToInt(Integer::intValue).toArray()) : null;
	}

	/** Writes one tune (lines start to end), its sections in order if it has one; returns whether it had. */
	private static boolean orderTune(List<String> lines, int start, int end, List<String> out, List<Integer> sources) {
		int bodyStart = start + 1;
		while (bodyStart < end && !lines.get(bodyStart).startsWith("K:"))
			bodyStart++;
		List<Character> order = null;
		if (bodyStart < end) {
			bodyStart++;
			for (int h = start + 1; h < bodyStart; h++) {
				if (lines.get(h).startsWith("P:"))
					order = orderOf(lines.get(h));
			}
			if (order == null) {
				// The Session's: on the first line after K: (comments aside), before any notes
				int first = bodyStart;
				while (first < end && lines.get(first).startsWith("%"))
					first++;
				if (first < end && lines.get(first).startsWith("P:") && !LABEL.matcher(lines.get(first)).matches())
					order = orderOf(lines.get(first));
			}
		}

		// The sections: the lines before the first label, then each label's lines
		List<Integer> before = new ArrayList<>();
		Map<Character, List<Integer>> sections = new LinkedHashMap<>();
		List<Integer> current = before;
		if (order != null) {
			for (int b = bodyStart; b < end; b++) {
				Matcher label = LABEL.matcher(lines.get(b));
				if (label.matches()) {
					// A section written twice: the later one counts, as a later definition would
					current = new ArrayList<>();
					sections.put(label.group(1).charAt(0), current);
				}
				current.add(b);
			}
		}
		if (order == null || order.stream().noneMatch(sections::containsKey)) {
			for (int k = start; k < end; k++) {
				out.add(lines.get(k));
				sources.add(k + 1);
			}
			return false;
		}

		for (int k = start; k < bodyStart; k++) {
			out.add(lines.get(k));
			sources.add(k + 1);
		}
		for (int k : before) {
			out.add(lines.get(k));
			sources.add(k + 1);
		}
		for (char name : order) {
			List<Integer> section = sections.get(name);
			if (section == null)
				continue; // Not in the body: skipped
			for (int k : section) {
				out.add(lines.get(k));
				sources.add(k + 1);
			}
		}
		return true;
	}

	/** The order of a P: line, or null if it is none (see parse) or has fewer than two sections. */
	private static List<Character> orderOf(String line) {
		List<Character> parsed = parse(stripComment(line.substring(2)));
		return (parsed != null && parsed.size() >= 2) ? parsed : null;
	}

	/**
	 * The sections of a header P: field's value in play order (P:(AB)2C = A B A B C), or null if it is no order.
	 */
	static List<Character> parse(String value) {
		if (!ORDER.matcher(value).matches())
			return null;
		String text = value.replaceAll("[.\\s]", "");
		if (text.isEmpty() || !text.chars().anyMatch(Character::isLetter))
			return null;
		int[] position = { 0 };
		List<Character> sections = sequence(text, position);
		return (sections == null || position[0] != text.length()) ? null : sections;
	}

	/** A sequence up to a ) or the end: letters and groups, each with an optional count. */
	private static List<Character> sequence(String text, int[] position) {
		List<Character> sections = new ArrayList<>();
		while (position[0] < text.length() && text.charAt(position[0]) != ')') {
			char c = text.charAt(position[0]);
			List<Character> item;
			if (Character.isLetter(c)) {
				item = List.of(c);
				position[0]++;
			} else if (c == '(') {
				position[0]++;
				item = sequence(text, position);
				if (item == null || position[0] >= text.length())
					return null; // No )
				position[0]++;
			} else {
				return null; // A count without a section
			}
			int count = 0;
			while (position[0] < text.length() && Character.isDigit(text.charAt(position[0])))
				count = count * 10 + (text.charAt(position[0]++) - '0');
			if (count > 100)
				return null;
			for (int n = 0; n < Math.max(count, 1); n++)
				sections.addAll(item);
		}
		return sections;
	}

	private static String stripComment(String value) {
		int percent = value.indexOf('%');
		return (percent < 0) ? value : value.substring(0, percent);
	}
}