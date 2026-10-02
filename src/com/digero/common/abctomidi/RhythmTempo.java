package com.digero.common.abctomidi;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tempo a tune without Q: gets from its type in R: (ABC 2.1, 3.1.7: the rhythm, e.g. reel, jig, hornpipe), as a Q:
 * would give it: each with the beat the type is counted in, so it stays right in another meter (a waltz written in
 * 6/8, a slide in 6/8 instead of 12/8). ABC 2.1 gives no default tempo; without a known type, abc2midi's Q:1/4=120.
 * <p>
 * Session tempos, at the relaxed end, so a tune is easy to follow (TradTab's metronome guide: reel 100-124 half notes,
 * jig 108-124 and slip jig 113-124 dotted quarters, hornpipe 160-200 quarters, polka 121-140 quarters, slide 37-41
 * bars a minute; Irish dance: treble jig 72-96 dotted quarters, set dances 92-96 dotted quarters in jig time and
 * 130-144 quarters in hornpipe time). Strathspeys at Scottish country dance speed (about 32 bars a minute), airs from
 * the Norbeck collection's Q: fields (slow air 23, air 35 bars a minute), and so the Balkan dances in quick beats of an
 * eighth (rachenitsa, kopanitsa: 1/8=220; paidushko 2/8=180; sandansko 2/16=180), the Breton dances (an dro 1/4=100;
 * ridée and rond, 180 to 200 quarters), the three-time bourrée (190 eighths in 3/8) and the huayno (1/4=90).
 * <p>
 * A type has its names in English and in the languages tune books are written in (German, French, Italian, Spanish,
 * the Nordic languages), matched without accents: "muiñeira" and "muineira", "gånglåt" and "ganglat", also when
 * written with ABC's escapes (decoded first, AbcText).
 * <p>
 * The types danced in bars of 4/4 (reels, hornpipes, barn dances, strathspeys, highlands, set dances in hornpipe time)
 * are also written in 2/4, each 2/4 bar holding a 4/4 bar's notes, in sixteenths: O'Neill's hornpipes and some of his
 * reels (with L:1/16, or with L:1/8 and notes like A/B/). In a meter of half a whole note their beat is halved, so the
 * bars a minute stay (a reel's 1/2=100 is 1/4=100 in 2/4).
 * <p>
 * Left out, as their tempo would be the default's anyway (the Norbeck collection's Q: fields, 120 to 140 quarters):
 * hora, kolo, freylekhs, Breton gavotte and hanter dro. Not a type: a composer (carolan) or a heading (misc).
 */
public final class RhythmTempo {
	private RhythmTempo() {
	}

	/** Danced in bars of 4/4: written in 2/4, a 2/4 bar holds a 4/4 bar's notes. */
	private static final boolean COMMON_TIME = true;

	/** A tune type; MidiProgramGuess gives some of them a program. */
	enum TuneType {
		REEL("reels?", "1/2=100", COMMON_TIME), // 50 bars a minute
		JIG("(?:single |double |light )?jigs?|gigues?|gigas?", "3/8=108"), // 54 in 6/8
		TREBLE_JIG("treble jigs?", "3/8=76"), // 38 in 6/8
		SLIP_JIG("(?:slip|hop) ?jigs?", "3/8=113"), // 38 in 9/8
		SLIDE("slides?", "3/8=148"), // 37 in 12/8
		HORNPIPE("hornpipes?", "1/4=160", COMMON_TIME), // 40 in 4/4
		BARN_DANCE("barn ?dances?", "1/4=160", COMMON_TIME), // 40 in 4/4, like a hornpipe
		THREE_TWO("three-twos?|3/2 hornpipes?", "1/2=80"), // 27 in 3/2
		SET_DANCE("set ?dances?", "1/4=138", COMMON_TIME, "3/8=96", null), // 35 in 4/4; 48 in 6/8
		COUNTRY_DANCE("(?:english )?country ?dances?|contredanses?", "1/4=112", false, "3/8=108", null), // 56 in 2/4
		MUINEIRA("mui?neiras?", "3/8=108"), // 54 in 6/8, as a jig
		TARANTELLA("tarantell[ae]s?", "3/8=112"), // 56 in 6/8
		POLKA("polkk?as?|polcas?", "1/4=120"), // 60 in 2/4
		STRATHSPEY("strathspeys?|flings?", "1/4=128", COMMON_TIME), // 32 in 4/4
		SCHOTTISCHE("highlands?|schottisc?he?s?|schottis|skottis|scottish(?!\\s+\\p{L})|jenkkas?|chotis|xotis"
				+ "|(?:rh?ein|rei|rej)l(?:a|ae|e)nders?", "1/4=144", COMMON_TIME), // 36 in 4/4 or 2/2
		MAZURKA("ma[sz]urk{1,2}as?|mazureks?|mazur", "1/4=120"), // 40 in 3/4
		WALTZ("waltz(?:es)?|valses?|vals|valse musette|walzer|wals|valzer|valssi|walc|valcik|landler|dreher",
				"1/4=108"), // 36 in 3/4
		FIVE_TIME_WALTZ("valses? (?:a |en )?(?:5|cinq) temps", "1/8=180"), // 36 in 5/8
		MINUET("minuets?|menuett?s?|minuetto|minueto", "1/4=100"), // 33 in 3/4
		MARCH("marche?s?|marsch|marcia|marcha", "1/4=112"), // 56 in 2/4, 28 in 4/4
		POLSKA("\\w*polska|polskas|polon.s|hambo|halling|pols|springars?|springleik|s[o\u00f8]nderhoning",
				"1/4=112"), // 37 in 3/4
		GANGLAT("ganglat(?:ar)?|gangar", "1/4=100", false, "3/8=100", null), // walking tunes: 50 in 2/4, 50 in 6/8
		AN_DRO("an[- ]?dro", "1/4=100"), // 50 in 2/4
		RIDEE("ridees?|larides?|rond", "1/4=160"), // 27 in 6/4, 40 in 4/4
		BOURREE("bourrees?", "1/4=120", false, null, "1/8=180"), // two-time 60 in 2/4; three-time 60 in 3/8
		HUAYNO("huaynos?|waynus?|waynos?", "1/4=90"), // 45 in 2/4
		AIR("airs?", "1/4=100"), // 33 in 3/4
		SONG("songs?|ballads?|lied(?:er)?|chansons?|cancion(?:es)?|canzon[ei]|vis[ae]|visor|sang(?:er)?|laulu",
				"1/4=100"), // 33 in 3/4
		SLOW_AIR("slow airs?|laments?|lullab(?:y|ies)|berceuses?|wiegenlied(?:er)?", "1/4=70"), // 23 in 3/4
		HYMN("hymns?|psalms?|chorales?|carols?", "1/4=90"), // 22 in 4/4
		// Balkan: counted in eighths, the quick beat of a 2+2+3 bar
		BALKAN("r[au]che?nit[sz]as?|racenicas?|kopanit[sz]as?|gankino|cadaneasca|geampara|buchimish|da[yj]chovo"
				+ "|krivo|horos?", "1/8=220"), // 63 in 7/16
		PAIDUSHKO("pa[iy]dushko", "1/4=180"), // 72 in 5/8
		SANDANSKO("sandansko", "1/8=180"); // 16 in 22/16

		private final Pattern pattern;
		private final String tempo;
		private final boolean commonTime;
		/** In a compound meter (6/8, 9/8, 12/8), or null for the tempo */
		private final String compoundTempo;
		/** In a meter of three (3/8, 3/4), or null for the tempo */
		private final String tripleTempo;

		TuneType(String regex, String tempo) {
			this(regex, tempo, false, null, null);
		}

		TuneType(String regex, String tempo, boolean commonTime) {
			this(regex, tempo, commonTime, null, null);
		}

		TuneType(String regex, String tempo, boolean commonTime, String compoundTempo, String tripleTempo) {
			this.pattern = Pattern.compile("\\b(?:" + regex + ")\\b",
					Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
			this.tempo = tempo;
			this.commonTime = commonTime;
			this.compoundTempo = compoundTempo;
			this.tripleTempo = tripleTempo;
		}
	}

	/** The Q: value for the first tune type in an R: field's value (decoded, AbcText), or null. */
	public static String of(String rhythm) {
		TuneType type = typeOf(rhythm);
		return (type == null) ? null : type.tempo;
	}

	/**
	 * The Q: value for the first tune type in an R: field's value (decoded, AbcText), or null, in this meter: a type
	 * counted otherwise in a compound meter or a meter of three has that tempo (a set dance in 6/8, a bourrée in 3/8),
	 * and a type danced in bars of 4/4, written in a meter of half a whole note (2/4), has its beat halved (a reel's
	 * Q:1/2=100 is Q:1/4=100).
	 */
	public static String of(String rhythm, int meterNumerator, int meterDenominator) {
		TuneType type = typeOf(rhythm);
		if (type == null)
			return null;
		if (type.compoundTempo != null && meterNumerator > 3 && meterNumerator % 3 == 0)
			return type.compoundTempo;
		if (type.tripleTempo != null && meterNumerator == 3)
			return type.tripleTempo;
		if (!type.commonTime || 2 * meterNumerator != meterDenominator)
			return type.tempo;
		String[] tempo = type.tempo.split("=");
		String[] beat = tempo[0].split("/");
		return beat[0] + "/" + (2 * Integer.parseInt(beat[1])) + "=" + tempo[1];
	}

	/** The first tune type in a text (decoded, AbcText), or null. */
	static TuneType typeOf(String text) {
		return typeOf(text, EnumSet.allOf(TuneType.class));
	}

	/**
	 * The first of these tune types in a text (decoded, AbcText), or null. At the same place in the text the longer
	 * match wins ("slip jig" over "jig", "slow air" over "air").
	 */
	static TuneType typeOf(String text, Set<TuneType> among) {
		if (text == null)
			return null;
		String plain = withoutAccents(text);
		TuneType found = null;
		int start = Integer.MAX_VALUE;
		int length = 0;
		for (TuneType type : among) {
			Matcher m = type.pattern.matcher(plain);
			if (m.find() && (m.start() < start || (m.start() == start && m.end() - m.start() > length))) {
				found = type;
				start = m.start();
				length = m.end() - m.start();
			}
		}
		return found;
	}

	/** "Muiñeira" → "Muineira", "Gånglåt" → "Ganglat" (ø stays: it has no accent to take off). */
	private static String withoutAccents(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}
}