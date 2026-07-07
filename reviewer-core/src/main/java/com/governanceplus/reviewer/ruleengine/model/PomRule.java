package com.governanceplus.reviewer.ruleengine.model;

public class PomRule implements ProjectScopedRule {
	String artifactId;
	String minVersion;
	String projectNamePattern;

	public PomRule(String artifactId, String minVersion) {
		this(artifactId, minVersion, null);
	}

	public PomRule(String artifactId, String minVersion, String projectNamePattern) {
		this.artifactId = artifactId;
		this.minVersion = minVersion;
		this.projectNamePattern = projectNamePattern;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}

	public String getMinVersion() {
		return minVersion;
	}

	public void setMinVersion(String minVersion) {
		this.minVersion = minVersion;
	}

	public String getProjectNamePattern() {
		return projectNamePattern;
	}

	public void setProjectNamePattern(String projectNamePattern) {
		this.projectNamePattern = projectNamePattern;
	}

}
