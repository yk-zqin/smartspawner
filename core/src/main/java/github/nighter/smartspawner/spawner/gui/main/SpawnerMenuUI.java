package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.loot.EntityLootConfig;
import github.nighter.smartspawner.spawner.loot.LootItem;
import github.nighter.smartspawner.spawner.utils.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SpawnerMenuUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int CHEST_SLOT = 11;
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int EXP_SLOT = 15;
    private static final int TICKS_PER_SECOND = 20;
    private static final Map<String, String> EMPTY_PLACEHOLDERS = Collections.emptyMap();

    private final SmartSpawner plugin;
    private final LanguageManager languageManager;

    public SpawnerMenuUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
    }

    public void openSpawnerMenu(Player player, SpawnerData spawner, boolean refresh) {
        Inventory menu = createMenu(spawner);

        // Populate menu items
        menu.setItem(CHEST_SLOT, createLootStorageItem(spawner));
        menu.setItem(SPAWNER_INFO_SLOT, createSpawnerInfoItem(player, spawner));
        menu.setItem(EXP_SLOT, createExpItem(spawner));

        // Open inventory and play sound if not refreshing
        player.openInventory(menu);

        if (!refresh) {
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        }
    }

    private Inventory createMenu(SpawnerData spawner) {
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String entityNameSmallCaps = languageManager.getSmallCaps(entityName);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
        placeholders.put("amount", String.valueOf(spawner.getStackSize()));

        String title;
        if (spawner.getStackSize() > 1) {
            title = languageManager.getGuiTitle("gui_title_main.stacked_spawner", placeholders);
        } else {
            title = languageManager.getGuiTitle("gui_title_main.single_spawner", placeholders);
        }

        return Bukkit.createInventory(new SpawnerMenuHolder(spawner), INVENTORY_SIZE, title);
    }

    public ItemStack createLootStorageItem(SpawnerData spawner) {
        ItemStack chestItem = new ItemStack(Material.CHEST);
        ItemMeta chestMeta = chestItem.getItemMeta();
        if (chestMeta == null) return chestItem;

        // Get important data upfront
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = calculatePercentage(currentItems, maxSlots);

        // Create cache key for this specific spawner's storage state
        // This helps avoid rebuilding the entire lore when nothing has changed
        String cacheKey = spawner.getSpawnerId() + "|storage|" + currentItems + "|" + spawner.getEntityType();

        // Check if we have a cached item for this exact spawner state
        ItemStack cachedItem = plugin.getItemCache().getIfPresent(cacheKey);
        if (cachedItem != null) {
            return cachedItem.clone();
        }

        // Build base placeholders - these are needed regardless of caching
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("max_slots", languageManager.formatNumber(maxSlots));
        placeholders.put("current_items", String.valueOf(currentItems));
        placeholders.put("percent_storage", String.valueOf(percentStorage));

        // Get consolidated items and prepare the loot items section
        Map<VirtualInventory.ItemSignature, Long> storedItems = virtualInventory.getConsolidatedItems();

        // Create a map to efficiently look up stored amounts by material
        Map<Material, Long> materialAmountMap = new HashMap<>();
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : storedItems.entrySet()) {
            Material material = entry.getKey().getTemplateRef().getType();
            materialAmountMap.merge(material, entry.getValue(), Long::sum);
        }

        // Get all possible loot items
        EntityType entityType = spawner.getEntityType();
        EntityLootConfig lootConfig = plugin.getEntityLootRegistry().getLootConfig(entityType);
        List<LootItem> possibleLootItems = lootConfig != null
                ? lootConfig.getValidItems(true)
                : Collections.emptyList();

        // Use StringBuilder for efficient string concatenation
        StringBuilder lootItemsBuilder = new StringBuilder(possibleLootItems.size() * 40); // Estimate space needed

        if (!possibleLootItems.isEmpty()) {
            // Get the loot item format once to avoid repeated config lookups
            String lootItemFormat = languageManager.getGuiItemName("spawner_storage_item.loot_items", EMPTY_PLACEHOLDERS);

            // Sort items by name for consistent display
            possibleLootItems.sort(Comparator.comparing(item ->
                    languageManager.getVanillaItemName(item.getMaterial())));

            for (LootItem lootItem : possibleLootItems) {
                Material material = lootItem.getMaterial();
                long amount = materialAmountMap.getOrDefault(material, 0L);

                // Get cached item name if possible
                String itemName = languageManager.getVanillaItemName(material);
                String itemNameSmallCaps = languageManager.getSmallCaps(itemName);
                String formattedAmount = languageManager.formatNumber(amount);

                // Format the line with minimal placeholder replacements
                String line = lootItemFormat
                        .replace("%item_name%", itemName)
                        .replace("%ɪᴛᴇᴍ_ɴᴀᴍᴇ%", itemNameSmallCaps)
                        .replace("%amount%", formattedAmount)
                        .replace("%raw_amount%", String.valueOf(amount))
                        .replace("%chance%", String.format("%.1f", lootItem.getChance()) + "%");

                lootItemsBuilder.append(line).append('\n');
            }
        } else if (!storedItems.isEmpty()) {
            // Get format once
            String lootItemFormat = languageManager.getGuiItemName("spawner_storage_item.loot_items", EMPTY_PLACEHOLDERS);

            // Sort items by name
            List<Map.Entry<VirtualInventory.ItemSignature, Long>> sortedItems =
                    new ArrayList<>(storedItems.entrySet());
            sortedItems.sort(Comparator.comparing(e -> e.getKey().getMaterialName()));

            for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : sortedItems) {
                ItemStack templateItem = entry.getKey().getTemplateRef();
                long amount = entry.getValue();

                String itemName = languageManager.getVanillaItemName(templateItem.getType());
                String itemNameSmallCaps = languageManager.getSmallCaps(itemName);
                String formattedAmount = languageManager.formatNumber(amount);

                // Format with minimal replacements
                String line = lootItemFormat
                        .replace("%item_name%", itemName)
                        .replace("%ɪᴛᴇᴍ_ɴᴀᴍᴇ%", itemNameSmallCaps)
                        .replace("%amount%", formattedAmount)
                        .replace("%raw_amount%", String.valueOf(amount))
                        .replace("%chance%", "");

                lootItemsBuilder.append(line).append('\n');
            }
        } else {
            // Empty inventory - just add the empty message
            String emptyMessage = languageManager.getGuiItemName("spawner_storage_item.loot_items_empty", EMPTY_PLACEHOLDERS);
            if (!emptyMessage.isEmpty()) {
                lootItemsBuilder.append(emptyMessage);
            }
        }

        // Remove trailing newline if it exists
        if (lootItemsBuilder.length() > 0 && lootItemsBuilder.charAt(lootItemsBuilder.length() - 1) == '\n') {
            lootItemsBuilder.setLength(lootItemsBuilder.length() - 1);
        }

        // Add the loot_items string to the placeholders
        placeholders.put("loot_items", lootItemsBuilder.toString());

        // Set display name
        chestMeta.setDisplayName(languageManager.getGuiItemName("spawner_storage_item.name", placeholders));

        // Get lore efficiently
        List<String> lore = languageManager.getGuiItemLoreWithMultilinePlaceholders("spawner_storage_item.lore", placeholders);
        chestMeta.setLore(lore);

        chestItem.setItemMeta(chestMeta);

        // Cache the result for future use
        plugin.getItemCache().put(cacheKey, chestItem.clone());

        return chestItem;
    }

    private ItemStack createSpawnerInfoItem(Player player, SpawnerData spawner) {
        // Get important data upfront
        EntityType entityType = spawner.getEntityType();
        int stackSize = spawner.getStackSize();
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = calculatePercentage(currentItems, maxSlots);
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        int percentExp = calculatePercentage(currentExp, maxExp);

        // Create cache key for this specific spawner's info state
        String cacheKey = spawner.getSpawnerId() + "|info|" + stackSize + "|" + entityType + "|"
                + percentStorage + "|" + percentExp + "|" + spawner.getSpawnerRange() + "|"
                + spawner.getSpawnDelay() + "|" + spawner.getMinMobs() + "|" + spawner.getMaxMobs()
                + "|" + (plugin.hasShopIntegration() && player.hasPermission("smartspawner.sellall"));

        // Check if we have a cached item for this exact spawner state
        ItemStack cachedItem = plugin.getItemCache().getIfPresent(cacheKey);
        if (cachedItem != null) {
            return cachedItem.clone();
        }

        // Not in cache, create the ItemStack
        ItemStack spawnerItem = SpawnerMobHeadTexture.getCustomHead(entityType, player);
        ItemMeta spawnerMeta = spawnerItem.getItemMeta();
        if (spawnerMeta == null) return spawnerItem;

        // Get entity names with proper formatting
        String entityName = languageManager.getFormattedMobName(entityType);
        String entityNameSmallCaps = languageManager.getSmallCaps(entityName);

        // Prepare all placeholders
        Map<String, String> placeholders = new HashMap<>();

        // Entity information
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
        placeholders.put("entity_type", entityType.toString());

        // Stack information
        placeholders.put("stack_size", String.valueOf(stackSize));

        // Spawner settings
        placeholders.put("range", String.valueOf(spawner.getSpawnerRange()));
        placeholders.put("delay", String.valueOf(spawner.getSpawnDelay() / TICKS_PER_SECOND));
        placeholders.put("delay_raw", String.valueOf(spawner.getSpawnDelay()));
        placeholders.put("min_mobs", String.valueOf(spawner.getMinMobs()));
        placeholders.put("max_mobs", String.valueOf(spawner.getMaxMobs()));

        // Storage information
        placeholders.put("current_items", String.valueOf(currentItems));
        placeholders.put("max_items", languageManager.formatNumber(maxSlots));
        placeholders.put("percent_storage", String.valueOf(percentStorage));
        placeholders.put("formatted_storage", String.format("%.1f", (double)percentStorage));

        // Experience information
        placeholders.put("current_exp", languageManager.formatNumber(currentExp));
        placeholders.put("max_exp", languageManager.formatNumber(maxExp));
        placeholders.put("raw_current_exp", String.valueOf(currentExp));
        placeholders.put("raw_max_exp", String.valueOf(maxExp));
        placeholders.put("percent_exp", String.valueOf(percentExp));
        placeholders.put("formatted_exp", String.format("%.1f", (double)percentExp));

        // Set display name with the specified placeholders
        spawnerMeta.setDisplayName(languageManager.getGuiItemName("spawner_info_item.name", placeholders));

        // Select appropriate lore based on shop integration availability
        String loreKey = plugin.hasShopIntegration() && player.hasPermission("smartspawner.sellall")
                ? "spawner_info_item.lore"
                : "spawner_info_item.lore_no_shop";

        // Get and set lore with placeholders
        List<String> lore = languageManager.getGuiItemLoreWithMultilinePlaceholders(loreKey, placeholders);
        spawnerMeta.setLore(lore);

        spawnerItem.setItemMeta(spawnerMeta);

        // Cache the result for future use
        plugin.getItemCache().put(cacheKey, spawnerItem.clone());

        return spawnerItem;
    }

    private ItemStack createExpItem(SpawnerData spawner) {
        ItemStack expItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta expMeta = expItem.getItemMeta();
        if (expMeta == null) return expItem;

        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        String formattedExp = languageManager.formatNumber(currentExp);
        String formattedMaxExp = languageManager.formatNumber(maxExp);
        int percentExp = calculatePercentage(currentExp, maxExp);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("current_exp", formattedExp);
        placeholders.put("raw_current_exp", String.valueOf(currentExp));
        placeholders.put("max_exp", formattedMaxExp);
        placeholders.put("percent_exp", String.valueOf(percentExp));
        placeholders.put("u_max_exp", String.valueOf(maxExp));

        expMeta.setDisplayName(languageManager.getGuiItemName("exp_info_item.name", placeholders));

        String[] loreArray = languageManager.getGuiItemLore("exp_info_item.lore", placeholders);
        List<String> loreExp = Arrays.asList(loreArray);

        expMeta.setLore(loreExp);
        expItem.setItemMeta(expMeta);
        return expItem;
    }

    private int calculatePercentage(long current, long maximum) {
        return maximum > 0 ? (int) ((double) current / maximum * 100) : 0;
    }
}