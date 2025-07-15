package github.nighter.smartspawner.nms;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionInitializer {
    private final SmartSpawner plugin;
    private final String serverVersion;
    private final String basePackage = "github.nighter.smartspawner";

    private final String[][] components = {
            {"Materials", "MaterialInitializer"},
            {"Particles", "ParticleInitializer"},
            {"Textures", "TextureInitializer"},
            {"Spawners", "SpawnerInitializer"}
    };

    // Supported versions in descending order (newest first)
    private final List<Version> supportedVersions = Arrays.asList(
            new Version(1, 21, 6, "v1_21_6"),
            new Version(1, 21, 4, "v1_21_4"),
            new Version(1, 21, 0, "v1_21"),
            new Version(1, 20, 0, "v1_20")
    );

    public VersionInitializer(SmartSpawner plugin) {
        this.plugin = plugin;
        this.serverVersion = Bukkit.getServer().getBukkitVersion();
    }

    public void initialize() {
        String versionPath = determineVersionPath();
        if (versionPath == null) {
            throw new IllegalStateException("Unsupported server version: " + serverVersion);
        }

        plugin.debug("Detected server version: " + serverVersion + ", using version path: " + versionPath);
        initializeComponentsForVersion(versionPath);
    }

    private String determineVersionPath() {
        plugin.debug("Checking server version: " + serverVersion);

        Version currentVersion = parseVersion(serverVersion);
        if (currentVersion == null) {
            plugin.debug("Failed to parse version: " + serverVersion);
            return null;
        }

        // Find the best matching version (highest supported version that's <= current version)
        for (Version supportedVersion : supportedVersions) {
            if (currentVersion.isGreaterThanOrEqualTo(supportedVersion)) {
                plugin.debug("Matched version " + currentVersion + " -> " + supportedVersion.packageName);
                return supportedVersion.packageName;
            }
        }

        plugin.debug("No matching version found for: " + serverVersion);
        return null;
    }

    private Version parseVersion(String versionString) {
        // Extract version numbers from strings like "1.21.6-R0.1-SNAPSHOT"
        Pattern pattern = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
        Matcher matcher = pattern.matcher(versionString);

        if (matcher.find()) {
            int major = Integer.parseInt(matcher.group(1));
            int minor = Integer.parseInt(matcher.group(2));
            int patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            return new Version(major, minor, patch, null);
        }

        return null;
    }

    private void initializeComponentsForVersion(String versionPath) {
        for (String[] component : components) {
            initializeComponent(component[0], component[1], versionPath);
        }
    }

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

    private static class Version {
        final int major;
        final int minor;
        final int patch;
        final String packageName;

        Version(int major, int minor, int patch, String packageName) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.packageName = packageName;
        }

        boolean isGreaterThanOrEqualTo(Version other) {
            if (this.major != other.major) return this.major >= other.major;
            if (this.minor != other.minor) return this.minor >= other.minor;
            return this.patch >= other.patch;
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }
}