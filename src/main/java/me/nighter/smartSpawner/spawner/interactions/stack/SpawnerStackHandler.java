package me.nighter.smartSpawner.spawner.interactions.stack;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.protections.CheckStackBlock;
import me.nighter.smartSpawner.nms.ParticleWrapper;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the stacking functionality for spawners in the SmartSpawner plugin.
 * Manages the process of adding spawners to existing spawner stacks.
 */
public class SpawnerStackHandler {
    private static final Pattern ECONOMY_SHOP_GUI_PATTERN = Pattern.compile("§9§l([A-Za-z]+(?: [A-Za-z]+)?) §rSpawner");
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 1.0f;

    private final ConfigManager configManager;
    private final LanguageManager languageManager;

    /**
     * Constructs a new SpawnerStackHandler with the given plugin instance.
     *
     * @param plugin The SmartSpawner plugin instance
     */
    public SpawnerStackHandler(SmartSpawner plugin) {
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
    }

    /**
     * Handles the stacking of a spawner when a player interacts with it.
     *
     * @param player The player stacking the spawner
     * @param block The spawner block being interacted with
     * @param spawnerData The spawner data of the target spawner
     * @param itemInHand The item in the player's hand
     */
    public void handleSpawnerStacking(Player player, Block block, SpawnerData spawnerData, ItemStack itemInHand) {
        boolean success = handleSpawnerStack(player, spawnerData, itemInHand, player.isSneaking());

        if (success && player.isSneaking()) {
            playStackSuccessEffects(player, block);
        }
    }

    /**
     * Processes a spawner stacking attempt.
     *
     * @param player The player attempting to stack
     * @param targetSpawner The target spawner data
     * @param itemInHand The spawner item in hand
     * @param stackAll Whether to stack all available spawners at once
     * @return true if stacking was successful, false otherwise
     */
    public boolean handleSpawnerStack(Player player, SpawnerData targetSpawner, ItemStack itemInHand, boolean stackAll) {
        // Preliminary checks
        if (itemInHand.getType() != Material.SPAWNER) {
            return false;
        }

        Location location = targetSpawner.getSpawnerLocation();

        if (!hasStackPermissions(player, location)) {
            return false;
        }

        // Validate spawner item
        Optional<EntityType> handEntityTypeOpt = getEntityTypeFromItem(itemInHand);
        if (!handEntityTypeOpt.isPresent()) {
            languageManager.sendMessage(player, "messages.invalid-spawner");
            return false;
        }

        EntityType handEntityType = handEntityTypeOpt.get();
        EntityType targetEntityType = targetSpawner.getEntityType();

        if (handEntityType != targetEntityType) {
            languageManager.sendMessage(player, "messages.different-type");
            return false;
        }

        // Check stack limits
        int maxStackSize = configManager.getMaxStackSize();
        int currentStack = targetSpawner.getStackSize();

        if (currentStack >= maxStackSize) {
            languageManager.sendMessage(player, "messages.stack-full");
            return false;
        }

        // Process stacking
        return processStackAddition(player, targetSpawner, itemInHand, stackAll, currentStack, maxStackSize);
    }

    /**
     * Checks if player has the necessary permissions to stack spawners at the given location.
     *
     * @param player The player to check
     * @param location The location of the spawner
     * @return true if player has permissions, false otherwise
     */
    private boolean hasStackPermissions(Player player, Location location) {
        if (!CheckStackBlock.CanPlayerPlaceBlock(player.getUniqueId(), location)) {
            languageManager.sendMessage(player, "messages.spawner-protected");
            return false;
        }

        if (!player.hasPermission("smartspawner.stack")) {
            languageManager.sendMessage(player, "no-permission");
            return false;
        }

        return true;
    }

    /**
     * Attempts to extract EntityType from a spawner item.
     * Supports standard spawners, EconomyShopGUI format, and custom naming.
     *
     * @param item The spawner item
     * @return Optional containing the EntityType if found, empty otherwise
     */
    private Optional<EntityType> getEntityTypeFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return Optional.empty();
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        CreatureSpawner handSpawner = (CreatureSpawner) blockMeta.getBlockState();
        EntityType entityType = handSpawner.getSpawnedType();

        if (entityType != null) {
            return Optional.of(entityType);
        }

        // Check for custom formats
        String displayName = meta.getDisplayName();

        // Try EconomyShopGUI format
        Matcher matcher = ECONOMY_SHOP_GUI_PATTERN.matcher(displayName);
        if (matcher.matches()) {
            String entityName = matcher.group(1).replace(" ", "_").toUpperCase();
            try {
                return Optional.of(EntityType.valueOf(entityName));
            } catch (IllegalArgumentException e) {
                // Invalid entity name, continue to next check
            }
        }

        // Try language manager format
        return tryLanguageManagerFormat(displayName);
    }

    /**
     * Attempts to match a spawner display name against known entity types using language manager.
     *
     * @param displayName The display name to check
     * @return Optional containing the EntityType if found, empty otherwise
     */
    private Optional<EntityType> tryLanguageManagerFormat(String displayName) {
        for (EntityType entityType : EntityType.values()) {
            String entityTypeName = entityType.name();
            String expectedName = languageManager.getMessage("spawner-name", "%entity%", entityTypeName);
            if (displayName.equals(expectedName)) {
                return Optional.of(entityType);
            }
        }
        return Optional.empty();
    }

    /**
     * Processes the actual addition of spawners to the stack.
     *
     * @param player The player stacking the spawners
     * @param targetSpawner The target spawner data
     * @param itemInHand The spawner item in hand
     * @param stackAll Whether to stack all available spawners at once
     * @param currentStack Current stack size
     * @param maxStackSize Maximum allowed stack size
     * @return true if stacking was successful, false otherwise
     */
    private boolean processStackAddition(Player player, SpawnerData targetSpawner, ItemStack itemInHand,
                                         boolean stackAll, int currentStack, int maxStackSize) {
        int itemAmount = itemInHand.getAmount();
        int spaceLeft = maxStackSize - currentStack;

        int amountToStack = stackAll ? Math.min(spaceLeft, itemAmount) : 1;
        int newStack = currentStack + amountToStack;

        targetSpawner.setStackSize(newStack);
        updatePlayerInventory(player, itemInHand, amountToStack);
        showStackAnimation(targetSpawner, newStack, player);

        return true;
    }

    /**
     * Updates the player's inventory after stacking.
     *
     * @param player The player whose inventory to update
     * @param itemInHand The item being used for stacking
     * @param amountUsed The number of items used in stacking
     */
    private void updatePlayerInventory(Player player, ItemStack itemInHand, int amountUsed) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            int remainingAmount = itemInHand.getAmount() - amountUsed;

            if (remainingAmount <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                itemInHand.setAmount(remainingAmount);
            }
        }
    }

    /**
     * Plays visual and audio effects for successful stacking.
     *
     * @param player The player who stacked the spawner
     * @param block The spawner block
     */
    private void playStackSuccessEffects(Player player, Block block) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SOUND_VOLUME, SOUND_PITCH);

        if (configManager.isSpawnerStackParticlesEnabled()) {
            Location particleLocation = block.getLocation().add(0.5, 0.5, 0.5);
            block.getWorld().spawnParticle(
                    ParticleWrapper.VILLAGER_HAPPY,
                    particleLocation,
                    10, 0.3, 0.3, 0.3, 0
            );
        }
    }

    /**
     * Displays the stacking animation and notification to the player.
     *
     * @param spawner The spawner that was stacked
     * @param newStack The new stack size
     * @param player The player to show the animation to
     */
    private void showStackAnimation(SpawnerData spawner, int newStack, Player player) {
        if (configManager.isSpawnerStackParticlesEnabled()) {
            Location loc = spawner.getSpawnerLocation();
            World world = loc.getWorld();

            if (world != null) {
                world.spawnParticle(
                        ParticleWrapper.VILLAGER_HAPPY,
                        loc.clone().add(0.5, 0.5, 0.5),
                        10, 0.3, 0.3, 0.3, 0
                );
            }
        }

        languageManager.sendMessage(player, "messages.hand-stack", "%amount%", String.valueOf(newStack));
    }
}