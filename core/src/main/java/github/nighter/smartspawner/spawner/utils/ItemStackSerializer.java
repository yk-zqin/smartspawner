package github.nighter.smartspawner.spawner.utils;

import github.nighter.smartspawner.spawner.properties.VirtualInventory;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;

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
        private final Map<String, Integer> potionDataCount; // For TIPPED_ARROW

        public ItemGroup(Material material) {
            this.material = material;
            this.durabilityCount = new HashMap<>();
            this.potionDataCount = new HashMap<>();
        }

        public void addItem(short durability, int count) {
            durabilityCount.merge(durability, count, Integer::sum);
        }

        public void addPotionArrow(PotionData potionData, int count) {
            String potionKey = serializePotionData(potionData);
            potionDataCount.merge(potionKey, count, Integer::sum);
        }

        private String serializePotionData(PotionData potionData) {
            return String.format("%s;%s;%s",
                    potionData.getType().name(),
                    potionData.isExtended(),
                    potionData.isUpgraded());
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
                if (meta != null && meta.getBasePotionData() != null) {
                    group.addPotionArrow(meta.getBasePotionData(), entry.getValue().intValue());
                } else {
                    // Handle case where tipped arrow has no potion data (default to WATER)
                    PotionData defaultData = new PotionData(PotionType.WATER, false, false);
                    group.addPotionArrow(defaultData, entry.getValue().intValue());
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
                // Format: TIPPED_ARROW#potion_type;extended;upgraded:count,...
                StringBuilder sb = new StringBuilder("TIPPED_ARROW#");
                boolean first = true;
                for (Map.Entry<String, Integer> entry : group.getPotionDataCount().entrySet()) {
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
                // Handle TIPPED_ARROW with potion data
                String[] potionEntries = entry.substring("TIPPED_ARROW#".length()).split(",");
                for (String potionEntry : potionEntries) {
                    String[] parts = potionEntry.split(":");
                    String[] potionData = parts[0].split(";");
                    int count = Integer.parseInt(parts[1]);

                    ItemStack arrow = new ItemStack(Material.TIPPED_ARROW);
                    PotionMeta meta = (PotionMeta) arrow.getItemMeta();
                    if (meta != null) {
                        try {
                            PotionType potionType = PotionType.valueOf(potionData[0]);
                            boolean isExtended = Boolean.parseBoolean(potionData[1]);
                            boolean isUpgraded = Boolean.parseBoolean(potionData[2]);

                            PotionData potionDataMeta = new PotionData(potionType, isExtended, isUpgraded);
                            meta.setBasePotionData(potionDataMeta);
                            arrow.setItemMeta(meta);
                        } catch (IllegalArgumentException e) {
                            // Handle case where potion type is not valid, default to WATER
                            PotionData defaultData = new PotionData(PotionType.WATER, false, false);
                            meta.setBasePotionData(defaultData);
                            arrow.setItemMeta(meta);
                        }
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