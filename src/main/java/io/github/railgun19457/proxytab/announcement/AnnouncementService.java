package io.github.railgun19457.proxytab.announcement;

import com.velocitypowered.api.proxy.Player;
import io.github.railgun19457.proxytab.config.AnnouncementSlotConfig;
import io.github.railgun19457.proxytab.config.ProxyTabConfig;
import io.github.railgun19457.proxytab.placeholder.PlaceholderService;
import io.github.railgun19457.proxytab.storage.AnnouncementRepository;
import io.github.railgun19457.proxytab.storage.PlayerNoticeStateRepository;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.slf4j.Logger;

public final class AnnouncementService {
    private final Logger logger;
    private final Supplier<ProxyTabConfig> configSupplier;
    private final PlaceholderService placeholderService;
    private final AtomicReference<AnnouncementRepository> announcementRepository = new AtomicReference<>();
    private final AtomicReference<PlayerNoticeStateRepository> stateRepository = new AtomicReference<>();
    private final EnumMap<AnnouncementSlot, Announcement> announcements = new EnumMap<>(AnnouncementSlot.class);

    public AnnouncementService(
        Logger logger,
        Supplier<ProxyTabConfig> configSupplier,
        PlaceholderService placeholderService
    ) {
        this.logger = logger;
        this.configSupplier = configSupplier;
        this.placeholderService = placeholderService;
    }

    public synchronized void useRepositories(
        AnnouncementRepository announcementRepository,
        PlayerNoticeStateRepository stateRepository
    ) throws IOException {
        this.announcementRepository.set(announcementRepository);
        this.stateRepository.set(stateRepository);
        announcements.clear();
        announcements.putAll(announcementRepository.loadAll());
    }

    public synchronized Optional<Announcement> current(AnnouncementSlot slot) {
        return Optional.ofNullable(announcements.get(slot));
    }

    public synchronized Map<AnnouncementSlot, Announcement> snapshot() {
        return Map.copyOf(announcements);
    }

    public synchronized void set(AnnouncementSlot slot, AnnouncementMode mode, String content) throws IOException {
        Announcement announcement = new Announcement(slot, mode, content, Instant.now());
        repository().save(slot, announcement);
        announcements.put(slot, announcement);
        clearSlotState(slot);
    }

    public synchronized void delete(AnnouncementSlot slot) throws IOException {
        repository().delete(slot);
        announcements.remove(slot);
        clearSlotState(slot);
    }

    public void sendJoinAnnouncement(Player player) {
        ProxyTabConfig config = configSupplier.get();
        AnnouncementSlotConfig slotConfig = config.announcements().chat();
        if (!slotConfig.enabled()) {
            return;
        }

        Announcement announcement;
        synchronized (this) {
            announcement = announcements.get(AnnouncementSlot.CHAT);
        }

        if (announcement == null || announcement.content().isBlank()) {
            return;
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (isClosed(player.getUniqueId(), AnnouncementSlot.CHAT, today)) {
            return;
        }

        if (announcement.mode() == AnnouncementMode.ONCE_PER_DAY
            && hasSeen(player.getUniqueId(), AnnouncementSlot.CHAT, today)) {
            return;
        }

        Component message = placeholderService.renderViewerText(
            announcement.content(),
            config,
            player,
            "announcement.chat"
        );
        if (slotConfig.closeButton().enabled()) {
            message = message.append(placeholderService.renderViewerText(
                slotConfig.closeButton().format(),
                config,
                player,
                "announcements.chat.close-button.format"
            ));
        }

        player.sendMessage(message);

        if (announcement.mode() == AnnouncementMode.ONCE_PER_DAY) {
            markSeen(player.getUniqueId(), AnnouncementSlot.CHAT, today);
        }
    }

    public Optional<Component> tabFooterLine(ProxyTabConfig config, Player viewer) {
        if (!config.announcements().tab().enabled()) {
            return Optional.empty();
        }

        Announcement announcement;
        synchronized (this) {
            announcement = announcements.get(AnnouncementSlot.TAB);
        }

        if (announcement == null || announcement.content().isBlank()) {
            return Optional.empty();
        }

        Component announcementContent = placeholderService.renderViewerText(
            announcement.content(),
            config,
            viewer,
            "announcement.tab"
        );
        String format = config.announcements().tab().format();
        if (format == null || format.isBlank()) {
            return Optional.of(announcementContent);
        }

        return Optional.of(placeholderService.renderViewerText(
            format,
            config,
            viewer,
            "announcements.tab.format",
            Placeholder.component("announcement", announcementContent)
        ));
    }

    public boolean closeToday(Player player) {
        try {
            stateRepository().closeForToday(
                player.getUniqueId(),
                AnnouncementSlot.CHAT,
                LocalDate.now(ZoneId.systemDefault())
            );
            return true;
        } catch (IOException exception) {
            logger.error("Failed to store close-notice state for {}", player.getUsername(), exception);
            return false;
        }
    }

    private boolean hasSeen(UUID playerId, AnnouncementSlot slot, LocalDate date) {
        try {
            return stateRepository().hasSeenToday(playerId, slot, date);
        } catch (IOException exception) {
            logger.error("Failed to read notice seen state for {}", playerId, exception);
            return false;
        }
    }

    private void markSeen(UUID playerId, AnnouncementSlot slot, LocalDate date) {
        try {
            stateRepository().markSeenToday(playerId, slot, date);
        } catch (IOException exception) {
            logger.error("Failed to store notice seen state for {}", playerId, exception);
        }
    }

    private boolean isClosed(UUID playerId, AnnouncementSlot slot, LocalDate date) {
        try {
            return stateRepository().isClosedToday(playerId, slot, date);
        } catch (IOException exception) {
            logger.error("Failed to read notice close state for {}", playerId, exception);
            return false;
        }
    }

    private void clearSlotState(AnnouncementSlot slot) {
        try {
            stateRepository().clearSlotState(slot);
        } catch (IOException exception) {
            logger.error("Failed to clear notice state for {} announcement.", slot.key(), exception);
        }
    }

    private AnnouncementRepository repository() {
        AnnouncementRepository repository = announcementRepository.get();
        if (repository == null) {
            throw new IllegalStateException("Announcement repository is not initialized.");
        }
        return repository;
    }

    private PlayerNoticeStateRepository stateRepository() {
        PlayerNoticeStateRepository repository = stateRepository.get();
        if (repository == null) {
            throw new IllegalStateException("Player notice state repository is not initialized.");
        }
        return repository;
    }
}

