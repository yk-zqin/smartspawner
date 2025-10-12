package github.nighter.smartspawner.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.logging.SpawnerEventType;
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

        // Set execution behavior with logging
        builder.executes(context -> {
            logCommandExecution(context);
            return execute(context);
        });

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
     * Check if the sender is a player
     */
    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    protected Player getPlayer(CommandSender sender) {
        if (isPlayer(sender)) {
            return (Player) sender;
        }
        return null;
    }

    /**
     * Check if the sender is console or RCON
     */
    protected boolean isConsoleOrRcon(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender;
    }
    
    /**
     * Log command execution
     */
    protected void logCommandExecution(CommandContext<CommandSourceStack> context) {
        if (plugin.getSpawnerActionLogger() == null) {
            return;
        }
        
        CommandSender sender = context.getSource().getSender();
        SpawnerEventType eventType;
        
        if (sender instanceof RemoteConsoleCommandSender) {
            eventType = SpawnerEventType.COMMAND_EXECUTE_RCON;
        } else if (sender instanceof ConsoleCommandSender) {
            eventType = SpawnerEventType.COMMAND_EXECUTE_CONSOLE;
        } else {
            eventType = SpawnerEventType.COMMAND_EXECUTE_PLAYER;
        }
        
        plugin.getSpawnerActionLogger().log(eventType, builder -> {
            if (sender instanceof Player player) {
                builder.player(player.getName(), player.getUniqueId());
            } else {
                builder.metadata("sender", sender.getName());
            }
            builder.metadata("command", getName());
            builder.metadata("full_command", context.getInput());
        });
    }
}