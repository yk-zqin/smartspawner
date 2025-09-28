package github.nighter.smartspawner.api.events;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class SpawnerOpenGUIEvent extends SpawnerEvent implements Cancellable {
    @Getter
    private final Player player;
    @Getter
    private final EntityType entityType;
    @Getter
    private final boolean isRefresh;
    private boolean cancelled = false;

    private static final HandlerList handlers = new HandlerList();

    public SpawnerOpenGUIEvent(Player player, Location location, EntityType entityType, int stackSize, boolean isRefresh) {
        super(location, stackSize);
        this.player = player;
        this.entityType = entityType;
        this.isRefresh = isRefresh;
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