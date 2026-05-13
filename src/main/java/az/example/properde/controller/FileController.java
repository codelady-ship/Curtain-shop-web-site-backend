package az.example.properde.controller;

import az.example.properde.service.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://localhost:5174", "http://127.0.0.1:5173", "http://127.0.0.1:5174"}, allowedHeaders = "*", methods = {RequestMethod.POST, RequestMethod.OPTIONS})
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // Sənin servisin faylı saxlayır və "/uploads/uuid.jpg" formatında path qaytarır
            String fileUrl = fileStorageService.save(file);

            // Frontend adətən JSON gözlədiyi üçün Map formatında qaytarırıq
            return ResponseEntity.ok(Map.of("url", fileUrl));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Xəta baş verdi: " + e.getMessage());
        }
    }
}