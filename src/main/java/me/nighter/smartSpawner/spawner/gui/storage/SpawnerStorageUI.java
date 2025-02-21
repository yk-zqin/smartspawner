package me.nighter.smartSpawner.spawner.gui.storage;

import me.nighter.smartSpawner.*;
import me.nighter.smartSpawner.holders.StoragePageHolder;
import me.nighter.smartSpawner.spawner.properties.VirtualInventory;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.*;

public class SpawnerStorageUI implements SpawnerStorageDisplay {
    private static final int NAVIGATION_ROW = 5;
    private static final int SLOTS_PER_PAGE = 45;
    private static final int INVENTORY_SIZE = 54;

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final Map<String, ItemStack> cachedButtons;

    public SpawnerStorageUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.cachedButtons = new HashMap<>();
        initializeCachedButtons();
    }

    private void initializeCachedButtons() {
        cachedButtons.put("return", createButton(
                Material.SPAWNER,
                languageManager.getMessage("return-button.name"),
                Collections.emptyList()
        ));

        cachedButtons.put("takeAll", createButton(
                Material.CHEST,
                languageManager.getMessage("take-all-button.name"),
                Collections.emptyList()
        ));
    }

    @Override
    public Inventory createInventory(SpawnerData spawner, String title, int page) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();
        int totalItems = displayItems.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / SLOTS_PER_PAGE));
        page = Math.min(Math.max(1, page), totalPages);

        Inventory pageInv = Bukkit.createInventory(
                new StoragePageHolder(spawner, page, totalPages),
                INVENTORY_SIZE,
                title + " - [" + page + "/" + totalPages + "]"
        );

        updateDisplay(pageInv, spawner, page);
        return pageInv;
    }

    @Override
    public void updateDisplay(Inventory inventory, SpawnerData spawner, int page) {
        Map<Integer, ItemStack> updates = new HashMap<>();

        // Clear existing items
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            updates.put(i, null);
        }

        addPageItems(updates, spawner, page);
        addNavigationButtons(updates, spawner, page);

        // Batch update inventory
        updates.forEach(inventory::setItem);

        if (configManager.isHologramEnabled()) {
            spawner.updateHologramData();
        }
    }

    private void addPageItems(Map<Integer, ItemStack> updates, SpawnerData spawner, int page) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> displayItems = virtualInv.getDisplayInventory();

        int startIndex = (page - 1) * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, displayItems.size());

        int slot = 0;
        for (Map.Entry<Integer, ItemStack> entry : displayItems.entrySet()) {
            if (slot >= startIndex && slot < endIndex) {
                updates.put(slot - startIndex, entry.getValue());
            }
            slot++;
            if (slot >= endIndex) break;
        }
    }

    private void addNavigationButtons(Map<Integer, ItemStack> updates, SpawnerData spawner, int page) {
        int totalPages = getTotalPages(spawner);

        // Navigation buttons
        if (page > 1) {
            updates.put(NAVIGATION_ROW * 9 + 3, createNavigationButton("previous", page - 1));
        }

        if (page < totalPages) {
            updates.put(NAVIGATION_ROW * 9 + 5, createNavigationButton("next", page + 1));
        }

        // Add page indicator
        updates.put(NAVIGATION_ROW * 9 + 4, createPageIndicator(page, totalPages, spawner));

        // Add cached buttons
        updates.put(NAVIGATION_ROW * 9 + 8, cachedButtons.get("return"));
        updates.put(NAVIGATION_ROW * 9 + 0, cachedButtons.get("takeAll"));

        // Add equipment toggle if enabled
        if (configManager.isAllowToggleEquipmentItems()) {
            updates.put(NAVIGATION_ROW * 9 + 1, createEquipmentToggleButton(spawner.isAllowEquipmentItems()));
        }
    }

    private int getTotalPages(SpawnerData spawner) {
        int totalItems = spawner.getVirtualInventory().getDisplayInventory().size();
        return Math.max(1, (int) Math.ceil((double) totalItems / SLOTS_PER_PAGE));
    }

    private ItemStack createButton(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNavigationButton(String type, int targetPage) {
        String buttonName;
        String loreKey;

        if (type.equals("previous")) {
            buttonName = languageManager.getMessage("navigation-button.previous.name")
                    .replace("%target_page%", String.valueOf(targetPage));
            loreKey = "navigation-button.previous.lore";
        } else {
            buttonName = languageManager.getMessage("navigation-button.next.name")
                    .replace("%target_page%", String.valueOf(targetPage));
            loreKey = "navigation-button.next.lore";
        }

        List<String> buttonLore = Arrays.asList(
                languageManager.getMessage(loreKey)
                        .replace("%target_page%", String.valueOf(targetPage))
                        .split("\n")
        );

        return createButton(Material.ARROW, buttonName, buttonLore);
    }

    private ItemStack createEquipmentToggleButton(boolean currentState) {
        String displayName = languageManager.getMessage("equipment-toggle.name");
        String loreKey = currentState ? "equipment-toggle.lore.enabled" : "equipment-toggle.lore.disabled";
        List<String> lore = Arrays.asList(languageManager.getMessage(loreKey).split("\n"));

        return createButton(Material.HOPPER, displayName, lore);
    }

    private ItemStack createPageIndicator(int currentPage, int totalPages, SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int usedSlots = virtualInv.getUsedSlots();
        int percentStorage = (int) ((double) usedSlots / maxSlots * 100);

        String formattedMaxSlots = languageManager.formatNumberTenThousand(maxSlots);
        String formattedUsedSlots = languageManager.formatNumberTenThousand(usedSlots);

        Material material = plugin.hasShopIntegration() ? Material.GOLD_INGOT : Material.PAPER;
        String nameKey = plugin.hasShopIntegration() ? "shop-page-indicator.name" : "page-indicator.name";
        String loreKey = plugin.hasShopIntegration() ? "shop-page-indicator.lore" : "page-indicator.lore";

        String name = languageManager.getMessage(nameKey)
                .replace("%current_page%", String.valueOf(currentPage))
                .replace("%total_pages%", String.valueOf(totalPages));

        String loreText = languageManager.getMessage(loreKey)
                .replace("%max_slots%", formattedMaxSlots)
                .replace("%used_slots%", formattedUsedSlots)
                .replace("%percent_storage%", String.valueOf(percentStorage));

        List<String> lore = Arrays.asList(loreText.split("\n"));

        return createButton(material, name, lore);
    }
}