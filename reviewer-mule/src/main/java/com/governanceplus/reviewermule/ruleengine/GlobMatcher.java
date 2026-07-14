package com.governanceplus.reviewermule.ruleengine;

import java.nio.file.FileSystems;
import java.nio.file.Paths;

/**
 * Matches a rule's optional projectNamePattern (e.g. "*-api") against a project's pom.xml
 * artifactId — a self-contained copy of reviewer-core's GlobMatcher (ported, not depended on).
 * Uses java.nio's real glob syntax (*, ?, [...], {...}), replacing an earlier hand-rolled
 * DataWeave glob-to-regex conversion that only handled * and ?.
 *
 * Called from review-engine.xml via DataWeave's native Java interop
 * (`import * from java!com::governanceplus::reviewermule::ruleengine::GlobMatcher`), never the
 * Mule Java Module.
 */
public final class GlobMatcher {

    private GlobMatcher() {
    }

    public static boolean matchGlob(String globPattern, String value) {
        return FileSystems.getDefault().getPathMatcher("glob:" + globPattern).matches(Paths.get(value));
    }
}
