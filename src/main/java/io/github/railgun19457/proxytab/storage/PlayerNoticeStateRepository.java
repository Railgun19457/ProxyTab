package io.github.railgun19457.proxytab.storage;

import io.github.railgun19457.proxytab.announcement.AnnouncementSlot;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

public interface PlayerNoticeStateRepository {
    boolean hasSeenToday(UUID playerId, AnnouncementSlot slot, LocalDate date) throws IOException;

    void markSeenToday(UUID playerId, AnnouncementSlot slot, LocalDate date) throws IOException;

    boolean isClosedToday(UUID playerId, AnnouncementSlot slot, LocalDate date) throws IOException;

    void closeForToday(UUID playerId, AnnouncementSlot slot, LocalDate date) throws IOException;

    void clearSlotState(AnnouncementSlot slot) throws IOException;
}

