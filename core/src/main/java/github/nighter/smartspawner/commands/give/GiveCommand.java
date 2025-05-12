package github.nighter.smartspawner.commands.give;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.nms.SpawnerWrapper;
import github.nighter.smartspawner.spawner.item.SpawnerItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class GiveCommand {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;
    private final SpawnerItemFactory spawnerItemFactory;
    private final List<String> supportedMobs;
    private final int MAX_AMOUNT = 6400;

    public GiveCommand(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
        this.spawnerItemFactory = plugin.getSpawnerItemFactory();
        this.supportedMobs = SpawnerWrapper.SUPPORTED_MOBS;
    }

    public boolean executeCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smartspawner.give")) {
            messageService.sendMessage(sender, "no_permission");
            return true;
        }

        // Check if this is a vanilla spawner command
        boolean isVanillaSpawner = args.length > 0 && args[0].equalsIgnoreCase("giveVanillaSpawner");

        // Check number of arguments (currently requires 3 or 4 arguments: give, player, mobtype, [amount])
        if (args.length < 3 || args.length > 4) {
            if (isVanillaSpawner) {
                messageService.sendMessage(sender, "command_give_vanilla_usage");
            } else {
                messageService.sendMessage(sender, "command_give_usage");
            }
            return true;
        }

        // Get target player
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageService.sendMessage(sender, "command_give_player_not_found");
            return true;
        }

        String mobType = args[2].toUpperCase();
        if (!supportedMobs.contains(mobType)) {
            messageService.sendMessage(sender, "command_give_invalid_mob_type");
            return true;
        }

        // Handle amount
        int amount = parseAmount(sender, args);
        if (amount <= 0) return true;

        try {
            EntityType entityType = EntityType.valueOf(mobType);

            // Create the appropriate spawner type
            ItemStack spawner;
            String messageKey = "command_give_spawner_";

            if (isVanillaSpawner) {
                // Create vanilla spawner
                spawner = spawnerItemFactory.createVanillaSpawnerItem(entityType, amount);
            } else {
                // Create smart spawner
                spawner = spawnerItemFactory.createSpawnerItem(entityType, amount);
            }

            String entityName = languageManager.getFormattedMobName(entityType);
            String smallCapsEntityName = languageManager.getSmallCaps(entityName);

            // Check inventory space
            HashMap<Integer, ItemStack> leftoverItems = target.getInventory().addItem(spawner);

            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            placeholders.put("entity", entityName);
            placeholders.put("ᴇɴᴛɪᴛʏ", smallCapsEntityName);
            placeholders.put("amount", String.valueOf(amount));

            HashMap<String, String> targetPlaceholders = new HashMap<>();
            targetPlaceholders.put("amount", String.valueOf(amount));
            targetPlaceholders.put("entity", entityName);
            targetPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsEntityName);

            if (!leftoverItems.isEmpty()) {
                // Drop remaining items on the ground if inventory is full
                for (ItemStack leftover : leftoverItems.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
                messageService.sendMessage(target, "command_give_inventory_full");
                messageService.sendMessage(target, messageKey + "received", targetPlaceholders);
                messageService.sendMessage(sender, messageKey + "given", placeholders);
            } else {
                messageService.sendMessage(target, messageKey + "received", targetPlaceholders);
                messageService.sendMessage(sender, messageKey + "given", placeholders);
            }

            return true;
        } catch (IllegalArgumentException e) {
            messageService.sendMessage(sender, "command_give_invalid_mob_type");
            plugin.getLogger().warning("Error creating spawner: " + e.getMessage());
            return true;
        }
    }

    // For console
    public boolean executeCommand(String[] args) {
        // Check if this is a vanilla spawner command
        boolean isVanillaSpawner = args.length > 0 && args[0].equalsIgnoreCase("giveVanillaSpawner");

        if (args.length < 3 || args.length > 4) {
            if (isVanillaSpawner) {
                messageService.sendConsoleMessage("command_give_vanilla_usage");
            } else {
                messageService.sendConsoleMessage("command_give_usage");
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageService.sendConsoleMessage("command_give_player_not_found");
            return true;
        }

        String mobType = args[2].toUpperCase();
        if (!supportedMobs.contains(mobType)) {
            messageService.sendConsoleMessage("command_give_invalid_mob_type");
            return true;
        }

        // Parse amount
        int amount = 1;
        if (args.length == 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0) {
                    messageService.sendConsoleMessage("command_give_invalid_amount");
                    return true;
                }
                if (amount > MAX_AMOUNT) {
                    messageService.sendConsoleMessage("command_give_amount_too_large",
                            Collections.singletonMap("max", String.valueOf(MAX_AMOUNT)));
                    return true;
                }
            } catch (NumberFormatException e) {
                messageService.sendConsoleMessage("command_give_invalid_amount");
                return true;
            }
        }

        try {
            EntityType entityType = EntityType.valueOf(mobType);

            // Create the appropriate spawner type
            ItemStack spawner;
            String messageKey = "command_give_spawner_";

            if (isVanillaSpawner) {
                // Create vanilla spawner for console command
                spawner = spawnerItemFactory.createVanillaSpawnerItem(entityType, amount);
            } else {
                // Create smart spawner
                spawner = spawnerItemFactory.createSpawnerItem(entityType, amount);
            }

            String entityName = languageManager.getFormattedMobName(entityType);
            String smallCapsEntityName = languageManager.getSmallCaps(entityName);

            // Check inventory space
            HashMap<Integer, ItemStack> leftoverItems = target.getInventory().addItem(spawner);

            HashMap<String, String> placeholders = new HashMap<>();
            placeholders.put("player", target.getName());
            placeholders.put("entity", entityName);
            placeholders.put("ᴇɴᴛɪᴛʏ", smallCapsEntityName);
            placeholders.put("amount", String.valueOf(amount));

            HashMap<String, String> targetPlaceholders = new HashMap<>();
            targetPlaceholders.put("amount", String.valueOf(amount));
            targetPlaceholders.put("entity", entityName);
            targetPlaceholders.put("ᴇɴᴛɪᴛʏ", smallCapsEntityName);


            if (!leftoverItems.isEmpty()) {
                // Drop remaining items on the ground if inventory is full
                for (ItemStack leftover : leftoverItems.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                }
                messageService.sendMessage(target, "command_give_inventory_full");
                target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                messageService.sendConsoleMessage(messageKey + "given_dropped", placeholders);
            } else {
                messageService.sendMessage(target, messageKey + "received", targetPlaceholders);
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                messageService.sendConsoleMessage(messageKey + "given", placeholders);
            }

            return true;
        } catch (IllegalArgumentException e) {
            messageService.sendConsoleMessage("command_give_invalid_mob_type");
            plugin.getLogger().warning("Error creating spawner: " + e.getMessage());
            return true;
        }
    }

    private int parseAmount(CommandSender sender, String[] args) {
        int amount = 1;
        if (args.length == 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0) {
                    messageService.sendMessage(sender, "command_give_invalid_amount");
                    return -1;
                }
                if (amount > MAX_AMOUNT) {
                    messageService.sendMessage(sender, "command_give_amount_too_large",
                            Collections.singletonMap("max", String.valueOf(MAX_AMOUNT)));
                    return -1;
                }
            } catch (NumberFormatException e) {
                messageService.sendMessage(sender, "command_give_invalid_amount");
                return -1;
            }
        }
        return amount;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smartspawner.give")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            String input = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 3) {
            String input = args[2].toLowerCase();
            return supportedMobs.stream()
                    .map(String::toLowerCase)
                    .filter(mob -> mob.startsWith(input))
                    .collect(Collectors.toList());
        }

        if (args.length == 4) {
            String input = args[3].toLowerCase();
            return Arrays.asList("1", "16", "32", "64").stream()
                    .filter(amount -> amount.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}