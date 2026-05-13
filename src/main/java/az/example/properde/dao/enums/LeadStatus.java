package az.example.properde.dao.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum LeadStatus {
    NEW("Yeni"),
    CONTACTED("Əlaqə saxlanıldı"),
    IN_PROGRESS("İcradadır"),
    PROMO_SENT("Promo göndərildi"),
    COMPLETED("Tamamlandı"),
    CANCELLED("Ləğv edildi"),

    // Legacy value kept for compatibility with old UI/status labels.
    CALLED("Zəng edildi");

    private final String displayName;

    LeadStatus(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    public String canonicalName() {
        return this == CALLED ? CONTACTED.name() : name();
    }

    @JsonCreator
    public static LeadStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return NEW;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        return switch (normalized) {
            case "NEW", "YENI", "YENİ" -> NEW;
            case "CONTACTED", "CALLED", "ZENG_EDILDI", "ZƏNG_EDİLDİ", "ZƏNG_EDILDI" -> CONTACTED;
            case "IN_PROGRESS", "PROGRESS", "PENDING" -> IN_PROGRESS;
            case "PROMO_SENT", "PROMO" -> PROMO_SENT;
            case "COMPLETED", "DONE", "FINISHED" -> COMPLETED;
            case "CANCELLED", "CANCELED", "CANCEL" -> CANCELLED;
            default -> {
                for (LeadStatus status : LeadStatus.values()) {
                    if (status.name().equalsIgnoreCase(value) || status.displayName.equalsIgnoreCase(value)) {
                        yield status == CALLED ? CONTACTED : status;
                    }
                }
                yield NEW;
            }
        };
    }
}
