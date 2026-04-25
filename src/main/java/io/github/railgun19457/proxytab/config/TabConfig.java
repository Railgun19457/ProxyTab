package io.github.railgun19457.proxytab.config;

public record TabConfig(
    boolean enabled,
    TextSectionConfig header,
    TextSectionConfig footer,
    String playerFormat,
    SortMode sortMode,
    boolean showEmptyGroup
) {
}

