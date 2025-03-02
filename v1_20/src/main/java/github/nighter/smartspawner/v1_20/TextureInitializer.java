package github.nighter.smartspawner.v1_20;

import github.nighter.smartspawner.nms.TextureWrapper;
import org.bukkit.entity.EntityType;

public class TextureInitializer {
    public static void init() {
        TextureWrapper.addVersionSpecificTexture(
                EntityType.MUSHROOM_COW,
                "a3b9003ba2d05562c75119b8a62185c67130e9282f7acbac4bc2824c21eb95d9"
        );
    }
}