package github.nighter.smartspawner.spawner.interactions.place;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerPlaceEvent;
import github.nighter.smartspawner.extras.HopperHandler;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.nms.ParticleWrapper;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.utils.SpawnerTypeChecker; // Add this import

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Handles spawner placement events, including entity type inheritance
 * and activation behavior.
 */
public class SpawnerPlaceListener implements Listener {
    private static final double PARTICLE_OFFSET = 0.5;

    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final HopperHandler hopperHandler;

    public SpawnerPlaceListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.hopperHandler = plugin.getHopperHandler();
    }

    /**
     * Handles spawner placement events, managing entity type inheritance
     * and activation behavior.
     *
     * @param event The block place event
     */
    /**
     * Handles spawner placement events, managing entity type inheritance
     * and activation behavior.
     *
     * @param event The block place event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();

        // Early return if not a spawner
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();

        // Validate spawner item
        if (!(meta instanceof BlockStateMeta)) {
            event.setCancelled(true);
            plugin.debug("Invalid spawner item: " + item.getType());
            return;
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;

        // Check if this is a vanilla or smart spawner using our utility class
        boolean isVanillaSpawner = SpawnerTypeChecker.isVanillaSpawner(item);

        // Extract entity type from spawner item
        EntityType storedEntityType = null;
        if (blockMeta.hasBlockState() && blockMeta.getBlockState() instanceof CreatureSpawner) {
            storedEntityType = ((CreatureSpawner) blockMeta.getBlockState()).getSpawnedType();
        }

        // Instead, schedule a task to get the entity type from the placed spawner
        boolean needsDeferredEntityTypeResolution = storedEntityType == null || storedEntityType == EntityType.UNKNOWN;

        if(SpawnerPlaceEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerPlaceEvent e = new SpawnerPlaceEvent(player, block.getLocation(), 1);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) {
                event.setCancelled(true);
                return;
            }
        }

        plugin.debug("Player: " + player.getName() + " placed spawner, isOp: " + player.isOp() +
                ", isVanillaSpawner: " + isVanillaSpawner + ", entityType: " +
                (needsDeferredEntityTypeResolution ? "unknown (will resolve after placement)" : storedEntityType));

        // Handle spawner setup
        if (needsDeferredEntityTypeResolution) {
            // Use a slightly longer delay to ensure the block is fully placed and initialized
            Scheduler.runLocationTaskLater(block.getLocation(), () -> {
                if (block.getType() != Material.SPAWNER) {
                    plugin.debug("Block is no longer a spawner, aborting setup");
                    return;
                }

                CreatureSpawner placedSpawner = (CreatureSpawner) block.getState();
                EntityType resolvedEntityType = placedSpawner.getSpawnedType();

                if (resolvedEntityType == null || resolvedEntityType == EntityType.UNKNOWN) {
                    plugin.debug("Failed to resolve entity type from placed spawner, using default");
                    return;
                    // resolvedEntityType = EntityType.PIG; // Default fallback entity
                }

                plugin.debug("Resolved entity type from placed spawner: " + resolvedEntityType);
                handleSpawnerSetup(block, player, resolvedEntityType, isVanillaSpawner);
            }, 3L); // Wait 3 ticks (longer than the original 2 ticks) to ensure spawner is fully initialized
        } else {
            // Original flow for known entity types
            handleSpawnerSetup(block, player, storedEntityType, isVanillaSpawner);
        }
    }

    private void handleSpawnerSetup(Block block, Player player, EntityType entityType, boolean isVanillaSpawner) {
        // Get the spawner state once
        CreatureSpawner spawner = (CreatureSpawner) block.getState();

        if (isVanillaSpawner) {
            // For vanilla spawners, just set the entity type and update
            spawner.setSpawnedType(entityType);
            spawner.update(true, false);
            return;
        }

        // For smart spawners, only use scheduler if actually needed
        // Some servers might not need this delay, so consider making it configurable
        Scheduler.runLocationTaskLater(block.getLocation(), () -> {
            // Recheck the block state in case it changed during the delay
            if (block.getType() != Material.SPAWNER) {
                return;
            }

            CreatureSpawner delayedSpawner = (CreatureSpawner) block.getState();
            EntityType finalEntityType = getEntityType(entityType, delayedSpawner);

            delayedSpawner.setSpawnedType(finalEntityType);
            delayedSpawner.update(true, false);
            createSmartSpawner(block, player, finalEntityType);

            // Set up hopper integration if enabled
            setupHopperIntegration(block);
        }, 2L);
    }

    private EntityType getEntityType(EntityType storedEntityType, CreatureSpawner placedSpawner) {
        EntityType entityType = storedEntityType;

        // Determine entity type
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            entityType = placedSpawner.getSpawnedType();

            // Make sure we explicitly set and update
            placedSpawner.setSpawnedType(entityType);
            placedSpawner.update(true, false);
        }

        return entityType;
    }

    private void createSmartSpawner(Block block, Player player, EntityType entityType) {
        String spawnerId = UUID.randomUUID().toString().substring(0, 8);

        // Ensure the block state is properly updated
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner) {
            CreatureSpawner spawner = (CreatureSpawner) state;
            spawner.setSpawnedType(entityType);
            spawner.update(true, false);
        }

        // Create and configure new spawner
        SpawnerData spawner = new SpawnerData(spawnerId, block.getLocation(), entityType, plugin);
        spawner.setSpawnerActive(true);

        // Register with manager
        spawnerManager.addSpawner(spawnerId, spawner);

        // Save spawner data
        spawnerManager.queueSpawnerForSaving(spawnerId);

        // Visual effect if enabled
        if (plugin.getConfig().getBoolean("particle.spawner_generate_loot", true)) {
            showCreationParticles(block);
        }

        messageService.sendMessage(player, "spawner_activated");
    }

    private void showCreationParticles(Block block) {
        // Use location-based scheduling to ensure particles are shown in the right region
        Scheduler.runLocationTask(block.getLocation(), () -> {
            Location particleLocation = block.getLocation().clone().add(
                    PARTICLE_OFFSET, PARTICLE_OFFSET, PARTICLE_OFFSET);
            block.getWorld().spawnParticle(
                    ParticleWrapper.SPELL_WITCH,
                    particleLocation,
                    50, PARTICLE_OFFSET, PARTICLE_OFFSET, PARTICLE_OFFSET, 0
            );
        });
    }

    private void setupHopperIntegration(Block block) {
        Scheduler.runLocationTask(block.getLocation(), () -> {
            plugin.debug("Checking for hopper integration...");
            Block blockBelow = block.getRelative(BlockFace.DOWN);
            if (blockBelow.getType() == Material.HOPPER && hopperHandler != null) {
                hopperHandler.startHopperTask(blockBelow.getLocation(), block.getLocation());
                plugin.debug("Hopper integration started for block: " + block.getLocation());
            }
            plugin.debug("Block below type: " + blockBelow.getType());
            plugin.debug("hopperHandler: " + (hopperHandler != null ? "exists" : "null"));
        });
    }
}