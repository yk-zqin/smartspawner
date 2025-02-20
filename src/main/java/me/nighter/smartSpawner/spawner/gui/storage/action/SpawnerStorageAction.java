package me.nighter.smartSpawner.spawner.gui.storage.action;

import me.nighter.smartSpawner.*;
import me.nighter.smartSpawner.spawner.gui.main.SpawnerMenuUI;
import me.nighter.smartSpawner.spawner.properties.VirtualInventory;
import me.nighter.smartSpawner.spawner.gui.storage.StoragePageHolder;
import me.nighter.smartSpawner.spawner.gui.storage.SpawnerStorageUI;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;
    private static final Set<Integer> CONTROL_SLOTS = Set.of(45, 46, 48, 49, 50, 53);

    private final Map<ClickType, ItemClickHandler> clickHandlers;

    public SpawnerStorageAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.clickHandlers = initializeClickHandlers();
        this.spawnerMenuUI = new SpawnerMenuUI(plugin);
    }

    private Map<ClickType, ItemClickHandler> initializeClickHandlers() {
        Map<ClickType, ItemClickHandler> handlers = new EnumMap<>(ClickType.class);
        handlers.put(ClickType.SHIFT_LEFT, (player, inv, slot, item, spawner) ->
                handleTakeAllSimilarItems(player, inv, item, spawner));
        handlers.put(ClickType.SHIFT_RIGHT, (player, inv, slot, item, spawner) ->
                handleTakeAllSimilarItems(player, inv, item, spawner));
        handlers.put(ClickType.RIGHT, (player, inv, slot, item, spawner) ->
                takeSingleItem(player, inv, slot, item, spawner, true));
        handlers.put(ClickType.LEFT, (player, inv, slot, item, spawner) ->
                takeSingleItem(player, inv, slot, item, spawner, false));
        return Collections.unmodifiableMap(handlers);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player) ||
                !(event.getInventory().getHolder() instanceof StoragePageHolder)) {
            return;
        }

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        StoragePageHolder holder = (StoragePageHolder) event.getInventory().getHolder();
        int slot = event.getRawSlot();

        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        if (CONTROL_SLOTS.contains(slot)) {
            handleControlSlotClick(player, slot, holder, holder.getSpawnerData(), event.getInventory());
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        ItemClickHandler handler = clickHandlers.get(event.getClick());
        if (handler != null) {
            handler.handle(player, event.getInventory(), slot, clickedItem, holder.getSpawnerData());
        }
    }

    private void takeSingleItem(Player player, Inventory sourceInv, int slot, ItemStack item,
                                SpawnerData spawner, boolean singleItem) {
        PlayerInventory playerInv = player.getInventory();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        ItemMoveResult result = ItemMoveHelper.moveItems(
                item,
                singleItem ? 1 : item.getAmount(),
                playerInv,
                virtualInv
        );

        if (result.getAmountMoved() > 0) {
            updateInventorySlot(sourceInv, slot, item, result.getAmountMoved());
            virtualInv.removeItems(result.getMovedItems());
            player.updateInventory();
            spawner.updateHologramData();
        } else {
            languageManager.sendMessage(player, "messages.inventory-full");
        }
    }

    private void handleTakeAllSimilarItems(Player player, Inventory sourceInv, ItemStack targetItem,
                                           SpawnerData spawner) {
        Map<Integer, ItemStack> similarItems = findSimilarItems(sourceInv, targetItem);
        if (similarItems.isEmpty()) return;

        BatchItemMoveResult result = ItemMoveHelper.moveBatchItems(
                similarItems,
                player.getInventory(),
                spawner.getVirtualInventory()
        );

        if (result.getTotalMoved() > 0) {
            updateBatchInventory(sourceInv, result.getSlotUpdates());
            spawner.getVirtualInventory().removeItems(result.getMovedItems());
            player.updateInventory();
        }

        if (result.isInventoryFull()) {
            languageManager.sendMessage(player, "messages.inventory-full");
        }
    }

    private static Map<Integer, ItemStack> findSimilarItems(Inventory sourceInv, ItemStack targetItem) {
        Map<Integer, ItemStack> similarItems = new HashMap<>();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack invItem = sourceInv.getItem(i);
            if (invItem != null && invItem.getType() != Material.AIR && invItem.isSimilar(targetItem)) {
                similarItems.put(i, invItem.clone());
            }
        }
        return similarItems;
    }

    private static void updateInventorySlot(Inventory sourceInv, int slot, ItemStack item, int amountMoved) {
        if (amountMoved >= item.getAmount()) {
            sourceInv.setItem(slot, null);
            return;
        }

        ItemStack remaining = item.clone();
        remaining.setAmount(item.getAmount() - amountMoved);
        sourceInv.setItem(slot, remaining);
    }

    private static void updateBatchInventory(Inventory sourceInv, Map<Integer, ItemUpdate> slotUpdates) {
        slotUpdates.forEach((slot, update) ->
                sourceInv.setItem(slot, update.getUpdatedItem()));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof StoragePageHolder) {
            event.setCancelled(true);
        }
    }

    private void handleControlSlotClick(Player player, int slot, StoragePageHolder holder,
                                        SpawnerData spawner, Inventory inventory) {
        switch (slot) {
            case 48:
                if (holder.getCurrentPage() > 1) {
                    openLootPage(player, spawner, holder.getCurrentPage() - 1, false);
                }
                break;
            case 50:
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    openLootPage(player, spawner, holder.getCurrentPage() + 1, false);
                }
                break;
            case 49:
                handleSellAllItems(player, spawner, holder);
                break;
            case 53:
                openMainMenu(player, spawner);
                break;
            case 45:
                handleTakeAllItems(player, inventory);
                break;
            case 46:
                handleToggleEquipment(player, spawner, holder);
                break;
        }
    }

    private void handleSellAllItems(Player player, SpawnerData spawner, StoragePageHolder holder) {
        if (!plugin.hasShopIntegration()) return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        if (!player.hasPermission("smartspawner.sellall")) {
            player.sendMessage(languageManager.getMessage("no-permission"));
            return;
        }

        if (plugin.getShopIntegration().sellAllItems(player, spawner)) {
            openLootPage(player, spawner, holder.getCurrentPage(), true);
        }
    }

    private void openMainMenu(Player player, SpawnerData spawner) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
    }

    private void handleToggleEquipment(Player player, SpawnerData spawner, StoragePageHolder holder) {
        if (configManager.isAllowToggleEquipmentItems()) {
            spawner.setAllowEquipmentItems(!spawner.isAllowEquipmentItems());
            openLootPage(player, spawner, holder.getCurrentPage(), true);
        }
    }

    private void openLootPage(Player player, SpawnerData spawner, int page, boolean refresh) {
        SpawnerStorageUI lootManager = plugin.getLootManager();
        String title = languageManager.getGuiTitle("gui-title.loot-menu");
        Inventory pageInventory = lootManager.createInventory(spawner, title, page);

        Sound sound = refresh ? Sound.ITEM_ARMOR_EQUIP_DIAMOND : Sound.UI_BUTTON_CLICK;
        float pitch = refresh ? 1.2f : 1.0f;
        player.playSound(player.getLocation(), sound, 1.0f, pitch);

        player.openInventory(pageInventory);
    }

    public void handleTakeAllItems(Player player, Inventory sourceInventory) {
        StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder();
        SpawnerData spawner = holder.getSpawnerData();
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        Map<Integer, ItemStack> sourceItems = new HashMap<>();
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            ItemStack item = sourceInventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                sourceItems.put(i, item.clone());
            }
        }

        if (sourceItems.isEmpty()) {
            languageManager.sendMessage(player, "messages.no-items-to-take");
            return;
        }

        TransferResult result = transferItems(player, sourceInventory, sourceItems, virtualInv);
        sendTransferMessage(player, result);
        player.updateInventory();
    }

    private TransferResult transferItems(Player player, Inventory sourceInventory,
                                         Map<Integer, ItemStack> sourceItems, VirtualInventory virtualInv) {
        boolean anyItemMoved = false;
        boolean inventoryFull = false;
        PlayerInventory playerInv = player.getInventory();
        int totalAmountMoved = 0;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        for (Map.Entry<Integer, ItemStack> entry : sourceItems.entrySet()) {
            int sourceSlot = entry.getKey();
            ItemStack itemToMove = entry.getValue();

            int amountToMove = itemToMove.getAmount();
            int amountMoved = 0;

            for (int i = 0; i < 36 && amountToMove > 0; i++) {
                ItemStack targetItem = playerInv.getItem(i);

                if (targetItem == null || targetItem.getType() == Material.AIR) {
                    ItemStack newStack = itemToMove.clone();
                    newStack.setAmount(Math.min(amountToMove, itemToMove.getMaxStackSize()));
                    playerInv.setItem(i, newStack);
                    amountMoved += newStack.getAmount();
                    amountToMove -= newStack.getAmount();
                    anyItemMoved = true;
                }
                else if (targetItem.isSimilar(itemToMove)) {
                    int spaceInStack = targetItem.getMaxStackSize() - targetItem.getAmount();
                    if (spaceInStack > 0) {
                        int addAmount = Math.min(spaceInStack, amountToMove);
                        targetItem.setAmount(targetItem.getAmount() + addAmount);
                        amountMoved += addAmount;
                        amountToMove -= addAmount;
                        anyItemMoved = true;
                    }
                }
            }

            if (amountMoved > 0) {
                totalAmountMoved += amountMoved;

                ItemStack movedItem = itemToMove.clone();
                movedItem.setAmount(amountMoved);
                itemsToRemove.add(movedItem);

                if (amountMoved == itemToMove.getAmount()) {
                    sourceInventory.setItem(sourceSlot, null);
                } else {
                    ItemStack remaining = itemToMove.clone();
                    remaining.setAmount(itemToMove.getAmount() - amountMoved);
                    sourceInventory.setItem(sourceSlot, remaining);
                    inventoryFull = true;
                }
            }

            if (inventoryFull) {
                break;
            }
        }

        if (!itemsToRemove.isEmpty()) {
            virtualInv.removeItems(itemsToRemove);
            StoragePageHolder holder = (StoragePageHolder) sourceInventory.getHolder();
            holder.getSpawnerData().updateHologramData();
        }

        return new TransferResult(anyItemMoved, inventoryFull, totalAmountMoved);
    }
    private void sendTransferMessage(Player player, TransferResult result) {
        if (!result.anyItemMoved) {
            languageManager.sendMessage(player, "messages.inventory-full");
        } else if (result.inventoryFull) {
            languageManager.sendMessage(player, "messages.take-some-items",
                    "%amount%", String.valueOf(result.totalMoved));
        } else {
            languageManager.sendMessage(player, "messages.take-all-items",
                    "%amount%", String.valueOf(result.totalMoved));
        }
    }

    private record TransferResult(boolean anyItemMoved, boolean inventoryFull, int totalMoved) {}
    private record TransferItemResult(int amountMoved, boolean inventoryFull) {}
}