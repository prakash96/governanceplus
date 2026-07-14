package com.governanceplus.reviewer.ruleengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PomDependencyValidatorTest {

    @Test
    void validateSampleBridgeDetectsVersionBelowMinimum() throws Exception {
        String samplePom = "<project><dependencies>"
                + "<dependency><groupId>g</groupId><artifactId>mule-http-connector</artifactId><version>1.0.0</version></dependency>"
                + "</dependencies></project>";

        String json = PomDependencyValidator.validateSample("mule-http-connector", "1.3.0", samplePom);

        assertTrue(json.contains("\"message\""));
        assertTrue(json.contains("1.3.0"));
    }

    @Test
    void validateSampleBridgeReportsNoViolationWhenVersionIsSufficient() throws Exception {
        String samplePom = "<project><dependencies>"
                + "<dependency><groupId>g</groupId><artifactId>mule-http-connector</artifactId><version>1.5.0</version></dependency>"
                + "</dependencies></project>";

        String json = PomDependencyValidator.validateSample("mule-http-connector", "1.3.0", samplePom);

        assertEquals("[]", json);
    }
}
