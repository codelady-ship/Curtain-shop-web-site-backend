package az.example.properde.mapper;

import az.example.properde.dao.dto.product.ColorDto;
import az.example.properde.dao.dto.product.ProductRequestDTO;
import az.example.properde.dao.dto.product.ProductResponseDTO;
import az.example.properde.dao.dto.product.SizeDto;
import az.example.properde.dao.entity.Product;
import az.example.properde.dao.entity.ProductColor;
import az.example.properde.dao.entity.ProductSize;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.util.StringUtils;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "colors", ignore = true)
    @Mapping(target = "sizeOptions", ignore = true)
    Product toEntity(ProductRequestDTO dto);

    ProductResponseDTO toResponseDTO(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "colors", ignore = true)
    @Mapping(target = "sizeOptions", ignore = true)
    void updateEntityFromDto(ProductRequestDTO dto, @MappingTarget Product entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "colorName", expression = "java(org.springframework.util.StringUtils.hasText(dto.getColorName()) ? dto.getColorName().trim() : \"Standart\")")
    @Mapping(target = "colorHex", expression = "java(dto.resolvedColorHex())")
    @Mapping(target = "imageUrl", expression = "java(dto.resolvedImageUrl())")
    ProductColor toColorEntity(ColorDto dto);

    @Mapping(target = "colorCode", source = "colorHex")
    @Mapping(target = "colorHex", source = "colorHex")
    @Mapping(target = "mainImage", source = "imageUrl")
    @Mapping(target = "imageUrl", source = "imageUrl")
    ColorDto toColorDto(ProductColor entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    ProductSize toSizeEntity(SizeDto dto);

    SizeDto toSizeDto(ProductSize entity);

    @AfterMapping
    default void fillDerivedCardFields(Product product, @MappingTarget ProductResponseDTO dto) {
        if (product.getColors() != null && !product.getColors().isEmpty()) {
            ProductColor color = product.getColors().stream()
                    .filter(c -> StringUtils.hasText(c.getImageUrl()))
                    .findFirst()
                    .orElse(product.getColors().get(0));

            dto.setImageUrl(color.getImageUrl());
            dto.setColorName(color.getColorName());
            dto.setColorCode(color.getColorHex());
            dto.setColorHex(color.getColorHex());
        }

        if (product.getSizeOptions() != null && !product.getSizeOptions().isEmpty()) {
            ProductSize size = product.getSizeOptions().get(0);
            dto.setSizeValue(size.getSizeValue());
            dto.setPrice(size.getPrice());
            dto.setOldPrice(size.getOldPrice());
        }
    }
}
