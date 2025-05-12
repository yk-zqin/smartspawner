package github.nighter.smartspawner.economy;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CustomEconomyManager {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final ItemPriceManager priceManager;
    private Economy economy;
    private boolean isVaultAvailable;
    private final Map<String, CacheEntry> transactionCache = new ConcurrentHashMap<>();
    private final Map<Material, Double> materialPriceCache = new ConcurrentHashMap<>();
    private Scheduler.Task cleanupTask;

    // Cache cleanup interval in minutes
    private static final int CACHE_CLEANUP_INTERVAL_MINUTES = 15;
    // Cache entry expiration time in milliseconds (30 minutes)
    private static final long CACHE_EXPIRY_MS = 30 * 60 * 1000;

    public CustomEconomyManager(SmartSpawner plugin, ItemPriceManager priceManager) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.priceManager = priceManager;
        this.isVaultAvailable = false;

        String currencyType = plugin.getConfig().getString("custom_sell_prices.currency", "VAULT");
        if (currencyType.equalsIgnoreCase("VAULT")) {
            if (setupVaultEconomy()) {
                startCleanupTask();
            }
        } else {
            plugin.getLogger().warning("Unsupported currency type: " + currencyType + ". Currently only VAULT is supported.");
        }
    }

    // Static class to hold transaction amount and timestamp
    private static class CacheEntry {
        final double amount;
        final long timestamp;

        CacheEntry(double amount) {
            this.amount = amount;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private void startCleanupTask() {
        // Cancel any existing task
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        // Schedule a new cleanup task to run periodically
        // Convert minutes to ticks (20 ticks per second)
        long ticks = CACHE_CLEANUP_INTERVAL_MINUTES * 60 * 20;

        cleanupTask = Scheduler.runTaskTimerAsync(this::cleanTransactionCache, ticks, ticks);
    }

    private void cleanTransactionCache() {
        if (transactionCache.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        int beforeSize = transactionCache.size();

        // Remove expired entries
        transactionCache.entrySet().removeIf(entry ->
                (now - entry.getValue().timestamp) > CACHE_EXPIRY_MS);

        int removedCount = beforeSize - transactionCache.size();
        if (removedCount > 0) {
            // Only log if actual cleaning occurred
            plugin.debug("Cleaned up " + removedCount + " expired transaction cache entries");
        }
    }

    private boolean setupVaultEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Custom sell prices system disabled.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            isVaultAvailable = false;
            plugin.getLogger().warning("No economy provider found for Vault! Custom sell prices system disabled.");
            return false;
        }

        economy = rsp.getProvider();
        isVaultAvailable = true;

        plugin.getLogger().info("Successfully connected to Vault & Economy provider: " + economy.getName());
        return isVaultAvailable;
    }

    public boolean sellAllItems(Player player, SpawnerData spawner) {
        if (!isEnabled()) {
            return false;
        }

        // Get virtual inventory from spawner
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<VirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();

        if (items.isEmpty()) {
            messageService.sendMessage(player, "shop.no_items");
            return false;
        }

        // Use a more efficient approach for calculating value and preparing items
        String playerName = player.getName();
        double totalValue = 0.0;
        Map<Material, Long> materialAmounts = new HashMap<>();
        List<ItemStack> itemsToRemove = new ArrayList<>();
        long totalItemCount = 0; // Add this to track total item count

        // First pass: consolidate by material and calculate values
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            VirtualInventory.ItemSignature sig = entry.getKey();
            ItemStack template = sig.getTemplate();
            Material material = template.getType();
            long amount = entry.getValue();

            // Get cached price for this material
            double price = getMaterialPrice(material);

            // Skip if price is zero
            if (price <= 0) {
                continue;
            }

            // Add to the consolidated map for bulk processing
            materialAmounts.merge(material, amount, Long::sum);

            // Calculate and accumulate value
            totalValue += price * amount;

            // Track total items being sold
            totalItemCount += amount;

            // Create item stacks for removal more efficiently
            createItemStacksForRemoval(template, amount, itemsToRemove);
        }

        // If nothing to sell
        if (totalValue <= 0 || itemsToRemove.isEmpty()) {
            messageService.sendMessage(player, "shop.no_items");
            return false;
        }

        // Use a single transaction for adding money to player's account
        if (economy.depositPlayer(player, totalValue).transactionSuccess()) {
            // Use bulk removal of items for better performance
            virtualInv.removeItems(itemsToRemove);

            // Update spawner state
            spawner.updateHologramData();
            if (spawner.getIsAtCapacity()) {
                spawner.setIsAtCapacity(false);
            }

            // Update transaction cache with timestamp
            transactionCache.put(playerName, new CacheEntry(totalValue));

            // Update spawner GUI viewers in a separate thread to avoid blocking
            plugin.getSpawnerGuiViewManager().updateSpawnerMenuViewers(spawner);

            // Send success message with total item count
            sendSellSuccessMessage(player, totalValue, totalItemCount);

            return true;
        } else {
            plugin.getLogger().log(Level.WARNING, "Failed to deposit money to player: " + player.getName());
            return false;
        }
    }

    // Helper method to create ItemStacks for removal more efficiently
    private void createItemStacksForRemoval(ItemStack template, long amount, List<ItemStack> itemsList) {
        int maxStackSize = template.getMaxStackSize();

        // Use integer division and modulo for faster processing
        int fullStacks = (int)(amount / maxStackSize);
        int remainder = (int)(amount % maxStackSize);

        // Add full stacks
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = template.clone();
            stack.setAmount(maxStackSize);
            itemsList.add(stack);
        }

        // Add remainder stack if needed
        if (remainder > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(remainder);
            itemsList.add(stack);
        }
    }

    // Use cached material price lookup
    private double getMaterialPrice(Material material) {
        return materialPriceCache.computeIfAbsent(material, priceManager::getPrice);
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("custom_sell_prices.enabled", false) && isVaultAvailable;
    }

    public double getLatestTransactionAmount(Player player) {
        CacheEntry entry = transactionCache.get(player.getName());
        return entry != null ? entry.amount : 0.0;
    }

    private void sendSellSuccessMessage(Player player, double amount, long itemCount) {
        messageService.sendMessage(player, "shop.sell_all",
                Map.of("price", languageManager.formatNumber(amount), "amount", languageManager.formatNumber(itemCount)));
    }

    public void reload() {
        // Cancel existing cleanup task
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        isVaultAvailable = false;
        String currencyType = plugin.getConfig().getString("custom_sell_prices.currency", "VAULT");
        if (plugin.getConfig().getBoolean("custom_sell_prices.enabled", false) &&
                currencyType.equalsIgnoreCase("VAULT")) {
            setupVaultEconomy();
            startCleanupTask();
        }

        // Clear caches
        transactionCache.clear();
        materialPriceCache.clear();
    }

    // Method to properly shut down the manager
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }

        transactionCache.clear();
        materialPriceCache.clear();
    }
}