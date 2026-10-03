package com.digero.common.util;

import com.digero.common.i18n.UIText;

public class FileParseException extends Exception {
	private final String detail;
	private final String fileName;
	private final int line;
	private final int column;
	private final int relatedLine;
	private final int relatedColumn;

	/**
	 * An error at one place that is about another, e.g. a line that seems to be the rest of the field above it.
	 *
	 * @param relatedLine   The other place's line, or -1
	 * @param relatedColumn Its column, from 0, or -1
	 */
	public FileParseException(String message, String fileName, int line, int column, int relatedLine,
							  int relatedColumn) {
		this(message, fileName, line, column, relatedLine, relatedColumn, null);
	}

	/**
	 * @param place How the message says where, instead of "on line N" (e.g. "6 lines down", counted from a tune's X:
	 *              in text taken from a file); null for the line. getLine() is the line either way.
	 */
	public FileParseException(String message, String fileName, int line, int column, int relatedLine,
							  int relatedColumn, String place) {
		super(formatMessage(message, fileName, line, column, place));
		this.detail = message;
		this.fileName = fileName;
		this.line = line;
		this.column = column;
		this.relatedLine = relatedLine;
		this.relatedColumn = relatedColumn;
	}

	public FileParseException(String message, String fileName, int line, int column) {
		this(message, fileName, line, column, -1, -1);
	}
	
	public FileParseException(String message, String fileName) {
		this(message, fileName, -1, -1, -1, -1);
	}

	/** The message as given, without "Error reading" and where. */
	public String getDetail() {
		return detail;
	}

	public FileParseException(String message, String fileName, int line) {
		this(message, fileName, line, -1, -1, -1);
	}

	public String getFileName() {
		return fileName;
	}

	/** The line of the error, as given (the parsers count from 1), or -1. */
	public int getLine() {
		return line;
	}

	/** The column of the error, from 0, or -1. */
	public int getColumn() {
		return column;
	}

	/** The line of another place the error is about, or -1. */
	public int getRelatedLine() {
		return relatedLine;
	}

	/** The column of another place the error is about, from 0, or -1. */
	public int getRelatedColumn() {
		return relatedColumn;
	}


	private static String formatMessage(String message, String fileName, int line, int column, String place) {
		boolean file = fileName != null && !fileName.isEmpty();
		if (file && place != null) {
			if (column < 0)
				return UIText.get("common.fileparse.error.file.place", fileName, place, message);
			return UIText.get("common.fileparse.error.file.place.column", fileName, place,
					String.valueOf(column + 1), message);
		}
		if (line < 0)
			return file ? UIText.get("common.fileparse.error.file", fileName, message)
					: UIText.get("common.fileparse.error", message);
		String lineText = String.valueOf(line);
		if (column < 0)
			return file ? UIText.get("common.fileparse.error.file.line", fileName, lineText, message)
					: UIText.get("common.fileparse.error.line", lineText, message);
		String columnText = String.valueOf(column + 1);
		return file ? UIText.get("common.fileparse.error.file.line.column", fileName, lineText, columnText, message)
				: UIText.get("common.fileparse.error.line.column", lineText, columnText, message);
	}
}