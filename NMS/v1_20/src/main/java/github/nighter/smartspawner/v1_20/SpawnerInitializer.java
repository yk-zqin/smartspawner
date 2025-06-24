package github.nighter.smartspawner.v1_20;

import github.nighter.smartspawner.nms.SpawnerWrapper;
import org.bukkit.entity.EntityType;

import java.util.Arrays;

public class SpawnerInitializer {
    public static void init() {
        SpawnerWrapper.SUPPORTED_MOBS = Arrays.stream(EntityType.values())
                .map(EntityType::name)
                .toList();
    }
}