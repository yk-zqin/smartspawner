package me.nighter.smartSpawner.spawner.gui.stacker;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.holders.SpawnerStackerHolder;
import me.nighter.smartSpawner.spawner.gui.main.SpawnerMenuUI;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import org.bukkit.Bukkit;
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
    private final SpawnerStackerActionUpdater spawnerStackerActionUpdater;
    private final Map<String, Integer> changeAmountMap;

    public SpawnerStackerAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerStackerActionUpdater = new SpawnerStackerActionUpdater(plugin);
        this.changeAmountMap = initializeChangeAmountMap();
    }

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

    @EventHandler
    public void onStackControlClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder holder)) return;

        event.setCancelled(true);
        SpawnerData spawner = holder.getSpawnerData();

        // Check for click cooldown to prevent spam clicking
        if (spawnerStackerActionUpdater.isPlayerOnCooldown(player)) {
            // Play error sound if clicking too fast
            //player.playSound(player.getLocation(), ERROR_SOUND, SOUND_VOLUME, SOUND_PITCH);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // Return to main menu if spawner is clicked
        if (clicked.getType() == Material.SPAWNER) {
            navigateToMainMenu(player, spawner);
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) return;

        // Determine the change amount based on the clicked item's name
        int change = getChangeAmount(meta.getDisplayName());
        if (change == 0) return;

        // Mark player as actively interacting with the GUI
        spawnerStackerActionUpdater.markPlayerActive(player);

        // Handle stack change based on direction (increase or decrease)
        if (change > 0) {
            handleStackIncrease(player, spawner, change);
        } else {
            handleStackDecrease(player, spawner, change);
        }

        // Only update for the player who clicked and other active interactors
        spawnerStackerActionUpdater.scheduleUpdateForAll(spawner, player);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof SpawnerStackerHolder holder)) return;

        SpawnerData spawner = holder.getSpawnerData();
        spawnerStackerActionUpdater.trackViewer(spawner.getSpawnerId(), player);
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
                spawnerStackerActionUpdater.untrackViewer(spawner.getSpawnerId(), player);
            }
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        spawnerStackerActionUpdater.cleanup(event.getPlayer().getUniqueId());
    }

    public void cleanup() {
        spawnerStackerActionUpdater.cleanupAll();
    }

    public SpawnerStackerActionUpdater getSpawnerStackerUpdater() {
        return spawnerStackerActionUpdater;
    }

    private void navigateToMainMenu(Player player, SpawnerData spawner) {
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
        player.playSound(player.getLocation(), MENU_NAVIGATION_SOUND, SOUND_VOLUME, SOUND_PITCH);
    }

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
            while (remainingAmount > 0) {
                int stackSize = Math.min(MAX_STACK_SIZE, remainingAmount);
                ItemStack spawnerItem = createSpawnerItem(entityType);
                spawnerItem.setAmount(stackSize);

                // Try to add to inventory first
                Map<Integer, ItemStack> failedItems = player.getInventory().addItem(spawnerItem);

                // Drop any items that couldn't fit
                if (!failedItems.isEmpty()) {
                    failedItems.values().forEach(item ->
                            player.getWorld().dropItemNaturally(player.getLocation(), item)
                    );
                    languageManager.sendMessage(player, "messages.inventory-full-drop");
                }

                remainingAmount -= stackSize;
            }
        }

        // Update inventory
        player.updateInventory();
    }

    private int getChangeAmount(String displayName) {
        return changeAmountMap.entrySet().stream()
                .filter(entry -> displayName.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(0);
    }
}