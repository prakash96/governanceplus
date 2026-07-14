package com.governanceplus.reviewermule.ruleengine.model;

/** Compares dot-separated version strings (e.g. "1.10.2") component-by-component, numerically. */
public class ComparableVersion implements Comparable<ComparableVersion> {
    private final int[] parts;

    public ComparableVersion(String version) {
        if (version == null || version.isEmpty()) {
            parts = new int[]{0};
        } else {
            String[] tokens = version.split("\\.");
            parts = new int[tokens.length];
            for (int i = 0; i < tokens.length; i++) {
                try {
                    parts[i] = Integer.parseInt(tokens[i]);
                } catch (NumberFormatException e) {
                    parts[i] = 0; // fallback for non-numeric parts
                }
            }
        }
    }

    @Override
    public int compareTo(ComparableVersion other) {
        int maxLen = Math.max(this.parts.length, other.parts.length);
        for (int i = 0; i < maxLen; i++) {
            int a = i < this.parts.length ? this.parts[i] : 0;
            int b = i < other.parts.length ? other.parts[i] : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }
}
