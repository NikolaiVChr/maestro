package com.digero.common.abc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.digero.common.util.LotroFileParseException;

/**
 * LotRO's 60 ms minimum, pinned to what LotRO actually did in game. Every observation is a note that lasts exactly
 * 60 ms mathematically; whether LotRO played or refused it depends on the rounding of its own calculation
 * ({@link AbcConstants#lotroNoteSeconds}).
 * <p>
 * If one of these fails, the calculation no longer matches LotRO. Don't adjust the expectations; they're
 * measurements.
 */
class LotroMinimumLengthTest {

	/**
	 * @param refusedByLotro what LotRO did in game
	 */
	record Observation(String source, int meterNum, int meterDen, long lNum, long lDen, int q, long n, long d,
			boolean refusedByLotro) {
		@Override
		public String toString() {
			return source + ": M:" + meterNum + "/" + meterDen + " L:" + lNum + "/" + lDen + " Q:" + q + " E" + n + "/"
					+ d + (refusedByLotro ? " refused" : " played");
		}
	}

	/** Make60msTester format: M:4/4, L:1/1, note E{25000*Q}/100000000 */
	private static Observation tester(String source, int q, boolean refused) {
		return new Observation(source, 4, 4, 1, 1, q, 25000L * q, 100_000_000L, refused);
	}

	static Stream<Observation> observations() {
		List<Observation> o = new ArrayList<>();

		// Original Make60msTester run, tempos 1-140: refused exactly at the listed tempos
		Set<Integer> refused = Set.of(9, 11, 13, 15, 18, 22, 26, 30, 36, 37, 43, 44, 45, 51, 52, 60, 72, 74, 86, 88, 90,
				102, 104, 120);
		for (int q = 1; q <= 140; q++)
			o.add(tester("tester", q, refused.contains(q)));

		// Prediction test (new tempos above 140, and controls next to them)
		for (int q : new int[] { 141, 172, 173, 282, 283, 285, 291, 293, 299, 307, 346, 349, 355 })
			o.add(tester("prediction", q, true));
		for (int q : new int[] { 142, 174, 284, 300, 356 })
			o.add(tester("prediction", q, false));

		// L: and meter test (the controls, 5 times longer, all played and aren't repeated here)
		o.add(new Observation("L-test 1", 2, 2, 1, 1, 22, 22, 2000, true));
		o.add(new Observation("L-test 2", 4, 4, 1, 3, 22, 66, 4000, true));
		o.add(new Observation("L-test 3", 4, 4, 3, 8, 22, 176, 12000, true));
		o.add(new Observation("L-test 4", 4, 4, 3, 8, 141, 1128, 12000, false));
		o.add(new Observation("L-test 5", 4, 4, 1, 3, 25, 75, 4000, true));
		o.add(new Observation("L-test 6", 4, 4, 1, 3, 26, 78, 4000, true));
		return o.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("observations")
	void calculationMatchesLotro(Observation o) {
		double seconds = AbcConstants.lotroNoteSeconds(o.n(), o.d(), o.lNum(), o.lDen(), o.q(), o.meterDen());
		assertEquals(o.refusedByLotro(), seconds < AbcConstants.SHORTEST_NOTE_SECONDS, "LotRO's length: " + seconds);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("observations")
	void strictConversionMatchesLotro(Observation o) throws Exception {
		AbcCase abc = AbcCase.of("lotro_minimum", "X:1", "T:Test", "M:" + o.meterNum() + "/" + o.meterDen(),
				"L:" + o.lNum() + "/" + o.lDen(), "Q:" + o.q(), "K:C", "E" + o.n() + "/" + o.d() + " |]");
		if (o.refusedByLotro()) {
			LotroFileParseException e = assertThrows(LotroFileParseException.class,
					() -> ConversionDump.convert(abc, Profile.ABC_PLAYER_STRICT));
			assertTrue(e.getMessage().contains("too short"), e.getMessage());
		} else {
			ConversionDump.convert(abc, Profile.ABC_PLAYER_STRICT);
		}
	}
}