package com.digero.common.abc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digero.common.abctomidi.AbcInstructions;
import com.digero.common.abctomidi.TuneInfo;
import org.junit.jupiter.api.Test;

/**
 * I:linebreak and I:decoration (ABC 2.1, 6.1.1 and 4.14), kept in AbcInstructions, and their scope in TuneInfo: every
 * tune starts from the file header's. In this package, as both classes are package-private.
 */
class AbcInstructionsTest {

	@Test
	void defaults() {
		// ABC 2.1: I:linebreak <EOL> $, and !trill! decorations
		AbcInstructions instructions = new AbcInstructions();
		assertTrue(instructions.isLineBreakAtEol());
		assertTrue(instructions.isLineBreakAtDollar());
		assertFalse(instructions.isLineBreakAtBang());
		assertEquals('!', instructions.getDecorationDelimiter());
	}

	@Test
	void lineBreakSymbolsReplaceTheOnesBefore() {
		AbcInstructions instructions = new AbcInstructions();
		assertTrue(instructions.apply("linebreak $"));
		assertFalse(instructions.isLineBreakAtEol());
		assertTrue(instructions.isLineBreakAtDollar());
		assertTrue(instructions.apply("linebreak <EOL>"));
		assertTrue(instructions.isLineBreakAtEol());
		assertFalse(instructions.isLineBreakAtDollar());
		// <none>: no symbol breaks a line; unknown symbols are skipped; the words in any case
		assertTrue(instructions.apply("LineBreak <none>"));
		assertFalse(instructions.isLineBreakAtEol());
		assertFalse(instructions.isLineBreakAtDollar());
		assertTrue(instructions.apply("linebreak   <eol>  %  $"));
		assertTrue(instructions.isLineBreakAtEol());
		assertTrue(instructions.isLineBreakAtDollar());
	}

	@Test
	void lineBreakBangSetsPlusDecorations() {
		// ! can't be both a line break and a decoration's delimiter: I:linebreak ! sets I:decoration +
		AbcInstructions instructions = new AbcInstructions();
		instructions.apply("linebreak !");
		assertTrue(instructions.isLineBreakAtBang());
		assertEquals('+', instructions.getDecorationDelimiter());
		// A later I:decoration ! sets ! again: the last instruction wins
		instructions.apply("decoration !");
		assertEquals('!', instructions.getDecorationDelimiter());
		// Without ! in the line breaks the delimiter stays as it is
		instructions.apply("decoration +");
		instructions.apply("linebreak $");
		assertEquals('+', instructions.getDecorationDelimiter());
	}

	@Test
	void decorationDelimiter() {
		AbcInstructions instructions = new AbcInstructions();
		assertTrue(instructions.apply("decoration +"));
		assertEquals('+', instructions.getDecorationDelimiter());
		assertTrue(instructions.apply("decoration !"));
		assertEquals('!', instructions.getDecorationDelimiter());
		// Another delimiter, or none, changes nothing (but it is an I:decoration)
		assertTrue(instructions.apply("decoration *"));
		assertTrue(instructions.apply("decoration"));
		assertEquals('!', instructions.getDecorationDelimiter());
	}

	@Test
	void otherInstructionsAreNotKept() {
		AbcInstructions instructions = new AbcInstructions();
		assertFalse(instructions.apply("MIDI program 73"));
		assertFalse(instructions.apply("abc-charset utf-8"));
		assertFalse(instructions.apply(""));
		assertTrue(instructions.isLineBreakAtDollar());
		assertEquals('!', instructions.getDecorationDelimiter());
	}

	@Test
	void copyIsIndependent() {
		AbcInstructions original = new AbcInstructions();
		original.apply("linebreak !");
		AbcInstructions copy = original.copy();
		assertTrue(copy.isLineBreakAtBang());
		assertEquals('+', copy.getDecorationDelimiter());
		copy.apply("decoration !");
		assertEquals('+', original.getDecorationDelimiter());
	}

	@Test
	void everyTuneStartsFromTheFileHeader() {
		// The file header's instructions (before the first X:) apply to every tune; a tune's own apply to it only
		TuneInfo info = new TuneInfo();
		info.newFile();
		info.applyInstruction("linebreak !");
		info.newPart(1);
		assertTrue(info.getInstructions().isLineBreakAtBang());
		assertEquals('+', info.getInstructions().getDecorationDelimiter());
		info.applyInstruction("decoration !"); // Tune 1 only
		assertEquals('!', info.getInstructions().getDecorationDelimiter());
		info.newPart(2);
		assertEquals('+', info.getInstructions().getDecorationDelimiter());
		// A new file starts from the defaults
		info.newFile();
		info.newPart(1);
		assertFalse(info.getInstructions().isLineBreakAtBang());
		assertEquals('!', info.getInstructions().getDecorationDelimiter());
		// A tune's I: doesn't reach the next tune when the file header has none
		info.applyInstruction("linebreak !");
		info.newPart(2);
		assertFalse(info.getInstructions().isLineBreakAtBang());
	}
}