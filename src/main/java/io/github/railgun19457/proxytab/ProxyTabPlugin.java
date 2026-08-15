package io.github.railgun19457.proxytab;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.proxytab.announcement.AnnouncementService;
import io.github.railgun19457.proxytab.command.ProxyTabCommand;
import io.github.railgun19457.proxytab.config.ConfigManager;
import io.github.railgun19457.proxytab.config.ProxyTabConfig;
import io.github.railgun19457.proxytab.placeholder.PlaceholderService;
import io.github.railgun19457.proxytab.storage.JsonAnnouncementRepository;
import io.github.railgun19457.proxytab.storage.JsonPlayerNoticeStateRepository;
import io.github.railgun19457.proxytab.storage.MemoryAnnouncementRepository;
import io.github.railgun19457.proxytab.storage.MemoryPlayerNoticeStateRepository;
import io.github.railgun19457.proxytab.tab.TabEntryFactory;
import io.github.railgun19457.proxytab.tab.TabRenderer;
import io.github.railgun19457.proxytab.tab.TabScheduler;
import io.github.railgun19457.proxytab.tab.TabViewBuilder;
import io.github.railgun19457.proxytab.tab.VirtualTabEntryRegistry;
import io.github.railgun19457.proxytab.tab.VirtualPlayerBridge;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

@Plugin(
    id = "proxytab",
    name = "ProxyTab",
    version = "0.0.4",
    description = "Global tab-list manager for Velocity.",
    authors = {"ProxyTab Contributors"}
)
public final class ProxyTabPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private ConfigManager configManager;
    private AnnouncementService announcementService;
    private TabRenderer tabRenderer;
    private TabScheduler tabScheduler;
    private VirtualPlayerBridge virtualPlayerBridge;

    @Inject
    public ProxyTabPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        configManager = new ConfigManager(dataDirectory, logger, miniMessage);
        ProxyTabConfig config = configManager.loadInitial();

        PlaceholderService placeholderService = new PlaceholderService(server, miniMessage, logger);
        announcementService = new AnnouncementService(logger, configManager::current, placeholderService);
        configureStorage(config);

        VirtualTabEntryRegistry virtualEntries = new VirtualTabEntryRegistry();
        TabEntryFactory entryFactory = new TabEntryFactory(placeholderService);
        TabViewBuilder viewBuilder = new TabViewBuilder(server, entryFactory, virtualEntries);
        tabRenderer = new TabRenderer(logger, placeholderService, announcementService, virtualEntries);
        tabScheduler = new TabScheduler(this, server, logger, configManager::current, viewBuilder, tabRenderer);
        virtualPlayerBridge = new VirtualPlayerBridge(virtualEntries, logger);
        server.getChannelRegistrar().register(VirtualPlayerBridge.CHANNEL);
        server.getEventManager().register(this, virtualPlayerBridge);

        registerCommand();
        tabScheduler.restart();
        logger.info("ProxyTab has been enabled.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (tabScheduler != null) {
            tabScheduler.shutdown();
        }
        server.getChannelRegistrar().unregister(VirtualPlayerBridge.CHANNEL);
        logger.info("ProxyTab has been disabled.");
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (announcementService == null) {
            return;
        }

        server.getScheduler()
            .buildTask(this, () -> announcementService.sendJoinAnnouncement(event.getPlayer()))
            .delay(1, TimeUnit.SECONDS)
            .schedule();
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (tabRenderer == null || configManager == null) {
            return;
        }

        String currentServer = event.getServer().getServerInfo().getName();
        if (configManager.current().general().blacklistedServers().contains(ConfigManager.normalizeKey(currentServer))) {
            tabRenderer.releaseForBlacklistedServer(event.getPlayer());
        }
    }

    public boolean reloadPlugin() {
        try {
            ProxyTabConfig config = configManager.load();
            configureStorage(config);
            tabScheduler.restart();
            return true;
        } catch (RuntimeException exception) {
            logger.error("Failed to reload ProxyTab.", exception);
            return false;
        }
    }

    private void registerCommand() {
        CommandManager commandManager = server.getCommandManager();
        CommandMeta commandMeta = commandManager.metaBuilder("proxytab")
            .aliases("ptab")
            .plugin(this)
            .build();
        commandManager.register(commandMeta, new ProxyTabCommand(
            server,
            configManager,
            announcementService,
            this::reloadPlugin,
            miniMessage,
            logger
        ));
    }

    private void configureStorage(ProxyTabConfig config) {
        if (!"json".equals(config.storage().type().toLowerCase(Locale.ROOT))) {
            logger.warn("Unsupported storage.type '{}'; falling back to json.", config.storage().type());
        }

        try {
            announcementService.useRepositories(
                new JsonAnnouncementRepository(config.storage().announcementsFile()),
                new JsonPlayerNoticeStateRepository(config.storage().playerStateFile())
            );
        } catch (Exception exception) {
            logger.error("Failed to initialize JSON storage. Announcements will use empty volatile state.", exception);
            try {
                announcementService.useRepositories(
                    new MemoryAnnouncementRepository(),
                    new MemoryPlayerNoticeStateRepository()
                );
            } catch (Exception fallbackException) {
                logger.error("Failed to initialize fallback in-memory announcement storage.", fallbackException);
            }
        }
    }
}

