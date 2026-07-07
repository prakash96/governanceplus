
package com.governanceplus.reviewer.ruleengine.model;

/**
 * Implemented by every rule type (Rule, PomRule, SwaggerRule) so RuleEngine
 * can filter which rules apply to a given project with one shared method,
 * instead of duplicating the same glob-match logic per rule type.
 */
public interface ProjectScopedRule {

    /**
     * Optional glob pattern (e.g. "*-api") matched against the project's
     * pom.xml artifactId. Null/blank means "applies to every project."
     */
    String getProjectNamePattern();
}
