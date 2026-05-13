package az.example.properde.controller;

import az.example.properde.dao.dto.product.ProductRequestDTO;
import az.example.properde.dao.dto.product.ProductResponseDTO;
import az.example.properde.service.FileStorageService;
import az.example.properde.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://localhost:5173",
        "http://localhost:5174",
        "http://127.0.0.1:5173",
        "http://127.0.0.1:5174"
})
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product Management", description = "Məhsulların idarə edilməsi üçün API-lər")
public class ProductController {
    private final ProductService productService;
    private final FileStorageService fileStorageService;

    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    @Operation(summary = "Şəkil yükləməklə məhsul yarat", description = "Multipart form-data vasitəsilə şəkil və məhsul məlumatlarını qəbul edir")
    public ProductResponseDTO createWithImage(
            @RequestPart("image") MultipartFile file,
            @RequestPart("product") @Valid ProductRequestDTO dto) {

        String imagePath = fileStorageService.save(file);
        dto.setImageUrl(imagePath);
        return productService.create(dto);
    }

    @PostMapping
    public ResponseEntity<ProductResponseDTO> addProduct(@Valid @RequestBody ProductRequestDTO productRequestDTO) {
        return ResponseEntity.ok(productService.create(productRequestDTO));
    }

    @PutMapping("/{id}")
    public ProductResponseDTO update(@PathVariable Long id, @Valid @RequestBody ProductRequestDTO dto) {
        return productService.update(id, dto);
    }

    @GetMapping("/{id}")
    public ProductResponseDTO get(@PathVariable Long id) {
        return productService.get(id);
    }

    @GetMapping("/all")
    public Page<ProductResponseDTO> getAll(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return productService.getAll(page, size);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    @GetMapping("/filter")
    public Page<ProductResponseDTO> filter(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String room,
            @RequestParam(required = false) Boolean inDiscount,
            @RequestParam(defaultValue = "newest") String sortType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.getFilteredProducts(search, category, room, inDiscount, sortType, page, size);
    }
}
