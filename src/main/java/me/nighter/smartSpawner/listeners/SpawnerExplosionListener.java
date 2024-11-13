package me.nighter.smartSpawner.listeners;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.SpawnerManager;
import me.nighter.smartSpawner.utils.SpawnerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

public class SpawnerExplosionListener implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final ConfigManager configManager;

    public SpawnerExplosionListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (block.getType() == Material.SPAWNER) {
                SpawnerData spawnerData = this.spawnerManager.getSpawnerByLocation(block.getLocation());

                if (spawnerData != null) {
                    if (configManager.isAllowGrief()) {
                        String spawnerId = spawnerData.getSpawnerId();

                        spawnerManager.removeSpawner(spawnerId);
                        plugin.getRangeChecker().stopSpawnerTask(spawnerData);
                        plugin.getConfigManager().debug("Spawner " + spawnerId + " has been exploded.");
                    } else event.setCancelled(true);
                } else return;
            }
        }
    }
}
