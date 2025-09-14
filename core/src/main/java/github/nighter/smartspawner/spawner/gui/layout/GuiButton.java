package github.nighter.smartspawner.spawner.gui.layout;

import lombok.Getter;
import org.bukkit.Material;

import java.util.Map;

@Getter
public class GuiButton {
    private final String buttonType;
    private final int slot;
    private final Material material;
    private final boolean enabled;
    private final String condition;
    private final Map<String, String> actions;

    public GuiButton(String buttonType, int slot, Material material, boolean enabled, String condition, Map<String, String> actions) {
        this.buttonType = buttonType;
        this.slot = slot;
        this.material = material;
        this.enabled = enabled;
        this.condition = condition;
        this.actions = actions;
    }

    public String getAction(String clickType) {
        return actions != null ? actions.get(clickType) : null;
    }

    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }
}