package com.digero.common.abc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden-master test: each case in {@link AbcCases} is converted with each of its {@link Profile}s and the complete
 * observable output is compared with the recorded snapshot in {@code srcTest/resources/com/digero/abc/golden/cases}.
 */
class AbcToMidiSnapshotTest {

	static Stream<AbcCase> cases() {
		return AbcCases.all().stream();
	}

	@BeforeEach
	void setUp() {
		TestSetup.beforeEachTest();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("cases")
	void matchesSnapshot(AbcCase abcCase) {
		SnapshotStore.assertMatches("cases/" + abcCase.name(), ConversionDump.snapshot(abcCase));
	}

	@Test
	void caseNamesAreUnique() {
		List<String> names = AbcCases.all().stream().map(AbcCase::name).toList();
		Set<String> seen = new HashSet<>();
		List<String> duplicates = names.stream().filter(n -> !seen.add(n)).toList();
		assertTrue(duplicates.isEmpty(), "Duplicate case names: " + duplicates);
	}

	/** A renamed or removed case would otherwise leave a snapshot behind that nothing checks. */
	@Test
	void noOrphanSnapshots() throws IOException {
		Path dir = SnapshotStore.SNAPSHOT_DIR.resolve("cases");
		if (!Files.isDirectory(dir))
			return;

		Set<String> names = AbcCases.all().stream().map(AbcCase::name).collect(Collectors.toSet());
		List<Path> orphans;
		try (Stream<Path> files = Files.list(dir)) {
			orphans = files.filter(f -> f.getFileName().toString().endsWith(".golden.txt"))
					.filter(f -> !names.contains(f.getFileName().toString().replaceFirst("\\.golden.txt$", "")))
					.toList();
		}

		if (SnapshotStore.UPDATE) {
			for (Path orphan : orphans) {
				try {
					Files.delete(orphan);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}
			return;
		}
		assertEquals(List.of(), orphans, "Snapshots without a case (delete them, or run with -Dabc.golden.update=true)");
	}
}