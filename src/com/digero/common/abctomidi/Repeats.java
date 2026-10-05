package com.digero.common.abctomidi;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.digero.common.abc.Dynamics;

/**
 * Where the parser is in a part's repeats (ABC 2.1, 4.8 and 4.9), with Params.expandRepeats. A :| goes back to the
 * |: before it; without one, to the part's start, or to the last double bar (|| |] [|) or :| before it. The endings
 * [1 [2 [1,3 [2-4 (also |1 and :|2) play on the passes they're numbered for; an ending runs to the next ending, :|,
 * ||, |] or [|. Without expandRepeats everything plays once, one after the other, as in Lotro.
 * <p>
 * The parser tells it the signs it reads; where the music goes back, end() gives the Jump, and the parser goes on
 * reading there (AbcToMidi.restoreAt).
 */
final class Repeats {
	/**
	 * Where to go on reading: a line (index) and column, with how the notes were read there (null without
	 * expandRepeats: as they are).
	 */
	record Jump(int line, int column, TuneInfo.ReadState state) {
	}

	private final boolean expand;
	private final TuneInfo info; // Read at every place a :| can go back to
	private int startLine = -1; // Where a :| goes back to (line index); -1 until the part's first line of music
	private int startColumn;
	private TuneInfo.ReadState startState; // How the notes were read at startLine/startColumn; null without expand
	private TuneInfo.ReadState skipState; // How the notes were read where the skipped ending starts: restored at its end
	private Dynamics skipDynamics;
	private int pass = 1; // 2 is the first time through the section again
	private boolean open; // After a |: whose :| hasn't been played often enough yet
	private Set<Integer> ending; // The numbers of the ending the parser is in, null outside an ending
	private boolean skipping; // The ending isn't played on this pass: its notes take no time
	private final Set<Long> jumped = new HashSet<>(); // The :| that went back, as its source position and pass

	Repeats(boolean expand, TuneInfo info) {
		this.expand = expand;
		this.info = info;
	}

	/** The ending the parser is in isn't played on this pass: its notes take no time, its fields aren't read. */
	boolean skipping() {
		return skipping;
	}

	/** The pass through the section: 1, and 2 the first time again. */
	int pass() {
		return pass;
	}

	void newPart() {
		startLine = -1;
		startState = null;
		open = false;
		pass = 1;
		ending = null;
		skipping = false;
		jumped.clear();
	}

	/** A line of music: the part's first one is where a :| without |: goes back to. */
	void musicLine(int lineIndex) {
		if (startLine < 0) {
			startLine = lineIndex;
			startColumn = 0;
			markState();
		}
	}

	/** |: at the column before this one. */
	void repeatStart(int lineIndex, int column) {
		start(lineIndex, column);
		open = true;
	}

	/** Where a :| goes back to: here, at the column. */
	private void start(int lineIndex, int column) {
		startLine = lineIndex;
		startColumn = column;
		pass = 1;
		ending = null;
		skip(false);
		markState();
	}

	/** The state a :| restores when it goes back to here. */
	private void markState() {
		if (expand)
			startState = info.readState();
	}

	/** || |] [| : ends an ending, and a :| without |: after it goes back to here. */
	void sectionEnd(int lineIndex, int column) {
		start(lineIndex, column);
		open = false;
	}

	/**
	 * A P: line (ABC 2.1, 3.1.9) at the start of this line: a section starts, as at ||. Not between |: and its :|,
	 * which goes back to the |: (X:10829 Ragtime Annie, a P: in the middle of a bar of a repeated section).
	 */
	void sectionLabel(int lineIndex) {
		if (!open)
			sectionEnd(lineIndex, 0);
	}

	/** [1 |1 :|2 ... : an ending starts. */
	void ending(String numbers) {
		ending = parseEndingNumbers(numbers);
		skip(expand && pass > 1 && !ending.contains(pass));
	}

	/**
	 * Starts or stops skipping an ending this pass doesn't play. What is written in it isn't read either (BUG1015):
	 * its K: M: L: I: and dynamics are undone at its end, so they don't reach the ending that is played.
	 */
	private void skip(boolean skip) {
		if (skip && !skipping) {
			skipState = info.readState();
			skipDynamics = info.getDynamics();
		} else if (!skip && skipping) {
			info.restore(skipState);
			info.setDynamics(skipDynamics.name());
		}
		skipping = skip;
	}

	/**
	 * :| at the column, the whole sign ending before column after.
	 *
	 * @return Where to go back to, to play the section again; null to go on
	 */
	Jump end(List<String> lines, int lineIndex, int column, int after) {
		if (skipping) {
			// The end of an ending this pass doesn't play: go on after it
			skip(false);
			ending = null;
			return null;
		}
		boolean again;
		if (!expand)
			again = false;
		else if (ending == null)
			again = (pass == 1);
		else // After an ending: again if another pass has an ending in this section
			again = ending.contains(pass + 1) || AbcToMidi.endingFollows(lines, startLine, startColumn, pass + 1);
		if (again && jumped.add(passKey(lineIndex, column, pass))) {
			pass++;
			ending = null;
			return new Jump(startLine, startColumn, startState);
		}
		open = false;
		if (ending != null) {
			// The end of the ending for this pass. The endings after it are for other passes, and are skipped.
			ending = null;
			return null;
		}
		// Played often enough: a later :| without |: goes back to here
		start(lineIndex, after);
		return null;
	}

	/** A :| at its place on one pass: each goes back once a pass. */
	private static long passKey(int lineIndex, int column, int pass) {
		return ((long) lineIndex << 40) | ((long) column << 8) | pass;
	}

	/** The numbers of an ending: 1, 1,3 or 1-3 (ABC 2.1 also allows e.g. 1,3,5-7). */
	static Set<Integer> parseEndingNumbers(String numbers) {
		Set<Integer> result = new HashSet<>();
		for (String range : numbers.split(",")) {
			String[] fromTo = range.split("-");
			int from = Integer.parseInt(fromTo[0]);
			int to = Integer.parseInt(fromTo[fromTo.length - 1]);
			for (int n = from; n <= to; n++)
				result.add(n);
		}
		return result;
	}
}