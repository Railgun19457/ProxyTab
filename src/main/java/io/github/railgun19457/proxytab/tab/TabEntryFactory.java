package io.github.railgun19457.proxytab.tab;

import com.velocitypowered.api.proxy.Player;
import io.github.railgun19457.proxytab.config.ProxyTabConfig;
import io.github.railgun19457.proxytab.placeholder.PlaceholderService;

public final class TabEntryFactory {
    private final PlaceholderService placeholderService;

    public TabEntryFactory(PlaceholderService placeholderService) {
        this.placeholderService = placeholderService;
    }

    public TabPlayerEntry create(Player player, ProxyTabConfig config) {
        return new TabPlayerEntry(
            player,
            placeholderService.serverName(player),
            placeholderService.renderPlayerText(
                config.tab().playerFormat(),
                config,
                player,
                "tab.player-format"
            ),
            safeLatency(player.getPing())
        );
    }

    private int safeLatency(long ping) {
        if (ping > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (ping < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) ping;
    }
}

