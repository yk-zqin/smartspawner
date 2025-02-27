package me.nighter.smartSpawner;

import me.nighter.smartSpawner.bstats.Metrics;
import me.nighter.smartSpawner.commands.CommandHandler;
import me.nighter.smartSpawner.commands.list.SpawnerListGUI;
import me.nighter.smartSpawner.extras.HopperHandler;
import me.nighter.smartSpawner.hooks.protections.api.Lands;
import me.nighter.smartSpawner.hooks.shops.IShopIntegration;
import me.nighter.smartSpawner.hooks.shops.SaleLogger;
import me.nighter.smartSpawner.hooks.shops.ShopIntegrationManager;
import me.nighter.smartSpawner.hooks.shops.api.shopguiplus.SpawnerHook;
import me.nighter.smartSpawner.hooks.shops.api.shopguiplus.SpawnerProvider;
import me.nighter.smartSpawner.migration.SpawnerDataMigration;
import me.nighter.smartSpawner.spawner.gui.main.SpawnerMenuAction;
import me.nighter.smartSpawner.spawner.gui.main.SpawnerMenuUI;
import me.nighter.smartSpawner.spawner.gui.synchronization.SpawnerGuiManager;
import me.nighter.smartSpawner.spawner.gui.stacker.SpawnerStackerAction;
import me.nighter.smartSpawner.spawner.gui.stacker.SpawnerStackerUI;
import me.nighter.smartSpawner.spawner.gui.storage.SpawnerStorageUI;
import me.nighter.smartSpawner.spawner.gui.storage.SpawnerStorageAction;
import me.nighter.smartSpawner.spawner.gui.stacker.SpawnerStackerActionUpdater;
import me.nighter.smartSpawner.spawner.interactions.SpawnerClickManager;
import me.nighter.smartSpawner.spawner.interactions.destroy.SpawnerBreakListener;
import me.nighter.smartSpawner.spawner.interactions.destroy.SpawnerExplosionListener;
import me.nighter.smartSpawner.spawner.interactions.place.SpawnerPlaceListener;
import me.nighter.smartSpawner.spawner.interactions.stack.SpawnerStackHandler;
import me.nighter.smartSpawner.spawner.interactions.type.SpawnEggHandler;
import me.nighter.smartSpawner.spawner.lootgen.SpawnerRangeChecker;
import me.nighter.smartSpawner.spawner.properties.SpawnerManager;
import me.nighter.smartSpawner.spawner.properties.utils.SpawnerMobHeadTexture;
import me.nighter.smartSpawner.spawner.lootgen.SpawnerLootGenerator;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import me.nighter.smartSpawner.utils.UpdateChecker;
import me.nighter.smartSpawner.nms.VersionInitializer;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Main class for the SmartSpawner plugin.
 * Handles initialization, dependencies, and manages the lifecycle of the plugin.
 *
 */
public class SmartSpawner extends JavaPlugin {
    // Singleton instance
    private static SmartSpawner instance;

    // Core UI components
    private SpawnerMenuUI spawnerMenuUI;
    private SpawnerStorageUI spawnerStorageUI;
    private SpawnerStackerUI spawnerStackerUI;

    // Core handlers
    private SpawnEggHandler spawnEggHandler;
    private SpawnerClickManager spawnerClickManager;
    private SpawnerStackHandler spawnerStackHandler;

    // UI actions
    private SpawnerMenuAction spawnerMenuAction;
    private SpawnerStackerAction spawnerStackerAction;
    private SpawnerStorageAction spawnerStorageAction;

    // Core managers
    private ConfigManager configManager;
    private LanguageManager languageManager;
    private SpawnerManager spawnerManager;
    private ShopIntegrationManager shopIntegrationManager;
    private HopperHandler hopperHandler;

    // Event handlers and utilities
    private GlobalEventHandlers globalEventHandlers;
    private SpawnerLootGenerator spawnerLootGenerator;
    private SpawnerListGUI spawnerListGUI;
    private SpawnerRangeChecker rangeChecker;
    private SpawnerGuiManager spawnerGuiManager;
    private SpawnerExplosionListener spawnerExplosionListener;
    private SpawnerBreakListener spawnerBreakListener;
    private SpawnerPlaceListener spawnerPlaceListener;
    private UpdateChecker updateChecker;

    // Integration flags - static for quick access
    public static boolean hasTowny = false;
    public static boolean hasLands = false;
    public static boolean hasWorldGuard = false;
    public static boolean hasGriefPrevention = false;
    public static boolean hasSuperiorSkyblock2 = false;

    /**
     * Called when the plugin is enabled.
     * Initializes all components, checks dependencies, and registers listeners.
     */
    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        instance = this;

        // Initialize version-specific components
        initializeVersionComponents();

        // Check for data migration needs
        migrateDataIfNeeded();

        // Initialize core components async where possible
        initializeComponents()
                .thenRun(() -> {
                    setupCommand();
                    checkDependencies();
                    setupBtatsMetrics();
                    registerListeners();
                    initializeSaleLogging();

                    long loadTime = System.currentTimeMillis() - startTime;
                    getLogger().info("SmartSpawner has been enabled! (Loaded in " + loadTime + "ms)");
                });
    }

    /**
     * Initializes version-specific components using the dedicated initializer class.
     */
    private void initializeVersionComponents() {
        try {
            new VersionInitializer(this).initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize version-specific components", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    /**
     * Checks for and performs data migration if needed.
     */
    private void migrateDataIfNeeded() {
        SpawnerDataMigration migration = new SpawnerDataMigration(this);
        if (migration.checkAndMigrateData()) {
            getLogger().info("Data migration completed. Loading with new format...");
        }
    }

    /**
     * Initializes all plugin components with appropriate async operations.
     *
     * @return CompletableFuture that completes when initialization is done
     */
    private CompletableFuture<Void> initializeComponents() {
        // Initialize core components in order
        this.configManager = new ConfigManager(this);
        this.languageManager = new LanguageManager(this);
        this.spawnerStorageUI = new SpawnerStorageUI(this);
        this.spawnerManager = new SpawnerManager(this);
        this.spawnerListGUI = new SpawnerListGUI(this);
        this.spawnerGuiManager = new SpawnerGuiManager(this);
        this.spawnerLootGenerator = new SpawnerLootGenerator(this);
        this.rangeChecker = new SpawnerRangeChecker(this);
;
        // Parallel initialization for components that can be initialized concurrently
        CompletableFuture<Void> asyncInit = CompletableFuture.runAsync(() -> {
            this.shopIntegrationManager = new ShopIntegrationManager(this);
            this.updateChecker = new UpdateChecker(this, "9tQwxSFr");
        });

        // Main thread initialization for components that need the main thread
        this.spawnerMenuUI = new SpawnerMenuUI(this);
        this.spawnerStackerUI = new SpawnerStackerUI(this);

        this.spawnEggHandler = new SpawnEggHandler(this);
        this.spawnerStackHandler = new SpawnerStackHandler(this);
        this.spawnerClickManager = new SpawnerClickManager(this);

        this.spawnerMenuAction = new SpawnerMenuAction(this);
        this.spawnerStackerAction = new SpawnerStackerAction(this);
        this.spawnerStorageAction = new SpawnerStorageAction(this);

        this.globalEventHandlers = new GlobalEventHandlers(this);
        this.spawnerExplosionListener = new SpawnerExplosionListener(this);
        this.spawnerBreakListener = new SpawnerBreakListener(this);
        this.spawnerPlaceListener = new SpawnerPlaceListener(this);

        // Initialize hopper handler if enabled in config
        if (configManager.isHopperEnabled()) {
            this.hopperHandler = new HopperHandler(this);
        }

        // Complete initialization
        return asyncInit.thenRunAsync(() -> {
            updateChecker.initialize();
        }, getServer().getScheduler().getMainThreadExecutor(this));
    }

    /**
     * Registers all event listeners.
     */
    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        // Register core listeners
        pm.registerEvents(globalEventHandlers, this);
        pm.registerEvents(spawnerListGUI, this);
        pm.registerEvents(spawnerBreakListener, this);
        pm.registerEvents(spawnerPlaceListener, this);
        pm.registerEvents(spawnerStorageAction, this);
        pm.registerEvents(spawnerExplosionListener, this);
        pm.registerEvents(spawnerGuiManager, this);
        pm.registerEvents(spawnerClickManager, this);
        pm.registerEvents(spawnerMenuAction, this);
        pm.registerEvents(spawnerStackerAction, this);

        // Register shop integration listeners if available
        if (isShopGUIPlusEnabled()) {
            pm.registerEvents(new SpawnerHook(this), this);
        }
    }

    /**
     * Sets up and registers plugin commands.
     */
    private void setupCommand() {
        CommandHandler commandHandler = new CommandHandler(this);
        getCommand("smartspawner").setExecutor(commandHandler);
        getCommand("smartspawner").setTabCompleter(commandHandler);
    }

    /**
     * Sets up bStats metrics for plugin usage tracking.
     */
    private void setupBtatsMetrics() {
        Metrics metrics = new Metrics(this, 24822);
        metrics.addCustomChart(new Metrics.SimplePie("players",
                () -> String.valueOf(Bukkit.getOnlinePlayers().size())));
    }

    /**
     * Initializes the sale logging system if enabled in config.
     */
    private void initializeSaleLogging() {
        if (configManager.isLoggingEnabled()) {
            SaleLogger.getInstance();
        }
    }

    /**
     * Checks and initializes all plugin dependencies and integrations.
     */
    private void checkDependencies() {
        // Run protection plugin checks in parallel
        CompletableFuture.runAsync(this::checkProtectionPlugins);

        // Initialize shop integrations
        shopIntegrationManager.initialize();

        // Check for Floodgate (Bedrock support)
        checkFloodgate();
    }

    /**
     * Checks for Floodgate integration (for Bedrock players).
     */
    private void checkFloodgate() {
        checkPlugin("Floodgate", () -> FloodgateApi.getInstance() != null, false);
    }

    /**
     * Checks for protection plugin integrations.
     */
    private void checkProtectionPlugins() {
        hasWorldGuard = checkPlugin("WorldGuard", () -> {
            try {
                Class.forName("com.sk89q.worldguard.WorldGuard");
                return com.sk89q.worldguard.WorldGuard.getInstance() != null;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }, true);

        hasGriefPrevention = checkPlugin("GriefPrevention", () -> {
            Plugin griefPlugin = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            return griefPlugin != null && griefPlugin instanceof GriefPrevention;
        }, true);

        hasLands = checkPlugin("Lands", () -> {
            Plugin landsPlugin = Bukkit.getPluginManager().getPlugin("Lands");
            if (landsPlugin != null) {
                new Lands(this);
                return true;
            }
            return false;
        }, true);

        hasTowny = checkPlugin("Towny", () -> {
            try {
                Class.forName("com.palmergames.bukkit.towny.TownyAPI");
                return com.palmergames.bukkit.towny.TownyAPI.getInstance() != null;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }, true);

        hasSuperiorSkyblock2 = checkPlugin("SuperiorSkyblock2", () ->
                Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2") != null, true);
    }

    /**
     * Checks if a plugin is available and functional.
     *
     * @param pluginName The name of the plugin to check
     * @param checker A function that checks if the plugin is properly loaded
     * @param logSuccess Whether to log successful integration
     * @return true if the plugin is available and functional
     */
    private boolean checkPlugin(String pluginName, PluginCheck checker, boolean logSuccess) {
        try {
            if (checker.check()) {
                if (logSuccess) {
                    getLogger().info(pluginName + " integration enabled successfully!");
                }
                return true;
            }
        } catch (NoClassDefFoundError | NullPointerException e) {
            // Silent fail - plugin not available
        }
        return false;
    }

    /**
     * Called when the plugin is disabled.
     * Saves data and cleans up resources.
     */
    @Override
    public void onDisable() {
        saveAndCleanup();
        SpawnerMobHeadTexture.clearCache();
        shutdownSaleLogger();
        getLogger().info("SmartSpawner has been disabled!");
    }

    /**
     * Shuts down the sale logger if it was enabled.
     */
    private void shutdownSaleLogger() {
        if (configManager != null && configManager.isLoggingEnabled()) {
            SaleLogger.getInstance().shutdown();
        }
    }

    /**
     * Saves all data and cleans up resources.
     */
    private void saveAndCleanup() {
        // Save spawner data
        if (spawnerManager != null) {
            spawnerManager.saveSpawnerData();
            spawnerManager.cleanupAllSpawners();
        }

        // Clean up other resources
        if (rangeChecker != null) rangeChecker.cleanup();
        if (spawnerGuiManager != null) spawnerGuiManager.cleanup();
        if (hopperHandler != null) hopperHandler.cleanup();
        if (spawnerClickManager != null) spawnerClickManager.cleanup();
        if (spawnerStackerAction != null) spawnerStackerAction.cleanup();
        if (spawnerStorageUI != null) spawnerStorageUI.cleanup();
        if (updateChecker != null) updateChecker.shutdown();
    }

    /**
     * Runs a task asynchronously.
     *
     * @param runnable The task to run
     * @return The BukkitTask representing the scheduled task
     */
    public BukkitTask runTaskAsync(Runnable runnable) {
        return getServer().getScheduler().runTaskAsynchronously(this, runnable);
    }

    /**
     * Functional interface for plugin availability checks.
     */
    @FunctionalInterface
    private interface PluginCheck {
        boolean check();
    }

    // Getters

    public static SmartSpawner getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public SpawnerMenuUI getSpawnerMenuUI() {
        return spawnerMenuUI;
    }

    public SpawnerGuiManager getSpawnerGuiManager() {
        return spawnerGuiManager;
    }

    public SpawnEggHandler getSpawnEggHandler() {
        return spawnEggHandler;
    }

    public SpawnerStackerUI getSpawnerStackerUI() {
        return spawnerStackerUI;
    }

    public SpawnerStackerActionUpdater getSpawnerStackerUpdater() {
        return spawnerStackerAction.getSpawnerStackerUpdater();
    }

    public SpawnerStorageUI getSpawnerStorageUI() {
        return spawnerStorageUI;
    }

    public SpawnerLootGenerator getSpawnerLootGenerator() {
        return spawnerLootGenerator;
    }

    public SpawnerManager getSpawnerManager() {
        return spawnerManager;
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

    public IShopIntegration getShopIntegration() {
        return shopIntegrationManager.getShopIntegration();
    }

    /**
     * Checks if any shop integration is available.
     *
     * @return true if a shop integration is available
     */
    public boolean hasShopIntegration() {
        return shopIntegrationManager.hasShopIntegration();
    }

    /**
     * Checks if ShopGUIPlus integration is enabled.
     *
     * @return true if ShopGUIPlus integration is enabled
     */
    public boolean isShopGUIPlusEnabled() {
        return shopIntegrationManager.isShopGUIPlusEnabled();
    }

    /**
     * Gets the spawner provider for shop integrations.
     *
     * @return A new SpawnerProvider instance
     */
    public SpawnerProvider getSpawnerProvider() {
        return new SpawnerProvider(this);
    }
}