package io.github.railgun19457.proxytab.storage;

import io.github.railgun19457.proxytab.announcement.AnnouncementSlot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.gson.GsonConfigurationLoader;

public final class JsonPlayerNoticeStateRepository implements PlayerNoticeStateRepository {
    private final Path path;
    private final GsonConfigurationLoader loader;
    private final Map<UUID, EnumMap<AnnouncementSlot, LocalDate>> seen = new HashMap<>();
    private final Map<UUID, EnumMap<AnnouncementSlot, LocalDate>> closed = new HashMap<>();

    public JsonPlayerNoticeStateRepository(Path path) throws IOException {
        this.path = path;
        this.loader = GsonConfigurationLoader.builder()
            .path(path)
            .build();
        loadFromDisk();
    }

    @Override
    public synchronized boolean hasSeenToday(UUID playerId, AnnouncementSlot slot, LocalDate date) {
        return date.equals(readDate(seen, playerId, slot));
    }

    @Override
    public synchronized void markSeenToday(UUID playerId, AnnouncementSlot slot, LocalDate date) throws IOException {
        writeDate(seen, playerId, slot, date);
        saveToDisk();
    }

    @Override
    public synchronized boolean isClosedToday(UUID playerId, AnnouncementSlot slot, LocalDate date) {
        return date.equals(readDate(closed, playerId, slot));
    }

    @Override
    public synchronized void closeForToday(UUID playerId, AnnouncementSlot slot, LocalDate date) throws IOException {
        writeDate(closed, playerId, slot, date);
        saveToDisk();
    }

    @Override
    public synchronized void clearSlotState(AnnouncementSlot slot) throws IOException {
        clearSlot(seen, slot);
        clearSlot(closed, slot);
        saveToDisk();
    }

    private void loadFromDisk() throws IOException {
        seen.clear();
        closed.clear();
        if (!Files.exists(path)) {
            ensureParentDirectory();
            return;
        }

        try {
            ConfigurationNode root = loader.load();
            readSection(root.node("seen"), seen);
            readSection(root.node("closed"), closed);
        } catch (ConfigurateException exception) {
            throw new IOException("Failed to load player notice state JSON: " + path, exception);
        }
    }

    private void readSection(
        ConfigurationNode root,
        Map<UUID, EnumMap<AnnouncementSlot, LocalDate>> target
    ) {
        for (Map.Entry<Object, ? extends ConfigurationNode> playerEntry : root.childrenMap().entrySet()) {
            UUID playerId;
            try {
                playerId = UUID.fromString(String.valueOf(playerEntry.getKey()));
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            EnumMap<AnnouncementSlot, LocalDate> slots = new EnumMap<>(AnnouncementSlot.class);
            for (AnnouncementSlot slot : AnnouncementSlot.values()) {
                String rawDate = playerEntry.getValue().node(slot.key()).getString();
                if (rawDate == null || rawDate.isBlank()) {
                    continue;
                }

                try {
                    slots.put(slot, LocalDate.parse(rawDate));
                } catch (RuntimeException ignored) {
                    // Bad persisted dates are ignored so one corrupt entry does not disable the plugin.
                }
            }

            if (!slots.isEmpty()) {
                target.put(playerId, slots);
            }
        }
    }

    private void saveToDisk() throws IOException {
        ensureParentDirectory();
        try {
            ConfigurationNode root = loader.createNode();
            writeSection(root.node("seen"), seen);
            writeSection(root.node("closed"), closed);
            loader.save(root);
        } catch (ConfigurateException exception) {
            throw new IOException("Failed to save player notice state JSON: " + path, exception);
        }
    }

    private void writeSection(
        ConfigurationNode root,
        Map<UUID, EnumMap<AnnouncementSlot, LocalDate>> source
    ) throws ConfigurateException {
        for (Map.Entry<UUID, EnumMap<AnnouncementSlot, LocalDate>> playerEntry : source.entrySet()) {
            for (Map.Entry<AnnouncementSlot, LocalDate> slotEntry : playerEntry.getValue().entrySet()) {
                root.node(playerEntry.getKey().toString(), slotEntry.getKey().key())
                    .set(slotEntry.getValue().toString());
            }
        }
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

    private void ensureParentDirectory() throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}

