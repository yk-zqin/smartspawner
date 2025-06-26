package github.nighter.smartspawner.hooks;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.List;

public class PluginCompatibilityHandler implements Listener {
    private final SmartSpawner plugin;

    public PluginCompatibilityHandler(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (plugin.hasShopIntegration()) {
            plugin.debug("Shop integration already active, ignoring plugin enable event for: " +
                    event.getPlugin().getName());
            return;
        }

        String pluginName = event.getPlugin().getName();

        // Support EconomyShopGUI for double integration
        if (pluginName.equalsIgnoreCase("EconomyShopGUI") ||
                pluginName.equalsIgnoreCase("EconomyShopGUI-Premium")) {

            Scheduler.runTaskLater(() -> {
                plugin.getItemPriceManager().reload();
                plugin.getEntityLootRegistry().loadConfigurations();
                reloadSpawnerLootConfigs();
            }, 20L); // Run after 1 second to ensure the plugin is fully loaded
        }
    }

    private void reloadSpawnerLootConfigs() {
        List<SpawnerData> allSpawners = plugin.getSpawnerManager().getAllSpawners();
        for (SpawnerData spawner : allSpawners) {
            try {
                spawner.reloadLootConfig();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reload loot config for spawner " +
                        spawner.getSpawnerId() + ": " + e.getMessage());
            }
        }
    }
}