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
 * D.C. and D.S. (user's rules, 2026-10-05; JumpMarks): a D.C. goes back to the part's start, a D.S. to the segno
 * (only if there is one), each once, at the end of the section it is in (|| |] [| or a :| that doesn't go back). On
 * the pass after it a repeated section plays once, with its last ending; Fine ends the part at the next bar line,
 * and To Coda (or the first coda mark, before the D.C. or D.S.) goes to the coda mark after the D.C. or D.S. at the
 * next bar line. Before the jump, Fine and To Coda are passed over. An al Fine plays no coda, an al Coda no Fine.
 * <p>
 * The parser tells it the signs and marks it reads; where the music goes on elsewhere, end() and bar() give the Jump,
 * and the parser goes on reading there (AbcToMidi.restoreAt).
 */
final class Repeats {
	/**
	 * However the marks are written, a part jumps (a :| back, a D.C., D.S. or To Coda) at most this often: more is an
	 * error (AbcToMidi), not a parser going round in circles.
	 */
	static final int MAX_JUMPS = 1000;

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
	// D.C., D.S., segno, coda, Fine (JumpMarks)
	private Jump partStart; // The part's first line of music, as it was read there
	private Jump segno; // Where the segno is, as the notes were read there; null before one
	private JumpMarks.Mark armed; // A D.C. or D.S. read, which jumps at the end of its section; null for none
	private int armedLine;
	private int armedColumn;
	private boolean sectionEnded; // The bar line read last ends a section (sectionEnd, or end() without going back)
	private final Set<Long> jumpedBack = new HashSet<>(); // The D.C. and D.S. that jumped, as their source positions
	private JumpMarks.Mark jumpedWith; // The D.C. or D.S. that jumped last; null before a jump
	private int fromLine; // Where it is
	private int fromColumn;
	private boolean fineArmed; // Fine read after the jump: the part ends at the next bar line
	private boolean codaArmed; // To Coda read after the jump: on to the coda at the next bar line
	private boolean codaTaken; // The coda was gone to: a To Coda in it doesn't go there again
	private int jumps; // The jumps in this part so far (MAX_JUMPS)
	private boolean finished; // After Fine: the rest of the part isn't played

	Repeats(boolean expand, TuneInfo info) {
		this.expand = expand;
		this.info = info;
	}

	/** The ending the parser is in isn't played on this pass: its notes take no time, its fields aren't read. */
	boolean skipping() {
		return skipping || finished;
	}

	/** The pass through the section: 1, and 2 the first time again; from 101 after a D.C. or D.S. (the lyrics). */
	int pass() {
		return (jumpedWith == null) ? pass : 100 + pass;
	}

	void newPart() {
		startLine = -1;
		startState = null;
		open = false;
		pass = 1;
		ending = null;
		skipping = false;
		jumped.clear();
		partStart = null;
		segno = null;
		armed = null;
		sectionEnded = false;
		jumpedBack.clear();
		jumpedWith = null;
		fineArmed = false;
		codaArmed = false;
		codaTaken = false;
		finished = false;
		jumps = 0;
	}

	/** The jumps in this part so far: a :| back, a D.C., D.S. or To Coda (MAX_JUMPS). */
	int jumps() {
		return jumps;
	}
	
	/** A line of music: the part's first one is where a :| without |: goes back to. */
	void musicLine(int lineIndex) {
		if (startLine < 0) {
			startLine = lineIndex;
			startColumn = 0;
			markState();
			partStart = new Jump(lineIndex, 0, startState);
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
		sectionEnded = true;
	}
	
	/**
	 * A P: line (ABC 2.1, 3.1.9) at the start of this line: a section starts, as at ||. Not between |: and its :|,
	 * which goes back to the |: (X:10829 Ragtime Annie, a P: in the middle of a bar of a repeated section).
	 */
	void sectionLabel(int lineIndex) {
		if (!open)
			sectionEnd(lineIndex, 0);
	}

	/**
	 * [1 |1 :|2 ... : an ending starts; its numbers end before the column. After a D.C. or D.S. only the section's last
	 * ending is played.
	 */
	void ending(String numbers, List<String> lines, int lineIndex, int column) {
		ending = parseEndingNumbers(numbers);
		if (jumpedWith != null)
			skip(AbcToMidi.endingFollows(lines, lineIndex, column, followingNumbers -> true));
		else
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
		if (!expand || jumpedWith != null)
			again = false; // After a D.C. or D.S. a section plays once
		else if (ending == null)
			again = (pass == 1);
		else // After an ending: again if another pass has an ending in this section
			again = ending.contains(pass + 1) || AbcToMidi.endingFollows(lines, startLine, startColumn, pass + 1);
		if (again && jumped.add(passKey(lineIndex, column, pass))) {
			pass++;
			ending = null;
			jumps++;
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
		sectionEnded = true;
		return null;
	}


	/**
	 * A mark of the form from the column to end in the line (JumpMarks; null for none): a segno is where a D.S. goes
	 * back to; a D.C. or D.S. jumps at its section's end; after the jump, Fine and To Coda at the next bar line. A Fine
	 * right after a bar line with nothing after it on its line (Norbeck's ":| !fine!") belongs to that bar line.
	 */
	void mark(JumpMarks.Mark mark, String line, int lineIndex, int column, int end) {
		if (!expand || mark == null || finished)
			return;
		switch (mark) {
			case SEGNO -> {
				if (segno == null)
					segno = new Jump(lineIndex, column, info.readState());
			}
			case FINE -> {
				if (jumpedWith != null && jumpedWith.stopsAtFine()) {
					if (JumpMarks.endsBarBefore(line, column, end))
						finished = true;
					else
						fineArmed = true;
				}
			}
			case TO_CODA -> {
				if (jumpedWith != null && jumpedWith.jumpsToCoda() && !codaTaken)
					codaArmed = true;
			}
			case CODA -> {
				// The first coda mark: To Coda, before the D.C. or D.S.; the one after it is where the coda starts
				boolean before = lineIndex < fromLine || (lineIndex == fromLine && column < fromColumn);
				if (jumpedWith != null && jumpedWith.jumpsToCoda() && before && !codaTaken)
					codaArmed = true;
			}
			default -> {
				// D.C. D.S.: a D.S. only with a segno before it (user), each only once. A second one before the same
				// section's end ("D.C." and !D.C.! on one bar) is the same jump written twice: it never jumps itself.
				long key = sourceKey(lineIndex, column);
				if (armed != null) {
					jumpedBack.add(key);
				} else if ((!mark.toSegno() || segno != null) && !jumpedBack.contains(key)) {
					armed = mark;
					armedLine = lineIndex;
					armedColumn = column;
				}
			}
		}
	}

	/**
	 * After every bar line (also || |] [| :| |: and their endings), what the marks read before it ask for: Fine ends
	 * the part, To Coda goes to the coda, and at a section's end a D.C. or D.S. goes back.
	 *
	 * @return Where to go on reading; null to go on here
	 */
	Jump bar(List<String> lines) {
		boolean ended = sectionEnded;
		sectionEnded = false;
		if (fineArmed) {
			fineArmed = false;
			finished = true;
			return null;
		}
		if (codaArmed) {
			codaArmed = false;
			int[] coda = JumpMarks.nextCoda(lines, fromLine, fromColumn); // The D.C. or D.S. itself is no coda
			if (coda != null) {
				codaTaken = true;
				jumps++;
				start(coda[0], coda[1]);
				return new Jump(coda[0], coda[1], null);
			}
		}
		if (!ended || armed == null)
			return null;
		Jump back = armed.toSegno() ? segno : partStart;
		jumpedBack.add(sourceKey(armedLine, armedColumn));
		jumpedWith = armed;
		fromLine = armedLine;
		fromColumn = armedColumn;
		armed = null;
		start(back.line(), back.column());
		startState = back.state();
		jumps++;
		return back;
	}

	/**
	 * The part ends (its next X:, the empty line that ends its tune, or the file's end), as a section does: a D.C. or
	 * D.S. still waiting goes back from here (a mark after the last bar line, or a part without one at its end).
	 *
	 * @return Where to go on reading; null when the part ends here
	 */
	Jump partEnd(List<String> lines) {
		sectionEnded = true;
		return bar(lines);
	}

	/** A place in the part's lines. */
	private static long sourceKey(int lineIndex, int column) {
		return ((long) lineIndex << 32) | column;
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