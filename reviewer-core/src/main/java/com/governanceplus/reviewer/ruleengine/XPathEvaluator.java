package com.governanceplus.reviewer.ruleengine;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

import javax.xml.parsers.DocumentBuilderFactory;

import com.governanceplus.reviewer.ruleengine.model.Rule;
import com.governanceplus.reviewer.ruleengine.model.Violation;
import com.ximpleware.*;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XPathEvaluator {

    private final Map<File, String> fileContents = new HashMap<>();

    public List<Violation> evaluate(File xmlFile, List<Rule> rules, List<File> allMuleFiles) throws Exception {
        List<Violation> violations = new ArrayList<>();

        // Read current file content
        String xmlContent = Files.readString(xmlFile.toPath());

        // Preload all Mule files for usage checks
        for (File f : allMuleFiles) {
            if (!fileContents.containsKey(f)) {
                fileContents.put(f, Files.readString(f.toPath()));
            }
        }

        // Parse XML with DOM
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document domDoc = factory.newDocumentBuilder().parse(xmlFile);

        // Extract all namespaces recursively
        Map<String, String> namespaces = extractAllNamespaces(domDoc.getDocumentElement());

        // Parse XML with VTD-XML
        VTDGen vg = new VTDGen();
        vg.setDoc(xmlContent.getBytes());
        vg.parse(true);
        VTDNav vn = vg.getNav();

        System.out.println("Scanning file: " + xmlFile.getCanonicalPath());

        // Evaluate each rule
        for (Rule rule : rules) {
            try {
                AutoPilot ap = new AutoPilot(vn);
                vn.toElement(VTDNav.ROOT);

                // Dynamically register all namespaces
                for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                    ap.declareXPathNameSpace(entry.getKey(), entry.getValue());
                }

                // Use original XPath from rule
                ap.selectXPath(rule.getXpath());

                int result;
                while ((result = ap.evalXPath()) != -1) {

                    // Calculate line number
                    int offset = vn.getTokenOffset(vn.getCurrentIndex());
                    int line = getLineNumber(xmlContent, offset);

                    // Extract variableName attribute if present
                    
                    // Handle usagePattern
                    if (rule.getUsagePattern() != null && !rule.getUsagePattern().isEmpty()) {
                    	String variableName = null;
                        int attrIndex = vn.getAttrVal(rule.getUsageAttribute());
                        if (attrIndex != -1) {
                            variableName = vn.toString(attrIndex);
                        }

                        if (variableName == null) continue;

                        String usageSearch = rule.getUsagePattern().replace("${" + rule.getUsageAttribute() + "}", variableName);

                        boolean isUsed = fileContents.values().stream()
                                .anyMatch(content -> content.contains(usageSearch));

                        if (!isUsed) {
                            violations.add(new Violation(
                                    rule.getId(),
                                    rule.getDescription() + ": " + variableName,
                                    xmlFile.getName(),
                                    line
                            ));
                        }

                    } else {
                        violations.add(new Violation(
                                rule.getId(),
                                rule.getDescription(),
                                xmlFile.getName(),
                                line
                        ));
                    }
                }

            } catch (Exception e) {
                System.err.println("Error processing rule: " + rule.getId());
                e.printStackTrace();
            }
        }

        return violations;
    }

    // Recursively extract all namespace declarations in the XML tree
    private Map<String, String> extractAllNamespaces(Element element) {
        Map<String, String> namespaces = new HashMap<>();
        extractNamespacesRecursive(element, namespaces);
        return namespaces;
    }

    private void extractNamespacesRecursive(Element element, Map<String, String> namespaces) {
        NamedNodeMap attrs = element.getAttributes();

        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            String name = attr.getNodeName();
            String value = attr.getNodeValue();

            if (name.startsWith("xmlns:")) {
                String prefix = name.substring(6);
                namespaces.put(prefix, value);
            }
        }

        // Recurse into child elements
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                extractNamespacesRecursive((Element) child, namespaces);
            }
        }
    }

    // Convert byte offset to line number
    private int getLineNumber(String xmlContent, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < xmlContent.length(); i++) {
            if (xmlContent.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}