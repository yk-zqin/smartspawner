package me.nighter.smartSpawner.spawner.interactions.stack;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.protections.CheckStackBlock;
import me.nighter.smartSpawner.nms.ParticleWrapper;
import me.nighter.smartSpawner.spawner.gui.synchronization.SpawnerViewsUpdater;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpawnerStackHandler {
    private static final Pattern ECONOMY_SHOP_GUI_PATTERN = Pattern.compile("§9§l([A-Za-z]+(?: [A-Za-z]+)?) §rSpawner");
    private static final long STACK_COOLDOWN = 250L; // 250ms cooldown between stacks
    private static final long GUI_UPDATE_DELAY = 1L;

    private final SmartSpawner plugin;
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerViewsUpdater spawnerViewsUpdater;
    private final Map<UUID, Long> lastStackTime;
    private final Map<Location, UUID> stackLocks;
    private final Cache<Location, EntityType> spawnerTypeCache;
    private final Map<String, BukkitTask> pendingUpdates = new ConcurrentHashMap<>();

    public SpawnerStackHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerViewsUpdater = plugin.getSpawnerViewUpdater();
        this.lastStackTime = new ConcurrentHashMap<>();
        this.stackLocks = new ConcurrentHashMap<>();
        this.spawnerTypeCache = new Cache<>(30); // 30 seconds cache

        // Start cleanup task
        startCleanupTask();
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                lastStackTime.entrySet().removeIf(entry -> now - entry.getValue() > 10000); // Clear after 10 seconds
                stackLocks.entrySet().removeIf(entry -> !Bukkit.getPlayer(entry.getValue()).isOnline());
                spawnerTypeCache.cleanup();
            }
        }.runTaskTimer(plugin, 200L, 200L); // Run every 10 seconds
    }

    public void handleSpawnerStacking(Player player, Block block, SpawnerData spawnerData, ItemStack itemInHand) {
        // Anti-spam check
        if (isOnCooldown(player)) {
            return;
        }

        // Cancel any pending updates for this spawner
        cancelPendingUpdate(spawnerData.getSpawnerId());

        // Try to acquire lock
        if (!acquireStackLock(player, block.getLocation())) {
            return;
        }

        try {
            boolean success = handleSpawnerStack(player, spawnerData, itemInHand, player.isSneaking());

            if (success) {

                // Schedule delayed GUI update
                BukkitTask updateTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            plugin.getSpawnerStackerUpdater().scheduleUpdateForAll(spawnerData, player);
                            // spawnerViewsUpdater.updateViewersIncludeTitle(spawnerData);
                            pendingUpdates.remove(spawnerData.getSpawnerId());
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error updating spawner GUI: " + e.getMessage());
                        }
                    }
                }.runTaskLater(plugin, GUI_UPDATE_DELAY);

                pendingUpdates.put(spawnerData.getSpawnerId(), updateTask);
            }
        } finally {
            releaseStackLock(block.getLocation());
            updateLastStackTime(player);
        }
    }

    private void cancelPendingUpdate(String spawnerId) {
        BukkitTask pendingTask = pendingUpdates.remove(spawnerId);
        if (pendingTask != null && !pendingTask.isCancelled()) {
            pendingTask.cancel();
        }
    }

    private boolean isOnCooldown(Player player) {
        long lastTime = lastStackTime.getOrDefault(player.getUniqueId(), 0L);
        return System.currentTimeMillis() - lastTime < STACK_COOLDOWN;
    }

    private void updateLastStackTime(Player player) {
        lastStackTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean acquireStackLock(Player player, Location location) {
        return stackLocks.putIfAbsent(location, player.getUniqueId()) == null;
    }

    private void releaseStackLock(Location location) {
        stackLocks.remove(location);
    }

    public boolean handleSpawnerStack(Player player, SpawnerData targetSpawner, ItemStack itemInHand, boolean stackAll) {
        if (itemInHand.getType() != Material.SPAWNER) {
            return false;
        }

        Location location = targetSpawner.getSpawnerLocation();
        if (!hasStackPermissions(player, location)) {
            return false;
        }

        // Use cached entity type if available
        Optional<EntityType> handEntityTypeOpt = spawnerTypeCache.get(location)
                .map(Optional::of)
                .orElseGet(() -> getEntityTypeFromItem(itemInHand));

        if (!handEntityTypeOpt.isPresent()) {
            languageManager.sendMessage(player, "messages.invalid-spawner");
            return false;
        }

        EntityType handEntityType = handEntityTypeOpt.get();
        spawnerTypeCache.put(location, handEntityType);

        EntityType targetEntityType = targetSpawner.getEntityType();
        if (handEntityType != targetEntityType) {
            languageManager.sendMessage(player, "messages.different-type");
            return false;
        }

        int maxStackSize = configManager.getMaxStackSize();
        int currentStack = targetSpawner.getStackSize();

        if (currentStack >= maxStackSize) {
            languageManager.sendMessage(player, "messages.stack-full");
            return false;
        }

        spawnerViewsUpdater.trackViewer(targetSpawner.getSpawnerId(), player);
        return processStackAddition(player, targetSpawner, itemInHand, stackAll, currentStack, maxStackSize);
    }


    private boolean hasStackPermissions(Player player, Location location) {
        if (!CheckStackBlock.CanPlayerPlaceBlock(player.getUniqueId(), location)) {
            languageManager.sendMessage(player, "messages.spawner-protected");
            return false;
        }

        if (!player.hasPermission("smartspawner.stack")) {
            languageManager.sendMessage(player, "no-permission");
            return false;
        }

        return true;
    }

    private Optional<EntityType> getEntityTypeFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta)) {
            return Optional.empty();
        }

        BlockStateMeta blockMeta = (BlockStateMeta) meta;
        CreatureSpawner handSpawner = (CreatureSpawner) blockMeta.getBlockState();
        EntityType entityType = handSpawner.getSpawnedType();

        if (entityType != null) {
            return Optional.of(entityType);
        }

        // Check for custom formats
        String displayName = meta.getDisplayName();

        // Try EconomyShopGUI format
        Matcher matcher = ECONOMY_SHOP_GUI_PATTERN.matcher(displayName);
        if (matcher.matches()) {
            String entityName = matcher.group(1).replace(" ", "_").toUpperCase();
            try {
                return Optional.of(EntityType.valueOf(entityName));
            } catch (IllegalArgumentException e) {
                // Invalid entity name, continue to next check
            }
        }

        // Try language manager format
        return tryLanguageManagerFormat(displayName);
    }

    private Optional<EntityType> tryLanguageManagerFormat(String displayName) {
        for (EntityType entityType : EntityType.values()) {
            String entityTypeName = entityType.name();
            String expectedName = languageManager.getMessage("spawner-name", "%entity%", entityTypeName);
            if (displayName.equals(expectedName)) {
                return Optional.of(entityType);
            }
        }
        return Optional.empty();
    }

    private boolean processStackAddition(Player player, SpawnerData targetSpawner, ItemStack itemInHand,
                                         boolean stackAll, int currentStack, int maxStackSize) {
        int itemAmount = itemInHand.getAmount();
        int spaceLeft = maxStackSize - currentStack;

        int amountToStack = stackAll ? Math.min(spaceLeft, itemAmount) : 1;
        int newStack = currentStack + amountToStack;

        targetSpawner.setStackSize(newStack);
        updatePlayerInventory(player, itemInHand, amountToStack);
        showStackAnimation(targetSpawner, newStack, player);

        // Update all viewers after successful stacking
        // spawnerViewsUpdater.updateViewersIncludeTitle(targetSpawner);
        return true;
    }

    private void updatePlayerInventory(Player player, ItemStack itemInHand, int amountUsed) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            int remainingAmount = itemInHand.getAmount() - amountUsed;

            if (remainingAmount <= 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                itemInHand.setAmount(remainingAmount);
            }
        }
    }

    private void showStackAnimation(SpawnerData spawner, int newStack, Player player) {
        if (configManager.isSpawnerStackParticlesEnabled()) {
            Location loc = spawner.getSpawnerLocation();
            World world = loc.getWorld();

            if (world != null) {
                world.spawnParticle(
                        ParticleWrapper.VILLAGER_HAPPY,
                        loc.clone().add(0.5, 0.5, 0.5),
                        10, 0.3, 0.3, 0.3, 0
                );
            }
        }

        languageManager.sendMessage(player, "messages.hand-stack", "%amount%", String.valueOf(newStack));
    }

    private static class Cache<K, V> {
        private final Map<K, CacheEntry<V>> map = new ConcurrentHashMap<>();
        private final long expirationSeconds;

        public Cache(long expirationSeconds) {
            this.expirationSeconds = expirationSeconds;
        }

        public Optional<V> get(K key) {
            CacheEntry<V> entry = map.get(key);
            if (entry != null && !entry.isExpired(expirationSeconds)) {
                return Optional.of(entry.getValue());
            }
            map.remove(key);
            return Optional.empty();
        }

        public void put(K key, V value) {
            map.put(key, new CacheEntry<>(value));
        }

        public void cleanup() {
            map.entrySet().removeIf(entry -> entry.getValue().isExpired(expirationSeconds));
        }

        private static class CacheEntry<V> {
            private final V value;
            private final long timestamp;

            public CacheEntry(V value) {
                this.value = value;
                this.timestamp = System.currentTimeMillis();
            }

            public V getValue() {
                return value;
            }

            public boolean isExpired(long expirationSeconds) {
                return System.currentTimeMillis() - timestamp > expirationSeconds * 1000;
            }
        }
    }
}