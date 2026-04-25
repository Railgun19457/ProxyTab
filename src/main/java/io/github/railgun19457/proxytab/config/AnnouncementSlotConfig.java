package io.github.railgun19457.proxytab.config;

public record AnnouncementSlotConfig(
    boolean enabled,
    String format,
    CloseButtonConfig closeButton
) {
}

