package github.nighter.smartspawner.spawner.gui.main;

import github.nighter.smartspawner.Scheduler;
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

import java.util.*;

public class SpawnerMenuFormUI {
    private final SmartSpawner plugin;
    private final LanguageManager languageManager;
    private final MessageService messageService;

    // Action to button info mapping
    private static final Map<String, ActionButtonInfo> ACTION_BUTTON_CONFIG = new HashMap<>();

    static {
        ACTION_BUTTON_CONFIG.put("open_storage", new ActionButtonInfo(
                "bedrock.main_gui.button_names.storage",
                "https://minecraft.wiki/images/thumb/Chest.gif/150px-Chest.gif"
        ));

        ACTION_BUTTON_CONFIG.put("open_stacker", new ActionButtonInfo(
                "bedrock.main_gui.button_names.stacker",
                "https://minecraft.wiki/images/thumb/Spawner_with_fire.png/150px-Spawner_with_fire.png"
        ));

        ACTION_BUTTON_CONFIG.put("sell_and_exp", new ActionButtonInfo(
                "bedrock.main_gui.button_names.sell_and_exp",
                "https://img.icons8.com/?size=100&id=12815&format=png&color=FFD700"
        ));

        ACTION_BUTTON_CONFIG.put("sell_all", new ActionButtonInfo(
                "bedrock.main_gui.button_names.sell_all",
                "https://img.icons8.com/?size=100&id=12815&format=png&color=FFA500"
        ));

        ACTION_BUTTON_CONFIG.put("collect_exp", new ActionButtonInfo(
                "bedrock.main_gui.button_names.exp",
                "https://minecraft.wiki/images/Bottle_o%27_Enchanting.gif"
        ));
    }

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
            title = languageManager.getGuiTitle("bedrock.main_gui.title_stacked_spawner", placeholders);
        } else {
            title = languageManager.getGuiTitle("bedrock.main_gui.title_single_spawner", placeholders);
        }

        GuiLayout layout = plugin.getGuiLayoutConfig().getCurrentMainLayout();
        List<ButtonInfo> availableButtons = collectAvailableButtons(layout, player, spawner, placeholders);

        if (availableButtons.isEmpty()) {
            messageService.sendMessage(player, "no_permission");
            return;
        }

        SimpleForm.Builder formBuilder = SimpleForm.builder()
                .title(title);

        for (ButtonInfo buttonInfo : availableButtons) {
            formBuilder.button(buttonInfo.text, FormImage.Type.URL, buttonInfo.imageUrl);
        }

        SimpleForm form = formBuilder
                .closedOrInvalidResultHandler(() -> {
                })
                .validResultHandler(response -> {
                    int buttonId = response.clickedButtonId();
                    if (buttonId < availableButtons.size()) {
                        ButtonInfo buttonInfo = availableButtons.get(buttonId);
                        Scheduler.runTask(() -> {
                            switch (buttonInfo.action) {
                                case "open_storage":
                                    plugin.getSpawnerMenuAction().handleStorageClickBedrock(player, spawner);
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
                                default:
                                    plugin.getLogger().warning("Unknown action in FormUI: " + buttonInfo.action);
                                    break;
                            }
                        });
                    }
                })
                .build();
        FloodgateApi.getInstance().getPlayer(player.getUniqueId()).sendForm(form);
    }

    private List<ButtonInfo> collectAvailableButtons(GuiLayout layout, Player player, SpawnerData spawner, Map<String, String> placeholders) {
        List<ButtonInfo> buttons = new ArrayList<>();
        Set<String> addedActions = new HashSet<>();

        // Get all buttons from layout sorted by slot
        List<GuiButton> sortedButtons = layout.getAllButtons().values().stream()
                .filter(GuiButton::isEnabled)
                .sorted(Comparator.comparing(GuiButton::getSlot))
                .toList();

        for (GuiButton button : sortedButtons) {
            // Check condition if present
            if (button.getCondition() != null && !evaluateCondition(button.getCondition())) {
                continue;
            }

            // Get all unique actions from this button
            Set<String> buttonActions = new HashSet<>(button.getActions().values());

            for (String action : buttonActions) {
                // Skip if action already added or if action is "none"
                if (addedActions.contains(action) || "none".equals(action)) {
                    continue;
                }

                // Check if player has permission for this action
                if (!hasPermissionForAction(player, action)) {
                    continue;
                }

                // Get button configuration for this action
                ActionButtonInfo actionConfig = ACTION_BUTTON_CONFIG.get(action);
                if (actionConfig != null) {
                    String text = languageManager.getGuiItemName(actionConfig.langKey, placeholders);
                    buttons.add(new ButtonInfo(action, text, actionConfig.imageUrl));
                    addedActions.add(action);
                }
            }
        }

        return buttons;
    }

    private boolean evaluateCondition(String condition) {
        switch (condition) {
            case "shop_integration":
                return plugin.hasSellIntegration();
            case "no_shop_integration":
                return !plugin.hasSellIntegration();
            default:
                plugin.getLogger().warning("Unknown condition in FormUI: " + condition);
                return true;
        }
    }

    private boolean hasPermissionForAction(Player player, String action) {
        switch (action) {
            case "open_storage":
                return true; // Storage access doesn't require special permission
            case "open_stacker":
                return player.hasPermission("smartspawner.stack");
            case "sell_and_exp":
            case "sell_all":
                return plugin.hasSellIntegration() && player.hasPermission("smartspawner.sellall");
            case "collect_exp":
                return true; // EXP collection doesn't require special permission
            default:
                return false;
        }
    }

    private void handleButtonAction(Player player, SpawnerData spawner, String action) {
        Scheduler.runTask(() -> {
            switch (action) {
                case "open_storage":
                    plugin.getSpawnerMenuAction().handleStorageClickBedrock(player, spawner);
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
                default:
                    plugin.getLogger().warning("Unknown action in FormUI: " + action);
                    break;
            }
        });
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
        plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, true);
        plugin.getSpawnerSellManager().sellAllItems(player, spawner);
    }

    private void handleSellAll(Player player, SpawnerData spawner) {
        if (!plugin.hasSellIntegration() || !player.hasPermission("smartspawner.sellall")) {
            messageService.sendMessage(player, "no_permission");
            return;
        }
        plugin.getSpawnerSellManager().sellAllItems(player, spawner);
    }

    private void handleExpCollection(Player player, SpawnerData spawner) {
        plugin.getSpawnerMenuAction().handleExpBottleClick(player, spawner, false);
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

    private static class ActionButtonInfo {
        final String langKey;
        final String imageUrl;

        ActionButtonInfo(String langKey, String imageUrl) {
            this.langKey = langKey;
            this.imageUrl = imageUrl;
        }
    }
}