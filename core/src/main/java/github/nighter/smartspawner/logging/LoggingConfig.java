package github.nighter.smartspawner.logging;

import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration for the spawner logging system.
 * Controls what events are logged and how they're formatted.
 */
public class LoggingConfig {
    private boolean enabled;
    private boolean jsonFormat;
    private boolean consoleOutput;
    private Set<SpawnerEventType> enabledEvents;
    private String logDirectory;
    private int maxLogFiles;
    private long maxLogSizeMB;
    
    public LoggingConfig(ConfigurationSection config) {
        loadConfig(config);
    }
    
    /**
     * Reload configuration from the provided configuration section.
     */
    public void reload(ConfigurationSection config) {
        loadConfig(config);
    }
    
    private void loadConfig(ConfigurationSection config) {
        if (config == null) {
            // Use defaults if config section is null
            this.enabled = false;
            this.jsonFormat = false;
            this.consoleOutput = false;
            this.logDirectory = "logs";
            this.maxLogFiles = 10;
            this.maxLogSizeMB = 10;
            this.enabledEvents = EnumSet.noneOf(SpawnerEventType.class);
            return;
        }
        
        this.enabled = config.getBoolean("enabled", false);
        this.jsonFormat = config.getBoolean("json_format", false);
        this.consoleOutput = config.getBoolean("console_output", false);
        this.logDirectory = config.getString("log_directory", "logs");
        this.maxLogFiles = config.getInt("max_log_files", 10);
        this.maxLogSizeMB = config.getLong("max_log_size_mb", 10);
        
        // Parse enabled events
        this.enabledEvents = parseEnabledEvents(config);
    }
    
    private Set<SpawnerEventType> parseEnabledEvents(ConfigurationSection config) {
        Set<SpawnerEventType> events = EnumSet.noneOf(SpawnerEventType.class);
        
        // Check if we should log all events
        if (config.getBoolean("log_all_events", false)) {
            return EnumSet.allOf(SpawnerEventType.class);
        }
        
        // Parse specific event types
        List<String> eventList = config.getStringList("logged_events");
        if (eventList == null || eventList.isEmpty()) {
            // Default to logging major events
            events.add(SpawnerEventType.SPAWNER_PLACE);
            events.add(SpawnerEventType.SPAWNER_BREAK);
            events.add(SpawnerEventType.SPAWNER_STACK_HAND);
            events.add(SpawnerEventType.SPAWNER_STACK_GUI);
            events.add(SpawnerEventType.SPAWNER_DESTACK_GUI);
            return events;
        }
        
        for (String eventName : eventList) {
            try {
                events.add(SpawnerEventType.valueOf(eventName.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                // Invalid event type, skip
            }
        }
        
        return events;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isJsonFormat() {
        return jsonFormat;
    }
    
    public boolean isConsoleOutput() {
        return consoleOutput;
    }
    
    public Set<SpawnerEventType> getEnabledEvents() {
        return new HashSet<>(enabledEvents);
    }
    
    public String getLogDirectory() {
        return logDirectory;
    }
    
    public int getMaxLogFiles() {
        return maxLogFiles;
    }
    
    public long getMaxLogSizeMB() {
        return maxLogSizeMB;
    }
    
    public boolean isEventEnabled(SpawnerEventType eventType) {
        return enabled && enabledEvents.contains(eventType);
    }
}
