package me.nighter.smartSpawner.spawner.gui.synchronization;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.holders.SpawnerHolder;
import me.nighter.smartSpawner.holders.SpawnerMenuHolder;
import me.nighter.smartSpawner.holders.StoragePageHolder;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerViewsUpdater implements Listener {
    private final SmartSpawner plugin;
    private final Map<String, Set<UUID>> spawnerViewers; // SpawnerID -> Set of Player UUIDs
    private final Map<UUID, String> playerCurrentSpawner; // PlayerUUID -> SpawnerID
    private final Set<Class<? extends InventoryHolder>> validHolderTypes;
    private static final int ITEMS_PER_PAGE = 45; // Standard chest inventory size minus navigation items

    public SpawnerViewsUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerViewers = new ConcurrentHashMap<>();
        this.playerCurrentSpawner = new ConcurrentHashMap<>();
        this.validHolderTypes = Set.of(
                StoragePageHolder.class,
                SpawnerMenuHolder.class
        );
    }

    public void trackViewer(String spawnerId, Player player) {
        spawnerViewers.computeIfAbsent(spawnerId, k -> ConcurrentHashMap.newKeySet()).add(player.getUniqueId());
        playerCurrentSpawner.put(player.getUniqueId(), spawnerId);
    }

    public void untrackViewer(Player player) {
        String spawnerId = playerCurrentSpawner.remove(player.getUniqueId());
        if (spawnerId != null) {
            Set<UUID> viewers = spawnerViewers.get(spawnerId);
            if (viewers != null) {
                viewers.remove(player.getUniqueId());
                if (viewers.isEmpty()) {
                    spawnerViewers.remove(spawnerId);
                }
            }
        }
    }

    public Set<Player> getViewers(String spawnerId) {
        Set<Player> players = new HashSet<>();
        Set<UUID> viewerIds = spawnerViewers.get(spawnerId);
        if (viewerIds != null) {
            viewerIds.forEach(uuid -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    players.add(player);
                }
            });
        }
        return players;
    }

    private int calculateTotalPages(int totalItems) {
        return (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!isValidHolder(event.getInventory().getHolder())) return;

        // Schedule check for inventory reopen
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            Inventory openInv = player.getOpenInventory().getTopInventory();
            if (openInv == null || !isValidHolder(openInv.getHolder())) {
                untrackViewer(player);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        untrackViewer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        InventoryHolder holder = event.getInventory().getHolder();
        if (!isValidHolder(holder)) return;

        // Get spawner ID and update all viewers
        SpawnerData spawner = ((SpawnerHolder) holder).getSpawnerData();
        updateViewers(spawner);
    }

    public void updateViewers(SpawnerData spawner) {
        Set<Player> viewers = getViewers(spawner.getSpawnerId());
        if (viewers.isEmpty()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player viewer : viewers) {
                if (!viewer.isOnline()) continue;

                Inventory openInv = viewer.getOpenInventory().getTopInventory();
                if (openInv != null && isValidHolder(openInv.getHolder())) {
                    if (openInv.getHolder() instanceof SpawnerMenuHolder) {
                        plugin.getSpawnerGuiUpdater().updateSpawnerGui(viewer, spawner, true);
                    } else if (openInv.getHolder() instanceof StoragePageHolder) {
                        StoragePageHolder storageHolder = (StoragePageHolder) openInv.getHolder();
                        int oldTotalPages = calculateTotalPages(storageHolder.getOldUsedSlots());
                        int newTotalPages = calculateTotalPages(spawner.getVirtualInventory().getUsedSlots());

                        plugin.getSpawnerGuiUpdater().updateLootInventoryViewers(
                                spawner, oldTotalPages, newTotalPages);
                    }
                }
            }
        });
    }

    private boolean isValidHolder(InventoryHolder holder) {
        return holder != null && validHolderTypes.contains(holder.getClass());
    }

    public void closeAllViewersInventory(SpawnerData spawner) {
        Set<Player> viewers = getViewers(spawner.getSpawnerId());

        if (!viewers.isEmpty()) {
            for (Player viewer : viewers) {
                if (viewer != null && viewer.isOnline()) {
                    Inventory openInv = viewer.getOpenInventory().getTopInventory();
                    if (openInv != null && (openInv.getHolder() instanceof SpawnerMenuHolder ||
                            openInv.getHolder() instanceof StoragePageHolder)) {
                        viewer.closeInventory();
                    }
                }
            }
        }

        Set<UUID> stackerViewers = plugin.getSpawnerStackerUpdater().getSpawnerViewers(spawner.getSpawnerId());
        if (stackerViewers != null) {
            for (UUID viewerId : stackerViewers) {
                Player viewer = Bukkit.getPlayer(viewerId);
                if (viewer != null && viewer.isOnline()) {
                    viewer.closeInventory();
                }
            }
        }
    }

    public void cleanup() {
        spawnerViewers.clear();
        playerCurrentSpawner.clear();
    }
}