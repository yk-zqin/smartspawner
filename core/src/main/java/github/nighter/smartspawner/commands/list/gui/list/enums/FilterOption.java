package github.nighter.smartspawner.commands.list.gui.list.enums;

import lombok.Getter;

@Getter
public enum FilterOption {
    ALL("filter.all"),
    ACTIVE("filter.active"),
    INACTIVE("filter.inactive");

    private final String langPath;

    FilterOption(String langPath) {
        this.langPath = langPath;
    }

    public FilterOption getNextOption() {
        return switch (this) {
            case ALL -> ACTIVE;
            case ACTIVE -> INACTIVE;
            case INACTIVE -> ALL;
        };
    }

    public String getColorPath() {
        return langPath + ".color";
    }

    public String getName() {
        return name().toLowerCase();
    }
}