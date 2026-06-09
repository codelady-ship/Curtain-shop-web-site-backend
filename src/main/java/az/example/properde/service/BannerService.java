package az.example.properde.service;

import az.example.properde.dao.dto.admin.BannerDto;
import az.example.properde.dao.entity.Banner;
import az.example.properde.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BannerService {
    private final BannerRepository repository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<BannerDto> list(boolean activeOnly) {
        return (activeOnly
                ? repository.findByDeletedFalseAndActiveTrueOrderBySortOrderAscCreatedAtDesc()
                : repository.findByDeletedFalseOrderBySortOrderAscCreatedAtDesc())
                .stream().map(this::toDto).toList();
    }

    public BannerDto create(BannerDto dto) {
        Banner banner = new Banner();
        apply(dto, banner, false);
        return toDto(repository.save(banner));
    }

    public BannerDto update(Long id, BannerDto dto) {
        Banner banner = repository.findById(id).orElseThrow(() -> new RuntimeException("Banner not found: " + id));
        String oldDesktop = banner.getDesktopImageUrl();
        String oldMobile = banner.getMobileImageUrl();
        apply(dto, banner, true);
        Banner saved = repository.save(banner);
        deleteIfReplaced(oldDesktop, saved.getDesktopImageUrl());
        deleteIfReplaced(oldMobile, saved.getMobileImageUrl());
        return toDto(saved);
    }

    public void delete(Long id) {
        Banner banner = repository.findById(id).orElseThrow(() -> new RuntimeException("Banner not found: " + id));
        banner.setDeleted(true);
        repository.save(banner);
    }

    private void apply(BannerDto dto, Banner b, boolean updating) {
        if (dto == null) dto = new BannerDto();
        b.setTitleAz(clean(dto.getTitleAz(), "Yeni kampaniya"));
        b.setTitleRu(clean(dto.getTitleRu(), b.getTitleAz()));
        b.setTitleEn(clean(dto.getTitleEn(), b.getTitleAz()));
        b.setDescriptionAz(clean(dto.getDescriptionAz(), "Perde.az kampaniya təklifi"));
        b.setDescriptionRu(clean(dto.getDescriptionRu(), b.getDescriptionAz()));
        b.setDescriptionEn(clean(dto.getDescriptionEn(), b.getDescriptionAz()));
        b.setButtonTextAz(clean(dto.getButtonTextAz(), "Məhsullara bax"));
        b.setButtonTextRu(clean(dto.getButtonTextRu(), "Посмотреть"));
        b.setButtonTextEn(clean(dto.getButtonTextEn(), "Shop now"));
        b.setLinkUrl(clean(dto.getLinkUrl(), "#shop"));
        b.setPlacement(clean(dto.getPlacement(), "MAIN").toUpperCase());
        b.setVisualType(clean(dto.getVisualType(), "IMAGE").toUpperCase());
        b.setActive(dto.getActive() == null ? true : dto.getActive());
        b.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        b.setDesktopImageUrl(resolveImage(dto.getDesktopImageUrl(), updating ? b.getDesktopImageUrl() : null));
        b.setMobileImageUrl(resolveImage(dto.getMobileImageUrl(), updating ? b.getMobileImageUrl() : null));
    }

    private String resolveImage(String value, String existing) {
        if (!StringUtils.hasText(value)) return existing;
        String trimmed = value.trim();
        if (trimmed.startsWith("data:image/")) return fileStorageService.saveDataUrl(trimmed);
        return trimmed;
    }

    private String clean(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private void deleteIfReplaced(String oldUrl, String newUrl) {
        if (StringUtils.hasText(oldUrl) && !oldUrl.equals(newUrl)) {
            fileStorageService.deletePublicFile(oldUrl);
        }
    }

    private BannerDto toDto(Banner b) {
        BannerDto dto = new BannerDto();
        dto.setId(b.getId());
        dto.setTitleAz(b.getTitleAz());
        dto.setTitleRu(b.getTitleRu());
        dto.setTitleEn(b.getTitleEn());
        dto.setDescriptionAz(b.getDescriptionAz());
        dto.setDescriptionRu(b.getDescriptionRu());
        dto.setDescriptionEn(b.getDescriptionEn());
        dto.setButtonTextAz(b.getButtonTextAz());
        dto.setButtonTextRu(b.getButtonTextRu());
        dto.setButtonTextEn(b.getButtonTextEn());
        dto.setLinkUrl(b.getLinkUrl());
        dto.setDesktopImageUrl(b.getDesktopImageUrl());
        dto.setMobileImageUrl(b.getMobileImageUrl());
        dto.setPlacement(b.getPlacement());
        dto.setVisualType(b.getVisualType());
        dto.setActive(b.getActive());
        dto.setSortOrder(b.getSortOrder());
        return dto;
    }
}
