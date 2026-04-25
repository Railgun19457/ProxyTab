package io.github.railgun19457.proxytab.tab;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.TabList;
import com.velocitypowered.api.proxy.player.TabListEntry;
import io.github.railgun19457.proxytab.announcement.AnnouncementService;
import io.github.railgun19457.proxytab.config.ProxyTabConfig;
import io.github.railgun19457.proxytab.placeholder.PlaceholderService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

public final class TabRenderer {
    private final Logger logger;
    private final PlaceholderService placeholderService;
    private final AnnouncementService announcementService;
    private final Set<UUID> managedViewers = ConcurrentHashMap.newKeySet();

    public TabRenderer(
        Logger logger,
        PlaceholderService placeholderService,
        AnnouncementService announcementService
    ) {
        this.logger = logger;
        this.placeholderService = placeholderService;
        this.announcementService = announcementService;
    }

    public void renderAll(ProxyTabConfig config, List<TabPlayerEntry> entries, Collection<Player> viewers) {
        for (Player viewer : viewers) {
            if (!config.tab().enabled()) {
                release(viewer);
                continue;
            }
            if (isBlacklisted(config, viewer)) {
                suspend(viewer);
                continue;
            }
            render(viewer, config, entries);
        }
    }

    public void releaseAll(Collection<Player> players) {
        for (Player player : players) {
            release(player);
        }
        managedViewers.clear();
    }

    public void releaseBeforeServerSwitch(Player viewer) {
        release(viewer);
    }

    private boolean isBlacklisted(ProxyTabConfig config, Player viewer) {
        return config.general().blacklistedServers().contains(placeholderService.serverName(viewer));
    }

    private void render(Player viewer, ProxyTabConfig config, List<TabPlayerEntry> entries) {
        try {
            Component header = renderHeader(config, viewer);
            Component baseFooter = renderFooter(config, viewer);

            Component footer = announcementService.tabFooterLine(config, viewer)
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
        tabList.clearAll();

        for (TabPlayerEntry entry : entries) {
            if (!entry.player().isActive()) {
                continue;
            }

            try {
                TabListEntry tabEntry = TabListEntry.builder()
                    .tabList(tabList)
                    .profile(entry.player().getGameProfile())
                    .displayName(entry.displayName())
                    .latency(entry.latency())
                    .gameMode(0)
                    .listed(true)
                    .build();
                tabList.addEntry(tabEntry);
            } catch (RuntimeException exception) {
                logger.warn(
                    "Failed to add tab entry {} for viewer {}.",
                    entry.player().getUsername(),
                    viewer.getUsername(),
                    exception
                );
            }
        }
    }

    private Component renderHeader(ProxyTabConfig config, Player viewer) {
        if (!config.tab().header().enabled()) {
            return Component.empty();
        }

        return placeholderService.renderViewerText(
            config.tab().header().value(),
            config,
            viewer,
            "tab.header.value"
        );
    }

    private Component renderFooter(ProxyTabConfig config, Player viewer) {
        if (!config.tab().footer().enabled()) {
            return Component.empty();
        }

        return placeholderService.renderViewerText(
            config.tab().footer().value(),
            config,
            viewer,
            "tab.footer.value"
        );
    }

    private void release(Player viewer) {
        if (!managedViewers.remove(viewer.getUniqueId())) {
            return;
        }

        try {
            viewer.clearPlayerListHeaderAndFooter();
            viewer.getTabList().clearAll();
        } catch (RuntimeException exception) {
            logger.warn("Failed to release tab control for {}.", viewer.getUsername(), exception);
        }
    }

    private void suspend(Player viewer) {
        if (!managedViewers.remove(viewer.getUniqueId())) {
            return;
        }

        try {
            viewer.clearPlayerListHeaderAndFooter();
        } catch (RuntimeException exception) {
            logger.warn("Failed to suspend tab control for {}.", viewer.getUsername(), exception);
        }
    }
}

