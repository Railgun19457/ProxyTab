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
    private final VirtualTabEntryRegistry virtualEntries;

    public TabViewBuilder(ProxyServer server, TabEntryFactory entryFactory, VirtualTabEntryRegistry virtualEntries) {
        this.server = server;
        this.entryFactory = entryFactory;
        this.virtualEntries = virtualEntries;
    }

    public List<TabPlayerEntry> build(ProxyTabConfig config) {
        Comparator<TabPlayerEntry> comparator = config.tab().sortMode() == SortMode.NAME
            ? Comparator.comparing(TabPlayerEntry::username, String.CASE_INSENSITIVE_ORDER)
            : Comparator
                .comparingInt((TabPlayerEntry entry) -> groupIndex(config, entry.serverName()))
                .thenComparing(TabPlayerEntry::serverName)
                .thenComparing(TabPlayerEntry::username, String.CASE_INSENSITIVE_ORDER);

        List<TabPlayerEntry> realEntries = server.getAllPlayers().stream()
            .filter(player -> !isIgnored(config, player.getUsername()))
            .map(player -> entryFactory.create(player, config))
            .toList();
        List<TabPlayerEntry> dummyEntries = virtualEntries.all().stream()
            .filter(player -> !isIgnored(config, player.username()))
            .map(player -> entryFactory.create(player, config))
            .toList();

        return java.util.stream.Stream.concat(realEntries.stream(), dummyEntries.stream())
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

