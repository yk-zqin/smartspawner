package me.nighter.smartSpawner.spawner.lootgen;

import me.nighter.smartSpawner.SmartSpawner;
import me.nighter.smartSpawner.holders.StoragePageHolder;
import me.nighter.smartSpawner.nms.ParticleWrapper;
import me.nighter.smartSpawner.spawner.gui.synchronization.SpawnerGuiUpdater;
import me.nighter.smartSpawner.spawner.properties.SpawnerData;
import me.nighter.smartSpawner.spawner.properties.SpawnerManager;
import me.nighter.smartSpawner.spawner.properties.VirtualInventory;
import me.nighter.smartSpawner.utils.ConfigManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SpawnerLootGenerator {
    private final SmartSpawner plugin;
    private final SpawnerGuiUpdater spawnerGuiUpdater;
    private final SpawnerManager spawnerManager;
    private final ConfigManager configManager;
    private final Random random;
    private final Map<String, EntityLootConfig> entityLootConfigs;
    private final Map<String, String> effectNameCache = new HashMap<>();
    private final Map<Integer, String> romanNumeralCache = new HashMap<>();
    private static final Map<Material, ItemStack> itemTemplateCache = new ConcurrentHashMap<>();

    public SpawnerLootGenerator(SmartSpawner plugin) {
        this.plugin = plugin;
        this.spawnerGuiUpdater = plugin.getSpawnerGuiUpdater();
        this.spawnerManager = plugin.getSpawnerManager();
        this.configManager = plugin.getConfigManager();
        this.random = new Random();
        this.entityLootConfigs = new ConcurrentHashMap<>();
        loadConfigurations();
        initCaches();
    }

    private void initCaches() {
        // Initialize Roman numeral cache
        String[] romanNumerals = {"I", "II", "III", "IV", "V"};
        for (int i = 1; i <= romanNumerals.length; i++) {
            romanNumeralCache.put(i, " " + romanNumerals[i-1]);
        }
    }

    // Cache for entity configurations
    private static class EntityLootConfig {
        final int experience;
        final List<LootItem> possibleItems;

        EntityLootConfig(int experience, List<LootItem> items) {
            this.experience = experience;
            this.possibleItems = items;
        }
    }

    // Cache structure for item configuration
    private static class LootItem {
        final Material material;
        final int minAmount;
        final int maxAmount;
        final double chance;
        final Integer minDurability;
        final Integer maxDurability;
        final String potionEffectType;
        final Integer potionDuration;
        final Integer potionAmplifier;

        LootItem(Material material, int minAmount, int maxAmount, double chance,
                 Integer minDurability, Integer maxDurability, String potionEffectType,
                 Integer potionDuration, Integer potionAmplifier) {
            this.material = material;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.chance = chance;
            this.minDurability = minDurability;
            this.maxDurability = maxDurability;
            this.potionEffectType = potionEffectType;
            this.potionDuration = potionDuration;
            this.potionAmplifier = potionAmplifier;
        }

        private ItemStack getItemTemplate(Material material) {
            return itemTemplateCache.computeIfAbsent(material, ItemStack::new);
        }

        public ItemStack createItemStack(Random random, Map<String, String> effectNameCache, Map<Integer, String> romanNumeralCache) {
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
            if (material == Material.TIPPED_ARROW && potionEffectType != null) {
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

                        // Format display attributes using cached values when possible
                        String duration = formatMinecraftDuration(potionDuration);

                        // Get or cache effect name
                        String effectName = effectNameCache.computeIfAbsent(
                                effectType.getName(),
                                SpawnerLootGenerator::formatEffectName
                        );

                        // Get Roman numeral from cache or use fallback
                        String level = potionAmplifier > 0 ?
                                romanNumeralCache.getOrDefault(potionAmplifier + 1, " " + (potionAmplifier + 1)) :
                                "";

                        // Create lore
                        List<String> lore = new ArrayList<>();
                        lore.add(ChatColor.RED + effectName + level + " (" + duration + ")");
                        meta.setLore(lore);
                        meta.setDisplayName("Arrow of " + effectName);
                        item.setItemMeta(meta);
                    }
                }
            }

            return item;
        }
    }

    private static String formatMinecraftDuration(int ticks) {
        int totalSeconds = ticks / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private static String formatEffectName(String name) {
        String formatted = name.substring(0, 1).toUpperCase() + name.toLowerCase().substring(1);
        return formatted.replace("_", " ");
    }

    private void loadConfigurations() {
        ConfigurationSection mobDropSection = configManager.getLootConfig().getConfigurationSection("per_mob_drop");
        if (mobDropSection == null) {
            configManager.debug("No mob drop section found in config");
            return;
        }

        for (String entityName : mobDropSection.getKeys(false)) {
            ConfigurationSection entitySection = mobDropSection.getConfigurationSection(entityName);
            if (entitySection == null) continue;

            int experience = entitySection.getInt("experience", 0);
            List<LootItem> items = new ArrayList<>();

            ConfigurationSection lootSection = entitySection.getConfigurationSection("loot");
            if (lootSection != null) {
                for (String itemKey : lootSection.getKeys(false)) {
                    ConfigurationSection itemSection = lootSection.getConfigurationSection(itemKey);
                    if (itemSection == null) continue;

                    try {
                        Material material = Material.valueOf(itemKey.toUpperCase());
                        String[] amounts = itemSection.getString("amount", "1-1").split("-");
                        int minAmount = Integer.parseInt(amounts[0]);
                        int maxAmount = Integer.parseInt(amounts.length > 1 ? amounts[1] : amounts[0]);
                        double chance = itemSection.getDouble("chance", 100.0);

                        Integer minDurability = null;
                        Integer maxDurability = null;
                        if (itemSection.contains("durability")) {
                            String[] durabilities = itemSection.getString("durability").split("-");
                            minDurability = Integer.parseInt(durabilities[0]);
                            maxDurability = Integer.parseInt(durabilities.length > 1 ? durabilities[1] : durabilities[0]);
                        }

                        String potionEffectType = null;
                        Integer potionDuration = null;
                        Integer potionAmplifier = null;

                        if (material == Material.TIPPED_ARROW && itemSection.contains("potion_effect")) {
                            ConfigurationSection potionSection = itemSection.getConfigurationSection("potion_effect");
                            if (potionSection != null) {
                                potionEffectType = potionSection.getString("type");
                                potionDuration = potionSection.getInt("duration", 200);
                                potionAmplifier = potionSection.getInt("amplifier", 0);
                            }
                        }

                        items.add(new LootItem(material, minAmount, maxAmount, chance,
                                minDurability, maxDurability, potionEffectType,
                                potionDuration, potionAmplifier));

                    } catch (IllegalArgumentException e) {
                        configManager.debug("Error loading item config: " + entityName + " -> " + itemKey +
                                " Error: " + e.getMessage());
                    }
                }
            }

            entityLootConfigs.put(entityName.toLowerCase(), new EntityLootConfig(experience, items));
        }
    }

    public LootResult generateLoot(EntityType entityType, int minMobs, int maxMobs, SpawnerData spawner) {
        String entityName = entityType.name().toLowerCase();
        EntityLootConfig config = entityLootConfigs.get(entityName);

        if (config == null) {
            return new LootResult(Collections.emptyList(), 0);
        }

        int mobCount = random.nextInt(maxMobs - minMobs + 1) + minMobs;
        List<ItemStack> totalLoot = new ArrayList<>();
        int totalExperience = config.experience * mobCount;

        boolean allowEquipment = spawner.isAllowEquipmentItems();

        // Pre-filter items based on equipment permission
        List<LootItem> validItems = config.possibleItems.stream()
                .filter(item -> allowEquipment ||
                        (item.minDurability == null && item.maxDurability == null))
                .collect(Collectors.toList());

        if (validItems.isEmpty()) {
            return new LootResult(Collections.emptyList(), totalExperience);
        }

        // Process each mob individually for accurate drop rates
        for (int i = 0; i < mobCount; i++) {
            for (LootItem lootItem : validItems) {
                if (random.nextDouble() * 100 <= lootItem.chance) {
                    int amount = random.nextInt(lootItem.maxAmount - lootItem.minAmount + 1) + lootItem.minAmount;
                    if (amount > 0) {
                        ItemStack item = lootItem.createItemStack(random, effectNameCache, romanNumeralCache);
                        if (item != null) {
                            item.setAmount(amount);
                            totalLoot.add(item);
                        }
                    }
                }
            }
        }

        return new LootResult(totalLoot, totalExperience);
    }

    public void spawnLootToSpawner(SpawnerData spawner) {
        long currentTime = System.currentTimeMillis();
        long lastSpawnTime = spawner.getLastSpawnTime();
        long spawnDelay = spawner.getSpawnDelay();

        if (currentTime - lastSpawnTime < spawnDelay) {
            return;
        }

        // Get exact inventory slot usage
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();

        // Check if both inventory and exp are full, only then skip loot generation
        if (usedSlots >= maxSlots &&
                spawner.getSpawnerExp() >= spawner.getMaxStoredExp()) {
            return; // Skip generation if both exp and inventory are full
        }

        // Update spawn time immediately
        spawner.setLastSpawnTime(currentTime);

        // Run heavy calculations async and batch updates
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Generate loot with full mob count
            LootResult loot = generateLoot(
                    spawner.getEntityType(),
                    spawner.getMinMobs(),
                    spawner.getMaxMobs(),
                    spawner
            );

            // Only proceed if we generated something
            if (loot.getItems().isEmpty() && loot.getExperience() == 0) {
                return;
            }

            // Switch back to main thread for Bukkit API calls
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Cache viewers state to avoid multiple checks
                boolean hasLootViewers = spawnerGuiUpdater.hasLootInventoryViewers(spawner);
                boolean hasSpawnerViewers = spawnerGuiUpdater.hasSpawnerGuiViewers(spawner);

                // Cache pages calculation
                int oldTotalPages = hasLootViewers ? calculateTotalPages(spawner) : 0;

                // Modified approach: Handle items and exp separately
                boolean changed = false;

                // Process experience if there's any to add and not at max
                if (loot.getExperience() > 0 && spawner.getSpawnerExp() < spawner.getMaxStoredExp()) {
                    int currentExp = spawner.getSpawnerExp();
                    int maxExp = spawner.getMaxStoredExp();
                    int newExp = Math.min(currentExp + loot.getExperience(), maxExp);

                    if (newExp != currentExp) {
                        spawner.setSpawnerExp(newExp);
                        changed = true;
                    }
                }

                // Process items if there are any to add and inventory isn't completely full
                if (!loot.getItems().isEmpty() && usedSlots < maxSlots) {
                    List<ItemStack> itemsToAdd = new ArrayList<>(loot.getItems());

                    // Get exact calculation of slots with the new items
                    int totalRequiredSlots = calculateRequiredSlots(itemsToAdd, spawner.getVirtualInventory());
                    int currentUsedSlots = spawner.getVirtualInventory().getUsedSlots();

                    // If we'll exceed the limit, limit the items we're adding
                    if (totalRequiredSlots > maxSlots) {
                        itemsToAdd = limitItemsToAvailableSlots(itemsToAdd, spawner);
                    }

                    if (!itemsToAdd.isEmpty()) {
                        spawner.getVirtualInventory().addItems(itemsToAdd);
                        changed = true;
                    }
                }

                if (!changed) {
                    return;
                }

                // Handle GUI updates in batches
                handleGuiUpdates(spawner, hasLootViewers, hasSpawnerViewers, oldTotalPages);

                // Mark for saving only once
                spawnerManager.markSpawnerModified(spawner.getSpawnerId());
            });
        });
    }

    private List<ItemStack> limitItemsToAvailableSlots(List<ItemStack> items, SpawnerData spawner) {
        VirtualInventory currentInventory = spawner.getVirtualInventory();
        int maxSlots = spawner.getMaxSpawnerLootSlots();

        // If already full, return empty list
        if (currentInventory.getUsedSlots() >= maxSlots) {
            return Collections.emptyList();
        }

        // Create a simulation inventory
        Map<VirtualInventory.ItemSignature, Long> simulatedInventory = new HashMap<>(currentInventory.getConsolidatedItems());
        List<ItemStack> acceptedItems = new ArrayList<>();

        // Sort items by priority (you can change this sorting strategy)
        items.sort(Comparator.comparing(item -> item.getType().name()));

        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;

            // Add to simulation and check slot count
            Map<VirtualInventory.ItemSignature, Long> tempSimulation = new HashMap<>(simulatedInventory);
            VirtualInventory.ItemSignature sig = new VirtualInventory.ItemSignature(item);
            tempSimulation.merge(sig, (long) item.getAmount(), Long::sum);

            // Calculate slots needed
            int slotsNeeded = 0;
            for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : tempSimulation.entrySet()) {
                long amount = entry.getValue();
                int maxStackSize = entry.getKey().getTemplateRef().getMaxStackSize();
                slotsNeeded += (int) Math.ceil((double) amount / maxStackSize);
            }

            // If we still have room, accept this item
            if (slotsNeeded <= maxSlots) {
                acceptedItems.add(item);
                simulatedInventory = tempSimulation; // Update simulation
            } else {
                // Try to accept a partial amount of this item
                int maxStackSize = item.getMaxStackSize();
                long currentAmount = simulatedInventory.getOrDefault(sig, 0L);

                // Calculate how many we can add without exceeding slot limit
                int remainingSlots = maxSlots - calculateSlots(simulatedInventory);
                if (remainingSlots > 0) {
                    // Maximum items we can add in the remaining slots
                    long maxAddAmount = remainingSlots * maxStackSize - (currentAmount % maxStackSize);
                    if (maxAddAmount > 0) {
                        // Create a partial item
                        ItemStack partialItem = item.clone();
                        partialItem.setAmount((int) Math.min(maxAddAmount, item.getAmount()));
                        acceptedItems.add(partialItem);

                        // Update simulation
                        simulatedInventory.merge(sig, (long) partialItem.getAmount(), Long::sum);
                    }
                }

                // We've filled all slots, stop processing
                break;
            }
        }

        return acceptedItems;
    }

    private int calculateSlots(Map<VirtualInventory.ItemSignature, Long> items) {
        int slots = 0;
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : items.entrySet()) {
            long amount = entry.getValue();
            int maxStackSize = entry.getKey().getTemplateRef().getMaxStackSize();
            slots += (int) Math.ceil((double) amount / maxStackSize);
        }
        return slots;
    }

    private int calculateRequiredSlots(List<ItemStack> items, VirtualInventory inventory) {
        // Create a temporary map to simulate how items would stack
        Map<VirtualInventory.ItemSignature, Long> simulatedItems = new HashMap<>();

        // First, get existing items if we need to account for them
        if (inventory != null) {
            simulatedItems.putAll(inventory.getConsolidatedItems());
        }

        // Add the new items to our simulation
        for (ItemStack item : items) {
            if (item == null || item.getAmount() <= 0) continue;

            VirtualInventory.ItemSignature sig = new VirtualInventory.ItemSignature(item);
            simulatedItems.merge(sig, (long) item.getAmount(), Long::sum);
        }

        // Calculate exact slots needed
        int totalSlotsNeeded = 0;
        for (Map.Entry<VirtualInventory.ItemSignature, Long> entry : simulatedItems.entrySet()) {
            long amount = entry.getValue();
            int maxStackSize = entry.getKey().getTemplateRef().getMaxStackSize();
            totalSlotsNeeded += (int) Math.ceil((double) amount / maxStackSize);
        }

        return totalSlotsNeeded;
    }

    private void handleGuiUpdates(SpawnerData spawner, boolean hasLootViewers,
                                  boolean hasSpawnerViewers, int oldTotalPages) {
        // Show particles if needed
        if (configManager.isLootSpawnParticlesEnabled()) {
            Location loc = spawner.getSpawnerLocation();
            World world = loc.getWorld();
            if (world != null) {
                world.spawnParticle(ParticleWrapper.VILLAGER_HAPPY,
                        loc.clone().add(0.5, 0.5, 0.5),
                        10, 0.3, 0.3, 0.3, 0);
            }
        }

        // Batch GUI updates
        if (hasLootViewers) {
            if (spawner.getVirtualInventory().isDirty()) {
                int newTotalPages = calculateTotalPages(spawner);
                spawnerGuiUpdater.updateStorageGuiViewers(spawner, oldTotalPages, newTotalPages);
            }
        }

        if (hasSpawnerViewers) {
            spawnerGuiUpdater.updateSpawnerMenuGuiViewers(spawner);
        }

        if (configManager.isHologramEnabled()) {
            spawner.updateHologramData();
        }
    }

    private int calculateTotalPages(SpawnerData spawner) {
        int usedSlots = spawner.getVirtualInventory().getUsedSlots();
        return Math.max(1, (int) Math.ceil((double) usedSlots / StoragePageHolder.MAX_ITEMS_PER_PAGE));
    }
}