package az.example.properde.dao.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CategoryType {
    CURTAINS("Dəst Pərdələr"),
    CORNICES("Kornizlər"),
    SUNSHADES("Günəşliklər"),
    BACKGROUNDS("Fonluqlar"),
    TULLES("Tüllər"),
    BLINDS("Jalüzlər"),
    ACCESSORIES("Aksesuarlar");

    private final String categoryName;

    CategoryType(String categoryName) {
        this.categoryName = categoryName;
    }

    @JsonValue
    public String getCategoryName() {
        return categoryName;
    }

    @JsonCreator
    public static CategoryType fromString(String categoryName) {
        for (CategoryType category : CategoryType.values()) {
            if (category.categoryName.equalsIgnoreCase(categoryName) || 
                category.name().equalsIgnoreCase(categoryName)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Naməlum kateqoriya: " + categoryName);
    }
}