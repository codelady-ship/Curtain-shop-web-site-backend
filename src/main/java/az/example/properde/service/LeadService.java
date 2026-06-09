package az.example.properde.service;

import az.example.properde.dao.dto.lead.LeadRequestDTO;
import az.example.properde.dao.dto.lead.LeadResponseDTO;
import az.example.properde.dao.dto.lead.StatsDTO;
import az.example.properde.dao.entity.Lead;
import az.example.properde.dao.enums.LeadSource;
import az.example.properde.dao.enums.LeadStatus;
import az.example.properde.mapper.LeadMapper;
import az.example.properde.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadMapper leadMapper;
    private final FileStorageService fileStorageService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public LeadResponseDTO createLead(LeadRequestDTO dto, MultipartFile image) {
        if (dto == null) {
            throw new IllegalArgumentException("Lead payload is required");
        }

        LeadSource leadSource = resolveSource(dto, image);
        validateLeadPayload(dto, image, leadSource);

        Lead lead = new Lead();
        lead.setFullName(clean(dto.getFullName()));
        lead.setPhone(normalizePhone(dto.getPhone()));
        lead.setEmail(clean(dto.getEmail()));
        lead.setMessage(clean(dto.getMessage()));
        lead.setReferrer(normalizeReferrer(dto.getReferrer()));
        lead.setRequestedProducts(join(dto.getRequestedProducts()));
        lead.setLikedProductsSummary(join(dto.getLikedProducts()));
        lead.setLikedProductLinks(join(dto.getLikedProductLinks()));
        lead.setTotalAmount(dto.getTotalAmount());
        lead.setPromoCode(clean(dto.getPromoCode()));
        lead.setContacted(false);
        lead.setStatus(LeadStatus.NEW.name());
        lead.setSource(leadSource.name());

        if (image != null && !image.isEmpty()) {
            lead.setVisualizationImageUrl(fileStorageService.save(image));
            lead.setSource(LeadSource.VISUAL.name());
        }

        return leadMapper.toDto(leadRepository.save(lead));
    }

    @Transactional
    public Lead submitLead(Lead lead) {
        LeadSource normalizedSource = LeadSource.fromString(lead.getSource());
        lead.setSource(normalizedSource.canonical().name());
        if (!StringUtils.hasText(lead.getStatus())) {
            lead.setStatus(LeadStatus.NEW.name());
        }
        if (lead.getContacted() == null) {
            lead.setContacted(false);
        }
        if (!StringUtils.hasText(lead.getReferrer())) {
            lead.setReferrer("WEBSITE");
        }
        return leadRepository.save(lead);
    }

    /**
     * Admin listing is intentionally read through JdbcTemplate instead of JPA.
     *
     * Reason: existing client databases may contain older lead schemas and older
     * source/status values. Hibernate entity hydration can fail before we can
     * normalize a row, which causes GET /api/leads?source=ALL to return 500 even
     * though inserts are successful. This reader inspects the live leads table,
     * uses only columns that actually exist, and maps rows defensively.
     */
    @Transactional(readOnly = true)
    public List<LeadResponseDTO> getAllLeads(String source, String status, String referrer, String search) {
        Set<String> columns = getLeadColumns();
        if (columns.isEmpty()) {
            return List.of();
        }

        String sourceColumn = column(columns, "source");
        String statusColumn = column(columns, "status");
        String referrerColumn = column(columns, "referrer", "referer");
        String createdColumn = column(columns, "created_at", "createdat", "created");
        String idColumn = column(columns, "id");

        String normalizedSource = normalizeSourceFilter(source);
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedReferrer = normalizeReferrerFilter(referrer);
        String searchQuery = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM leads WHERE 1=1");
        String deletedColumn = column(columns, "deleted");
        if (deletedColumn != null) {
            sql.append(" AND COALESCE(").append(deletedColumn).append(", false) = false");
        }

        if (normalizedSource != null && sourceColumn != null) {
            sql.append(" AND UPPER(").append(sourceColumn).append(") = ?");
            params.add(normalizedSource);
        }
        if (normalizedStatus != null && statusColumn != null) {
            sql.append(" AND UPPER(").append(statusColumn).append(") = ?");
            params.add(normalizedStatus);
        }
        if (normalizedReferrer != null && referrerColumn != null) {
            sql.append(" AND UPPER(").append(referrerColumn).append(") = ?");
            params.add(normalizedReferrer);
        }
        if (StringUtils.hasText(searchQuery)) {
            List<String> searchableColumns = List.of(
                            "full_name", "name", "customer_name", "fullname", "phone", "email", "message",
                            "promo_code", "requested_products", "liked_products_summary", "liked_product_links")
                    .stream()
                    .map(candidate -> column(columns, candidate))
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            if (!searchableColumns.isEmpty()) {
                sql.append(" AND (");
                for (int i = 0; i < searchableColumns.size(); i++) {
                    if (i > 0) {
                        sql.append(" OR ");
                    }
                    sql.append("LOWER(COALESCE(CAST(").append(searchableColumns.get(i)).append(" AS TEXT), '')) LIKE ?");
                    params.add("%" + searchQuery + "%");
                }
                sql.append(")");
            }
        }

        if (createdColumn != null) {
            sql.append(" ORDER BY ").append(createdColumn).append(" DESC NULLS LAST");
            if (idColumn != null) {
                sql.append(", ").append(idColumn).append(" DESC");
            }
        } else if (idColumn != null) {
            sql.append(" ORDER BY ").append(idColumn).append(" DESC");
        }

        return jdbcTemplate.queryForList(sql.toString(), params.toArray())
                .stream()
                .map(row -> mapLeadRow(row, columns))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StatsDTO> getStatistics() {
        Map<LeadSource, Long> sourceCounts = getCanonicalSourceCounts();
        long totalCount = safeTotalLeadCount();

        return Arrays.stream(new LeadSource[]{
                        LeadSource.ALL,
                        LeadSource.ORDER,
                        LeadSource.DISCOUNT,
                        LeadSource.VISUAL,
                        LeadSource.MEASURE,
                        LeadSource.HEART
                })
                .map(source -> new StatsDTO(
                        source.name(),
                        getLabel(source),
                        source == LeadSource.ALL ? totalCount : sourceCounts.getOrDefault(source, 0L),
                        ""
                ))
                .toList();
    }

    @Transactional
    public LeadResponseDTO updateStatus(Long id, String status) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Müraciət tapılmadı: " + id));
        String normalized = normalizeLeadStatusValue(status);
        lead.setStatus(normalized);
        if (LeadStatus.CONTACTED.name().equalsIgnoreCase(normalized)) {
            lead.setContacted(true);
        }
        return leadMapper.toDto(leadRepository.save(lead));
    }

    @Transactional
    public LeadResponseDTO updateContacted(Long id, Boolean contacted) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Müraciət tapılmadı: " + id));
        boolean isContacted = Boolean.TRUE.equals(contacted);
        lead.setContacted(isContacted);
        if (isContacted) {
            lead.setStatus(LeadStatus.CONTACTED.name());
        } else if (!StringUtils.hasText(lead.getStatus())) {
            lead.setStatus(LeadStatus.NEW.name());
        }
        return leadMapper.toDto(leadRepository.save(lead));
    }

    @Transactional
    public LeadResponseDTO updatePromoCode(Long id, String promoCode) {
        return updatePromoCode(id, promoCode, null);
    }

    @Transactional
    public LeadResponseDTO updatePromoCode(Long id, String promoCode, String message) {
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Müraciət tapılmadı: " + id));
        lead.setPromoCode(clean(promoCode));
        if (message != null) {
            lead.setMessage(clean(message));
        }
        if (StringUtils.hasText(promoCode)) {
            lead.setStatus(LeadStatus.PROMO_SENT.name());
        }
        return leadMapper.toDto(leadRepository.save(lead));
    }

    private LeadResponseDTO mapLeadRow(Map<String, Object> rawRow, Set<String> knownColumns) {
        Map<String, Object> row = normalizeRowKeys(rawRow);
        String rawSource = string(row, "source");
        String rawStatus = string(row, "status");
        String rawReferrer = firstString(row, "referrer", "referer");
        String fullName = firstString(row, "full_name", "name", "customer_name", "fullname");

        return LeadResponseDTO.builder()
                .id(longValue(first(row, "id")))
                .fullName(fullName)
                .name(fullName)
                .phone(string(row, "phone"))
                .email(string(row, "email"))
                .message(string(row, "message"))
                .referrer(StringUtils.hasText(rawReferrer) ? normalizeReferrer(rawReferrer) : "WEBSITE")
                .source(LeadSource.fromString(rawSource).canonical().name())
                .status(normalizeLeadStatusValue(rawStatus))
                .contacted(booleanValue(first(row, "contacted")))
                .promoCode(firstString(row, "promo_code", "promoCode"))
                .requestedProducts(firstString(row, "requested_products", "requestedProducts"))
                .likedProductsSummary(firstString(row, "liked_products_summary", "likedProductsSummary"))
                .likedProductLinks(firstString(row, "liked_product_links", "likedProductLinks"))
                .totalAmount(doubleValue(first(row, "total_amount", "totalAmount")))
                .visualizationImageUrl(firstString(row, "visualization_image_url", "visualizationImageUrl", "image_url", "imageUrl"))
                .deleted(booleanValue(first(row, "deleted")))
                .createdAt(localDateTime(first(row, "created_at", "createdAt", "created")))
                .updatedAt(localDateTime(first(row, "updated_at", "updatedAt", "updated")))
                .build();
    }

    @Transactional
    public void softDelete(Long id) {
        Set<String> columns = getLeadColumns();
        if (column(columns, "deleted") != null) {
            jdbcTemplate.update("UPDATE leads SET deleted = true, updated_at = NOW() WHERE id = ?", id);
            return;
        }
        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Müraciət tapılmadı: " + id));
        leadRepository.delete(lead);
    }

    private Map<String, Object> normalizeRowKeys(Map<String, Object> row) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        row.forEach((key, value) -> normalized.put(key == null ? "" : key.toLowerCase(Locale.ROOT), value));
        return normalized;
    }

    private Set<String> getLeadColumns() {
        return jdbcTemplate.queryForList("""
                        SELECT lower(column_name) AS column_name
                        FROM information_schema.columns
                        WHERE table_schema = current_schema()
                          AND table_name = 'leads'
                        """, String.class)
                .stream()
                .collect(Collectors.toSet());
    }

    private String column(Set<String> columns, String... candidates) {
        if (columns == null || columns.isEmpty()) {
            return null;
        }
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            String normalized = candidate.toLowerCase(Locale.ROOT);
            if (columns.contains(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private Object first(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            Object value = row.get(key.toLowerCase(Locale.ROOT));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstString(Map<String, Object> row, String... keys) {
        Object value = first(row, keys);
        return value == null ? null : String.valueOf(value);
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key.toLowerCase(Locale.ROOT));
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            try {
                return Double.parseDouble(String.valueOf(value).replace(',', '.'));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return normalized.equals("true") || normalized.equals("1") || normalized.equals("yes") || normalized.equals("bəli");
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private long safeTotalLeadCount() {
        try {
            Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM leads WHERE COALESCE(deleted, false) = false", Long.class);
            return total == null ? 0L : total;
        } catch (Exception ignored) {
            try {
                Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM leads", Long.class);
                return total == null ? 0L : total;
            } catch (Exception ignoredAgain) {
                return 0L;
            }
        }
    }

    private Map<LeadSource, Long> getCanonicalSourceCounts() {
        Map<LeadSource, Long> counts = new EnumMap<>(LeadSource.class);
        Set<String> columns = getLeadColumns();
        String sourceColumn = column(columns, "source");

        if (sourceColumn == null) {
            return counts;
        }

        try {
            jdbcTemplate.queryForList("SELECT " + sourceColumn + " AS source, COUNT(*) AS count FROM leads WHERE COALESCE(deleted, false) = false GROUP BY " + sourceColumn)
                    .forEach(row -> {
                        LeadSource normalizedSource = LeadSource.fromString(row.get("source") == null ? null : String.valueOf(row.get("source"))).canonical();
                        Long count = longValue(row.get("count"));
                        if (count != null && normalizedSource != LeadSource.ALL) {
                            counts.merge(normalizedSource, count, Long::sum);
                        }
                    });
        } catch (Exception ignored) {
            for (LeadSource source : List.of(LeadSource.ORDER, LeadSource.DISCOUNT, LeadSource.VISUAL, LeadSource.MEASURE, LeadSource.HEART)) {
                try {
                    counts.put(source, leadRepository.countBySource(source.name()));
                } catch (Exception ignoredAgain) {
                    counts.put(source, 0L);
                }
            }
        }

        return counts;
    }


    private void validateLeadPayload(LeadRequestDTO dto, MultipartFile image, LeadSource leadSource) {
        String normalizedPhone = normalizePhone(dto.getPhone());
        if (!StringUtils.hasText(normalizedPhone) || !isValidPhone(normalizedPhone)) {
            throw new IllegalArgumentException("Düzgün nömrə daxil edin");
        }

        if ((leadSource == LeadSource.MEASURE || leadSource == LeadSource.VISUAL || leadSource == LeadSource.ORDER)
                && !StringUtils.hasText(dto.getFullName())) {
            throw new IllegalArgumentException("Ad və soyad daxil edin");
        }

        if (leadSource == LeadSource.VISUAL && (image == null || image.isEmpty())) {
            throw new IllegalArgumentException("Vizualizasiya üçün şəkil yükləyin");
        }
    }

    private boolean isValidPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return false;
        }
        String digits = phone.replaceAll("\\D", "");
        return digits.matches("^(0\\d{9}|994\\d{9}|\\d{9})$");
    }

    private LeadSource resolveSource(LeadRequestDTO dto, MultipartFile image) {
        if (image != null && !image.isEmpty()) {
            return LeadSource.VISUAL;
        }
        LeadSource requestedSource = LeadSource.fromString(dto.getSource());
        if (requestedSource != null && requestedSource != LeadSource.ALL) {
            return requestedSource.canonical();
        }
        if (dto.getRequestedProducts() != null && !dto.getRequestedProducts().isEmpty()) {
            return LeadSource.ORDER;
        }
        if (dto.getLikedProducts() != null && !dto.getLikedProducts().isEmpty()) {
            return LeadSource.HEART;
        }
        if (StringUtils.hasText(dto.getPromoCode())) {
            return LeadSource.DISCOUNT;
        }
        return LeadSource.ALL;
    }

    private String normalizeSourceFilter(String source) {
        LeadSource normalized = LeadSource.fromString(source);
        if (normalized == null || normalized == LeadSource.ALL) {
            return null;
        }
        return normalized.canonical().name();
    }

    private String normalizeStatusFilter(String status) {
        if (!StringUtils.hasText(status) || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        return normalizeLeadStatusValue(status).toUpperCase(Locale.ROOT);
    }

    private String normalizeLeadStatusValue(String status) {
        if (!StringUtils.hasText(status)) {
            return LeadStatus.NEW.name();
        }

        String trimmed = status.trim();
        String normalized = trimmed.toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        return switch (normalized) {
            case "NEW", "YENI", "YENİ" -> LeadStatus.NEW.name();
            case "CONTACTED", "CALLED", "ZENG_EDILDI", "ZƏNG_EDİLDİ", "ZƏNG_EDILDI" -> LeadStatus.CONTACTED.name();
            case "IN_PROGRESS", "PROGRESS", "PENDING" -> LeadStatus.IN_PROGRESS.name();
            case "PROMO_SENT", "PROMO" -> LeadStatus.PROMO_SENT.name();
            case "COMPLETED", "DONE", "FINISHED" -> LeadStatus.COMPLETED.name();
            case "CANCELLED", "CANCELED", "CANCEL" -> LeadStatus.CANCELLED.name();
            default -> {
                for (LeadStatus leadStatus : LeadStatus.values()) {
                    if (leadStatus.name().equalsIgnoreCase(trimmed) || leadStatus.getDisplayName().equalsIgnoreCase(trimmed)) {
                        yield leadStatus.canonicalName();
                    }
                }
                yield trimmed;
            }
        };
    }

    private String normalizeReferrerFilter(String referrer) {
        if (!StringUtils.hasText(referrer) || "ALL".equalsIgnoreCase(referrer.trim())) {
            return null;
        }
        return normalizeReferrer(referrer);
    }

    private String getLabel(LeadSource source) {
        if (source == null) {
            return "Ümumi";
        }
        return switch (source.canonical()) {
            case ORDER -> "Sifarişlər";
            case DISCOUNT -> "Promokod";
            case VISUAL -> "Vizualizasiya";
            case MEASURE -> "Ölçü Alımı";
            case HEART -> "İstək Siyahısı";
            case ALL -> "Ümumi";
        };
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        return phone.replaceAll("\\D", "");
    }

    private String normalizeReferrer(String referrer) {
        if (!StringUtils.hasText(referrer)) {
            return "WEBSITE";
        }
        String normalized = referrer.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "FB" -> "FACEBOOK";
            case "IG" -> "INSTAGRAM";
            case "WA" -> "WHATSAPP";
            case "DIRECT", "FACEBOOK", "INSTAGRAM", "WHATSAPP", "GOOGLE", "WEBSITE", "UNKNOWN" -> normalized;
            default -> normalized;
        };
    }
}
