package github.nighter.smartspawner.logging.discord;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerEventType;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Configuration for Discord webhook logging.
 */
public class DiscordWebhookConfig {
    private final SmartSpawner plugin;
    
    @Getter
    private boolean enabled;
    @Getter
    private String webhookUrl;
    @Getter
    private boolean showPlayerHead;
    @Getter
    private String embedTitle;
    @Getter
    private String embedDescription;
    @Getter
    private String embedFooter;
    @Getter
    private Map<String, Integer> eventColors;
    @Getter
    private List<EmbedField> customFields;
    @Getter
    private Set<SpawnerEventType> enabledEvents;
    @Getter
    private boolean logAllEvents;
    
    public DiscordWebhookConfig(SmartSpawner plugin) {
        this.plugin = plugin;
        loadConfig();
    }
    
    public void loadConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("logging.discord");
        if (section == null) {
            this.enabled = false;
            return;
        }
        
        this.enabled = section.getBoolean("enabled", false);
        this.webhookUrl = section.getString("webhook_url", "");
        this.showPlayerHead = section.getBoolean("show_player_head", true);
        this.embedTitle = section.getString("embed.title", "{description}");
        this.embedDescription = section.getString("embed.description", "{description}");
        this.embedFooter = section.getString("embed.footer", "SmartSpawner • {time}");
        this.logAllEvents = section.getBoolean("log_all_events", false);
        
        // Load event colors
        this.eventColors = new HashMap<>();
        ConfigurationSection colorsSection = section.getConfigurationSection("embed.colors");
        if (colorsSection != null) {
            for (String key : colorsSection.getKeys(false)) {
                String colorHex = colorsSection.getString(key, "#5865F2");
                this.eventColors.put(key.toUpperCase(), parseColor(colorHex));
            }
        }
        
        // Load custom fields
        this.customFields = new ArrayList<>();
        List<Map<?, ?>> fieldsList = section.getMapList("embed.fields");
        for (Map<?, ?> fieldMap : fieldsList) {
            String name = (String) fieldMap.get("name");
            String value = (String) fieldMap.get("value");
            boolean inline = fieldMap.containsKey("inline") ? (Boolean) fieldMap.get("inline") : false;
            if (name != null && value != null) {
                customFields.add(new EmbedField(name, value, inline));
            }
        }
        
        // Load enabled events
        this.enabledEvents = parseEnabledEvents(section);
    }
    
    private Set<SpawnerEventType> parseEnabledEvents(ConfigurationSection section) {
        if (logAllEvents) {
            return EnumSet.allOf(SpawnerEventType.class);
        }
        
        List<String> eventList = section.getStringList("logged_events");
        if (eventList.isEmpty()) {
            // Default to all command events and major spawner events
            Set<SpawnerEventType> events = EnumSet.noneOf(SpawnerEventType.class);
            events.add(SpawnerEventType.SPAWNER_PLACE);
            events.add(SpawnerEventType.SPAWNER_BREAK);
            events.add(SpawnerEventType.SPAWNER_EXPLODE);
            events.add(SpawnerEventType.SPAWNER_STACK_HAND);
            events.add(SpawnerEventType.SPAWNER_STACK_GUI);
            events.add(SpawnerEventType.SPAWNER_DESTACK_GUI);
            events.add(SpawnerEventType.COMMAND_EXECUTE_PLAYER);
            events.add(SpawnerEventType.COMMAND_EXECUTE_CONSOLE);
            events.add(SpawnerEventType.COMMAND_EXECUTE_RCON);
            return events;
        }
        
        Set<SpawnerEventType> events = EnumSet.noneOf(SpawnerEventType.class);
        for (String eventName : eventList) {
            try {
                events.add(SpawnerEventType.valueOf(eventName.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Invalid event type, skip
            }
        }
        return events;
    }
    
    private int parseColor(String colorHex) {
        try {
            // Remove # if present
            if (colorHex.startsWith("#")) {
                colorHex = colorHex.substring(1);
            }
            return Integer.parseInt(colorHex, 16);
        } catch (NumberFormatException e) {
            return 0x5865F2; // Default Discord blurple
        }
    }
    
    public boolean isEventEnabled(SpawnerEventType eventType) {
        return enabled && enabledEvents.contains(eventType);
    }
    
    public int getColorForEvent(SpawnerEventType eventType) {
        // Try specific event type first
        Integer color = eventColors.get(eventType.name());
        if (color != null) {
            return color;
        }
        return eventColors.getOrDefault("DEFAULT", 0x5865F2); // Default blurple
    }
    
    public static class EmbedField {
        @Getter
        private final String name;
        @Getter
        private final String value;
        @Getter
        private final boolean inline;
        
        public EmbedField(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }
}
