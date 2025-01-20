package me.nighter.smartSpawner.hooks.shops;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.shops.api.EconomyShopGUI;
import me.nighter.smartSpawner.hooks.shops.api.ShopGuiPlus;
import me.nighter.smartSpawner.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class ShopIntegrationManager {
    private final SmartSpawner plugin;
    private IShopIntegration shopIntegration;
    private EconomyShopGUI economyShopIntegration;
    private boolean hasShopIntegration = false;
    private boolean hasShopGuiPlus = false;

    public ShopIntegrationManager(SmartSpawner plugin) {
        this.plugin = plugin;
        ConfigManager configManager = plugin.getConfigManager();
    }

    public void initialize() {
        setupShopIntegration();
    }

    private void setupShopIntegration() {
        // Ensure only one shop integration is active
        if (hasShopIntegration) {
            plugin.getLogger().warning("Another shop integration is already enabled. Skipping additional shop setup.");
            return;
        }

        ConfigManager.ShopType shopType = plugin.getConfigManager().getShopType();
        switch (shopType) {
            case ECONOMY_SHOP_GUI_PREMIUM:
                plugin.getLogger().info("Checking for EconomyShopGUI-Premium...");
                setupEconomyShopGUI("EconomyShopGUI-Premium");
                break;
            case ECONOMY_SHOP_GUI:
                plugin.getLogger().info("Checking for EconomyShopGUI...");
                setupEconomyShopGUI("EconomyShopGUI");
                break;
            case SHOP_GUI_PLUS:
                plugin.getLogger().info("Checking for ShopGUIPlus...");
                setupShopGuiPlus();
                break;
            case DISABLED:
                plugin.getLogger().info("Shop integration is disabled by configuration");
                break;
        }
    }

    private void setupShopGuiPlus() {
        if (Bukkit.getPluginManager().getPlugin("ShopGUIPlus") != null) {
            shopIntegration = new ShopGuiPlus(plugin);
            hasShopIntegration = true;
            hasShopGuiPlus = true;
            plugin.getLogger().info("ShopGUIPlus integration enabled!");
            return;
        }
        plugin.getLogger().info("ShopGUIPlus is not found. Skipping ShopGUIPlus integration.");
    }

    private void setupEconomyShopGUI(String pluginName) {
        if (!hasShopIntegration) {
            hasShopIntegration = checkPlugin(pluginName, () -> {
                Plugin shop = Bukkit.getPluginManager().getPlugin(pluginName);
                if (shop != null) {
                    economyShopIntegration = new EconomyShopGUI(plugin);
                    return true;
                }
                return false;
            });
        }
    }

    private boolean checkPlugin(String pluginName, PluginCheck checker) {
        try {
            if (checker.check()) {
                plugin.getLogger().info(pluginName + " integration enabled successfully!");
                return true;
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            plugin.getLogger().info(pluginName + " not detected, continuing without it");
        }
        return false;
    }

    public IShopIntegration getShopIntegration() {
        if (shopIntegration != null && shopIntegration.isEnabled()) {
            return shopIntegration;
        }
        return null;
    }

    // Getters
    public boolean hasShopIntegration() {
        return hasShopIntegration;
    }

    public EconomyShopGUI getEconomyShopIntegration() {
        return economyShopIntegration;
    }

    public boolean hasShopGuiPlus() {
        return hasShopGuiPlus;
    }

    @FunctionalInterface
    private interface PluginCheck {
        boolean check();
    }
}