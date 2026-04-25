package io.github.railgun19457.proxytab.announcement;

import java.util.Locale;
import java.util.Optional;

public enum AnnouncementSlot {
    CHAT("chat"),
    TAB("tab");

    private final String key;

    AnnouncementSlot(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static Optional<AnnouncementSlot> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        return switch (value.toLowerCase(Locale.ROOT)) {
            case "chat" -> Optional.of(CHAT);
            case "tab" -> Optional.of(TAB);
            default -> Optional.empty();
        };
    }
}

