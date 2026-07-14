package com.governanceplus.reviewer.ruleengine;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.governanceplus.reviewer.ruleengine.model.ComparableVersion;
import com.governanceplus.reviewer.ruleengine.model.PomRule;
import com.governanceplus.reviewer.ruleengine.model.Violation;

public class PomDependencyValidator {

    /**
     * DataWeave-callable convenience wrapper (`import * from java!...PomDependencyValidator` in a
     * Mule flow) — see {@link com.governanceplus.reviewer.ruleengine.XPathEvaluator#evaluateXmlSample}
     * for why this takes/returns plain strings instead of File/PomRule/List&lt;Violation&gt;.
     */
    public static String validateSample(String artifactId, String minVersion, String samplePom) throws Exception {
        Path tempFile = Files.createTempFile("rule-test-", ".xml");
        try {
            Files.writeString(tempFile, samplePom);
            File file = tempFile.toFile();
            PomRule rule = new PomRule(artifactId, minVersion);
            List<Violation> violations = validate(file, List.of(rule));
            return new ObjectMapper().writeValueAsString(violations);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    // ✅ Load XML
    private static Document loadXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(file);
    }

    /**
     * The project's own &lt;artifactId&gt; — a direct child of the &lt;project&gt; root,
     * not {@code //artifactId} (which would also match &lt;parent&gt; and every
     * &lt;dependency&gt;). Used to match rules' optional projectNamePattern.
     * Returns null if the pom has no top-level artifactId.
     */
    public static String readProjectArtifactId(File file) throws Exception {
        Document doc = loadXml(file);
        NodeList children = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && "artifactId".equals(child.getNodeName())) {
                return child.getTextContent().trim();
            }
        }
        return null;
    }

    // ✅ Validation logic
    public static List<Violation> validate(File file, List<PomRule> rules) throws Exception {

    	List<Violation> violations = new ArrayList<Violation>();
    	Document doc = loadXml(file);
        XPath xpath = XPathFactory.newInstance().newXPath();

        Map<String, String> properties = loadProperties(doc, xpath);

        // Convert rules to map
        Map<String, PomRule> ruleMap = new HashMap<>();
        for (PomRule r : rules) {
            ruleMap.put(r.getArtifactId(), r);
        }

        NodeList deps = (NodeList) xpath.evaluate(
                "//dependency",
                doc,
                XPathConstants.NODESET
        );

        for (int i = 0; i < deps.getLength(); i++) {

            Element dep = (Element) deps.item(i);

            String artifactId = getChildText(dep, "artifactId");
            String version = getChildText(dep, "version");

            if (artifactId == null) continue;

            PomRule rule = ruleMap.get(artifactId);
            if (rule == null) continue;

            // ❌ Missing version
            if (version == null) {
            	violations.add(new Violation(artifactId, "required min version: " + rule.getMinVersion(), "pom.xml", -1));
            	continue;
            }

            // Resolve ${property}
            version = resolveProperty(version, properties);

            if (version == null) {
                System.out.println("❌ Unable to resolve version for " + artifactId);
                continue;
            }

            ComparableVersion current = new ComparableVersion(version);
            ComparableVersion min = new ComparableVersion(rule.getMinVersion());

            if (current.compareTo(min) < 0) {
            	violations.add(new Violation(artifactId, "required min version: " + rule.getMinVersion(), "pom.xml", -1));

            } else {
                System.out.println("✅ OK: " + artifactId + " (" + version + ")");
            }
        }
        
        return violations;
    }

    // ✅ Load properties
    private static Map<String, String> loadProperties(Document doc, XPath xpath) throws Exception {

        Map<String, String> props = new HashMap<>();

        NodeList nodes = (NodeList) xpath.evaluate(
                "//properties/*",
                doc,
                XPathConstants.NODESET
        );

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            props.put(node.getNodeName(), node.getTextContent().trim());
        }

        return props;
    }

    // ✅ Resolve ${...}
    private static String resolveProperty(String version, Map<String, String> props) {

        if (version.startsWith("${") && version.endsWith("}")) {
            String key = version.substring(2, version.length() - 1);
            return props.get(key);
        }
        return version;
    }

    // ✅ Get child text
    private static String getChildText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent().trim();
    }
}