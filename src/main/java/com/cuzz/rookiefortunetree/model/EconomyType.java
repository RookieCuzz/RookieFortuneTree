package com.cuzz.rookiefortunetree.model;

import java.util.Locale;

public enum EconomyType {
    PLAYERPOINTS,
    COMMAND,
    VAULT;

    public static EconomyType fromString(String value) {
        if (value == null || value.isBlank()) {
            return PLAYERPOINTS;
        }
        try {
            return EconomyType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PLAYERPOINTS;
        }
    }
}
