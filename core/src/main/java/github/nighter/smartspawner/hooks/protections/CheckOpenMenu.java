package github.nighter.smartspawner.hooks.protections;

import java.util.UUID;

import github.nighter.smartspawner.hooks.protections.api.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import github.nighter.smartspawner.SmartSpawner;

public class CheckOpenMenu {
    // Check if player can open a menu
    public static boolean CanPlayerOpenMenu(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention && !GriefPrevention.canPlayerOpenMenuOnClaim(playerUUID, location)) return false;
        if (SmartSpawner.hasWorldGuard && !WorldGuard.canPlayerInteractInRegion(player, location)) return false;
        if (SmartSpawner.hasLands && !Lands.CanPlayerInteractContainer(playerUUID, location)) return false;
        if (SmartSpawner.hasTowny && !Towny.canPlayerInteractSpawner(playerUUID, location)) return false;
        if (SmartSpawner.hasSuperiorSkyblock2 && SuperiorSkyblock2.canPlayerOpenMenu(playerUUID, location)) return false;
        if (SmartSpawner.hasBentoBox && !BentoBoxAPI.canPlayerOpenMenu(playerUUID, location)) return false;

        return true;
    }

}
