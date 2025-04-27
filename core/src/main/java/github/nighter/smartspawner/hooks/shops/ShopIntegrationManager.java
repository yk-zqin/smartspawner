package github.nighter.smartspawner.hooks.shops;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.hooks.shops.api.economyshopgui.EconomyShopGUI;
import github.nighter.smartspawner.hooks.shops.api.shopguiplus.ShopGuiPlus;
import github.nighter.smartspawner.hooks.economies.VaultEconomyIntegration;
import github.nighter.smartspawner.hooks.shops.api.zshop.ZShop;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class ShopIntegrationManager {
    private final SmartSpawner plugin;
    private IShopIntegration shopIntegration;
    private boolean hasShopIntegration = false;
    @Getter
    private boolean isShopGUIPlusEnabled = false;
    @Getter
    private boolean isUsingCustomPrices = false;
    private final Map<String, Function<SmartSpawner, IShopIntegration>> shopIntegrations = new LinkedHashMap<>();

    public ShopIntegrationManager(SmartSpawner plugin) {
        this.plugin = plugin;
        registerShopIntegrations();
    }

    private void registerShopIntegrations() {
        shopIntegrations.put("economyshopgui-premium", EconomyShopGUI::new);
        shopIntegrations.put("economyshopgui", EconomyShopGUI::new);
        shopIntegrations.put("shopguiplus", ShopGuiPlus::new);
        shopIntegrations.put("zshop", ZShop::new);
        shopIntegrations.put("vault", VaultEconomyIntegration::new);
    }

    public void initialize() {
        // Check if custom economy is enabled - if so, skip shop integration
        if (plugin.getConfig().getBoolean("custom_economy.enabled", false)) {
            plugin.getLogger().info("Using custom economy system - shop integration disabled");
            setupVaultIntegration();
            return;
        }

        if (!plugin.getConfig().getBoolean("shop_integration.enabled", true)) {
            plugin.getLogger().info("Shop integration is disabled by configuration");
            return;
        }

        String configuredShopType = plugin.getConfig().getString("shop_integration.type", "auto").toLowerCase();

        if ("auto".equals(configuredShopType)) {
            autoDetectAndSetupShop();
        } else {
            setupSpecificShop(configuredShopType);
        }
    }

    private void setupVaultIntegration() {
        Function<SmartSpawner, IShopIntegration> integrationCreator = shopIntegrations.get("vault");
        if (integrationCreator != null) {
            try {
                shopIntegration = integrationCreator.apply(plugin);
                hasShopIntegration = shopIntegration.isEnabled();
                isUsingCustomPrices = true;
                plugin.getLogger().info("Custom economy integration enabled successfully!");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to setup custom economy integration: " + e.getMessage());
                hasShopIntegration = false;
                isUsingCustomPrices = false;
            }
        }
    }

    private void setupSpecificShop(String shopType) {
        Function<SmartSpawner, IShopIntegration> integrationCreator = shopIntegrations.get(shopType);

        if (integrationCreator != null) {
            String bukkitPluginName = getBukkitPluginName(shopType);

            plugin.getLogger().info("Checking for " + bukkitPluginName + "...");
            Plugin shopPlugin = Bukkit.getPluginManager().getPlugin(bukkitPluginName);

            if (shopPlugin != null && shopPlugin.isEnabled()) {
                setupShopIntegration(bukkitPluginName, integrationCreator);
            } else {
                plugin.getLogger().warning(bukkitPluginName + " not found - integration disabled");
                tryUseCustomPrices();
            }
        } else {
            plugin.getLogger().warning("No integration found for shop type: " + shopType);
            tryUseCustomPrices();
        }
    }

    private String getBukkitPluginName(String shopType) {
        return switch (shopType.toLowerCase()) {
            case "economyshopgui" -> "EconomyShopGUI";
            case "economyshopgui-premium" -> "EconomyShopGUI-Premium";
            case "shopguiplus" -> "ShopGUIPlus";
            case "zshop" -> "zShop";
            case "vault" -> "Vault";
            default -> shopType;
        };
    }

    private void autoDetectAndSetupShop() {
        plugin.getLogger().info("Auto-detecting available shop plugins...");

        String[] pluginNames = {
                "EconomyShopGUI-Premium",
                "EconomyShopGUI",
                "ShopGUIPlus",
                "zShop"
        };

        for (String bukkitPluginName : pluginNames) {
            Plugin shopPlugin = Bukkit.getPluginManager().getPlugin(bukkitPluginName);

            if (shopPlugin != null && shopPlugin.isEnabled()) {
                String lowercasePluginName = bukkitPluginName.toLowerCase();
                // Handle the special case for EconomyShopGUI-Premium
                if (lowercasePluginName.equals("economyshopgui-premium")) {
                    lowercasePluginName = "economyshopgui-premium";
                }

                Function<SmartSpawner, IShopIntegration> integrationCreator = shopIntegrations.get(lowercasePluginName);

                if (integrationCreator != null) {
                    try {
                        setupShopIntegration(bukkitPluginName, integrationCreator);
                        return;
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to setup " + bukkitPluginName + " integration: " + e.getMessage());
                    }
                }
            }
        }

        plugin.getLogger().warning("No compatible shop plugins were found during auto-detection.");
        tryUseCustomPrices();
    }

    private void tryUseCustomPrices() {
        if (plugin.getConfig().getBoolean("shop_integration.use_custom_prices_if_no_shop", true)) {
            plugin.getLogger().info("No shop plugin found, attempting to use custom prices with Vault...");
            setupVaultIntegration();
        }
    }

    private void setupShopIntegration(String pluginName, Function<SmartSpawner, IShopIntegration> integrationCreator) {
        try {
            shopIntegration = integrationCreator.apply(plugin);
            hasShopIntegration = true;
            isShopGUIPlusEnabled = pluginName.equals("ShopGUIPlus");
            isUsingCustomPrices = pluginName.equals("Vault");
            plugin.getLogger().info(pluginName + " integration enabled successfully!");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to setup " + pluginName + " integration: " + e.getMessage());
            hasShopIntegration = false;
            isShopGUIPlusEnabled = false;
            isUsingCustomPrices = false;
        }
    }

    public IShopIntegration getShopIntegration() {
        if (shopIntegration != null && shopIntegration.isEnabled()) {
            return shopIntegration;
        }
        return null;
    }

    public boolean hasShopIntegration() {
        return hasShopIntegration;
    }

    public void reload() {
        hasShopIntegration = false;
        isShopGUIPlusEnabled = false;
        isUsingCustomPrices = false;
        shopIntegration = null;
        initialize();
    }
}