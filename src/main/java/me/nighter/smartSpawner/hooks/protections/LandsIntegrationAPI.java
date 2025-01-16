package me.nighter.smartSpawner.hooks.protections;

import java.util.UUID;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.Flags;
import me.angeschossen.lands.api.land.Area;
import me.angeschossen.lands.api.land.LandWorld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class LandsIntegrationAPI {

    private static LandsIntegration landsIntegration;
    
        public void LandsIntegrationAPI(Plugin SmartSpawner) {
            landsIntegration = LandsIntegration.of(SmartSpawner);
        }
    
        public static boolean CanplayerBreakClaimBlock(@NotNull UUID pUUID, @NotNull Location location) {
    
            Player player = Bukkit.getPlayer(pUUID);
            if (player == null)  return true;
    
            LandWorld world = landsIntegration.getWorld(location.getWorld());
        if (world != null) { // Lands is enabled in this world
            if (world.hasFlag(player, location, Material.SPAWNER, Flags.BLOCK_BREAK, true)) {
                return true;
                
            } else {
                return false;
            }
        }
        return true;
    }

    public static boolean CanplayerPlaceClaimBlock(@NotNull UUID pUUID, @NotNull Location location) {

        if (landsIntegration == null) {
            // Handle the case where landsIntegration is null
            return true; // or throw an exception, depending on your requirements
        }

        Player player = Bukkit.getPlayer(pUUID);
        if (player == null)  return true;

        LandWorld world = landsIntegration.getWorld(location.getWorld());
        if (world != null) { // Lands is enabled in this world
            if (world.hasFlag(player, location, Material.SPAWNER, Flags.BLOCK_PLACE, true)) {
                return true;
                
            } else {
                return false;
            }
        }
        return true;
    }

    public static boolean CanPlayerInteractContainer(@NotNull UUID pUUID, @NotNull Location location) {

        Player player = Bukkit.getPlayer(pUUID);
        if (player == null)  return true;

        LandWorld world = landsIntegration.getWorld(location.getWorld());
        if (world != null) { // Lands is enabled in this world
            if (world.hasFlag(player, location, Material.SPAWNER, Flags.INTERACT_CONTAINER, false)) {
                return true;
                
            } else {
                return false;
            }
        }
        return true;
    }
}
