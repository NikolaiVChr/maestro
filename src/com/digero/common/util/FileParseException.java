package com.digero.common.util;

import com.digero.common.i18n.UIText;

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
		boolean file = fileName != null && !fileName.isEmpty();
		String lineText = String.valueOf(line);
		String columnText = String.valueOf(column + 1);
		if (line < 0) {
			return file ? UIText.get("common.fileparse.error.file", fileName, message)
					: UIText.get("common.fileparse.error", message);
		}
		if (column < 0) {
			return file ? UIText.get("common.fileparse.error.file.line", fileName, lineText, message)
					: UIText.get("common.fileparse.error.line", lineText, message);
		}
		return file ? UIText.get("common.fileparse.error.file.line.column", fileName, lineText, columnText, message)
				: UIText.get("common.fileparse.error.line.column", lineText, columnText, message);
	}
}