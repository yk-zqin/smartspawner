package github.nighter.smartspawner.spawner.loot;

import github.nighter.smartspawner.nms.MaterialWrapper;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

import java.util.Random;

@Getter
public class LootItem {
    private final Material material;
    private final int minAmount;
    private final int maxAmount;
    private final double chance;
    private final Integer minDurability;
    private final Integer maxDurability;
    private final PotionType potionType;
    private final boolean isExtended;
    private final boolean isUpgraded;
    private final double sellPrice;

    public LootItem(Material material, int minAmount, int maxAmount, double chance,
                    Integer minDurability, Integer maxDurability, PotionType potionType,
                    boolean isExtended, boolean isUpgraded, double sellPrice) {
        this.material = material;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chance = chance;
        this.minDurability = minDurability;
        this.maxDurability = maxDurability;
        this.potionType = potionType;
        this.isExtended = isExtended;
        this.isUpgraded = isUpgraded;
        this.sellPrice = sellPrice;
    }

    public ItemStack createItemStack(Random random) {
        if (material == null) {
            return null; // Material not available in this version
        }

        ItemStack item = new ItemStack(material, 1);

        // Apply durability only if needed
        if (minDurability != null && maxDurability != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof Damageable) {
                int durability = random.nextInt(maxDurability - minDurability + 1) + minDurability;
                ((Damageable) meta).setDamage(durability);
                item.setItemMeta(meta);
            }
        }

        // Handle potion effects for tipped arrows
        if (isTippedArrow() && potionType != null) {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null) {
                PotionData potionData = new PotionData(potionType, isExtended, isUpgraded);
                meta.setBasePotionData(potionData);
                item.setItemMeta(meta);
            }
        }

        return item;
    }

    public int generateAmount(Random random) {
        return random.nextInt(maxAmount - minAmount + 1) + minAmount;
    }

    public boolean isAvailable() {
        return material != null;
    }

    private boolean isTippedArrow() {
        if (material == null) return false;

        // Check if TIPPED_ARROW is available in current version
        Material tippedArrow = MaterialWrapper.getMaterial("TIPPED_ARROW");
        return tippedArrow != null && material == tippedArrow;
    }
}