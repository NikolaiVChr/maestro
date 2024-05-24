package com.digero.maestro.util;

import java.io.File;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.digero.common.abc.LotroInstrument;
import com.digero.common.midi.KeySignature;
import com.digero.common.midi.TimeSignature;
import com.digero.common.util.ParseException;
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
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		return node.getTextContent();
	}

	public static int parseValue(Node parent, String xpath, int defaultValue)
			throws ParseException, XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		try {
			return Integer.parseInt(node.getTextContent());
		} catch (NumberFormatException e) {
			throw invalidValueException(node, e.getMessage());
		}
	}

	public static byte parseValue(Node parent, String xpath, byte defaultValue)
			throws ParseException, XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		try {
			return Byte.parseByte(node.getTextContent());
		} catch (NumberFormatException e) {
			throw invalidValueException(node, e.getMessage());
		}
	}

	public static float parseValue(Node parent, String xpath, float defaultValue)
			throws ParseException, XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		try {
			return Float.parseFloat(node.getTextContent());
		} catch (NumberFormatException e) {
			throw invalidValueException(node, e.getMessage());
		}
	}

	public static boolean parseValue(Node parent, String xpath, boolean defaultValue)
			throws ParseException, XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		String value = node.getTextContent().toLowerCase();
		if (value.equals("true") || value.equals("1"))
			return true;
		if (value.equals("false") || value.equals("0"))
			return false;

		throw invalidValueException(node, "Value must be 'true' or 'false'");
	}

	public static TimeSignature parseValue(Node parent, String xpath, TimeSignature defaultValue)
			throws ParseException, XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		try {
			return new TimeSignature(node.getTextContent());
		} catch (IllegalArgumentException e) {
			throw invalidValueException(node, e.getMessage());
		}
	}

	public static KeySignature parseValue(Node parent, String xpath, KeySignature defaultValue)
			throws ParseException, XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		try {
			return new KeySignature(node.getTextContent());
		} catch (IllegalArgumentException e) {
			throw invalidValueException(node, e.getMessage());
		}
	}

	public static LotroInstrument parseValue(Node parent, String xpath, LotroInstrument defaultValue)
			throws ParseException, XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		LotroInstrument instrument = LotroInstrument.findInstrumentName(node.getTextContent(), null);
		if (instrument == null)
			throw invalidValueException(node, "Could not parse instrument name: " + node.getTextContent());

		return instrument;
	}

	public static Version parseValue(Node parent, String xpath, Version defaultValue)
			throws ParseException, XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		Version version = Version.parseVersion(node.getTextContent());
		if (version == null)
			version = defaultValue;
		return version;
	}

	public static byte[] parseValue(Node parent, String xpath, byte[] defaultValue)
			throws ParseException, XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		String text = node.getTextContent();
		if (text == null || text.equals(""))
			return defaultValue;

		try {
			return Hex.decodeHex(text);
		} catch (IllegalArgumentException | DecoderException e) {
			throw invalidValueException(node, e.getMessage());
		}
	}

	public static File parseValue(Node parent, String xpath, File defaultValue) throws XPathExpressionException {
		Node node = XmlUtil.selectSingleNode(parent, xpath);
		if (node == null)
			return defaultValue;

		return new File(node.getTextContent());
	}

	public static ParseException invalidTrackException(Node node, String message) {
		File f = XmlUtil.getDocumentFile(node.getOwnerDocument());
		String fileName = (f == null) ? null : f.getName();
		return new ParseException(message, fileName, XmlUtil.getLineNumber(node));
	}

	public static ParseException invalidValueException(Node node, String message) {
		SaveUtil.clean(node);
		String msg = "Invalid value \"" + node.getTextContent() + "\" for " + node.getNodeName();
		if (message != null && message.length() > 0)
			msg += ": " + message;

		File f = XmlUtil.getDocumentFile(node.getOwnerDocument());
		String fileName = (f == null) ? null : f.getName();
		return new ParseException(msg, fileName, XmlUtil.getLineNumber(node));
	}

	private static void clean(Node node) {
		NodeList childNodes = node.getChildNodes();

		for (int n = childNodes.getLength() - 1; n >= 0; n--) {
			Node child = childNodes.item(n);
			short nodeType = child.getNodeType();

			if (nodeType == Node.ELEMENT_NODE)
				clean(child);
			else if (nodeType == Node.TEXT_NODE) {
				String trimmedNodeVal = child.getNodeValue().trim();
				if (trimmedNodeVal.length() == 0)
					node.removeChild(child);
				else
					child.setNodeValue(trimmedNodeVal);
			} else if (nodeType == Node.COMMENT_NODE)
				node.removeChild(child);
		}
	}

	public static ParseException missingValueException(Node node, String xpath) {
		String msg = "Missing required value \"" + xpath + "\" for <" + node.getNodeName() + "> element";
		File f = XmlUtil.getDocumentFile(node.getOwnerDocument());
		String fileName = (f == null) ? null : f.getName();
		return new ParseException(msg, fileName, XmlUtil.getLineNumber(node));
	}
}
