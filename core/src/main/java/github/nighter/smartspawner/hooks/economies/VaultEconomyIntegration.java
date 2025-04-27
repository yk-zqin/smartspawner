package github.nighter.smartspawner.hooks.economies;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.economy.CustomEconomyManager;
import github.nighter.smartspawner.hooks.shops.IShopIntegration;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.entity.Player;

public class VaultEconomyIntegration implements IShopIntegration {
    private final SmartSpawner plugin;
    private final CustomEconomyManager economyManager;
    private final LanguageManager languageManager;
    private final boolean isEnabled;

    public VaultEconomyIntegration(SmartSpawner plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getCustomEconomyManager();
        this.languageManager = plugin.getLanguageManager();
        this.isEnabled = economyManager.isEnabled();
    }

    @Override
    public boolean sellAllItems(Player player, SpawnerData spawner) {
        if (!isEnabled()) {
            return false;
        }

        return economyManager.sellAllItems(player, spawner);
    }

    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    @Override
    public LanguageManager getLanguageManager() {
        return languageManager;
    }
}