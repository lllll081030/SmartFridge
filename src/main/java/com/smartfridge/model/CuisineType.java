package com.smartfridge.model;

public enum CuisineType {
    CHINESE("Chinese"),
    JAPANESE("Japanese"),
    ITALIAN("Italian"),
    MEXICAN("Mexican"),
    INDIAN("Indian"),
    THAI("Thai"),
    KOREAN("Korean"),
    FRENCH("French"),
    AMERICAN("American"),
    MEDITERRANEAN("Mediterranean"),
    MIDDLE_EASTERN("Middle Eastern"),
    OTHER("Other");

    private final String displayName;

    CuisineType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
