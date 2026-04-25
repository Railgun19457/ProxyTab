package io.github.railgun19457.proxytab.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;

public record GeneralConfig(
    Component networkId,
    Duration updateInterval,
    List<Pattern> ignorePatterns,
    Set<String> blacklistedServers,
    Component defaultServerName
) {
}

