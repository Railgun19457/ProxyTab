package io.github.railgun19457.proxytab.storage;

import io.github.railgun19457.proxytab.announcement.Announcement;
import io.github.railgun19457.proxytab.announcement.AnnouncementSlot;
import java.util.EnumMap;
import java.util.Map;

public final class MemoryAnnouncementRepository implements AnnouncementRepository {
    private final EnumMap<AnnouncementSlot, Announcement> cache = new EnumMap<>(AnnouncementSlot.class);

    @Override
    public synchronized Map<AnnouncementSlot, Announcement> loadAll() {
        return Map.copyOf(cache);
    }

    @Override
    public synchronized void save(AnnouncementSlot slot, Announcement announcement) {
        cache.put(slot, announcement);
    }

    @Override
    public synchronized void delete(AnnouncementSlot slot) {
        cache.remove(slot);
    }
}

