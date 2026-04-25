package io.github.railgun19457.proxytab.storage;

import io.github.railgun19457.proxytab.announcement.Announcement;
import io.github.railgun19457.proxytab.announcement.AnnouncementMode;
import io.github.railgun19457.proxytab.announcement.AnnouncementSlot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.gson.GsonConfigurationLoader;

public final class JsonAnnouncementRepository implements AnnouncementRepository {
    private final Path path;
    private final GsonConfigurationLoader loader;
    private final EnumMap<AnnouncementSlot, Announcement> cache = new EnumMap<>(AnnouncementSlot.class);

    public JsonAnnouncementRepository(Path path) throws IOException {
        this.path = path;
        this.loader = GsonConfigurationLoader.builder()
            .path(path)
            .build();
        loadFromDisk();
    }

    @Override
    public synchronized Map<AnnouncementSlot, Announcement> loadAll() {
        return Map.copyOf(cache);
    }

    @Override
    public synchronized void save(AnnouncementSlot slot, Announcement announcement) throws IOException {
        cache.put(slot, announcement);
        saveToDisk();
    }

    @Override
    public synchronized void delete(AnnouncementSlot slot) throws IOException {
        cache.remove(slot);
        saveToDisk();
    }

    private void loadFromDisk() throws IOException {
        cache.clear();
        if (!Files.exists(path)) {
            ensureParentDirectory();
            return;
        }

        try {
            ConfigurationNode root = loader.load();
            for (AnnouncementSlot slot : AnnouncementSlot.values()) {
                ConfigurationNode node = root.node(slot.key());
                String content = node.node("content").getString();
                if (content == null || content.isBlank()) {
                    continue;
                }

                AnnouncementMode mode = AnnouncementMode.parse(
                    node.node("mode").getString(),
                    slot == AnnouncementSlot.CHAT ? AnnouncementMode.ONCE_PER_DAY : AnnouncementMode.ALWAYS
                );
                String updatedAtRaw = node.node("updated-at").getString();
                Instant updatedAt = parseInstant(updatedAtRaw);
                cache.put(slot, new Announcement(slot, mode, content, updatedAt));
            }
        } catch (ConfigurateException exception) {
            throw new IOException("Failed to load announcements JSON: " + path, exception);
        }
    }

    private void saveToDisk() throws IOException {
        ensureParentDirectory();
        try {
            ConfigurationNode root = loader.createNode();
            for (Announcement announcement : cache.values()) {
                ConfigurationNode node = root.node(announcement.slot().key());
                node.node("mode").set(announcement.mode().key());
                node.node("content").set(announcement.content());
                node.node("updated-at").set(announcement.updatedAt().toString());
            }
            loader.save(root);
        } catch (ConfigurateException exception) {
            throw new IOException("Failed to save announcements JSON: " + path, exception);
        }
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }

        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.now();
        }
    }

    private void ensureParentDirectory() throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}

