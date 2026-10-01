package com.digero.common.abctomidi;

import org.opentest4j.AssertionFailedError;

import java.util.Objects;

/**
 * Compares long texts (snapshots, dumps) with a failure message that stays short: only the first differing line,
 * the profile section it's in, and the line counts. The full texts are still attached to the AssertionFailedError, so
 * the IDE's "show difference" view works as usual; they just aren't printed to the console.
 */
final class TextDiff {
	private TextDiff() {
	}

	private static final int MAX_LINE_LENGTH = 160;

	static void assertSameText(String expected, String actual, String what) {
		if (Objects.equals(expected, actual))
			return;
		if (expected == null || actual == null)
			throw new AssertionFailedError(what + ": expected " + (expected == null ? "no text" : "a text")
					+ " but got " + (actual == null ? "none" : "one"), expected, actual);
		throw new AssertionFailedError(what + ": " + describe(expected, actual), expected, actual);
	}

	/** "first difference at line N in profile P (expected X lines, got Y)" plus the two differing lines. */
	static String describe(String expected, String actual) {
		String[] e = expected.split("\n", -1);
		String[] a = actual.split("\n", -1);
		int i = 0;
		while (i < e.length && i < a.length && e[i].equals(a[i]))
			i++;

		// The lines before i are identical, so the enclosing profile section is the same in both
		String profile = null;
		for (int j = Math.min(i, e.length) - 1; j >= 0; j--) {
			if (e[j].startsWith("### profile ")) {
				profile = e[j].split(" ")[2];
				break;
			}
		}

		return "first difference at line " + (i + 1) + (profile == null ? "" : " in profile " + profile)
				+ " (expected " + e.length + " lines, got " + a.length + ")"
				+ "\n    expected: " + line(e, i)
				+ "\n    actual:   " + line(a, i);
	}

	private static String line(String[] lines, int index) {
		if (index >= lines.length)
			return "<end of text>";
		String s = lines[index].strip();
		return s.length() > MAX_LINE_LENGTH ? s.substring(0, MAX_LINE_LENGTH) + "..." : s;
	}
}