package me.nighter.smartSpawner.hooks.shops.api;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.shops.IShopIntegration;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.utils.VirtualInventory;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyManager;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class ShopGuiPlus implements IShopIntegration {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;

    public ShopGuiPlus(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public synchronized boolean sellAllItems(Player player, SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<Integer, ItemStack> items = virtualInv.getAllItems();

        if (items.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-items");
            return false;
        }

        // Store items grouped by economy type to handle different economies correctly
        Map<EconomyType, Map<ItemStack, Integer>> itemsByEconomy = new HashMap<>();
        Map<EconomyType, Double> totalPriceByEconomy = new HashMap<>();
        int totalAmount = 0;
        boolean foundSellableItem = false;

        // First pass: Group items by economy type and calculate prices
        synchronized (virtualInv) {
            for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                ItemStack item = entry.getValue();
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }

                double sellPrice = ShopGuiPlusApi.getItemStackPriceSell(player, item);
                if (sellPrice <= 0) {
                    continue;
                }

                // Get economy type for this specific item
                EconomyType itemEconomyType = getEconomyType(item);
                foundSellableItem = true;

                // Create normalized item key (amount = 1)
                ItemStack itemKey = item.clone();
                itemKey.setAmount(1);

                // Group items by economy type
                Map<ItemStack, Integer> itemsForEconomy = itemsByEconomy.computeIfAbsent(
                        itemEconomyType,
                        k -> new HashMap<>()
                );
                itemsForEconomy.merge(itemKey, item.getAmount(), Integer::sum);

                // Track total price for each economy type
                totalPriceByEconomy.merge(itemEconomyType, sellPrice, Double::sum);
                totalAmount += item.getAmount();
            }
        }

        if (!foundSellableItem || itemsByEconomy.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
            return false;
        }

        // Get tax percentage if configured
        double taxPercentage = plugin.getConfigManager().getTaxPercentage();

        // Process transactions for each economy type
        boolean allTransactionsSuccessful = true;
        for (Map.Entry<EconomyType, Double> entry : totalPriceByEconomy.entrySet()) {
            EconomyType economyType = entry.getKey();
            double totalPrice = entry.getValue();
            double finalPrice = taxPercentage > 0
                    ? totalPrice * (1 - taxPercentage / 100.0)
                    : totalPrice;

            try {
                EconomyProvider economyProvider = ShopGuiPlusApi.getPlugin().getEconomyManager()
                        .getEconomyProvider(economyType);

                if (economyProvider == null) {
                    plugin.getLogger().severe("No economy provider found for type: " + economyType);
                    allTransactionsSuccessful = false;
                    continue;
                }

                economyProvider.deposit(player, finalPrice);
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing transaction for economy " +
                        economyType + ": " + e.getMessage());
                allTransactionsSuccessful = false;
            }
        }

        if (!allTransactionsSuccessful) {
            plugin.getLanguageManager().sendMessage(player, "messages.transaction-error");
            return false;
        }

        // Remove sold items from inventory
        synchronized (virtualInv) {
            for (Map<ItemStack, Integer> itemsForEconomy : itemsByEconomy.values()) {
                for (Map.Entry<ItemStack, Integer> soldEntry : itemsForEconomy.entrySet()) {
                    ItemStack soldItem = soldEntry.getKey();
                    int remainingToRemove = soldEntry.getValue();

                    for (Map.Entry<Integer, ItemStack> invEntry : new HashMap<>(items).entrySet()) {
                        if (remainingToRemove <= 0) break;

                        int slot = invEntry.getKey();
                        ItemStack item = invEntry.getValue();

                        if (item != null && item.getType() == soldItem.getType() &&
                                item.isSimilar(soldItem)) {
                            int amountToRemove = Math.min(remainingToRemove, item.getAmount());
                            remainingToRemove -= amountToRemove;

                            if (amountToRemove >= item.getAmount()) {
                                virtualInv.setItem(slot, null);
                            } else {
                                ItemStack newItem = item.clone();
                                newItem.setAmount(item.getAmount() - amountToRemove);
                                virtualInv.setItem(slot, newItem);
                            }
                        }
                    }
                }
            }
        }

        // Calculate total final price across all economies for message
        double totalFinalPrice = totalPriceByEconomy.values().stream()
                .mapToDouble(price -> taxPercentage > 0
                        ? price * (1 - taxPercentage / 100.0)
                        : price)
                .sum();

        // Send notification
        String formattedPrice = formatPrice(totalFinalPrice, configManager.isFormatedPrice());
        if (taxPercentage > 0) {
            plugin.getLanguageManager().sendMessage(player, "messages.sell-all-tax",
                    "%amount%", String.valueOf(languageManager.formatNumber(totalAmount)),
                    "%price%", formattedPrice,
                    "%tax%", String.format("%.2f", taxPercentage)
            );
        } else {
            plugin.getLanguageManager().sendMessage(player, "messages.sell-all",
                    "%amount%", String.valueOf(languageManager.formatNumber(totalAmount)),
                    "%price%", formattedPrice);
        }

        return true;
    }

    private EconomyType getEconomyType(ItemStack material) {
        EconomyType economyType = ShopGuiPlusApi.getItemStackShop(material).getEconomyType();
        if(economyType != null) {
            return economyType;
        }

        EconomyManager economyManager = ShopGuiPlusApi.getPlugin().getEconomyManager();
        EconomyProvider defaultEconomyProvider = economyManager.getDefaultEconomyProvider();
        if(defaultEconomyProvider != null) {
            String defaultEconomyTypeName = defaultEconomyProvider.getName().toUpperCase(Locale.US);
            try {
                return EconomyType.valueOf(defaultEconomyTypeName);
            } catch(IllegalArgumentException ex) {
                return EconomyType.CUSTOM;
            }
        }

        return EconomyType.CUSTOM;
    }

    @Override
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public boolean isEnabled() {
        return ShopGuiPlusApi.getPlugin() != null &&
                ShopGuiPlusApi.getPlugin().getShopManager().areShopsLoaded();
    }
}