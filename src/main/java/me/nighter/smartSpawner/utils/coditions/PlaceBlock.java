package me.nighter.smartSpawner.utils.coditions;

import me.nighter.smartSpawner.hooks.claiming.*;
import me.nighter.smartSpawner.SmartSpawner;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaceBlock {
    public static boolean CanPlayerPlaceBlock(@NotNull final UUID playerUUID, @NotNull Block block) {
        return CanPlayerPlaceBlock(playerUUID, block.getLocation());
    }
    // Check if player can place a block
    public static boolean CanPlayerPlaceBlock(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention)
            if (!GriefPreventionAPI.CanplayerPlaceClaimBlock(playerUUID, location)) return false;

        if (SmartSpawner.hasWorldGuard)
            if (!WorldGuardAPI.canPlayerPlaceBlockInRegion(playerUUID, location)) return false;

        return true;
    }
}
