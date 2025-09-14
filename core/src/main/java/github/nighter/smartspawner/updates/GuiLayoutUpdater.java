package github.nighter.smartspawner.updates;

import github.nighter.smartspawner.SmartSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
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
}