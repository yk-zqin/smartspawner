package github.nighter.smartspawner.commands.list;

import github.nighter.smartspawner.Scheduler;
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
        private final ListCommand.FilterOption filterOption;
        private final ListCommand.SortOption sortOption;
        private final long timestamp;

        public UserPreference(ListCommand.FilterOption filterOption, ListCommand.SortOption sortOption) {
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
                               ListCommand.FilterOption filterOption,
                               ListCommand.SortOption sortOption) {
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
     * Cancels the cleanup task when no longer needed
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }
}