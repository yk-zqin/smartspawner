package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SpawnerEggChangeEvent extends Event implements Cancellable {
    private final Player player;
    private final Location location;
    private final EntityType oldEggEntityType, newEggEntityType;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    /**
     * The constructor for the event.
     *
     * @param player The player who added a spawner at the stack
     * @param location The location of the spawner.
     * @param oldEggEntityType The previous entity type in the spawner
     * @param newEggEntityType The new entity type in the spawner
     */
    public SpawnerEggChangeEvent(Player player, Location location, EntityType oldEggEntityType, EntityType newEggEntityType) {
        this.player = player;
        this.location = location;
        this.oldEggEntityType = oldEggEntityType;
        this.newEggEntityType = newEggEntityType;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location;
    }

    /**
     * Get the entity type of the spawner before egg interaction
     * @return EntityType before interaction
     */
    public EntityType getOldEntityType() {
        return oldEggEntityType;
    }

    /**
     * Get the entity type of the spawner post egg interaction
     * @return EntityType post interaction
     */
    public EntityType getNewEntityType() {
        return newEggEntityType;
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