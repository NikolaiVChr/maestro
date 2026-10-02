package com.digero.common.abctomidi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** AbcTunebook: a songbook's tunes, one tune as ABC of its own, and splitting a book into files. */
class AbcTunebookTest {

	/** A small book in Norbeck's style: free text, a file header, tunes, an escape in a title. */
	private static final List<String> BOOK = List.of( //
			"This file contains 3 polskas.", //
			"(c) Copyright Henrik Norbeck.", //
			"", //
			"M:3/4", //
			"L:1/16", //
			"", //
			"X:1", //
			"T:Nittonbundna", //
			"T:V\\\"avpolskan", //
			"K:Dm", //
			"A4 d4 f4|", //
			"", //
			"X:2", //
			"T:Polska fr{\\aa}n Sm{\\aa}land", //
			"K:G", //
			"G4 B4 d4|", //
			"", //
			"Notes: play it slowly.", //
			"", //
			"X:10", //
			"T:What? Why: <no> \"quotes\" | star*", //
			"K:C", //
			"c4 e4 g4|");

	@Test
	void listsTheTunes() {
		AbcTunebook book = new AbcTunebook(BOOK);
		assertEquals(List.of("1", "2", "10"), book.tunes().stream().map(AbcTunebook.Tune::number).toList());
		// The first title, its escapes decoded (also TeX's braces)
		assertEquals(List.of("Nittonbundna", "Polska från Småland", "What? Why: <no> \"quotes\" | star*"),
				book.tunes().stream().map(AbcTunebook.Tune::title).toList());
		// A file without X: has no tunes
		assertEquals(List.of(), new AbcTunebook(List.of("M:4/4", "K:C", "c d e f|")).tunes());
	}

	@Test
	void aTuneIsTheFileHeaderAndItsLines() {
		AbcTunebook book = new AbcTunebook(BOOK);
		// Free text in the file header as % comments; blank lines at its end go, one comes back before X:
		assertEquals(List.of("% This file contains 3 polskas.", "% (c) Copyright Henrik Norbeck.", "", "M:3/4", "L:1/16",
				"", "X:1", "T:Nittonbundna", "T:V\\\"avpolskan", "K:Dm", "A4 d4 f4|"), book.tuneLines(book.tunes().get(0)));
		// An empty line ends the tune (ABC 2.1, 2.2.1): the text after it is free text, kept as % comments
		List<String> second = book.tuneLines(book.tunes().get(1));
		assertEquals(List.of("X:2", "T:Polska fr{\\aa}n Sm{\\aa}land", "K:G", "G4 B4 d4|", "%",
				"% Notes: play it slowly."), second.subList(6, second.size()));
		// Windows line ends
		assertTrue(book.tuneText(book.tunes().get(0)).endsWith("A4 d4 f4|\r\n"));
		// A book without a file header: the tune alone
		AbcTunebook bare = new AbcTunebook(List.of("X:1", "T:a", "K:C", "c|"));
		assertEquals(List.of("X:1", "T:a", "K:C", "c|"), bare.tuneLines(bare.tunes().get(0)));
	}

	@Test
	void fileNames() {
		AbcTunebook book = new AbcTunebook(BOOK);
		// The X: number zero-padded (at least 3 digits), the title as StringCleaner makes it safe for a file name, and
		// what Windows doesn't allow (: * " < > | ?) as spaces
		assertEquals(List.of("001 Nittonbundna", "002 Polska fraan Smaaland", "010 What Why no quotes star"),
				book.tunes().stream().map(book::fileName).toList());
		// Wider numbers, no title, a title of nothing usable
		AbcTunebook other = new AbcTunebook(List.of("X:1234", "K:C", "c|", "X:7", "T:???", "K:C", "d|"));
		assertEquals(List.of("1234", "0007"), other.tunes().stream().map(other::fileName).toList());
	}

	@Test
	void extractingWritesVersionsOnlyForOtherContent() throws IOException {
		File folder = Files.createTempDirectory("songbook").toFile();
		try {
			AbcTunebook book = new AbcTunebook(BOOK);
			AbcTunebook.Tune first = book.tunes().get(0);
			File file = book.extract(first, folder);
			assertEquals("001 Nittonbundna.abc", file.getName());
			assertEquals(book.tuneText(first), Files.readString(file.toPath(), StandardCharsets.UTF_8));
			// The same content again: the same file, nothing new written
			assertEquals(file, book.extract(first, folder));
			assertEquals(1, folder.list().length);
			// Other content under the same name: _v002, then _v003; each found again later
			AbcTunebook changed = new AbcTunebook(BOOK.stream().map(l -> l.equals("A4 d4 f4|") ? "A4 d4 e4|" : l).toList());
			File v2 = changed.extract(changed.tunes().get(0), folder);
			assertEquals("001 Nittonbundna_v002.abc", v2.getName());
			AbcTunebook changedAgain = new AbcTunebook(
					BOOK.stream().map(l -> l.equals("A4 d4 f4|") ? "A4 d4 d4|" : l).toList());
			assertEquals("001 Nittonbundna_v003.abc", changedAgain.extract(changedAgain.tunes().get(0), folder).getName());
			assertEquals(v2, changed.extract(changed.tunes().get(0), folder));
			assertEquals(file, book.extract(first, folder));
			// Splitting the whole book: one file per tune, the first one found again
			List<File> files = book.splitAll(folder);
			assertEquals(List.of("001 Nittonbundna.abc", "002 Polska fraan Smaaland.abc", "010 What Why no quotes star.abc"),
					files.stream().map(File::getName).toList());
			assertEquals(5, folder.list().length);
		} finally {
			try (Stream<java.nio.file.Path> walk = Files.walk(folder.toPath())) {
				walk.sorted(Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(File::delete);
			}
		}
	}
}