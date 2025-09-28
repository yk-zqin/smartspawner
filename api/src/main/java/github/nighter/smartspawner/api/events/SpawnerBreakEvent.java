package github.nighter.smartspawner.api.events;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * SpawnerBreakEvent is called when a spawner being broken by a player or an explosion.
 */
public class SpawnerBreakEvent extends SpawnerEvent {
    private final Entity entity;

    private static final HandlerList handlers = new HandlerList();

    /**
     * The constructor for the event.
     *
     * @param entity         The entity who broke the spawner.
     * @param location       The location of the spawner broken.
     * @param quantity       The quantity of the spawner broken.
     */
    public SpawnerBreakEvent(Entity entity, Location location, int quantity) {
        super(location, quantity);
        this.entity = entity;
    }

    /**
     * @return The entity who broke the spawner.
     */
    public Entity getEntity() {
        return entity;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}