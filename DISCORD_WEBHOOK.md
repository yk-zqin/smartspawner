# Discord Webhook Integration

## Overview
The Discord webhook integration allows you to send beautiful, formatted log messages from SmartSpawner directly to your Discord server. This provides real-time notifications and an audit trail of all spawner-related activities.

## Features
- ✅ **Rich Embeds**: Beautiful Discord embeds with color coding
- ✅ **Player Avatars**: Show player heads/skins in embed thumbnails
- ✅ **Configurable**: Fully customizable titles, descriptions, colors, and fields
- ✅ **Placeholder Support**: Dynamic content with extensive placeholder system
- ✅ **Rate Limiting**: Built-in protection against Discord API rate limits
- ✅ **Async Processing**: Non-blocking webhook delivery
- ✅ **Error Handling**: Robust error handling with automatic retry
- ✅ **Memory Safe**: Proper resource cleanup and queue management

## Setup Guide

### 1. Create Discord Webhook
1. Go to your Discord server
2. Navigate to: Server Settings → Integrations → Webhooks
3. Click "Create Webhook" or "New Webhook"
4. Choose the channel where logs should be sent
5. Copy the webhook URL
6. (Optional) Customize the webhook name and avatar

### 2. Configure SmartSpawner
Open `config.yml` and find the `logging.discord` section:

```yaml
logging:
  discord:
    enabled: true  # Enable Discord logging
    webhook_url: "YOUR_WEBHOOK_URL_HERE"  # Paste your webhook URL
    show_player_head: true  # Show player avatars
```

### 3. Choose Events to Log
Configure which events should be sent to Discord:

```yaml
discord:
  log_all_events: false  # Set to true to log everything
  logged_events:
    - COMMAND_EXECUTE_PLAYER
    - COMMAND_EXECUTE_CONSOLE
    - SPAWNER_PLACE
    - SPAWNER_BREAK
    # Add more events as needed
```

## Configuration

### Embed Customization

#### Title
```yaml
embed:
  title: "🔔 Spawner Action Log"
```

#### Description
```yaml
embed:
  description: "{description}"  # Uses event description
  # Or custom: "{player} performed {description} at {location}"
```

#### Footer
```yaml
embed:
  footer: "SmartSpawner Logger"
```

### Color Scheme
Colors are in hexadecimal format (without #):

```yaml
embed:
  colors:
    COMMAND: "5865F2"   # Blurple for commands
    PLACE: "57F287"     # Green for placements
    BREAK: "ED4245"     # Red for breaks/explosions
    STACK: "FEE75C"     # Yellow for stacking
    DEFAULT: "5865F2"   # Default color
```

You can also set colors for specific events:
```yaml
colors:
  SPAWNER_PLACE: "57F287"
  SPAWNER_BREAK: "ED4245"
  COMMAND_EXECUTE_PLAYER: "5865F2"
```

### Custom Fields
Add custom fields to embeds:

```yaml
embed:
  fields:
    - name: "Server"
      value: "Production"
      inline: true
    - name: "Action Time"
      value: "{time}"
      inline: false
```

## Placeholders

Available placeholders for customization:

| Placeholder | Description | Example |
|------------|-------------|---------|
| `{description}` | Event description | "Spawner placed" |
| `{event_type}` | Event type name | "SPAWNER_PLACE" |
| `{player}` | Player name | "Steve" |
| `{player_uuid}` | Player UUID | "069a79f4-..." |
| `{world}` | World name | "world" |
| `{x}`, `{y}`, `{z}` | Coordinates | "100", "64", "200" |
| `{location}` | Full location | "world (100, 64, 200)" |
| `{entity}` | Entity type | "PIG" |
| `{time}` | Timestamp | "2025-01-15 14:30:00" |
| `{command}` | Command name (for command events) | "give" |
| `{full_command}` | Full command (for command events) | "/ss give spawner..." |

Plus any custom metadata fields from the specific log entry.

## Event Types

### Command Events
- `COMMAND_EXECUTE_PLAYER` - Player executes command
- `COMMAND_EXECUTE_CONSOLE` - Console executes command
- `COMMAND_EXECUTE_RCON` - RCON executes command

### Spawner Lifecycle
- `SPAWNER_PLACE` - Spawner placed
- `SPAWNER_BREAK` - Spawner broken
- `SPAWNER_EXPLODE` - Spawner destroyed by explosion

### Spawner Stacking
- `SPAWNER_STACK_HAND` - Spawner stacked by hand
- `SPAWNER_STACK_GUI` - Spawner stacked via GUI
- `SPAWNER_DESTACK_GUI` - Spawner destacked via GUI

### GUI Interactions
- `SPAWNER_GUI_OPEN` - Main GUI opened
- `SPAWNER_STORAGE_OPEN` - Storage GUI opened
- `SPAWNER_STACKER_OPEN` - Stacker GUI opened

### Player Actions
- `SPAWNER_EXP_CLAIM` - Experience claimed
- `SPAWNER_SELL_ALL` - Items sold

### Storage Actions
- `SPAWNER_ITEM_TAKE_ALL` - All items taken
- `SPAWNER_ITEM_DROP` - Item dropped
- `SPAWNER_ITEMS_SORT` - Items sorted
- `SPAWNER_ITEM_FILTER` - Filter toggled
- `SPAWNER_DROP_PAGE_ITEMS` - Page items dropped

### Other
- `SPAWNER_EGG_CHANGE` - Entity type changed

## Rate Limiting

The system automatically handles Discord's rate limits:
- Maximum 25 requests per minute (safe buffer)
- Automatic queue management
- Warning when queue backs up
- Graceful degradation under load

## Best Practices

### Performance
1. **Don't log everything to Discord** - Choose important events only
2. **Use file logging for detailed audit** - Discord for notifications
3. **Monitor queue size** - Check logs for backup warnings

### Security
1. **Keep webhook URL secret** - Don't share publicly
2. **Use appropriate channel permissions** - Limit who can see logs
3. **Regularly rotate webhooks** - If compromised

### Maintenance
1. **Test configuration changes** - Use test server first
2. **Monitor webhook health** - Check Discord for delivery
3. **Review logs periodically** - Ensure proper logging

## Troubleshooting

### Webhooks Not Sending
1. Check `enabled: true` in config
2. Verify webhook URL is correct
3. Check console for errors
4. Ensure Discord channel exists
5. Verify webhook hasn't been deleted

### Rate Limit Warnings
1. Reduce logged events
2. Increase processing interval
3. Use file logging for high-volume events

### Missing Information
1. Check placeholder syntax `{placeholder}`
2. Verify event includes that data
3. Review console for errors

### Player Heads Not Showing
1. Check `show_player_head: true`
2. Ensure player has a valid username
3. Verify Mineatar service is accessible

## Example Configurations

### Minimal Setup (Commands Only)
```yaml
discord:
  enabled: true
  webhook_url: "YOUR_URL"
  logged_events:
    - COMMAND_EXECUTE_PLAYER
    - COMMAND_EXECUTE_CONSOLE
```

### Audit Trail (All Major Events)
```yaml
discord:
  enabled: true
  webhook_url: "YOUR_URL"
  logged_events:
    - SPAWNER_PLACE
    - SPAWNER_BREAK
    - SPAWNER_EXPLODE
    - COMMAND_EXECUTE_PLAYER
    - COMMAND_EXECUTE_CONSOLE
    - SPAWNER_STACK_GUI
    - SPAWNER_DESTACK_GUI
```

### Full Monitoring
```yaml
discord:
  enabled: true
  webhook_url: "YOUR_URL"
  log_all_events: true  # Log everything
```

## Memory Leak Prevention

The implementation includes several safeguards:
1. **Bounded Queues**: ConcurrentLinkedQueue with monitoring
2. **Resource Cleanup**: Proper connection closure
3. **Task Cancellation**: Clean shutdown handling
4. **Thread Safety**: ThreadLocal for date formatting
5. **Limited Flush**: Max 10 entries on shutdown

## Technical Details

### Architecture
- Async webhook processing (non-blocking)
- Separate queue from file logging
- Rate limiting with atomic counters
- Graceful degradation under load

### HTTP Implementation
- 5-second connection timeout
- 5-second read timeout
- Proper error handling
- Automatic retry from queue

### Thread Safety
- ConcurrentLinkedQueue for thread-safe operations
- AtomicBoolean for shutdown flag
- AtomicLong for rate limit counters
- ThreadLocal for SimpleDateFormat

## Support

For issues or questions:
1. Check console logs for errors
2. Verify configuration syntax
3. Test with minimal config
4. Report issues on GitHub with logs
