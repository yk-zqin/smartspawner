package github.nighter.smartspawner.economy;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@RequiredArgsConstructor
public class ItemPriceManager {
    private final SmartSpawner plugin;
    private final Map<String, Double> itemPrices = new ConcurrentHashMap<>();
    private File priceFile;
    private FileConfiguration priceConfig;

    @Getter
    private double defaultPrice;

    /**
     * Initialize price configuration from file
     */
    public void init() {
        // Create directory if it doesn't exist
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Initialize the price file
        priceFile = new File(plugin.getDataFolder(), "item_prices.yml");

        // Create the file if it doesn't exist by copying from resources
        if (!priceFile.exists()) {
            plugin.saveResource("item_prices.yml", false);
            plugin.debug("Created default item_prices.yml from plugin resources");
        }

        // Load configuration
        priceConfig = YamlConfiguration.loadConfiguration(priceFile);

        // Get default price from main config
        this.defaultPrice = plugin.getConfig().getDouble("custom_economy.default_price", 1.0);

        // Load all prices into memory
        loadPrices();
    }

    private void loadPrices() {
        itemPrices.clear();

        // Get all keys from the config file
        for (String key : priceConfig.getKeys(false)) {
            double price = priceConfig.getDouble(key, defaultPrice);
            itemPrices.put(key, price);
        }

        // plugin.getLogger().info("Loaded " + itemPrices.size() + " item prices with default price: " + defaultPrice);
    }

    /**
     * Get the sell price for a specific material
     * @param material The material to get the price for
     * @return The sell price, or default price if not defined
     */
    public double getPrice(Material material) {
        if (material == null) return 0.0;
        return itemPrices.getOrDefault(material.name(), defaultPrice);
    }

    /**
     * Set the sell price for a material
     * @param material The material to set the price for
     * @param price The price to set
     */
    public void setPrice(Material material, double price) {
        if (material == null) return;

        itemPrices.put(material.name(), price);
        priceConfig.set(material.name(), price);

        saveConfig();
    }

    /**
     * Reload prices from the configuration file
     */
    public void reload() {
        priceConfig = YamlConfiguration.loadConfiguration(priceFile);

        // Update default price from main config
        this.defaultPrice = plugin.getConfig().getDouble("custom_economy.default_price", 1.0);

        loadPrices();
    }

    /**
     * Check if a price for the given material exists
     * @param material The material to check
     * @return true if a specific price exists, false otherwise
     */
    public boolean hasPriceFor(Material material) {
        if (material == null) return false;
        return itemPrices.containsKey(material.name());
    }

    /**
     * Remove price for a specific material
     * @param material The material to remove price for
     */
    public void removePrice(Material material) {
        if (material == null) return;

        itemPrices.remove(material.name());
        priceConfig.set(material.name(), null);

        saveConfig();
    }

    /**
     * Get all current item prices
     * @return Map of material names to prices
     */
    public Map<String, Double> getAllPrices() {
        return new ConcurrentHashMap<>(itemPrices);
    }

    /**
     * Save the configuration file
     */
    private void saveConfig() {
        try {
            priceConfig.save(priceFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save item prices configuration", e);
        }
    }
}