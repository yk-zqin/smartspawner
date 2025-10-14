package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerLogEntry;
import org.bukkit.Location;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds Discord embeds from log entries with placeholder support.
 */
public class DiscordEmbedBuilder {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static DiscordEmbed buildEmbed(SpawnerLogEntry entry, DiscordWebhookConfig config, SmartSpawner plugin) {
        DiscordEmbed embed = new DiscordEmbed();

        // Set color based on event type
        embed.setColor(config.getColorForEvent(entry.getEventType()));

        // Build placeholders
        Map<String, String> placeholders = buildPlaceholders(entry);

        // Set title with placeholder replacement
        String title = replacePlaceholders(config.getEmbedTitle(), placeholders);
        embed.setTitle(title);

        // Set description with placeholder replacement - add line breaks for better spacing
        String description = replacePlaceholders(config.getEmbedDescription(), placeholders);
        embed.setDescription(description + "\n"); // Add spacing after description

        // Set footer
        String footer = replacePlaceholders(config.getEmbedFooter(), placeholders);
        embed.setFooter(footer, null);

        // Set timestamp
        embed.setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis()));

        // Add player thumbnail if enabled and player exists
        if (config.isShowPlayerHead() && entry.getPlayerName() != null) {
            String avatarUrl = getPlayerAvatarUrl(entry.getPlayerName());
            embed.setThumbnail(avatarUrl);
        }

        // Add fields in organized groups with better spacing

        // Group 1: Player Info (if exists)
        if (entry.getPlayerName() != null) {
            embed.addField("👤 Player: ", "`" + entry.getPlayerName() + "`", true);
            // Add empty field for spacing in 3-column layout
            embed.addField("\u200B", "\u200B", true);
            embed.addField("\u200B", "\u200B", true);
        }

        // Group 2: Location Info (if exists)
        if (entry.getLocation() != null) {
            Location loc = entry.getLocation();
            String locationStr = String.format("```\n%s(%d, %d, %d)\n```",
                    loc.getWorld().getName(),
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ());
            embed.addField("📍 Location", locationStr, false); // Full width for better readability
        }

        // Group 3: Entity Info (if exists)
        if (entry.getEntityType() != null) {
            String entityName = formatEntityName(entry.getEntityType().name());
            embed.addField("🐾 Entity Type", "`" + entityName + "`", true);
        }

        // Add metadata fields with improved formatting
        Map<String, Object> metadata = entry.getMetadata();
        if (!metadata.isEmpty()) {
            for (Map.Entry<String, Object> metaEntry : metadata.entrySet()) {
                String key = formatFieldName(metaEntry.getKey());
                String value = formatValue(String.valueOf(metaEntry.getValue()));
                String fieldName = addFieldIcon(key);
                embed.addField(fieldName, value, true);
            }
        }

        // Add separator before custom fields
        if (!config.getCustomFields().isEmpty() && (!metadata.isEmpty() || entry.getEntityType() != null)) {
            embed.addField("\u200B", "\u200B", false);
        }

        // Add custom fields from config
        for (DiscordWebhookConfig.EmbedField customField : config.getCustomFields()) {
            String fieldName = replacePlaceholders(customField.getName(), placeholders);
            String fieldValue = formatValue(replacePlaceholders(customField.getValue(), placeholders));
            embed.addField(fieldName, fieldValue, customField.isInline());
        }

        return embed;
    }

    private static Map<String, String> buildPlaceholders(SpawnerLogEntry entry) {
        Map<String, String> placeholders = new HashMap<>();

        placeholders.put("description", entry.getEventType().getDescription());
        placeholders.put("event_type", entry.getEventType().name());
        placeholders.put("time", FORMATTER.format(Instant.ofEpochMilli(System.currentTimeMillis())));

        if (entry.getPlayerName() != null) {
            placeholders.put("player", entry.getPlayerName());
        }

        if (entry.getPlayerUuid() != null) {
            placeholders.put("player_uuid", entry.getPlayerUuid().toString());
        }

        if (entry.getLocation() != null) {
            Location loc = entry.getLocation();
            placeholders.put("world", loc.getWorld().getName());
            placeholders.put("x", String.valueOf(loc.getBlockX()));
            placeholders.put("y", String.valueOf(loc.getBlockY()));
            placeholders.put("z", String.valueOf(loc.getBlockZ()));
            placeholders.put("location", String.format("%s (%d, %d, %d)",
                    loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
        }

        if (entry.getEntityType() != null) {
            placeholders.put("entity", entry.getEntityType().name());
        }

        // Add all metadata as placeholders
        for (Map.Entry<String, Object> metaEntry : entry.getMetadata().entrySet()) {
            placeholders.put(metaEntry.getKey(), String.valueOf(metaEntry.getValue()));
        }

        return placeholders;
    }

    private static String replacePlaceholders(String text, Map<String, String> placeholders) {
        if (text == null) return "";

        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * Format value with code blocks for better readability
     */
    private static String formatValue(String value) {
        if (value == null || value.isEmpty()) {
            return "`N/A`";
        }

        // If value is a number, format it nicely
        try {
            if (value.contains(".")) {
                Double.parseDouble(value);
                return "`" + value + "`";
            } else {
                Integer.parseInt(value);
                return "`" + value + "`";
            }
        } catch (NumberFormatException e) {
            // Not a number, continue
        }

        // If value contains spaces or is long, use regular code block
        if (value.length() > 30 || value.contains(" ")) {
            return "`" + value + "`";
        }

        return "`" + value + "`";
    }

    private static String getPlayerAvatarUrl(String playerName) {
        return "https://mc-heads.net/avatar/" + playerName;
    }

    private static String formatFieldName(String fieldName) {
        // Convert snake_case or camelCase to Title Case
        String[] words = fieldName.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }

    private static String formatEntityName(String entityType) {
        // Convert UPPER_CASE to Title Case for better readability
        if (entityType == null || entityType.isEmpty()) {
            return entityType;
        }
        String[] words = entityType.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
                result.append(" ");
            }
        }
        return result.toString().trim();
    }

    private static String addFieldIcon(String fieldName) {
        // Add appropriate icons for common field names
        String lowerFieldName = fieldName.toLowerCase();
        if (lowerFieldName.contains("command")) {
            return "⚙️ " + fieldName;
        } else if (lowerFieldName.contains("amount") || lowerFieldName.contains("count")) {
            return "🔢 " + fieldName;
        } else if (lowerFieldName.contains("quantity")) {
            return "📊 " + fieldName;
        } else if (lowerFieldName.contains("price") || lowerFieldName.contains("cost") || lowerFieldName.contains("money")) {
            return "💰 " + fieldName;
        } else if (lowerFieldName.contains("exp") || lowerFieldName.contains("experience")) {
            return "✨ " + fieldName;
        } else if (lowerFieldName.contains("stack")) {
            return "📚 " + fieldName;
        } else if (lowerFieldName.contains("type")) {
            return "🏷️ " + fieldName;
        }
        // Return with a generic info icon for other fields
        return "ℹ️ " + fieldName;
    }
}