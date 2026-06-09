package az.example.properde.controller;

import az.example.properde.service.AnalyticsService;
import az.example.properde.service.VisitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {
        "http://localhost:3000",
        "http://localhost:5173",
        "http://localhost:5174",
        "http://127.0.0.1:5173",
        "http://127.0.0.1:5174"
}, allowCredentials = "true")
@Tag(name = "Analytics", description = "Ziyarətçi statistikası üçün API-lər")
public class AnalyticsController {

    private final AnalyticsService service;
    private final VisitorService visitorService;

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard statistikasını gətir")
    public ResponseEntity<?> getDashboard() {
        try {
            Map<String, Object> stats = service.getDashboardStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Dashboard datası gətirilərkən xəta: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Statistika yüklənə bilmədi", "error", e.getMessage()));
        }
    }

    @PostMapping("/hit")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Ziyarəti qeydə al")
    public void trackVisit(HttpServletRequest request) {
        try {
            service.incrementVisit();
            visitorService.record(request);
        } catch (Exception e) {
            log.error("Ziyarət sayı artırılanda xəta baş verdi: ", e);
        }
    }
}
