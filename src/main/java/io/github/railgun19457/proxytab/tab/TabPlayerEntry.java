package io.github.railgun19457.proxytab.tab;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public record TabPlayerEntry(
    Player player,
    String serverName,
    Component displayName,
    int latency,
    int gameMode
) {
}

