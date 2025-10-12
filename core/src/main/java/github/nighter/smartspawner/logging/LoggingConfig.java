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
    private final boolean enabled;
    private final boolean asyncLogging;
    private final boolean jsonFormat;
    private final boolean consoleOutput;
    private final Set<SpawnerEventType> enabledEvents;
    private final String logDirectory;
    private final int maxLogFiles;
    private final long maxLogSizeMB;
    
    public LoggingConfig(ConfigurationSection config) {
        this.enabled = config.getBoolean("enabled", false);
        this.asyncLogging = config.getBoolean("async", true);
        this.jsonFormat = config.getBoolean("json_format", false);
        this.consoleOutput = config.getBoolean("console_output", false);
        this.logDirectory = config.getString("log_directory", "logs/spawner");
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
    
    public boolean isAsyncLogging() {
        return asyncLogging;
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
