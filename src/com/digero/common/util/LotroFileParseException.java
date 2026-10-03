package com.digero.common.util;

@SuppressWarnings("serial")
public class LotroFileParseException extends FileParseException {
	
	public LotroFileParseException(String message, String fileName, int line, int column) {
		super(message, fileName, line, column);
	}

	/** @param place How the message says where, instead of "on line N" (see FileParseException) */
	public LotroFileParseException(String message, String fileName, int line, int column, String place) {
		super(message, fileName, line, column, -1, -1, place);
	}

	public LotroFileParseException(String message, String fileName, int line) {
		super(message, fileName, line);
	}

	public LotroFileParseException(String message, String fileName) {
		super(message, fileName);
	}
}
