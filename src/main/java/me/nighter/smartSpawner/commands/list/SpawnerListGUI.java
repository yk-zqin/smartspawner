package me.nighter.smartSpawner.commands.list;

import me.nighter.smartSpawner.*;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;

import me.nighter.smartSpawner.spawner.properties.SpawnerManager;
import me.nighter.smartSpawner.utils.ConfigManager;
import me.nighter.smartSpawner.utils.LanguageManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpawnerListGUI implements Listener {
    private final ConfigManager configManager;
    private final LanguageManager languageManager;
    private final SpawnerManager spawnerManager;
    private final ListCommand listCommand;
    private static final Set<Material> SPAWNER_MATERIALS = EnumSet.of(
            Material.PLAYER_HEAD, Material.SPAWNER, Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD, Material.PIGLIN_HEAD
    );

    public SpawnerListGUI(SmartSpawner plugin) {
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.spawnerManager = plugin.getSpawnerManager();
        this.listCommand = new ListCommand(plugin);
    }

    @EventHandler
    public void onWorldSelectionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ListCommand.WorldSelectionHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.list")) {
            languageManager.sendMessage(player, "no-permission");
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        switch (event.getSlot()) {
            case 11 -> listCommand.openSpawnerListGUI(player, "world", 1);
            case 13 -> listCommand.openSpawnerListGUI(player, "world_nether", 1);
            case 15 -> listCommand.openSpawnerListGUI(player, "world_the_end", 1);
        }
    }

    @EventHandler
    public void onSpawnerListClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ListCommand.SpawnerListHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.list")) {
            languageManager.sendMessage(player, "no-permission");
            return;
        }
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        // Navigation handling
        if (event.getSlot() == 45 && holder.getCurrentPage() > 1) {
            listCommand.openSpawnerListGUI(player, holder.getWorldName(), holder.getCurrentPage() - 1);
        } else if (event.getSlot() == 53 && holder.getCurrentPage() < holder.getTotalPages()) {
            listCommand.openSpawnerListGUI(player, holder.getWorldName(), holder.getCurrentPage() + 1);
        }
        // Back button
        else if (event.getSlot() == 49) {
            listCommand.openWorldSelectionGUI(player);
        }
        // Spawner click handling
        else if (SPAWNER_MATERIALS.contains(event.getCurrentItem().getType())) {
            handleSpawnerClick(event);
        }
    }

    private void handleSpawnerClick(InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String displayName = clickedItem.getItemMeta().getDisplayName();
        if (displayName == null) return;

        // Extract spawner ID from display name, now handling alphanumeric IDs
        String patternString = languageManager.getMessage("spawner-list.spawner-item.id_pattern");
        configManager.debug("Pattern string: " + patternString);
        Pattern pattern = Pattern.compile(patternString);
        configManager.debug("Pattern: " + pattern);
        Matcher matcher = pattern.matcher(ChatColor.stripColor(displayName));
        configManager.debug("Matcher: " + ChatColor.stripColor(displayName));

        if (matcher.find()) {
            String spawnerId = matcher.group(1);
            configManager.debug("Clicked spawner ID: " + spawnerId);
            SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);

            if (spawner != null) {
                Player player = (Player) event.getWhoClicked();
                Location loc = spawner.getSpawnerLocation();
                player.teleport(loc);
                languageManager.sendMessage(player, "messages.teleported",
                        "%spawnerId%", spawnerId);
            } else {
                Player player = (Player) event.getWhoClicked();
                languageManager.sendMessage(player, "messages.not-found");
            }
        } else {
            Player player = (Player) event.getWhoClicked();
            languageManager.sendMessage(player, "messages.not-found");
        }
    }
}