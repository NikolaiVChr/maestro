package com.digero.maestro.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

public class XmlUtil {
	private XmlUtil() {
	}

	//
	// Document
	//

	public static Document createDocument() {
		try {
			return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e) {
			// How can a vanilla instance throw a configuration exception?
			e.printStackTrace();
			assert false : e.getMessage();
			throw new RuntimeException(e);
		}
	}

	public static Document openDocument(String file) throws SAXException, IOException {
		return openDocument(new File(file));
	}

	public static Document openDocument(File file) throws SAXException, IOException {
		try (FileInputStream stream = new FileInputStream(file)) {
			Document doc = openDocument(stream);
			doc.setUserData(DOCUMENT_FILE_USERDATA, file, null);
			return doc;
		}
	}
	
	public static String sanitizeXmlStringForLoading(String input) {
	    if (input == null) return "";

	    StringBuilder out = new StringBuilder(input.length());
	    int i = 0, len = input.length();

	    while (i < len) {
	        int cp = input.codePointAt(i);

	        switch (cp) {
	            case 0x0099:       // “™” in C1 block
	                cp = 0x2122;   // → ™
	                break;
	            case 0x00A9:       // © (okay in XML 1.1)
	            case 0x00AE:       // ®
	            case 0x20AC:       // €
	                // leave as-is
	                break;
	            case 0x201C:      // “
	            case 0x201D:      // ”
	                cp = '"';
	                break;
	            case 0x2018:      // ‘
	            case 0x2019:      // ’
	                cp = '\'';
	                break;
	            case 0x2013:      // en-dash
	                cp = '-';
	                break;
	            case 0x2014:      // em-dash
	                out.append("--");
	                i += Character.charCount(cp);
	                continue;
	            default:
	        }

	        // 2) filter against XML 1.1 literal Char production (disallow raw C1 controls)
	        if (isValidXml11Literal(cp)) {
	            out.append(Character.toChars(cp));
	        } else {
	            out.append('?');
	        }

	        i += Character.charCount(cp);
	    }

	    return out.toString();
	}
	
	public static String sanitizeStringForXMLSaving(String input) {
	    if (input == null || input.isEmpty()) {
	        return "";
	    }
	    StringBuilder out = new StringBuilder(input.length());
	    int codePoint, i = 0, len = input.length();
	    while (i < len) {
	        codePoint = input.codePointAt(i);

	        // Always allow whitespace
	        if (codePoint == 0x9   // tab
	         || codePoint == 0xA   // line feed
	         || codePoint == 0xD)  // carriage return
	        {
	            out.append((char) codePoint);

	        // Char production minus RestrictedChar
	        } else if (
	            // XML 1.1 Char ranges
	            ((codePoint >= 0x20   && codePoint <= 0xD7FF)
	          || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
	          || (codePoint >= 0x10000&& codePoint <= 0x10FFFF))

	            // minus RestrictedChar: [#x7F-#x84] and [#x86-#x9F]
	            && !( (codePoint >= 0x7F  && codePoint <= 0x84)
	                || (codePoint >= 0x86 && codePoint <= 0x9F) )
	        ) {
	            out.append(Character.toChars(codePoint));
	        }

	        i += Character.charCount(codePoint);
	    }
	    return out.toString();
	}

	/** True if cp is a literal Char in XML 1.1 (i.e. including whitespace but excluding raw C1 controls). */
	private static boolean isValidXml11Literal(int cp) {
	    // mandatory whitespace
	    if (cp == 0x9 || cp == 0xA || cp == 0xD) {
	        return true;
	    }
	    // BMP range
	    if (cp >= 0x20 && cp <= 0xD7FF) {
	        // exclude C1 raw controls: 0x7F-0x84 and 0x86-0x9F
	        if ((cp >= 0x7F && cp <= 0x84) || (cp >= 0x86 && cp <= 0x9F)) {
	            return false;
	        }
	        return true;
	    }
	    // supplementary BMP and above
	    if (cp >= 0xE000 && cp <= 0xFFFD) {
	        return true;
	    }
	    if (cp >= 0x10000 && cp <= 0x10FFFF) {
	        return true;
	    }
	    return false;
	}


	private static boolean isValidXmlCharacter(char c) {
	    return (c == 0x09 || c == 0x0A || c == 0x0D) || 
	           (c >= 0x20 && c <= 0xD7FF) ||
	           (c >= 0xE000 && c <= 0xFFFD) ||
	           (c >= 0x10000 && c <= 0x10FFFF);
	}

	public static Document openDocument(InputStream stream) throws SAXException, IOException {
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
	        StringBuilder xmlBuilder = new StringBuilder();
	        String line;
	        
	        while ((line = reader.readLine()) != null) {
	        	// Sanitize each line
	        	// This is done due to previous Maestro versions might have saved illegal XML 1.1 chars
	        	// And we need to be able to open the broken projects.
	            xmlBuilder.append(sanitizeXmlStringForLoading(line)).append("\n"); 
	        }

	        String sanitizedXml = xmlBuilder.toString(); // Final sanitized XML string

	        // Convert sanitized XML string back to an InputStream
	        InputStream sanitizedStream = new ByteArrayInputStream(sanitizedXml.getBytes(StandardCharsets.UTF_8));
			LineNumberHandler handler = new LineNumberHandler();
			SAXParserFactory.newInstance().newSAXParser().parse(sanitizedStream, handler);
			return handler.getDocument();
		} catch (ParserConfigurationException e) {
			// How can a vanilla instance throw a configuration exception?
			e.printStackTrace();
			assert false : e.getMessage();
			throw new RuntimeException(e);
		}
	}

	public static void saveDocument(Document document, String file) throws TransformerException, IOException {
		saveDocument(document, new File(file));
	}

	public static void saveDocument(Document document, File file) throws TransformerException, IOException {
		try (FileOutputStream stream = new FileOutputStream(file)) {
			saveDocument(document, stream);
		}
	}

	public static void saveDocument(Document document, OutputStream stream) throws TransformerException, IOException {
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.METHOD,               "xml");
			transformer.setOutputProperty(OutputKeys.VERSION,              "1.1");
			transformer.setOutputProperty(OutputKeys.ENCODING,             "UTF-8");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			transformer.transform(new DOMSource(document), new StreamResult(stream));
		} catch (TransformerConfigurationException | TransformerFactoryConfigurationError e) {
			// How can a vanilla instance throw a configuration exception?
			e.printStackTrace();
			assert false : e.getMessage();
			throw new RuntimeException(e);
		}
	}

	public static String DOCUMENT_FILE_USERDATA = XmlUtil.class.getName() + ".DOCUMENT_FILE";
	public static String LINE_NUMBER_USERDATA = XmlUtil.class.getName() + ".LINE_NUMBER";

	private static class LineNumberHandler extends DefaultHandler {
		private Document doc = null;
		private final Deque<Node> stack = new ArrayDeque<>();
		private final StringBuilder text = new StringBuilder();
		private Locator locator = null;

		public Document getDocument() {
			return doc;
		}

		private void appendText() {
			if (!text.isEmpty())
				stack.peek().appendChild(doc.createTextNode(text.toString()));
			text.setLength(0);
		}

		@Override
		public void startDocument() throws SAXException {
			doc = createDocument();
			stack.push(doc);
		}

		@Override
		public void endDocument() throws SAXException {
			stack.pop();
		}

		@Override
		public void setDocumentLocator(Locator locator) {
			this.locator = locator;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes)
				throws SAXException {
			appendText();
			Element ele = doc.createElement(qName);
			stack.push(ele);
			if (locator != null)
				ele.setUserData(LINE_NUMBER_USERDATA, locator.getLineNumber(), null);
			for (int i = 0; i < attributes.getLength(); i++)
				ele.setAttribute(attributes.getQName(i), attributes.getValue(i));
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException {
			appendText();
			Node node = stack.pop();
			stack.peek().appendChild(node);
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException {
			text.append(ch, start, length);
		}
	}

	public static int getLineNumber(Node node) {
		if (node instanceof Attr)
			node = ((Attr) node).getOwnerElement();

		Object data = node.getUserData(LINE_NUMBER_USERDATA);
		if (data instanceof Integer)
			return (Integer) data;

		return -1;
	}

	public static File getDocumentFile(Document doc) {
		Object data = doc.getUserData(DOCUMENT_FILE_USERDATA);
		if (data instanceof File)
			return (File) data;

		return null;
	}

	//
	// NodeList
	//

	public static class NodeListWrapper<TNode extends Node> extends AbstractList<TNode> {
		private final NodeList nodeList;

		public NodeListWrapper(NodeList nodeList) {
			this.nodeList = nodeList;
		}

		public NodeList getNodeList() {
			return nodeList;
		}

		@Override
		public int size() {
			return nodeList.getLength();
		}

		@SuppressWarnings("unchecked") //
		@Override
		public TNode get(int index) {
			return (TNode) nodeList.item(index);
		}
	}

	//
	// XPath
	//

	private static final XPath xpath = XPathFactory.newInstance().newXPath();

	public static Node selectSingleNode(Node fromNode, String xpathString) throws XPathExpressionException {
		return (Node) xpath.evaluate(xpathString, fromNode, XPathConstants.NODE);
	}

	public static Element selectSingleElement(Node fromNode, String xpathString) throws XPathExpressionException {
		return (Element) xpath.evaluate(xpathString, fromNode, XPathConstants.NODE);
	}

	public static NodeListWrapper<Node> selectNodes(Node fromNode, String xpathString) throws XPathExpressionException {
		return new NodeListWrapper<>((NodeList) xpath.evaluate(xpathString, fromNode, XPathConstants.NODESET));
	}

	public static NodeListWrapper<Element> selectElements(Node fromNode, String xpathString)
			throws XPathExpressionException {
		return new NodeListWrapper<>((NodeList) xpath.evaluate(xpathString, fromNode, XPathConstants.NODESET));
	}

	//
	// Exceptions
	//

	public static String formatException(SAXException e) {
		String msg = e.getMessage();
		if (e instanceof SAXParseException e2) {
            if (e2.getLineNumber() >= 0) {
				msg += " (line " + e2.getLineNumber();
				if (e2.getColumnNumber() >= 0)
					msg += ", column " + e2.getColumnNumber();
				msg += ")";
			}
		}
		return msg;
	}

	public static String formatException(TransformerException e) {
		return e.getMessageAndLocation();
	}
}
