package github.nighter.smartspawner.spawner.gui.storage;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemClickHandler;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveHelper;
import github.nighter.smartspawner.spawner.gui.storage.utils.ItemMoveResult;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuAction;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.synchronization.SpawnerGuiViewManager;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import github.nighter.smartspawner.holders.StoragePageHolder;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.inventory.InventoryAction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerStorageAction implements Listener {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerStorageUI spawnerStorageUI;
    private final SpawnerGuiViewManager spawnerGuiViewManager;
    private final SpawnerMenuAction spawnerMenuAction;
    private final MessageService messageService;

    private static final int INVENTORY_SIZE = 54;
    private static final int STORAGE_SLOTS = 45;
    private static final Set<Integer> CONTROL_SLOTS = Set.of(45, 46, 48, 49, 50, 52, 53);

    private final Map<ClickType, ItemClickHandler> clickHandlers;
    private final Map<UUID, Inventory> openStorageInventories = new HashMap<>();

    // Track currently selected slot for drop functionality
    private final Map<UUID, SelectedItemInfo> selectedItems = new ConcurrentHashMap<>();

    // Anti spam click properties
    private final Map<UUID, Long> lastItemClickTime = new ConcurrentHashMap<>();

    public SpawnerStorageAction(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.clickHandlers = initializeClickHandlers();
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerStorageUI = plugin.getSpawnerStorageUI();
        this.spawnerGuiViewManager = plugin.getSpawnerGuiViewManager();
        this.spawnerMenuAction = plugin.getSpawnerMenuAction();
        this.messageService = plugin.getMessageService();
    }

    private Map<ClickType, ItemClickHandler> initializeClickHandlers() {
        Map<ClickType, ItemClickHandler> handlers = new EnumMap<>(ClickType.class);
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

        Player player = (Player) event.getWhoClicked();
        StoragePageHolder holder = (StoragePageHolder) event.getInventory().getHolder();
        SpawnerData spawner = holder.getSpawnerData();
        int slot = event.getRawSlot();

        // Handle drop actions specifically (Q key press)
        if (event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                event.getAction() == InventoryAction.DROP_ALL_SLOT) {

            if (slot >= 0 && slot < STORAGE_SLOTS) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                    // Don't cancel the event yet
                    event.setCancelled(true);

                    // Handle the drop based on whether it's drop all or drop one
                    boolean dropStack = event.getAction() == InventoryAction.DROP_ALL_SLOT;
                    handleItemDrop(player, spawner, event.getInventory(), slot, clickedItem, dropStack);
                    return;
                }
            }
        }

        // For all other actions, cancel and handle custom behavior
        event.setCancelled(true);

        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return;
        }

        if (CONTROL_SLOTS.contains(slot)) {
            handleControlSlotClick(player, slot, holder, spawner, event.getInventory());
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        ItemClickHandler handler = clickHandlers.get(event.getClick());
        if (handler != null) {
            handler.handle(player, event.getInventory(), slot, clickedItem, spawner);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof StoragePageHolder) {
            event.setCancelled(true);
        }
    }

    private void handleItemDrop(Player player, SpawnerData spawner, Inventory inventory,
                                int slot, ItemStack item, boolean dropStack) {
        // Determine amount to drop
        int amountToDrop = dropStack ? item.getAmount() : 1;

        // Create the item to drop
        ItemStack droppedItem = item.clone();
        droppedItem.setAmount(Math.min(amountToDrop, item.getAmount()));

        // Update the virtual inventory
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        List<ItemStack> itemsToRemove = new ArrayList<>();
        itemsToRemove.add(droppedItem);
        virtualInv.removeItems(itemsToRemove);

        // Update the displayed inventory
        int remaining = item.getAmount() - amountToDrop;
        if (remaining <= 0) {
            inventory.setItem(slot, null);
        } else {
            ItemStack remainingItem = item.clone();
            remainingItem.setAmount(remaining);
            inventory.setItem(slot, remainingItem);
        }
        // Most performant - just offset in the rough direction they're facing
        Location dropLocation = player.getLocation().clone();

        // Add a small offset in the direction of their yaw
        double yaw = Math.toRadians(player.getLocation().getYaw());
        dropLocation.add(-Math.sin(yaw) * 1.8, 0.1, Math.cos(yaw) * 1.8);

        // Drop the item at the calculated location instead of directly on the player
        player.getWorld().dropItem(dropLocation, droppedItem);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);

        // Update hologram and capacity state
        spawner.updateHologramData();

        // Synchronize total pages after item removal
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder();
        if (holder != null) {
            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

            // Check if spawner is at capacity and update if necessary
            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
        }
    }

    private void handleControlSlotClick(Player player, int slot, StoragePageHolder holder,
                                        SpawnerData spawner, Inventory inventory) {
        switch (slot) {
            case 48:
                if (holder.getCurrentPage() > 1) {
                    updatePageContent(player, spawner, holder.getCurrentPage() - 1, inventory, false);
                }
                break;
            case 50:
                if (holder.getCurrentPage() < holder.getTotalPages()) {
                    updatePageContent(player, spawner, holder.getCurrentPage() + 1, inventory, false);
                }
                break;
            case 49:
                handleSellAllItems(player, spawner, holder);
                break;
            case 52:
                handleDiscardAllItems(player, spawner, inventory);
                break;
            case 53:
                openMainMenu(player, spawner);
                break;
            case 45:
                handleTakeAllItems(player, inventory);
                break;
            case 46:
                handleToggleEquipment(player, spawner, inventory);
                break;
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

            // Update hologram and recalculate pages after item removal
            spawner.updateHologramData();

            // Synchronize total pages after item removal
            StoragePageHolder holder = (StoragePageHolder) sourceInv.getHolder();
            if (holder != null) {
                spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

                // Check if spawner is at capacity and update if necessary
                if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                    spawner.setIsAtCapacity(false);
                }
            }
        } else {
            messageService.sendMessage(player, "inventory_full");
        }
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

    private void updatePageContent(Player player, SpawnerData spawner, int newPage, Inventory inventory, boolean refresh) {
        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder();

        // Always recalculate total pages to ensure synchronization
        int totalPages = calculateTotalPages(spawner);

        // Update holder with latest values
        assert holder != null;
        holder.setTotalPages(totalPages);
        holder.setCurrentPage(newPage);
        holder.updateOldUsedSlots();

        // Update inventory display
        lootManager.updateDisplay(inventory, spawner, newPage, totalPages);

        // Update inventory title to reflect current page and total pages
        updateInventoryTitle(player, inventory, spawner, newPage, totalPages);

        Sound sound = refresh ? Sound.ITEM_ARMOR_EQUIP_DIAMOND : Sound.UI_BUTTON_CLICK;
        float pitch = refresh ? 1.2f : 1.0f;
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }

    private void updateInventoryTitle(Player player, Inventory inventory, SpawnerData spawner, int page, int totalPages) {
        String baseTitle = languageManager.getGuiTitle("gui_title_storage");
        String newTitle = baseTitle + " - [" + page + "/" + totalPages + "]";

        try {
            player.getOpenInventory().setTitle(newTitle);
        } catch (Exception e) {
            // Fallback: if title update fails, recreate and reopen the inventory
            openLootPage(player, spawner, page, false);
        }
    }

    private void handleSellAllItems(Player player, SpawnerData spawner, StoragePageHolder holder) {
        if (!plugin.hasShopIntegration()) return;

        if (isClickTooFrequent(player)) {
            return;
        }

        // Play click sound
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Permission check
        if (!player.hasPermission("smartspawner.sellall")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        // Use the cooldown system from SpawnerMenuAction
        if (spawnerMenuAction.isSellCooldownActive(player)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("time", spawnerMenuAction.getRemainingCooldownTimeFormatted(player));
            messageService.sendMessage(player, "shop.sell_cooldown", placeholders);
            return;
        }

        // Update cooldown timestamp before processing
        spawnerMenuAction.updateSellCooldown(player);

        // Process the sale through shop integration
        boolean success = plugin.getShopIntegration().sellAllItems(player, spawner);

        // Reset at capacity if successful
        if (success && spawner.getIsAtCapacity()) {
            spawner.setIsAtCapacity(false);
        }
    }

    private boolean isClickTooFrequent(Player player) {
        long now = System.currentTimeMillis();
        long last = lastItemClickTime.getOrDefault(player.getUniqueId(), 0L);
        lastItemClickTime.put(player.getUniqueId(), now);
        return (now - last) < 300;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastItemClickTime.remove(playerId);
        selectedItems.remove(playerId);
    }

    private void openMainMenu(Player player, SpawnerData spawner) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        spawnerMenuUI.openSpawnerMenu(player, spawner, true);
    }

    private void handleToggleEquipment(Player player, SpawnerData spawner, Inventory inventory) {
        if (isClickTooFrequent(player)) {
            return;
        }
        spawner.setAllowEquipmentItems(!spawner.isAllowEquipmentItems());

        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder();
        updatePageContent(player, spawner, holder.getCurrentPage(), inventory, true);
    }

    private void handleDiscardAllItems(Player player, SpawnerData spawner, Inventory inventory) {
        if (isClickTooFrequent(player)) {
            return;
        }
        // Get the virtual inventory
        VirtualInventory virtualInv = spawner.getVirtualInventory();

        // Check if there are items to discard
        if (virtualInv.getUsedSlots() == 0) {
            messageService.sendMessage(player, "no_items_to_discard");
            return;
        }

        // Count the total items being discarded for the message
        long totalItems = virtualInv.getTotalItems();

        // Clear all items from the virtual inventory
        Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();
        List<ItemStack> itemsToRemove = new ArrayList<>();

        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            // Handle stacks efficiently by creating appropriate sized stacks
            while (amount > 0) {
                ItemStack stack = template.clone();
                int stackSize = (int) Math.min(amount, template.getMaxStackSize());
                stack.setAmount(stackSize);
                itemsToRemove.add(stack);
                amount -= stackSize;
            }
        }

        // Remove all items at once
        virtualInv.removeItems(itemsToRemove);

        // Get the holder for updating
        StoragePageHolder holder = (StoragePageHolder) inventory.getHolder();
        int oldTotalPages = calculateTotalPages(spawner);

        // Update the spawner state
        spawner.updateHologramData();
        if (spawner.getIsAtCapacity()) {
            spawner.setIsAtCapacity(false);
        }

        // Update the holder
        int newTotalPages = calculateTotalPages(spawner);
        holder.setTotalPages(newTotalPages);
        holder.setCurrentPage(1); // Reset to first page after discarding all
        holder.updateOldUsedSlots();

        // Update all viewers
        spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

        // Update the current inventory view
        updatePageContent(player, spawner, 1, inventory, true);

        // Send a confirmation message
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("amount", languageManager.formatNumber(totalItems));
        messageService.sendMessage(player, "discard_all_success", placeholders);
    }

    private void openLootPage(Player player, SpawnerData spawner, int page, boolean refresh) {
        SpawnerStorageUI lootManager = plugin.getSpawnerStorageUI();
        String title = languageManager.getGuiTitle("gui_title_storage");

        // Recalculate total pages to ensure consistency
        int totalPages = calculateTotalPages(spawner);

        // Ensure page is valid
        page = Math.max(1, Math.min(page, totalPages));

        // Check if player already has an open inventory cache
        UUID playerId = player.getUniqueId();
        Inventory existingInventory = openStorageInventories.get(playerId);

        // If the player already has an open inventory, update the content
        if (existingInventory != null && !refresh && existingInventory.getHolder() instanceof StoragePageHolder) {
            StoragePageHolder holder = (StoragePageHolder) existingInventory.getHolder();

            // Update holder with latest values
            holder.setTotalPages(totalPages);
            holder.setCurrentPage(page);
            holder.updateOldUsedSlots();

            updatePageContent(player, spawner, page, existingInventory, false);
            return;
        }

        // If there is no existing inventory or a refresh is needed, create a new inventory
        Inventory pageInventory = lootManager.createInventory(spawner, title, page, totalPages);

        // Store the inventory in the cache
        openStorageInventories.put(playerId, pageInventory);

        Sound sound = refresh ? Sound.ITEM_ARMOR_EQUIP_DIAMOND : Sound.UI_BUTTON_CLICK;
        float pitch = refresh ? 1.2f : 1.0f;
        player.playSound(player.getLocation(), sound, 1.0f, pitch);

        player.openInventory(pageInventory);
    }

    public void handleTakeAllItems(Player player, Inventory sourceInventory) {
        if (isClickTooFrequent(player)) {
            return;
        }
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
            messageService.sendMessage(player, "no_items_to_take");
            return;
        }

        TransferResult result = transferItems(player, sourceInventory, sourceItems, virtualInv);
        sendTransferMessage(player, result);
        player.updateInventory();

        // After items are taken, recalculate pages and update the UI
        if (result.anyItemMoved) {
            // Recalculate total pages
            int newTotalPages = calculateTotalPages(spawner);

            // Update holder
            holder.setTotalPages(newTotalPages);

            // Update the display and title
            spawnerGuiViewManager.updateSpawnerMenuViewers(spawner);

            // Check if spawner is at capacity and update if necessary
            if (spawner.getMaxSpawnerLootSlots() > holder.getOldUsedSlots() && spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }
        }
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
            holder.updateOldUsedSlots();
        }

        return new TransferResult(anyItemMoved, inventoryFull, totalAmountMoved);
    }

    private void sendTransferMessage(Player player, TransferResult result) {
        if (!result.anyItemMoved) {
            messageService.sendMessage(player, "inventory_full");
        } else {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("amount", String.valueOf(result.totalMoved));
            messageService.sendMessage(player, "take_all_items", placeholders);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof StoragePageHolder)) {
            return;
        }

        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            UUID playerId = player.getUniqueId();
            openStorageInventories.remove(playerId);
            selectedItems.remove(playerId);
        }
    }

    private record TransferResult(boolean anyItemMoved, boolean inventoryFull, int totalMoved) {}

    private record SelectedItemInfo(SpawnerData spawnerData, Inventory inventory, int slot, ItemStack item) {}
}