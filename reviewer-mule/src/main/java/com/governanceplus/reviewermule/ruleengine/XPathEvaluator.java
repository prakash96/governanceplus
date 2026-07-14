package com.governanceplus.reviewermule.ruleengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.governanceplus.reviewermule.ruleengine.model.Violation;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Evaluates XPath rules against Mule XML file content. Parses NAMESPACE-UNAWARE (same choice
 * PomDependencyValidator makes for pom.xml) so unprefixed rule expressions like //logger or //flow
 * match regardless of the file's declared default namespace (every Mule XML file here declares
 * xmlns="http://www.mulesoft.org/schema/mule/core" as the DEFAULT, unprefixed namespace) — this is
 * the exact behavior the native DataWeave xpath() reimplementation could never quite replicate
 * (it kept returning zero matches because strict namespace-aware XPath doesn't match an unprefixed
 * name test against an element in a non-null default namespace).
 *
 * Line numbers: a plain DocumentBuilder-parsed DOM has no source-location info at all, so every
 * match would otherwise report line: -1. Instead, the DOM here is built by hand from SAX events
 * (see {@link LineTrackingHandler}), stamping each element with its source line number as DOM user
 * data via the SAX {@link Locator} — the same technique reviewer-core's VTD-XML-backed evaluator
 * achieved a different way. This is real, not approximate: it's the parser's own line count.
 *
 * File paths in violations are reported RELATIVE to the project root (e.g.
 * "src/main/mule/order-processing.xml"), not as the full absolute path read from disk — see
 * {@link #relativize}. Otherwise a review's findings would leak the server's filesystem layout
 * (an extracted-zip temp directory, or wherever a caller-supplied projectPath happened to point).
 *
 * Called from Mule flows via DataWeave's native Java interop
 * (`import * from java!com::governanceplus::reviewermule::ruleengine::XPathEvaluator`), never the
 * Mule Java Module. No usageAttribute/usagePattern "is this variable used elsewhere" convention —
 * removed at the user's request; every match is reported as a violation directly.
 */
public class XPathEvaluator {

    private static final String LINE_NUMBER_KEY = "lineNumber";

    /**
     * Real-review bridge (review-engine.xml): evaluates every applicable rule (already filtered by
     * projectNamePattern in DataWeave) against one already-read file's content. rulesJson is a JSON
     * array of {id, description, xpath} objects. filePath is the file's full absolute path as read
     * from disk (extracted-zip temp dir or a caller-supplied projectPath) — projectDir is that same
     * project root, so the violation's file name can be reported RELATIVE to it
     * (e.g. "src/main/mule/order-processing.xml") instead of leaking the server's absolute
     * filesystem layout (a temp extraction directory, or wherever projectPath happened to point).
     */
    public static String evaluateXmlRulesAsJson(String xmlContent, String filePath, String projectDir, String rulesJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> ruleMaps = mapper.readValue(rulesJson, new TypeReference<List<Map<String, String>>>() {});

        Document doc = loadXmlWithLineNumbers(xmlContent);
        XPath xpath = XPathFactory.newInstance().newXPath();
        String displayPath = relativize(filePath, projectDir);

        List<Violation> violations = new ArrayList<>();
        for (Map<String, String> rule : ruleMaps) {
            String xpathExpr = rule.get("xpath");
            if (xpathExpr == null || xpathExpr.trim().isEmpty()) {
                continue;
            }
            try {
                NodeList matches = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
                for (int i = 0; i < matches.getLength(); i++) {
                    int line = lineNumberOf(matches.item(i));
                    violations.add(new Violation(rule.get("id"), rule.get("description"), displayPath, line));
                }
            } catch (Exception e) {
                // A malformed stored rule expression shouldn't fail the whole review.
            }
        }

        return mapper.writeValueAsString(violations);
    }

    /**
     * Reports a file relative to the project root instead of its full absolute path — falls back to
     * just the bare file name if the two paths don't share a common root (relativize() throws) or
     * either argument is missing, rather than leaking the whole absolute path in that edge case.
     */
    private static String relativize(String filePath, String projectDir) {
        try {
            return Paths.get(projectDir).relativize(Paths.get(filePath)).toString().replace('\\', '/');
        } catch (Exception e) {
            Path fileName = Paths.get(filePath).getFileName();
            return fileName != null ? fileName.toString() : filePath;
        }
    }

    /** "Test this rule" bridge (rules-test.xml) — one ad-hoc rule against one pasted sample. */
    public static String evaluateXmlSampleAsJson(String xpathExpr, String description, String sampleXml) throws Exception {
        Document doc = loadXmlWithLineNumbers(sampleXml);
        XPath xpath = XPathFactory.newInstance().newXPath();

        List<Violation> violations = new ArrayList<>();
        NodeList matches = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
        for (int i = 0; i < matches.getLength(); i++) {
            int line = lineNumberOf(matches.item(i));
            violations.add(new Violation("TEST", description, "sample", line));
        }

        return new ObjectMapper().writeValueAsString(violations);
    }

    /**
     * A matched node might be an element, an attribute, or (rarely) something else the XPath
     * targeted directly — only elements carry a stamped line number, so walk up to the nearest one.
     * Returns -1 if no line number is available (shouldn't normally happen for a real element node).
     */
    private static int lineNumberOf(Node node) {
        Node current = node;
        if (current instanceof Attr) {
            current = ((Attr) current).getOwnerElement();
        }
        while (current != null && !(current instanceof Element)) {
            current = current.getParentNode();
        }
        if (current == null) {
            return -1;
        }
        Object line = current.getUserData(LINE_NUMBER_KEY);
        return line instanceof Integer ? (Integer) line : -1;
    }

    private static Document loadXmlWithLineNumbers(String content) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        Document doc = dbf.newDocumentBuilder().newDocument();

        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(false);
        LineTrackingHandler handler = new LineTrackingHandler(doc);
        spf.newSAXParser().parse(new InputSource(new StringReader(content)), handler);

        return doc;
    }

    /**
     * Builds a DOM tree by hand from SAX events instead of using DocumentBuilder directly, so each
     * element can be stamped with its source line number (via the SAX Locator) as DOM user data —
     * something a standard DocumentBuilder-parsed DOM has no way to carry.
     */
    private static final class LineTrackingHandler extends DefaultHandler {
        private final Document doc;
        private final Deque<Element> stack = new ArrayDeque<>();
        private final StringBuilder textBuffer = new StringBuilder();
        private Locator locator;

        LineTrackingHandler(Document doc) {
            this.doc = doc;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            flushText();
            Element element = doc.createElement(qName);
            for (int i = 0; i < attributes.getLength(); i++) {
                element.setAttribute(attributes.getQName(i), attributes.getValue(i));
            }
            if (locator != null) {
                element.setUserData(LINE_NUMBER_KEY, locator.getLineNumber(), null);
            }
            if (stack.isEmpty()) {
                doc.appendChild(element);
            } else {
                stack.peek().appendChild(element);
            }
            stack.push(element);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            flushText();
            stack.pop();
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            textBuffer.append(ch, start, length);
        }

        private void flushText() {
            if (textBuffer.length() > 0 && !stack.isEmpty()) {
                stack.peek().appendChild(doc.createTextNode(textBuffer.toString()));
                textBuffer.setLength(0);
            }
        }
    }
}
