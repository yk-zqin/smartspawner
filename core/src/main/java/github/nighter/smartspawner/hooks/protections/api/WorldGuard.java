package github.nighter.smartspawner.hooks.protections.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class WorldGuard {
    // Check if player can break in a location
    public static boolean canPlayerBreakBlockInRegion(@NotNull Player player, @NotNull org.bukkit.Location location) {
        if (player.isOp() || player.hasPermission("worldguard.region.bypass")) {
            return true;
        }

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        return query.testBuild(loc, localPlayer, Flags.BLOCK_BREAK);
    }

    // Check if player can place in a location
    public static boolean canPlayerStackBlockInRegion(@NotNull Player player, @NotNull org.bukkit.Location location) {
        if (player.isOp() || player.hasPermission("worldguard.region.bypass")) {
            return true;
        }

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapOfflinePlayer(player);
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        return query.testBuild(loc, localPlayer, Flags.BLOCK_PLACE);
    }

    // Check if player can interact in a location
    public static boolean canPlayerInteractInRegion(@NotNull Player player, org.bukkit.Location location) {
        if (player.isOp() || player.hasPermission("worldguard.region.bypass")) {
            return true;
        }
        
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        Location loc = new Location(BukkitAdapter.adapt(location.getWorld()), location.getX(), location.getY(), location.getZ());
        RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        return query.testBuild(loc, localPlayer, Flags.INTERACT);
    }
}
