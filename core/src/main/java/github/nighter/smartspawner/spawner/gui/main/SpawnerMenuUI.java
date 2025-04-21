package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.holders.SpawnerMenuHolder;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.utils.SpawnerMobHeadTexture;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpawnerMenuUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int CHEST_SLOT = 11;
    private static final int SPAWNER_INFO_SLOT = 13;
    private static final int EXP_SLOT = 15;
    private static final int TICKS_PER_SECOND = 20;

    private final SmartSpawner plugin;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final LanguageManager languageManager;

    public SpawnerMenuUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
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

        if (spawner.getIsAtCapacity()) {
            spawnerGuiViewManager.updateSpawnerGuiInfo(player, spawner, false);
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

    private ItemStack createLootStorageItem(SpawnerData spawner) {
        ItemStack chestItem = new ItemStack(Material.CHEST);
        ItemMeta chestMeta = chestItem.getItemMeta();
        if (chestMeta == null) return chestItem;

        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = calculatePercentage(currentItems, maxSlots);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("max_slots", String.valueOf(maxSlots));
        placeholders.put("current_items", String.valueOf(currentItems));
        placeholders.put("percent_storage", String.valueOf(percentStorage));

        chestMeta.setDisplayName(languageManager.getGuiItemName("spawner_storage_item.name", placeholders));

        String[] loreArray = languageManager.getGuiItemLore("spawner_storage_item.lore", placeholders);
        List<String> chestLore = Arrays.asList(loreArray);

        chestMeta.setLore(chestLore);
        chestItem.setItemMeta(chestMeta);
        return chestItem;
    }

    private ItemStack createSpawnerInfoItem(Player player, SpawnerData spawner) {
        ItemStack spawnerItem = SpawnerMobHeadTexture.getCustomHead(spawner.getEntityType(), player);
        ItemMeta spawnerMeta = spawnerItem.getItemMeta();
        if (spawnerMeta == null) return spawnerItem;

        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String entityNameSmallCaps = languageManager.getSmallCaps(entityName);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
        placeholders.put("stack_size", String.valueOf(spawner.getStackSize()));
        placeholders.put("range", String.valueOf(spawner.getSpawnerRange()));
        placeholders.put("delay", String.valueOf(spawner.getSpawnDelay() / TICKS_PER_SECOND));
        placeholders.put("min_mobs", String.valueOf(spawner.getMinMobs()));
        placeholders.put("max_mobs", String.valueOf(spawner.getMaxMobs()));

        spawnerMeta.setDisplayName(languageManager.getGuiItemName("spawner_info_item.name", placeholders));

        // Select appropriate lore based on shop integration availability
        String loreKey = plugin.hasShopIntegration() && player.hasPermission("smartspawner.sellall")
                ? "spawner_info_item.lore"
                : "spawner_info_item.lore_no_shop";

        String[] loreArray = languageManager.getGuiItemLore(loreKey, placeholders);
        List<String> lore = Arrays.asList(loreArray);

        spawnerMeta.setLore(lore);
        spawnerItem.setItemMeta(spawnerMeta);
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