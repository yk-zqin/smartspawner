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
        
        // Set description with placeholder replacement
        String description = replacePlaceholders(config.getEmbedDescription(), placeholders);
        embed.setDescription(description);
        
        // Set footer
        String footer = replacePlaceholders(config.getEmbedFooter(), placeholders);
        embed.setFooter(footer, null);
        
        // Set timestamp
        embed.setTimestamp(Instant.ofEpochMilli(System.currentTimeMillis()));
        
        // Add player thumbnail if enabled and player exists
        if (config.isShowPlayerHead() && entry.getPlayerUuid() != null) {
            String avatarUrl = getPlayerAvatarUrl(entry.getPlayerUuid());
            embed.setThumbnail(avatarUrl);
        }
        
        // Add default fields based on entry data
        if (entry.getPlayerName() != null) {
            embed.addField("Player", entry.getPlayerName(), true);
        }
        
        if (entry.getLocation() != null) {
            Location loc = entry.getLocation();
            String locationStr = String.format("%s (%d, %d, %d)", 
                    loc.getWorld().getName(), 
                    loc.getBlockX(), 
                    loc.getBlockY(), 
                    loc.getBlockZ());
            embed.addField("Location", locationStr, true);
        }
        
        if (entry.getEntityType() != null) {
            embed.addField("Entity Type", entry.getEntityType().name(), true);
        }
        
        // Add metadata fields
        Map<String, Object> metadata = entry.getMetadata();
        if (!metadata.isEmpty()) {
            for (Map.Entry<String, Object> metaEntry : metadata.entrySet()) {
                String key = formatFieldName(metaEntry.getKey());
                String value = String.valueOf(metaEntry.getValue());
                embed.addField(key, value, true);
            }
        }
        
        // Add custom fields from config
        for (DiscordWebhookConfig.EmbedField customField : config.getCustomFields()) {
            String fieldName = replacePlaceholders(customField.getName(), placeholders);
            String fieldValue = replacePlaceholders(customField.getValue(), placeholders);
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
    
    private static String getPlayerAvatarUrl(UUID playerUuid) {
        // Use Crafatar service for player heads
        return "https://crafatar.com/avatars/" + playerUuid.toString() + "?overlay=true";
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
}
