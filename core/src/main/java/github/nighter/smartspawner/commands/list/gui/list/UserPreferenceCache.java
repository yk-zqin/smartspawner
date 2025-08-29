package github.nighter.smartspawner.commands.list.gui.list;

import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.commands.list.gui.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.gui.list.enums.SortOption;
import lombok.Data;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches user preferences for the spawner list GUI
 */
public class UserPreferenceCache {
    private final Map<UUID, Map<String, UserPreference>> userPreferences = new ConcurrentHashMap<>();
    private final Plugin plugin;
    private static final long EXPIRY_TICKS = 20 * 60 * 30; // 30 minutes
    private Scheduler.Task cleanupTask;

    @Data
    public static class UserPreference {
        private final FilterOption filterOption;
        private final SortOption sortOption;
        private final long timestamp;

        public UserPreference(FilterOption filterOption, SortOption sortOption) {
            this.filterOption = filterOption;
            this.sortOption = sortOption;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 1000 * 60 * 30; // 30 minutes
        }
    }

    public UserPreferenceCache(Plugin plugin) {
        this.plugin = plugin;
        cleanupTask = Scheduler.runTaskTimerAsync(this::cleanupExpiredPreferences, EXPIRY_TICKS, EXPIRY_TICKS);
    }

    /**
     * Saves user preferences for a specific world
     */
    public void savePreference(UUID playerUuid, String worldName,
                               FilterOption filterOption,
                               SortOption sortOption) {
        userPreferences.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>())
                .put(worldName, new UserPreference(filterOption, sortOption));
    }

    /**
     * Gets user preferences for a specific world
     * @return The user's preference or null if not found
     */
    public UserPreference getPreference(UUID playerUuid, String worldName) {
        Map<String, UserPreference> worldPreferences = userPreferences.get(playerUuid);
        if (worldPreferences == null) {
            return null;
        }

        UserPreference preference = worldPreferences.get(worldName);
        if (preference != null && preference.isExpired()) {
            worldPreferences.remove(worldName);
            return null;
        }

        return preference;
    }

    /**
     * Cleans up expired preferences to prevent memory leaks
     */
    private void cleanupExpiredPreferences() {
        long now = System.currentTimeMillis();
        userPreferences.forEach((uuid, worldPrefs) -> {
            worldPrefs.entrySet().removeIf(entry -> entry.getValue().isExpired());
            if (worldPrefs.isEmpty()) {
                userPreferences.remove(uuid);
            }
        });
    }

    /**
     * Clears all preferences for a player
     */
    public void clearPreferences(UUID playerUuid) {
        userPreferences.remove(playerUuid);
    }

    /**
     * Gets user's filter preference for a specific world
     */
    public FilterOption getUserFilter(org.bukkit.entity.Player player, String worldName) {
        UserPreference preference = getPreference(player.getUniqueId(), worldName);
        return preference != null ? preference.getFilterOption() : FilterOption.ALL;
    }

    /**
     * Gets user's sort preference for a specific world
     */
    public SortOption getUserSort(org.bukkit.entity.Player player, String worldName) {
        UserPreference preference = getPreference(player.getUniqueId(), worldName);
        return preference != null ? preference.getSortOption() : SortOption.DEFAULT;
    }

    /**
     * Cancels the cleanup task when no longer needed
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }
}