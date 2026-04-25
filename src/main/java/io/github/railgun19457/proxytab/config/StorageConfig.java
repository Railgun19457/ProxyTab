package io.github.railgun19457.proxytab.config;

import java.nio.file.Path;

public record StorageConfig(
    String type,
    Path playerStateFile,
    Path announcementsFile
) {
}

