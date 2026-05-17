package io.github.railgun19457.proxytab.tab;

import io.github.railgun19457.proxytab.config.ConfigManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VirtualTabEntryRegistry {
    private final Map<String, Map<UUID, VirtualTabPlayer>> entriesByServer = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> hiddenEntriesByServer = new ConcurrentHashMap<>();

    public void update(String serverName, UUID uniqueId, String username, String textureValue, String textureSignature, int latency, int gameMode) {
        String normalizedServer = ConfigManager.normalizeKey(serverName);
        if (normalizedServer.isBlank() || username == null || username.isBlank()) {
            return;
        }
        Set<UUID> hiddenEntries = hiddenEntriesByServer.get(normalizedServer);
        if (hiddenEntries != null) {
            hiddenEntries.remove(uniqueId);
        }
        entriesByServer
            .computeIfAbsent(normalizedServer, ignored -> new ConcurrentHashMap<>())
            .put(uniqueId, new VirtualTabPlayer(
                normalizedServer,
                uniqueId,
                username,
                textureValue == null ? "" : textureValue,
                textureSignature == null ? "" : textureSignature,
                latency,
                normalizedGameMode(gameMode)
            ));
    }

    private int normalizedGameMode(int gameMode) {
        return gameMode >= 0 && gameMode <= 3 ? gameMode : 0;
    }

    public void hide(String serverName, UUID uniqueId) {
        String normalizedServer = ConfigManager.normalizeKey(serverName);
        if (normalizedServer.isBlank()) {
            return;
        }
        Map<UUID, VirtualTabPlayer> entries = entriesByServer.get(normalizedServer);
        if (entries != null) {
            entries.remove(uniqueId);
        }
        hiddenEntriesByServer
            .computeIfAbsent(normalizedServer, ignored -> ConcurrentHashMap.newKeySet())
            .add(uniqueId);
    }

    public void remove(String serverName, UUID uniqueId) {
        String normalizedServer = ConfigManager.normalizeKey(serverName);
        Map<UUID, VirtualTabPlayer> entries = entriesByServer.get(normalizedServer);
        if (entries == null) {
            Set<UUID> hiddenEntries = hiddenEntriesByServer.get(normalizedServer);
            if (hiddenEntries != null) {
                hiddenEntries.remove(uniqueId);
            }
            return;
        }
        entries.remove(uniqueId);
        Set<UUID> hiddenEntries = hiddenEntriesByServer.get(normalizedServer);
        if (hiddenEntries != null) {
            hiddenEntries.remove(uniqueId);
        }
    }

    public void clearServer(String serverName) {
        String normalizedServer = ConfigManager.normalizeKey(serverName);
        entriesByServer.remove(normalizedServer);
        hiddenEntriesByServer.remove(normalizedServer);
    }

    public List<VirtualTabPlayer> all() {
        return entriesByServer.values()
            .stream()
            .flatMap(entries -> entries.values().stream())
            .toList();
    }

    public Set<UUID> hiddenEntryIds() {
        return hiddenEntriesByServer.values()
            .stream()
            .flatMap(Set::stream)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
