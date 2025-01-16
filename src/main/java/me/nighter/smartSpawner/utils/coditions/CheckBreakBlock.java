package me.nighter.smartSpawner.utils.coditions;

import me.nighter.smartSpawner.hooks.protections.*;
import me.nighter.smartSpawner.SmartSpawner;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CheckBreakBlock {
    public static boolean CanPlayerBreakBlock(@NotNull final UUID playerUUID, @NotNull Block block) {
        return CanPlayerBreakBlock(playerUUID, block.getLocation());
    }
    // Check if player can break a block
    public static boolean CanPlayerBreakBlock(@NotNull final UUID playerUUID, @NotNull Location location) {

        Player player = Bukkit.getServer().getPlayer(playerUUID);

        if(player != null && (player.isOp() || player.hasPermission("*"))) return true;

        if (SmartSpawner.hasGriefPrevention)
            if (!GriefPreventionAPI.CanplayerBreakClaimBlock(playerUUID, location)) return false;

        if (SmartSpawner.hasWorldGuard)
            if (!WorldGuardAPI.canPlayerBreakBlockInRegion(playerUUID, location)) return false;

        if (SmartSpawner.hasLands)
            if (!LandsIntegrationAPI.canPlayerBreakClaimBlock(playerUUID, location)) return false;

        return true;
    }

}
