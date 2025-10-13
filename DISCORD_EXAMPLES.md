# Discord Webhook Integration - Visual Examples

## Example 1: Command Execution Log

### Configuration
```yaml
discord:
  enabled: true
  show_player_head: true
  embed:
    title: "🔔 Spawner Action Log"
    description: "{description}"
    colors:
      COMMAND: "5865F2"
```

### Result
```
┌─────────────────────────────────────────────┐
│ 🔔 Spawner Action Log                [BLUE] │
├─────────────────────────────────────────────┤
│ Command executed by player           [IMG]  │
│                                      [HEAD] │
│ ┌─────────────┬─────────────────────┐      │
│ │ 👤 Player   │ Steve               │      │
│ │ ⚙️ Command   │ give                │      │
│ │ ⚙️ Full Cmd  │ /ss give spawner... │      │
│ └─────────────┴─────────────────────┘      │
│                                             │
│ SmartSpawner Logger          2025-01-15... │
└─────────────────────────────────────────────┘
```

## Example 2: Spawner Placement

### Configuration
```yaml
discord:
  show_player_head: true
  embed:
    colors:
      PLACE: "57F287"  # Green
```

### Result
```
┌─────────────────────────────────────────────┐
│ 🔔 Spawner Action Log              [GREEN]  │
├─────────────────────────────────────────────┤
│ Spawner placed                       [IMG]  │
│                                     [HEAD]  │
│ ┌──────────────┬──────────────────────────┐│
│ │ 👤 Player    │ Alex            │        ││
│ │ 📍 Location  │ world (100, 64, 200)    ││
│ │ 🐾 Entity    │ Pig             │        ││
│ └──────────────┴──────────────────────────┘│
│                                             │
│ SmartSpawner Logger          2025-01-15... │
└─────────────────────────────────────────────┘
```

## Example 3: Spawner Break/Explosion

### Configuration
```yaml
discord:
  embed:
    colors:
      BREAK: "ED4245"  # Red
```

### Result
```
┌─────────────────────────────────────────────┐
│ 🔔 Spawner Action Log                [RED]  │
├─────────────────────────────────────────────┤
│ Spawner destroyed by explosion       [IMG]  │
│                                     [HEAD]  │
│ ┌──────────────┬──────────────────────────┐│
│ │ 📍 Location  │ world_nether (45, 64, 89)││
│ │ 🐾 Entity    │ Blaze           │        ││
│ └──────────────┴──────────────────────────┘│
│                                             │
│ SmartSpawner Logger          2025-01-15... │
└─────────────────────────────────────────────┘
```

## Example 4: Custom Fields

### Configuration
```yaml
discord:
  embed:
    title: "🎮 {player} performed an action"
    description: "**{description}** in {world}"
    fields:
      - name: "Server"
        value: "Production"
        inline: true
      - name: "Region"
        value: "US-East"
        inline: true
```

### Result
```
┌─────────────────────────────────────────────┐
│ 🎮 Steve performed an action       [BLUE]  │
├─────────────────────────────────────────────┤
│ Command executed by player in world  [IMG]  │
│                                     [HEAD]  │
│ ┌──────────────┬──────────────────────────┐│
│ │ 👤 Player    │ Steve           │        ││
│ │ ⚙️ Command   │ reload          │        ││
│ │ Server       │ Production      │ Region ││
│ │              │                 │ US-East││
│ └──────────────┴──────────────────────────┘│
│                                             │
│ SmartSpawner Logger          2025-01-15... │
└─────────────────────────────────────────────┘
```

## Color Reference

### Pre-configured Colors

| Event Category | Color | Hex Code | Example |
|---------------|-------|----------|---------|
| Commands | Blurple | `5865F2` | `/ss give`, `/ss reload` |
| Placement | Green | `57F287` | Spawner placed |
| Break/Explode | Red | `ED4245` | Spawner broken, exploded |
| Stacking | Yellow | `FEE75C` | Stack/destack operations |
| Default | Blurple | `5865F2` | Any other event |

### Custom Event Colors

You can set specific colors for any event:

```yaml
colors:
  SPAWNER_PLACE: "57F287"        # Green
  SPAWNER_BREAK: "ED4245"        # Red
  SPAWNER_EXPLODE: "ED4245"      # Red
  COMMAND_EXECUTE_PLAYER: "5865F2"   # Blurple
  SPAWNER_STACK_GUI: "FEE75C"    # Yellow
  SPAWNER_EXP_CLAIM: "EB459E"    # Pink
  SPAWNER_SELL_ALL: "57F287"     # Green
```

## Features Showcase

### 1. Player Avatars
- Automatic player head/skin display
- Uses Mineatar API
- High-quality face rendering
- Fallback to no image if unavailable

### 2. Dynamic Placeholders
- `{player}` - Player name
- `{world}` - World name
- `{location}` - Full location string
- `{x}`, `{y}`, `{z}` - Individual coordinates
- `{entity}` - Entity type
- `{time}` - Formatted timestamp
- `{description}` - Event description
- Plus all metadata fields!

### 3. Flexible Layouts
- Inline fields for compact display
- Full-width fields for detailed info
- Custom field ordering
- Mix of automatic and custom fields

### 4. Rate Limiting Protection
- Max 25 requests/minute (safe buffer)
- Automatic queue management
- Warning when backing up
- Graceful degradation

## Real-World Use Cases

### Use Case 1: Admin Monitoring
Monitor all admin commands in a dedicated channel:
```yaml
logged_events:
  - COMMAND_EXECUTE_PLAYER
  - COMMAND_EXECUTE_CONSOLE
  - COMMAND_EXECUTE_RCON
```

### Use Case 2: Grief Prevention
Track spawner destruction:
```yaml
logged_events:
  - SPAWNER_BREAK
  - SPAWNER_EXPLODE
```

### Use Case 3: Economy Tracking
Monitor spawner economy:
```yaml
logged_events:
  - SPAWNER_PLACE
  - SPAWNER_BREAK
  - SPAWNER_SELL_ALL
  - SPAWNER_EXP_CLAIM
```

### Use Case 4: Audit Trail
Complete audit trail:
```yaml
log_all_events: true
```

## Performance Impact

- ✅ **Minimal**: Async processing, non-blocking
- ✅ **Efficient**: Batched queue processing
- ✅ **Safe**: Rate limiting prevents API abuse
- ✅ **Reliable**: Error handling and retry logic

## Setup Time

1. **Create webhook**: 1 minute
2. **Configure plugin**: 2 minutes
3. **Test**: 1 minute
4. **Total**: ~5 minutes

Start monitoring your server's spawner activity in Discord today! 🚀
