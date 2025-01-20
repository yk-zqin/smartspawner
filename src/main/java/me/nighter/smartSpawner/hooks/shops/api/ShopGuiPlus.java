package me.nighter.smartSpawner.hooks.shops.api;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.shops.IShopIntegration;
import me.nighter.smartSpawner.managers.ConfigManager;
import me.nighter.smartSpawner.managers.LanguageManager;
import me.nighter.smartSpawner.utils.SpawnerData;
import me.nighter.smartSpawner.utils.VirtualInventory;
import net.brcdev.shopgui.ShopGuiPlusApi;
import net.brcdev.shopgui.economy.EconomyType;
import net.brcdev.shopgui.provider.economy.EconomyProvider;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class ShopGuiPlus  implements IShopIntegration {
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

        double totalPrice = 0;
        int totalAmount = 0;
        boolean foundSellableItem = false;
        Map<ItemStack, Double> itemPrices = new HashMap<>();
        Map<ItemStack, Integer> soldItems = new HashMap<>();

        // Tính toán các item có thể bán và gộp số lượng
        synchronized (virtualInv) {
            for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                ItemStack item = entry.getValue();
                if (item != null && item.getType() != Material.AIR) {
                    double sellPrice = ShopGuiPlusApi.getItemStackPriceSell(player, item);

                    if (sellPrice <= 0) continue;

                    foundSellableItem = true;
                    ItemStack itemKey = item.clone();
                    itemKey.setAmount(1);

                    // Gộp số lượng item giống nhau
                    soldItems.merge(itemKey, item.getAmount(), Integer::sum);
                    itemPrices.put(itemKey, sellPrice / item.getAmount());
                }
            }
        }

        if (!foundSellableItem) {
            plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
            return false;
        }

        // Log thông tin đã gộp
        for (Map.Entry<ItemStack, Integer> entry : soldItems.entrySet()) {
            ItemStack item = entry.getKey();
            int amount = entry.getValue();
            double pricePerUnit = itemPrices.get(item);
            double totalItemPrice = pricePerUnit * amount;

            //plugin.getLogger().info(String.format("[SmartSpawner] Item: %s, Amount: %d, Price per unit: %.2f, Total: %.2f",
            //        item.getType().name(), amount, pricePerUnit, totalItemPrice));

            totalAmount += amount;
            totalPrice += totalItemPrice;
        }

        if (totalAmount > 0) {
            // Áp dụng thuế nếu có
            double taxPercentage = plugin.getConfigManager().getTaxPercentage();
            double finalPrice = totalPrice;

            if (taxPercentage > 0) {
                double taxAmount = totalPrice * (taxPercentage / 100.0);
                finalPrice = totalPrice - taxAmount;
            }

            // Xóa items đã bán khỏi inventory
            synchronized (virtualInv) {
                for (Map.Entry<ItemStack, Integer> soldEntry : soldItems.entrySet()) {
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

            // Thêm tiền cho người chơi
            //EconomyType economyType = ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().get("economyTypes");
            plugin.getLogger().info("EconomyType: " + ShopGuiPlusApi.getPlugin().getConfigMain().getConfig().get("economyTypes"));
            ShopGuiPlusApi.getPlugin().getEconomyManager().getEconomyProvider(EconomyType.VAULT).deposit(player, finalPrice);

            // Gửi thông báo
            String formattedPrice = formatPrice(finalPrice);
            if (taxPercentage > 0) {
                plugin.getLanguageManager().sendMessage(player, "messages.sell-all-tax",
                        "%amount%", String.valueOf(totalAmount),
                        "%price%", formattedPrice,
                        "%tax%", String.format("%.2f", taxPercentage));
            } else {
                plugin.getLanguageManager().sendMessage(player, "messages.sell-all",
                        "%amount%", String.valueOf(totalAmount),
                        "%price%", formattedPrice);
            }
            return true;
        }

        plugin.getLanguageManager().sendMessage(player, "messages.no-sellable-items");
        return false;
    }

    private String formatPrice(double price) {
        if (configManager.getFormatedPrice() && price >= 1000) {
            return languageManager.formatNumber((long) price);
        }

        DecimalFormat df = new DecimalFormat("#,##0.00");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        df.setDecimalFormatSymbols(symbols);
        return df.format(price);
    }

    @Override
    public boolean isEnabled() {
        return ShopGuiPlusApi.getPlugin() != null &&
                ShopGuiPlusApi.getPlugin().getShopManager().areShopsLoaded();
    }
}