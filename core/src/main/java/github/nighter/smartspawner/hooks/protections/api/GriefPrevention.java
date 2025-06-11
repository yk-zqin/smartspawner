package github.nighter.smartspawner.hooks.protections.api;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class GriefPrevention {

    public static boolean canPlayerBreakClaimBlock(@NotNull Player player, @NotNull Location location) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        return claim.allowBreak(player, player.getLocation().getBlock().getType()) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }

    public static boolean canPlayerStackClaimBlock(@NotNull Player player, @NotNull Location location) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        return claim.allowBuild(player, player.getLocation().getBlock().getType()) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }

    public static boolean canPlayerOpenMenuOnClaim(@NotNull Player player, @NotNull Location location) {
        Claim claim = me.ryanhamshire.GriefPrevention.GriefPrevention.instance.dataStore.getClaimAt(location, true, null);
        if (claim == null) return true;

        return claim.allowContainers(player) == null && claim.hasExplicitPermission(player, ClaimPermission.Build);
    }
}