package github.nighter.smartspawner.spawner.interactions.place;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.api.events.SpawnerPlaceEvent;
import github.nighter.smartspawner.extras.HopperHandler;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.nms.ParticleWrapper;
import github.nighter.smartspawner.spawner.limits.ChunkSpawnerLimiter;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.utils.SpawnerTypeChecker;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnerPlaceListener implements Listener {
    private static final double PARTICLE_OFFSET = 0.5;

    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final HopperHandler hopperHandler;
    private ChunkSpawnerLimiter chunkSpawnerLimiter;

    public SpawnerPlaceListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.hopperHandler = plugin.getHopperHandler();
        this.chunkSpawnerLimiter = plugin.getChunkSpawnerLimiter();
    }

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

        // Determine stack size based on shift-click and item amount
        int stackSize = calculateStackSize(player, item, isVanillaSpawner);

        // For smart spawners, check chunk limits with the calculated stack size
        if (!isVanillaSpawner) {
            if (!chunkSpawnerLimiter.canPlaceSpawner(player, block.getLocation()) ||
                    !chunkSpawnerLimiter.canStackSpawner(player, block.getLocation(), stackSize - 1)) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("limit", String.valueOf(chunkSpawnerLimiter.getMaxSpawnersPerChunk()));
                messageService.sendMessage(player, "spawner_chunk_limit_reached", placeholders);
                event.setCancelled(true);
                return;
            }
        }

        // Extract entity type from spawner item
        EntityType storedEntityType = null;
        if (blockMeta.hasBlockState() && blockMeta.getBlockState() instanceof CreatureSpawner) {
            storedEntityType = ((CreatureSpawner) blockMeta.getBlockState()).getSpawnedType();
        }

        // Call custom event with the actual stack size
        if(SpawnerPlaceEvent.getHandlerList().getRegisteredListeners().length != 0) {
            SpawnerPlaceEvent e = new SpawnerPlaceEvent(player, block.getLocation(), stackSize);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) {
                event.setCancelled(true);
                return;
            }
        }

        handleSpawnerSetup(block, player, storedEntityType, isVanillaSpawner, item, stackSize);
    }

    private int calculateStackSize(Player player, ItemStack item, boolean isVanillaSpawner) {
        // Vanilla spawners always have stack size of 1
        if (isVanillaSpawner) {
            return 1;
        }

        // For smart spawners, check if player is sneaking
        if (player.isSneaking()) {
            return item.getAmount(); // Use the full stack amount
        } else {
            return 1; // Single spawner
        }
    }

    private void handleSpawnerSetup(Block block, Player player, EntityType entityType,
                                    boolean isVanillaSpawner, ItemStack item, int stackSize) {
        // Validate entity type
        if (entityType == null || entityType == EntityType.UNKNOWN) {
            return;
        }

        // Get the spawner state once
        CreatureSpawner spawner = (CreatureSpawner) block.getState();

        if (isVanillaSpawner) {
            // For vanilla spawners, just set the entity type and update
            spawner.setSpawnedType(entityType);
            spawner.update(true, false);
            return;
        }

        // For smart spawners, we need to delay the setup to ensure the block is fully placed
        Scheduler.runLocationTaskLater(block.getLocation(), () -> {
            // Recheck the block state in case it changed during the delay
            if (block.getType() != Material.SPAWNER) {
                return;
            }

            CreatureSpawner delayedSpawner = (CreatureSpawner) block.getState();
            EntityType finalEntityType = getEntityType(entityType, delayedSpawner);

            delayedSpawner.setSpawnedType(finalEntityType);
            delayedSpawner.update(true, false);
            createSmartSpawner(block, player, finalEntityType, stackSize);

            // Update player inventory based on the stack size used
            updatePlayerInventory(player, item, stackSize);

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

    private void createSmartSpawner(Block block, Player player, EntityType entityType, int stackSize) {
        String spawnerId = UUID.randomUUID().toString().substring(0, 8);

        // Ensure the block state is properly updated
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner) {
            CreatureSpawner spawner = (CreatureSpawner) state;
            spawner.setSpawnedType(entityType);
            spawner.update(true, false);
        }

        // Create and configure new spawner with the specified stack size
        SpawnerData spawner = new SpawnerData(spawnerId, block.getLocation(), entityType, plugin);
        spawner.setSpawnerActive(true);
        spawner.setStackSize(stackSize); // Set the stack size based on placement

        // Register with manager
        spawnerManager.addSpawner(spawnerId, spawner);
        chunkSpawnerLimiter.registerSpawnerPlacement(block.getLocation(), spawner.getStackSize());

        // Save spawner data
        spawnerManager.queueSpawnerForSaving(spawnerId);

        // Visual effect if enabled
        if (plugin.getConfig().getBoolean("particle.spawner_generate_loot", true)) {
            showCreationParticles(block);
        }

        // Send appropriate message based on stack size
        if (stackSize > 1) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.valueOf(stackSize));
            messageService.sendMessage(player, "spawner_stack_placed", placeholders);
        } else {
            messageService.sendMessage(player, "spawner_activated");
        }
    }

    private void updatePlayerInventory(Player player, ItemStack item, int stackSize) {
        // Don't consume items in creative mode
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        int remainingAmount = item.getAmount() - stackSize;

        if (remainingAmount <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(remainingAmount);
        }
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
            Block blockBelow = block.getRelative(BlockFace.DOWN);
            if (blockBelow.getType() == Material.HOPPER && hopperHandler != null) {
                hopperHandler.startHopperTask(blockBelow.getLocation(), block.getLocation());
            }
        });
    }
}