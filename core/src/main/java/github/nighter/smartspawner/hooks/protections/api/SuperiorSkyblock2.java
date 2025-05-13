package github.nighter.smartspawner.hooks.protections.api;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class SuperiorSkyblock2 {

    public static boolean canPlayerStackBlock(@NotNull Player player, @NotNull Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);
        if (island != null) {
            return !island.hasPermission(SuperiorSkyblockAPI.getPlayer(player.getUniqueId()), IslandPrivilege.getByName("build"));
        }
        // Player is not in island
        return false;
    }

    public static boolean canPlayerOpenMenu(@NotNull Player player, @NotNull Location location) {
        Island island = SuperiorSkyblockAPI.getIslandAt(location);
        if (island != null) {
            return !island.hasPermission(SuperiorSkyblockAPI.getPlayer(player.getUniqueId()), IslandPrivilege.getByName("chest_access"));
        }
        // Player is not in island
        return false;
    }
}
