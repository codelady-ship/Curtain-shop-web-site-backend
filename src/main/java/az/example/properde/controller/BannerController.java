package az.example.properde.controller;

import az.example.properde.dao.dto.admin.BannerDto;
import az.example.properde.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173", "http://127.0.0.1:5174"})
public class BannerController {
    private final BannerService bannerService;

    @GetMapping("/api/banners")
    public List<BannerDto> publicBanners(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return bannerService.list(activeOnly);
    }

    @GetMapping("/api/admin/banners")
    public List<BannerDto> adminBanners() {
        return bannerService.list(false);
    }

    @PostMapping("/api/admin/banners")
    public BannerDto create(@RequestBody BannerDto dto) {
        return bannerService.create(dto);
    }

    @PutMapping("/api/admin/banners/{id}")
    public BannerDto update(@PathVariable Long id, @RequestBody BannerDto dto) {
        return bannerService.update(id, dto);
    }

    @DeleteMapping("/api/admin/banners/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
