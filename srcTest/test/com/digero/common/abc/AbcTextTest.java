package com.digero.common.abc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** ABC 2.1 text strings (section 8.2) and the accents & ligatures table (14.1). */
class AbcTextTest {

	@Test
	void plainTextIsUnchanged() {
		String text = "Antífona - Nós";
		assertSame(text, AbcText.decode(text));
	}

	@Test
	void everyMnemonicOfTheStandardTable() {
		assertEquals("ÀàèòÁáéóÂâêôÃãñõÄäëö", AbcText.decode(
				"\\`A\\`a\\`e\\`o\\'A\\'a\\'e\\'o\\^A\\^a\\^e\\^o\\~A\\~a\\~n\\~o\\\"A\\\"a\\\"e\\\"o"));
		assertEquals("ÇçÅåØøĂăĔĕŠšŽžŐőŰű", AbcText.decode(
				"\\cC\\cc\\AA\\aa\\/O\\/o\\uA\\ua\\uE\\ue\\vS\\vs\\vZ\\vz\\HO\\Ho\\HU\\Hu"));
		assertEquals("ßÆæŒœ", AbcText.decode("\\ss\\AE\\ae\\OE\\oe"));
		// The same accents on other letters
		assertEquals("ýčÿ", AbcText.decode("\\'y\\vc\\\"y"));
	}

	@Test
	void unicodeEscapes() {
		assertEquals("é€", AbcText.decode("\\u00e9\\u20AC"));
		assertEquals("𝄞", AbcText.decode("\\U0001d11e")); // 𝄞, beyond 16 bits
		// Backslash u followed by 4 hex digits is unicode, else it's the breve mnemonic
		assertEquals("ăb", AbcText.decode("\\uab"));
	}

	@Test
	void htmlEntities() {
		assertEquals("é & ó < ' – €", AbcText.decode("&eacute; &amp; &oacute; &lt; &apos; &ndash; &euro;"));
		assertEquals("éé", AbcText.decode("&#233;&#xE9;"));
	}

	@Test
	void escapedCharacters() {
		assertEquals("\\ 100% &eacute;", AbcText.decode("\\\\ 100\\% \\&eacute;"));
	}

	@Test
	void unknownEscapesStayAsWritten() {
		assertEquals("\\q \\'1 \\'q \\u12 &nosuch; & a;b &# \\", AbcText.decode("\\q \\'1 \\'q \\u12 &nosuch; & a;b &# \\"));
	}
}