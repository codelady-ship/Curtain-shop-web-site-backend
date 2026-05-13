package az.example.properde.service;

import az.example.properde.dao.dto.product.ColorDto;
import az.example.properde.dao.dto.product.ProductRequestDTO;
import az.example.properde.dao.dto.product.ProductResponseDTO;
import az.example.properde.dao.dto.product.SizeDto;
import az.example.properde.dao.entity.Product;
import az.example.properde.dao.entity.ProductColor;
import az.example.properde.dao.entity.ProductSize;
import az.example.properde.dao.enums.CategoryType;
import az.example.properde.mapper.ProductMapper;
import az.example.properde.repository.ProductRepository;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private static final String DEFAULT_PART_TYPE = "Standart";
    private static final String DEFAULT_SIZE_VALUE = "Standart";
    private static final String DEFAULT_COLOR_NAME = "Standart";
    private static final String DEFAULT_COLOR_HEX = "#cccccc";

    private final ProductRepository repo;
    private final ProductMapper mapper;

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

        mapper.updateEntityFromDto(dto, entity);
        applyProductDefaults(entity);
        replaceColors(entity, dto.getColors(), dto.getImageUrl());
        replaceSizes(entity, dto.getSizeOptions());

        Product savedProduct = repo.save(entity);
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
        Sort sort = switch (String.valueOf(sortType).toLowerCase()) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "name" -> Sort.by(Sort.Direction.ASC, "name");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        CategoryType categoryType = null;
        if (StringUtils.hasText(category)) {
            categoryType = CategoryType.fromString(category);
        }

        CategoryType finalCategoryType = categoryType;
        Pageable pageable = PageRequest.of(page, size, sort);

        return repo.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("deleted"), false));

            if (StringUtils.hasText(search)) {
                String q = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), q),
                        cb.like(cb.lower(root.get("description")), q),
                        cb.like(cb.lower(root.get("partType")), q)
                ));
            }
            if (finalCategoryType != null) {
                predicates.add(cb.equal(root.get("category"), finalCategoryType));
            }
            if (StringUtils.hasText(room)) {
                predicates.add(cb.equal(root.get("room"), room));
            }
            if (inDiscount != null) {
                predicates.add(cb.equal(root.get("isDiscount"), inDiscount));
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
        if (dto.getCategory() == null) {
            throw new IllegalArgumentException("Product category is required");
        }
    }

    private void applyProductDefaults(Product entity) {
        entity.setName(entity.getName().trim());
        entity.setDescription(entity.getDescription().trim());

        if (!StringUtils.hasText(entity.getPartType())) {
            entity.setPartType(DEFAULT_PART_TYPE);
        } else {
            entity.setPartType(entity.getPartType().trim());
        }

        if (entity.getRating() == null) {
            entity.setRating(5.0);
        }
        if (entity.getIsPopular() == null) {
            entity.setIsPopular(false);
        }
        if (entity.getIsDiscount() == null) {
            entity.setIsDiscount(false);
        }
    }

    private void replaceColors(Product entity, List<ColorDto> colorDtos, String imageUrl) {
        entity.getColors().clear();

        if (colorDtos != null) {
            for (ColorDto colorDto : colorDtos) {
                if (colorDto == null) {
                    continue;
                }
                ProductColor color = mapper.toColorEntity(colorDto);
                if (!StringUtils.hasText(color.getImageUrl()) && StringUtils.hasText(imageUrl)) {
                    color.setImageUrl(imageUrl.trim());
                }
                normalizeColor(color);
                color.setProduct(entity);
                entity.getColors().add(color);
            }
        }

        if (entity.getColors().isEmpty() && StringUtils.hasText(imageUrl)) {
            ProductColor defaultColor = new ProductColor();
            defaultColor.setColorName(DEFAULT_COLOR_NAME);
            defaultColor.setColorHex(DEFAULT_COLOR_HEX);
            defaultColor.setImageUrl(imageUrl.trim());
            defaultColor.setProduct(entity);
            entity.getColors().add(defaultColor);
        }
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

    private void normalizeColor(ProductColor color) {
        if (!StringUtils.hasText(color.getColorName())) {
            color.setColorName(DEFAULT_COLOR_NAME);
        } else {
            color.setColorName(color.getColorName().trim());
        }
        if (!StringUtils.hasText(color.getColorHex())) {
            color.setColorHex(DEFAULT_COLOR_HEX);
        } else {
            color.setColorHex(color.getColorHex().trim());
        }
        if (color.getImageUrl() == null) {
            color.setImageUrl("");
        }
    }

    private void normalizeSize(ProductSize size) {
        if (!StringUtils.hasText(size.getSizeValue())) {
            size.setSizeValue(DEFAULT_SIZE_VALUE);
        } else {
            size.setSizeValue(size.getSizeValue().trim());
        }
        if (size.getPrice() == null) {
            size.setPrice(0.0);
        }
    }

    @Transactional(readOnly = true)
    public Product getProductById(Long id) {
        return repo.findById(id).orElse(null);
    }
}
