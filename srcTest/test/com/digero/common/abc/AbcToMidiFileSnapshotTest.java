package com.digero.common.abc;

import static org.junit.jupiter.api.Assumptions.abort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Golden-master test over real ABC files: every *.abc under {@code srcTest/resources/com/digero/abc/files} (recursively) is read
 * from disk with {@link com.digero.common.abctomidi.AbcToMidi#readLines} (via {@code Params(File)}), converted with every {@link Profile}, and
 * compared with {@code srcTest/resources/com/digero/abc/golden/files/<relative path>.golden.txt}.
 * <p>
 * Real exported files exercise combinations the hand-written cases don't: %% extended fields, tempo changes, long
 * multi-part songs, organic timings. Add files that have caused bugs.
 */
class AbcToMidiFileSnapshotTest {

	@BeforeEach
	void setUp() {
		TestSetup.beforeEachTest();
	}

	@TestFactory
	Stream<DynamicTest> realFilesMatchSnapshots() throws IOException {
		Path dir = SnapshotStore.TEST_DATA_DIR.resolve("files");
		List<Path> files = List.of();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> walk = Files.walk(dir)) {
				files = walk.filter(Files::isRegularFile)
						.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".abc"))
						.sorted()
						.toList();
			}
		}

		if (files.isEmpty()) {
			return Stream.of(DynamicTest.dynamicTest("no ABC files in " + dir,
					() -> abort("Put .abc files in " + dir.toAbsolutePath() + " to enable this test")));
		}

		return files.stream().map(file -> {
			String relative = dir.relativize(file).toString().replace('\\', '/').replaceFirst("(?i)\\.abc$", "");
			return DynamicTest.dynamicTest(relative,
					() -> {
						TestSetup.beforeEachTest();
						SnapshotStore.assertMatches("files/" + relative, ConversionDump.snapshot(file.toFile()));
					});
		});
	}
}