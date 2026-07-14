package com.governanceplus.reviewermule.ruleengine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.governanceplus.reviewermule.ruleengine.model.ComparableVersion;
import com.governanceplus.reviewermule.ruleengine.model.Violation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates pom.xml dependency versions against minimum-version rules. Parses NAMESPACE-UNAWARE
 * (real Maven pom.xml files declare a default xmlns) so plain XPath like //dependency and
 * //properties/* match without needing namespace registration.
 *
 * Called from Mule flows via DataWeave's native Java interop
 * (`import * from java!com::governanceplus::reviewermule::ruleengine::PomDependencyValidator`),
 * never the Mule Java Module.
 */
public class PomDependencyValidator {

    /**
     * Real-review bridge (review-engine.xml): evaluates every applicable pom rule (already filtered
     * by projectNamePattern in DataWeave) against the project's pom.xml content. pomRulesJson is a
     * JSON array of {artifactId, minVersion} objects.
     */
    public static String validatePomRulesAsJson(String pomContent, String pomRulesJson) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, String>> ruleMaps = mapper.readValue(pomRulesJson, new TypeReference<List<Map<String, String>>>() {});
        Map<String, String> minVersionByArtifact = new HashMap<>();
        for (Map<String, String> rule : ruleMaps) {
            minVersionByArtifact.put(rule.get("artifactId"), rule.get("minVersion"));
        }

        Document doc = loadXml(pomContent);
        XPath xpath = XPathFactory.newInstance().newXPath();
        Map<String, String> properties = loadProperties(doc, xpath);

        List<Violation> violations = new ArrayList<>();
        NodeList deps = (NodeList) xpath.evaluate("//dependency", doc, XPathConstants.NODESET);
        for (int i = 0; i < deps.getLength(); i++) {
            Element dep = (Element) deps.item(i);
            String artifactId = getChildText(dep, "artifactId");
            if (artifactId == null || !minVersionByArtifact.containsKey(artifactId)) {
                continue;
            }
            String minVersion = minVersionByArtifact.get(artifactId);
            addViolationIfBelowMinVersion(violations, dep, artifactId, minVersion, properties);
        }

        return mapper.writeValueAsString(violations);
    }

    /** "Test this rule" bridge (rules-test.xml) — one ad-hoc rule against one pasted sample pom.xml. */
    public static String validateSample(String artifactId, String minVersion, String samplePom) throws Exception {
        Document doc = loadXml(samplePom);
        XPath xpath = XPathFactory.newInstance().newXPath();
        Map<String, String> properties = loadProperties(doc, xpath);

        List<Violation> violations = new ArrayList<>();
        NodeList deps = (NodeList) xpath.evaluate("//dependency", doc, XPathConstants.NODESET);
        for (int i = 0; i < deps.getLength(); i++) {
            Element dep = (Element) deps.item(i);
            if (!artifactId.equals(getChildText(dep, "artifactId"))) {
                continue;
            }
            addViolationIfBelowMinVersion(violations, dep, artifactId, minVersion, properties);
        }

        return new ObjectMapper().writeValueAsString(violations);
    }

    private static void addViolationIfBelowMinVersion(List<Violation> violations, Element dep, String artifactId,
            String minVersion, Map<String, String> properties) {
        String version = getChildText(dep, "version");
        if (version == null) {
            violations.add(new Violation(artifactId, "required min version: " + minVersion, "pom.xml", -1));
            return;
        }

        version = resolveProperty(version, properties);
        if (version == null) {
            return;
        }

        if (new ComparableVersion(version).compareTo(new ComparableVersion(minVersion)) < 0) {
            violations.add(new Violation(artifactId, "required min version: " + minVersion, "pom.xml", -1));
        }
    }

    private static Document loadXml(String content) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(content)));
    }

    private static Map<String, String> loadProperties(Document doc, XPath xpath) throws Exception {
        Map<String, String> props = new HashMap<>();
        NodeList nodes = (NodeList) xpath.evaluate("//properties/*", doc, XPathConstants.NODESET);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            props.put(node.getNodeName(), node.getTextContent().trim());
        }
        return props;
    }

    private static String resolveProperty(String version, Map<String, String> props) {
        if (version.startsWith("${") && version.endsWith("}")) {
            String key = version.substring(2, version.length() - 1);
            return props.get(key);
        }
        return version;
    }

    private static String getChildText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        return list.item(0).getTextContent().trim();
    }
}
