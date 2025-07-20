package github.nighter.smartspawner.hooks.economy.shops.providers.economyshopgui;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import me.gypopo.economyshopgui.api.events.ShopItemsLoadEvent;

import java.util.List;

public class ESGUICompatibilityHandler implements Listener {
    private final SmartSpawner plugin;

    public ESGUICompatibilityHandler(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onESGUIShopItemsLoad(ShopItemsLoadEvent event) {
        plugin.getItemPriceManager().reloadShopIntegration();
        plugin.getEntityLootRegistry().loadConfigurations();
        reloadSpawnerLootConfigs();
    }

    private void reloadSpawnerLootConfigs() {
        List<SpawnerData> allSpawners = plugin.getSpawnerManager().getAllSpawners();
        for (SpawnerData spawner : allSpawners) {
            try {
                spawner.reloadLootConfig();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reload loot config for spawner " +
                        spawner.getSpawnerId() + ": " + e.getMessage());
            }
        }
    }
}