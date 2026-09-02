package com.bountysmp.itemlock.model;

import java.util.Locale;

public enum MatchType {
    MATERIAL,
    EXACT;

    public static MatchType parse(String input, MatchType fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public MatchType next() {
        return this == MATERIAL ? EXACT : MATERIAL;
    }
}
