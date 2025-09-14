package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.utils.Version;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

public class GuiLayoutUpdater {
    private static final String GUI_LAYOUT_VERSION_KEY = "gui_layout_version";
    private static final String GUI_LAYOUTS_DIR = "gui_layouts";
    private static final String[] LAYOUT_FILES = {"storage_gui.yml", "main_gui.yml"};
    private static final String[] LAYOUT_NAMES = {"default", "DonutSMP"};

    private final SmartSpawner plugin;
    private final String currentVersion;

    public GuiLayoutUpdater(SmartSpawner plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    public void checkAndUpdateLayouts() {
        File layoutsDir = new File(plugin.getDataFolder(), GUI_LAYOUTS_DIR);
        if (!layoutsDir.exists()) {
            return;
        }

        for (String layoutName : LAYOUT_NAMES) {
            File layoutDir = new File(layoutsDir, layoutName);
            if (!layoutDir.exists()) {
                continue;
            }

            for (String fileName : LAYOUT_FILES) {
                File layoutFile = new File(layoutDir, fileName);
                if (layoutFile.exists()) {
                    updateLayoutFile(layoutName, layoutFile, fileName);
                }
            }
        }
    }

    private void updateLayoutFile(String layoutName, File layoutFile, String fileName) {
        try {
            FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(layoutFile);
            String configVersionStr = currentConfig.getString(GUI_LAYOUT_VERSION_KEY, "0.0.0");

            if (configVersionStr.equals("0.0.0")) {
                plugin.debug("No version found in " + layoutName + "/" + fileName + ", creating default layout file with header");
                createDefaultLayoutFileWithHeader(layoutName, layoutFile, fileName);
                return;
            }

            Version configVersion = new Version(configVersionStr);
            Version pluginVersion = new Version(currentVersion);

            if (configVersion.compareTo(pluginVersion) >= 0) {
                return;
            }

            if (!configVersionStr.equals("0.0.0")) {
                plugin.debug("Updating " + layoutName + " " + fileName +
                        " from version " + configVersionStr + " to " + currentVersion);
            }

            Map<String, Object> userValues = flattenConfig(currentConfig);

            File tempFile = new File(layoutFile.getParent(), fileName.replace(".yml", "_new.yml"));
            createDefaultLayoutFileWithHeader(layoutName, tempFile, fileName);

            FileConfiguration newConfig = YamlConfiguration.loadConfiguration(tempFile);
            newConfig.set(GUI_LAYOUT_VERSION_KEY, currentVersion);

            boolean configDiffers = hasConfigDifferences(userValues, newConfig);

            if (configDiffers) {
                File backupFile = new File(layoutFile.getParent(), fileName.replace(".yml", "_backup_" + configVersionStr + ".yml"));
                Files.copy(layoutFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Layout backup created at " + backupFile.getName());
            } else {
                plugin.debug("No significant layout changes detected for " + layoutName + "/" + fileName + ", skipping backup creation");
            }

            applyUserValues(newConfig, userValues);
            newConfig.save(layoutFile);
            tempFile.delete();

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to update layout " + layoutName + "/" + fileName + ": " + e.getMessage(), e);
        }
    }

    private void createDefaultLayoutFileWithHeader(String layoutName, File destinationFile, String fileName) {
        try {
            String resourcePath = GUI_LAYOUTS_DIR + "/" + layoutName + "/" + fileName;
            plugin.saveResource(resourcePath, true);

            FileConfiguration config = YamlConfiguration.loadConfiguration(destinationFile);
            config.set(GUI_LAYOUT_VERSION_KEY, currentVersion);
            config.save(destinationFile);

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to create default layout file with header for " + layoutName + "/" + fileName, e);
        }
    }

    private boolean hasConfigDifferences(Map<String, Object> userValues, FileConfiguration newConfig) {
        Map<String, Object> newValues = flattenConfig(newConfig);

        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object userValue = entry.getValue();

            if (path.equals(GUI_LAYOUT_VERSION_KEY)) continue;

            if (!newValues.containsKey(path)) {
                return true;
            }

            Object newValue = newValues.get(path);
            if (!Objects.equals(userValue, newValue)) {
                return true;
            }
        }

        for (String path : newValues.keySet()) {
            if (!path.equals(GUI_LAYOUT_VERSION_KEY) && !userValues.containsKey(path)) {
                return true;
            }
        }

        return false;
    }

    private Map<String, Object> flattenConfig(ConfigurationSection config) {
        Map<String, Object> result = new HashMap<>();
        for (String key : config.getKeys(true)) {
            if (!config.isConfigurationSection(key)) {
                result.put(key, config.get(key));
            }
        }
        return result;
    }

    private void applyUserValues(FileConfiguration newConfig, Map<String, Object> userValues) {
        for (Map.Entry<String, Object> entry : userValues.entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();

            if (path.equals(GUI_LAYOUT_VERSION_KEY)) continue;

            if (newConfig.contains(path)) {
                newConfig.set(path, value);
            } else {
                plugin.getLogger().fine("Layout path '" + path + "' from old config no longer exists in new config");
            }
        }
    }
}