package github.nighter.smartspawner.commands.clear;

import com.mojang.brigadier.context.CommandContext;
import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.commands.BaseSubCommand;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.Scheduler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

@NullMarked
public class ClearGhostSpawnersSubCommand extends BaseSubCommand {

    public ClearGhostSpawnersSubCommand(SmartSpawner plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "ghost_spawners";
    }

    @Override
    public String getPermission() {
        return "smartspawner.command.clear";
    }

    @Override
    public String getDescription() {
        return "Check and remove all ghost spawners asynchronously";
    }

    @Override
    public int execute(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        // Notify that the check is starting
        plugin.getMessageService().sendMessage(sender, "command_ghost_spawner_check_start");

        // Run the check asynchronously
        Scheduler.runTaskAsync(() -> {
            List<String> ghostSpawnerIds = new ArrayList<>();
            List<SpawnerData> allSpawners = plugin.getSpawnerManager().getAllSpawners();

            // Check each spawner
            for (SpawnerData spawner : allSpawners) {
                if (plugin.getSpawnerManager().isGhostSpawner(spawner)) {
                    ghostSpawnerIds.add(spawner.getSpawnerId());
                }
            }

            // Remove all ghost spawners found
            int removedCount = ghostSpawnerIds.size();
            for (String spawnerId : ghostSpawnerIds) {
                plugin.getSpawnerManager().removeGhostSpawner(spawnerId);
            }

            // Send result message back on main thread
            Scheduler.runTask(() -> {
                if (removedCount > 0) {
                    plugin.getMessageService().sendMessage(sender, "command_ghost_spawner_cleared", 
                        java.util.Map.of("count", String.valueOf(removedCount)));
                } else {
                    plugin.getMessageService().sendMessage(sender, "command_ghost_spawner_none_found");
                }
            });
        });

        return 1;
    }
}
