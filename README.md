<div align="center">

# Smart Spawner - GUI Spawner Plugin ✨

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/smart-spawner-plugin?logo=modrinth&logoColor=white&label=downloads&labelColor=%23139549&color=%2318c25f)](https://modrinth.com/plugin/smart-spawner-plugin)
[![Spigot Downloads](https://img.shields.io/spiget/downloads/120743?logo=spigotmc&logoColor=white&label=spigot%20downloads&labelColor=%23ED8106&color=%23FF994C)](https://www.spigotmc.org/resources/smart-spawner-gui-spawner-plugin%E2%9C%A8-1-21-1-21-3-%EF%B8%8F.120743/)
[![Discord](https://img.shields.io/discord/1299353023532896296?label=Discord&logo=discord)](https://discord.gg/zrnyG4CuuT)
[![License](https://img.shields.io/badge/license-CC%20BY--NC--SA%204.0-brightgreen.svg)](LICENSE)

**A powerful and intuitive GUI-based spawner plugin for Minecraft servers**

[![Modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg)](https://modrinth.com/plugin/smart-spawner-plugin)
[![Spigot](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/spigot_vector.svg)](https://www.spigotmc.org/resources/120743/)

</div>

![Smart Spawner Banner](https://i.imgur.com/your-banner-image.png)

## ✨ Key Features

- **Intuitive GUI** - Allows players effortlessly view, manage, and interact with spawners.
- **Storage System** - Built-in storage GUI for spawner drops
- **Sell Feature** - Sell items directly from storage GUI from other shop plugins
- **Spawner Stacking** - Stack multiple spawners to save space
- **Hologram Support** - Display important information above spawners
- **Extensive Customization** - Configure spawner behavior, drops, and more
- **Multi-Language Support** - Easily customize all plugin messages
- **Performance Optimized** - Minimal impact on server performance

## 🚀 Getting Started

### Prerequisites
- Minecraft Server (Bukkit, Paper, or compatible fork)
- Using a newer fork like **Folia**? Check out [Smart Spawner for Folia (Beta)
](https://github.com/maiminhdung/Smart-Spawner-Plugin)
- Server version 1.20 - 1.21.4

### Installation
1. Download the latest version from [Modrinth](https://modrinth.com/plugin/smart-spawner-plugin) or [SpigotMC](https://www.spigotmc.org/resources/120743/)
2. Place the `.jar` file in your server's `plugins` folder
3. Restart your server
4. Edit the generated configuration files to customize the plugin

## 🌍 Supported Translations
| Language | Locale Code | Contributor | Version | Status |
|----------|-------------|-------------|---------|--------|
| English (Default) | `en_US.yml` | - | `latest` | ✅ Complete |
| Vietnamese | `vi_VN.yml` | - | `latest` | ✅ Complete |
| Chinese Simplified | `zh_CN.yml` | [SnowCutieOwO](https://github.com/SnowCutieOwO) | `latest` | ✅ Complete |

**Want to contribute a translation?** Fork the repository, create your locale file based on `en_US.yml`, and submit a pull request!

### Translation Guidelines
When contributing translations:
1. Use the `en_US.yml` file as a template
2. Maintain all placeholders (%variable%)
3. Keep formatting codes (like &e, &7)
4. Test your translation in-game if possible

## 📝 Commands & Permissions

<details> <summary>🛠️ Commands</summary>

**Aliases:** `/ss`, `/spawner`, `/smartspawner`

| Command | Description | Permission |
|---------|-------------|------------|
| `/smartspawner reload` | Reload the plugin settings | `smartspawner.reload` |
| `/smartspawner give <player> <mobtype> <amount>` | Give a spawner to a player or yourself | `smartspawner.give` |
| `/smartspawner list` | Open the spawner list GUI for admin management | `smartspawner.list` |
| `/smartspawner hologram` | Toggle hologram display for spawners | `smartspawner.hologram` |

</details>

<br>

<details> <summary>📜 Permissions</summary>

| Permission | Description | Default |
|------------|-------------|---------|
| `smartspawner.reload` | Allows reloading the plugin | OP |
| `smartspawner.give` | Allows giving spawners to yourself or other players | OP |
| `smartspawner.list` | Allows accessing the spawner list command | OP |
| `smartspawner.hologram` | Allows toggling hologram display for spawners | OP |
| `smartspawner.changetype` | Permits spawner type changes using spawn eggs | OP |
| `smartspawner.stack` | Allows stacking in the GUI or by right-click | true |
| `smartspawner.break` | Allows players to break spawners | true |
| `smartspawner.sellall` | Allows selling items in the spawner storage GUI | true |

</details>

## 📖 Documentation
Full documentation will be available soon. Stay tuned!

## 🤝 Contributing
SmartSpawner is an open-source project, and we welcome contributions from the community to help improve and expand its features. Whether you want to fix a bug, add a new feature, or improve documentation, your contributions are highly valued.

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add some amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

## 📢 Support & Community
Need help or found a bug? Feel free to:
- Join our [Discord Server](https://discord.gg/zrnyG4CuuT)
- Report bugs on [GitHub Issues](https://github.com/ptthanh02/Smart-Spawner-Plugin/issues)
- Check our [FAQ Section](https://github.com/ptthanh02/Smart-Spawner-Plugin/wiki/FAQ)

## 📄 License
This project is licensed under the **CC BY-NC-SA 4.0 License**. See the [LICENSE](LICENSE) file for more details.

## 📊 bStats Statistics
See real-time usage statistics on **[bStats](https://bstats.org/plugin/bukkit/SmartSpawner)**:  
[![bStats Graph](https://bstats.org/signatures/bukkit/SmartSpawner.svg)](https://bstats.org/plugin/bukkit/SmartSpawner)

## 📊 Server Showcase

Are you using Smart Spawner on your server? Let us know to be featured here!
