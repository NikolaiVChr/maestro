package com.digero.maestro.util;

import java.io.File;
import java.util.HexFormat;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.TimeSignature;
import com.digero.common.util.FileParseException;
import com.digero.common.util.Version;

public class SaveUtil {
	private SaveUtil() {
	}

	public static void appendChildTextElement(Element parent, String childName, String value) {
		Element child = parent.getOwnerDocument().createElement(childName);
		child.setTextContent(value);
		parent.appendChild(child);
	}

    public static String parseValue(Node parent, String xpath, String defaultValue) throws XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        return (val == null) ? defaultValue : val;
    }

    /**
     * Helper to retrieve a node or attribute value string.
     * Optimization: Handles '@attribute' selector directly to avoid XPath overhead.
     */
    private static String getNodeContent(Node parent, String xpath) throws XPathExpressionException {
        if (xpath.startsWith("@") && parent instanceof Element) {
            String attrName = xpath.substring(1);
            if (((Element) parent).hasAttribute(attrName)) {
                return ((Element) parent).getAttribute(attrName);
            }
            return null; // Simulate XPath "not found" behavior
        }

        // Optimization: Path/Attribute combination (e.g. "exportSettings/@transpose")
        // We can split simple paths to avoid XPath if it's just child/@attr
        int slashIndex = xpath.lastIndexOf("/@");
        if (slashIndex > 0) {
            String childName = xpath.substring(0, slashIndex);
            String attrName = xpath.substring(slashIndex + 2);

            // Ensure no other complex XPath syntax exists in the child part
            if (XmlUtil.isSimpleTagName(childName)) {
                Node child = XmlUtil.selectSingleNode(parent, childName);
                if (child instanceof Element && ((Element) child).hasAttribute(attrName)) {
                    return ((Element) child).getAttribute(attrName);
                }
                return null;
            }
        }

        // Standard Element lookup
        Node node = XmlUtil.selectSingleNode(parent, xpath);
        return (node == null) ? null : node.getTextContent();
    }

    public static int parseValue(Node parent, String xpath, int defaultValue)
            throws FileParseException, XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null) return defaultValue;

        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            throw createInvalidValueException(parent, xpath, val, e.getMessage());
        }
    }

    public static byte parseValue(Node parent, String xpath, byte defaultValue)
            throws FileParseException, XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null) return defaultValue;

        try {
            return Byte.parseByte(val);
        } catch (NumberFormatException e) {
            throw createInvalidValueException(parent, xpath, val, e.getMessage());
        }
    }

    public static float parseValue(Node parent, String xpath, float defaultValue)
            throws FileParseException, XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null) return defaultValue;

        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            throw createInvalidValueException(parent, xpath, val, e.getMessage());
        }
    }

    public static boolean parseValue(Node parent, String xpath, boolean defaultValue)
            throws FileParseException, XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null) return defaultValue;

        String lower = val.toLowerCase();
        if (lower.equals("true") || lower.equals("1")) return true;
        if (lower.equals("false") || lower.equals("0")) return false;

        throw createInvalidValueException(parent, xpath, val, "Value must be 'true' or 'false'");
    }

    public static TimeSignature parseValue(Node parent, String xpath, TimeSignature defaultValue)
            throws FileParseException, XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null) return defaultValue;

        try {
            return new TimeSignature(val);
        } catch (IllegalArgumentException e) {
            throw createInvalidValueException(parent, xpath, val, e.getMessage());
        }
    }

    public static KeySignature parseValue(Node parent, String xpath, KeySignature defaultValue)
            throws FileParseException, XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null) return defaultValue;

        try {
            return new KeySignature(val);
        } catch (IllegalArgumentException e) {
            throw createInvalidValueException(parent, xpath, val, e.getMessage());
        }
    }

    public static LotroInstrument parseValue(Node parent, String xpath, LotroInstrument defaultValue)
            throws FileParseException, XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null) return defaultValue;

        LotroInstrument instrument = LotroInstrument.findInstrumentName(val, null);
        if (instrument == null)
            throw createInvalidValueException(parent, xpath, val, "Could not parse instrument name: " + val);

        return instrument;
    }

    public static Version parseValue(Node parent, String xpath, Version defaultValue)
            throws XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null) return defaultValue;

        Version version = Version.parseVersion(val);
        return (version == null) ? defaultValue : version;
    }

    public static byte[] parseValue(Node parent, String xpath, byte[] defaultValue)
            throws FileParseException, XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null || val.isEmpty()) return defaultValue;

        try {
            return HexFormat.of().parseHex(val);
        } catch (IllegalArgumentException e) {
            throw createInvalidValueException(parent, xpath, val, e.getMessage());
        }
    }

    public static File parseValue(Node parent, String xpath, File defaultValue) throws XPathExpressionException {
        String val = getNodeContent(parent, xpath);
        if (val == null) return defaultValue;

        return new File(val);
    }

    public static FileParseException invalidTrackException(Node node, String message) {
        File f = XmlUtil.getDocumentFile(node.getOwnerDocument());
        String fileName = (f == null) ? null : f.getName();
        return new FileParseException(message, fileName, XmlUtil.getLineNumber(node));
    }

    @Deprecated
	private static void clean(Node node) {
		NodeList childNodes = node.getChildNodes();

		for (int n = childNodes.getLength() - 1; n >= 0; n--) {
			Node child = childNodes.item(n);
			short nodeType = child.getNodeType();

			if (nodeType == Node.ELEMENT_NODE)
				clean(child);
			else if (nodeType == Node.TEXT_NODE) {
				String trimmedNodeVal = child.getNodeValue().trim();
				if (trimmedNodeVal.isEmpty())
					node.removeChild(child);
				else
					child.setNodeValue(trimmedNodeVal);
			} else if (nodeType == Node.COMMENT_NODE)
				node.removeChild(child);
		}
	}

    public static FileParseException missingValueException(Node node, String xpath) {
        String msg = "Missing required value \"" + xpath + "\" for <" + node.getNodeName() + "> element";
        File f = XmlUtil.getDocumentFile(node.getOwnerDocument());
        String fileName = (f == null) ? null : f.getName();
        return new FileParseException(msg, fileName, XmlUtil.getLineNumber(node));
    }

    /**
     * helper that doesn't require a Node object if we parsed an attribute string directly
     */
    private static FileParseException createInvalidValueException(Node parent, String xpath, String value, String message) {
        String msg = "Invalid value \"" + value + "\" for " + xpath;
        if (message != null && !message.isEmpty())
            msg += ": " + message;

        File f = XmlUtil.getDocumentFile(parent.getOwnerDocument());
        String fileName = (f == null) ? null : f.getName();
        return new FileParseException(msg, fileName, XmlUtil.getLineNumber(parent));
    }
}
