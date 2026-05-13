package az.example.properde.dao.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Keep this enum aligned with the existing database values.
 *
 * Frontend/business aliases such as PROMO, VISUALIZATION, MEASUREMENT,
 * WISHLIST, FAVORITES, GENERAL, ORDERS are accepted by fromString(), but they
 * are normalized to the existing enum constants below so Hibernate enum
 * hydration does not break for old rows.
 */
public enum LeadSource {
    ALL("Bütün Mənbələr"),
    ORDER("Sifariş"),
    // Legacy values kept so old database rows do not break enum hydration.
    VISUAL("Vizualizasiya"),
    DISCOUNT("Promo Kod"),
    MEASURE("Ölçü Alımı"),
    HEART("Ürək");

    private final String displayName;

    LeadSource(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    public String getValue() {
        return name();
    }

    public String getBusinessKey() {
        return switch (this) {
            case ALL -> "ALL";
            case ORDER -> "ORDER";
            case VISUAL -> "VISUALIZATION";
            case DISCOUNT -> "PROMO";
            case MEASURE -> "MEASUREMENT";
            case HEART -> "WISHLIST";
        };
    }

    public LeadSource canonical() {
        return this;
    }

    public boolean isAllFilter() {
        return this == ALL;
    }

    @JsonCreator
    public static LeadSource fromString(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        return switch (normalized) {
            case "ALL", "HAMISI", "BÜTÜN_MƏNBƏLƏR", "BUTUN_MENBELER", "GENERAL", "CONTACT", "UNKNOWN" -> ALL;
            case "ORDER", "ORDERS", "CART", "BASKET", "SIFARIS", "SİFARİŞ" -> ORDER;
            case "PROMO", "PROMO_CODE", "PROMOCODE", "DISCOUNT", "ENDIRIM", "ENDİRİM", "PROMO_KOD" -> DISCOUNT;
            case "VISUAL", "VISUALIZATION", "VISUALISATION", "VIRTUAL", "VIRTUAL_DESIGN", "VIRTUAL_DIZAYN", "VIRTUAL_DİZAYN", "DIZAYN", "DİZAYN", "DESIGN", "VIZUALIZASIYA", "VİZUALİZASİYA" -> VISUAL;
            case "MEASURE", "MEASUREMENT", "OLCU", "ÖLÇÜ", "OLCU_ALIMI", "ÖLÇÜ_ALIMI" -> MEASURE;
            case "HEART", "WISHLIST", "FAVORITE", "FAVORITES", "LIKED", "LIKES", "URƏK", "ÜRƏK" -> HEART;
            default -> {
                for (LeadSource source : LeadSource.values()) {
                    if (source.name().equalsIgnoreCase(value) || source.displayName.equalsIgnoreCase(value)) {
                        yield source;
                    }
                }
                yield ALL;
            }
        };
    }
}
