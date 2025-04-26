package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SpawnerPlayerBreakEvent extends SpawnerBreakEvent implements Cancellable {
    private final Player player;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    /**
     * The constructor for the event.
     *
     * @param player   The player who broke the spawner
     * @param location The location who broke of the spawner.
     * @param quantity The quantity of the spawner.
     */
    public SpawnerPlayerBreakEvent(Player player, Location location, int quantity) {
        super(player, location, quantity);
        this.player = player;
    }

    /**
     * @return Player who broke the spawner
     */
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