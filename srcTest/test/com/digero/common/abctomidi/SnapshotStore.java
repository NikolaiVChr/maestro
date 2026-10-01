package com.digero.common.abctomidi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Golden-file storage for the snapshot tests.
 * <p>
 * Snapshots live in {@code srcTest/resources/com/digero/abctomidi/golden} (read from the source tree, not the classpath, so
 * a stale copy in target/ can never be compared against). Run with {@code -Dabc.golden.update=true} to (re)record
 * them (in PowerShell, quote the argument: {@code "-Dabc.golden.update=true"}); then review the diff in version
 * control before committing.
 * <p>
 * On a mismatch the actual output is also written to {@code target/abctomidi-actual/}, for diffing with a tool.
 */
final class SnapshotStore {
	private SnapshotStore() {
	}

	static final boolean UPDATE = Boolean.getBoolean("abc.golden.update");

	static final Path BASE_DIR = Path.of(System.getProperty("basedir", "."));
	static final Path TEST_DATA_DIR = BASE_DIR.resolve("srcTest/resources/com/digero/abctomidi");
	static final Path SNAPSHOT_DIR = TEST_DATA_DIR.resolve("golden");
	static final Path ACTUAL_DIR = BASE_DIR.resolve("target/abctomidi-actual");

	private static final String HEADER = "# AbcToMidi golden snapshot. Re-record with: mvn test -Dabc.golden.update=true"
			+ " (and review the diff before committing)\n";

	/**
	 * @param id Relative path of the snapshot without extension, e.g. "cases/notes_basic".
	 */
	static void assertMatches(String id, String actualBody) {
		String actual = HEADER + normalize(actualBody);
		Path file = SNAPSHOT_DIR.resolve(id + ".golden.txt");

		if (UPDATE) {
			write(file, actual);
			return;
		}

		if (!Files.exists(file)) {
			fail("No snapshot " + file.toAbsolutePath() + ". Record it with: mvn test -Dabc.golden.update=true");
		}

		String expected = normalize(read(file));
		if (!expected.equals(actual)) {
			Path actualFile = ACTUAL_DIR.resolve(id + ".golden.txt");
			write(actualFile, actual);
			// assertEquals with strings gives the IDE's side-by-side diff view
			TextDiff.assertSameText(expected, actual,
					"Output differs from snapshot " + file + " (actual output written to " + actualFile + ")");
		}
	}

	/** Line endings are normalized so a checkout with autocrlf doesn't fail every snapshot. */
	private static String normalize(String s) {
		String n = s.replace("\r\n", "\n");
		return n.endsWith("\n") ? n : n + "\n";
	}

	private static String read(Path file) {
		try {
			return Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static void write(Path file, String content) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, content, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}