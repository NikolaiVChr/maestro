package com.digero.common.util;

@SuppressWarnings("serial")
public class LotroFileParseException extends FileParseException {
	public LotroFileParseException(String message, String fileName, int line, int column) {
		super(message, fileName, line, column);
	}

	public LotroFileParseException(String message, String fileName, int line) {
		super(message, fileName, line);
	}

	public LotroFileParseException(String message, String fileName) {
		super(message, fileName);
	}
}
