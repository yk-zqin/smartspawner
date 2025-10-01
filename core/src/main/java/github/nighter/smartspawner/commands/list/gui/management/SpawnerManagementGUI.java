package github.nighter.smartspawner.commands.list.gui.management;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.spawner.properties.SpawnerManager;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpawnerManagementGUI {
    private static final int INVENTORY_SIZE = 27;
    private static final int TELEPORT_SLOT = 10;
    private static final int OPEN_SPAWNER_SLOT = 12;
    private static final int STACK_SLOT = 14;
    private static final int REMOVE_SLOT = 16;
    private static final int BACK_SLOT = 26;
    
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final SpawnerManager spawnerManager;

    public SpawnerManagementGUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.spawnerManager = plugin.getSpawnerManager();
    }

    public void openManagementMenu(Player player, String spawnerId, String worldName, int listPage) {
        SpawnerData spawner = spawnerManager.getSpawnerById(spawnerId);
        if (spawner == null) {
            messageService.sendMessage(player, "spawner_not_found");
            return;
        }

        String title = languageManager.getGuiTitle("spawner_management.title");

        Inventory inv = Bukkit.createInventory(
            new SpawnerManagementHolder(spawnerId, worldName, listPage),
            INVENTORY_SIZE, title
        );

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

        // Create action items with better materials and positioning
        createActionItem(inv, TELEPORT_SLOT, "spawner_management.teleport", Material.ENDER_PEARL);
        createActionItem(inv, OPEN_SPAWNER_SLOT, "spawner_management.open_spawner", Material.ENDER_EYE);
        createActionItem(inv, STACK_SLOT, "spawner_management.stack", Material.SPAWNER);
        createActionItem(inv, REMOVE_SLOT, "spawner_management.remove", Material.BARRIER);
        createActionItem(inv, BACK_SLOT, "spawner_management.back", Material.RED_STAINED_GLASS_PANE);

        // Fill empty slots with glass panes
        // fillEmptySlots(inv);

        player.openInventory(inv);
    }

    private void createActionItem(Inventory inv, int slot, String langKey, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(languageManager.getGuiItemName(langKey + ".name"));
            List<String> lore = Arrays.asList(languageManager.getGuiItemLore(langKey + ".lore"));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

//    private void fillEmptySlots(Inventory inv) {
//        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
//        ItemMeta meta = glass.getItemMeta();
//        if (meta != null) {
//            meta.setDisplayName(" ");
//            glass.setItemMeta(meta);
//        }
//
//        for (int i = 0; i < inv.getSize(); i++) {
//            if (inv.getItem(i) == null) {
//                inv.setItem(i, glass);
//            }
//        }
//    }
}