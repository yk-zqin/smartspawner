package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.SmartSpawner;
import github.nighter.smartspawner.language.MessageService;
import github.nighter.smartspawner.spawner.properties.SpawnerData;
import github.nighter.smartspawner.language.LanguageManager;
import github.nighter.smartspawner.spawner.gui.layout.GuiLayout;
import github.nighter.smartspawner.spawner.gui.layout.GuiButton;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class SpawnerMenuFormUI {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;

    public SpawnerMenuFormUI(SmartSpawner plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.messageService = plugin.getMessageService();
    }

    public void openSpawnerForm(Player player, SpawnerData spawner) {
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("ᴇɴᴛɪᴛʏ", entityName);
        placeholders.put("entity", entityName);
        placeholders.put("amount", String.valueOf(spawner.getStackSize()));

        String title;
        if (spawner.getStackSize() > 1) {
            title = languageManager.getGuiTitle("gui_title_main.stacked_spawner", placeholders);
        } else {
            title = languageManager.getGuiTitle("gui_title_main.single_spawner", placeholders);
        }

        // Get layout configuration to determine which buttons to show
        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMainLayout();
        if (layout == null) {
            // Fallback to original behavior if no layout available
            openFallbackForm(player, spawner, title);
            return;
        }

        // Collect available buttons based on layout and permissions
        List<ButtonInfo> availableButtons = collectAvailableButtons(layout, player, spawner, placeholders);

        if (availableButtons.isEmpty()) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        // Build the form with available buttons and info content at bottom
        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title(title)
                .content(createInfoContent(player, spawner));

        // Add buttons to form
        for (ButtonInfo buttonInfo : availableButtons) {
            formBuilder.button(buttonInfo.text, FormImage.Type.URL, buttonInfo.imageUrl);
        }

        // Configure form response handlers
        SimpleForm form = formBuilder
                .closedOrInvalidResultHandler(() -> {
                    // Do nothing when form is closed without selecting
                })
                .validResultHandler(response -> {
                    int buttonId = response.clickedButtonId();
                    if (buttonId >= 0 && buttonId < availableButtons.size()) {
                        ButtonInfo buttonInfo = availableButtons.get(buttonId);

                        // Schedule the action to run on the main server thread
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            handleButtonAction(player, spawner, buttonInfo.action);
                        });
                    }
                })
                .build();

        // Send the form to the player
        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(form);
    }

    private List<ButtonInfo> collectAvailableButtons(GuiLayout layout, Player player, SpawnerData spawner, Map<String, String> placeholders) {
        List<ButtonInfo> buttons = new ArrayList<>();

        // Check for storage button
        GuiButton storageButton = layout.getButton("storage");
        if (storageButton != null && storageButton.isEnabled()) {
            String text = languageManager.getGuiItemName("bedrock_gui.buttons.storage", placeholders);
            buttons.add(new ButtonInfo("open_storage", text, "https://art.pixilart.com/sr2f53a0222ccaws3.png"));
        }

        // Check for spawner info/stacker button
        boolean hasShopPermission = plugin.hasSellIntegration() && player.hasPermission("smartspawner.sellall");
        GuiButton spawnerInfoButton = getSpawnerInfoButton(layout, hasShopPermission);
        if (spawnerInfoButton != null && spawnerInfoButton.isEnabled() &&
            player.hasPermission("smartspawner.stack")) {
            String text = languageManager.getGuiItemName("bedrock_gui.buttons.stack_info", placeholders);
            buttons.add(new ButtonInfo("open_stacker", text, "https://static.wikia.nocookie.net/minecraft_gamepedia/images/c/cf/Spawner_with_fire.png/revision/latest?cb=20190925003048"));
        }

        // Check for sell_and_exp button (Claim XP and Sell All)
        if (hasShopPermission) {
            GuiButton sellInventoryButton = layout.getButton("spawner_info_with_shop");
            if (sellInventoryButton != null && sellInventoryButton.isEnabled() &&
                sellInventoryButton.getAction("left_click") != null &&
                sellInventoryButton.getAction("left_click").equals("sell_and_exp")) {
                String text = languageManager.getGuiItemName("bedrock_gui.buttons.sell_and_exp", placeholders);
                buttons.add(new ButtonInfo("sell_and_exp", text, "https://img.icons8.com/?size=100&id=12815&format=png&color=FFD700"));
            }

            // Check for sell_all button (Sell items only)
            GuiButton sellAllButton = layout.getButton("sell_all");
            if (sellAllButton != null && sellAllButton.isEnabled()) {
                String text = languageManager.getGuiItemName("bedrock_gui.buttons.sell_all", placeholders);
                buttons.add(new ButtonInfo("sell_all", text, "https://img.icons8.com/?size=100&id=12815&format=png&color=FFA500"));
            }
        }

        // Check for experience button
        GuiButton expButton = layout.getButton("exp");
        if (expButton != null && expButton.isEnabled()) {
            String text = languageManager.getGuiItemName("bedrock_gui.buttons.experience", placeholders);
            buttons.add(new ButtonInfo("collect_exp", text, "https://static.wikia.nocookie.net/minecraft_gamepedia/images/1/10/Bottle_o%27_Enchanting.gif/revision/latest?cb=20200428012753"));
        }

        return buttons;
    }

    private GuiButton getSpawnerInfoButton(GuiLayout layout, boolean hasShopPermission) {
        // Check for shop integration permission
        if (hasShopPermission) {
            GuiButton shopButton = layout.getButton("spawner_info_with_shop");
            if (shopButton != null) {
                return shopButton;
            }
        } else {
            GuiButton noShopButton = layout.getButton("spawner_info_no_shop");
            if (noShopButton != null) {
                return noShopButton;
            }
        }

        // Fallback to the generic spawner_info button if conditional ones don't exist
        return layout.getButton("spawner_info");
    }

    private String createInfoContent(Player player, SpawnerData spawner) {
        // Get configured info content from language file - use hardcoded header as fallback
        String header = "INFORMATION:";

        StringBuilder content = new StringBuilder();
        content.append(header).append("\n\n");

        // Prepare placeholders for content replacement
        Map<String, String> placeholders = createContentPlaceholders(player, spawner);

        // Add spawner info section
        addConfiguredSection(content, "spawner_info", placeholders);

        // Add storage info section
        addConfiguredSection(content, "storage_info", placeholders);

        // Add experience info section
        addConfiguredSection(content, "experience_info", placeholders);

        return content.toString();
    }

    private Map<String, String> createContentPlaceholders(Player player, SpawnerData spawner) {
        Map<String, String> placeholders = new HashMap<>();

        // Entity information
        String entityName = languageManager.getFormattedMobName(spawner.getEntityType());
        String entityNameSmallCaps = languageManager.getSmallCaps(entityName);
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

        return placeholders;
    }

    private void addConfiguredSection(StringBuilder content, String sectionName, Map<String, String> placeholders) {
        // Use the GUI item lore method which can access configuration
        List<String> sectionLines = languageManager.getGuiItemLoreAsList("bedrock_gui.info_content.sections." + sectionName, placeholders);
        // If the configuration key doesn't exist, add default content
        if (sectionLines.isEmpty()) {
            addDefaultSection(content, sectionName, placeholders);
            return;
        }

        for (String line : sectionLines) {
            content.append(line).append("\n");
        }
    }

    private void addDefaultSection(StringBuilder content, String sectionName, Map<String, String> placeholders) {
        // Default fallback content if configuration is missing
        switch (sectionName) {
            case "spawner_info":
                content.append("Stack: ").append(placeholders.get("stack_size")).append("\n");
                content.append("Range: ").append(placeholders.get("range")).append(" blocks\n");
                content.append("Mobs: ").append(placeholders.get("min_mobs")).append(" - ").append(placeholders.get("max_mobs")).append("\n");
                content.append("Delay: ").append(placeholders.get("delay")).append("s\n\n");
                break;
            case "storage_info":
                content.append("STORAGE:\n");
                content.append("Slots: ").append(placeholders.get("current_items")).append("/").append(placeholders.get("max_items")).append("\n");
                content.append("Status: ").append(placeholders.get("storage_status")).append("\n\n");
                break;
            case "experience_info":
                content.append("EXPERIENCE:\n");
                content.append("Current: ").append(placeholders.get("current_exp")).append("/").append(placeholders.get("max_exp")).append(" XP\n");
                content.append("Status: ").append(placeholders.get("exp_status")).append("\n");
                break;
        }
    }

    private void handleButtonAction(Player player, SpawnerData spawner, String action) {
        switch (action) {
            case "open_storage":
                handleLootStorage(player, spawner);
                break;
            case "open_stacker":
                handleSpawnerInfo(player, spawner);
                break;
            case "sell_and_exp":
                handleSellInventory(player, spawner);
                break;
            case "sell_all":
                handleSellAll(player, spawner);
                break;
            case "collect_exp":
                handleExpCollection(player, spawner);
                break;
        }
    }

    private void handleLootStorage(Player player, SpawnerData spawner) {
        plugin.getSpawnerMenuAction().handleChestClick(player, spawner);
    }

    private void handleSpawnerInfo(Player player, SpawnerData spawner) {
        if (!player.hasPermission("smartspawner.stack")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        plugin.getSpawnerStackerUI().openStackerGui(player, spawner);
    }

    private void handleSellInventory(Player player, SpawnerData spawner) {
        if (!plugin.hasSellIntegration() || !player.hasPermission("smartspawner.sellall")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        // Collect XP and sell all items
        plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, true);
        plugin.getSpawnerSellManager().sellAllItems(player, spawner);
    }

    private void handleSellAll(Player player, SpawnerData spawner) {
        if (!plugin.hasSellIntegration() || !player.hasPermission("smartspawner.sellall")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        // Sell all items only (no XP collection)
        plugin.getSpawnerSellManager().sellAllItems(player, spawner);
    }

    private void handleExpCollection(Player player, SpawnerData spawner) {
        plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, false);
    }

    private void openFallbackForm(Player player, SpawnerData spawner, String title) {
        // Fallback to original hard-coded form if layout is not available
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("entity", languageManager.getFormattedMobName(spawner.getEntityType()));
        placeholders.put("amount", String.valueOf(spawner.getStackSize()));

        String lootButtonText = languageManager.getGuiItemName("bedrock_gui.buttons.storage", placeholders);
        String infoButtonText = languageManager.getGuiItemName("bedrock_gui.buttons.stack_info", placeholders);
        String expButtonText = languageManager.getGuiItemName("bedrock_gui.buttons.experience", placeholders);

        SimpleForm form = SimpleForm.builder()
                .title(title)
                .content(createInfoContent(player, spawner))
                .button(lootButtonText, FormImage.Type.URL, "https://img.icons8.com/?size=100&id=e78DnJp8bhmX&format=png&color=000000")
                .button(infoButtonText, FormImage.Type.URL, "https://static.wikia.nocookie.net/minecraft_gamepedia/images/c/cf/Spawner_with_fire.png/revision/latest?cb=20190925003048")
                .button(expButtonText, FormImage.Type.URL, "https://static.wikia.nocookie.net/minecraft_gamepedia/images/1/10/Bottle_o%27_Enchanting.gif/revision/latest?cb=20200428012753")
                .closedOrInvalidResultHandler(() -> {})
                .validResultHandler(response -> {
                    int buttonId = response.clickedButtonId();
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        switch (buttonId) {
                            case 0: handleLootStorage(player, spawner); break;
                            case 1: handleSpawnerInfo(player, spawner); break;
                            case 2: handleExpCollection(player, spawner); break;
                        }
                    });
                })
                .build();

        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(form);
    }

    public static boolean isBedrockPlayer(Player player) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (Exception e) {
            return false;
        }
    }

    private static class ButtonInfo {
        final String action;
        final String text;
        final String imageUrl;

        ButtonInfo(String action, String text, String imageUrl) {
            this.action = action;
            this.text = text;
            this.imageUrl = imageUrl;
        }
    }
}