package github.nighter.smartspawner.spawner.natural;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;

public class NaturalSpawnerListener implements Listener {
    private final SmartSpawner plugin;
    private final SpawnerManager spawnerManager;
    private static final int CHECK_RADIUS = 4;

    public NaturalSpawnerListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerManager = plugin.getSpawnerManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (event.getSpawner() == null) return;

        SpawnerData smartSpawner = spawnerManager.getSpawnerByLocation(event.getSpawner().getLocation());

        if (smartSpawner != null) {
            event.setCancelled(true);
        } else {
            // This is a natural spawner - check if natural spawning is allowed
            if (!plugin.getConfig().getBoolean("natural_spawner.spawn_mobs", true)) {
                event.setCancelled(true);
            }
        }
    }

    // Prevent chickens from zombie chicken jockeys
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        // Only check for chickens for maximum performance
        if (entity.getType() != EntityType.CHICKEN) {
            return;
        }

        // Get spawn reason if this is a CreatureSpawnEvent
        CreatureSpawnEvent.SpawnReason spawnReason = null;
        if (event instanceof CreatureSpawnEvent) {
            spawnReason = ((CreatureSpawnEvent) event).getSpawnReason();
        }

        // Allow chickens spawned from breeding
        if (spawnReason == CreatureSpawnEvent.SpawnReason.BREEDING) {
            return;
        }

        if (spawnReason == CreatureSpawnEvent.SpawnReason.EGG) {
            return;
        }

        if (spawnReason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }

        // Check if there's a zombie spawner nearby
        if (isNearZombieSpawner(entity.getLocation())) {
            // Cancel chicken spawn if near zombie spawner (unless from breeding)
            event.setCancelled(true);
        }
    }

    // Check if there's a zombie spawner nearby
    private boolean isNearZombieSpawner(Location location) {
        // Check in a cube area around the location
        for (int x = -CHECK_RADIUS; x <= CHECK_RADIUS; x++) {
            for (int y = -CHECK_RADIUS; y <= CHECK_RADIUS; y++) {
                for (int z = -CHECK_RADIUS; z <= CHECK_RADIUS; z++) {
                    Location checkLoc = new Location(
                            location.getWorld(),
                            location.getBlockX() + x,
                            location.getBlockY() + y,
                            location.getBlockZ() + z
                    );

                    SpawnerData spawner = spawnerManager.getSpawnerByLocation(checkLoc);
                    if (spawner != null && (spawner.getEntityType() == EntityType.ZOMBIE || spawner.getEntityType() == EntityType.ZOMBIFIED_PIGLIN)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
