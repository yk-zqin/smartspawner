package github.nighter.smartspawner.spawner.gui.synchronization;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for efficiently updating ItemMeta using Bukkit API.
 * Optimized with an enhanced caching system for color translations.
 */
public class ItemUpdater {
    private static final Logger LOGGER = Logger.getLogger("SmartSpawner");

    // Enhanced cache configuration
    private static final int MAX_CACHE_SIZE = 512;
    private static final long CACHE_EXPIRY_TIME_MS = TimeUnit.MINUTES.toMillis(30);

    // Cache statistics
    private static final AtomicInteger cacheHits = new AtomicInteger(0);
    private static final AtomicInteger cacheMisses = new AtomicInteger(0);

    // Thread-safe cache implementation
    private static final Map<String, CachedColorTranslation> COLOR_TRANSLATION_CACHE = new ConcurrentHashMap<>();
    private static long lastCacheCleanup = System.currentTimeMillis();

    /**
     * Class to represent a cached color translation with timestamp and hit count
     */
    private static class CachedColorTranslation {
        final String translatedValue;
        final long timestamp;
        final AtomicInteger hitCount;

        CachedColorTranslation(String translatedValue) {
            this.translatedValue = translatedValue;
            this.timestamp = System.currentTimeMillis();
            this.hitCount = new AtomicInteger(1);
        }

        void incrementHits() {
            hitCount.incrementAndGet();
        }
    }

    /**
     * Updates the lore of an ItemStack efficiently by checking if changes are needed.
     *
     * @param item The ItemStack to update
     * @param newLore The new lore list
     * @return true if update was successful, false otherwise
     */
    public static boolean updateLore(ItemStack item, List<String> newLore) {
        if (item == null || newLore == null) {
            return false;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            // Apply color codes to new lore
            List<String> coloredLore = colorLoreList(newLore);

            // Check if lore is different to avoid unnecessary updates
            if (meta.hasLore() && areLoreListsEqual(meta.getLore(), coloredLore)) {
                return true; // No changes needed
            }

            // Update lore
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update lore", e);
            return false;
        }
    }

    /**
     * Updates the display name of an ItemStack if changes are needed.
     *
     * @param item The ItemStack to update
     * @param displayName The new display name
     * @return true if update was successful, false otherwise
     */
    public static boolean updateDisplayName(ItemStack item, String displayName) {
        if (item == null || displayName == null) {
            return false;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            // Apply color codes
            String coloredName = translateColorCodes(displayName);

            // Check if name is different to avoid unnecessary updates
            if (meta.hasDisplayName() && meta.getDisplayName().equals(coloredName)) {
                return true; // No changes needed
            }

            // Update name
            meta.setDisplayName(coloredName);
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update display name", e);
            return false;
        }
    }

    /**
     * Update both display name and lore in a single operation if changes are needed.
     *
     * @param item The ItemStack to update
     * @param displayName The new display name (or null to leave unchanged)
     * @param lore The new lore (or null to leave unchanged)
     * @return true if update was successful, false otherwise
     */
    public static boolean updateItemMeta(ItemStack item, String displayName, List<String> lore) {
        if (item == null) {
            return false;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            boolean needsUpdate = false;

            // Check and update display name if needed
            if (displayName != null) {
                String coloredName = translateColorCodes(displayName);
                if (!meta.hasDisplayName() || !meta.getDisplayName().equals(coloredName)) {
                    meta.setDisplayName(coloredName);
                    needsUpdate = true;
                }
            }

            // Check and update lore if needed
            if (lore != null) {
                List<String> coloredLore = colorLoreList(lore);
                if (!meta.hasLore() || !areLoreListsEqual(meta.getLore(), coloredLore)) {
                    meta.setLore(coloredLore);
                    needsUpdate = true;
                }
            }

            // Only update if changes were made
            if (needsUpdate) {
                item.setItemMeta(meta);
            }

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update item meta", e);
            return false;
        }
    }

    /**
     * Updates a specific line in the lore without replacing the entire lore list.
     *
     * @param item The ItemStack to update
     * @param lineIndex The index of the line to update (0-based)
     * @param newLine The new content for the line
     * @return true if update was successful, false otherwise
     */
    public static boolean updateLoreLine(ItemStack item, int lineIndex, String newLine) {
        if (item == null || newLine == null || lineIndex < 0) {
            return false;
        }

        try {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }

            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            // Ensure list is large enough
            while (lore.size() <= lineIndex) {
                lore.add("");
            }

            // Apply color codes
            String coloredLine = translateColorCodes(newLine);

            // Check if line is different to avoid unnecessary updates
            if (lore.get(lineIndex).equals(coloredLine)) {
                return true; // No changes needed
            }

            // Update the line
            lore.set(lineIndex, coloredLine);
            meta.setLore(lore);
            item.setItemMeta(meta);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update lore line", e);
            return false;
        }
    }

    /**
     * Efficiently applies color codes with an enhanced caching system.
     * Uses LRU-like eviction with hit counting to keep frequently used entries.
     */
    public static String translateColorCodes(String text) {
        if (text == null) return null;

        // Periodically clean expired entries
        cleanupCacheIfNeeded();

        // Check cache first
        CachedColorTranslation cached = COLOR_TRANSLATION_CACHE.get(text);
        if (cached != null) {
            cached.incrementHits();
            cacheHits.incrementAndGet();
            return cached.translatedValue;
        }

        // Not in cache, translate and store
        cacheMisses.incrementAndGet();
        String translated = ChatColor.translateAlternateColorCodes('&', text);

        // Add to cache
        COLOR_TRANSLATION_CACHE.put(text, new CachedColorTranslation(translated));

        // Manage cache size if needed
        if (COLOR_TRANSLATION_CACHE.size() > MAX_CACHE_SIZE) {
            evictLeastValuableEntries();
        }

        return translated;
    }

    /**
     * Clean expired cache entries if needed
     */
    private static void cleanupCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();

        // Only clean up once every 5 minutes to avoid performance overhead
        if (currentTime - lastCacheCleanup < TimeUnit.MINUTES.toMillis(5)) {
            return;
        }

        // Mark this cleanup
        lastCacheCleanup = currentTime;

        // Remove expired entries
        COLOR_TRANSLATION_CACHE.entrySet().removeIf(entry ->
                currentTime - entry.getValue().timestamp > CACHE_EXPIRY_TIME_MS);
    }

    /**
     * Removes least valuable entries from cache when size limit is exceeded.
     * Considers both recency and frequency (a hybrid approach).
     */
    private static void evictLeastValuableEntries() {
        // Calculate target size to remove about 25% of cache
        int targetSize = (int)(MAX_CACHE_SIZE * 0.75);

        if (COLOR_TRANSLATION_CACHE.size() <= targetSize) {
            return;
        }

        // Find entries with lowest hit count and oldest timestamps
        long currentTime = System.currentTimeMillis();

        // Create a list of entries sorted by value (hit count / age)
        List<Map.Entry<String, CachedColorTranslation>> entries =
                new ArrayList<>(COLOR_TRANSLATION_CACHE.entrySet());

        // Sort by a weighted score of hit count and recency
        entries.sort((a, b) -> {
            double scoreA = calculateEntryValue(a.getValue(), currentTime);
            double scoreB = calculateEntryValue(b.getValue(), currentTime);
            return Double.compare(scoreA, scoreB);
        });

        // Remove lowest scoring entries
        int entriesToRemove = COLOR_TRANSLATION_CACHE.size() - targetSize;
        for (int i = 0; i < entriesToRemove && i < entries.size(); i++) {
            COLOR_TRANSLATION_CACHE.remove(entries.get(i).getKey());
        }
    }

    /**
     * Calculate a value score for a cache entry based on hits and age.
     * Higher score = more valuable to keep.
     */
    private static double calculateEntryValue(CachedColorTranslation entry, long currentTime) {
        // Age factor: newer = higher value
        double ageFactor = 1.0 - Math.min(1.0,
                (double)(currentTime - entry.timestamp) / CACHE_EXPIRY_TIME_MS);

        // Hit factor: more hits = higher value
        int hits = entry.hitCount.get();

        // Combined score with more weight on hits than age
        return (hits * 0.7) + (ageFactor * 0.3);
    }

    /**
     * Applies color codes to a list of lore strings.
     */
    private static List<String> colorLoreList(List<String> lore) {
        if (lore == null) return null;

        List<String> coloredLore = new ArrayList<>(lore.size());
        for (String line : lore) {
            coloredLore.add(translateColorCodes(line));
        }
        return coloredLore;
    }

    /**
     * Compares two lore lists for equality.
     * This method is optimized for performance with early returns.
     */
    private static boolean areLoreListsEqual(List<String> lore1, List<String> lore2) {
        if (lore1 == lore2) return true;  // Same instance
        if (lore1 == null || lore2 == null) return false;

        int size = lore1.size();
        if (size != lore2.size()) {
            return false;
        }

        for (int i = 0; i < size; i++) {
            String s1 = lore1.get(i);
            String s2 = lore2.get(i);
            if (s1 == s2) continue;  // Same instance
            if (s1 == null || s2 == null) return false;
            if (!s1.equals(s2)) return false;
        }

        return true;
    }

    /**
     * Compares two ItemStacks for equality, focusing on display name and lore.
     * This helps avoid unnecessary item updates in the GUI.
     *
     * @param item1 First ItemStack to compare
     * @param item2 Second ItemStack to compare
     * @return true if items have the same material, display name and lore
     */
    public static boolean areItemsEqual(ItemStack item1, ItemStack item2) {
        if (item1 == item2) return true;  // Same instance
        if (item1 == null || item2 == null) return false;

        // Check material first (fast check)
        if (item1.getType() != item2.getType()) return false;

        // Get item metas
        ItemMeta meta1 = item1.getItemMeta();
        ItemMeta meta2 = item2.getItemMeta();

        // If both have no meta, they're equal
        if (meta1 == null && meta2 == null) return true;
        // If only one has meta, they're not equal
        if (meta1 == null || meta2 == null) return false;

        // Check display name
        if (meta1.hasDisplayName() != meta2.hasDisplayName()) return false;
        if (meta1.hasDisplayName() && !meta1.getDisplayName().equals(meta2.getDisplayName())) return false;

        // Check lore
        return areLoreListsEqual(meta1.getLore(), meta2.getLore());
    }

    /**
     * Clears the color translation cache
     */
    public static void clearCache() {
        COLOR_TRANSLATION_CACHE.clear();
        lastCacheCleanup = System.currentTimeMillis();
        // LOGGER.info("Color translation cache cleared");
    }

    /**
     * Get cache statistics for debugging/monitoring
     * @return Map with cache statistics
     */
    public static Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("cacheSize", COLOR_TRANSLATION_CACHE.size());
        stats.put("hits", cacheHits.get());
        stats.put("misses", cacheMisses.get());
        stats.put("hitRatio", calculateHitRatio());
        stats.put("maxSize", MAX_CACHE_SIZE);
        stats.put("lastCleanup", lastCacheCleanup);
        return stats;
    }

    /**
     * Calculate the cache hit ratio (hits divided by total access attempts)
     * @return Hit ratio as a percentage
     */
    private static double calculateHitRatio() {
        int hits = cacheHits.get();
        int total = hits + cacheMisses.get();
        return total > 0 ? (double)hits / total * 100.0 : 0.0;
    }

    /**
     * Reset cache statistics counters
     */
    public static void resetStats() {
        cacheHits.set(0);
        cacheMisses.set(0);
    }
}