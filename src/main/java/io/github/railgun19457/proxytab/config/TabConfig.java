package io.github.railgun19457.proxytab.config;

public record TabConfig(
    boolean enabled,
    String header,
    String footer,
    String playerFormat,
    SortMode sortMode,
    boolean showEmptyGroup
) {
}

