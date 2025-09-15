package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.language.LanguageManager;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages form-based UI for Bedrock players using Floodgate
 */
public class SpawnerMenuFormUI {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;

    /**
     * Constructs the SpawnerMenuFormUI.
     *
     * @param plugin The main plugin instance
     */
    public SpawnerMenuFormUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
    }

    /**
     * Opens the spawner form for a Bedrock player
     *
     * @param player The player to open the form for
     * @param spawner The spawner data to display
     */
    public void openSpawnerForm(Player player, SpawnerData spawner) {
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", entityName);
        placeholders.put("amount", String.valueOf(spawner.getStackSize()));

        String title;
        if (spawner.getStackSize() > 1) {
            title = languageManager.getGuiTitle("gui_title_main.stacked_spawner", placeholders);
        } else {
            title = languageManager.getGuiTitle("gui_title_main.single_spawner", placeholders);
        }

        // Get button texts from language configuration  
        String lootButtonText = languageManager.getGuiItemName("bedrock_gui.buttons.storage", placeholders);
        String infoButtonText = languageManager.getGuiItemName("bedrock_gui.buttons.stack_info", placeholders);
        String expButtonText = languageManager.getGuiItemName("bedrock_gui.buttons.experience", placeholders);

        // Create a simple form with buttons for each action
        SimpleForm form = SimpleForm.builder()
                .title(title)
                .content(createInfoContent(player, spawner))
                // Add buttons with configurable text from language files
                .button(lootButtonText, FormImage.Type.URL, "https://img.icons8.com/?size=100&id=e78DnJp8bhmX&format=png&color=000000")
                .button(infoButtonText, FormImage.Type.URL, "https://static.wikia.nocookie.net/minecraft_gamepedia/images/c/cf/Spawner_with_fire.png/revision/latest?cb=20190925003048")
                .button(expButtonText, FormImage.Type.URL, "https://static.wikia.nocookie.net/minecraft_gamepedia/images/1/10/Bottle_o%27_Enchanting.gif/revision/latest?cb=20200428012753")
                // Add closed or invalid response handler
                .closedOrInvalidResultHandler(() -> {
                    // Do nothing when form is closed without selecting
                })
                // Add valid response handler
                .validResultHandler(response -> {
                    // Get the index of the clicked button
                    int buttonId = response.clickedButtonId();

                    // Schedule the action to run on the main server thread
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        // Handle the button click based on the button ID
                        switch (buttonId) {
                            case 0: // Loot Storage
                                handleLootStorage(player, spawner);
                                break;
                            case 1: // Spawner Info
                                handleSpawnerInfo(player, spawner);
                                break;
                            case 2: // Experience
                                handleExpCollection(player, spawner);
                                break;
                        }
                    });
                })
                .build();

        // Send the form to the player
        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(form);
    }

    private String createInfoContent(Player player, SpawnerData spawner) {
        StringBuilder content = new StringBuilder();

        // Get entity names with proper formatting
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String entityNameSmallCaps = languageManager.getSmallCaps(languageManager.getFormattedMobName(spawner.getEntityType()));

        // Prepare placeholders for consistent formatting
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", entityName);
        placeholders.put("ᴇɴᴛɪᴛʏ", entityNameSmallCaps);
        placeholders.put("stack_size", String.valueOf(spawner.getStackSize()));
        placeholders.put("range", String.valueOf(spawner.getSpawnerRange()));
        placeholders.put("min_mobs", String.valueOf(spawner.getMinMobs()));
        placeholders.put("max_mobs", String.valueOf(spawner.getMaxMobs()));
        placeholders.put("delay", String.valueOf(spawner.getSpawnDelay() / 20));

        // Storage information
        int currentItems = spawner.getVirtualInventory().getUsedSlots();
        int maxSlots = spawner.getMaxSpawnerLootSlots();
        double percentStorageDecimal = maxSlots > 0 ? ((double) currentItems / maxSlots) * 100 : 0;
        String formattedPercentStorage = String.format("%.1f", percentStorageDecimal);
        
        placeholders.put("current_items", String.valueOf(currentItems));
        placeholders.put("max_items", languageManager.formatNumber(maxSlots));
        placeholders.put("formatted_storage", formattedPercentStorage);

        // Experience information
        long currentExp = spawner.getSpawnerExp();
        long maxExp = spawner.getMaxStoredExp();
        double percentExpDecimal = maxExp > 0 ? ((double) currentExp / maxExp) * 100 : 0;
        String formattedPercentExp = String.format("%.1f", percentExpDecimal);
        String formattedCurrentExp = languageManager.formatNumber(currentExp);
        String formattedMaxExp = languageManager.formatNumber(maxExp);

        placeholders.put("current_exp", formattedCurrentExp);
        placeholders.put("max_exp", formattedMaxExp);
        placeholders.put("formatted_exp", formattedPercentExp);

        // Build content using the same style as the main GUI
        
        // Spawner Information Section - matching spawner_info_item style
        content.append("&#7b68ee◈ &#8a2be2ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ:\n");
        content.append("  &#e6e6fa•  ꜱᴛᴀᴄᴋ: &#c2a8fc").append(spawner.getStackSize()).append("\n");
        content.append("  &#e6e6fa•  ʀᴀɴɢᴇ: &#c2a8fc").append(spawner.getSpawnerRange()).append("&#e6e6fa ʙʟᴏᴄᴋꜱ\n");
        content.append("  &#e6e6fa•  ᴍᴏʙꜱ: &#c2a8fc").append(spawner.getMinMobs()).append("&#e6e6fa - &#c2a8fc").append(spawner.getMaxMobs()).append("\n");
        content.append("  &#e6e6fa•  ᴅᴇʟᴀʏ: &#c2a8fc").append(spawner.getSpawnDelay() / 20).append("&#e6e6faꜱ\n\n");

        // Storage Section - matching spawner_storage_item style
        content.append("&#d9b50c◈ &#fce96aꜱᴛᴏʀᴀɢᴇ:\n");
        content.append("  &#f8f8ff•  ꜱʟᴏᴛꜱ: &#f9cf51").append(currentItems).append("&#d9b50c/&#f9cf51").append(languageManager.formatNumber(maxSlots)).append("\n");
        content.append("  &#f8f8ff•  ꜱᴛᴀᴛᴜꜱ: ").append(getStorageStatus(currentItems, maxSlots)).append("\n\n");

        // Experience Section - matching exp_info_item style  
        content.append("&#2cc483◈ &#48e89bᴇxᴘᴇʀɪᴇɴᴄᴇ:\n");
        content.append("  &#f8f8ff•  ᴄᴜʀʀᴇɴᴛ: &#37eb9a").append(formattedCurrentExp).append("&#2cc483/&#37eb9a").append(formattedMaxExp).append(" &#2cc483xᴘ\n");
        content.append("  &#f8f8ff•  ꜱᴛᴀᴛᴜꜱ: ").append(getExpStatus(currentExp, maxExp)).append("\n");

        return content.toString();
    }

    private String getStorageStatus(int current, int max) {
        double ratio = (double) current / max;
        Map<String, String> placeholders = new HashMap<>();
        
        if (ratio >= 0.9) return languageManager.getGuiItemName("bedrock_gui.status.storage.nearly_full", placeholders);
        if (ratio >= 0.7) return languageManager.getGuiItemName("bedrock_gui.status.storage.filling_up", placeholders);
        if (ratio >= 0.4) return languageManager.getGuiItemName("bedrock_gui.status.storage.half_full", placeholders);
        if (ratio > 0) return languageManager.getGuiItemName("bedrock_gui.status.storage.plenty_space", placeholders);
        return languageManager.getGuiItemName("bedrock_gui.status.storage.empty", placeholders);
    }

    private String getExpStatus(long current, long max) {
        double ratio = (double) current / max;
        Map<String, String> placeholders = new HashMap<>();
        
        if (ratio >= 0.9) return languageManager.getGuiItemName("bedrock_gui.status.experience.almost_full", placeholders);
        if (ratio >= 0.7) return languageManager.getGuiItemName("bedrock_gui.status.experience.large_amount", placeholders);
        if (ratio >= 0.4) return languageManager.getGuiItemName("bedrock_gui.status.experience.medium_amount", placeholders);
        if (ratio > 0) return languageManager.getGuiItemName("bedrock_gui.status.experience.small_amount", placeholders);
        return languageManager.getGuiItemName("bedrock_gui.status.experience.empty", placeholders);
    }

    /**
     * Handles the loot storage button click
     *
     * @param player The player clicking the button
     * @param spawner The spawner data
     */
    private void handleLootStorage(Player player, SpawnerData spawner) {
        // Use the existing chest handler from your SpawnerMenuAction class
        plugin.getSpawnerMenuAction().handleChestClick(player, spawner);
    }

    /**
     * Handles the spawner info button click
     *
     * @param player The player clicking the button
     * @param spawner The spawner data
     */
    private void handleSpawnerInfo(Player player, SpawnerData spawner) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        plugin.getSpawnerStackerUI().openStackerGui(player, spawner);
    }

    /**
     * Handles the experience collection button click
     *
     * @param player The player clicking the button
     * @param spawner The spawner data
     */
    private void handleExpCollection(Player player, SpawnerData spawner) {
        // This will execute the same logic as your handleExpBottleClick method
        plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, false);
    }

    /**
     * Calculates a percentage safely (avoids division by zero).
     *
     * @param current Current value
     * @param maximum Maximum value
     * @return Percentage (0-100)
     */
    private int calculatePercentage(long current, long maximum) {
        return maximum > 0 ? (int) ((double) current / maximum * 100) : 0;
    }

    /**
     * Checks if a player is a Bedrock player via Floodgate
     *
     * @param player The player to check
     * @return true if player is from Bedrock, false otherwise
     */
    public static boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }
}