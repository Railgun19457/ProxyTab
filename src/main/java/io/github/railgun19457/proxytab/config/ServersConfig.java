package io.github.railgun19457.proxytab.config;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;

public record ServersConfig(
    List<String> groupOrder,
    Map<String, Component> mapping
) {
}

