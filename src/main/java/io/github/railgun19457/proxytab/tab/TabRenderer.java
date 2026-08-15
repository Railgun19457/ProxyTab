package io.github.railgun19457.proxytab.tab;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import io.github.railgun19457.proxytab.announcement.AnnouncementService;
import io.github.railgun19457.proxytab.config.ProxyTabConfig;
import io.github.railgun19457.proxytab.placeholder.PlaceholderService;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

public final class TabRenderer {
    private final Logger logger;
    private final PlaceholderService placeholderService;
    private final AnnouncementService announcementService;
    private final VirtualTabEntryRegistry virtualEntries;
    private final Set<UUID> managedViewers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<UUID>> managedEntriesByViewer = new ConcurrentHashMap<>();

    public TabRenderer(
        Logger logger,
        PlaceholderService placeholderService,
        AnnouncementService announcementService,
        VirtualTabEntryRegistry virtualEntries
    ) {
        this.logger = logger;
        this.placeholderService = placeholderService;
        this.announcementService = announcementService;
        this.virtualEntries = virtualEntries;
    }

    public void renderAll(ProxyTabConfig config, List<TabPlayerEntry> entries, Collection<Player> viewers) {
        Map<String, Integer> serverOnlineCounts = config.tab().enabled()
            ? placeholderService.serverOnlineCounts()
            : Map.of();
        for (Player viewer : viewers) {
            if (!config.tab().enabled()) {
                release(viewer);
                continue;
            }
            if (isBlacklisted(config, viewer)) {
                suspend(viewer);
                continue;
            }
            render(viewer, config, entries, serverOnlineCounts);
        }
    }

    public void releaseAll(Collection<Player> players) {
        for (Player player : players) {
            release(player);
        }
        managedViewers.clear();
        managedEntriesByViewer.clear();
    }

    public void releaseForBlacklistedServer(Player viewer) {
        release(viewer);
    }

    private boolean isBlacklisted(ProxyTabConfig config, Player viewer) {
        return config.general().blacklistedServers().contains(placeholderService.serverName(viewer));
    }

    private void render(
        Player viewer,
        ProxyTabConfig config,
        List<TabPlayerEntry> entries,
        Map<String, Integer> serverOnlineCounts
    ) {
        try {
            Component header = renderHeader(config, viewer, serverOnlineCounts);
            Component baseFooter = renderFooter(config, viewer, serverOnlineCounts);

            Component footer = announcementService.tabFooterLine(config, viewer, serverOnlineCounts)
                .map(line -> baseFooter.append(Component.newline()).append(line))
                .orElse(baseFooter);

            viewer.sendPlayerListHeaderAndFooter(header, footer);
            rewriteEntries(viewer, entries);
            managedViewers.add(viewer.getUniqueId());
        } catch (RuntimeException exception) {
            logger.error("Failed to render tab list for {}", viewer.getUsername(), exception);
        }
    }

    private void rewriteEntries(Player viewer, List<TabPlayerEntry> entries) {
        TabList tabList = viewer.getTabList();
        UUID viewerId = viewer.getUniqueId();
        Set<UUID> managedEntries = managedEntriesByViewer.get(viewerId);
        if (managedEntries == null) {
            managedEntries = ConcurrentHashMap.newKeySet();
            managedEntriesByViewer.put(viewerId, managedEntries);
        }

        Set<UUID> desiredEntryIds = new HashSet<>();
        for (TabPlayerEntry entry : entries) {
            if (entry.active()) {
                desiredEntryIds.add(entry.uniqueId());
            }
        }

        for (UUID managedEntryId : Set.copyOf(managedEntries)) {
            if (!desiredEntryIds.contains(managedEntryId)) {
                tabList.removeEntry(managedEntryId);
                managedEntries.remove(managedEntryId);
            }
        }

        for (UUID hiddenEntryId : virtualEntries.hiddenEntryIds()) {
            tabList.removeEntry(hiddenEntryId);
            managedEntries.remove(hiddenEntryId);
        }

        for (TabPlayerEntry entry : entries) {
            if (!entry.active()) {
                continue;
            }

            try {
                upsertEntry(tabList, entry);
                managedEntries.add(entry.uniqueId());
            } catch (RuntimeException exception) {
                logger.warn(
                    "Failed to update tab entry {} for viewer {}.",
                    entry.username(),
                    viewer.getUsername(),
                    exception
                );
            }
        }
    }

    private void upsertEntry(TabList tabList, TabPlayerEntry entry) {
        UUID playerId = entry.uniqueId();
        TabListEntry tabEntry = tabList.getEntry(playerId).orElse(null);
        if (tabEntry == null) {
            tabEntry = TabListEntry.builder()
                .tabList(tabList)
                .profile(entry.profile())
                .displayName(entry.displayName())
                .latency(entry.latency())
                .gameMode(entry.gameMode())
                .listed(true)
                .build();
            tabList.addEntry(tabEntry);
            return;
        }

        tabEntry
            .setDisplayName(entry.displayName())
            .setLatency(entry.latency())
            .setGameMode(entry.gameMode())
            .setListed(true);
    }

    private Component renderHeader(
        ProxyTabConfig config,
        Player viewer,
        Map<String, Integer> serverOnlineCounts
    ) {
        if (!config.tab().header().enabled()) {
            return Component.empty();
        }

        return placeholderService.renderViewerText(
            config.tab().header().value(),
            config,
            viewer,
            "tab.header.value",
            serverOnlineCounts
        );
    }

    private Component renderFooter(
        ProxyTabConfig config,
        Player viewer,
        Map<String, Integer> serverOnlineCounts
    ) {
        if (!config.tab().footer().enabled()) {
            return Component.empty();
        }

        return placeholderService.renderViewerText(
            config.tab().footer().value(),
            config,
            viewer,
            "tab.footer.value",
            serverOnlineCounts
        );
    }

    private void release(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        Set<UUID> managedEntries = managedEntriesByViewer.get(viewerId);
        boolean wasManaged = managedViewers.contains(viewerId) || managedEntries != null;
        if (!wasManaged) {
            return;
        }

        try {
            viewer.clearPlayerListHeaderAndFooter();
            removeManagedEntries(viewer.getTabList(), managedEntries);
            managedViewers.remove(viewerId);
            managedEntriesByViewer.remove(viewerId);
        } catch (RuntimeException exception) {
            logger.warn("Failed to release tab control for {}.", viewer.getUsername(), exception);
        }
    }

    private void suspend(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        Set<UUID> managedEntries = managedEntriesByViewer.get(viewerId);
        boolean wasManaged = managedViewers.contains(viewerId) || managedEntries != null;
        if (!wasManaged) {
            return;
        }

        try {
            viewer.clearPlayerListHeaderAndFooter();
            removeManagedEntries(viewer.getTabList(), managedEntries);
            managedViewers.remove(viewerId);
            managedEntriesByViewer.remove(viewerId);
        } catch (RuntimeException exception) {
            logger.warn("Failed to suspend tab control for {}.", viewer.getUsername(), exception);
        }
    }

    private void removeManagedEntries(TabList tabList, Set<UUID> managedEntries) {
        if (managedEntries == null || managedEntries.isEmpty()) {
            return;
        }

        for (UUID managedEntryId : Set.copyOf(managedEntries)) {
            tabList.removeEntry(managedEntryId);
        }
    }
}
