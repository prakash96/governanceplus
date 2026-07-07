
package com.governanceplus.reviewer.ruleengine;

import java.nio.file.FileSystems;
import java.nio.file.Paths;

/** Matches a rule's optional projectNamePattern (e.g. "*-api") against a project's pom.xml artifactId. */
public final class GlobMatcher {

    private GlobMatcher() {
    }

    public static boolean matches(String globPattern, String value) {
        return FileSystems.getDefault().getPathMatcher("glob:" + globPattern).matches(Paths.get(value));
    }
}
