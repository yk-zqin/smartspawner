package me.nighter.smartSpawner.utils;

import me.nighter.smartSpawner.managers.LanguageManager;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryOpenEvent;


import java.util.logging.Logger;

public class MenuTitleValidator {
    private final LanguageManager languageManager;
    private static final Logger logger = Logger.getLogger("SmartSpawner");

    public MenuTitleValidator(LanguageManager languageManager) {
        this.languageManager = languageManager;
    }

    public boolean isValidSpawnerMenu(InventoryOpenEvent inventory, SpawnerData spawner) {
        String expectedTitle = generateExpectedTitle(spawner);
        String actualTitle = inventory.getView().getTitle();
//        logger.info("Expected title: " + expectedTitle);
//        logger.info("Actual title: " + actualTitle);
        return expectedTitle.equals(actualTitle);
    }

    public boolean isValidSpawnerMenu(Player player, SpawnerData spawner) {
        String expectedTitle = generateExpectedTitle(spawner);
        String actualTitle = player.getOpenInventory().getTitle();
//        logger.info("Expected title: " + expectedTitle);
//        logger.info("Actual title: " + actualTitle);
        return expectedTitle.equals(actualTitle);
    }
    private String generateExpectedTitle(SpawnerData spawner) {
        String entityName = formatEntityName(spawner.getEntityType().name());
        if (spawner.getStackSize() > 1) {
            return languageManager.getGuiTitle("gui-title.stacked-menu",
                    "%amount%", String.valueOf(spawner.getStackSize()),
                    "%entity%", entityName);
        } else {
            return languageManager.getGuiTitle("gui-title.menu",
                    "%entity%", entityName);
        }
    }

    private String formatEntityName(String entityName) {
        return WordUtils.capitalizeFully(entityName.replace("_", " "));
    }
}