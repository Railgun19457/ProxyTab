package io.github.railgun19457.proxytab.tab;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;

public final class VirtualPlayerBridge {
    public static final ChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("proxytab", "virtual_players");

    private static final int PROTOCOL_VERSION = 1;

    private final VirtualTabEntryRegistry registry;
    private final Logger logger;

    public VirtualPlayerBridge(VirtualTabEntryRegistry registry, Logger logger) {
        this.registry = registry;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }

        String serverName = connection.getServerInfo().getName();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(event.getData()))) {
            int version = input.readUnsignedByte();
            if (version != PROTOCOL_VERSION) {
                logger.warn("Ignored virtual-player payload from {} with unsupported version {}.", serverName, version);
                return;
            }

            String action = input.readUTF();
            switch (action) {
                case "reset" -> registry.clearServer(serverName);
                case "update" -> registry.update(
                    serverName,
                    readUuid(input),
                    input.readUTF(),
                    input.readUTF(),
                    input.readUTF(),
                    input.readInt(),
                    input.readInt()
                );
                case "hide" -> registry.hide(serverName, readUuid(input));
                case "remove" -> registry.remove(serverName, readUuid(input));
                default -> logger.warn("Ignored unknown virtual-player action '{}' from {}.", action, serverName);
            }
        } catch (IOException | RuntimeException exception) {
            logger.warn("Failed to handle virtual-player payload from {}.", serverName, exception);
        }
    }

    private UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
