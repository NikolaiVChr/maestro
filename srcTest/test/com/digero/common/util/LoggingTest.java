package com.digero.common.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.jupiter.api.Test;

public class LoggingTest {
	@Test
	public void sanitizesExceptionTextWithoutFlatteningStackTrace() throws Exception {
		Formatter formatter = newSanitizingFormatter();
		RuntimeException cause = new IllegalArgumentException("cause\u202Etext\nline");
		RuntimeException thrown = new RuntimeException("top\r\nmessage\u202E", cause);
		thrown.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("com.digero.TestClass", "testMethod", "TestClass.java", 42)
		});
		cause.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("com.digero.CauseClass", "causeMethod", "CauseClass.java", 7)
		});

		LogRecord record = new LogRecord(Level.SEVERE, "Message {0}");
		record.setLoggerName("test");
		record.setParameters(new Object[] { new Object() {
			@Override
			public String toString() {
				return "param\u202E\nvalue";
			}
		} });
		record.setThrown(thrown);

		String formatted = formatter.format(record);
		String lineSeparator = System.lineSeparator();

		assertTrue(formatted.contains("SEVERE test - Message param__value" + lineSeparator));
		assertTrue(formatted.contains("java.lang.RuntimeException: top__message_" + lineSeparator));
		assertTrue(formatted.contains("Caused by: java.lang.IllegalArgumentException: cause_text_line"));
		assertTrue(formatted.contains("\tat com.digero.TestClass.testMethod(TestClass.java:42)"));
		assertTrue(formatted.contains("\tat com.digero.CauseClass.causeMethod(CauseClass.java:7)"));
		assertTrue(formatted.contains(lineSeparator + "java.lang.RuntimeException: top__message_"));
		assertFalse(formatted.contains("\u202E"));
		assertFalse(formatted.contains("top\r\nmessage"));
		assertFalse(formatted.contains("cause\u202Etext\nline"));
	}

	private static Formatter newSanitizingFormatter() {
		return new SanitizingFormatter();
	}
}