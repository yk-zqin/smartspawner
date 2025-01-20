package me.nighter.smartSpawner.utils.coditions;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.hooks.protections.Towny;
import me.nighter.smartSpawner.hooks.protections.WorldGuardAPI;
import me.nighter.smartSpawner.hooks.protections.GriefPreventionAPI;
import me.nighter.smartSpawner.hooks.protections.LandsIntegrationAPI;

public class CheckOpenMenu {
    public static boolean CanPlayerOpenMenu(@NotNull final UUID playerUUID, @NotNull Block block) {
        return CanPlayerOpenMenu(playerUUID, block.getLocation());
    }
    // Check if player can open a menu
    public static boolean CanPlayerOpenMenu(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention)
            if (!GriefPreventionAPI.CanplayerOpenMenuOnClaim(playerUUID, location)) return false;

        if (SmartSpawner.hasWorldGuard)
            if (!WorldGuardAPI.CanPlayerInteractInRegion(player, location)) return false;

        if (SmartSpawner.hasLands)
            if (!LandsIntegrationAPI.CanPlayerInteractContainer(playerUUID, location)) return false;
        
        if (SmartSpawner.hasTowny)
            if (!Towny.IfPlayerHasResident(playerUUID, location)) return false;

        return true;
    }

}
