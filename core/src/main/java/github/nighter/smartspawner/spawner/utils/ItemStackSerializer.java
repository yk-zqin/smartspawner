package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import java.util.*;

public class ItemStackSerializer {
    // Static lists for armor and tool materials
    private static final List<String> ARMOR_MATERIALS = Arrays.asList("LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE");
    private static final List<String> ARMOR_PIECES = Arrays.asList("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS");
    private static final List<String> TOOL_TYPES = Arrays.asList("_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE");

    @Getter
    public static class ItemGroup {
        private final Material material;
        private final Map<Short, Integer> durabilityCount;
        private final Map<String, Integer> potionEffectCount; // For TIPPED_ARROW

        public ItemGroup(Material material) {
            this.material = material;
            this.durabilityCount = new HashMap<>();
            this.potionEffectCount = new HashMap<>();
        }

        public void addItem(short durability, int count) {
            durabilityCount.merge(durability, count, Integer::sum);
        }

        public void addPotionArrow(PotionEffect effect, int count) {
            String effectKey = serializePotionEffect(effect);
            potionEffectCount.merge(effectKey, count, Integer::sum);
        }

        private String serializePotionEffect(PotionEffect effect) {
            return String.format("%s;%d;%d",
                    effect.getType().getName(),
                    effect.getDuration(),
                    effect.getAmplifier());
        }
    }

    public static List<String> serializeInventory(Map<VirtualInventory.ItemSignature, Long> items) {
        Map<Material, ItemGroup> groupedItems = new HashMap<>();

        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            ItemStack template = entry.getKey().getTemplate();
            Material material = template.getType();
            ItemGroup group = groupedItems.computeIfAbsent(material, ItemGroup::new);

            if (material == Material.TIPPED_ARROW) {
                PotionMeta meta = (PotionMeta) template.getItemMeta();
                if (meta != null && !meta.getCustomEffects().isEmpty()) {
                    group.addPotionArrow(meta.getCustomEffects().get(0), entry.getValue().intValue());
                }
            } else if (isDestructibleItem(material)) {
                // Only add durability for items that can be damaged
                group.addItem(template.getDurability(), entry.getValue().intValue());
            } else {
                // For non-destructible items, always use durability 0
                group.addItem((short) 0, entry.getValue().intValue());
            }
        }

        List<String> serializedItems = new ArrayList<>();
        for (ItemGroup group : groupedItems.values()) {
            if (group.getMaterial() == Material.TIPPED_ARROW) {
                // Format: TIPPED_ARROW;effect_type;duration;amplifier:count,...
                StringBuilder sb = new StringBuilder("TIPPED_ARROW#");
                boolean first = true;
                for (Map.Entry<String, Integer> entry : group.getPotionEffectCount().entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append(entry.getKey()).append(':').append(entry.getValue());
                    first = false;
                }
                serializedItems.add(sb.toString());
            } else if (isDestructibleItem(group.getMaterial())) {
                // Format for destructible items with durability
                StringBuilder sb = new StringBuilder(group.getMaterial().name());
                sb.append(';');
                boolean first = true;
                for (Map.Entry<Short, Integer> entry : group.getDurabilityCount().entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    sb.append(entry.getKey()).append(':').append(entry.getValue());
                    first = false;
                }
                serializedItems.add(sb.toString());
            } else {
                // Format for normal items without durability
                int totalCount = group.getDurabilityCount().values().stream()
                        .mapToInt(Integer::intValue).sum();
                serializedItems.add(group.getMaterial().name() + ":" + totalCount);
            }
        }
        return serializedItems;
    }

    public static Map<ItemStack, Integer> deserializeInventory(List<String> data) {
        Map<ItemStack, Integer> result = new HashMap<>();

        for (String entry : data) {
            if (entry.startsWith("TIPPED_ARROW#")) {
                // Handle TIPPED_ARROW with potion effects
                String[] effectEntries = entry.substring("TIPPED_ARROW#".length()).split(",");
                for (String effectEntry : effectEntries) {
                    String[] parts = effectEntry.split(":");
                    String[] effectData = parts[0].split(";");
                    int count = Integer.parseInt(parts[1]);

                    ItemStack arrow = new ItemStack(Material.TIPPED_ARROW);
                    PotionMeta meta = (PotionMeta) arrow.getItemMeta();
                    if (meta != null) {
                        PotionEffect effect = new PotionEffect(
                                org.bukkit.potion.PotionEffectType.getByName(effectData[0]),
                                Integer.parseInt(effectData[1]),
                                Integer.parseInt(effectData[2])
                        );
                        meta.addCustomEffect(effect, true);
                        arrow.setItemMeta(meta);
                    }
                    result.put(arrow, count);
                }
            } else if (entry.contains(";")) {
                // Logic for destructible items
                String[] parts = entry.split(";");
                Material material = Material.valueOf(parts[0]);

                for (String durabilityCount : parts[1].split(",")) {
                    String[] dc = durabilityCount.split(":");
                    short durability = Short.parseShort(dc[0]);
                    int count = Integer.parseInt(dc[1]);

                    ItemStack item = new ItemStack(material);
                    item.setDurability(durability);
                    result.put(item, count);
                }
            } else {
                // Logic for normal items
                String[] parts = entry.split(":");
                Material material = Material.valueOf(parts[0]);
                int count = Integer.parseInt(parts[1]);

                ItemStack item = new ItemStack(material);
                // No need to set durability for non-destructible items
                result.put(item, count);
            }
        }
        return result;
    }

    public static boolean isDestructibleItem(Material material) {
        String name = material.name();

        // Check if it's a tool
        for (String type : TOOL_TYPES) {
            if (name.endsWith(type)) {
                return true;
            }
        }

        // Check if it's armor
        for (String armorMaterial : ARMOR_MATERIALS) {
            for (String armorPiece : ARMOR_PIECES) {
                if (name.equals(armorMaterial + armorPiece)) {
                    return true;
                }
            }
        }

        // Check specific items that can be damaged
        return name.equals("BOW")
                || name.equals("FISHING_ROD")
                || name.equals("FLINT_AND_STEEL")
                || name.equals("SHEARS")
                || name.equals("SHIELD")
                || name.equals("ELYTRA")
                || name.equals("TRIDENT")
                || name.equals("CROSSBOW")
                || name.equals("CARROT_ON_A_STICK")
                || name.equals("WARPED_FUNGUS_ON_A_STICK");
    }
}