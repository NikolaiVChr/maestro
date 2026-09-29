package com.digero.common.abctomidi;

import java.util.Locale;

/**
 * The I: instructions of ABC 2.1 that change how the notes are read: I:linebreak (6.1.1), I:decoration (4.14) and
 * I:propagate-accidentals (11.3). Each may also be written as a stylesheet directive, %%linebreak ... (3.1.17: I: and %%
 * are interchangeable, but only I: can be inline).
 * <p>
 * A tune starts from the file header's (the fields before the first X:, see TuneInfo.newPart); an I: in its header or
 * body, or an inline [I:...], changes them from there on, for that tune only. The last instruction wins. Other
 * instructions (I:MIDI, I:abc-charset, %%writeout-accidentals, which only changes how a score is printed ...) are not
 * kept here.
 * <ul>
 * <li>I:linebreak: the symbols that break a score line: &lt;EOL&gt; (the end of a line of code), $ and !, or
 * &lt;none&gt;. The default is &lt;EOL&gt; $. A score line break is layout: it changes nothing that is played, but ! as
 * a line break isn't the start of a decoration. I:linebreak ! is deprecated, for ABC 2.0 files.</li>
 * <li>I:decoration: the decoration delimiter, ! (!trill!, the default) or + (+trill+, ABC 2.0). I:linebreak ! sets +,
 * since ! can't be both; a later I:decoration ! sets ! again (e.g. in one tune of a file whose header has +). The ABC
 * 2.0 form +trill+ is read either way (Lotro's +f+ volumes are written so).</li>
 * <li>I:propagate-accidentals: how far a written accidental reaches, to the end of the bar: not (only its own note),
 * octave (the same note in the same octave) or pitch (the same note in every octave, the default). VOLATILE in ABC 2.1.
 * Followed only with Params.standard2011: Lotro's reading is octave, whatever the file says (TuneInfo).</li>
 * </ul>
 */
public final class AbcInstructions {
	/** How far a written accidental reaches, to the end of the bar (I:propagate-accidentals). */
    public enum AccidentalScope {
		/** Only the note it's written on */
		NOT,
		/** The same note in the same octave */
		OCTAVE,
		/** The same note in every octave */
		PITCH
	}

	private boolean lineBreakAtEol = true;
	private boolean lineBreakAtDollar = true;
	private boolean lineBreakAtBang = false;
	private char decorationDelimiter = '!';
	private AccidentalScope propagateAccidentals = AccidentalScope.PITCH;

	/** A copy, for a tune that starts from the file header's instructions. */
    public AbcInstructions copy() {
		AbcInstructions copy = new AbcInstructions();
		copy.lineBreakAtEol = lineBreakAtEol;
		copy.lineBreakAtDollar = lineBreakAtDollar;
		copy.lineBreakAtBang = lineBreakAtBang;
		copy.decorationDelimiter = decorationDelimiter;
		copy.propagateAccidentals = propagateAccidentals;
		return copy;
	}

	/**
	 * Applies an I: field's value or a %% directive (without the %%), e.g. "linebreak $" or "decoration +".
	 *
	 * @return Whether it's an instruction kept here (linebreak, decoration, propagate-accidentals); false for any other
	 */
    public boolean apply(String instruction) {
		String[] words = instruction.trim().split("\\s+");
		switch (words[0].toLowerCase(Locale.ROOT)) {
			case "linebreak" -> {
				// The symbols replace the ones before; unknown symbols are skipped
				lineBreakAtEol = false;
				lineBreakAtDollar = false;
				lineBreakAtBang = false;
				for (int i = 1; i < words.length; i++) {
					switch (words[i].toLowerCase(Locale.ROOT)) {
						case "<eol>" -> lineBreakAtEol = true;
						case "$" -> lineBreakAtDollar = true;
						case "!" -> lineBreakAtBang = true;
						default -> {
							// <none>, or a symbol ABC 2.1 doesn't have
						}
					}
				}
				if (lineBreakAtBang)
					decorationDelimiter = '+';
				return true;
			}
			case "decoration" -> {
				if (words.length > 1 && (words[1].equals("!") || words[1].equals("+")))
					decorationDelimiter = words[1].charAt(0);
				return true;
			}
			case "propagate-accidentals" -> {
				// Another value, or none, changes nothing
				if (words.length > 1) {
					switch (words[1].toLowerCase(Locale.ROOT)) {
						case "not" -> propagateAccidentals = AccidentalScope.NOT;
						case "octave" -> propagateAccidentals = AccidentalScope.OCTAVE;
						case "pitch" -> propagateAccidentals = AccidentalScope.PITCH;
						default -> {
						}
					}
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** The end of a line of code breaks the score line (layout only). */
    public boolean isLineBreakAtEol() {
		return lineBreakAtEol;
	}

	/** $ breaks the score line (layout only). */
    public boolean isLineBreakAtDollar() {
		return lineBreakAtDollar;
	}

	/** ! breaks the score line (layout only; I:linebreak !, ABC 2.0 files). */
    public boolean isLineBreakAtBang() {
		return lineBreakAtBang;
	}

	/** The decoration delimiter: '!' (!trill!) or '+' (+trill+). */
    public char getDecorationDelimiter() {
		return decorationDelimiter;
	}

	/** How far a written accidental reaches, as the file says (default: pitch). */
    public AccidentalScope getPropagateAccidentals() {
		return propagateAccidentals;
	}
}