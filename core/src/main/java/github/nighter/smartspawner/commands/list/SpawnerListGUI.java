package github.nighter.smartspawner.commands.list;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.Scheduler;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SpawnerListGUI implements Listener {
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final ListCommand listCommand;
    private static final Set<Material> SPAWNER_MATERIALS = EnumSet.of(
            Material.PLAYER_HEAD, Material.SPAWNER, Material.ZOMBIE_HEAD,
            Material.SKELETON_SKULL, Material.WITHER_SKELETON_SKULL,
            Material.CREEPER_HEAD, Material.PIGLIN_HEAD
    );
    private static final String patternString = "#([A-Za-z0-9]+)";

    public SpawnerListGUI(SmartSpawner plugin) {
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.listCommand = new ListCommand(plugin);
    }

    @EventHandler
    public void onWorldSelectionClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ListCommand.WorldSelectionHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.list")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) return;

        String displayName = ChatColor.stripColor(clickedItem.getItemMeta().getDisplayName());

        // Check for original layout slots first (for backward compatibility)
        if (event.getSlot() == 11 && displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.overworld.name")))) {
            listCommand.openSpawnerListGUI(player, "world", 1);
            return;
        } else if (event.getSlot() == 13 && displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.nether.name")))) {
            listCommand.openSpawnerListGUI(player, "world_nether", 1);
            return;
        } else if (event.getSlot() == 15 && displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.end.name")))) {
            listCommand.openSpawnerListGUI(player, "world_the_end", 1);
            return;
        }

        // For custom layout or any other slots, determine world by name
        if (displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.overworld.name")))) {
            listCommand.openSpawnerListGUI(player, "world", 1);
        } else if (displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.nether.name")))) {
            listCommand.openSpawnerListGUI(player, "world_nether", 1);
        } else if (displayName.equals(ChatColor.stripColor(languageManager.getGuiTitle("world_buttons.end.name")))) {
            listCommand.openSpawnerListGUI(player, "world_the_end", 1);
        } else {
            // For custom worlds, find the matching world
            for (World world : Bukkit.getWorlds()) {
                String worldDisplayName = formatWorldName(world.getName());

                if (spawnerManager.countSpawnersInWorld(world.getName()) > 0 &&
                        displayName.contains(worldDisplayName)) {
                    listCommand.openSpawnerListGUI(player, world.getName(), 1);
                    break;
                }
            }
        }
    }

    // Helper method to format world name (same as in ListCommand)
    private String formatWorldName(String worldName) {
        // Convert something like "my_custom_world" to "My Custom World"
        return Arrays.stream(worldName.replace('_', ' ').split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    @EventHandler
    public void onSpawnerListClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ListCommand.SpawnerListHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("smartspawner.list")) {
            messageService.sendMessage(player, "no_permission");
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
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(ChatColor.stripColor(displayName));

        if (matcher.find()) {
            String spawnerId = matcher.group(1);
            SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);

            if (spawner != null) {
                Player player = (Player) event.getWhoClicked();
                Location loc = spawner.getSpawnerLocation();

                // Use the Scheduler to handle teleportation properly for both Bukkit and Folia
                player.teleportAsync(loc).thenAccept(success -> {
                    if (success) {
                        Scheduler.runEntityTask(player, () -> {
                            messageService.sendMessage(player, "teleported_to_spawner");
                        });
                    }
                });
            } else {
                Player player = (Player) event.getWhoClicked();
                messageService.sendMessage(player, "spawner_not_found");
            }
        } else {
            Player player = (Player) event.getWhoClicked();
            messageService.sendMessage(player, "spawner_not_found");
        }
    }
}