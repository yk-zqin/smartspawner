package github.nighter.smartspawner.api.events;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SpawnerSellEvent extends Event implements Cancellable {
    @Getter
    private final Player player;
    @Getter
    private final Location location;
    @Getter
    private final List<ItemStack> items;
    @Getter @Setter
    private double moneyAmount;
    @Getter @Setter
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    /**
     * Constructor for the event.
     *
     * @param player The player who should be selling the items.
     * @param location The location of the spawner.
     * @param items The items that should be sold.
     * @param moneyAmount The final amount that should be given to the player.
     */
    public SpawnerSellEvent(Player player, Location location, List<ItemStack> items, double moneyAmount) {
        this.player = player;
        this.location = location;
        this.items = items;
        this.moneyAmount = moneyAmount;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}
