package me.nighter.smartSpawner.hooks.shops.api.zshop;

import fr.maxlego08.zshop.api.ShopManager;
import fr.maxlego08.zshop.api.buttons.ItemButton;
import fr.maxlego08.zshop.api.economy.EconomyManager;
import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.shops.IShopIntegration;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.utils.OptimizedVirtualInventory;
import me.nighter.smartSpawner.utils.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ZShop implements IShopIntegration {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private ShopManager shopManager;
    private EconomyManager economyManager;

    public ZShop(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.configManager = plugin.getConfigManager();
    }

    private Optional<ItemButton> getItemButton(Player player, ItemStack itemStack) {
        try {
            ShopManager shopManager = this.getShopManager();
            return shopManager.getItemButton(player, itemStack);
        } catch (Exception exception) {
            exception.printStackTrace();
            return Optional.empty();
        }
    }

    public ShopManager getShopManager() {
        if (this.shopManager != null) return this.shopManager;
        return this.shopManager = this.plugin.getServer().getServicesManager().getRegistration(ShopManager.class).getProvider();
    }

    @Override
    public synchronized boolean sellAllItems(Player player, SpawnerData spawner) {
        if (!isEnabled()) {
            return false;
        }

        OptimizedVirtualInventory virtualInv = spawner.getVirtualInventory();
        Map<OptimizedVirtualInventory.ItemSignature, Long> items = virtualInv.getConsolidatedItems();

        if (items.isEmpty()) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-items");
            return false;
        }

        double totalPrice = 0;
        int totalAmount = 0;
        boolean foundSellableItem = false;
        List<ItemStack> itemsToRemove = new ArrayList<>();

        // Process each item type
        for (Map.Entry<OptimizedVirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            long amount = entry.getValue();

            if (amount > 0) {
                // Try to find a sell button for this item
                Optional<ItemButton> sellButton = getItemButton(player, template);
                if (sellButton.isEmpty()) continue;

                double sellPrice = sellButton.get().getSellPrice(player, (int) amount);
                if (sellPrice <= 0) continue;

                foundSellableItem = true;

                // Create item for removal
                ItemStack itemToRemove = template.clone();
                itemToRemove.setAmount((int) Math.min(amount, Integer.MAX_VALUE));
                itemsToRemove.add(itemToRemove);

                // Process transaction for this item
                sellButton.get().getEconomy().depositMoney(player, sellPrice);

                totalPrice += sellPrice;
                totalAmount += amount;
            }
        }

        if (!foundSellableItem) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
            return false;
        }

        double taxPercentage = plugin.getConfigManager().getTaxPercentage();
        double finalPrice = taxPercentage > 0
                ? totalPrice * (1 - taxPercentage / 100.0)
                : totalPrice;

        // Remove all sold items
        virtualInv.removeItems(itemsToRemove);

        // Send success message
        String formattedPrice = formatPrice(finalPrice, configManager.isFormatedPrice());
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

    @Override
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public boolean isEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("zShop");
    }
}