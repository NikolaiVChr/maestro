package com.digero.common.abctomidi;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The tempo a tune without Q: gets from its type in R: (ABC 2.1, 3.1.7: the rhythm, e.g. reel, jig, hornpipe), as a Q:
 * would give it: each with the beat the type is counted in, so it stays right in another meter (a waltz written in
 * 6/8, a slide in 6/8 instead of 12/8). ABC 2.1 gives no default tempo; without a known type, abc2midi's Q:1/4=120.
 * <p>
 * Session tempos, at the relaxed end, so a tune is easy to follow (TradTab's metronome guide: reel 100-124 half notes,
 * jig 108-124 and slip jig 113-124 dotted quarters, hornpipe 160-200 quarters, polka 121-140 quarters, slide 37-41
 * bars a minute; Irish dance: treble jig 72-96 dotted quarters). Strathspeys at Scottish country dance speed (about 32
 * bars a minute), airs from the Norbeck collection's Q: fields (slow air 23, air 35 bars a minute), and so the Balkan
 * dances in quick beats of an eighth (rachenitsa, kopanitsa: 1/8=220; paidushko 2/8=180; sandansko 2/16=180).
 */
public final class RhythmTempo {
	private RhythmTempo() {
	}

	private record Type(Pattern pattern, String tempo) {
	}

	private static Type type(String regex, String tempo) {
		return new Type(Pattern.compile("(?i)\\b(?:" + regex + ")\\b"), tempo);
	}

	/** At the same place in the text the longer match wins ("slip jig" over "jig", "slow air" over "air"). */
	private static final List<Type> TYPES = List.of( //
			type("reels?", "1/2=100"), // 50 bars a minute
			type("(?:single |double |light )?jigs?", "3/8=108"), // 54 in 6/8
			type("treble jigs?", "3/8=76"), // 38 in 6/8
			type("(?:slip |hop )jigs?", "3/8=113"), // 38 in 9/8
			type("slides?", "3/8=148"), // 37 in 12/8
			type("hornpipes?", "1/4=160"), // 40 in 4/4
			type("barn ?dances?", "1/4=160"), // 40 in 4/4, like a hornpipe
			type("three-twos?|3/2 hornpipes?", "1/2=80"), // 27 in 3/2
			type("polkas?", "1/4=120"), // 60 in 2/4
			type("strathspeys?|flings?", "1/4=128"), // 32 in 4/4
			type("highlands?|schottisc?he?s?|schottis|reinlenders?", "1/4=144"), // 36 in 4/4 or 2/2
			type("mazurkas?", "1/4=120"), // 40 in 3/4
			type("waltz(?:es)?|valses?|vals|valse musette", "1/4=108"), // 36 in 3/4
			type("marche?s?|marsch", "1/4=112"), // 56 in 2/4, 28 in 4/4
			type("\\w*polska|polskas|polon.s|hambo|halling", "1/4=112"), // 37 in 3/4
			type("airs?|songs?", "1/4=100"), // 33 in 3/4
			type("slow airs?|laments?", "1/4=70"), // 23 in 3/4
			// Balkan: counted in eighths, the quick beat of a 2+2+3 bar
			type("r[au]che?nit[sz]as?|racenicas?|kopanit[sz]as?|gankino|cadaneasca|geampara|buchimish|da[yj]chovo"
					+ "|krivo|horos?", "1/8=220"), // 63 in 7/16
			type("pa[iy]dushko", "1/4=180"), // 72 in 5/8
			type("sandansko", "1/8=180")); // 16 in 22/16

	/** The Q: value for the first tune type in an R: field's value (decoded, AbcText), or null. */
	public static String of(String rhythm) {
		if (rhythm == null)
			return null;
		String tempo = null;
		int start = Integer.MAX_VALUE;
		int length = 0;
		for (Type type : TYPES) {
			Matcher m = type.pattern.matcher(rhythm);
			if (m.find() && (m.start() < start || (m.start() == start && m.end() - m.start() > length))) {
				tempo = type.tempo;
				start = m.start();
				length = m.end() - m.start();
			}
		}
		return tempo;
	}
}