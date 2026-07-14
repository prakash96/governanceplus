package com.governanceplus.reviewer.ruleengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XPathEvaluatorTest {

    @Test
    void evaluateXmlSampleBridgeReportsMatch() throws Exception {
        String json = XPathEvaluator.evaluateXmlSample("//foo", null, null, "<root><foo/></root>");

        assertTrue(json.startsWith("["));
        assertTrue(json.contains("\"ruleId\":\"TEST\""));
    }

    @Test
    void evaluateXmlSampleBridgeReportsNoMatch() throws Exception {
        String json = XPathEvaluator.evaluateXmlSample("//bar", null, null, "<root><foo/></root>");

        assertEquals("[]", json);
    }
}
