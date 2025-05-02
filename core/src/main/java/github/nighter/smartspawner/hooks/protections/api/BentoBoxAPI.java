package github.nighter.smartspawner.hooks.protections.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import world.bentobox.bentobox.BentoBox;
import world.bentobox.bentobox.api.user.User;
import world.bentobox.bentobox.lists.Flags;

import java.util.UUID;

public class BentoBoxAPI {

    public static boolean canPlayerStackBlock(@NotNull UUID playerUUID, @NotNull Location location) {

        return BentoBox.getInstance().getIslandsManager().getIslandAt(location).
                map(island -> island.isAllowed(User.getInstance(playerUUID), Flags.PLACE_BLOCKS)).
                orElse(Flags.PLACE_BLOCKS.isSetForWorld(location.getWorld()));
    }

    public static boolean canPlayerOpenMenu(@NotNull UUID playerUUID, @NotNull Location location) {

        return BentoBox.getInstance().getIslandsManager().getIslandAt(location).
                map(island -> island.isAllowed(User.getInstance(playerUUID), Flags.CONTAINER)).
                orElse(Flags.CONTAINER.isSetForWorld(location.getWorld()));
    }
}