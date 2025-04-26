package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SpawnerExplodeEvent extends SpawnerBreakEvent {
    private final boolean exploded;

    private static final HandlerList handlers = new HandlerList();

    /**
     * The constructor for the event.
     *
     * @param entity   The entity who explode the spawner
     * @param location The location where the spawner was exploded.
     * @param quantity The quantity of the spawner.
     */
    public SpawnerExplodeEvent(Entity entity, Location location, int quantity, boolean exploded) {
        super(entity, location, quantity);
        this.exploded = exploded;
    }

    /**
     * @return - {@code true}: if the spawner is exploded
     *         - {@code false}: if the spawner isn't exploded
     */
    public boolean isExploded() {
        return exploded;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}