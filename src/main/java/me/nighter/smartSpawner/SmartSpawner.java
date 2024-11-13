package me.nighter.smartSpawner;

import me.nighter.smartSpawner.commands.ReloadCommand;
import me.nighter.smartSpawner.listeners.*;
import me.nighter.smartSpawner.managers.*;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;

public class SmartSpawner extends JavaPlugin {
    private static SmartSpawner instance;
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private SpawnerManager spawnerManager;
    private SpawnerRangeChecker rangeChecker;
    private SpawnerLootGenerator lootGenerator;
    private SpawnerLootManager lootManager;
    private SpawnerGuiListener spawnerGuiListener;
    private UpdateChecker updateChecker;
    private SpawnerStackHandler spawnerStackHandler;
    private SpawnerBreakHandler spawnerBreakHandler;
    private GUIClickHandler guiClickHandler;
    private SpawnerExplosionListener spawnerExplosionListener;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new ConfigManager(this);
        languageManager = new LanguageManager(this);
        lootGenerator = new SpawnerLootGenerator(this);
        lootManager = new SpawnerLootManager(this);
        spawnerManager = new SpawnerManager(this);
        rangeChecker = new SpawnerRangeChecker(this);
        spawnerGuiListener = new SpawnerGuiListener(this);
        spawnerBreakHandler = new SpawnerBreakHandler(this);
        spawnerStackHandler = new SpawnerStackHandler(this);
        guiClickHandler = new GUIClickHandler(this);
        updateChecker = new UpdateChecker(this, 120743);
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api != null) {
                getLogger().info("Floodgate detected, enabling Floodgate support");
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            getLogger().info("Floodgate not detected, continuing without it");
        }

        // Load configs
        spawnerManager.loadSpawnerData();
        updateChecker.initialize();

        // Register the reload command
        ReloadCommand reloadCommand = new ReloadCommand(this);
        getCommand("smartspawner").setExecutor(reloadCommand);
        getCommand("smartspawner").setTabCompleter(reloadCommand);

        registerListeners();
        getLogger().info("SmartSpawner has been enabled!");
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new SpawnerListener(this), this);
        pm.registerEvents(new SpawnerGuiListener(this), this);
        pm.registerEvents(new SpawnerRangeChecker(this), this);
        pm.registerEvents(new SpawnerBreakHandler(this), this);
        pm.registerEvents(new GUIClickHandler(this), this);
        pm.registerEvents(new SpawnerExplosionListener(this), this);
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

    public SpawnerManager getSpawnerManage() {
        return spawnerManager;
    }

    public SpawnerLootGenerator getLootGenerator() {
        return lootGenerator;
    }

    public SpawnerGuiListener getSpawnerGuiListener() {
        return spawnerGuiListener;
    }

    public SpawnerLootManager getLootManager() {
        return lootManager;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
    }

    public SpawnerRangeChecker getRangeChecker() {
        return rangeChecker;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public SpawnerStackHandler getSpawnerStackHandler() {
        return spawnerStackHandler;
    }

    public void checkUpdate() {
        if (updateChecker.hasUpdate()) {
            getLogger().info("New version available: " + updateChecker.getLatestVersion());
        }
    }
}