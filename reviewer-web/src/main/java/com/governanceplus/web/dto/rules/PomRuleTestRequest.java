package com.governanceplus.web.dto.rules;

/** "Test on sample" request for a pom rule — samplePom should be a full, valid pom.xml. */
public class PomRuleTestRequest {

    private String artifactId;
    private String minVersion;
    private String samplePom;

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public String getMinVersion() { return minVersion; }
    public void setMinVersion(String minVersion) { this.minVersion = minVersion; }

    public String getSamplePom() { return samplePom; }
    public void setSamplePom(String samplePom) { this.samplePom = samplePom; }
}
