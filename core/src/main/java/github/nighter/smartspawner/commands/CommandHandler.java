package github.nighter.smartspawner.commands;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.give.GiveCommand;
import github.nighter.smartspawner.commands.hologram.HologramCommand;
import github.nighter.smartspawner.commands.list.ListCommand;
import github.nighter.smartspawner.commands.reload.ReloadCommand;
import github.nighter.smartspawner.language.MessageService;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final ReloadCommand reloadCommand;
    private final GiveCommand giveCommand;
    private final ListCommand listCommand;
    private final HologramCommand hologramCommand;
    private final SmartSpawner plugin;
    private final MessageService messageService;

    public CommandHandler(SmartSpawner plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.reloadCommand = plugin.getReloadCommand();
        this.giveCommand = plugin.getGiveCommand();
        this.listCommand = plugin.getListCommand();
        this.hologramCommand = plugin.getHologramCommand();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messageService.sendMessage(sender, "command_usage");
            return true;
        }

        // Handle console commands
        if (sender instanceof ConsoleCommandSender) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveVanillaSpawner")) {
                return giveCommand.executeCommand(args);
            } else if (args[0].equalsIgnoreCase("reload")) {
                return reloadCommand.onCommand(sender, command, label, args);
            } else {
                messageService.sendConsoleMessage("command_usage");
                return true;
            }
        }

        // Handle player commands
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return reloadCommand.onCommand(sender, command, label, args);
            case "give":
            case "givevanillaspawner":
                return giveCommand.executeCommand(sender, args);
            case "list":
                listCommand.openWorldSelectionGUI(player);
                return true;
            case "hologram":
                return hologramCommand.onCommand(sender, command, label, args);
            default:
                messageService.sendMessage(sender, "command_usage");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("smartspawner.reload")) {
                completions.add("reload");
            }
            if (sender.hasPermission("smartspawner.give")) {
                completions.add("give");
                completions.add("giveVanillaSpawner");
            }
            if (sender.hasPermission("smartspawner.list")) {
                completions.add("list");
            }
            if (sender.hasPermission("smartspawner.hologram")) {
                completions.add("hologram");
            }
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Handle subcommand tab completions
        if (args[0].equalsIgnoreCase("reload")) {
            return reloadCommand.onTabComplete(sender, command, alias, args);
        } else if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("giveVanillaSpawner")) {
            return giveCommand.tabComplete(sender, args);
        }

        return Collections.emptyList();
    }
}