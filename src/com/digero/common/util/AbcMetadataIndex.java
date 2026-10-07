package com.digero.common.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

/**
 * In-memory index of the searchable header fields of ABC files:
 * {@code %%song-title}, {@code %%song-composer}, {@code N: Genre:} and {@code N: Mood:}.
 * <p>
 * Only the file header (the lines before the first {@code X:} line) is read, on a
 * background thread. Entries are cached by last-modified time, so a rebuild after a
 * browser refresh only re-reads files that actually changed.
 * <p>
 * Threading: {@link #matches} may be called from any thread. {@link #rebuild} and
 * {@link #hasStarted} must be called on the EDT. The {@code onIndexed} callback runs on the EDT.
 */
public class AbcMetadataIndex {

	/** Safety cap for files without an X: line (e.g. a .txt that isn't ABC at all). */
	private static final int MAX_HEADER_LINES = 200;

	/** Joins fields so a search term can never match across two fields. */
	private static final char FIELD_SEPARATOR = '\n';

	private static final String SONG_TITLE = "%%song-title";
	private static final String SONG_COMPOSER = "%%song-composer";
	private static final String GENRE = "Genre:";
	private static final String MOOD = "Mood:";

	private record Entry(long lastModified, String searchText) {
	}

	private final Map<File, Entry> entries = new ConcurrentHashMap<>();
	private final Consumer<List<File>> onIndexed;
	private IndexWorker worker = null;

	/**
	 * @param onIndexed called on the EDT with batches of files that were (re)indexed and
	 *                  have at least one searchable field
	 */
	public AbcMetadataIndex(Consumer<List<File>> onIndexed) {
		this.onIndexed = onIndexed;
	}

	/** @return true once {@link #rebuild} has been called at least once. EDT only. */
	public boolean hasStarted() {
		return worker != null;
	}

	/** Cancels any running pass and starts indexing the given files. EDT only. */
	public void rebuild(List<File> files) {
		if (worker != null) {
			worker.cancel(false);
		}
		worker = new IndexWorker(files);
		worker.execute();
	}

	/**
	 * @param lowerCaseFilter search text, already lower-cased
	 * @return true if one of the indexed header fields of the file contains the filter text
	 */
	public boolean matches(File file, String lowerCaseFilter) {
		Entry entry = entries.get(file);
		return entry != null && entry.searchText().contains(lowerCaseFilter);
	}

	private class IndexWorker extends SwingWorker<Void, File> {
		private final List<File> files;

		IndexWorker(List<File> files) {
			this.files = files;
		}

		@Override
		protected Void doInBackground() {
			Set<File> present = new HashSet<>(files.size() * 2);
			for (File file : files) {
				if (isCancelled()) {
					return null;
				}
				if (Util.stringEndsWithIgnoreCase(file.getName(), Util.ABCP_FILE_EXTENSION)) {
					continue; // playlists have no song header
				}

				// One stat call gives both "is it a file" and the modification time
				BasicFileAttributes attrs;
				try {
					attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
				} catch (IOException | InvalidPathException e) {
					continue;
				}
				if (!attrs.isRegularFile()) {
					continue; // empty folder in the tree
				}

				present.add(file);
				long lastModified = attrs.lastModifiedTime().toMillis();
				Entry cached = entries.get(file);
				if (cached != null && cached.lastModified() == lastModified) {
					continue; // unchanged since last pass
				}

				String searchText = readSearchText(file);
				entries.put(file, new Entry(lastModified, searchText));
				if (!searchText.isEmpty()) {
					publish(file);
				}
			}

			if (!isCancelled()) {
				entries.keySet().retainAll(present); // forget deleted/moved files
			}
			return null;
		}

		@Override
		protected void process(List<File> chunk) {
			if (!isCancelled()) {
				onIndexed.accept(chunk);
			}
		}
	}

	/** Reads the header fields of one file, lower-cased and joined by FIELD_SEPARATOR. */
	static String readSearchText(File file) {
		StringBuilder sb = new StringBuilder();
		// InputStreamReader replaces malformed bytes instead of throwing,
		// so a non-UTF-8 file won't abort the read.
		try (BufferedReader in = new BufferedReader(
				new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8), 4096)) {
			String line;
			int lineCount = 0;
			while ((line = in.readLine()) != null && lineCount++ < MAX_HEADER_LINES) {
				if (lineCount == 1 && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
					line = line.substring(1); // UTF-8 BOM
				}
				line = line.trim();
				if (startsWithIgnoreCase(line, "X:")) {
					break; // end of file header
				}
				String value = extractValue(line);
				if (value != null && !value.isEmpty()) {
					if (sb.length() > 0) {
						sb.append(FIELD_SEPARATOR);
					}
					// Same lower-casing as AbcFileTreeModel.filter() applies to the search text
					sb.append(value.toLowerCase());
				}
			}
		} catch (IOException | InvalidPathException e) {
			return "";
		}
		return sb.toString();
	}

	private static String extractValue(String line) {
		if (startsWithIgnoreCase(line, SONG_TITLE)) {
			return line.substring(SONG_TITLE.length()).trim();
		}
		if (startsWithIgnoreCase(line, SONG_COMPOSER)) {
			return line.substring(SONG_COMPOSER.length()).trim();
		}
		if (startsWithIgnoreCase(line, "N:")) {
			String note = line.substring(2).trim();
			if (startsWithIgnoreCase(note, GENRE)) {
				return note.substring(GENRE.length()).trim();
			}
			if (startsWithIgnoreCase(note, MOOD)) {
				return note.substring(MOOD.length()).trim();
			}
		}
		return null;
	}

	private static boolean startsWithIgnoreCase(String s, String prefix) {
		return s.regionMatches(true, 0, prefix, 0, prefix.length());
	}
}
