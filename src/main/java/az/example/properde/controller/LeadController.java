package az.example.properde.controller;

import az.example.properde.dao.dto.lead.LeadRequestDTO;
import az.example.properde.dao.dto.lead.LeadResponseDTO;
import az.example.properde.dao.dto.lead.StatsDTO;
import az.example.properde.dao.enums.LeadSource;
import az.example.properde.service.LeadService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://localhost:5173",
        "http://localhost:5174",
        "http://127.0.0.1:5173",
        "http://127.0.0.1:5174"
})
@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
@Tag(name = "Properde Lead API", description = "Müştəri müraciətlərini idarə edən mərkəz")
public class LeadController {

    private final LeadService leadService;
    private final ObjectMapper objectMapper;


    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Yeni JSON müraciət daxil et", description = "Promo, səbət və sadə form müraciətlərini qəbul edir")
    public ResponseEntity<LeadResponseDTO> createLeadJson(@Valid @RequestBody LeadRequestDTO dto) {
        // Promo slider currently posts only name/phone to /api/leads. When no
        // order/wishlist fields exist, keep that request as a promo lead.
        applyDefaultSourceForSimpleLead(dto, LeadSource.DISCOUNT);
        return ResponseEntity.ok(leadService.createLead(dto, null));
    }

    @PostMapping(value = "/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Yeni JSON müraciət daxil et", description = "Səbət və sadə form müraciətlərini qəbul edir")
    public ResponseEntity<LeadResponseDTO> submitLeadJson(@Valid @RequestBody LeadRequestDTO dto) {
        return ResponseEntity.ok(leadService.createLead(dto, null));
    }
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Multipart müraciət daxil et", description = "Virtual dizayn, ölçü alımı və şəkilli formaları qəbul edir")
    public ResponseEntity<LeadResponseDTO> uploadLeadMultipart(
            @RequestParam(required = false) MultiValueMap<String, String> fields,
            @RequestPart(value = "data", required = false) String dataJson,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "visualizationImage", required = false) MultipartFile visualizationImage,
            @RequestPart(value = "upload", required = false) MultipartFile upload) {
        MultipartFile uploadedFile = firstFile(image, file, photo, visualizationImage, upload);
        LeadRequestDTO dto = resolveMultipartDto(fields, dataJson);
        if (!StringUtils.hasText(dto.getSource())) {
            dto.setSource(uploadedFile == null ? LeadSource.MEASURE.name() : LeadSource.VISUAL.name());
        }
        return ResponseEntity.ok(leadService.createLead(dto, uploadedFile));
    }


    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Yeni multipart müraciət daxil et", description = "UI-dan gələn multipart müraciəti qəbul edir")
    public ResponseEntity<LeadResponseDTO> submitLeadMultipart(
            @RequestParam(required = false) MultiValueMap<String, String> fields,
            @RequestPart(value = "data", required = false) String dataJson,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "visualizationImage", required = false) MultipartFile visualizationImage,
            @RequestPart(value = "upload", required = false) MultipartFile upload) {
        return ResponseEntity.ok(leadService.createLead(resolveMultipartDto(fields, dataJson), firstFile(image, file, photo, visualizationImage, upload)));
    }

    /**
     * Root multipart endpoint for clients that post FormData directly to /api/leads.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Root multipart müraciət daxil et", description = "FormData-nı /api/leads endpointində qəbul edir")
    public ResponseEntity<LeadResponseDTO> createLeadMultipart(
            @RequestParam(required = false) MultiValueMap<String, String> fields,
            @RequestPart(value = "data", required = false) String dataJson,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "visualizationImage", required = false) MultipartFile visualizationImage,
            @RequestPart(value = "upload", required = false) MultipartFile upload) {
        return ResponseEntity.ok(leadService.createLead(resolveMultipartDto(fields, dataJson), firstFile(image, file, photo, visualizationImage, upload)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistika datası", description = "Dashboard-dakı kartlar üçün sayları gətirir")
    public ResponseEntity<List<StatsDTO>> getStats() {
        return ResponseEntity.ok(leadService.getStatistics());
    }

    @GetMapping
    @Operation(summary = "Müraciətləri siyahıla", description = "Admin panelindəki cədvəl üçün")
    public ResponseEntity<List<LeadResponseDTO>> getAllLeads(
            @RequestParam(defaultValue = "ALL") String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String referrer,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(leadService.getAllLeads(source, status, referrer, search));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Statusu yenilə")
    public ResponseEntity<LeadResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestBody(required = false) Map<String, String> body) {
        String resolvedStatus = status != null ? status : body == null ? null : body.get("status");
        return ResponseEntity.ok(leadService.updateStatus(id, resolvedStatus));
    }

    @PatchMapping("/{id}/contacted")
    @Operation(summary = "Əlaqə saxlanıldı statusunu yenilə")
    public ResponseEntity<LeadResponseDTO> updateContacted(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean contacted,
            @RequestBody(required = false) Map<String, Boolean> body) {
        Boolean resolvedContacted = contacted != null ? contacted : body == null ? null : body.get("contacted");
        return ResponseEntity.ok(leadService.updateContacted(id, resolvedContacted));
    }

    @DeleteMapping("/{id}/soft-delete")
    @Operation(summary = "Müraciəti admin cədvəlindən gizlət", description = "Soft delete edir: sətir görünmür, DB-də qalır")
    public ResponseEntity<Void> softDelete(@PathVariable Long id) {
        leadService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/promo")
    @Operation(summary = "Promokod və mesajı lead üzərinə yaz")
    public ResponseEntity<LeadResponseDTO> updatePromo(
            @PathVariable Long id,
            @RequestParam(required = false) String promoCode,
            @RequestParam(required = false) String message,
            @RequestBody(required = false) Map<String, String> body) {
        String resolvedPromoCode = promoCode != null ? promoCode : body == null ? null : body.get("promoCode");
        String resolvedMessage = message != null ? message : body == null ? null : body.get("message");
        return ResponseEntity.ok(leadService.updatePromoCode(id, resolvedPromoCode, resolvedMessage));
    }

    private void applyDefaultSourceForSimpleLead(LeadRequestDTO dto, LeadSource fallback) {
        if (dto == null || StringUtils.hasText(dto.getSource())) {
            return;
        }
        boolean hasProducts = (dto.getRequestedProducts() != null && !dto.getRequestedProducts().isEmpty())
                || (dto.getLikedProducts() != null && !dto.getLikedProducts().isEmpty())
                || (dto.getLikedProductLinks() != null && !dto.getLikedProductLinks().isEmpty())
                || dto.getTotalAmount() != null
                || StringUtils.hasText(dto.getMessage());
        if (!hasProducts) {
            dto.setSource(fallback.name());
        }
    }

    private LeadRequestDTO resolveMultipartDto(MultiValueMap<String, String> fields, String dataJson) {
        MultiValueMap<String, String> safeFields = fields == null ? new LinkedMultiValueMap<>() : fields;
        String jsonPayload = StringUtils.hasText(dataJson) ? dataJson : first(safeFields, "data", "payload", "lead");
        LeadRequestDTO dto = parseJsonData(jsonPayload);

        setIfBlank(dto::getFullName, dto::setFullName, first(safeFields, "fullName", "name", "customerName", "fullname"));
        setIfBlank(dto::getPhone, dto::setPhone, first(safeFields, "phone", "phoneNumber", "number", "mobile"));
        setIfBlank(dto::getEmail, dto::setEmail, first(safeFields, "email"));
        setIfBlank(dto::getMessage, dto::setMessage, first(safeFields, "message", "note", "comment"));
        setIfBlank(dto::getSource, dto::setSource, first(safeFields, "source", "leadSource", "type", "formType"));
        setIfBlank(dto::getReferrer, dto::setReferrer, first(safeFields, "referrer", "referer", "utmSource"));
        setIfBlank(dto::getPromoCode, dto::setPromoCode, first(safeFields, "promoCode", "promo", "promo_code", "discountCode"));

        if (dto.getTotalAmount() == null) {
            dto.setTotalAmount(parseDouble(first(safeFields, "totalAmount", "amount", "total", "totalPrice")));
        }
        if (dto.getRequestedProducts() == null || dto.getRequestedProducts().isEmpty()) {
            dto.setRequestedProducts(list(safeFields, "requestedProducts", "products", "cartProducts", "selectedProducts", "requestedProductNames"));
        }
        if (dto.getLikedProducts() == null || dto.getLikedProducts().isEmpty()) {
            dto.setLikedProducts(list(safeFields, "likedProducts", "likedProductNames", "favorites", "favoriteProducts", "wishlist", "wishlistProducts"));
        }
        if (dto.getLikedProductLinks() == null || dto.getLikedProductLinks().isEmpty()) {
            dto.setLikedProductLinks(list(safeFields, "likedProductLinks", "favoriteProductLinks", "wishlistProductLinks"));
        }
        return dto;
    }

    private LeadRequestDTO parseJsonData(String dataJson) {
        if (!StringUtils.hasText(dataJson)) {
            return new LeadRequestDTO();
        }
        try {
            return objectMapper.readValue(dataJson, LeadRequestDTO.class);
        } catch (Exception ignored) {
            return new LeadRequestDTO();
        }
    }

    private MultipartFile firstFile(MultipartFile... files) {
        if (files == null) {
            return null;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                return file;
            }
        }
        return null;
    }

    private String first(MultiValueMap<String, String> fields, String... keys) {
        for (String key : keys) {
            List<String> values = fields.get(key);
            if (values != null) {
                for (String value : values) {
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private List<String> list(MultiValueMap<String, String> fields, String... keys) {
        List<String> values = new ArrayList<>();
        for (String key : keys) {
            List<String> rawValues = fields.get(key);
            if (rawValues == null) {
                continue;
            }
            for (String raw : rawValues) {
                values.addAll(parseListValue(raw));
            }
        }
        return values;
    }

    private List<String> parseListValue(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
            } catch (Exception ignored) {
                // Fall through to delimiter parsing.
            }
        }
        return List.of(trimmed.split("\\s*[,|;\\n]\\s*"))
                .stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private Double parseDouble(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value.replace(',', '.').trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void setIfBlank(java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter, String value) {
        if (!StringUtils.hasText(getter.get()) && StringUtils.hasText(value)) {
            setter.accept(value.trim());
        }
    }
}
