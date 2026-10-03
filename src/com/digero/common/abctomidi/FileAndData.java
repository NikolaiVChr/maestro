package com.digero.common.abctomidi;

import java.io.File;
import java.util.List;

public class FileAndData {
	public final File file;
	public final List<String> lines;
	/**
	 * The name in error messages: the file's, or for text taken from it, what it is, e.g. "Book.abc, the tune"
	 * (AbcToMidi.tuneAloneName, keptTextName)
	 */
	public final String name;
	/** Text taken from the file, not its own lines: an error's line is counted from the tune's X: line */
	public final boolean taken;

	public FileAndData(File file, List<String> lines) {
		this.file = file;
		this.lines = lines;
		this.name = (file != null) ? file.getName() : "";
		this.taken = false;
	}

	/** Text taken from the file (a tune of a book, the ABC a project keeps), named in messages so. */
	public FileAndData(File file, List<String> lines, String name) {
		this.file = file;
		this.lines = lines;
		this.name = name;
		this.taken = true;
	}
}
