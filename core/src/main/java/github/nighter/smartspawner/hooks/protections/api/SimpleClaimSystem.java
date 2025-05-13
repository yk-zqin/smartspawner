package github.nighter.smartspawner.hooks.protections.api;

import fr.xyness.SCS.API.SimpleClaimSystemAPI;
import fr.xyness.SCS.API.SimpleClaimSystemAPI_Provider;
import fr.xyness.SCS.Types.Claim;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SimpleClaimSystem {
    private static final SimpleClaimSystemAPI scs = SimpleClaimSystemAPI_Provider.getAPI();

    public static boolean canPlayerBreakClaimBlock(@NotNull final Player player, @NotNull Location location) {
        if (scs == null) return true;
        Claim claim = scs.getClaimAtChunk(location.getChunk());
        if (claim == null) return true;
        return claim.getPermissionForPlayer("Destroy", player);
    }

    public static boolean canPlayerStackClaimBlock(@NotNull final Player player, @NotNull Location location) {
        if (scs == null) return true;
        Claim claim = scs.getClaimAtChunk(location.getChunk());
        if (claim == null) return true;
        return claim.getPermissionForPlayer("InteractBlocks", player);
    }

    public static boolean canPlayerOpenMenuOnClaim(@NotNull final Player player, @NotNull Location location) {
        if (scs == null) return true;
        Claim claim = scs.getClaimAtChunk(location.getChunk());
        if (claim == null) return true;
        return claim.getPermissionForPlayer("InteractBlocks", player);
    }
}