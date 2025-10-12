package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.spawner.properties.SpawnerData;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class RangeMath {

    private final List<SpawnerData> spawners;
    private final RangePlayerWrapper[] rangePlayers;
    @Getter
    private final boolean[] activeSpawners;

    public RangeMath(List<Player> players, List<SpawnerData> spawners) {
        this.spawners = spawners;
        this.rangePlayers = new RangePlayerWrapper[players.size()];
        this.activeSpawners = new boolean[spawners.size()];

        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            Location loc = p.getLocation();
            boolean conditions = p.isConnected() && !p.isDead()
                    && p.getGameMode() != GameMode.SPECTATOR;

            // Store data in wrapper for faster access
            this.rangePlayers[i] = new RangePlayerWrapper(
                    loc.getWorld() != null ? loc.getWorld().getUID() : null,
                    loc.getX(),
                    loc.getY(),
                    loc.getZ(),
                    conditions
            );
        }
    }

    public void updateActiveSpawners() {
        boolean playerFound;

        for (int i = 0; i < spawners.size(); i++) {
            SpawnerData s = spawners.get(i);
            final Location spawnerLoc = s.getSpawnerLocation();
            if (spawnerLoc == null) continue;

            final World locWorld = spawnerLoc.getWorld();
            if (locWorld == null) continue;

            final UUID worldUID = locWorld.getUID();
            final double rangeSq = s.getSpawnerRange() * s.getSpawnerRange();

            playerFound = false;

            for (RangePlayerWrapper p : rangePlayers) {
                if (!p.spawnConditions) continue;
                if (p.worldUID == null) continue;
                if (!worldUID.equals(p.worldUID)) continue;

                if (p.distanceSquared(spawnerLoc) <= rangeSq) {
                    playerFound = true;
                    break;
                }
            }

            activeSpawners[i] = playerFound;
        }
    }

    private record RangePlayerWrapper(UUID worldUID, double x, double y, double z, boolean spawnConditions) {

        double distanceSquared(Location loc2) {
            double dx = this.x - loc2.getX();
            double dy = this.y - loc2.getY();
            double dz = this.z - loc2.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }

}
