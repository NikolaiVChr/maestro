package com.digero.common.abc;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Text strings in ABC 2.1 (section 8.2): information fields, lyrics and annotations can write characters as escapes.
 * <ul>
 * <li>Mnemonics (section 14.1), e.g. backslash ' e for é. An accent before a letter: ` ' ^ ~ " c (cedilla), u (breve),
 * v (caron), H (double acute). Or one of /O /o AA aa ss AE ae OE oe.
 * <li>Named HTML entities, e.g. &amp;eacute; for é; also numeric ones, &amp;#233; and &amp;#xe9;
 * <li>Unicode: backslash u followed by 4 hex digits, or backslash U followed by 8, e.g. backslash u 00e9 for é
 * <li>A backslash before a backslash, % or &amp; for that character
 * </ul>
 * Anything else, e.g. a backslash before another character or an unknown entity, stays as written.
 */
public final class AbcText {

	/** The accent mnemonics before a letter, and the combining mark of each. */
	private static final String ACCENTS = "`'^~\"cuvH";
	private static final String COMBINING = "̧̀́̂̃̈̆̌̋";

	/** Mnemonics that aren't an accent on a letter. */
	private static final Map<String, String> SPECIAL_MNEMONICS = Map.of(
			"/O", "Ø", "/o", "ø", "AA", "Å", "aa", "å", "ss", "ß",
			"AE", "Æ", "ae", "æ", "OE", "Œ", "oe", "œ");

	/** The named entities of HTML 4.01, and &amp;apos;: name=code point. */
	private static final Map<String, Integer> ENTITIES = new HashMap<>();
	static {
		String table = ""
				+ "quot=34 amp=38 apos=39 lt=60 gt=62 nbsp=160 iexcl=161 cent=162 pound=163 curren=164 yen=165 "
				+ "brvbar=166 sect=167 uml=168 copy=169 ordf=170 laquo=171 not=172 shy=173 reg=174 macr=175 deg=176 "
				+ "plusmn=177 sup2=178 sup3=179 acute=180 micro=181 para=182 middot=183 cedil=184 sup1=185 ordm=186 "
				+ "raquo=187 frac14=188 frac12=189 frac34=190 iquest=191 Agrave=192 Aacute=193 Acirc=194 Atilde=195 "
				+ "Auml=196 Aring=197 AElig=198 Ccedil=199 Egrave=200 Eacute=201 Ecirc=202 Euml=203 Igrave=204 "
				+ "Iacute=205 Icirc=206 Iuml=207 ETH=208 Ntilde=209 Ograve=210 Oacute=211 Ocirc=212 Otilde=213 "
				+ "Ouml=214 times=215 Oslash=216 Ugrave=217 Uacute=218 Ucirc=219 Uuml=220 Yacute=221 THORN=222 "
				+ "szlig=223 agrave=224 aacute=225 acirc=226 atilde=227 auml=228 aring=229 aelig=230 ccedil=231 "
				+ "egrave=232 eacute=233 ecirc=234 euml=235 igrave=236 iacute=237 icirc=238 iuml=239 eth=240 "
				+ "ntilde=241 ograve=242 oacute=243 ocirc=244 otilde=245 ouml=246 divide=247 oslash=248 ugrave=249 "
				+ "uacute=250 ucirc=251 uuml=252 yacute=253 thorn=254 yuml=255 OElig=338 oelig=339 Scaron=352 "
				+ "scaron=353 Yuml=376 fnof=402 circ=710 tilde=732 Alpha=913 Beta=914 Gamma=915 Delta=916 Epsilon=917 "
				+ "Zeta=918 Eta=919 Theta=920 Iota=921 Kappa=922 Lambda=923 Mu=924 Nu=925 Xi=926 Omicron=927 Pi=928 "
				+ "Rho=929 Sigma=931 Tau=932 Upsilon=933 Phi=934 Chi=935 Psi=936 Omega=937 alpha=945 beta=946 "
				+ "gamma=947 delta=948 epsilon=949 zeta=950 eta=951 theta=952 iota=953 kappa=954 lambda=955 mu=956 "
				+ "nu=957 xi=958 omicron=959 pi=960 rho=961 sigmaf=962 sigma=963 tau=964 upsilon=965 phi=966 chi=967 "
				+ "psi=968 omega=969 thetasym=977 upsih=978 piv=982 ensp=8194 emsp=8195 thinsp=8201 zwnj=8204 zwj=8205 "
				+ "lrm=8206 rlm=8207 ndash=8211 mdash=8212 lsquo=8216 rsquo=8217 sbquo=8218 ldquo=8220 rdquo=8221 "
				+ "bdquo=8222 dagger=8224 Dagger=8225 bull=8226 hellip=8230 permil=8240 prime=8242 Prime=8243 "
				+ "lsaquo=8249 rsaquo=8250 oline=8254 frasl=8260 euro=8364 image=8465 weierp=8472 real=8476 trade=8482 "
				+ "alefsym=8501 larr=8592 uarr=8593 rarr=8594 darr=8595 harr=8596 crarr=8629 lArr=8656 uArr=8657 "
				+ "rArr=8658 dArr=8659 hArr=8660 forall=8704 part=8706 exist=8707 empty=8709 nabla=8711 isin=8712 "
				+ "notin=8713 ni=8715 prod=8719 sum=8721 minus=8722 lowast=8727 radic=8730 prop=8733 infin=8734 "
				+ "ang=8736 and=8743 or=8744 cap=8745 cup=8746 int=8747 there4=8756 sim=8764 cong=8773 asymp=8776 "
				+ "ne=8800 equiv=8801 le=8804 ge=8805 sub=8834 sup=8835 nsub=8836 sube=8838 supe=8839 oplus=8853 "
				+ "otimes=8855 perp=8869 sdot=8901 lceil=8968 rceil=8969 lfloor=8970 rfloor=8971 lang=9001 rang=9002 "
				+ "loz=9674 spades=9824 clubs=9827 hearts=9829 diams=9830 ";
		for (String entry : table.trim().split(" ")) {
			int eq = entry.indexOf('=');
			ENTITIES.put(entry.substring(0, eq), Integer.parseInt(entry.substring(eq + 1)));
		}
	}

	private AbcText() {
	}

	/** The text with every escape above replaced by its character. */
	public static String decode(String text) {
		if (text.indexOf('\\') < 0 && text.indexOf('&') < 0)
			return text;
		StringBuilder out = new StringBuilder(text.length());
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			String decoded = null;
			int end = i; // After the escape at i, if decoded
			if (c == '\\' && i + 1 < text.length()) {
				char next = text.charAt(i + 1);
				if (next == '\\' || next == '%' || next == '&') {
					decoded = String.valueOf(next);
					end = i + 2;
				} else if ((next == 'u' && isHex(text, i + 2, 4)) || (next == 'U' && isHex(text, i + 2, 8))) {
					end = i + 2 + (next == 'u' ? 4 : 8);
					int codePoint = (int) Long.parseLong(text.substring(i + 2, end), 16);
					if (Character.isValidCodePoint(codePoint))
						decoded = new String(Character.toChars(codePoint));
				} else if (i + 2 < text.length()) {
					decoded = mnemonic(next, text.charAt(i + 2));
					end = i + 3;
				}
			} else if (c == '&') {
				int semicolon = text.indexOf(';', i + 1);
				if (semicolon > i + 1) {
					decoded = entity(text.substring(i + 1, semicolon));
					end = semicolon + 1;
				}
			}
			if (decoded != null) {
				// {\aa}: TeX's braces around a mnemonic (not ABC 2.1, but common in older files, e.g. Norbeck's)
				// go with it
				if (out.length() > 0 && out.charAt(out.length() - 1) == '{' && end < text.length()
						&& text.charAt(end) == '}' && c == '\\') {
					out.setLength(out.length() - 1);
					end++;
				}
				out.append(decoded);
				i = end;
			} else {
				out.append(c);
				i++;
			}
		}
		return out.toString();
	}

	/** The character of the mnemonic backslash a b, or null. */
	private static String mnemonic(char a, char b) {
		String special = SPECIAL_MNEMONICS.get("" + a + b);
		if (special != null)
			return special;
		int accent = ACCENTS.indexOf(a);
		if (accent < 0 || !Character.isLetter(b))
			return null;
		// The letter and the accent's combining mark, composed into one character if Unicode has one (q with an acute
		// has none)
		String composed = Normalizer.normalize("" + b + COMBINING.charAt(accent), Normalizer.Form.NFC);
		return composed.length() == 1 ? composed : null;
	}

	/** The character of the entity &amp;name;, or null. */
	private static String entity(String name) {
		Integer codePoint = ENTITIES.get(name);
		if (codePoint == null && name.length() > 1 && name.charAt(0) == '#') {
			boolean hex = name.charAt(1) == 'x' || name.charAt(1) == 'X';
			try {
				codePoint = Integer.parseInt(name.substring(hex ? 2 : 1), hex ? 16 : 10);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		if (codePoint == null || !Character.isValidCodePoint(codePoint))
			return null;
		return new String(Character.toChars(codePoint));
	}

	/** Whether the text has count hex digits from index start. */
	private static boolean isHex(String text, int start, int count) {
		if (start + count > text.length())
			return false;
		for (int k = start; k < start + count; k++) {
			if (Character.digit(text.charAt(k), 16) < 0)
				return false;
		}
		return true;
	}
}
