package github.nighter.smartspawner.hooks.bedrock;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * Hook for Floodgate API to check if players are Bedrock Edition players.
 * This provides a centralized way to check Bedrock players without repeatedly
 * accessing the FloodgateApi instance.
 */
public class FloodgateHook {
    private final SmartSpawner plugin;
    private FloodgateApi floodgateApi;
    private boolean enabled = false;

    public FloodgateHook(SmartSpawner plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        try {
            // Check if Floodgate plugin is available
            if (plugin.getServer().getPluginManager().getPlugin("floodgate") == null) {
                plugin.debug("Floodgate plugin not found");
                return;
            }

            // Get FloodgateApi instance
            floodgateApi = FloodgateApi.getInstance();
            if (floodgateApi == null) {
                plugin.getLogger().warning("Failed to get FloodgateApi instance");
                return;
            }

            enabled = true;
            plugin.getLogger().info("Floodgate integration initialized successfully!");
        } catch (NoClassDefFoundError | NullPointerException e) {
            plugin.debug("Floodgate API not available: " + e.getMessage());
            enabled = false;
        } catch (Exception e) {
            plugin.getLogger().warning("Error initializing Floodgate integration: " + e.getMessage());
            enabled = false;
        }
    }

    /**
     * Check if the Floodgate integration is enabled and available.
     *
     * @return true if Floodgate is available, false otherwise
     */
    public boolean isEnabled() {
        return enabled && floodgateApi != null;
    }

    /**
     * Check if a player is a Bedrock Edition player.
     *
     * @param player The player to check
     * @return true if the player is a Bedrock player, false otherwise
     */
    public boolean isBedrockPlayer(Player player) {
        if (!isEnabled() || player == null) {
            return false;
        }

        try {
            return floodgateApi.isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            plugin.debug("Error checking if player is Bedrock: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a UUID belongs to a Bedrock Edition player.
     *
     * @param uuid The UUID to check
     * @return true if the UUID belongs to a Bedrock player, false otherwise
     */
    public boolean isBedrockPlayer(UUID uuid) {
        if (!isEnabled() || uuid == null) {
            return false;
        }

        try {
            return floodgateApi.isFloodgatePlayer(uuid);
        } catch (Exception e) {
            plugin.debug("Error checking if UUID is Bedrock: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the FloodgateApi instance.
     * Only use this if you need direct access to the API.
     *
     * @return The FloodgateApi instance, or null if not available
     */
    public FloodgateApi getApi() {
        return floodgateApi;
    }
}
