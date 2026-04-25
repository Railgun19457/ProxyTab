package io.github.railgun19457.proxytab.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.proxytab.announcement.Announcement;
import io.github.railgun19457.proxytab.announcement.AnnouncementMode;
import io.github.railgun19457.proxytab.announcement.AnnouncementService;
import io.github.railgun19457.proxytab.announcement.AnnouncementSlot;
import io.github.railgun19457.proxytab.config.ConfigManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;

public final class ProxyTabCommand implements SimpleCommand {
    private final ProxyServer server;
    private final ConfigManager configManager;
    private final AnnouncementService announcementService;
    private final BooleanSupplier reloadAction;
    private final MiniMessage miniMessage;
    private final Logger logger;

    public ProxyTabCommand(
        ProxyServer server,
        ConfigManager configManager,
        AnnouncementService announcementService,
        BooleanSupplier reloadAction,
        MiniMessage miniMessage,
        Logger logger
    ) {
        this.server = server;
        this.configManager = configManager;
        this.announcementService = announcementService;
        this.reloadAction = reloadAction;
        this.miniMessage = miniMessage;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendStatus(source);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> executeReload(source);
            case "announcement" -> executeAnnouncement(source, args);
            case "close-notice" -> executeCloseNotice(source);
            default -> sendHelp(source);
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return List.of("reload", "announcement");
        }

        if (args.length == 1) {
            return filter(List.of("reload", "announcement"), args[0]);
        }

        if ("announcement".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                return filter(List.of("set", "delete"), args[1]);
            }
            if (args.length == 3) {
                return filter(List.of("chat", "tab"), args[2]);
            }
            if (args.length == 4 && "set".equalsIgnoreCase(args[1])) {
                return filter(List.of("always", "once_per_day"), args[3]);
            }
        }

        return List.of();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> has(source, "proxytab.reload");
            case "announcement" -> has(source, "proxytab.announcement");
            case "close-notice" -> source instanceof Player;
            default -> true;
        };
    }

    private void executeReload(CommandSource source) {
        if (!has(source, "proxytab.reload")) {
            send(source, "<red>你没有权限执行此命令。</red>");
            return;
        }

        if (reloadAction.getAsBoolean()) {
            send(source, "<green>ProxyTab 配置已重载。</green>");
        } else {
            send(source, "<red>ProxyTab 重载失败，请查看控制台日志。</red>");
        }
    }

    private void executeAnnouncement(CommandSource source, String[] args) {
        if (!has(source, "proxytab.announcement")) {
            send(source, "<red>你没有权限管理公告。</red>");
            return;
        }

        if (args.length < 3) {
            sendAnnouncementUsage(source);
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> executeAnnouncementSet(source, args);
            case "delete" -> executeAnnouncementDelete(source, args);
            default -> sendAnnouncementUsage(source);
        }
    }

    private void executeAnnouncementSet(CommandSource source, String[] args) {
        if (args.length < 5) {
            send(source, "<red>用法: /proxytab announcement set <chat|tab> <always|once_per_day> <content...></red>");
            return;
        }

        Optional<AnnouncementSlot> slot = AnnouncementSlot.parse(args[2]);
        if (slot.isEmpty()) {
            send(source, "<red>公告槽位只能是 chat 或 tab。</red>");
            return;
        }

        AnnouncementMode mode = AnnouncementMode.parse(args[3], null);
        if (mode == null) {
            send(source, "<red>公告模式只能是 always 或 once_per_day。</red>");
            return;
        }

        String content = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        try {
            announcementService.set(slot.get(), mode, content);
            send(source, "<green>公告已写入 " + slot.get().key() + " 槽位。</green>");
        } catch (IOException exception) {
            send(source, "<red>公告保存失败，请查看控制台日志。</red>");
            logger.error("Failed to save {} announcement.", slot.get().key(), exception);
        }
    }

    private void executeAnnouncementDelete(CommandSource source, String[] args) {
        Optional<AnnouncementSlot> slot = AnnouncementSlot.parse(args[2]);
        if (slot.isEmpty()) {
            send(source, "<red>公告槽位只能是 chat 或 tab。</red>");
            return;
        }

        try {
            announcementService.delete(slot.get());
            send(source, "<green>公告已从 " + slot.get().key() + " 槽位删除。</green>");
        } catch (IOException exception) {
            send(source, "<red>公告删除失败，请查看控制台日志。</red>");
            logger.error("Failed to delete {} announcement.", slot.get().key(), exception);
        }
    }

    private void executeCloseNotice(CommandSource source) {
        if (!(source instanceof Player player)) {
            send(source, "<red>此命令只能由玩家执行。</red>");
            return;
        }

        if (announcementService.closeToday(player)) {
            send(source, "<green>今天将不再提醒你当前聊天公告。</green>");
        } else {
            send(source, "<red>状态保存失败，请稍后重试。</red>");
        }
    }

    private void sendStatus(CommandSource source) {
        send(source, """
            <gold><bold>ProxyTab</bold></gold> <gray>v1.0.0</gray>
            <gray>在线玩家: <green>%d</green></gray>
            <gray>Tab 刷新: <green>%s</green></gray>
            <gray>聊天公告: <green>%s</green></gray>
            <gray>Tab 公告: <green>%s</green></gray>
            <gray>/proxytab reload</gray>
            <gray>/proxytab announcement set <chat|tab> <always|once_per_day> <content...></gray>
            """.formatted(
            server.getAllPlayers().size(),
            configManager.current().tab().enabled() ? "enabled" : "disabled",
            announcementState(AnnouncementSlot.CHAT),
            announcementState(AnnouncementSlot.TAB)
        ));
    }

    private void sendHelp(CommandSource source) {
        send(source, """
            <gold>ProxyTab 命令</gold>
            <gray>/proxytab</gray>
            <gray>/proxytab reload</gray>
            <gray>/proxytab announcement set <chat|tab> <always|once_per_day> <content...></gray>
            <gray>/proxytab announcement delete <chat|tab></gray>
            """);
    }

    private void sendAnnouncementUsage(CommandSource source) {
        send(source, """
            <red>公告命令用法:</red>
            <gray>/proxytab announcement set <chat|tab> <always|once_per_day> <content...></gray>
            <gray>/proxytab announcement delete <chat|tab></gray>
            """);
    }

    private String announcementState(AnnouncementSlot slot) {
        return announcementService.current(slot)
            .map(Announcement::mode)
            .map(AnnouncementMode::key)
            .orElse("none");
    }

    private List<String> filter(List<String> options, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(normalizedPrefix)) {
                result.add(option);
            }
        }
        return result;
    }

    private boolean has(CommandSource source, String permission) {
        if ("proxytab.use".equals(permission)) {
            return true;
        }
        return source.hasPermission(permission) || source.hasPermission("proxytab.*");
    }

    private void send(CommandSource source, String message) {
        source.sendMessage(miniMessage.deserialize(message));
    }
}

