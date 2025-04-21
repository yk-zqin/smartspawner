package github.nighter.smartspawner.commands.hologram;

import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class HologramCommand implements CommandExecutor {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final LanguageManager languageManager;
    private final MessageService messageService;

    public HologramCommand(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("smartspawner.hologram")) {
            messageService.sendMessage(sender, "no_permission");
            return true;
        }

        // Toggle hologram state
        boolean newValue = !plugin.getConfig().getBoolean("hologram.enabled");

        // Get main config and set new value
        FileConfiguration mainConfig = plugin.getConfig();
        mainConfig.set("hologram.enabled", newValue);

        // Save configs and reload
        plugin.saveConfig();
        plugin.reloadConfig();

        // Update all holograms
        spawnerManager.refreshAllHolograms();

        // Send message to player using MessageService
        String messageKey = newValue ? "command_hologram_enabled" : "command_hologram_disabled";
        messageService.sendMessage(sender, messageKey);

        // Log debug message with MessageService if console logging is needed
        // plugin.getLogger().info("Holograms " + (newValue ? "enabled" : "disabled") + " by " + sender.getName());

        return true;
    }
}