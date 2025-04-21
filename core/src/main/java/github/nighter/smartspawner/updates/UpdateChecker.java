package github.nighter.smartspawner.updates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import github.nighter.smartspawner.Scheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class UpdateChecker implements Listener {
    private final JavaPlugin plugin;
    private final String projectId = "9tQwxSFr";
    private boolean updateAvailable = false;
    private final String currentVersion;
    private String latestVersion = "";
    private String downloadUrl = "";
    private String directLink = "";

    // Console colors
    private static final String CONSOLE_RESET = "\u001B[0m";
    private static final String CONSOLE_BRIGHT_GREEN = "\u001B[92m";
    private static final String CONSOLE_YELLOW = "\u001B[33m";
    private static final String CONSOLE_INDIGO = "\u001B[38;5;93m";
    private static final String CONSOLE_LAVENDER = "\u001B[38;5;183m";
    private static final String CONSOLE_BRIGHT_PURPLE = "\u001B[95m";

    // Track players who have received an update notification today
    private final Map<UUID, LocalDate> notifiedPlayers = new HashMap<>();

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Check for updates asynchronously on plugin startup
        checkForUpdates().thenAccept(hasUpdate -> {
            if (hasUpdate) {
                displayConsoleUpdateMessage();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to check for updates: " + ex.getMessage());
            return null;
        });
    }

    /**
     * Displays a fancy update message in the console
     */
    private void displayConsoleUpdateMessage() {
        String modrinthLink = "https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion;
        String frameColor = CONSOLE_INDIGO;

        plugin.getLogger().info(frameColor +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CONSOLE_RESET);
        plugin.getLogger().info(frameColor + CONSOLE_BRIGHT_GREEN +
                "         🔮 ꜱᴍᴀʀᴛꜱᴘᴀᴡɴᴇʀ ᴜᴘᴅᴀᴛᴇ ᴀᴠᴀɪʟᴀʙʟᴇ 🔮" + CONSOLE_RESET);
        plugin.getLogger().info(frameColor +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CONSOLE_RESET);
        plugin.getLogger().info("");
        plugin.getLogger().info(frameColor +
                CONSOLE_RESET + "📦 ᴄᴜʀʀᴇɴᴛ ᴠᴇʀꜱɪᴏɴ: " + CONSOLE_YELLOW  + formatConsoleText(currentVersion, 31) + CONSOLE_RESET);
        plugin.getLogger().info(frameColor +
                CONSOLE_RESET + "✅ ʟᴀᴛᴇꜱᴛ ᴠᴇʀꜱɪᴏɴ: " + CONSOLE_BRIGHT_GREEN + formatConsoleText(latestVersion, 32) + CONSOLE_RESET);
        plugin.getLogger().info("");
        plugin.getLogger().info(frameColor +
                CONSOLE_RESET + "📥 ᴅᴏᴡɴʟᴏᴀᴅ ᴛʜᴇ ʟᴀᴛᴇꜱᴛ ᴠᴇʀꜱɪᴏɴ ᴀᴛ:" + CONSOLE_RESET);
        plugin.getLogger().info(frameColor + " " +
                CONSOLE_LAVENDER + formatConsoleText(modrinthLink, 51) + CONSOLE_RESET);
        plugin.getLogger().info("");
        plugin.getLogger().info(frameColor +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CONSOLE_RESET);
    }

    /**
     * Format text to fit within console box with padding
     */
    private String formatConsoleText(String text, int maxLength) {
        if (text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text + " ".repeat(maxLength - text.length());
    }

    /**
     * Checks for updates from Modrinth
     * @return CompletableFuture that resolves to true if an update is available
     */
    public CompletableFuture<Boolean> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // String currentVersion = "0.0.0";
                URL url = new URL("https://api.modrinth.com/v2/project/" + projectId + "/version");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "SmartSpawner-UpdateChecker/1.0");

                if (connection.getResponseCode() != 200) {
                    plugin.getLogger().warning("Failed to check for updates. HTTP Error: " + connection.getResponseCode());
                    return false;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String response = reader.lines().collect(Collectors.joining("\n"));
                reader.close();

                JsonArray versions = JsonParser.parseString(response).getAsJsonArray();
                if (versions.isEmpty()) {
                    return false;
                }

                // Find the latest version
                JsonObject latestVersionObj = null;
                for (JsonElement element : versions) {
                    JsonObject version = element.getAsJsonObject();
                    // Skip pre-releases by checking if version_type is "release"
                    String versionType = version.get("version_type").getAsString();
                    if (versionType.equals("release")) {
                        if (latestVersionObj == null) {
                            latestVersionObj = version;
                        } else {
                            // Compare date_published to find the newest
                            String currentDate = latestVersionObj.get("date_published").getAsString();
                            String newDate = version.get("date_published").getAsString();
                            if (newDate.compareTo(currentDate) > 0) {
                                latestVersionObj = version;
                            }
                        }
                    }
                }

                if (latestVersionObj == null) {
                    return false;
                }

                latestVersion = latestVersionObj.get("version_number").getAsString();
                String versionId = latestVersionObj.get("id").getAsString();

                // Create proper Modrinth page link (instead of direct download)
                downloadUrl = "https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion;

                // Also save direct link (but don't display it)
                JsonArray files = latestVersionObj.getAsJsonArray("files");
                if (!files.isEmpty()) {
                    JsonObject primaryFile = files.get(0).getAsJsonObject();
                    directLink = primaryFile.get("url").getAsString();
                }

                // Compare versions using the Version class
                Version latest = new Version(latestVersion);
                Version current = new Version(currentVersion);

                // If latest version is greater than current version, an update is available
                updateAvailable = latest.compareTo(current) > 0;
                return updateAvailable;

            } catch (Exception e) {
                plugin.getLogger().warning("Error checking for updates: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Sends a beautiful update notification to a player
     *
     * @param player The player to notify
     */
    private void sendUpdateNotification(Player player) {
        if (!updateAvailable || !player.hasPermission("smartspawner.update.notify")) {
            return;
        }

        // Use colors from the config file style
        TextColor primaryPurple = TextColor.fromHexString("#ab7afd"); // Match config purple
        TextColor deepPurple = TextColor.fromHexString("#7b68ee"); // Match config deep purple
        TextColor indigo = TextColor.fromHexString("#5B2C6F"); // Dark indigo
        TextColor brightGreen = TextColor.fromHexString("#37eb9a"); // Match config green
        TextColor yellow = TextColor.fromHexString("#f0c857"); // Match config yellow
        TextColor white = TextColor.fromHexString("#e6e6fa"); // Match config lavender-white

        Component borderTop = Component.text("━━━━━━━━ ꜱᴍᴀʀᴛꜱᴘᴀᴡɴᴇʀ ᴜᴘᴅᴀᴛᴇ ━━━━━━━━").color(deepPurple);
        Component borderBottom = Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").color(deepPurple);

        Component updateMsg = Component.text("➤ ɴᴇᴡ ᴜᴘᴅᴀᴛᴇ ᴀᴠᴀɪʟᴀʙʟᴇ!").color(brightGreen);

        Component versionsComponent = Component.text("✦ ᴄᴜʀʀᴇɴᴛ: ")
                .color(white)
                .append(Component.text(currentVersion).color(yellow))
                .append(Component.text("  ✦ ʟᴀᴛᴇꜱᴛ: ").color(white))
                .append(Component.text(latestVersion).color(brightGreen));

        Component downloadButton = Component.text("▶ [ᴄʟɪᴄᴋ ᴛᴏ ᴅᴏᴡɴʟᴏᴀᴅ ʟᴀᴛᴇꜱᴛ ᴠᴇʀꜱɪᴏɴ]")
                .color(primaryPurple)
                .clickEvent(ClickEvent.openUrl(downloadUrl))
                .hoverEvent(HoverEvent.showText(
                        Component.text("ᴅᴏᴡɴʟᴏᴀᴅ ᴠᴇʀꜱɪᴏɴ ")
                                .color(white)
                                .append(Component.text(latestVersion).color(brightGreen))
                ));

        player.sendMessage(" ");
        player.sendMessage(borderTop);
        player.sendMessage(" ");
        player.sendMessage(updateMsg);
        player.sendMessage(versionsComponent);
        player.sendMessage(downloadButton);
        player.sendMessage(" ");
        player.sendMessage(borderBottom);

        // Use the levelup sound as it's used for other success messages in the config
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if player has permission and if there's an update
        if (player.hasPermission("smartspawner.update.notify")) {

            UUID playerId = player.getUniqueId();
            LocalDate today = LocalDate.now();

            // Clean up old notifications
            notifiedPlayers.entrySet().removeIf(entry -> entry.getValue().isBefore(today));

            // Check if the player has already been notified today
            if (notifiedPlayers.containsKey(playerId) && notifiedPlayers.get(playerId).isEqual(today)) {
                return; // Already notified today
            }

            if (updateAvailable) {
                // Wait a bit before sending the notification
                Scheduler.runTaskLater(() -> {
                    sendUpdateNotification(player);
                    notifiedPlayers.put(playerId, today); // Mark as notified after sending
                }, 40L);
            } else {
                // Re-check for updates when an operator joins, but only if we haven't found an update yet
                checkForUpdates().thenAccept(hasUpdate -> {
                    if (hasUpdate) {
                        Scheduler.runTask(() -> {
                            sendUpdateNotification(player);
                            notifiedPlayers.put(playerId, today); // Mark as notified after sending
                        });
                    }
                });
                // Do NOT mark as notified here if no update is found yet
            }
        }
    }
}