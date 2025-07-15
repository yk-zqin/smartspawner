package github.nighter.smartspawner.nms;

import org.bukkit.Material;
import java.util.Map;
import java.util.Set;

public class MaterialWrapper {
    public static Map<String, Material> SUPPORTED_MATERIALS;
    public static Set<String> AVAILABLE_MATERIAL_NAMES;

    public static Material getMaterial(String materialName) {
        if (SUPPORTED_MATERIALS == null) {
            return null;
        }
        return SUPPORTED_MATERIALS.get(materialName.toUpperCase());
    }

    public static boolean isMaterialAvailable(String materialName) {
        if (AVAILABLE_MATERIAL_NAMES == null) {
            return false;
        }
        return AVAILABLE_MATERIAL_NAMES.contains(materialName.toUpperCase());
    }
}