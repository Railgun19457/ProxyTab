package io.github.railgun19457.proxytab.tab;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import io.github.railgun19457.proxytab.config.ProxyTabConfig;
import io.github.railgun19457.proxytab.placeholder.PlaceholderService;
import java.util.List;

public final class TabEntryFactory {
    private final PlaceholderService placeholderService;

    public TabEntryFactory(PlaceholderService placeholderService) {
        this.placeholderService = placeholderService;
    }

    public TabPlayerEntry create(Player player, ProxyTabConfig config) {
        String serverName = placeholderService.serverName(player);
        return new TabPlayerEntry(
            player.getUniqueId(),
            player.getUsername(),
            player.getGameProfile(),
            player.isActive(),
            serverName,
            placeholderService.renderPlayerText(
                config.tab().playerFormat(),
                config,
                player.getUsername(),
                serverName,
                player.getPing(),
                "tab.player-format"
            ),
            safeLatency(player.getPing()),
            safeGameMode(player)
        );
    }

    public TabPlayerEntry create(VirtualTabPlayer player, ProxyTabConfig config) {
        return new TabPlayerEntry(
            player.uniqueId(),
            player.username(),
            profile(player),
            true,
            player.serverName(),
            placeholderService.renderPlayerText(
                config.tab().playerFormat(),
                config,
                player.username(),
                player.serverName(),
                player.latency(),
                "tab.player-format"
            ),
            safeLatency(player.latency()),
            player.gameMode()
        );
    }

    private GameProfile profile(VirtualTabPlayer player) {
        List<GameProfile.Property> properties = player.textureValue().isBlank()
            ? List.of()
            : List.of(new GameProfile.Property("textures", player.textureValue(), player.textureSignature()));
        return new GameProfile(player.uniqueId(), player.username(), properties);
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

    private int safeGameMode(Player player) {
        return player.getTabList()
            .getEntry(player.getUniqueId())
            .map(TabListEntry::getGameMode)
            .filter(gameMode -> gameMode >= 0 && gameMode <= 3)
            .orElse(0);
    }
}
