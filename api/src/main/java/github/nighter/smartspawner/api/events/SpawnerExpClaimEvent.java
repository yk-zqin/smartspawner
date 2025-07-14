package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SpawnerExpClaimEvent extends Event implements Cancellable {
    private final Player player;
    private final Location location;
    private int quantity;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    /**
     * The constructor for the event.
     *
     * @param location The location of the spawner.
     * @param quantity The EXP quantity claimed from the spawner.
     */
    public SpawnerExpClaimEvent(Player player, Location location, int quantity) {
        this.player = player;
        this.location = location;
        this.quantity = quantity;
    }

    /**
     * @return The player who placed the spawner.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Get the quantity of the EXP given.
     * @return The quantity of the EXP given
     */
    public int getExpQuantity() {
        return quantity;
    }

    /**
     * Get the location of the spawner.
     * @return The location of the spawner
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Set EXP quantity.
     * @param quantity Amount of EXP
     */
    public void setExpQuantity(int quantity) {
        this.quantity = quantity;
    }

    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
