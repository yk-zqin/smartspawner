package github.nighter.smartspawner.hooks.economy.shops.api;

import org.bukkit.Material;

public interface ShopProvider {

    String getPluginName();

    boolean isAvailable();

    double getSellPrice(Material material);
}