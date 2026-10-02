package com.digero.common.abctomidi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.digero.common.abc.AbcText;
import com.digero.common.abc.StringCleaner;

/**
 * A songbook (tune book): a file of standard ABC with many tunes, each X: a tune of its own (ABC 2.1, 2.2). Lists its
 * tunes, gives one tune as ABC of its own (for a project, or a file), and splits the book into one file per tune. The
 * ABC isn't changed or fixed, only cut apart.
 * <p>
 * A tune gets the whole file header (everything before the first X:), then its lines from its X: to the first empty
 * line, which ends it (ABC 2.1, 2.2.1). Text in the file header and between tunes is free text: it's kept as %
 * comments, so it stays with the tune (a copyright notice, a description) without being read as music.
 * <p>
 * Whether a file is a songbook or a Lotro song (whose X: are parts that play together) is for the caller to decide,
 * e.g. by AbcToMidi.isMadeForLotro.
 */
public final class AbcTunebook {
	/** A tune: its X: value, its first title (escapes decoded, "" if none) and its lines in the book. */
	public record Tune(String number, String title, int firstLine, int endLine) {
	}

	/** A field (T:Title, M:3/4), a directive (%%MIDI ...) or a comment: kept as it is. */
	private static final Pattern FIELD_OR_COMMENT = Pattern.compile("^([A-Za-z]:|%).*");
	/** What StringCleaner.cleanForFileName leaves that Windows doesn't allow in a file name. */
	private static final Pattern NOT_IN_FILE_NAME = Pattern.compile("[<>:\"|*\\p{Cntrl}]");
	private static final int MAX_TITLE_LENGTH = 80;
	/** Written with Windows line ends, as Maestro and Lotro files are. */
	private static final String LINE_END = "\r\n";

	private final List<String> lines;
	private final List<String> header;
	private final List<Tune> tunes;
	private final int numberWidth;

	/** The songbook in a file, read as AbcToMidi reads it (UTF-8, else Windows-1252). */
	public static AbcTunebook read(File file) throws IOException {
		return new AbcTunebook(AbcToMidi.readLines(file));
	}

	/** The songbook in these lines (without line ends). */
	public AbcTunebook(List<String> lines) {
		this.lines = List.copyOf(lines);

		int first = 0;
		while (first < lines.size() && !isTuneStart(lines.get(first)))
			first++;
		List<String> headerLines = new ArrayList<>();
		for (String line : lines.subList(0, first))
			headerLines.add(asFieldOrComment(line));
		// Blank lines at its end go; one is put back between the header and the tune
		while (!headerLines.isEmpty() && headerLines.get(headerLines.size() - 1).isBlank())
			headerLines.remove(headerLines.size() - 1);
		this.header = Collections.unmodifiableList(headerLines);

		List<Tune> found = new ArrayList<>();
		int width = 3;
		for (int start = first; start < lines.size();) {
			int end = start + 1;
			while (end < lines.size() && !isTuneStart(lines.get(end)))
				end++;
			String number = lines.get(start).substring(2).trim();
			if (number.matches("\\d{1,9}"))
				width = Math.max(width, String.valueOf(Integer.parseInt(number)).length());
			found.add(new Tune(number, firstTitle(lines.subList(start, end)), start, end));
			start = end;
		}
		this.tunes = Collections.unmodifiableList(found);
		this.numberWidth = width;
	}

	/** The tunes, in the book's order. Empty if the file has no X:. */
	public List<Tune> tunes() {
		return tunes;
	}

	/** The file header: everything before the first X:, free text as % comments, without blank lines at its end. */
	public List<String> header() {
		return header;
	}

	/**
	 * The tune as ABC of its own: the file header, an empty line, then the tune from its X: to the first empty line.
	 * Lines after that (up to the next X:) are free text, kept as % comments.
	 */
	public List<String> tuneLines(Tune tune) {
		List<String> out = new ArrayList<>(header);
		if (!out.isEmpty())
			out.add("");
		List<String> own = lines.subList(tune.firstLine(), tune.endLine());
		int blank = 1;
		while (blank < own.size() && !own.get(blank).isBlank())
			blank++;
		out.addAll(own.subList(0, blank));
		// Free text after the tune: kept, but not as music (blank lines at the end go)
		int last = own.size();
		while (last > blank && own.get(last - 1).isBlank())
			last--;
		for (String line : own.subList(blank, last))
			out.add(line.isBlank() ? "%" : "% " + line);
		return out;
	}

	/** The tune as the text of a file: tuneLines with Windows line ends. */
	public String tuneText(Tune tune) {
		return String.join(LINE_END, tuneLines(tune)) + LINE_END;
	}

	/**
	 * The tune's file name, without .abc: its X: number (zero-padded, so the files sort in the book's order) and its
	 * first title, made safe for file names by StringCleaner.cleanForFileName, e.g. "007 Polska fraan Smaaland".
	 */
	public String fileName(Tune tune) {
		String number = tune.number();
		if (number.matches("\\d{1,9}"))
			number = String.format("%0" + numberWidth + "d", Integer.parseInt(number));
		else
			number = clean(number);
		String title = clean(tune.title());
		if (title.length() > MAX_TITLE_LENGTH)
			title = title.substring(0, MAX_TITLE_LENGTH).trim();
		if (title.isEmpty())
			return number.isEmpty() ? "tune" : number;
		return number.isEmpty() ? title : number + " " + title;
	}

	/**
	 * Writes the tune to the folder as fileName(tune).abc. If a file of that name has other content, the name gets a
	 * version: _v002, _v003 ... If the name or one of its versions already has exactly this content, that file is
	 * used and nothing is written.
	 *
	 * @return The tune's file
	 */
	public File extract(Tune tune, File folder) throws IOException {
		Files.createDirectories(folder.toPath());
		byte[] content = tuneText(tune).getBytes(StandardCharsets.UTF_8);
		String name = fileName(tune);
		for (int version = 1;; version++) {
			File file = new File(folder, (version == 1) ? name + ".abc" : String.format("%s_v%03d.abc", name, version));
			if (!file.exists()) {
				Files.write(file.toPath(), content);
				return file;
			}
			if (Arrays.equals(Files.readAllBytes(file.toPath()), content))
				return file;
		}
	}

	/** Writes every tune to the folder (see extract). @return The tunes' files, in the book's order */
	public List<File> splitAll(File folder) throws IOException {
		List<File> files = new ArrayList<>();
		for (Tune tune : tunes)
			files.add(extract(tune, folder));
		return files;
	}

	private static boolean isTuneStart(String line) {
		return line.startsWith("X:");
	}

	/** A line of the file header as it is if it's a field, a directive, a comment or blank; else a % comment. */
	private static String asFieldOrComment(String line) {
		return (line.isBlank() || FIELD_OR_COMMENT.matcher(line).matches()) ? line : "% " + line;
	}

	/** The first T: of the tune (before the first empty line), escapes decoded; "" if none. */
	private static String firstTitle(List<String> tuneLines) {
		for (String line : tuneLines) {
			if (line.isBlank())
				break;
			if (line.startsWith("T:"))
				return AbcText.decode(line.substring(2).trim());
		}
		return "";
	}

	/** Safe in a file name: StringCleaner's cleaning, then what Windows doesn't allow, one space between words. */
	private static String clean(String text) {
		if (text.isBlank())
			return "";
		String cleaned = StringCleaner.cleanForFileName(text);
		cleaned = NOT_IN_FILE_NAME.matcher(cleaned).replaceAll(" ").replaceAll("\\s+", " ").trim();
		return cleaned.equals("mySong") && !text.contains("mySong") ? "" : cleaned; // StringCleaner's name for nothing
	}
}