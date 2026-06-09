package az.example.properde.service;

import az.example.properde.dao.dto.product.ColorDto;
import az.example.properde.dao.dto.product.ProductRequestDTO;
import az.example.properde.dao.dto.product.ProductResponseDTO;
import az.example.properde.dao.dto.product.SizeDto;
import az.example.properde.dao.entity.Product;
import az.example.properde.dao.entity.ProductColor;
import az.example.properde.dao.entity.ProductSize;
import az.example.properde.mapper.ProductMapper;
import az.example.properde.repository.ProductRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private static final String DEFAULT_PART_TYPE = "Standart";
    private static final String DEFAULT_SIZE_VALUE = "Standart";
    private static final String DEFAULT_COLOR_NAME = "Standart";
    private static final String DEFAULT_COLOR_HEX = "#cccccc";
    private static final String DEFAULT_STATUS = "Standart";
    private static final int MAX_SHORT_TEXT_LENGTH = 255;
    private static final int MAX_ROOM_LENGTH = 100;

    private static final Map<String, String> CATEGORY_LABELS = buildCategoryLabels();

    private final ProductRepository repo;
    private final ProductMapper mapper;
    private final FileStorageService fileStorageService;

    public ProductResponseDTO create(ProductRequestDTO dto) {
        validateProduct(dto);

        Product entity = mapper.toEntity(dto);
        applyProductDefaults(entity);
        replaceSizes(entity, dto.getSizeOptions());
        replaceColors(entity, dto.getColors(), dto.getImageUrl());

        Product savedProduct = repo.save(entity);
        return mapper.toResponseDTO(savedProduct);
    }

    public ProductResponseDTO update(Long id, ProductRequestDTO dto) {
        validateProduct(dto);

        Product entity = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));

        List<String> oldImageUrls = entity.getColors().stream()
                .map(ProductColor::getImageUrl)
                .filter(StringUtils::hasText)
                .toList();

        mapper.updateEntityFromDto(dto, entity);
        applyProductDefaults(entity);
        replaceColors(entity, dto.getColors(), dto.getImageUrl());
        replaceSizes(entity, dto.getSizeOptions());

        Product savedProduct = repo.save(entity);
        cleanupReplacedImages(oldImageUrls, savedProduct.getColors().stream()
                .map(ProductColor::getImageUrl)
                .filter(StringUtils::hasText)
                .toList());
        return mapper.toResponseDTO(savedProduct);
    }

    @Transactional(readOnly = true)
    public ProductResponseDTO get(Long id) {
        return repo.findById(id)
                .map(mapper::toResponseDTO)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ProductResponseDTO> getAllList() {
        return repo.findAll().stream()
                .filter(product -> !product.isDeleted())
                .map(mapper::toResponseDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repo.findAll((root, query, cb) -> cb.equal(root.get("deleted"), false), pageable)
                .map(mapper::toResponseDTO);
    }

    public void delete(Long id) {
        Product entity = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
        entity.setDeleted(true);
        repo.save(entity);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDTO> getFilteredProducts(
            String search,
            String category,
            String room,
            Boolean inDiscount,
            String sortType,
            int page,
            int size
    ) {
        String normalizedSort = String.valueOf(sortType).toLowerCase(Locale.ROOT);
        Sort sort = switch (normalizedSort) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "name" -> Sort.by(Sort.Direction.ASC, "name");
            case "sort_order", "order" -> Sort.by(Sort.Direction.ASC, "sortOrder").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        List<String> categoryCandidates = categoryFilterCandidates(category);
        Pageable pageable = PageRequest.of(page, size, sort);

        return repo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), false));

            if (StringUtils.hasText(search)) {
                String q = "%" + search.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), q),
                        cb.like(cb.lower(root.get("description")), q),
                        cb.like(cb.lower(root.get("partType")), q),
                        cb.like(cb.lower(root.get("nameAz")), q),
                        cb.like(cb.lower(root.get("nameRu")), q),
                        cb.like(cb.lower(root.get("nameEn")), q),
                        cb.like(cb.lower(root.get("descriptionAz")), q),
                        cb.like(cb.lower(root.get("descriptionRu")), q),
                        cb.like(cb.lower(root.get("descriptionEn")), q)
                ));
            }
            if (!categoryCandidates.isEmpty()) {
                predicates.add(cb.lower(root.get("category")).in(categoryCandidates));
            }
            if (StringUtils.hasText(room)) {
                predicates.add(cb.or(
                        cb.equal(cb.lower(root.get("room")), room.toLowerCase(Locale.ROOT)),
                        cb.equal(cb.lower(root.get("roomType")), room.toLowerCase(Locale.ROOT))
                ));
            }
            if (inDiscount != null) {
                predicates.add(cb.equal(root.get("isDiscount"), inDiscount));
            }

            if ("cheap".equals(normalizedSort) || "price_asc".equals(normalizedSort) || "priceasc".equals(normalizedSort)) {
                query.distinct(true);
                query.orderBy(cb.asc(root.join("sizeOptions", JoinType.LEFT).get("price")));
            } else if ("expensive".equals(normalizedSort) || "price_desc".equals(normalizedSort) || "pricedesc".equals(normalizedSort)) {
                query.distinct(true);
                query.orderBy(cb.desc(root.join("sizeOptions", JoinType.LEFT).get("price")));
            } else if ("discount".equals(normalizedSort) || "discounted".equals(normalizedSort)) {
                predicates.add(cb.equal(root.get("isDiscount"), true));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable).map(mapper::toResponseDTO);
    }

    private void validateProduct(ProductRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Product payload is required");
        }
        if (!StringUtils.hasText(dto.getName())) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (!StringUtils.hasText(dto.getDescription())) {
            throw new IllegalArgumentException("Product description is required");
        }
        if (!StringUtils.hasText(dto.getCategory())) {
            throw new IllegalArgumentException("Product category is required");
        }
    }

    private void applyProductDefaults(Product entity) {
        entity.setName(limitText(entity.getName(), MAX_SHORT_TEXT_LENGTH));
        entity.setDescription(entity.getDescription().trim());
        entity.setCategory(limitText(normalizeCategory(entity.getCategory()), MAX_SHORT_TEXT_LENGTH));

        if (!StringUtils.hasText(entity.getPartType())) {
            entity.setPartType(DEFAULT_PART_TYPE);
        } else {
            entity.setPartType(limitText(entity.getPartType(), MAX_SHORT_TEXT_LENGTH));
        }

        if (StringUtils.hasText(entity.getRoom())) {
            entity.setRoom(limitText(entity.getRoom(), MAX_ROOM_LENGTH));
        }
        if (!StringUtils.hasText(entity.getRoomType()) && StringUtils.hasText(entity.getRoom())) {
            entity.setRoomType(entity.getRoom());
        }
        if (StringUtils.hasText(entity.getRoomType())) {
            entity.setRoomType(limitText(entity.getRoomType(), 120));
        }

        entity.setNameAz(limitText(StringUtils.hasText(entity.getNameAz()) ? entity.getNameAz() : entity.getName(), MAX_SHORT_TEXT_LENGTH));
        entity.setNameRu(limitText(StringUtils.hasText(entity.getNameRu()) ? entity.getNameRu() : entity.getName(), MAX_SHORT_TEXT_LENGTH));
        entity.setNameEn(limitText(StringUtils.hasText(entity.getNameEn()) ? entity.getNameEn() : entity.getName(), MAX_SHORT_TEXT_LENGTH));
        entity.setDescriptionAz(StringUtils.hasText(entity.getDescriptionAz()) ? entity.getDescriptionAz().trim() : entity.getDescription());
        entity.setDescriptionRu(StringUtils.hasText(entity.getDescriptionRu()) ? entity.getDescriptionRu().trim() : entity.getDescription());
        entity.setDescriptionEn(StringUtils.hasText(entity.getDescriptionEn()) ? entity.getDescriptionEn().trim() : entity.getDescription());
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        if (entity.getDiscountPercent() == null) {
            entity.setDiscountPercent(0);
        }

        if (entity.getRating() == null) {
            entity.setRating(5.0);
        }

        String resolvedStatus = normalizeStatus(entity.getStatus(), entity.getIsPopular(), entity.getIsDiscount());
        entity.setStatus(limitText(resolvedStatus, MAX_SHORT_TEXT_LENGTH));
        entity.setIsPopular("Popular".equalsIgnoreCase(resolvedStatus));
        entity.setIsDiscount("Endirimli".equalsIgnoreCase(resolvedStatus));
    }

    private void replaceColors(Product entity, List<ColorDto> colorDtos, String imageUrl) {
        entity.getColors().clear();
        String storedImageUrl = normalizeStoredImageUrl(imageUrl);

        if (colorDtos != null) {
            for (ColorDto colorDto : colorDtos) {
                if (colorDto == null) {
                    continue;
                }
                ProductColor color = mapper.toColorEntity(colorDto);
                color.setImageUrl(normalizeStoredImageUrl(color.getImageUrl()));
                if (!StringUtils.hasText(color.getImageUrl()) && StringUtils.hasText(storedImageUrl)) {
                    color.setImageUrl(storedImageUrl);
                }
                normalizeColor(color);
                color.setProduct(entity);
                entity.getColors().add(color);
            }
        }

        if (entity.getColors().isEmpty() && StringUtils.hasText(storedImageUrl)) {
            ProductColor defaultColor = new ProductColor();
            defaultColor.setColorName(DEFAULT_COLOR_NAME);
            defaultColor.setColorHex(DEFAULT_COLOR_HEX);
            defaultColor.setImageUrl(storedImageUrl);
            defaultColor.setProduct(entity);
            entity.getColors().add(defaultColor);
        }
    }

    private String normalizeStoredImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return "";
        }

        String trimmed = imageUrl.trim();
        if (trimmed.startsWith("data:image/")) {
            return fileStorageService.saveDataUrl(trimmed);
        }

        return trimmed;
    }

    private void replaceSizes(Product entity, List<SizeDto> sizeDtos) {
        entity.getSizeOptions().clear();

        if (sizeDtos == null || sizeDtos.isEmpty()) {
            ProductSize defaultSize = new ProductSize();
            defaultSize.setSizeValue(DEFAULT_SIZE_VALUE);
            defaultSize.setPrice(0.0);
            defaultSize.setProduct(entity);
            entity.getSizeOptions().add(defaultSize);
            return;
        }

        for (SizeDto sizeDto : sizeDtos) {
            if (sizeDto == null) {
                continue;
            }
            ProductSize size = mapper.toSizeEntity(sizeDto);
            normalizeSize(size);
            size.setProduct(entity);
            entity.getSizeOptions().add(size);
        }

        if (entity.getSizeOptions().isEmpty()) {
            ProductSize defaultSize = new ProductSize();
            defaultSize.setSizeValue(DEFAULT_SIZE_VALUE);
            defaultSize.setPrice(0.0);
            defaultSize.setProduct(entity);
            entity.getSizeOptions().add(defaultSize);
        }
    }


    private void cleanupReplacedImages(List<String> oldUrls, List<String> newUrls) {
        if (oldUrls == null || oldUrls.isEmpty()) {
            return;
        }
        List<String> safeNewUrls = newUrls == null ? List.of() : newUrls;
        oldUrls.stream()
                .filter(StringUtils::hasText)
                .filter(oldUrl -> safeNewUrls.stream().noneMatch(oldUrl::equals))
                .forEach(fileStorageService::deletePublicFile);
    }

    private void normalizeColor(ProductColor color) {
        if (!StringUtils.hasText(color.getColorName())) {
            color.setColorName(DEFAULT_COLOR_NAME);
        } else {
            color.setColorName(limitText(color.getColorName(), MAX_SHORT_TEXT_LENGTH));
        }
        if (!StringUtils.hasText(color.getColorHex())) {
            color.setColorHex(DEFAULT_COLOR_HEX);
        } else {
            color.setColorHex(limitText(color.getColorHex(), 50));
        }
        if (color.getImageUrl() == null) {
            color.setImageUrl("");
        }
    }

    private void normalizeSize(ProductSize size) {
        if (!StringUtils.hasText(size.getSizeValue())) {
            size.setSizeValue(DEFAULT_SIZE_VALUE);
        } else {
            size.setSizeValue(limitText(size.getSizeValue(), MAX_SHORT_TEXT_LENGTH));
        }
        if (size.getPrice() == null) {
            size.setPrice(0.0);
        }
    }

    private String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value == null ? null : value.trim();
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String normalizeCategory(String category) {
        String trimmed = category.trim();
        return CATEGORY_LABELS.getOrDefault(normalizeKey(trimmed), trimmed);
    }

    private String normalizeStatus(String status, Boolean isPopular, Boolean isDiscount) {
        if (StringUtils.hasText(status)) {
            return status.trim();
        }
        if (Boolean.TRUE.equals(isDiscount)) {
            return "Endirimli";
        }
        if (Boolean.TRUE.equals(isPopular)) {
            return "Popular";
        }
        return DEFAULT_STATUS;
    }

    private List<String> categoryFilterCandidates(String category) {
        if (!StringUtils.hasText(category)) {
            return List.of();
        }

        String normalizedCategory = normalizeCategory(category);
        List<String> candidates = new ArrayList<>();
        candidates.add(normalizedCategory.toLowerCase(Locale.ROOT));

        CATEGORY_LABELS.forEach((alias, label) -> {
            if (label.equalsIgnoreCase(normalizedCategory)) {
                candidates.add(alias.toLowerCase(Locale.ROOT));
            }
        });

        return candidates.stream().distinct().toList();
    }

    private static Map<String, String> buildCategoryLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(normalizeKey("CURTAINS"), "Dəst Pərdələr");
        labels.put(normalizeKey("SET_CURTAINS"), "Dəst Pərdələr");
        labels.put(normalizeKey("Dəst Pərdələr"), "Dəst Pərdələr");
        labels.put(normalizeKey("Dəst pərdələr"), "Dəst Pərdələr");
        labels.put(normalizeKey("CORNICES"), "Kornizlər");
        labels.put(normalizeKey("Kornizlər"), "Kornizlər");
        labels.put(normalizeKey("SUNSHADES"), "Günəşliklər");
        labels.put(normalizeKey("Günəşliklər"), "Günəşliklər");
        labels.put(normalizeKey("BACKGROUNDS"), "Fonluqlar");
        labels.put(normalizeKey("BACKDROPS"), "Fonluqlar");
        labels.put(normalizeKey("Fonluqlar"), "Fonluqlar");
        labels.put(normalizeKey("TULLES"), "Tüllər");
        labels.put(normalizeKey("Tüllər"), "Tüllər");
        labels.put(normalizeKey("BLINDS"), "Jalüzlər");
        labels.put(normalizeKey("Jalüzlər"), "Jalüzlər");
        labels.put(normalizeKey("ACCESSORIES"), "Aksesuarlar");
        labels.put(normalizeKey("Aksesuarlar"), "Aksesuarlar");
        labels.put(normalizeKey("PASTELS"), "Pastellər");
        labels.put(normalizeKey("Pastellər"), "Pastellər");
        labels.put(normalizeKey("Pasteller"), "Pastellər");
        return labels;
    }

    private static String normalizeKey(String value) {
        return String.valueOf(value == null ? "" : value)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("ə", "e")
                .replace("ı", "i")
                .replace("ö", "o")
                .replace("ü", "u")
                .replace("ğ", "g")
                .replace("ş", "s")
                .replace("ç", "c");
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return repo.findById(id).orElse(null);
    }
}
