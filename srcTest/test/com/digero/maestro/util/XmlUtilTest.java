package com.digero.maestro.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXParseException;

class XmlUtilTest {

    @Test
    void xml10IsAccepted() {
        assertDoesNotThrow(() ->
            openXml(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <song/>
                """
            )
        );
    }

    @Test
    void xml11IsAccepted() {
        assertDoesNotThrow(() ->
            openXml(
                """
                <?xml version="1.1" encoding="UTF-8"?>
                <song/>
                """
            )
        );
    }

    @Test
    void unsupportedXmlVersionHasAccurateErrorMessage() {
        SAXParseException exception = assertThrows(SAXParseException.class, () ->
            openXml(
                """
                <?xml version="2.0" encoding="UTF-8"?>
                <song/>
                """
            )
        );

        assertTrue(
            exception.getMessage().contains("Unsupported XML version \"2.0\""),
            () -> "Unexpected error message: " + exception.getMessage()
        );

        assertTrue(
            exception.getMessage().contains("1.0") && exception.getMessage().contains("1.1"),
            () -> "Supported XML versions should be stated: " + exception.getMessage()
        );
    }

    private static void openXml(String xml) throws Exception {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            XmlUtil.openDocument(stream);
        }
    }
}
