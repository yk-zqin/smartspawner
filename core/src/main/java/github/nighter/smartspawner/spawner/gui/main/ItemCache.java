package github.nighter.smartspawner.spawner.gui.main;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cache for ItemStacks to improve performance of frequently accessed items
 */
public class ItemCache {
    // Statistics for monitoring
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);

    // The actual cache using Guava's CacheBuilder
    private final Cache<String, ItemStack> itemCache;

    /**
     * Create a new ItemCache with default settings
     */
    public ItemCache() {
        this(500, 60); // Default: 500 items, 60 second expiry
    }

    /**
     * Create a new ItemCache with custom settings
     *
     * @param maxSize Maximum number of items to cache
     * @param expirySeconds Time in seconds after which items expire
     */
    public ItemCache(int maxSize, int expirySeconds) {
        itemCache = CacheBuilder.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expirySeconds, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Put an item in the cache
     *
     * @param key Cache key
     * @param item Item to cache
     */
    public void put(String key, ItemStack item) {
        if (key == null || item == null) return;
        itemCache.put(key, item.clone()); // Store a clone to prevent modification
    }

    /**
     * Get an item from the cache if present
     * Also tracks hit/miss statistics
     *
     * @param key Cache key
     * @return The cached item or null if not found
     */
    public ItemStack getIfPresent(String key) {
        if (key == null) return null;

        ItemStack item = itemCache.getIfPresent(key);
        if (item != null) {
            cacheHits.incrementAndGet();
            return item.clone(); // Return a clone to prevent modification of cached item
        }

        cacheMisses.incrementAndGet();
        return null;
    }

    /**
     * Clear the entire cache
     */
    public void clear() {
        itemCache.invalidateAll();
    }

    /**
     * Get current hit count
     */
    public int getHitCount() {
        return cacheHits.get();
    }

    /**
     * Get current miss count
     */
    public int getMissCount() {
        return cacheMisses.get();
    }

    /**
     * Get current cache size
     */
    public long getSize() {
        return itemCache.size();
    }

    /**
     * Calculate hit rate as a percentage
     */
    public double getHitRate() {
        int hits = cacheHits.get();
        int total = hits + cacheMisses.get();
        return total > 0 ? (double) hits / total * 100 : 0;
    }
}