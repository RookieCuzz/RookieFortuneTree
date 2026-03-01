package com.cuzz.rookiefortunetree.util;

public final class MenuTitleUtil {
    private MenuTitleUtil() {
    }

    public static String toJson(String title) {
        return "{\"text\":\"" + escape(title) + "\"}";
    }

    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

