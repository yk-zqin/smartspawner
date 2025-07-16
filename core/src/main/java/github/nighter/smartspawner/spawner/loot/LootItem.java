package github.nighter.smartspawner.spawner.loot;

import github.nighter.smartspawner.nms.MaterialWrapper;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
                    // Try to set base potion data first for proper display
                    PotionType potionType = getPotionTypeFromEffect(effectType);
                    if (potionType != null) {
                        // Determine if this should be extended or upgraded based on duration/amplifier
                        boolean extended = isExtendedPotion(potionType, potionDuration);
                        boolean upgraded = isUpgradedPotion(potionType, potionAmplifier);

                        PotionData potionData = new PotionData(potionType, extended, upgraded);
                        meta.setBasePotionData(potionData);
                    } else {
                        // Fallback to custom effect if no matching PotionType found
                        PotionEffect effect = new PotionEffect(
                                effectType,
                                potionDuration,
                                potionAmplifier,
                                true,
                                true,
                                true
                        );
                        meta.addCustomEffect(effect, true);
                    }
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

    private PotionType getPotionTypeFromEffect(PotionEffectType effectType) {
        // Map common effect types to their corresponding PotionType
        if (effectType == PotionEffectType.SPEED) return PotionType.SPEED;
        if (effectType == PotionEffectType.SLOW) return PotionType.SLOWNESS;
        if (effectType == PotionEffectType.FAST_DIGGING) return PotionType.INSTANT_DAMAGE; // Haste doesn't have direct potion
        if (effectType == PotionEffectType.SLOW_DIGGING) return PotionType.SLOWNESS;
        if (effectType == PotionEffectType.INCREASE_DAMAGE) return PotionType.STRENGTH;
        if (effectType == PotionEffectType.HEAL) return PotionType.INSTANT_HEAL;
        if (effectType == PotionEffectType.HARM) return PotionType.INSTANT_DAMAGE;
        if (effectType == PotionEffectType.JUMP) return PotionType.JUMP;
        if (effectType == PotionEffectType.CONFUSION) return PotionType.AWKWARD; // Nausea doesn't have direct potion
        if (effectType == PotionEffectType.REGENERATION) return PotionType.REGEN;
        if (effectType == PotionEffectType.DAMAGE_RESISTANCE) return PotionType.AWKWARD; // Resistance doesn't have direct potion
        if (effectType == PotionEffectType.FIRE_RESISTANCE) return PotionType.FIRE_RESISTANCE;
        if (effectType == PotionEffectType.WATER_BREATHING) return PotionType.WATER_BREATHING;
        if (effectType == PotionEffectType.INVISIBILITY) return PotionType.INVISIBILITY;
        if (effectType == PotionEffectType.BLINDNESS) return PotionType.AWKWARD; // Blindness doesn't have direct potion
        if (effectType == PotionEffectType.NIGHT_VISION) return PotionType.NIGHT_VISION;
        if (effectType == PotionEffectType.HUNGER) return PotionType.AWKWARD; // Hunger doesn't have direct potion
        if (effectType == PotionEffectType.WEAKNESS) return PotionType.WEAKNESS;
        if (effectType == PotionEffectType.POISON) return PotionType.POISON;
        if (effectType == PotionEffectType.WITHER) return PotionType.AWKWARD; // Wither doesn't have direct potion
        if (effectType == PotionEffectType.HEALTH_BOOST) return PotionType.AWKWARD; // Health boost doesn't have direct potion
        if (effectType == PotionEffectType.ABSORPTION) return PotionType.AWKWARD; // Absorption doesn't have direct potion
        if (effectType == PotionEffectType.SATURATION) return PotionType.AWKWARD; // Saturation doesn't have direct potion

        // For newer versions, add more mappings as needed
        try {
            if (effectType == PotionEffectType.getByName("SLOW_FALLING")) return PotionType.SLOW_FALLING;
        } catch (Exception ignored) {}

        return null; // No direct mapping found, will use custom effect
    }

    private boolean isExtendedPotion(PotionType potionType, int duration) {
        // Check if the potion type supports extension and if duration is longer than normal
        switch (potionType) {
            case SPEED:
            case SLOWNESS:
            case STRENGTH:
            case JUMP:
            case REGEN:
            case FIRE_RESISTANCE:
            case WATER_BREATHING:
            case INVISIBILITY:
            case NIGHT_VISION:
            case WEAKNESS:
            case POISON:
                // Extended versions typically have longer durations
                // Normal duration is usually around 3600 ticks (3 minutes)
                // Extended duration is usually around 9600 ticks (8 minutes)
                return duration > 4800; // If duration is more than 4 minutes, consider it extended
            default:
                return false;
        }
    }

    private boolean isUpgradedPotion(PotionType potionType, int amplifier) {
        // Check if the potion type supports upgrading and if amplifier is higher than normal
        switch (potionType) {
            case SPEED:
            case SLOWNESS:
            case STRENGTH:
            case JUMP:
            case REGEN:
            case INSTANT_HEAL:
            case INSTANT_DAMAGE:
            case POISON:
                // Level II potions have amplifier 1 (since amplifier 0 = level I)
                return amplifier >= 1;
            default:
                return false;
        }
    }
}