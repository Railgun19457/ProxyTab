package io.github.railgun19457.proxytab.announcement;

import java.util.Locale;

public enum AnnouncementMode {
    ALWAYS("always"),
    ONCE_PER_DAY("once_per_day");

    private final String key;

    AnnouncementMode(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static AnnouncementMode parse(String value, AnnouncementMode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        return switch (value.toLowerCase(Locale.ROOT)) {
            case "always" -> ALWAYS;
            case "once_per_day" -> ONCE_PER_DAY;
            default -> fallback;
        };
    }
}

