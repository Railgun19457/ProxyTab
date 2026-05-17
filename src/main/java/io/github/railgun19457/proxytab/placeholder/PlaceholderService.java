package io.github.railgun19457.proxytab.placeholder;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.proxytab.config.ConfigManager;
import io.github.railgun19457.proxytab.config.ProxyTabConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.slf4j.Logger;

public final class PlaceholderService {
    private final ProxyServer server;
    private final MiniMessage miniMessage;
    private final Logger logger;

    public PlaceholderService(ProxyServer server, MiniMessage miniMessage, Logger logger) {
        this.server = server;
        this.miniMessage = miniMessage;
        this.logger = logger;
    }

    public Component renderViewerText(String raw, ProxyTabConfig config, Player viewer, String fieldName) {
        return renderViewerText(raw, config, viewer, fieldName, TagResolver.empty(), null);
    }

    public Component renderViewerText(
        String raw,
        ProxyTabConfig config,
        Player viewer,
        String fieldName,
        TagResolver extraResolver
    ) {
        return renderViewerText(raw, config, viewer, fieldName, extraResolver, null);
    }

    public Component renderViewerText(
        String raw,
        ProxyTabConfig config,
        Player viewer,
        String fieldName,
        Map<String, Integer> serverOnlineCounts
    ) {
        return renderViewerText(raw, config, viewer, fieldName, TagResolver.empty(), serverOnlineCounts);
    }

    public Component renderViewerText(
        String raw,
        ProxyTabConfig config,
        Player viewer,
        String fieldName,
        TagResolver extraResolver,
        Map<String, Integer> serverOnlineCounts
    ) {
        return deserialize(raw, fieldName, TagResolver.resolver(
            globalResolver(config),
            viewerResolver(config, viewer, serverOnlineCounts),
            extraResolver,
            CustomTagResolvers.all()
        ));
    }

    public Component renderPlayerText(String raw, ProxyTabConfig config, Player target, String fieldName) {
        return renderPlayerText(raw, config, target.getUsername(), serverName(target), target.getPing(), fieldName);
    }

    public Component renderPlayerText(String raw, ProxyTabConfig config, String targetName, String targetServer, long targetPing, String fieldName) {
        return deserialize(raw, fieldName, TagResolver.resolver(
            globalResolver(config),
            playerResolver(config, targetName, targetServer, targetPing),
            CustomTagResolvers.all()
        ));
    }

    public String serverName(Player player) {
        return player.getCurrentServer()
            .map(connection -> connection.getServerInfo().getName())
            .map(ConfigManager::normalizeKey)
            .orElse("default");
    }

    public Component serverDisplayName(ProxyTabConfig config, String serverName) {
        String key = ConfigManager.normalizeKey(serverName);
        return config.servers().mapping().getOrDefault(key, config.general().defaultServerName());
    }

    public int serverOnline(String serverName) {
        return serverOnline(serverName, null);
    }

    public Map<String, Integer> serverOnlineCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (Player player : server.getAllPlayers()) {
            counts.merge(serverName(player), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private int serverOnline(String serverName, Map<String, Integer> serverOnlineCounts) {
        String key = ConfigManager.normalizeKey(serverName);
        if (serverOnlineCounts != null) {
            return serverOnlineCounts.getOrDefault(key, 0);
        }
        return (int) server.getAllPlayers().stream()
            .filter(player -> Objects.equals(serverName(player), key))
            .count();
    }

    private Component deserialize(String raw, String fieldName, TagResolver resolver) {
        String value = raw == null ? "" : raw;
        try {
            return miniMessage.deserialize(value, resolver);
        } catch (RuntimeException exception) {
            logger.error("Failed to render MiniMessage field {}: {}", fieldName, value, exception);
            return Component.text(value);
        }
    }

    private TagResolver globalResolver(ProxyTabConfig config) {
        return TagResolver.resolver(
            Placeholder.component("network_id", config.general().networkId()),
            Placeholder.unparsed("online", Integer.toString(server.getAllPlayers().size()))
        );
    }

    private TagResolver viewerResolver(
        ProxyTabConfig config,
        Player viewer,
        Map<String, Integer> serverOnlineCounts
    ) {
        String currentServer = serverName(viewer);
        return TagResolver.resolver(
            Placeholder.component("current_server", serverDisplayName(config, currentServer)),
            Placeholder.unparsed("server_online", Integer.toString(serverOnline(currentServer, serverOnlineCounts))),
            Placeholder.unparsed("ping", Long.toString(viewer.getPing()))
        );
    }

    private TagResolver playerResolver(ProxyTabConfig config, String targetName, String targetServer, long targetPing) {
        return TagResolver.resolver(
            Placeholder.unparsed("player_name", targetName),
            Placeholder.component("player_server", serverDisplayName(config, targetServer)),
            Placeholder.unparsed("ping", Long.toString(targetPing)),
            Placeholder.unparsed("player_ping", Long.toString(targetPing))
        );
    }
}

