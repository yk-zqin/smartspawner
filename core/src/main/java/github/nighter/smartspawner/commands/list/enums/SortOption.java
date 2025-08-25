package github.nighter.smartspawner.commands.list.enums;

import lombok.Getter;

@Getter
public enum SortOption {
    DEFAULT("sort.default"),
    STACK_SIZE_DESC("sort.stack_size_desc"),
    STACK_SIZE_ASC("sort.stack_size_asc");


    private final String langPath;

    SortOption(String langPath) {
        this.langPath = langPath;
    }

    public SortOption getNextOption() {
        return switch (this) {
            case STACK_SIZE_DESC -> DEFAULT;
            case STACK_SIZE_ASC -> STACK_SIZE_DESC;
            case DEFAULT -> STACK_SIZE_ASC;
        };
    }

    public String getColorPath() {
        return langPath + ".color";
    }

    public String getName() {
        return name().toLowerCase();
    }
}
