package me.nighter.smartSpawner;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SpawnerStackHandler {
    private final SmartSpawner plugin;
    private final int maxStackSize;
    private ConfigManager configManager;
    private LanguageManager languageManager;

    public SpawnerStackHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.languageManager = plugin.getLanguageManager();
        this.maxStackSize = configManager.getMaxStackSize();
    }

    public boolean handleSpawnerStack(Player player, SpawnerData targetSpawner, ItemStack itemInHand, boolean stackAll) {
        if (itemInHand.getType() != Material.SPAWNER) {
            return false;
        }

        if (!player.hasPermission("smartspawner.stack")) {
            languageManager.sendMessage(player, "no-permission");
            return false;
        }

        int currentStack = targetSpawner.getStackSize();
        if (currentStack >= maxStackSize) {
            languageManager.sendMessage(player, "messages.stack-full");
            return false;
        }

        int itemAmount = itemInHand.getAmount();
        int spaceLeft = maxStackSize - currentStack;

        int amountToStack;
        if (stackAll) {
            amountToStack = Math.min(spaceLeft, itemAmount);
        } else {
            amountToStack = 1;
        }

        int newStack = currentStack + amountToStack;
        targetSpawner.setStackSize(newStack);

        if (player.getGameMode() != GameMode.CREATIVE) {
            if (itemAmount <= amountToStack) {
                player.getInventory().setItemInMainHand(null);
            } else {
                itemInHand.setAmount(itemAmount - amountToStack);
            }
        }

        showStackAnimation(targetSpawner, newStack, player);
        return true;
    }

    private void showStackAnimation(SpawnerData spawner, int newStack, Player player) {
        Location loc = spawner.getSpawnerLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.HAPPY_VILLAGER,
                loc.clone().add(0.5, 0.5, 0.5),
                10, 0.3, 0.3, 0.3, 0);

        languageManager.sendMessage(player, "messages.hand-stack", "%amount%", String.valueOf(newStack));
    }
}
