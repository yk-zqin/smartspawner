package github.nighter.smartspawner.spawner.interactions.destroy;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerExplodeEvent;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.utils.SpawnerFileHandler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

public class SpawnerExplosionListener implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private final SpawnerFileHandler spawnerFileHandler;

    public SpawnerExplosionListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
        this.spawnerFileHandler = plugin.getSpawnerFileHandler();
    }

    @EventHandler
    public void onEntityExplosion(EntityExplodeEvent event) {
        List<Block> blocksToRemove = new ArrayList<>();

        for (Block block : event.blockList()) {
            if (block.getType() == Material.SPAWNER) {
                SpawnerData spawnerData = this.spawnerManager.getSpawnerByLocation(block.getLocation());

                if (spawnerData != null) {
                    SpawnerExplodeEvent e = null;
                    if (plugin.getConfig().getBoolean("spawner_properties.default.protect_from_explosions",true)) {
                        blocksToRemove.add(block);
                        plugin.getSpawnerGuiViewManager().closeAllViewersInventory(spawnerData);
                        if(SpawnerExplodeEvent.getHandlerList().getRegisteredListeners().length != 0)
                            e = new SpawnerExplodeEvent(event.getEntity(), spawnerData.getSpawnerLocation(), 1, false);
                    } else {
                        spawnerData.setSpawnerStop(true);
                        String spawnerId = spawnerData.getSpawnerId();
                        if(SpawnerExplodeEvent.getHandlerList().getRegisteredListeners().length != 0)
                            e = new SpawnerExplodeEvent(event.getEntity(), spawnerData.getSpawnerLocation(), 1, true);
                        spawnerManager.removeSpawner(spawnerId);
                        spawnerFileHandler.markSpawnerDeleted(spawnerId);
                    }
                    if(e != null)
                        Bukkit.getPluginManager().callEvent(e);
                } else {
                    // If no spawner data is found, we still want the spawner block to be destroyed
                    // So don't add it to the blocksToRemove list
                }
            }
        }

        // Remove the spawner blocks that should not be destroyed from the explosion list
        event.blockList().removeAll(blocksToRemove);
    }
}
