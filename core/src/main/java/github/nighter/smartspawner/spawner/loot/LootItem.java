package github.nighter.smartspawner.spawner.loot;

import github.nighter.smartspawner.nms.MaterialWrapper;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

@Getter
public class LootItem {
    private final Material material;
    private final int minAmount;
    private final int maxAmount;
    private final double chance;
    private final Integer minDurability;
    private final Integer maxDurability;
    private final String potionEffectType;
    private final Integer potionDuration;
    private final Integer potionAmplifier;
    private final double sellPrice;

    public LootItem(Material material, int minAmount, int maxAmount, double chance,
                    Integer minDurability, Integer maxDurability, String potionEffectType,
                    Integer potionDuration, Integer potionAmplifier, double sellPrice) {
        this.material = material;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chance = chance;
        this.minDurability = minDurability;
        this.maxDurability = maxDurability;
        this.potionEffectType = potionEffectType;
        this.potionDuration = potionDuration;
        this.potionAmplifier = potionAmplifier;
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
        if (isTippedArrow() && potionEffectType != null) {
            PotionEffectType effectType = PotionEffectType.getByName(potionEffectType);
            if (effectType != null && potionDuration != null && potionAmplifier != null) {
                PotionMeta meta = (PotionMeta) item.getItemMeta();
                if (meta != null) {
                    // Create potion effect
                    PotionEffect effect = new PotionEffect(
                            effectType,
                            potionDuration,
                            potionAmplifier,
                            true,
                            true,
                            true
                    );
                    meta.addCustomEffect(effect, true);
                    item.setItemMeta(meta);
                }
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