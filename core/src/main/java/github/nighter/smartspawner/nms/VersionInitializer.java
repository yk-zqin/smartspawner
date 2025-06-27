package github.nighter.smartspawner.nms;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles version-specific initialization for the SmartSpawner plugin.
 * This class manages loading appropriate implementation classes based on the server version.
 *
 */
public class VersionInitializer {
    private final SmartSpawner plugin;
    private final String serverVersion;
    private final String basePackage = "github.nighter.smartspawner";

    // Component definitions - each entry contains component type and initializer class name
    private final String[][] components = {
            {"Particles", "ParticleInitializer"},
            {"Textures", "TextureInitializer"},
            {"Spawners", "SpawnerInitializer"}
    };

    /**
     * Creates a new VersionInitializer for the specified plugin.
     *
     * @param plugin The plugin instance
     */
    public VersionInitializer(SmartSpawner plugin) {
        this.plugin = plugin;
        this.serverVersion = Bukkit.getServer().getBukkitVersion();
    }

    /**
     * Initializes all version-specific components.
     *
     * @throws IllegalStateException if the server version is not supported
     */
    public void initialize() {
        String versionPath = determineVersionPath();
        if (versionPath == null) {
            throw new IllegalStateException("Unsupported server version: " + serverVersion);
        }

        plugin.debug("Detected server version: " + serverVersion + ", using version path: " + versionPath);
        initializeComponentsForVersion(versionPath);
    }

    /**
     * Determines the appropriate version package path based on the server version.
     * Uses LinkedHashMap to maintain insertion order for proper version matching priority.
     *
     * @return The version package path, or null if not supported
     */
    private String determineVersionPath() {
        // Define supported versions and their package names
        // Order matters: more specific versions should come first
        Map<String, String> supportedVersions = new LinkedHashMap<>();
        supportedVersions.put("1.21.6", "v1_21_6");
        supportedVersions.put("1.21.4", "v1_21_4");
        supportedVersions.put("1.21", "v1_21");
        supportedVersions.put("1.20", "v1_20");

        plugin.debug("Checking server version: " + serverVersion);

        // Find matching version path - check most specific versions first
        for (Map.Entry<String, String> entry : supportedVersions.entrySet()) {
            if (serverVersion.contains(entry.getKey())) {
                plugin.debug("Matched version " + entry.getKey() + " -> " + entry.getValue());
                return entry.getValue();
            }
        }

        plugin.debug("No matching version found for: " + serverVersion);
        return null;
    }

    /**
     * Initializes all components for the detected version.
     *
     * @param versionPath The version package path
     */
    private void initializeComponentsForVersion(String versionPath) {
        for (String[] component : components) {
            initializeComponent(component[0], component[1], versionPath);
        }
    }

    /**
     * Initializes a specific component for the detected version.
     *
     * @param componentName The name of the component (for logging)
     * @param initializerClass The class name of the initializer
     * @param versionPath The version package path
     */
    private void initializeComponent(String componentName, String initializerClass, String versionPath) {
        try {
            String className = String.format("%s.%s.%s", basePackage, versionPath, initializerClass);
            Class<?> clazz = Class.forName(className);
            Method initMethod = clazz.getMethod("init");
            initMethod.invoke(null);
        } catch (Exception e) {
            plugin.getLogger().severe(String.format("Failed to initialize %s for version %s: %s",
                    componentName, serverVersion, e.getMessage()));
            plugin.debug("Stack trace for " + componentName + " initialization failure: " + e.toString());
            throw new RuntimeException("Failed to initialize " + componentName, e);
        }
    }
}