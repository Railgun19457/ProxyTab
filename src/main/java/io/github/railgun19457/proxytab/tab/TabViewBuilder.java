package io.github.railgun19457.proxytab.tab;

import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.proxytab.config.ProxyTabConfig;
import io.github.railgun19457.proxytab.config.SortMode;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class TabViewBuilder {
    private final ProxyServer server;
    private final TabEntryFactory entryFactory;

    public TabViewBuilder(ProxyServer server, TabEntryFactory entryFactory) {
        this.server = server;
        this.entryFactory = entryFactory;
    }

    public List<TabPlayerEntry> build(ProxyTabConfig config) {
        Comparator<TabPlayerEntry> comparator = config.tab().sortMode() == SortMode.NAME
            ? Comparator.comparing(entry -> entry.player().getUsername(), String.CASE_INSENSITIVE_ORDER)
            : Comparator
                .comparingInt((TabPlayerEntry entry) -> groupIndex(config, entry.serverName()))
                .thenComparing(TabPlayerEntry::serverName)
                .thenComparing(entry -> entry.player().getUsername(), String.CASE_INSENSITIVE_ORDER);

        return server.getAllPlayers().stream()
            .filter(player -> !isIgnored(config, player.getUsername()))
            .map(player -> entryFactory.create(player, config))
            .sorted(comparator)
            .toList();
    }

    private boolean isIgnored(ProxyTabConfig config, String username) {
        for (Pattern pattern : config.general().ignorePatterns()) {
            if (pattern.matcher(username).find()) {
                return true;
            }
        }
        return false;
    }

    private int groupIndex(ProxyTabConfig config, String serverName) {
        int index = config.servers().groupOrder().indexOf(serverName);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }
}

