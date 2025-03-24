<div align="center">
  
<br>
  
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<img src="https://github.com/user-attachments/assets/05e5f050-b661-40ed-a400-bcb7eea07430" alt="GUI Spawner Plugin for Minecraft Servers" />

<br>

[![Release](https://img.shields.io/github/v/release/ptthanh02/Smart-Spawner-Plugin?logo=github&logoColor=white&label=release&labelColor=%230D597F&color=%23116BBF)](https://github.com/ptthanh02/Smart-Spawner-Plugin/releases/latest)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/smart-spawner-plugin?logo=modrinth&logoColor=white&label=downloads&labelColor=%23139549&color=%2318c25f)](https://modrinth.com/plugin/smart-spawner-plugin)
[![Spigot Downloads](https://img.shields.io/spiget/downloads/120743?logo=spigotmc&logoColor=white&label=spigot%20downloads&labelColor=%23ED8106&color=%23FF994C)](https://www.spigotmc.org/resources/smart-spawner-gui-spawner-plugin%E2%9C%A8-1-21-1-21-3-%EF%B8%8F.120743/)
[![License](https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-brightgreen.svg)](LICENSE)

[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/smart-spawner-plugin)
[![Spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg)](https://www.spigotmc.org/resources/120743/)
[![Hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg)](https://hangar.papermc.io/ptthanh02/SmartSpawner)

</div>

<br>

## 🚀 Getting Started

### System Requirements

- **Server**: Paper, Folia or compatible forks
- **Minecraft Version**: 1.20 - 1.21.4
- **Java**: Java 21 or newer

### 📥 Installation

1. Download the latest version from [Modrinth](https://modrinth.com/plugin/smart-spawner-plugin) or [Spigot](https://www.spigotmc.org/resources/120743/)
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Configure settings in `plugins/SmartSpawner/config.yml`


## 🎮 Commands & Permissions

### Core Commands
> **Base Command:** `/smartspawner` (Aliases: `/ss`, `/spawner`)

| Command | Description                       | Permission | Default |
|---------|-----------------------------------|------------|---------|
| `/ss reload` | Reload plugin configuration       | `smartspawner.reload` | OP |
| `/ss give <player> <type> <amount>` | Give spawners to players          | `smartspawner.give` | OP |
| `/ss list` | Open admin spawner management GUI | `smartspawner.list` | OP |
| `/ss hologram` | Toggle hologram visibility        | `smartspawner.hologram` | OP |

### Player Permissions

| Permission | Description                            | Default |
|------------|----------------------------------------|---------|
| `smartspawner.stack` | Allow spawner stacking                 | true |
| `smartspawner.break` | Allow spawner breaking                 | true |
| `smartspawner.sellall` | Allow selling storage items directly   | true |
| `smartspawner.changetype` | Allow changing spawner types with eggs | OP |

## 🌍 Translations

| Language | Locale Code | Contributor                                     | Version  |
|----------|-------------|-------------------------------------------------|----------|
| [English](https://github.com/ptthanh02/Smart-Spawner/blob/main/src/main/resources/messages/en_US.yml) | `en_US` | Core language                                   | `Latest` |
| [Vietnamese](https://github.com/ptthanh02/Smart-Spawner/blob/main/src/main/resources/messages/vi_VN.yml) | `vi_VN` | ptthanh02                                       | `Latest` |
| [Chinese Simplified](https://github.com/ptthanh02/Smart-Spawner/blob/main/src/main/resources/messages/zh_CN.yml) | `zh_CN` | [SnowCutieOwO](https://github.com/SnowCutieOwO) | `v1.2.3` |

> 🔍 **Want to help translate?** Check our [Translation Guide](https://github.com/ptthanh02/Smart-Spawner-Plugin/wiki/Translation-Guide)

## 📊 Usage Statistics

[![bStats](https://bstats.org/signatures/bukkit/SmartSpawner.svg)](https://bstats.org/plugin/bukkit/SmartSpawner)


## 💻 Developer API
<details>
<summary>Smart Spawner API</summary>

### Installation & Documentation

For API installation instructions, usage examples, and complete documentation, please visit:
[Smart Spawner API Package](https://github.com/ptthanh02/Smart-Spawner/packages/2421916)

</details>

## 🤝 Contributing

We welcome contributions! Here's how you can help:

1. 🍴 Fork the repository
2. 🌿 Create your feature branch (`git checkout -b feature/amazing-feature`)
3. 💾 Commit your changes (`git commit -m 'Add amazing feature'`)
4. 📤 Push to the branch (`git push origin feature/amazing-feature`)
5. 🔄 Create a Pull Request

## 💬 Community & Support

[![Discord Banner](https://img.shields.io/discord/1299353023532896296?style=for-the-badge&logo=discord&logoColor=white&label=Join%20our%20Discord&color=5865F2)](https://discord.gg/zrnyG4CuuT)

- 🎮 [Discord Community](https://discord.gg/zrnyG4CuuT)
- 🐛 [Issue Tracker](https://github.com/ptthanh02/Smart-Spawner-Plugin/issues)
- 📚 [Wiki Documentation](https://github.com/ptthanh02/Smart-Spawner-Plugin/wiki)

## 📜 License

This project is licensed under the **CC BY-NC-SA 4.0 License** - see the [LICENSE](LICENSE) file for details.
