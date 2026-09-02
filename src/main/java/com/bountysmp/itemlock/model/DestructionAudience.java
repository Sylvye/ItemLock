package com.bountysmp.itemlock.model;

import java.util.Locale;

public enum DestructionAudience {
    OPERATOR,
    PUBLIC;

    public static DestructionAudience parse(String input, DestructionAudience fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public DestructionAudience next() {
        return this == OPERATOR ? PUBLIC : OPERATOR;
    }
}
