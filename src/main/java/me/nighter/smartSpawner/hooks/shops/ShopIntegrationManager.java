package me.nighter.smartSpawner.hooks.shops;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.shops.api.economyshopgui.EconomyShopGUI;
import me.nighter.smartSpawner.hooks.shops.api.shopguiplus.ShopGuiPlus;
import me.nighter.smartSpawner.hooks.shops.api.zshop.ZShop;
import me.nighter.smartSpawner.managers.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class ShopIntegrationManager {
    private final SmartSpawner plugin;
    private IShopIntegration shopIntegration;
    private boolean hasShopIntegration = false;
    private boolean isShopGUIPlusEnabled = false;

    // Map to store shop plugin details: plugin name -> integration creator
    private final Map<String, Function<SmartSpawner, IShopIntegration>> shopIntegrations = new LinkedHashMap<>();

    public ShopIntegrationManager(SmartSpawner plugin) {
        this.plugin = plugin;
        registerShopIntegrations();
    }

    private void registerShopIntegrations() {
        // Register available shop integrations with their creation logic
        shopIntegrations.put("EconomyShopGUI-Premium", EconomyShopGUI::new);
        shopIntegrations.put("EconomyShopGUI", EconomyShopGUI::new);
        shopIntegrations.put("ShopGUIPlus", ShopGuiPlus::new);
        shopIntegrations.put("zShop", ZShop::new);
    }

    public void initialize() {
        ConfigManager.ShopType configuredShopType = plugin.getConfigManager().getShopType();

        if (configuredShopType == ConfigManager.ShopType.DISABLED) {
            plugin.getLogger().info("Shop integration is disabled by configuration");
            return;
        }

        if (configuredShopType == ConfigManager.ShopType.AUTO) {
            autoDetectAndSetupShop();
        } else {
            setupSpecificShop(configuredShopType);
        }
    }

    private void autoDetectAndSetupShop() {
        plugin.getLogger().info("Auto-detecting available shop plugins...");

        for (Map.Entry<String, Function<SmartSpawner, IShopIntegration>> entry : shopIntegrations.entrySet()) {
            String pluginName = entry.getKey();
            Plugin shopPlugin = Bukkit.getPluginManager().getPlugin(pluginName);

            if (shopPlugin != null && shopPlugin.isEnabled()) {
                try {
                    setupShopIntegration(pluginName, entry.getValue());
                    //plugin.getLogger().info("Successfully auto-detected and enabled " + pluginName + " integration!");
                    return;
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to setup " + pluginName + " integration: " + e.getMessage());
                }
            }
        }

        plugin.getLogger().warning("No compatible shop plugins were found during auto-detection.");
    }

    private void setupSpecificShop(ConfigManager.ShopType shopType) {
        String pluginName = shopType.toString();
        Function<SmartSpawner, IShopIntegration> integrationCreator = shopIntegrations.get(pluginName);

        if (integrationCreator != null) {
            plugin.getLogger().info("Checking for " + pluginName + "...");
            Plugin shopPlugin = Bukkit.getPluginManager().getPlugin(pluginName);

            if (shopPlugin != null && shopPlugin.isEnabled()) {
                setupShopIntegration(pluginName, integrationCreator);
            } else {
                plugin.getLogger().info(pluginName + " not found - integration disabled");
            }
        }
    }

    private void setupShopIntegration(String pluginName, Function<SmartSpawner, IShopIntegration> integrationCreator) {
        try {
            shopIntegration = integrationCreator.apply(plugin);
            hasShopIntegration = true;
            isShopGUIPlusEnabled = pluginName.equals("ShopGUIPlus");
            plugin.getLogger().info(pluginName + " integration enabled successfully!");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to setup " + pluginName + " integration: " + e.getMessage());
            hasShopIntegration = false;
            isShopGUIPlusEnabled = false;
        }
    }

    // Getters remain the same
    public IShopIntegration getShopIntegration() {
        if (shopIntegration != null && shopIntegration.isEnabled()) {
            return shopIntegration;
        }
        return null;
    }

    public boolean hasShopIntegration() {
        return hasShopIntegration;
    }

    public boolean isShopGUIPlusEnabled() {
        return isShopGUIPlusEnabled;
    }
}