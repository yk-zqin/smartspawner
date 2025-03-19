package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.SpawnerMenuHolder;
import github.nighter.smartspawner.nms.ParticleWrapper;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerUI;
import github.nighter.smartspawner.spawner.gui.storage.SpawnerStorageUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.config.ConfigManager;
import github.nighter.smartspawner.language.LanguageManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all click interactions within the spawner menu interface.
 * Processes various actions based on clicked items and manages related UIs.
 */
public class SpawnerMenuAction implements Listener {
    private static final Set<Material> SPAWNER_INFO_MATERIALS = EnumSet.of(
            Material.PLAYER_HEAD,
            Material.SPAWNER,
            Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL,
            Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD,
            Material.PIGLIN_HEAD
    );
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerStackerUI spawnerStackerUI;
    private final SpawnerStorageUI spawnerStorageUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;

    // Add cooldown system properties
    private final Map<UUID, Long> sellCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastInfoClickTime = new ConcurrentHashMap<>();

    /**
     * Constructs a new SpawnerMenuAction with necessary dependencies.
     *
     * @param plugin The main plugin instance
     */
    public SpawnerMenuAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerStackerUI = plugin.getSpawnerStackerUI();
        this.spawnerStorageUI = plugin.getSpawnerStorageUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
    }

    /**
     * Handles click events within the spawner menu.
     * Processes different actions based on the item clicked.
     *
     * @param event The inventory click event
     */
    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof SpawnerMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        SpawnerMenuHolder holder = (SpawnerMenuHolder) event.getInventory().getHolder();
        SpawnerData spawner = holder.getSpawnerData();

        // Verify click was in the actual menu and not player inventory
        if (event.getClickedInventory() == null ||
                !(event.getClickedInventory().getHolder() instanceof SpawnerMenuHolder)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        Material itemType = clickedItem.getType();

        if (itemType == Material.CHEST) {
            handleChestClick(player, spawner);
        } else if (SPAWNER_INFO_MATERIALS.contains(itemType)) {
            handleSpawnerInfoClick(player, spawner, event.getClick());
        } else if (itemType == Material.EXPERIENCE_BOTTLE) {
            handleExpBottleClick(player, spawner, false);
        }
    }

    /**
     * Handles chest icon clicks to open the spawner's storage interface.
     *
     * @param player The player clicking the chest
     * @param spawner The spawner data for this menu
     */
    public void handleChestClick(Player player, SpawnerData spawner) {
        String title = languageManager.getGuiTitle("gui-title.loot-menu");
        Inventory pageInventory = spawnerStorageUI.createInventory(spawner, title, 1, -1);

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
        player.closeInventory();
        player.openInventory(pageInventory);
    }

    /**
     * Handles spawner info icon clicks with different actions based on click type:
     * Left click: Collect EXP and sell items in storage
     * Right click: Open the stacker GUI
     *
     * @param player The player clicking the info icon
     * @param spawner The spawner data for this menu
     * @param clickType The type of click performed
     */
    private void handleSpawnerInfoClick(Player player, SpawnerData spawner, ClickType clickType) {
        if (!player.hasPermission("smartspawner.stack")) {
            languageManager.sendMessage(player, "messages.no-permission");
            return;
        }
        if (isClickTooFrequent(player)) {
            return;
        }
        if (clickType == ClickType.LEFT) {
            // Left click: Collect EXP and sell items in storage
            boolean hasExp = handleExpBottleClick(player, spawner, true);

            // Attempt to sell items if shop integration is available
            boolean soldItems = false;
            if (plugin.hasShopIntegration() && player.hasPermission("smartspawner.sellall")) {
                soldItems = handleSellAllItems(player, spawner);
                if (soldItems && spawner.isAtCapacity()) {
                    spawner.setAtCapacity(false);
                }
            }

            // Play a sound if any action was performed
            if (hasExp || soldItems) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            } else if (!hasExp) {
                //
            }
        } else if (clickType == ClickType.RIGHT) {
            // Right click: Open stacker GUI
            spawnerStackerUI.openStackerGui(player, spawner);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            // Other click types are ignored
        }

    }

    /**
     * Checks if a player is clicking too frequently to prevent spam.
     *
     * @param player The player to check
     * @return True if player is clicking too frequently, false otherwise
     */
    private boolean isClickTooFrequent(Player player) {
        long now = System.currentTimeMillis();
        long last = lastInfoClickTime.getOrDefault(player.getUniqueId(), 0L);
        lastInfoClickTime.put(player.getUniqueId(), now);
        return (now - last) < 300; // 300ms threshold
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastInfoClickTime.remove(event.getPlayer().getUniqueId());
        sellCooldowns.remove(event.getPlayer().getUniqueId());
    }

    /**
     * Handles selling all items in the spawner storage.
     * Includes anti-spam cooldown protection.
     *
     * @param player The player selling items
     * @param spawner The spawner data containing items to sell
     * @return True if items were successfully sold, false otherwise
     */
    private boolean handleSellAllItems(Player player, SpawnerData spawner) {
        if (!plugin.hasShopIntegration()) return false;

        // Permission check
        if (!player.hasPermission("smartspawner.sellall")) {
            languageManager.sendMessage(player, "messages.no-permission");
            return false;
        }

        // Anti-spam cooldown check
        if (isOnCooldown(player)) {
            int cooldownSeconds = configManager.getInt("sell-cooldown");
            languageManager.sendMessage(player, "messages.sell-cooldown",
                    "%seconds%", String.valueOf(cooldownSeconds));
            return false;
        }

        // Update cooldown timestamp before processing
        updateCooldown(player);

        // Clean up old cooldowns periodically (e.g., every 10th call)
        if (Math.random() < 0.1) {
            clearOldCooldowns();
        }

        // Process the sale through shop integration
        return plugin.getShopIntegration().sellAllItems(player, spawner);
    }

    /**
     * Gets the cooldown time in milliseconds from config.
     *
     * @return Cooldown time in milliseconds
     */
    private long getSellCooldownMs() {
        return configManager.getInt("sell-cooldown") * 1000L;
    }

    /**
     * Checks if a player is currently on cooldown for selling items.
     *
     * @param player The player to check
     * @return True if player is on cooldown, false otherwise
     */
    private boolean isOnCooldown(Player player) {
        long cooldownMs = getSellCooldownMs();
        // If cooldown is disabled (set to 0), always return false
        if (cooldownMs <= 0) {
            return false;
        }

        long lastSellTime = sellCooldowns.getOrDefault(player.getUniqueId(), 0L);
        long currentTime = System.currentTimeMillis();
        boolean onCooldown = (currentTime - lastSellTime) < cooldownMs;

        // Debug information if needed
        if (onCooldown) {
            configManager.debug("Player " + player.getName() + " tried to sell items while on cooldown");
        }

        return onCooldown;
    }

    /**
     * Updates the cooldown timestamp for a player.
     *
     * @param player The player to update cooldown for
     */
    private void updateCooldown(Player player) {
        sellCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Clears old cooldown entries to prevent memory leaks.
     */
    private void clearOldCooldowns() {
        long cooldownMs = getSellCooldownMs();
        if (cooldownMs <= 0) {
            // If cooldown is disabled, clear all entries
            sellCooldowns.clear();
            return;
        }

        long currentTime = System.currentTimeMillis();
        sellCooldowns.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > cooldownMs * 10);
    }

    /**
     * Checks if a player is on cooldown for the sell action.
     * This method is made public so it can be accessed from other classes
     * like SpawnerStorageAction.
     *
     * @param player The player to check
     * @return True if player is on cooldown, false otherwise
     */
    public boolean isSellCooldownActive(Player player) {
        return isOnCooldown(player);
    }

    /**
     * Updates the sell cooldown for a player.
     * This method is made public so it can be called from other classes
     * like SpawnerStorageAction.
     *
     * @param player The player to update cooldown for
     */
    public void updateSellCooldown(Player player) {
        updateCooldown(player);
    }

    /**
     * Handles experience bottle clicks to collect and distribute spawner EXP.
     * Applies EXP to mending items first if enabled, then gives remaining EXP to player.
     *
     * @param player The player clicking the EXP bottle
     * @param spawner The spawner data containing accumulated EXP
     * @return True if EXP was collected, false if no EXP was available
     */
    public boolean handleExpBottleClick(Player player, SpawnerData spawner, boolean isSell) {
        if (isClickTooFrequent(player) && !isSell) {
            return false;
        }
        int exp = spawner.getSpawnerExp();

        if (exp <= 0 && !isSell) {
            languageManager.sendMessage(player, "messages.no-exp");
            return false;
        }

        int initialExp = exp;
        int expUsedForMending = 0;

        // Apply mending first if enabled
        if (configManager.getBoolean("allow-exp-mending")) {
            expUsedForMending = applyMendingFromExp(player, exp);
            exp -= expUsedForMending;
        }

        // Give remaining exp to player
        if (exp > 0) {
            player.giveExp(exp);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        // Reset spawner exp and update menu
        spawner.setSpawnerExp(0);
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);

        // Update all viewers instead of just current player
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        // Update spawner capacity status
        if (spawner.getSpawnerExp() < spawner.getMaxStoredExp()) {
            if (spawner.isAtCapacity()){
                spawner.setAtCapacity(false);
            }
        }

        // Send appropriate message based on exp distribution
        sendExpCollectionMessage(player, initialExp, expUsedForMending);

        return true;
    }

    /**
     * Applies experience points to repair items with Mending enchantment.
     * Prioritizes main hand, off hand, then armor.
     *
     * @param player The player collecting the EXP
     * @param availableExp The amount of EXP available for mending
     * @return The amount of EXP actually used for mending
     */
    private int applyMendingFromExp(Player player, int availableExp) {
        if (availableExp <= 0) {
            return 0;
        }

        int expUsed = 0;
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> itemsToCheck = Arrays.asList(
                inventory.getItemInMainHand(),
                inventory.getItemInOffHand(),
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        );

        for (ItemStack item : itemsToCheck) {
            if (availableExp <= 0) {
                break;
            }

            if (item == null || item.getType() == Material.AIR ||
                    !item.getEnchantments().containsKey(Enchantment.MENDING)) {
                continue;
            }

            if (!(item.getItemMeta() instanceof Damageable damageable) || damageable.getDamage() <= 0) {
                continue;
            }

            // Calculate repair amount based on available exp
            int damage = damageable.getDamage();
            int durabilityToRepair = Math.min(damage, availableExp * 2);
            int expNeeded = (durabilityToRepair + 1) / 2; // Round up for partial repairs

            if (expNeeded <= 0) {
                continue;
            }

            // Apply repair and track exp usage
            int actualExpUsed = Math.min(expNeeded, availableExp);
            int actualRepair = actualExpUsed * 2;

            // Ensure damage value does not go negative
            int newDamage = Math.max(0, damage - actualRepair);

            Damageable meta = (Damageable) item.getItemMeta();
            meta.setDamage(newDamage);
            item.setItemMeta(meta);

            availableExp -= actualExpUsed;
            expUsed += actualExpUsed;

            // Visual and sound effects for mending
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.0f);
            player.spawnParticle(ParticleWrapper.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 5);
        }

        return expUsed;
    }

    /**
     * Sends a message to the player about collected experience,
     * differentiating between mending and direct EXP based on distribution.
     *
     * @param player The player who collected the EXP
     * @param totalExp The total amount of EXP that was collected
     * @param mendingExp The amount of EXP used for mending items
     */
    private void sendExpCollectionMessage(Player player, int totalExp, int mendingExp) {
        if (mendingExp > 0) {
            int remainingExp = totalExp - mendingExp;
            languageManager.sendMessage(player, "messages.exp-collected-with-mending",
                    "%exp-mending%", languageManager.formatNumberTenThousand(mendingExp),
                    "%exp%", languageManager.formatNumberTenThousand(remainingExp));
        } else {
            if (totalExp > 0) {
                languageManager.sendMessage(player, "messages.exp-collected",
                        "%exp%", languageManager.formatNumberTenThousand(totalExp));
            }
        }
    }
}