package github.nighter.smartspawner.economy.currency;

import github.nighter.smartspawner.SmartSpawner;
import lombok.Getter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.OfflinePlayer;

import java.util.logging.Level;

public class CurrencyManager {
    private final SmartSpawner plugin;

    @Getter
    private boolean currencyAvailable = false;

    @Getter
    private String activeCurrencyProvider = "None";

    private Economy vaultEconomy;
    @Getter
    private String configuredCurrencyType;

    public CurrencyManager(SmartSpawner plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        loadConfiguration();
        setupCurrency();
    }

    private void loadConfiguration() {
        this.configuredCurrencyType = plugin.getConfig().getString("custom_economy.currency", "VAULT");
    }

    private void setupCurrency() {
        currencyAvailable = false;
        activeCurrencyProvider = "None";

        if (configuredCurrencyType.equalsIgnoreCase("VAULT")) {
            currencyAvailable = setupVaultEconomy();
        } else {
            plugin.getLogger().warning("Unsupported currency type: " + configuredCurrencyType + ". Currently only VAULT is supported.");
            plugin.getLogger().warning("Economy features will be disabled.");
        }
    }

    private boolean setupVaultEconomy() {
        // Check if Vault plugin is available
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Selling items from spawner will be disabled.");
            return false;
        }

        try {
            // Get economy service provider
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                plugin.getLogger().warning("No economy provider found for Vault! Selling items from spawner will be disabled.");
                return false;
            }

            vaultEconomy = rsp.getProvider();
            if (vaultEconomy == null) {
                plugin.getLogger().warning("Failed to get economy provider from Vault! Selling items from spawner will be disabled.");
                return false;
            }

            activeCurrencyProvider = "Vault (" + vaultEconomy.getName() + ")";
            plugin.getLogger().info("Successfully connected to Vault & Economy provider: " + vaultEconomy.getName());
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting up Vault economy integration", e);
            return false;
        }
    }

    public boolean deposit(double amount, OfflinePlayer player) {
        if (!currencyAvailable || vaultEconomy == null) {
            plugin.getLogger().warning("Currency not available for deposit operation.");
            return false;
        }
        return vaultEconomy.depositPlayer(player, amount).transactionSuccess();
    }

    public void reload() {
        // Clean up existing connections
        cleanup();

        // Reload configuration and reinitialize
        loadConfiguration();
        setupCurrency();
    }

    public void cleanup() {
        vaultEconomy = null;
        currencyAvailable = false;
        activeCurrencyProvider = "None";
    }
}