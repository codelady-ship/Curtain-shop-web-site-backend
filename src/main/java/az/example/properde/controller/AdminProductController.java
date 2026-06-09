package az.example.properde.controller;

import az.example.properde.dao.dto.product.ProductRequestDTO;
import az.example.properde.dao.dto.product.ProductResponseDTO;
import az.example.properde.service.FileStorageService;
import az.example.properde.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173", "http://127.0.0.1:5174"})
public class AdminProductController {
    private final ProductService productService;
    private final FileStorageService fileStorageService;

    @GetMapping
    public Page<ProductResponseDTO> getAll(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return productService.getAll(page, size);
    }

    @PostMapping
    public ProductResponseDTO create(@Valid @RequestBody ProductRequestDTO dto) {
        return productService.create(dto);
    }

    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    public ProductResponseDTO createWithImage(@RequestPart("image") MultipartFile file,
                                               @RequestPart("product") @Valid ProductRequestDTO dto) {
        dto.setImageUrl(fileStorageService.save(file));
        return productService.create(dto);
    }

    @PutMapping("/{id}")
    public ProductResponseDTO update(@PathVariable Long id, @Valid @RequestBody ProductRequestDTO dto) {
        return productService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
