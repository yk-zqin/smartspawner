package github.nighter.smartspawner.hooks.economy.shops.providers.excellentshop;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.economy.shops.providers.ShopProvider;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import su.nightexpress.nexshop.ShopAPI;
import su.nightexpress.nexshop.api.shop.type.TradeType;
import su.nightexpress.nexshop.shop.virtual.impl.VirtualProduct;

@RequiredArgsConstructor
public class ExcellentShopProvider implements ShopProvider {
    private final SmartSpawner plugin;

    @Override
    public String getPluginName() {
        return "ExcellentShop";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin excellentShopPlugin = Bukkit.getPluginManager().getPlugin("ExcellentShop");
            if (excellentShopPlugin != null && excellentShopPlugin.isEnabled()) {
                // Check if the ExcellentShop API classes are available
                Class.forName("su.nightexpress.nexshop.ShopAPI");
                Class.forName("su.nightexpress.nexshop.api.shop.type.TradeType");
                Class.forName("su.nightexpress.nexshop.shop.virtual.impl.VirtualProduct");

                // Try to access the ShopAPI to verify it's working
                ShopAPI.getVirtualShop();
                return true;
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            plugin.debug("ExcellentShop API not found: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Error initializing ExcellentShop integration: " + e.getMessage());
        }
        return false;
    }

    @Override
    public double getSellPrice(Material material) {
        try {
            ItemStack item = new ItemStack(material);
            VirtualProduct product = ShopAPI.getVirtualShop().getBestProductFor(item, TradeType.SELL);

            if (product == null || !product.isSellable()) {
                return 0.0;
            }

            // Calculate price per unit (since the original method considers item amount)
            double pricePerUnit = product.getPrice(TradeType.SELL) / product.getUnitAmount();
            return pricePerUnit > 0 ? pricePerUnit : 0.0;

        } catch (Exception e) {
            plugin.debug("Error getting sell price for " + material + " from ExcellentShop: " + e.getMessage());
            return 0.0;
        }
    }
}