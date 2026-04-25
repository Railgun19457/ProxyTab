package io.github.railgun19457.proxytab.tab;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import io.github.railgun19457.proxytab.config.ProxyTabConfig;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;

public final class TabScheduler {
    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final Supplier<ProxyTabConfig> configSupplier;
    private final TabViewBuilder viewBuilder;
    private final TabRenderer renderer;
    private ScheduledTask task;

    public TabScheduler(
        Object plugin,
        ProxyServer server,
        Logger logger,
        Supplier<ProxyTabConfig> configSupplier,
        TabViewBuilder viewBuilder,
        TabRenderer renderer
    ) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.configSupplier = configSupplier;
        this.viewBuilder = viewBuilder;
        this.renderer = renderer;
    }

    public synchronized void restart() {
        stop();
        ProxyTabConfig config = configSupplier.get();
        if (!config.tab().enabled()) {
            renderer.releaseAll(server.getAllPlayers());
            return;
        }

        task = server.getScheduler()
            .buildTask(plugin, this::renderOnce)
            .repeat(config.general().updateInterval().toMillis(), TimeUnit.MILLISECONDS)
            .schedule();
        renderOnce();
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        renderer.releaseAll(server.getAllPlayers());
    }

    private void renderOnce() {
        try {
            ProxyTabConfig config = configSupplier.get();
            if (!config.tab().enabled()) {
                renderer.releaseAll(server.getAllPlayers());
                return;
            }

            List<TabPlayerEntry> entries = viewBuilder.build(config);
            renderer.renderAll(config, entries, server.getAllPlayers());
        } catch (RuntimeException exception) {
            logger.error("Failed to refresh ProxyTab view.", exception);
        }
    }
}

