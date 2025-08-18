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
import org.bukkit.Bukkit;
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
import java.util.List;
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
    private boolean serverVersionSupported = true;
    private JsonArray latestSupportedVersions = null;

    private static final String CONSOLE_RESET = "\u001B[0m";
    private static final String CONSOLE_BRIGHT_GREEN = "\u001B[92m";
    private static final String CONSOLE_YELLOW = "\u001B[33m";
    private static final String CONSOLE_INDIGO = "\u001B[38;5;93m";
    private static final String CONSOLE_LAVENDER = "\u001B[38;5;183m";
    private static final String CONSOLE_BRIGHT_PURPLE = "\u001B[95m";
    private static final String CONSOLE_RED = "\u001B[91m";

    private final Map<UUID, LocalDate> notifiedPlayers = new HashMap<>();

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        checkForUpdates().thenAccept(hasUpdate -> {
            if (hasUpdate && serverVersionSupported) {
                displayConsoleUpdateMessage();
            } else if (!serverVersionSupported) {
                displayUnsupportedVersionMessage();
            }
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to check for updates: " + ex.getMessage());
            return null;
        });
    }

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

    private void displayUnsupportedVersionMessage() {
        String frameColor = CONSOLE_RED;
        String serverVersion = Bukkit.getVersion();

        plugin.getLogger().warning(frameColor +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CONSOLE_RESET);
        plugin.getLogger().warning(frameColor + CONSOLE_YELLOW +
                "      ⚠️  ꜱᴇʀᴠᴇʀ ᴠᴇʀꜱɪᴏɴ ɴᴏ ʟᴏɴɢᴇʀ ꜱᴜᴘᴘᴏʀᴛᴇᴅ  ⚠️" + CONSOLE_RESET);
        plugin.getLogger().warning(frameColor +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CONSOLE_RESET);
        plugin.getLogger().warning("");
        plugin.getLogger().warning(frameColor +
                CONSOLE_RESET + "🖥️ ʏᴏᴜʀ ꜱᴇʀᴠᴇʀ ᴠᴇʀꜱɪᴏɴ: " + CONSOLE_YELLOW + serverVersion + CONSOLE_RESET);
        plugin.getLogger().warning(frameColor +
                CONSOLE_RESET + "📦 ʟᴀᴛᴇꜱᴛ ᴘʟᴜɢɪɴ ᴠᴇʀꜱɪᴏɴ: " + CONSOLE_BRIGHT_GREEN + latestVersion + CONSOLE_RESET);
        plugin.getLogger().warning(frameColor +
                CONSOLE_RESET + "🎯 ꜱᴜᴘᴘᴏʀᴛᴇᴅ ꜱᴇʀᴠᴇʀ ᴠᴇʀꜱɪᴏɴꜱ: " + CONSOLE_LAVENDER + getSupportedVersionsString() + CONSOLE_RESET);
        plugin.getLogger().warning("");
        plugin.getLogger().warning(frameColor +
                CONSOLE_RESET + "⚠️  ᴛʜɪꜱ ꜱᴇʀᴠᴇʀ ᴠᴇʀꜱɪᴏɴ ɪꜱ ɴᴏ ʟᴏɴɢᴇʀ ꜱᴜᴘᴘᴏʀᴛᴇᴅ" + CONSOLE_RESET);
        plugin.getLogger().warning(frameColor +
                CONSOLE_RESET + "📋 ᴜᴘᴅᴀᴛᴇ ɴᴏᴛɪꜰɪᴄᴀᴛɪᴏɴꜱ ᴅɪꜱᴀʙʟᴇᴅ" + CONSOLE_RESET);
        plugin.getLogger().warning("");
        plugin.getLogger().warning(frameColor +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CONSOLE_RESET);
    }

    private String getSupportedVersionsString() {
        if (latestSupportedVersions == null || latestSupportedVersions.isEmpty()) {
            return "N/A";
        }

        return latestSupportedVersions.asList().stream()
                .map(JsonElement::getAsString)
                .collect(Collectors.joining(", "));
    }

    private String formatConsoleText(String text, int maxLength) {
        if (text.length() > maxLength) {
            return text.substring(0, maxLength - 3) + "...";
        }
        return text + " ".repeat(maxLength - text.length());
    }

    private boolean isServerVersionSupported(JsonObject latestVersionObj) {
        try {
            String serverVersion = Bukkit.getVersion();

            JsonArray gameVersions = latestVersionObj.getAsJsonArray("game_versions");
            if (gameVersions == null || gameVersions.isEmpty()) {
                return true;
            }

            String cleanServerVersion = extractMinecraftVersion(serverVersion);

            for (JsonElement versionElement : gameVersions) {
                String supportedVersion = versionElement.getAsString();
                if (isVersionCompatible(cleanServerVersion, supportedVersion)) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking server version compatibility: " + e.getMessage());
            return true;
        }
    }

    private String extractMinecraftVersion(String serverVersion) {
        if (serverVersion.contains("MC: ")) {
            String mcPart = serverVersion.substring(serverVersion.indexOf("MC: ") + 4);
            if (mcPart.contains(")")) {
                mcPart = mcPart.substring(0, mcPart.indexOf(")"));
            }
            return mcPart.trim();
        }

        if (serverVersion.matches(".*\\d+\\.\\d+(\\.\\d+)?.*")) {
            String[] parts = serverVersion.split("\\s+");
            for (String part : parts) {
                if (part.matches("\\d+\\.\\d+(\\.\\d+)?")) {
                    return part;
                }
            }
        }

        return serverVersion;
    }

    private boolean isVersionCompatible(String serverVersion, String supportedVersion) {
        try {
            if (serverVersion.equals(supportedVersion)) {
                return true;
            }

            String[] serverParts = serverVersion.split("\\.");
            String[] supportedParts = supportedVersion.split("\\.");

            if (serverParts.length >= 2 && supportedParts.length >= 2) {
                int serverMajor = Integer.parseInt(serverParts[0]);
                int serverMinor = Integer.parseInt(serverParts[1]);
                int supportedMajor = Integer.parseInt(supportedParts[0]);
                int supportedMinor = Integer.parseInt(supportedParts[1]);

                return serverMajor == supportedMajor && serverMinor == supportedMinor;
            }

            return false;
        } catch (NumberFormatException e) {
            return serverVersion.equals(supportedVersion);
        }
    }

    public CompletableFuture<Boolean> checkForUpdates() {
        return CompletableFuture.supplyAsync(() -> {
            try {
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

                JsonObject latestVersionObj = null;
                for (JsonElement element : versions) {
                    JsonObject version = element.getAsJsonObject();
                    String versionType = version.get("version_type").getAsString();
                    if (versionType.equals("release")) {
                        if (latestVersionObj == null) {
                            latestVersionObj = version;
                        } else {
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

                downloadUrl = "https://modrinth.com/plugin/" + projectId + "/version/" + latestVersion;

                JsonArray files = latestVersionObj.getAsJsonArray("files");
                if (!files.isEmpty()) {
                    JsonObject primaryFile = files.get(0).getAsJsonObject();
                    directLink = primaryFile.get("url").getAsString();
                }

                serverVersionSupported = isServerVersionSupported(latestVersionObj);
                latestSupportedVersions = latestVersionObj.getAsJsonArray("game_versions");

                Version latest = new Version(latestVersion);
                Version current = new Version(currentVersion);

                updateAvailable = latest.compareTo(current) > 0;
                return updateAvailable;

            } catch (Exception e) {
                plugin.getLogger().warning("Error checking for updates: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    private void sendUpdateNotification(Player player) {
        if (!updateAvailable || !serverVersionSupported || !player.hasPermission("smartspawner.admin")) {
            return;
        }

        TextColor primaryPurple = TextColor.fromHexString("#ab7afd");
        TextColor deepPurple = TextColor.fromHexString("#7b68ee");
        TextColor indigo = TextColor.fromHexString("#5B2C6F");
        TextColor brightGreen = TextColor.fromHexString("#37eb9a");
        TextColor yellow = TextColor.fromHexString("#f0c857");
        TextColor white = TextColor.fromHexString("#e6e6fa");

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

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.hasPermission("smartspawner.admin")) {
            UUID playerId = player.getUniqueId();
            LocalDate today = LocalDate.now();

            notifiedPlayers.entrySet().removeIf(entry -> entry.getValue().isBefore(today));

            if (notifiedPlayers.containsKey(playerId) && notifiedPlayers.get(playerId).isEqual(today)) {
                return;
            }

            if (updateAvailable && serverVersionSupported) {
                Scheduler.runTaskLater(() -> {
                    sendUpdateNotification(player);
                    notifiedPlayers.put(playerId, today);
                }, 40L);
            } else if (!serverVersionSupported) {
                return;
            } else {
                checkForUpdates().thenAccept(hasUpdate -> {
                    if (hasUpdate && serverVersionSupported) {
                        Scheduler.runTask(() -> {
                            sendUpdateNotification(player);
                            notifiedPlayers.put(playerId, today);
                        });
                    }
                });
            }
        }
    }
}