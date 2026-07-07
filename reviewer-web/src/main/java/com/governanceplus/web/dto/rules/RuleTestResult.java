package com.governanceplus.web.dto.rules;

import java.util.List;

public class RuleTestResult {

    private final boolean matched;
    private final List<RuleTestViolation> violations;

    public RuleTestResult(boolean matched, List<RuleTestViolation> violations) {
        this.matched = matched;
        this.violations = violations;
    }

    public boolean isMatched() { return matched; }
    public List<RuleTestViolation> getViolations() { return violations; }

    public static class RuleTestViolation {
        private final String message;
        private final int line;

        public RuleTestViolation(String message, int line) {
            this.message = message;
            this.line = line;
        }

        public String getMessage() { return message; }
        public int getLine() { return line; }
    }
}
