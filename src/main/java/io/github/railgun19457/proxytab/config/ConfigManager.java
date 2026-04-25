package io.github.railgun19457.proxytab.config;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

public final class ConfigManager {
    private static final String DEFAULT_NETWORK_ID = "<gradient:gold:yellow>ProxyTab Network</gradient>";
    private static final String DEFAULT_SERVER_NAME = "<gray>未知区域</gray>";
    private static final String DEFAULT_HEADER = """
        <bold><network_id></bold>
        <gray>当前在线: <green><online></green> 人</gray>
        """;
    private static final String DEFAULT_FOOTER = """
        <gray>当前服务器: <current_server></gray>
        <gray>网络延迟: <ping>ms</gray>
        """;
    private static final String DEFAULT_PLAYER_FORMAT =
        "<player_server> <gray>|</gray> <white><player_name></white>";
    private static final String DEFAULT_CLOSE_BUTTON =
        "<newline><click:run_command:'/proxytab close-notice'><hover:show_text:'点击后今天不再提醒'><red>[今日不再提醒]</red></hover></click>";
    private static final String DEFAULT_TAB_ANNOUNCEMENT_FORMAT = """
        <dark_gray>+------------------------------+</dark_gray>
        <gold><bold>公告</bold></gold> <gray>|</gray> <announcement>
        <dark_gray>+------------------------------+</dark_gray>
        """;

    private final Path dataDirectory;
    private final Path configPath;
    private final Logger logger;
    private final MiniMessage miniMessage;
    private final AtomicReference<ProxyTabConfig> current;

    public ConfigManager(Path dataDirectory, Logger logger, MiniMessage miniMessage) {
        this.dataDirectory = dataDirectory;
        this.configPath = dataDirectory.resolve("config.yml");
        this.logger = logger;
        this.miniMessage = miniMessage;
        this.current = new AtomicReference<>(defaults());
    }

    public ProxyTabConfig current() {
        return current.get();
    }

    public ProxyTabConfig loadInitial() {
        try {
            return load();
        } catch (ConfigLoadException exception) {
            logger.error("Failed to load config.yml; ProxyTab will use safe defaults.", exception);
            ProxyTabConfig fallback = defaults();
            current.set(fallback);
            return fallback;
        }
    }

    public ProxyTabConfig load() {
        try {
            ensureConfigFile();
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .build();
            ProxyTabConfig parsed = parse(loader.load());
            current.set(parsed);
            return parsed;
        } catch (ConfigLoadException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ConfigLoadException("Failed to load config.yml.", exception);
        }
    }

    private void ensureConfigFile() throws IOException {
        Files.createDirectories(dataDirectory);
        if (Files.exists(configPath)) {
            return;
        }

        try (InputStream input = ConfigManager.class.getResourceAsStream("/config.yml")) {
            if (input == null) {
                throw new IOException("Bundled config.yml resource is missing.");
            }
            Files.copy(input, configPath);
        }
    }

    private ProxyTabConfig parse(ConfigurationNode root) {
        GeneralConfig general = parseGeneral(root.node("general"));
        ServersConfig servers = parseServers(root.node("servers"));
        TabConfig tab = parseTab(root.node("tab"));
        AnnouncementConfig announcements = parseAnnouncements(root.node("announcements"));
        StorageConfig storage = parseStorage(root.node("storage"));
        return new ProxyTabConfig(general, servers, tab, announcements, storage);
    }

    private GeneralConfig parseGeneral(ConfigurationNode node) {
        Component networkId = parseComponent(
            "general.network-id",
            node.node("network-id").getString(DEFAULT_NETWORK_ID)
        );

        long interval = node.node("update-interval-ms").getLong(1000L);
        if (interval < 500L) {
            logger.warn("general.update-interval-ms is below 500ms; clamped to 500ms.");
            interval = 500L;
        }

        List<Pattern> ignorePatterns = readStringList(
            node.node("ignore-regex"),
            List.of(),
            "general.ignore-regex"
        ).stream()
            .map(this::compilePattern)
            .flatMap(List::stream)
            .toList();

        Set<String> blacklistedServers = readStringList(
            node.node("blacklisted-servers"),
            List.of(),
            "general.blacklisted-servers"
        ).stream()
            .map(ConfigManager::normalizeKey)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());

        Component defaultServerName = parseComponent(
            "general.default-server-name",
            node.node("default-server-name").getString(DEFAULT_SERVER_NAME)
        );

        return new GeneralConfig(
            networkId,
            Duration.ofMillis(interval),
            List.copyOf(ignorePatterns),
            blacklistedServers,
            defaultServerName
        );
    }

    private ServersConfig parseServers(ConfigurationNode node) {
        List<String> groupOrder = readStringList(node.node("group-order"), List.of("default"), "servers.group-order")
            .stream()
            .map(ConfigManager::normalizeKey)
            .filter(value -> !value.isBlank())
            .toList();

        Map<String, Component> mapping = new LinkedHashMap<>();
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.node("mapping").childrenMap().entrySet()) {
            String serverName = normalizeKey(String.valueOf(entry.getKey()));
            if (serverName.isBlank()) {
                continue;
            }
            String raw = entry.getValue().getString(serverName);
            mapping.put(serverName, parseComponent("servers.mapping." + serverName, raw));
        }

        return new ServersConfig(List.copyOf(groupOrder), Map.copyOf(mapping));
    }

    private TabConfig parseTab(ConfigurationNode node) {
        return new TabConfig(
            node.node("enabled").getBoolean(true),
            parseTextSection(node.node("header"), DEFAULT_HEADER),
            parseTextSection(node.node("footer"), DEFAULT_FOOTER),
            node.node("player-format").getString(DEFAULT_PLAYER_FORMAT),
            SortMode.parse(node.node("sort-mode").getString("server_then_name")),
            node.node("show-empty-group").getBoolean(false)
        );
    }

    private TextSectionConfig parseTextSection(ConfigurationNode node, String defaultValue) {
        return new TextSectionConfig(
            node.node("enabled").getBoolean(true),
            node.node("value").getString(defaultValue)
        );
    }

    private AnnouncementConfig parseAnnouncements(ConfigurationNode node) {
        AnnouncementSlotConfig chat = parseAnnouncementSlot(
            node.node("chat"),
            true,
            true,
            ""
        );
        AnnouncementSlotConfig tab = parseAnnouncementSlot(
            node.node("tab"),
            false,
            false,
            DEFAULT_TAB_ANNOUNCEMENT_FORMAT
        );
        return new AnnouncementConfig(chat, tab);
    }

    private AnnouncementSlotConfig parseAnnouncementSlot(
        ConfigurationNode node,
        boolean defaultEnabled,
        boolean defaultCloseButton,
        String defaultFormat
    ) {
        CloseButtonConfig closeButton = new CloseButtonConfig(
            node.node("close-button", "enabled").getBoolean(defaultCloseButton),
            node.node("close-button", "format").getString(DEFAULT_CLOSE_BUTTON)
        );
        return new AnnouncementSlotConfig(
            node.node("enabled").getBoolean(defaultEnabled),
            node.node("format").getString(defaultFormat),
            closeButton
        );
    }

    private StorageConfig parseStorage(ConfigurationNode node) {
        String type = node.node("type").getString("json");
        return new StorageConfig(
            type == null ? "json" : type,
            resolveDataPath(node.node("player-state-file").getString("data/player-state.json"), "data/player-state.json"),
            resolveDataPath(node.node("announcements-file").getString("data/announcements.json"), "data/announcements.json")
        );
    }

    private List<Pattern> compilePattern(String rawPattern) {
        try {
            return List.of(Pattern.compile(rawPattern));
        } catch (NullPointerException | PatternSyntaxException exception) {
            throw new ConfigLoadException("Invalid regex in general.ignore-regex: " + rawPattern, exception);
        }
    }

    private List<String> readStringList(ConfigurationNode node, List<String> fallback, String fieldName) {
        try {
            List<String> values = node.getList(String.class);
            return values == null ? fallback : values;
        } catch (Exception exception) {
            throw new ConfigLoadException("Failed to parse " + fieldName + " as a string list.", exception);
        }
    }

    private Component parseComponent(String fieldName, String raw) {
        try {
            return miniMessage.deserialize(raw == null ? "" : raw);
        } catch (RuntimeException exception) {
            logger.error("Failed to parse MiniMessage at {}: {}", fieldName, raw, exception);
            return Component.text(raw == null ? "" : raw);
        }
    }

    private Path resolveDataPath(String rawPath, String fallback) {
        String usablePath = rawPath == null || rawPath.isBlank() ? fallback : rawPath;
        try {
            Path path = Path.of(usablePath);
            return path.isAbsolute() ? path.normalize() : dataDirectory.resolve(path).normalize();
        } catch (InvalidPathException exception) {
            logger.error("Invalid storage path {}; using {}.", usablePath, fallback, exception);
            return dataDirectory.resolve(fallback).normalize();
        }
    }

    private ProxyTabConfig defaults() {
        return new ProxyTabConfig(
            new GeneralConfig(
                parseComponent("default.general.network-id", DEFAULT_NETWORK_ID),
                Duration.ofMillis(1000L),
                List.of(),
                Set.of(),
                parseComponent("default.general.default-server-name", DEFAULT_SERVER_NAME)
            ),
            new ServersConfig(
                List.of("lobby", "survival", "creative", "default"),
                Map.of()
            ),
            new TabConfig(
                true,
                new TextSectionConfig(true, DEFAULT_HEADER),
                new TextSectionConfig(true, DEFAULT_FOOTER),
                DEFAULT_PLAYER_FORMAT,
                SortMode.SERVER_THEN_NAME,
                false
            ),
            new AnnouncementConfig(
                new AnnouncementSlotConfig(
                    true,
                    "",
                    new CloseButtonConfig(true, DEFAULT_CLOSE_BUTTON)
                ),
                new AnnouncementSlotConfig(
                    false,
                    DEFAULT_TAB_ANNOUNCEMENT_FORMAT,
                    new CloseButtonConfig(false, DEFAULT_CLOSE_BUTTON)
                )
            ),
            new StorageConfig(
                "json",
                dataDirectory.resolve("data/player-state.json").normalize(),
                dataDirectory.resolve("data/announcements.json").normalize()
            )
        );
    }

    public static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

