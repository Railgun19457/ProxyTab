package io.github.railgun19457.proxytab.storage;

import io.github.railgun19457.proxytab.announcement.Announcement;
import io.github.railgun19457.proxytab.announcement.AnnouncementSlot;
import java.io.IOException;
import java.util.Map;

public interface AnnouncementRepository {
    Map<AnnouncementSlot, Announcement> loadAll() throws IOException;

    void save(AnnouncementSlot slot, Announcement announcement) throws IOException;

    void delete(AnnouncementSlot slot) throws IOException;
}

