package me.nighter.smartSpawner.utils;

public enum SupportedLanguage {
    ENGLISH("en", "English", "messages/en.yml"),
    VIETNAMESE("vi", "Tiếng Việt", "messages/vi.yml");

    private final String code;
    private final String displayName;
    private final String resourcePath;

    SupportedLanguage(String code, String displayName, String resourcePath) {
        this.code = code;
        this.displayName = displayName;
        this.resourcePath = resourcePath;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public static SupportedLanguage fromCode(String code) {
        for (SupportedLanguage lang : values()) {
            if (lang.getCode().equalsIgnoreCase(code)) {
                return lang;
            }
        }
        return ENGLISH; // Default fallback
    }
}