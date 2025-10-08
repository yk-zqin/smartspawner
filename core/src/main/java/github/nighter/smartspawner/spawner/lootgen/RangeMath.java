package github.nighter.smartspawner.spawner.lootgen;

import github.nighter.smartspawner.spawner.properties.SpawnerData;
import lombok.Getter;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class RangeMath {

    private final List<Player> players;
    private final List<SpawnerData> spawners;
    private final List<Location> playerLocations = new ArrayList<>();
    private final boolean[] playerIsConnectedAndAlive;
    @Getter
    private final boolean[] activeSpawners;

    public RangeMath(List<Player> players, List<SpawnerData> spawners) {
        this.players = players;
        this.spawners = spawners;

        this.activeSpawners = new boolean[spawners.size()];
        this.playerIsConnectedAndAlive = new boolean[players.size()];

        // Huge allocation improvement
        for (int i = 0; i < players.size(); i++) {
            this.playerLocations.add(players.get(i).getLocation());
            this.playerIsConnectedAndAlive[i] = players.get(i).isConnected() && !players.get(i).isDead();
        }
    }

    public void updateActiveSpawners() {
        int i = 0, j;
        boolean playerFound;
        Location playerLoc;

        for (SpawnerData s : spawners) {
            final Location spawnerLoc = s.getSpawnerLocation();
            if (spawnerLoc == null) continue;

            j = 0;

            playerFound = false;

            final double rangeSq = s.getSpawnerRange() * s.getSpawnerRange();

            for (Player p : players) {
                if (p == null || !playerIsConnectedAndAlive[j]) continue;
                if (p.getGameMode() == GameMode.SPECTATOR) continue;

                playerLoc = playerLocations.get(j);

                if (playerLoc.getWorld() == null || playerLoc.getWorld() != spawnerLoc.getWorld()) continue;

                if (this.distanceSquared(spawnerLoc, playerLoc) <= rangeSq) {
                    playerFound = true;
                    break;
                }

                j++;
            }

            activeSpawners[i++] = playerFound;
        }
    }

    private double distanceSquared(Location loc1, Location loc2) {
        double dx = loc1.getX() - loc2.getX();
        double dy = loc1.getY() - loc2.getY();
        double dz = loc1.getZ() - loc2.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

}
