package me.nighter.smartSpawner.spawner.properties;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.nms.ParticleWrapper;
import me.nighter.smartSpawner.holders.SpawnerMenuHolder;
import me.nighter.smartSpawner.spawner.gui.storage.SpawnerStorageUI;
import me.nighter.smartSpawner.holders.StoragePageHolder;
import me.nighter.smartSpawner.spawner.properties.utils.LootResult;
import me.nighter.smartSpawner.spawner.properties.utils.SpawnerFileHandler;
import me.nighter.smartSpawner.spawner.properties.utils.SpawnerLootGenerator;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import org.bukkit.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages all spawner data and interactions, delegating file operations
 * to SpawnerFileHandler for improved performance.
 */
public class SpawnerManager {
    private final SmartSpawner plugin;
    private Map<String, SpawnerData> spawners = new HashMap<>();
    private Map<LocationKey, SpawnerData> locationIndex = new HashMap<>();
    private long previousExpValue = 0;
    private final SpawnerLootGenerator spawnerLootGenerator;
    private final SpawnerStorageUI lootManager;
    private final Map<UUID, SpawnerData> openSpawnerGuis = new ConcurrentHashMap<>();
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final Map<String, Set<SpawnerData>> worldIndex = new HashMap<>();
    private final SpawnerFileHandler fileHandler;
    private final Logger logger;

    /**
     * Constructor for SpawnerManager
     *
     * @param plugin The SmartSpawner plugin instance
     */
    public SpawnerManager(SmartSpawner plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerLootGenerator = plugin.getLootGenerator();
        this.lootManager = plugin.getLootManager();

        // Initialize file handler
        this.fileHandler = new SpawnerFileHandler(plugin);

        // Load spawners from file
        loadSpawnerData();
    }

    /**
     * Key class for efficient location-based spawner lookups
     */
    private static class LocationKey {
        private final String world;
        private final int x, y, z;

        public LocationKey(Location location) {
            this.world = location.getWorld().getName();
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LocationKey)) return false;
            LocationKey that = (LocationKey) o;
            return x == that.x &&
                    y == that.y &&
                    z == that.z &&
                    world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, x, y, z);
        }
    }

    /**
     * Adds a spawner to the manager and indexes it
     *
     * @param id The spawner ID
     * @param spawner The spawner data object
     */
    public void addSpawner(String id, SpawnerData spawner) {
        spawners.put(id, spawner);
        locationIndex.put(new LocationKey(spawner.getSpawnerLocation()), spawner);

        // Add to world index
        String worldName = spawner.getSpawnerLocation().getWorld().getName();
        worldIndex.computeIfAbsent(worldName, k -> new HashSet<>()).add(spawner);

        // Queue for saving
        fileHandler.queueSpawnerForSaving(id);
    }

    /**
     * Removes a spawner from the manager and file storage
     *
     * @param id The spawner ID to remove
     */
    public void removeSpawner(String id) {
        SpawnerData spawner = spawners.get(id);
        if (spawner != null) {
            spawner.removeHologram();
            locationIndex.remove(new LocationKey(spawner.getSpawnerLocation()));

            // Remove from world index
            String worldName = spawner.getSpawnerLocation().getWorld().getName();
            Set<SpawnerData> worldSpawners = worldIndex.get(worldName);
            if (worldSpawners != null) {
                worldSpawners.remove(spawner);
                if (worldSpawners.isEmpty()) {
                    worldIndex.remove(worldName);
                }
            }

            spawners.remove(id);
        }
        fileHandler.deleteSpawnerFromFile(id);
    }

    /**
     * Counts spawners in a specific world
     *
     * @param worldName The name of the world
     * @return Number of spawners in that world
     */
    public int countSpawnersInWorld(String worldName) {
        Set<SpawnerData> worldSpawners = worldIndex.get(worldName);
        return worldSpawners != null ? worldSpawners.size() : 0;
    }

    /**
     * Counts total spawners including stacks in a world
     *
     * @param worldName The name of the world
     * @return Total count including stacked spawners
     */
    public int countTotalSpawnersWithStacks(String worldName) {
        Set<SpawnerData> worldSpawners = worldIndex.get(worldName);
        if (worldSpawners == null) return 0;

        return worldSpawners.stream()
                .mapToInt(SpawnerData::getStackSize)
                .sum();
    }

    /**
     * Rebuilds world indexes - useful after world loads/unloads
     */
    public void reindexWorlds() {
        worldIndex.clear();
        for (SpawnerData spawner : spawners.values()) {
            String worldName = spawner.getSpawnerLocation().getWorld().getName();
            worldIndex.computeIfAbsent(worldName, k -> new HashSet<>()).add(spawner);
        }
    }

    /**
     * Gets a spawner by its location in the world
     *
     * @param location The location to check
     * @return The spawner at that location, or null if none exists
     */
    public SpawnerData getSpawnerByLocation(Location location) {
        return locationIndex.get(new LocationKey(location));
    }

    /**
     * Gets a spawner by its unique ID
     *
     * @param id The spawner ID
     * @return The spawner with that ID, or null if none exists
     */
    public SpawnerData getSpawnerById(String id) {
        return spawners.get(id);
    }

    /**
     * Gets all spawners currently managed
     *
     * @return List of all spawner data objects
     */
    public List<SpawnerData> getAllSpawners() {
        return new ArrayList<>(spawners.values());
    }


    /**
     * Loads all spawner data from file storage
     */
    public void loadSpawnerData() {
        // Clear existing data
        spawners.clear();
        locationIndex.clear();
        worldIndex.clear();

        // Load spawners from file handler
        Map<String, SpawnerData> loadedSpawners = fileHandler.loadAllSpawners();
        boolean hologramEnabled = configManager.isHologramEnabled();

        // Add all loaded spawners to our indexes
        for (Map.Entry<String, SpawnerData> entry : loadedSpawners.entrySet()) {
            String spawnerId = entry.getKey();
            SpawnerData spawner = entry.getValue();

            spawners.put(spawnerId, spawner);
            locationIndex.put(new LocationKey(spawner.getSpawnerLocation()), spawner);

            // Add to world index
            World world = spawner.getSpawnerLocation().getWorld();
            if (world != null) {
                String worldName = world.getName();
                worldIndex.computeIfAbsent(worldName, k -> new HashSet<>()).add(spawner);
            }
        }

        // Update holograms if enabled
        if (hologramEnabled && !spawners.isEmpty()) {
            removeAllGhostsHolograms();
            Bukkit.getScheduler().runTask(plugin, () -> {
                logger.info("Updating holograms for all spawners...");
                spawners.values().forEach(SpawnerData::updateHologramData);
            });
        }
    }


    /**
     * Marks a spawner as modified for batch saving
     *
     * @param spawnerId The ID of the modified spawner
     */
    public void markSpawnerModified(String spawnerId) {
        fileHandler.markSpawnerModified(spawnerId);
    }

    /**
     * Immediately queues a spawner for saving
     *
     * @param spawnerId The ID of the spawner to save
     */
    public void queueSpawnerForSaving(String spawnerId) {
        fileHandler.queueSpawnerForSaving(spawnerId);
    }

    /**
     * Saves all spawner data to file - mainly used for server shutdown
     */
    public void saveSpawnerData() {
        fileHandler.saveAllSpawners(spawners);
    }

    /**
     * Saves only modified spawners
     */
    public void saveModifiedSpawners() {
        fileHandler.saveModifiedSpawners();
    }

    /**
     * Gets the file handler instance
     *
     * @return The spawner file handler
     */
    public SpawnerFileHandler getFileHandler() {
        return fileHandler;
    }

    // ===============================================================
    //                      Loot Spawning
    // ===============================================================

    public void spawnLoot(SpawnerData spawner) {
        if (System.currentTimeMillis() - spawner.getLastSpawnTime() >= spawner.getSpawnDelay()) {
            // Run heavy calculations async
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                LootResult loot = spawnerLootGenerator.generateLoot(
                        spawner.getEntityType(),
                        spawner.getMinMobs(),
                        spawner.getMaxMobs(),
                        spawner
                );

                // Switch back to main thread for Bukkit API calls
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (configManager.isLootSpawnParticlesEnabled()) {
                        Location loc = spawner.getSpawnerLocation();
                        World world = loc.getWorld();
                        world.spawnParticle(ParticleWrapper.VILLAGER_HAPPY,
                                loc.clone().add(0.5, 0.5, 0.5),
                                10, 0.3, 0.3, 0.3, 0);
                    }
                    // Calculate pages before adding new loot
                    int oldTotalPages = calculateTotalPages(spawner);

                    spawnerLootGenerator.addLootToSpawner(spawner, loot);
                    spawner.setLastSpawnTime(System.currentTimeMillis());

                    // Calculate pages after adding new loot
                    int newTotalPages = calculateTotalPages(spawner);

                    updateLootInventoryViewers(spawner, oldTotalPages, newTotalPages);
                    updateSpawnerGuiViewers(spawner);

                    if (configManager.isHologramEnabled()) {
                        spawner.updateHologramData();
                    }
                    // Mark spawner as modified for saving
                    markSpawnerModified(spawner.getSpawnerId());
                });
            });
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        VirtualInventory virtualInv = spawner.getVirtualInventory();
        int totalItems = virtualInv.getDisplayInventory().size();
        return Math.max(1, (int) Math.ceil((double) totalItems / 45));
    }

    private record UpdateAction(Player player, SpawnerData spawner, int page, boolean requiresNewInventory) {
    }

    private void updateLootInventoryViewers(SpawnerData spawner, int oldTotalPages, int newTotalPages) {
        // Check if total pages changed
        boolean pagesChanged = oldTotalPages != newTotalPages;

        // Batch all viewers that need updates
        List<UpdateAction> updateQueue = new ArrayList<>();

        for (HumanEntity viewer : getViewersForSpawner(spawner)) {
            if (viewer instanceof Player) {
                Player player = (Player) viewer;
                Inventory currentInv = player.getOpenInventory().getTopInventory();
                if (currentInv.getHolder() instanceof StoragePageHolder) {
                    StoragePageHolder holder = (StoragePageHolder) currentInv.getHolder();
                    int currentPage = holder.getCurrentPage();

                    boolean needsNewInventory = false;
                    int targetPage = currentPage;

                    // Determine if we need a new inventory
                    if (currentPage > newTotalPages) {
                        targetPage = newTotalPages;
                        needsNewInventory = true;
                    } else if (pagesChanged) {
                        // If total pages changed but current page is still valid,
                        // we need new inventory just to update the title
                        needsNewInventory = true;
                    }

                    updateQueue.add(new UpdateAction(player, spawner, targetPage, needsNewInventory));
                }
            }
        }

        // Process all updates in one server tick
        if (!updateQueue.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (UpdateAction action : updateQueue) {
                    if (action.requiresNewInventory) {
                        // Need new inventory for title update
                        Inventory newInv = lootManager.createInventory(
                                action.spawner,
                                languageManager.getGuiTitle("gui-title.loot-menu"),
                                action.page
                        );
                        action.player.closeInventory();
                        action.player.openInventory(newInv);
                    } else {
                        // Just update contents of current inventory
                        Inventory currentInv = action.player.getOpenInventory().getTopInventory();
                        lootManager.updateDisplay(currentInv, action.spawner, action.page);
                        action.player.updateInventory();
                    }
                }
            });
        }
    }

    private void updateSpawnerGuiViewers(SpawnerData spawner) {
        for (Map.Entry<UUID, SpawnerData> entry : openSpawnerGuis.entrySet()) {
            if (entry.getValue().getSpawnerId().equals(spawner.getSpawnerId())) {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer != null && viewer.isOnline()) {
                    updateSpawnerGui(viewer, spawner, true);
                }
            }
        }
    }

    public void updateSpawnerGui(Player player, SpawnerData spawner, boolean forceUpdate) {
        Inventory openInv = player.getOpenInventory().getTopInventory();
        if (openInv.getHolder() instanceof SpawnerMenuHolder) {
            SpawnerMenuHolder holder = (SpawnerMenuHolder) openInv.getHolder();
            if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId()) || forceUpdate) {
                updateSpawnerInfoItem(openInv, spawner);
                updateExpItem(openInv, spawner);
                updateChestItem(openInv, spawner);
                player.updateInventory();
            }
        }
    }

    private List<HumanEntity> getViewersForSpawner(SpawnerData spawner) {
        List<HumanEntity> viewers = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory openInv = player.getOpenInventory().getTopInventory();
            if (openInv.getHolder() instanceof StoragePageHolder) {
                StoragePageHolder holder = (StoragePageHolder) openInv.getHolder();
                if (holder.getSpawnerData().getSpawnerId().equals(spawner.getSpawnerId())) {
                    viewers.add(player);
                }
            }
        }
        return viewers;
    }

    public void trackOpenGui(UUID playerId, SpawnerData spawner) {
        openSpawnerGuis.put(playerId, spawner);
    }

    public void untrackOpenGui(UUID playerId) {
        openSpawnerGuis.remove(playerId);
    }

    private void updateChestItem(Inventory inventory, SpawnerData spawner) {
        ItemStack chestItem = inventory.getItem(11);
        if (chestItem == null || !chestItem.hasItemMeta()) return;

        ItemMeta chestMeta = chestItem.getItemMeta();
        chestMeta.setDisplayName(languageManager.getMessage("spawner-loot-item.name"));

        // Create replacements map
        VirtualInventory virtualInventory = spawner.getVirtualInventory();
        int currentItems = virtualInventory.getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        int percentStorage = (int) ((double) currentItems / maxSlots * 100);

        Map<String, String> replacements = new HashMap<>();
        replacements.put("%max_slots%", String.valueOf(maxSlots));
        replacements.put("%current_items%", String.valueOf(currentItems));
        replacements.put("%percent_storage%", String.valueOf(percentStorage));

        // Get lore from language file with replacements
        String loreMessageChest = languageManager.getMessage("spawner-loot-item.lore.chest", replacements);

        List<String> chestLore = Arrays.asList(loreMessageChest.split("\n"));
        chestMeta.setLore(chestLore);
        chestItem.setItemMeta(chestMeta);
        inventory.setItem(11, chestItem);
    }

    private void updateExpItem(Inventory inventory, SpawnerData spawner) {
        ItemStack expItem = inventory.getItem(15);
        if (expItem == null || !expItem.hasItemMeta()) return;

        long currentExp = spawner.getSpawnerExp();
        if (currentExp != previousExpValue) {

            ItemMeta expMeta = expItem.getItemMeta();
            Map<String, String> nameReplacements = new HashMap<>();
            String formattedExp = languageManager.formatNumber(currentExp);
            String formattedMaxExp = languageManager.formatNumber(spawner.getMaxStoredExp());
            int percentExp = (int) ((double) spawner.getSpawnerExp() / spawner.getMaxStoredExp() * 100);

            nameReplacements.put("%current_exp%", String.valueOf(spawner.getSpawnerExp()));
            expMeta.setDisplayName(languageManager.getMessage("exp-info-item.name", nameReplacements));
            Map<String, String> loreReplacements = new HashMap<>();
            loreReplacements.put("%current_exp%", formattedExp);
            loreReplacements.put("%max_exp%", formattedMaxExp);
            loreReplacements.put("%percent_exp%", String.valueOf(percentExp));
            loreReplacements.put("%u_max_exp%", String.valueOf(spawner.getMaxStoredExp()));
            String lorePathExp = "exp-info-item.lore.exp-bottle";
            String loreMessageExp = languageManager.getMessage(lorePathExp, loreReplacements);
            List<String> loreEx = Arrays.asList(loreMessageExp.split("\n"));
            expMeta.setLore(loreEx);
            expItem.setItemMeta(expMeta);
            inventory.setItem(15, expItem);
            previousExpValue = currentExp;
        }
    }

    private void updateSpawnerInfoItem(Inventory inventory, SpawnerData spawner) {
        ItemStack spawnerItem = inventory.getItem(13);
        if (spawnerItem == null || !spawnerItem.hasItemMeta()) return;

        ItemMeta meta = spawnerItem.getItemMeta();
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

        long timeUntilNextSpawn = calculateTimeUntilNextSpawn(spawner);
        String timeDisplay = getTimeDisplay(timeUntilNextSpawn);

        // Lấy template cho next spawn line
        String nextSpawnTemplate = languageManager.getMessage("spawner-info-item.lore-change");

        // Strip màu để so sánh
        String strippedTemplate = ChatColor.stripColor(nextSpawnTemplate);

        // Optimize lore update
        boolean updated = false;
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            // Strip màu của line hiện tại để so sánh
            String strippedLine = ChatColor.stripColor(line);

            // So sánh nội dung đã strip màu
            if (strippedLine.startsWith(ChatColor.stripColor(strippedTemplate))) {
                String newLine = nextSpawnTemplate + timeDisplay;
                if (!line.equals(newLine)) {
                    lore.set(i, newLine);
                    updated = true;
                }
                break;
            }
        }

        // Chỉ update ItemMeta nếu có thay đổi
        if (updated || !lore.stream()
                .map(ChatColor::stripColor)
                .anyMatch(line -> line.startsWith(ChatColor.stripColor(strippedTemplate)))) {
            if (!updated) {
                lore.add(nextSpawnTemplate + timeDisplay);
            }
            meta.setLore(lore);
            spawnerItem.setItemMeta(meta);
        }
    }

    private String getTimeDisplay(long timeUntilNextSpawn) {
        if (timeUntilNextSpawn == -1) {
            return languageManager.getMessage("spawner-info-item.lore-now");
        } else if (timeUntilNextSpawn == 0) {
            return languageManager.getMessage("spawner-info-item.lore-now");
        } else if (timeUntilNextSpawn < 0) {
            return languageManager.getMessage("spawner-info-item.lore-error");
        }
        return formatTime(timeUntilNextSpawn);
    }

    private long calculateTimeUntilNextSpawn(SpawnerData spawner) {
        if (!spawner.getSpawnerActive() || spawner.getSpawnerStop()) {
            return -1;
        }
        final long currentTime = System.currentTimeMillis();
        final long lastSpawnTime = spawner.getLastSpawnTime();
        final long spawnDelay = spawner.getSpawnDelay() * 50L;
        return lastSpawnTime + spawnDelay - currentTime;
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }


    public Map<UUID, SpawnerData> getOpenSpawnerGuis() {
        return openSpawnerGuis;
    }

    public void cleanupAllSpawners() {
        for (SpawnerData spawner : spawners.values()) {
            spawner.removeHologram();
        }
        spawners.clear();
        locationIndex.clear();
    }


    // ===============================================================
    //                    Spawner Hologram
    // ===============================================================

    public void refreshAllHolograms() {
        spawners.values().forEach(SpawnerData::refreshHologram);
    }

    public void reloadAllHolograms() {
        if (configManager.isHologramEnabled()) {
            spawners.values().forEach(SpawnerData::reloadHologramData);
        }
    }

    public void removeAllGhostsHolograms() {
        spawners.values().forEach(SpawnerData::removeGhostHologram);
    }

}