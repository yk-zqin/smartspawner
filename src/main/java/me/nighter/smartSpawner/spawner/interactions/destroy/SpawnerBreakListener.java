package me.nighter.smartSpawner.spawner.interactions.destroy;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.listeners.HopperHandler;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import me.nighter.smartSpawner.hooks.protections.CheckBreakBlock;
import me.nighter.smartSpawner.spawner.properties.SpawnerManager;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Handles spawner break interactions, including permissions checking,
 * silk touch requirements, and drop processing.
 */
public class SpawnerBreakListener implements Listener {
    private static final int PICKUP_DELAY = 10;
    private static final double DROP_SPREAD_RANGE = 0.5;
    private static final double DROP_VELOCITY_RANGE = 0.2;

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerManager spawnerManager;
    private final HopperHandler hopperHandler;
    private final Random random = new Random();

    /**
     * Creates a new spawner break handler with the given plugin instance.
     *
     * @param plugin The main plugin instance
     */
    public SpawnerBreakListener(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.hopperHandler = plugin.getHopperHandler();
    }

    /**
     * Handles the spawner break event, enforcing permissions, silk touch requirements,
     * and processing drops based on stack size.
     *
     * @param event The block break event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerBreak(BlockBreakEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final Location location = block.getLocation();

        // Quick return if not a spawner
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        // Protection plugin integration
        if (!CheckBreakBlock.CanPlayerBreakBlock(player.getUniqueId(), location)) {
            event.setCancelled(true);
            return;
        }

        // Feature toggle check
        if (!configManager.isSpawnerBreakEnabled()) {
            event.setCancelled(true);
            return;
        }

        // Permission check
        if (!player.hasPermission("smartspawner.break")) {
            event.setCancelled(true);
            languageManager.sendMessage(player, "no-permission");
            return;
        }

        // Get spawner data and handle based on type
        final SpawnerData spawner = spawnerManager.getSpawnerByLocation(location);
        if (spawner != null) {
            unlockSpawnerIfNeeded(spawner);
            handleSpawnerBreak(block, spawner, player);
        } else {
            // Fallback to vanilla spawner handling
            CreatureSpawner creatureSpawner = (CreatureSpawner) block.getState();
            handleVanillaSpawnerBreak(block, creatureSpawner, player);
        }

        // Cancel vanilla event as we handle it ourselves
        event.setCancelled(true);

        // Clean up associated hopper if present
        cleanupAssociatedHopper(block);
    }

    /**
     * Unlocks a spawner if it's currently being viewed by a player
     *
     * @param spawner The spawner data to check and unlock
     */
    private void unlockSpawnerIfNeeded(SpawnerData spawner) {
        UUID lockedBy = spawner.getLockedBy();
        if (lockedBy != null) {
            Player viewingPlayer = Bukkit.getPlayer(lockedBy);
            if (viewingPlayer != null) {
                viewingPlayer.closeInventory();
            }
            spawner.unlock(lockedBy);
        }
    }

    /**
     * Handles the breaking of a tracked spawner with custom stacking behavior
     *
     * @param block The spawner block
     * @param spawner The spawner data
     * @param player The player breaking the spawner
     */
    private void handleSpawnerBreak(Block block, SpawnerData spawner, Player player) {
        Location location = block.getLocation();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!validateBreakConditions(player, tool)) {
            return;
        }

        // Process drops optimally based on stack size
        SpawnerBreakResult result = processDrops(location, spawner);

        if (result.isSuccess()) {
            // Handle tool durability
            if (player.getGameMode() != GameMode.CREATIVE) {
                reduceDurability(tool, player, result.getDurabilityLoss());
            }

            // Clean up if no stacks remain
            if (spawner.getStackSize() <= 0) {
                cleanupSpawner(block, spawner, player);
            }
        }
    }

    /**
     * Handles breaking of vanilla (non-tracked) spawners
     *
     * @param block The spawner block
     * @param creatureSpawner The creature spawner state
     * @param player The player breaking the spawner
     */
    private void handleVanillaSpawnerBreak(Block block, CreatureSpawner creatureSpawner, Player player) {
        Location location = block.getLocation();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!validateBreakConditions(player, tool)) {
            return;
        }

        // Get entity type and create appropriate item
        EntityType entityType = creatureSpawner.getSpawnedType();
        ItemStack spawnerItem = createSpawnerItem(entityType);

        // Drop item and update world
        World world = location.getWorld();
        if (world != null) {
            block.setType(Material.AIR);
            world.dropItemNaturally(location, spawnerItem);
            reduceDurability(tool, player, configManager.getDurabilityLossPerSpawner());

            logDebugInfo("Player " + player.getName() + " broke " +
                    (entityType != null ? entityType + " spawner" : "empty spawner") +
                    " at " + location);
        }
    }

    /**
     * Validates if a player can break a spawner based on permissions,
     * tool type, and enchantments.
     *
     * @param player The player attempting to break the spawner
     * @param tool The tool being used
     * @return true if the player is allowed to break the spawner
     */
    private boolean validateBreakConditions(Player player, ItemStack tool) {
        // Skip validation for creative mode players
        if (player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }

        if (!player.hasPermission("smartspawner.break")) {
            languageManager.sendMessage(player, "no-permission");
            return false;
        }

        if (!isValidTool(tool)) {
            return false;
        }

        // Silk touch validation
        if (configManager.isSilkTouchRequired()) {
            int requiredLevel = configManager.getSilkTouchLevel();
            if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) < requiredLevel) {
                languageManager.sendMessage(player, "messages.silk-touch-required");
                return false;
            }
        }

        return true;
    }

    /**
     * Processes drops for stacked spawners, always maintaining at least one spawner
     * in the stack if starting with more than one.
     *
     * @param location The spawner location
     * @param spawner The spawner data
     * @return Result object containing success status and durability impact
     */
    private SpawnerBreakResult processDrops(Location location, SpawnerData spawner) {
        final int currentStackSize = spawner.getStackSize();
        // Ensure at least 1 spawner remains (if initially > 1)
        final int maxAllowedToDrop = currentStackSize > 1 ? currentStackSize - 1 : 1;
        final int configDropAmount = configManager.getDropStackAmount();
        final int durabilityLoss = configManager.getDurabilityLossPerSpawner();

        // Calculate actual drop amount
        final int dropAmount;
        if (currentStackSize == 1) {
            // If only one exists, drop one and block will be handled separately
            dropAmount = 1;
        } else {
            // If more than one exists, ensure at least one remains
            dropAmount = Math.min(configDropAmount, maxAllowedToDrop);
        }

        if (dropAmount <= 0) {
            return new SpawnerBreakResult(false, 0, durabilityLoss);
        }

        World world = location.getWorld();
        if (world == null) {
            return new SpawnerBreakResult(false, 0, durabilityLoss);
        }

        // Create template item for efficiency
        EntityType entityType = spawner.getEntityType();
        ItemStack template = createSpawnerItem(entityType);

        // Handle drops based on stack size
        if (currentStackSize == 1) {
            // Special case: drop single item, spawner will be handled by another method
            dropItems(world, location, template, 1);
            spawner.decreaseStackSizeByOne();
        } else {
            // Ensure at least one spawner always remains
            dropItems(world, location, template, dropAmount);
            int newStackSize = Math.max(currentStackSize - dropAmount, 1);
            spawner.setStackSize(newStackSize);

            // Debug logging
            logDebugInfo("Spawner stack at " + location + " reduced from " +
                    currentStackSize + " to " + newStackSize +
                    ", dropped " + dropAmount + " items");
        }

        return new SpawnerBreakResult(true, dropAmount, durabilityLoss);
    }

    /**
     * Efficiently drops multiple items using batch processing for larger quantities
     *
     * @param world The world to drop items in
     * @param location The base location
     * @param template The template item to clone
     * @param amount The number of items to drop
     */
    private void dropItems(World world, Location location, ItemStack template, int amount) {
        if (amount <= 0) {
            return;
        }

        if (amount <= 5) {
            // For small amounts, use simple dropping
            for (int i = 0; i < amount; i++) {
                world.dropItemNaturally(location, template.clone());
            }
        } else {
            // For larger amounts, use batch processing with optimized physics
            List<Item> drops = new ArrayList<>(amount);

            for (int i = 0; i < amount; i++) {
                double spreadX = random.nextDouble() * DROP_SPREAD_RANGE - (DROP_SPREAD_RANGE / 2);
                double spreadZ = random.nextDouble() * DROP_SPREAD_RANGE - (DROP_SPREAD_RANGE / 2);

                Location dropLoc = location.clone().add(spreadX, 0.5, spreadZ);
                Item item = world.dropItem(dropLoc, template.clone());

                // Calculate randomized but controlled velocity
                item.setVelocity(new Vector(
                        random.nextDouble() * DROP_VELOCITY_RANGE - (DROP_VELOCITY_RANGE / 2),
                        0.2,
                        random.nextDouble() * DROP_VELOCITY_RANGE - (DROP_VELOCITY_RANGE / 2)
                ));

                drops.add(item);
            }

            // Set pickup delay in batch
            drops.forEach(item -> item.setPickupDelay(PICKUP_DELAY));
        }
    }

    /**
     * Reduces tool durability based on spawner breaking cost
     *
     * @param tool The tool to reduce durability for
     * @param player The player using the tool
     * @param durabilityLoss The amount of durability to remove
     */
    private void reduceDurability(ItemStack tool, Player player, int durabilityLoss) {
        if (tool.getType().getMaxDurability() == 0) {
            return;
        }

        ItemMeta meta = tool.getItemMeta();
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            int currentDurability = damageable.getDamage();
            int newDurability = currentDurability + durabilityLoss;

            if (newDurability >= tool.getType().getMaxDurability()) {
                // Tool breaks
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                damageable.setDamage(newDurability);
                tool.setItemMeta(meta);
            }
        }
    }

    /**
     * Creates a spawner item with the specified entity type
     *
     * @param entityType The entity type to set for the spawner
     * @return A configured spawner ItemStack
     */
    private ItemStack createSpawnerItem(EntityType entityType) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null) {
            if (entityType != null && entityType != EntityType.UNKNOWN) {
                // Set display name
                String entityTypeName = languageManager.getFormattedMobName(entityType);
                String displayName = languageManager.getMessage("spawner-name", "%entity%", entityTypeName);
                meta.setDisplayName(displayName);

                // Store entity type in BlockStateMeta
                if (meta instanceof BlockStateMeta) {
                    BlockStateMeta blockMeta = (BlockStateMeta) meta;
                    BlockState blockState = blockMeta.getBlockState();

                    if (blockState instanceof CreatureSpawner) {
                        CreatureSpawner cs = (CreatureSpawner) blockState;
                        cs.setSpawnedType(entityType);
                        blockMeta.setBlockState(cs);
                    }
                }
            }
            spawner.setItemMeta(meta);
        }

        return spawner;
    }

    /**
     * Cleans up the spawner and its data when fully broken
     *
     * @param block The spawner block
     * @param spawner The spawner data
     * @param player The player breaking the spawner
     */
    private void cleanupSpawner(Block block, SpawnerData spawner, Player player) {
        spawner.setSpawnerStop(true);
        block.setType(Material.AIR);
        spawnerManager.removeSpawner(spawner.getSpawnerId());

        // Save asynchronously
        CompletableFuture.runAsync(() -> {
            spawnerManager.saveSpawnerData();
            logDebugInfo("Player " + player.getName() +
                    " broke spawner with ID: " + spawner.getSpawnerId());
        });
    }

    /**
     * Cleans up associated hopper tasks when a spawner is broken
     *
     * @param block The spawner block
     */
    private void cleanupAssociatedHopper(Block block) {
        Block blockBelow = block.getRelative(BlockFace.DOWN);
        if (blockBelow.getType() == Material.HOPPER && hopperHandler != null) {
            hopperHandler.stopHopperTask(blockBelow.getLocation());
        }
    }

    /**
     * Checks if a tool is valid for breaking spawners
     *
     * @param tool The tool to check
     * @return true if the tool is valid
     */
    private boolean isValidTool(ItemStack tool) {
        if (tool == null) {
            return false;
        }
        return configManager.getRequiredTools().contains(tool.getType().name());
    }

    /**
     * Logs debug information if debug mode is enabled
     *
     * @param message The debug message
     */
    private void logDebugInfo(String message) {
        configManager.debug(message);
    }

    /**
     * Immutable container for spawner break result information
     */
    private static class SpawnerBreakResult {
        private final boolean success;
        private final int droppedAmount;
        private final int baseDurabilityLoss;

        public SpawnerBreakResult(boolean success, int droppedAmount, int baseDurabilityLoss) {
            this.success = success;
            this.droppedAmount = droppedAmount;
            this.baseDurabilityLoss = baseDurabilityLoss;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getDroppedAmount() {
            return droppedAmount;
        }

        public int getDurabilityLoss() {
            return droppedAmount * baseDurabilityLoss;
        }
    }

    /**
     * Handles the initial damage to a spawner block, providing feedback
     * about requirements and permissions
     *
     * @param event The block damage event
     */
    @EventHandler
    public void onSpawnerDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Quick return if not a spawner
        if (block.getType() != Material.SPAWNER) {
            return;
        }

        // Skip feedback in creative mode
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType() == Material.AIR) {
            return;
        }

        // Provide appropriate feedback based on tool and permissions
        if (isValidTool(tool)) {
            if (configManager.isSilkTouchRequired()) {
                int requiredLevel = configManager.getSilkTouchLevel();
                if (tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) < requiredLevel) {
                    languageManager.sendMessage(player, "messages.silk-touch-required");
                    return;
                }
            }

            if (!player.hasPermission("smartspawner.break")) {
                languageManager.sendMessage(player, "no-permission");
                return;
            }

            // Warn about breaking
            languageManager.sendMessage(player, "messages.break-warning");
        } else {
            // Inform about required tools
            languageManager.sendMessage(player, "messages.required-tools");
        }
    }
}