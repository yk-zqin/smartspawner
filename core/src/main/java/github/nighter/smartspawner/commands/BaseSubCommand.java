package github.nighter.smartspawner.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import lombok.RequiredArgsConstructor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

@NullMarked
@RequiredArgsConstructor
public abstract class BaseSubCommand {
    protected final SmartSpawner plugin;

    // Abstract methods that must be implemented by subcommands
    public abstract String getName();
    public abstract String getPermission();
    public abstract String getDescription();

    /**
     * Execute the subcommand
     * @param context The command context
     * @return Command result (1 for success, 0 for failure)
     */
    public abstract int execute(CommandContext<CommandSourceStack> context);

    /**
     * Build the command with proper permission handling
     */
    public LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal(getName());

        // Set permission requirements
        builder.requires(source -> hasPermission(source.getSender()));

        // Set execution behavior
        builder.executes(this::execute);

        return builder;
    }

    /**
     * Check if the sender has permission to use this command
     * Console and RCON always have permission
     */
    protected boolean hasPermission(CommandSender sender) {
        // Console and RCON always have access
        if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
            return true;
        }

        // Check permission for other senders
        return sender.hasPermission(getPermission()) || sender.isOp();
    }

    /**
     * Send a message to the command sender with proper formatting
     */
    protected void sendMessage(CommandSender sender, String message) {
        sender.sendMessage("§7[§6SmartSpawner§7] " + message);
    }

    /**
     * Send an error message to the command sender
     */
    protected void sendError(CommandSender sender, String message) {
        sender.sendMessage("§7[§6SmartSpawner§7] §c" + message);
    }

    /**
     * Send a success message to the command sender
     */
    protected void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage("§7[§6SmartSpawner§7] §a" + message);
    }

    /**
     * Check if the sender is a player
     */
    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    /**
     * Check if the sender is console or RCON
     */
    protected boolean isConsoleOrRcon(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
    }

    /**
     * Get the player from the sender, or null if not a player
     */
    protected Player getPlayer(CommandSender sender) {
        return sender instanceof Player ? (Player) sender : null;
    }

    /**
     * Send no permission message
     */
    protected void sendNoPermission(CommandSender sender) {
        sendError(sender, "You don't have permission to use this command!");
    }

    /**
     * Send player only message
     */
    protected void sendPlayerOnly(CommandSender sender) {
        sendError(sender, "This command can only be used by players!");
    }
}