package az.example.properde.service;

import az.example.properde.dao.dto.admin.PartnerDto;
import az.example.properde.dao.entity.Partner;
import az.example.properde.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PartnerService {
    private final PartnerRepository repository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<PartnerDto> list(boolean activeOnly) {
        return (activeOnly
                ? repository.findByDeletedFalseAndActiveTrueOrderBySortOrderAscCreatedAtDesc()
                : repository.findByDeletedFalseOrderBySortOrderAscCreatedAtDesc())
                .stream().map(this::toDto).toList();
    }

    public PartnerDto create(PartnerDto dto) {
        Partner partner = new Partner();
        apply(dto, partner, false);
        return toDto(repository.save(partner));
    }

    public PartnerDto update(Long id, PartnerDto dto) {
        Partner partner = repository.findById(id).orElseThrow(() -> new RuntimeException("Partner not found: " + id));
        String oldLogo = partner.getLogoUrl();
        apply(dto, partner, true);
        Partner saved = repository.save(partner);
        if (StringUtils.hasText(oldLogo) && !oldLogo.equals(saved.getLogoUrl())) {
            fileStorageService.deletePublicFile(oldLogo);
        }
        return toDto(saved);
    }

    public void delete(Long id) {
        Partner partner = repository.findById(id).orElseThrow(() -> new RuntimeException("Partner not found: " + id));
        partner.setDeleted(true);
        repository.save(partner);
    }

    private void apply(PartnerDto dto, Partner p, boolean updating) {
        if (dto == null) dto = new PartnerDto();
        p.setName(StringUtils.hasText(dto.getName()) ? dto.getName().trim() : "Partner");
        p.setWebsiteUrl(StringUtils.hasText(dto.getWebsiteUrl()) ? dto.getWebsiteUrl().trim() : null);
        p.setActive(dto.getActive() == null ? true : dto.getActive());
        p.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        p.setLogoUrl(resolveImage(dto.getLogoUrl(), updating ? p.getLogoUrl() : null));
    }

    private String resolveImage(String value, String existing) {
        if (!StringUtils.hasText(value)) return existing;
        String trimmed = value.trim();
        if (trimmed.startsWith("data:image/")) return fileStorageService.saveDataUrl(trimmed);
        return trimmed;
    }

    private PartnerDto toDto(Partner p) {
        PartnerDto dto = new PartnerDto();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setLogoUrl(p.getLogoUrl());
        dto.setWebsiteUrl(p.getWebsiteUrl());
        dto.setActive(p.getActive());
        dto.setSortOrder(p.getSortOrder());
        return dto;
    }
}
