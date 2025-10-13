# Discord Webhook Field Icons Reference

This document lists all the emoji icons automatically added to Discord webhook embed fields.

## Default Field Icons

These icons are automatically added to the standard spawner log entry fields:

| Icon | Field Name | Description |
|------|------------|-------------|
| 👤 | Player | Player who performed the action |
| 📍 | Location | World coordinates of the spawner |
| 🐾 | Entity Type | Type of entity in the spawner (now in Title Case) |

## Metadata Field Icons

These icons are automatically added based on the metadata field name:

| Icon | Field Name Contains | Examples | Description |
|------|---------------------|----------|-------------|
| ⚙️ | command | Command, Full Command, Command Args | Command-related information |
| 🔢 | amount, count | Amount, Stack Count, Spawn Count | Numeric quantities |
| 💰 | price, cost, money | Price, Total Cost, Money Earned | Economy/financial data |
| ✨ | exp, experience | Experience, Exp Amount, Exp Gained | Experience points |
| 📚 | stack | Stack Size, Stack Amount, Stacked | Stacking operations |
| 🏷️ | type | Spawner Type, Action Type, Event Type | Type classifications |
| ℹ️ | (other) | Any other metadata field | General information |

## Examples

### Command Execution
```
🔔 Spawner Action Log
Command executed by player
━━━━━━━━━━━━━━━━━━━━━━━━
👤 Player        │ Steve
⚙️ Command       │ give
⚙️ Full Command  │ /ss give Steve pig_spawner 1
━━━━━━━━━━━━━━━━━━━━━━━━
```

### Spawner Placement
```
🔔 Spawner Action Log
Spawner placed
━━━━━━━━━━━━━━━━━━━━━━━━
👤 Player        │ Alex
📍 Location      │ world (100, 64, 200)
🐾 Entity Type   │ Pig
━━━━━━━━━━━━━━━━━━━━━━━━
```

### Spawner Stack
```
🔔 Spawner Action Log
Spawner stacked via GUI
━━━━━━━━━━━━━━━━━━━━━━━━
👤 Player        │ Alex
📍 Location      │ world (100, 64, 200)
🐾 Entity Type   │ Zombie Villager
📚 Stack Amount  │ 5
🔢 New Count     │ 15
━━━━━━━━━━━━━━━━━━━━━━━━
```

### Experience Claim
```
🔔 Spawner Action Log
Experience claimed from spawner
━━━━━━━━━━━━━━━━━━━━━━━━
👤 Player        │ Steve
📍 Location      │ world_nether (-50, 70, 120)
🐾 Entity Type   │ Blaze
✨ Exp Amount    │ 1250
━━━━━━━━━━━━━━━━━━━━━━━━
```

### Sell All
```
🔔 Spawner Action Log
Spawners sold using /ss sellall
━━━━━━━━━━━━━━━━━━━━━━━━
👤 Player        │ Alex
🔢 Amount Sold   │ 3
💰 Total Price   │ $15,000
🐾 Entity Type   │ Pig
━━━━━━━━━━━━━━━━━━━━━━━━
```

## Entity Type Formatting

Entity types are now displayed in **Title Case** for better readability:

| Raw Value | Displayed As |
|-----------|--------------|
| PIG | Pig |
| COW | Cow |
| ZOMBIE_VILLAGER | Zombie Villager |
| IRON_GOLEM | Iron Golem |
| PIGLIN_BRUTE | Piglin Brute |
| ENDER_DRAGON | Ender Dragon |
| WITHER | Wither |

## Customization

The icon system works automatically with any metadata field. When you add custom metadata to log entries, the system will:

1. Format the field name to Title Case
2. Check if it matches any icon keywords
3. Add the appropriate icon prefix
4. Display it in the Discord embed

Example:
```java
// In your code
logEntry.metadata("total_earnings", "$50,000");
logEntry.metadata("bonus_multiplier", "1.5x");

// In Discord
💰 Total Earnings  │ $50,000
ℹ️ Bonus Multiplier │ 1.5x
```

## Player Avatar Changes

Player avatars now use the **Mineatar API** instead of Crafatar:

- **Old**: `https://crafatar.com/avatars/{uuid}?overlay=true`
- **New**: `https://api.mineatar.io/face/{username}`

The new API:
- Uses player **username** instead of UUID
- Provides high-quality face rendering
- More reliable and straightforward to use
