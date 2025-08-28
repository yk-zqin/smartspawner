package github.nighter.smartspawner.commands.list;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.list.enums.FilterOption;
import github.nighter.smartspawner.commands.list.enums.SortOption;
import github.nighter.smartspawner.commands.list.holders.SpawnerManagementHolder;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.gui.main.SpawnerMenuUI;
import github.nighter.smartspawner.spawner.gui.stacker.SpawnerStackerUI;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SpawnerManagementHandler implements Listener {
    private final SmartSpawner plugin;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;
    private final ListSubCommand listSubCommand;
    private final SpawnerMenuUI spawnerMenuUI;
    private final SpawnerStackerUI spawnerStackerUI;

    public SpawnerManagementHandler(SmartSpawner plugin, ListSubCommand listSubCommand) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
        this.listSubCommand = listSubCommand;
        this.spawnerMenuUI = plugin.getSpawnerMenuUI();
        this.spawnerStackerUI = plugin.getSpawnerStackerUI();
    }

    @EventHandler
    public void onSpawnerManagementClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SpawnerManagementHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;

        String spawnerId = holder.getSpawnerId();
        String worldName = holder.getWorldName();
        int listPage = holder.getListPage();

        SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);
        if (spawner == null) {
            messageService.sendMessage(player, "spawner_not_found");
            return;
        }

        int slot = event.getSlot();
        ItemStack clickedItem = event.getCurrentItem();

        switch (slot) {
            case 10 -> handleTeleport(player, spawner);
            case 12 -> handleOpenSpawner(player, spawner);
            case 14 -> handleChangeEntity(player, spawner);
            case 16 -> handleRemoveSpawner(player, spawner, worldName, listPage);
            case 19, 20 -> handleStackManagement(player, spawner);
            case 22 -> handleBack(player, worldName, listPage);
        }
    }

    private void handleTeleport(Player player, SpawnerData spawner) {
        if (!player.hasPermission("smartspawner.list.teleport")) {
            messageService.sendMessage(player, "no_permission_teleport");
            return;
        }

        Location loc = spawner.getSpawnerLocation().clone().add(0.5, 1, 0.5);
        player.teleportAsync(loc);
        messageService.sendMessage(player, "teleported_to_spawner");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.closeInventory();
    }

    private void handleOpenSpawner(Player player, SpawnerData spawner) {
        if (!player.hasPermission("smartspawner.use")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        spawnerMenuUI.openSpawnerMenu(player, spawner, false);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleChangeEntity(Player player, SpawnerData spawner) {
        if (!player.hasPermission("smartspawner.changetype")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        // Simple entity cycling through common mob types
        EntityType currentType = spawner.getEntityType();
        EntityType nextType = getNextEntityType(currentType);
        
        // Update the spawner entity type directly
        spawner.setEntityType(nextType);
        
        // Update the physical spawner block
        Location loc = spawner.getSpawnerLocation();
        if (loc.getBlock().getState() instanceof org.bukkit.block.CreatureSpawner creatureSpawner) {
            creatureSpawner.setSpawnedType(nextType);
            creatureSpawner.update();
        }
        
        // Track interaction
        spawner.updateLastInteractedPlayer(player.getName());
        
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", nextType.name().toLowerCase().replace("_", " "));
        messageService.sendMessage(player, "spawner_management.entity_changed", placeholders);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }
    
    /**
     * Cycles through common entity types
     */
    private EntityType getNextEntityType(EntityType current) {
        EntityType[] commonTypes = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER,
            EntityType.ENDERMAN, EntityType.BLAZE, EntityType.COW, EntityType.PIG,
            EntityType.CHICKEN, EntityType.SHEEP, EntityType.IRON_GOLEM, EntityType.VILLAGER
        };
        
        for (int i = 0; i < commonTypes.length; i++) {
            if (commonTypes[i] == current) {
                return commonTypes[(i + 1) % commonTypes.length];
            }
        }
        
        // If current type is not in the list, return the first one
        return commonTypes[0];
    }

    private void handleRemoveSpawner(Player player, SpawnerData spawner, String worldName, int listPage) {
        if (!player.hasPermission("smartspawner.remove")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        // Remove the spawner block and data
        Location loc = spawner.getSpawnerLocation();
        if (loc.getBlock().getType() == Material.SPAWNER) {
            loc.getBlock().setType(Material.AIR);
        }

        // Remove from manager and save
        spawnerManager.removeSpawner(spawner.getSpawnerId());

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", spawner.getSpawnerId());
        messageService.sendMessage(player, "spawner_management.removed", placeholders);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

        // Return to spawner list
        handleBack(player, worldName, listPage);
    }

    private void handleStackManagement(Player player, SpawnerData spawner) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        spawnerStackerUI.openStackerGui(player, spawner);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    private void handleBack(Player player, String worldName, int listPage) {
        // Get the user's current preferences for filter and sort
        FilterOption filter = FilterOption.ALL; // Default
        SortOption sort = SortOption.DEFAULT; // Default
        
        // Try to get saved preferences
        try {
            filter = listSubCommand.getUserFilter(player, worldName);
            sort = listSubCommand.getUserSort(player, worldName);
        } catch (Exception ignored) {
            // Use defaults if loading fails
        }

        listSubCommand.openSpawnerListGUI(player, worldName, listPage, filter, sort);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }
}