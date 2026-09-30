package com.digero.common.abc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.digero.common.abctomidi.PartOrder;

/** PartOrder: a tune's sections in its header's P: order (ABC 2.1, 3.1.9), as text. */
class PartOrderTest {

	private static final List<String> HEAD = List.of("X:1", "T:t", "M:4/4", "L:1/8");

	private static List<String> tune(String order, String... body) {
		List<String> lines = new java.util.ArrayList<>(HEAD);
		if (order != null)
			lines.add(order);
		lines.add("K:C");
		lines.addAll(List.of(body));
		return lines;
	}

	@Test
	void sectionsPlayInTheHeadersOrder() {
		PartOrder.Result r = PartOrder.apply(tune("P:ABA", "P:A", "c d|", "P:B", "e f|"));
		assertEquals(List.of("X:1", "T:t", "M:4/4", "L:1/8", "P:ABA", "K:C", //
				"P:A", "c d|", "P:B", "e f|", "P:A", "c d|"), r.lines());
		// Each line from its source line, for messages and note regions
		assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 7, 8 }, r.sourceLineNumbers());
	}

	@Test
	void countsGroupsAndDots() {
		assertEquals(List.of('A', 'A', 'A', 'B'), orderOf("A3B"));
		assertEquals(List.of('A', 'B', 'A', 'B', 'A', 'B'), orderOf("(AB)3"));
		assertEquals(List.of('A', 'B', 'A', 'B', 'C', 'C', 'A', 'B', 'A', 'B', 'C', 'C'), orderOf("((AB)2.C2)2"));
		assertEquals(List.of('A', 'B', 'A'), orderOf("A.B.A"));
		assertEquals(List.of('A', 'B'), orderOf(" A B % the order"));
	}

	@Test
	void noOrderLeavesTheTuneAsItIs() {
		// No header P:, a single section, a name (Norbeck's labels), a broken group; P: only in the body
		assertNull(PartOrder.apply(tune(null, "P:A", "c d|", "P:B", "e f|")));
		assertNull(PartOrder.apply(tune("P:A", "P:A", "c d|", "P:B", "e f|")));
		assertNull(PartOrder.apply(tune("P:Verse", "P:A", "c d|")));
		assertNull(PartOrder.apply(tune("P:INTRO", "P:A", "c d|", "P:B", "e f|")));
		assertNull(PartOrder.apply(tune("P:(AB", "P:A", "c d|", "P:B", "e f|")));
		assertNull(PartOrder.apply(tune("P:3A", "P:A", "c d|")));
		// An order, but the body has no sections
		assertNull(PartOrder.apply(tune("P:AB", "c d|", "e f|")));
	}

	@Test
	void anOrderOnTheFirstLineAfterTheKey() {
		// The Session's "Boil The Coffee Early": the site writes the header, the user's P:ABACA comes after K:
		PartOrder.Result r = PartOrder.apply(tune(null, "P:ABACA", "P:A", "a|", "P:B", "b|", "P:C", "c|"));
		assertEquals(List.of("X:1", "T:t", "M:4/4", "L:1/8", "K:C", "P:ABACA", //
				"P:A", "a|", "P:B", "b|", "P:A", "a|", "P:C", "c|", "P:A", "a|"), r.lines());
		// Also after a comment; not after notes, and not a one-letter label
		assertEquals(r.lines().size() + 1,
				PartOrder.apply(tune(null, "% sections", "P:ABACA", "P:A", "a|", "P:B", "b|", "P:C", "c|")).lines().size());
		assertNull(PartOrder.apply(tune(null, "g|", "P:ABACA", "P:A", "a|", "P:B", "b|")));
		assertNull(PartOrder.apply(tune(null, "P:A", "a|", "P:B", "b|")));
		// A header order wins
		assertEquals(List.of("P:B", "b|", "P:A", "a|"), PartOrder.apply(tune("P:BA", "P:AB", "P:A", "a|", "P:B", "b|"))
				.lines().subList(7, 11));
	}

	@Test
	void musicBeforeTheSectionsFirstMissingSectionsSkipped() {
		PartOrder.Result r = PartOrder.apply(tune("P:ACB", "g a|", "P:A", "c d|", "P:B", "e f|", "P:D", "B c|"));
		assertEquals(List.of("X:1", "T:t", "M:4/4", "L:1/8", "P:ACB", "K:C", //
				"g a|", "P:A", "c d|", "P:B", "e f|"), r.lines());
	}

	@Test
	void eachTuneOfAFileItsOwn() {
		List<String> file = new java.util.ArrayList<>(List.of("% a book", ""));
		file.addAll(tune("P:BA", "P:A", "c|", "P:B", "d|"));
		file.add("");
		file.addAll(tune(null, "P:A", "e|", "P:B", "f|"));
		PartOrder.Result r = PartOrder.apply(file);
		assertEquals(List.of("% a book", "", "X:1", "T:t", "M:4/4", "L:1/8", "P:BA", "K:C", "P:B", "d|", "P:A", "c|", "",
				"X:1", "T:t", "M:4/4", "L:1/8", "K:C", "P:A", "e|", "P:B", "f|"), r.lines());
	}

	private static List<Character> orderOf(String value) {
		PartOrder.Result r = PartOrder.apply(tune("P:" + value, "P:A", "a|", "P:B", "b|", "P:C", "c|"));
		return r.lines().subList(HEAD.size() + 2, r.lines().size()).stream().filter(l -> l.startsWith("P:"))
				.map(l -> l.charAt(2)).toList();
	}
}