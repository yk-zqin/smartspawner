package github.nighter.smartspawner.spawner.gui.layout;

import lombok.Getter;
import org.bukkit.Material;

@Getter
public class GuiButton {
    private final String buttonType;
    private final int slot;
    private final Material material;
    private final boolean enabled;

    public GuiButton(String buttonType, int slot, Material material, boolean enabled) {
        this.buttonType = buttonType;
        this.slot = slot;
        this.material = material;
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "GuiButton{" +
                "buttonType='" + buttonType + '\'' +
                ", slot=" + slot +
                ", material=" + material +
                ", enabled=" + enabled +
                '}';
    }
}