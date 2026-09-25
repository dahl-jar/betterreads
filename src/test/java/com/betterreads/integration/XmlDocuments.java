package com.betterreads.integration;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.stream.IntStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class XmlDocuments {

    private XmlDocuments() {
    }

    public static Document fixture(final String path) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(Fixtures.read(path))));
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String write(final Document document) {
        try {
            final StringWriter out = new StringWriter();
            final TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            factory.newTransformer().transform(new DOMSource(document), new StreamResult(out));
            return out.toString();
        } catch (TransformerException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static List<Element> elements(final Document document, final String namespace, final String tag) {
        return elements(document.getElementsByTagNameNS(namespace, tag));
    }

    public static List<Element> elements(final Element parent, final String namespace, final String tag) {
        return elements(parent.getElementsByTagNameNS(namespace, tag));
    }

    private static List<Element> elements(final NodeList nodes) {
        return IntStream.range(0, nodes.getLength())
            .mapToObj(nodes::item)
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast)
            .toList();
    }
}
