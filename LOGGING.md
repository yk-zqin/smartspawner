# SmartSpawner Logging System

## Overview

The SmartSpawner logging system provides comprehensive audit trails for all spawner-related actions. It's designed to be lightweight, asynchronous, and configurable to meet various server administration needs.

## Features

- **Asynchronous Logging**: Non-blocking logging that doesn't impact server performance
- **Multiple Formats**: Support for both human-readable and JSON structured logs
- **Automatic Rotation**: Configurable log file rotation based on size
- **Event Filtering**: Choose which events to log
- **Flexible Storage**: File-based logging with configurable retention

## Configuration

The logging system is configured in `config.yml` under the `logging` section:

```yaml
logging:
  # Enable/disable the logging system
  enabled: false
  
  # Use asynchronous logging (recommended: true)
  async: true
  
  # Output format: false for human-readable, true for JSON
  json_format: false
  
  # Also output logs to console
  console_output: false
  
  # Directory for log files (relative to plugin folder)
  log_directory: "logs/spawner"
  
  # Maximum number of log files to keep
  max_log_files: 10
  
  # Maximum log file size in MB before rotation
  max_log_size_mb: 10
  
  # Log all events (overrides logged_events list)
  log_all_events: false
  
  # Specific events to log
  logged_events:
    - SPAWNER_PLACE
    - SPAWNER_BREAK
    - SPAWNER_STACK_HAND
    - SPAWNER_STACK_GUI
    - SPAWNER_DESTACK_GUI
    - SPAWNER_EXP_CLAIM
    - SPAWNER_SELL_ALL
    - COMMAND_EXECUTE_PLAYER
    - COMMAND_EXECUTE_CONSOLE
```

## Available Event Types

### Spawner Lifecycle
- `SPAWNER_PLACE` - When a spawner is placed
- `SPAWNER_BREAK` - When a spawner is broken
- `SPAWNER_EXPLODE` - When a spawner is destroyed by explosion

### Spawner Stacking
- `SPAWNER_STACK_HAND` - Spawner stacked by hand
- `SPAWNER_STACK_GUI` - Spawner stacked via GUI
- `SPAWNER_DESTACK_GUI` - Spawner destacked via GUI

### GUI Interactions
- `SPAWNER_GUI_OPEN` - Main spawner GUI opened
- `SPAWNER_STORAGE_OPEN` - Storage GUI opened
- `SPAWNER_STACKER_OPEN` - Stacker GUI opened

### Player Actions
- `SPAWNER_EXP_CLAIM` - Experience claimed from spawner
- `SPAWNER_SELL_ALL` - Items sold from spawner
- `SPAWNER_SELL_AND_CLAIM` - Items sold and experience claimed

### Commands
- `COMMAND_EXECUTE_PLAYER` - Command executed by player
- `COMMAND_EXECUTE_CONSOLE` - Command executed by console
- `COMMAND_EXECUTE_RCON` - Command executed by RCON

### Other Events
- `SPAWNER_EGG_CHANGE` - Entity type changed
- `LOOT_GENERATED` - Loot generation event
- `HOPPER_COLLECT` - Hopper collection
- `ADMIN_GIVE` - Admin gave spawner to player
- `ADMIN_REMOVE` - Admin removed spawner
- `CONFIG_RELOAD` - Configuration reloaded

## Log Formats

### Human-Readable Format
```
[2025-10-12 18:30:45] Spawner placed | Player: Steve | Location: world (100, 64, 200) | Entity: ZOMBIE | quantity=1
[2025-10-12 18:31:20] Storage GUI opened | Player: Steve | Location: world (100, 64, 200) | Entity: ZOMBIE | page=1 total_pages=1
```

### JSON Format
```json
{"timestamp":"2025-10-12 18:30:45","timestamp_ms":1697134245000,"event_type":"SPAWNER_PLACE","description":"Spawner placed","player":"Steve","player_uuid":"069a79f4-44e9-4726-a5be-fca90e38aaf5","location":{"world":"world","x":100,"y":64,"z":200},"entity_type":"ZOMBIE","metadata":{"quantity":1}}
{"timestamp":"2025-10-12 18:31:20","timestamp_ms":1697134280000,"event_type":"SPAWNER_STORAGE_OPEN","description":"Storage GUI opened","player":"Steve","player_uuid":"069a79f4-44e9-4726-a5be-fca90e38aaf5","location":{"world":"world","x":100,"y":64,"z":200},"entity_type":"ZOMBIE","metadata":{"page":1,"total_pages":1}}
```

## Log File Management

### Location
Log files are stored in `plugins/SmartSpawner/logs/spawner/` by default (configurable).

### File Naming
- Human-readable: `spawner-YYYY-MM-DD.log`
- JSON format: `spawner-YYYY-MM-DD.json`
- Rotated files: `spawner-YYYY-MM-DD_HH-mm-ss.log` or `.json`

### Rotation
Logs are automatically rotated when they exceed the configured size (`max_log_size_mb`). Oldest files are deleted when the number of files exceeds `max_log_files`.

## Programmatic Usage

### Direct Logging
```java
SmartSpawner plugin = SmartSpawner.getInstance();
SpawnerActionLogger logger = plugin.getSpawnerActionLogger();

// Simple logging
logger.log(SpawnerEventType.LOOT_GENERATED, builder -> 
    builder.location(location)
        .entityType(EntityType.ZOMBIE)
        .metadata("items_generated", 10)
);

// Player action logging
logger.log(SpawnerEventType.SPAWNER_GUI_OPEN, builder -> 
    builder.player(player.getName(), player.getUniqueId())
        .location(spawner.getSpawnerLocation())
        .entityType(spawner.getEntityType())
);
```

### Custom Event Logging
The logging system automatically logs all spawner-related events through the `SpawnerAuditListener`. For custom logging:

```java
SpawnerLogEntry entry = new SpawnerLogEntry.Builder(SpawnerEventType.CUSTOM_EVENT)
    .player(playerName, playerUuid)
    .location(location)
    .entityType(entityType)
    .metadata("custom_key", "custom_value")
    .build();

logger.log(entry);
```

## Performance Considerations

1. **Async Logging**: Always use `async: true` in production to prevent blocking the main thread
2. **Event Filtering**: Only log necessary events to reduce I/O operations
3. **File Rotation**: Configure appropriate `max_log_size_mb` based on your server activity
4. **Console Output**: Disable `console_output` in production unless debugging

## Troubleshooting

### Logs Not Being Created
1. Check that `enabled: true` in config.yml
2. Verify plugin has write permissions to the log directory
3. Ensure at least one event type is in the `logged_events` list

### Performance Issues
1. Disable `console_output` if enabled
2. Reduce the number of logged events
3. Increase `max_log_size_mb` to reduce rotation frequency
4. Verify `async: true` is set

### Missing Events
1. Check that the event type is included in `logged_events`
2. Verify `log_all_events` is false if using selective logging
3. Ensure the event is not being cancelled by another plugin

## Best Practices

1. **Production Setup**:
   - Enable only critical events (place, break, admin actions)
   - Use JSON format for easier parsing
   - Set reasonable rotation limits (10-20 files, 10-20 MB each)
   - Keep `async: true`

2. **Debugging Setup**:
   - Enable all events with `log_all_events: true`
   - Use human-readable format
   - Enable `console_output: true`
   - Lower file size limits for more frequent rotation

3. **Archive Old Logs**:
   - Periodically backup and remove old log files
   - Consider external log aggregation tools for long-term storage
   - Use log analysis tools for JSON formatted logs

## Integration with External Tools

### Log Aggregation
JSON formatted logs can be easily imported into:
- Elasticsearch + Kibana
- Splunk
- Graylog
- Logstash

### Analysis Tools
- `jq` for command-line JSON parsing
- Python scripts for custom analysis
- Grafana for visualization

Example `jq` query to find all spawner placements by a specific player:
```bash
cat spawner-2025-10-12.json | jq 'select(.event_type == "SPAWNER_PLACE" and .player == "Steve")'
```

## Future Enhancements

Potential future additions:
- Database backend support (MySQL, MongoDB)
- Remote logging to external services
- Web dashboard for log visualization
- Real-time alerting for suspicious activities
- Export to CSV/Excel formats
