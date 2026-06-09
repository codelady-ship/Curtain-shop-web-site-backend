package az.example.properde.controller;

import az.example.properde.dao.dto.admin.PartnerDto;
import az.example.properde.service.PartnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173", "http://127.0.0.1:5174"})
public class PartnerController {
    private final PartnerService partnerService;

    @GetMapping("/api/partners")
    public List<PartnerDto> publicPartners(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return partnerService.list(activeOnly);
    }

    @GetMapping("/api/admin/partners")
    public List<PartnerDto> adminPartners() {
        return partnerService.list(false);
    }

    @PostMapping("/api/admin/partners")
    public PartnerDto create(@RequestBody PartnerDto dto) {
        return partnerService.create(dto);
    }

    @PutMapping("/api/admin/partners/{id}")
    public PartnerDto update(@PathVariable Long id, @RequestBody PartnerDto dto) {
        return partnerService.update(id, dto);
    }

    @DeleteMapping("/api/admin/partners/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        partnerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
