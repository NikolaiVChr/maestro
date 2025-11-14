package com.digero.common.util;

@SuppressWarnings("serial")
public class FileParseException extends Exception {
	public FileParseException(String message, String fileName, int line, int column) {
		super(formatMessage(message, fileName, line, column));
	}

	public FileParseException(String message, String fileName, int line) {
		super(formatMessage(message, fileName, line, -1));
	}

	public FileParseException(String message, String fileName) {
		super(formatMessage(message, fileName, -1, -1));
	}

	private static String formatMessage(String message, String fileName, int line, int column) {
		String msg = "Error";
		if (fileName != null && !fileName.isEmpty())
			msg += " reading " + fileName;

		if (line >= 0) {
			msg += " on line " + line;
			if (column >= 0)
				msg += ", column " + (column + 1);
		}

		msg += ":\n" + message;

		return msg;
	}
}