package io.github.railgun19457.proxytab.announcement;

import java.time.Instant;

public record Announcement(
    AnnouncementSlot slot,
    AnnouncementMode mode,
    String content,
    Instant updatedAt
) {
}

