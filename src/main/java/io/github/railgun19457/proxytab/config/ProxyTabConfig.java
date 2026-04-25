package io.github.railgun19457.proxytab.config;

public record ProxyTabConfig(
    GeneralConfig general,
    ServersConfig servers,
    TabConfig tab,
    AnnouncementConfig announcements,
    StorageConfig storage
) {
}

