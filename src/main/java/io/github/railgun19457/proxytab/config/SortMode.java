package io.github.railgun19457.proxytab.config;

import java.util.Locale;

public enum SortMode {
    SERVER_THEN_NAME,
    NAME;

    public static SortMode parse(String value) {
        if (value == null || value.isBlank()) {
            return SERVER_THEN_NAME;
        }

        return switch (value.toLowerCase(Locale.ROOT)) {
            case "name" -> NAME;
            case "server_then_name" -> SERVER_THEN_NAME;
            default -> SERVER_THEN_NAME;
        };
    }
}

