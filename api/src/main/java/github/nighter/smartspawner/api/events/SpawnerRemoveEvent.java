package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SpawnerRemoveEvent extends SpawnerEvent implements Cancellable {
    private final Player player;
    private final int changeAmount;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    /**
     * The constructor for the event.
     *
     * @param location The location of the spawner.
     * @param newQuantity The quantity of the spawner.
     * @param changeAmount The difference between the new and old quantity
     */
    public SpawnerRemoveEvent(Player player, Location location, int newQuantity, int changeAmount) {
        super(location, newQuantity);
        this.player = player;
        this.changeAmount = changeAmount;
    }

    /**
     * @return The difference between the new and old quantity
     */
    public int getChangeAmount() {
        return changeAmount;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}