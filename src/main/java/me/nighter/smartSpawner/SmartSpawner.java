package me.nighter.smartSpawner;

import me.nighter.smartSpawner.managers.*;
import me.nighter.smartSpawner.listeners.*;
import me.nighter.smartSpawner.utils.UpdateChecker;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.nighter.smartSpawner.hooks.economy.EconomyShopGUI;
import me.nighter.smartSpawner.hooks.protections.LandsIntegrationAPI;
import me.nighter.smartSpawner.commands.SmartSpawnerCommand;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.geysermc.floodgate.api.FloodgateApi;

import com.sk89q.worldguard.WorldGuard;

public class SmartSpawner extends JavaPlugin {
    
    public static boolean hasLands = false;
    public static boolean hasWorldGuard = false;
    public static boolean hasGriefPrevention = false;

    private static SmartSpawner instance;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private EventHandlers eventHandlers;
    private SpawnerManager spawnerManager;
    private SpawnerRangeChecker rangeChecker;
    private SpawnerLootGenerator lootGenerator;
    private SpawnerLootManager lootManager;
    private SpawnerListener spawnerListener;
    private SpawnerGuiListener spawnerGuiListener;
    private UpdateChecker updateChecker;
    private SpawnerStackHandler spawnerStackHandler;
    private SpawnerBreakHandler spawnerBreakHandler;
    private GUIClickHandler guiClickHandler;
    private SpawnerExplosionListener spawnerExplosionListener;
    private EconomyShopGUI shopIntegration;
    private boolean isEconomyShopGUI = false;
    private HopperHandler hopperHandler;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        languageManager = new LanguageManager(this);
        eventHandlers = new EventHandlers(this);
        lootGenerator = new SpawnerLootGenerator(this);
        lootManager = new SpawnerLootManager(this);
        spawnerManager = new SpawnerManager(this);
        rangeChecker = new SpawnerRangeChecker(this);
        spawnerListener = new SpawnerListener(this);
        spawnerGuiListener = new SpawnerGuiListener(this);
        spawnerBreakHandler = new SpawnerBreakHandler(this);
        spawnerStackHandler = new SpawnerStackHandler(this);
        guiClickHandler = new GUIClickHandler(this);
        if (configManager.isHopperEnabled())
        {
            hopperHandler = new HopperHandler(this);
            hopperHandler.checkAllHoppers();
        } else {
            getLogger().info("Hopper integration is disabled by configuration");
            hopperHandler = null;
        }
        updateChecker = new UpdateChecker(this, "9tQwxSFr");
        checkDependencies();

        // Load configs
        spawnerManager.loadSpawnerData();
        updateChecker.initialize();

        // Register the reload command
        SmartSpawnerCommand smartSpawnerCommand = new SmartSpawnerCommand(this);
        getCommand("smartspawner").setExecutor(smartSpawnerCommand);
        getCommand("smartspawner").setTabCompleter(smartSpawnerCommand);

        registerListeners();
        getLogger().info("SmartSpawner has been enabled!");
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new EventHandlers(this), this);
        pm.registerEvents(new SpawnerListener(this), this);
        pm.registerEvents(new SpawnerGuiListener(this), this);
        pm.registerEvents(new SpawnerRangeChecker(this), this);
        pm.registerEvents(new SpawnerBreakHandler(this), this);
        pm.registerEvents(new GUIClickHandler(this), this);
        pm.registerEvents(new SpawnerExplosionListener(this), this);
    }

    private void checkDependencies() {
        // Check Floodgate
        try {
            FloodgateApi floodgateApi = FloodgateApi.getInstance();
            if (floodgateApi != null) {
                getLogger().info("Floodgate integration enabled successfully!");
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            getLogger().info("Floodgate not detected, continuing without it");
        }

        try {
            WorldGuard.getInstance();
            if (WorldGuard.getInstance() != null) {
                getLogger().info("WorldGuard integration enabled successfully!");
                hasWorldGuard = true;
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            getLogger().info("WorldGuard not detected, continuing without it");
        }

        try {
            GriefPrevention griefPrevention = (GriefPrevention) Bukkit.getPluginManager().getPlugin("GriefPrevention");
            if (griefPrevention != null) {
                getLogger().info("GriefPrevention integration enabled successfully!");
                hasGriefPrevention = true;
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            getLogger().info("GriefPrevention not detected, continuing without it");
        }

        try {
            Plugin landsPlugin = Bukkit.getPluginManager().getPlugin("Lands");
            if (landsPlugin != null) {
                new LandsIntegrationAPI(this);
                getLogger().info("Lands integration enabled successfully!");
                hasLands = true;
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            getLogger().info("Lands not detected, continuing without it");
        }

        ConfigManager.ShopType shopType = configManager.getShopType();

        switch (shopType) {
            case ECONOMY_SHOP_GUI_PREMIUM:
                Plugin premiumShop = Bukkit.getPluginManager().getPlugin("EconomyShopGUI-Premium");
                if (premiumShop != null) {
                    this.shopIntegration = new EconomyShopGUI(this);
                    isEconomyShopGUI = true;
                    getLogger().info("EconomyShopGUI Premium integration enabled!");
                } else {
                    getLogger().warning("EconomyShopGUI-Premium not found but configured. Sell features will be disabled.");
                }
                break;

            case ECONOMY_SHOP_GUI:
                Plugin basicShop = Bukkit.getPluginManager().getPlugin("EconomyShopGUI");
                if (basicShop != null) {
                    this.shopIntegration = new EconomyShopGUI(this);
                    isEconomyShopGUI = true;
                    getLogger().info("EconomyShopGUI integration enabled!");
                } else {
                    getLogger().warning("EconomyShopGUI not found but configured. Sell features will be disabled.");
                }
                break;

            case DISABLED:
            default:
                getLogger().info("Shop integration is disabled by configuration");
                break;
        }
    }

    @Override
    public void onDisable() {
        if (spawnerManager != null) {
            spawnerManager.saveSpawnerData();
        }
        if (rangeChecker != null) {
            rangeChecker.cleanup();
        }
        // Save configs before shutdown
        if (configManager != null) {
            configManager.saveConfigs();
        }

        if (spawnerGuiListener != null) {
            spawnerGuiListener.onDisable();
        }

        if (updateChecker != null) {
            updateChecker.shutdown();
        }
        if (hopperHandler != null) {
            hopperHandler.cleanup();
        }

        if (eventHandlers != null) {
            eventHandlers.cleanup();
        }

        SpawnerHeadManager.clearCache();
        getLogger().info("SmartSpawner has been disabled!");
    }

    public static SmartSpawner getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public SpawnerLootGenerator getLootGenerator() {
        return lootGenerator;
    }

    public SpawnerLootManager getLootManager() {
        return lootManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public SpawnerListener getSpawnerListener() {
        return spawnerListener;
    }

    public SpawnerRangeChecker getRangeChecker() {
        return rangeChecker;
    }

    public SpawnerStackHandler getSpawnerStackHandler() {
        return spawnerStackHandler;
    }

    public HopperHandler getHopperHandler() {
        return hopperHandler;
    }

    public SpawnerLootManager getSpawnerLootManager() {
        return lootManager;
    }

    public EconomyShopGUI getShopIntegration() {
        return shopIntegration;
    }

    public boolean isEconomyShopGUI() {
        return isEconomyShopGUI;
    }

    public BukkitTask runTaskAsync(Runnable runnable) {
        return this.getServer().getScheduler().runTaskAsynchronously(this, runnable);
    }
}