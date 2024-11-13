package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class UpdateChecker implements Listener {
    private final SmartSpawner plugin;
    private final int resourceId;
    private String latestVersion;
    private boolean hasUpdate = false;
    private static final String SPIGOT_API_URL = "https://api.spigotmc.org/legacy/update.php?resource=";
    private static final String RESOURCE_URL = "https://www.spigotmc.org/resources/";
    private static final int TIMEOUT_SECONDS = 5;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private BukkitTask updateTask;

    // Fancy console colors
    private static final String CONSOLE_RESET = "\u001B[0m";
    private static final String CONSOLE_RED = "\u001B[31m";
    private static final String CONSOLE_GREEN = "\u001B[32m";
    private static final String CONSOLE_YELLOW = "\u001B[33m";
    private static final String CONSOLE_BLUE = "\u001B[34m";
    private static final String CONSOLE_PURPLE = "\u001B[35m";

    public UpdateChecker(SmartSpawner plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    public void initialize() {
        if (!configManager.isUpdateCheckerEnabled()) {
            return;
        }

        // Register events only if update checker is enabled
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Initial check after server starts (delayed by 1 minute)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () ->
                checkForUpdate().thenAccept(this::handleUpdateResult), 20L * 60L);

        // Schedule periodic checks
        long intervalTicks = configManager.getUpdateCheckInterval() * 20L * 60L * 60L; // hours to ticks
        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> checkForUpdate().thenAccept(this::handleUpdateResult),
                intervalTicks, // First check after interval
                intervalTicks  // Repeat at interval
        );
    }

    public void shutdown() {
        if (updateTask != null) {
            updateTask.cancel();
        }
    }

    private void handleUpdateResult(boolean hasUpdate) {
        if (hasUpdate && configManager.shouldNotifyOps()) {
            printUpdateAlert();
        }
    }

    private CompletableFuture<Boolean> checkForUpdate() {
        return CompletableFuture.supplyAsync(() -> {
            String currentVersion = plugin.getDescription().getVersion();
            try {
                URL url = new URL(SPIGOT_API_URL + resourceId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
                connection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    latestVersion = reader.readLine();
                }

                if (latestVersion != null && !currentVersion.equals(latestVersion)) {
                    hasUpdate = true;
                    return true;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
            return false;
        });
    }

    private void printUpdateAlert() {
        String currentVersion = plugin.getDescription().getVersion();
        String downloadUrl = RESOURCE_URL + resourceId;

        // Fancy console box
        String[] consoleMessage = {
                CONSOLE_PURPLE + "╔═══════════════════════ UPDATE AVAILABLE ═════════════════════╗" + CONSOLE_RESET,
                CONSOLE_PURPLE + "║" + CONSOLE_RESET + "                                                              " + CONSOLE_PURPLE + "║" + CONSOLE_RESET,
                String.format(CONSOLE_PURPLE + "║" + CONSOLE_RESET + "  Plugin: %s%-52s" + CONSOLE_PURPLE + "║" + CONSOLE_RESET, CONSOLE_GREEN, plugin.getName()),
                String.format(CONSOLE_PURPLE + "║" + CONSOLE_RESET + "  Current Version: %s%-43s" + CONSOLE_PURPLE + "║" + CONSOLE_RESET, CONSOLE_RED, currentVersion),
                String.format(CONSOLE_PURPLE + "║" + CONSOLE_RESET + "  Latest Version: %s%-44s" + CONSOLE_PURPLE + "║" + CONSOLE_RESET, CONSOLE_GREEN, latestVersion),
                String.format(CONSOLE_PURPLE + "║" + CONSOLE_RESET + "  Download: %s%-50s" + CONSOLE_PURPLE + "║" + CONSOLE_RESET, CONSOLE_BLUE, downloadUrl),
                CONSOLE_PURPLE + "║" + CONSOLE_RESET + "                                                              " + CONSOLE_PURPLE + "║" + CONSOLE_RESET,
                CONSOLE_PURPLE + "╚══════════════════════════════════════════════════════════════╝" + CONSOLE_RESET
        };

        // Print to console
        for (String line : consoleMessage) {
            plugin.getLogger().info(line);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!configManager.shouldNotifyOnJoin() || !hasUpdate) {
            return;
        }

        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("smartspawner.update")) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> sendUpdateMessage(player),
                    40L
            );
        }
    }

    private void sendUpdateMessage(Player player) {
        // Lấy messages từ file ngôn ngữ
        String currentVersion = plugin.getDescription().getVersion();
        String titleMsg = languageManager.getMessage("update.title");
        String versionMsg = languageManager.getMessage("update.current_version")
                .replace("%current_version%", currentVersion);
        String lastVersionMsg = languageManager.getMessage("update.last_version")
                .replace("%new_version%", getLatestVersion());
        String downloadMsg = languageManager.getMessage("update.download_button");
        String downloadHoverMsg = languageManager.getMessage("update.download_hover");

        Component title = Component.text(titleMsg)
                .color(TextColor.fromHexString("#3287A9"))
                .decorate(TextDecoration.BOLD);

        Component version = Component.text(versionMsg)
                .color(TextColor.fromHexString("#C6C6C6"));

        Component lastVersion = Component.text(lastVersionMsg)
                .color(TextColor.fromHexString("#00E689"));

        // Nút download với hiệu ứng hover và click
        Component downloadButton = Component.text(downloadMsg)
                .color(TextColor.fromHexString("#ADF3FD"))
                .clickEvent(ClickEvent.openUrl(RESOURCE_URL + resourceId))
                .hoverEvent(HoverEvent.showText(
                        Component.text(downloadHoverMsg)
                                .color(TextColor.fromHexString("#ADF3FD"))
                ));

        // Gửi thông báo
        player.sendMessage(Component.empty());
        player.sendMessage(title);
        player.sendMessage(version);
        player.sendMessage(lastVersion);
        player.sendMessage(Component.empty());
        player.sendMessage(downloadButton);
        player.sendMessage(Component.empty());

        // Thêm hiệu ứng âm thanh
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
    }

    public boolean hasUpdate() {
        return hasUpdate;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}
