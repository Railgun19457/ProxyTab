package io.github.railgun19457.proxytab.storage;

import io.github.railgun19457.proxytab.announcement.AnnouncementSlot;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MemoryPlayerNoticeStateRepository implements PlayerNoticeStateRepository {
    private final Map<UUID, EnumMap<AnnouncementSlot, LocalDate>> seen = new HashMap<>();
    private final Map<UUID, EnumMap<AnnouncementSlot, LocalDate>> closed = new HashMap<>();

    @Override
    public synchronized boolean hasSeenToday(UUID playerId, AnnouncementSlot slot, LocalDate date) {
        return date.equals(readDate(seen, playerId, slot));
    }

    @Override
    public synchronized void markSeenToday(UUID playerId, AnnouncementSlot slot, LocalDate date) {
        writeDate(seen, playerId, slot, date);
    }

    @Override
    public synchronized boolean isClosedToday(UUID playerId, AnnouncementSlot slot, LocalDate date) {
        return date.equals(readDate(closed, playerId, slot));
    }

    @Override
    public synchronized void closeForToday(UUID playerId, AnnouncementSlot slot, LocalDate date) {
        writeDate(closed, playerId, slot, date);
    }

    @Override
    public synchronized void clearSlotState(AnnouncementSlot slot) {
        clearSlot(seen, slot);
        clearSlot(closed, slot);
    }

    private LocalDate readDate(
        Map<UUID, EnumMap<AnnouncementSlot, LocalDate>> source,
        UUID playerId,
        AnnouncementSlot slot
    ) {
        EnumMap<AnnouncementSlot, LocalDate> slots = source.get(playerId);
        return slots == null ? null : slots.get(slot);
    }

    private void writeDate(
        Map<UUID, EnumMap<AnnouncementSlot, LocalDate>> target,
        UUID playerId,
        AnnouncementSlot slot,
        LocalDate date
    ) {
        target.computeIfAbsent(playerId, ignored -> new EnumMap<>(AnnouncementSlot.class)).put(slot, date);
    }

    private void clearSlot(
        Map<UUID, EnumMap<AnnouncementSlot, LocalDate>> target,
        AnnouncementSlot slot
    ) {
        target.entrySet().removeIf(entry -> {
            entry.getValue().remove(slot);
            return entry.getValue().isEmpty();
        });
    }
}

