package me.nighter.smartSpawner.spawner.gui.stacker;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.holders.SpawnerStackerHolder;
import me.nighter.smartSpawner.spawner.gui.main.SpawnerMenuUI;
import me.nighter.smartSpawner.spawner.gui.synchronization.SpawnerStackerUpdater;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles all inventory click events and logic related to stacking and unstacking spawners.
 * This class manages the interaction between players and the spawner stacker GUI.
 */
public class SpawnerStackerAction implements Listener {
    private static final Pattern SPAWNER_NAME_PATTERN = Pattern.compile("§9§l([A-Za-z]+(?: [A-Za-z]+)?) §rSpawner");
    private static final Sound STACK_OPERATION_SOUND = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private static final Sound MENU_NAVIGATION_SOUND = Sound.UI_BUTTON_CLICK;
    private static final float SOUND_VOLUME = 1.0f;
    private static final float SOUND_PITCH = 1.0f;

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerStackerUpdater spawnerStackerUpdater;
    private final Map<String, Integer> changeAmountMap;

    /**
     * Constructs a new SpawnerStackerAction instance.
     *
     * @param plugin The main plugin instance
     */
    public SpawnerStackerAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerStackerUpdater = new SpawnerStackerUpdater(plugin);
        this.changeAmountMap = initializeChangeAmountMap();
    }

    /**
     * Initializes the map of display names to their corresponding stack change amounts.
     *
     * @return A map of button display names to their stack change values
     */
    private Map<String, Integer> initializeChangeAmountMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("-64", -64);
        map.put("-10", -10);
        map.put("-1 ", -1);
        map.put("+1 ", 1);
        map.put("+10", 10);
        map.put("+64", 64);
        return map;
    }

    /**
     * Handles the inventory click events within the spawner stacker GUI.
     *
     * @param event The inventory click event
     */
    @EventHandler
    public void onStackControlClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder holder)) return;

        event.setCancelled(true);
        SpawnerData spawner = holder.getSpawnerData();

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // Return to main menu if spawner is clicked
        if (clicked.getType() == Material.SPAWNER) {
            navigateToMainMenu(player, spawner);
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.displayName() == null) return;

        // Determine the change amount based on the clicked item's name
        int change = getChangeAmount(meta.getDisplayName());
        if (change == 0) return;

        // Handle stack change based on direction (increase or decrease)
        if (change > 0) {
            handleStackIncrease(player, spawner, change);
        } else {
            handleStackDecrease(player, spawner, change);
        }

        spawnerStackerUpdater.scheduleUpdateForAll(spawner, player);

    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder holder)) return;

        SpawnerData spawner = holder.getSpawnerData();
        spawnerStackerUpdater.trackViewer(spawner.getSpawnerId(), player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder holder)) return;

        SpawnerData spawner = holder.getSpawnerData();

        // Schedule a check to see if the player really closed the inventory
        // (handles inventory updates that temporarily "close" the inventory)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Inventory topInventory = player.getOpenInventory().getTopInventory();
            if (!(topInventory.getHolder() instanceof SpawnerStackerHolder)) {
                spawnerStackerUpdater.untrackViewer(spawner.getSpawnerId(), player);
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        spawnerStackerUpdater.cleanup(event.getPlayer().getUniqueId());
    }

    public void cleanup() {
        spawnerStackerUpdater.cleanupAll();
    }

    public SpawnerStackerUpdater getSpawnerStackerUpdater() {
        return spawnerStackerUpdater;
    }

    /**
     * Navigates the player back to the main spawner menu.
     *
     * @param player The player to show the menu to
     * @param spawner The spawner data to display
     */
    private void navigateToMainMenu(Player player, SpawnerData spawner) {
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
        player.playSound(player.getLocation(), MENU_NAVIGATION_SOUND, SOUND_VOLUME, SOUND_PITCH);
    }

    /**
     * Handles decreasing the stack size of a spawner and gives the removed spawners to the player.
     *
     * @param player The player performing the operation
     * @param spawner The spawner data being modified
     * @param change The negative amount to change the stack by
     */
    private void handleStackDecrease(Player player, SpawnerData spawner, int change) {
        int currentSize = spawner.getStackSize();
        int removeAmount = Math.abs(change);
        int targetSize = currentSize + change; // change is negative

        // Cannot decrease below 1
        if (targetSize < 1) {
            languageManager.sendMessage(player, "messages.cannot-go-below-one",
                    "%amount%", String.valueOf(currentSize - 1));
            return;
        }

        // Update spawner stack size
        spawner.setStackSize(targetSize, player);

        // Give spawners to player
        giveSpawnersToPlayer(player, removeAmount, spawner.getEntityType());

        // Play sound effect
        player.playSound(player.getLocation(), STACK_OPERATION_SOUND, SOUND_VOLUME, SOUND_PITCH);
    }

    /**
     * Handles increasing the stack size of a spawner by taking spawners from the player.
     *
     * @param player The player performing the operation
     * @param spawner The spawner data being modified
     * @param change The positive amount to change the stack by
     */
    private void handleStackIncrease(Player player, SpawnerData spawner, int change) {
        int currentSize = spawner.getStackSize();
        int maxStackSize = configManager.getMaxStackSize();

        // Calculate how many more spawners can be added
        int spaceLeft = maxStackSize - currentSize;
        if (spaceLeft <= 0) {
            languageManager.sendMessage(player, "messages.stack-full");
            return;
        }

        // Calculate actual change based on space left
        int actualChange = Math.min(change, spaceLeft);

        // Check for valid spawners in inventory
        int validSpawners = countValidSpawnersInInventory(player, spawner.getEntityType());

        // Handle case when player has different spawner types
        if (validSpawners == 0 && hasDifferentSpawnerType(player, spawner.getEntityType())) {
            languageManager.sendMessage(player, "messages.different-type");
            return;
        }

        // Handle case when player doesn't have enough spawners
        if (validSpawners < actualChange) {
            languageManager.sendMessage(player, "messages.not-enough-spawners",
                    "%amountChange%", String.valueOf(actualChange),
                    "%amountAvailable%", String.valueOf(validSpawners));
            return;
        }

        // Remove spawners from inventory and update stack size
        removeValidSpawnersFromInventory(player, spawner.getEntityType(), actualChange);
        spawner.setStackSize(currentSize + actualChange, player);

        // Notify if not all requested spawners could be added
        if (actualChange < change) {
            languageManager.sendMessage(player, "messages.stack-full-overflow",
                    "%amount%", String.valueOf(actualChange));
        }

        // Play sound effect
        player.playSound(player.getLocation(), STACK_OPERATION_SOUND, SOUND_VOLUME, SOUND_PITCH);
    }

    /**
     * Extracts the EntityType from a spawner item.
     *
     * @param item The item to check
     * @return The EntityType of the spawner, or null if not a valid spawner
     */
    private Optional<EntityType> getSpawnerEntityType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER) {
            return Optional.empty();
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockMeta)) {
            return Optional.empty();
        }

        CreatureSpawner spawner = (CreatureSpawner) blockMeta.getBlockState();
        EntityType spawnerEntity = spawner.getSpawnedType();

        // Support for stacking spawners with Spawner from EconomyShopGUI
        if (spawnerEntity == null) {
            String displayName = meta.getDisplayName();
            Matcher matcher = SPAWNER_NAME_PATTERN.matcher(displayName);

            if (matcher.matches()) {
                String entityName = matcher.group(1)
                        .replace(" ", "_")
                        .toUpperCase();
                try {
                    return Optional.of(EntityType.valueOf(entityName));
                } catch (IllegalArgumentException e) {
                    configManager.debug("Could not find entity type: " + entityName);
                }
            }
        }

        return Optional.ofNullable(spawnerEntity);
    }

    /**
     * Checks if the player has any spawners of a different type than the required one.
     *
     * @param player The player to check
     * @param requiredType The required EntityType
     * @return true if player has spawners of a different type, false otherwise
     */
    private boolean hasDifferentSpawnerType(Player player, EntityType requiredType) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            Optional<EntityType> entityTypeOpt = getSpawnerEntityType(item);
            if (entityTypeOpt.isPresent() && entityTypeOpt.get() != requiredType) {
                return true;
            }
        }
        return false;
    }
    /**
     * Counts how many spawners of the required type the player has in their inventory.
     *
     * @param player The player to check
     * @param requiredType The required EntityType
     * @return The count of valid spawners
     */
    private int countValidSpawnersInInventory(Player player, EntityType requiredType) {
        int count = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            Optional<EntityType> spawnerEntity = getSpawnerEntityType(item);
            if (spawnerEntity.isPresent() && spawnerEntity.get() == requiredType) {
                count += item.getAmount();
            }
        }

        return count;
    }

    /**
     * Removes a specific amount of spawners of the required type from the player's inventory.
     *
     * @param player The player to remove spawners from
     * @param requiredType The required EntityType
     * @param amountToRemove The amount to remove
     */
    private void removeValidSpawnersFromInventory(Player player, EntityType requiredType, int amountToRemove) {
        int remainingToRemove = amountToRemove;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remainingToRemove > 0; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            Optional<EntityType> spawnerType = getSpawnerEntityType(item);
            if (spawnerType.isPresent() && spawnerType.get() == requiredType) {
                int itemAmount = item.getAmount();
                if (itemAmount <= remainingToRemove) {
                    player.getInventory().setItem(i, null);
                    remainingToRemove -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remainingToRemove);
                    remainingToRemove = 0;
                }
            }
        }

        player.updateInventory();
    }

    /**
     * Creates a spawner item with the specified EntityType.
     *
     * @param entityType The EntityType for the spawner
     * @return The created spawner ItemStack
     */
    private ItemStack createSpawnerItem(EntityType entityType) {
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        ItemMeta meta = spawner.getItemMeta();

        if (meta != null && entityType != null && entityType != EntityType.UNKNOWN) {
            // Set display name
            String entityTypeName = languageManager.getFormattedMobName(entityType);
            String displayName = languageManager.getMessage("spawner-name", "%entity%", entityTypeName);
            meta.setDisplayName(displayName);

            // Store entity type in item NBT
            BlockStateMeta blockMeta = (BlockStateMeta) meta;
            CreatureSpawner cs = (CreatureSpawner) blockMeta.getBlockState();
            cs.setSpawnedType(entityType);
            blockMeta.setBlockState(cs);

            spawner.setItemMeta(meta);
        }

        return spawner;
    }

    /**
     * Gives spawners to a player, merging with existing stacks where possible.
     * If the inventory is full, excess spawners are dropped on the ground.
     *
     * @param player The player to give spawners to
     * @param amount The amount of spawners to give
     * @param entityType The EntityType of the spawners
     */
    private synchronized void giveSpawnersToPlayer(Player player, int amount, EntityType entityType) {
        final int MAX_STACK_SIZE = 64;
        ItemStack[] contents = player.getInventory().getContents();
        int remainingAmount = amount;

        // First pass: Try to merge with existing stacks
        for (int i = 0; i < contents.length && remainingAmount > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != Material.SPAWNER) continue;

            Optional<EntityType> itemEntityType = getSpawnerEntityType(item);
            if (itemEntityType.isEmpty() || itemEntityType.get() != entityType) continue;

            int currentAmount = item.getAmount();
            if (currentAmount < MAX_STACK_SIZE) {
                int canAdd = Math.min(MAX_STACK_SIZE - currentAmount, remainingAmount);
                item.setAmount(currentAmount + canAdd);
                remainingAmount -= canAdd;
            }
        }

        // Second pass: Create new stacks for remaining items
        if (remainingAmount > 0) {
            Location dropLocation = player.getLocation();
            while (remainingAmount > 0) {
                int stackSize = Math.min(MAX_STACK_SIZE, remainingAmount);
                ItemStack spawnerItem = createSpawnerItem(entityType);
                spawnerItem.setAmount(stackSize);

                // Try to add to inventory first
                Map<Integer, ItemStack> failedItems = player.getInventory().addItem(spawnerItem);

                // Drop any items that couldn't fit
                if (!failedItems.isEmpty()) {
                    failedItems.values().forEach(item ->
                            player.getWorld().dropItemNaturally(dropLocation, item)
                    );
                    languageManager.sendMessage(player, "messages.inventory-full-drop");
                }

                remainingAmount -= stackSize;
            }
        }

        // Update inventory
        player.updateInventory();
    }

    /**
     * Parses the display name of a button to determine the change amount.
     *
     * @param displayName The display name to parse
     * @return The change amount (positive or negative), or 0 if not recognized
     */
    private int getChangeAmount(String displayName) {
        return changeAmountMap.entrySet().stream()
                .filter(entry -> displayName.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(0);
    }
}