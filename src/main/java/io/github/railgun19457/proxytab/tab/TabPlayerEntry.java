package io.github.railgun19457.proxytab.tab;

import com.velocitypowered.api.util.GameProfile;
import java.util.UUID;
import net.kyori.adventure.text.Component;

public record TabPlayerEntry(
    UUID uniqueId,
    String username,
    GameProfile profile,
    boolean active,
    String serverName,
    Component displayName,
    int latency,
    int gameMode
) {
}
