package io.github.railgun19457.proxytab.tab;

import java.util.UUID;

public record VirtualTabPlayer(
    String serverName,
    UUID uniqueId,
    String username,
    String textureValue,
    String textureSignature,
    int latency,
    int gameMode
) {
}
