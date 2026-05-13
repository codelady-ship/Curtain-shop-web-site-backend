package az.example.properde.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path fileStorageLocation;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            throw new IllegalStateException("Yükləmə üçün qovluq yaradıla bilmədi.", ex);
        }
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fayl boş ola bilməz.");
        }

        try {
            String originalFileName = file.getOriginalFilename();

            if (!StringUtils.hasText(originalFileName)) {
                throw new IllegalArgumentException("Fayl adı boş ola bilməz.");
            }

            String cleanFileName = StringUtils.cleanPath(originalFileName);

            if (cleanFileName.contains("..")) {
                throw new IllegalArgumentException("Fayl adında təhlükəli yol ardıcıllığı var: " + cleanFileName);
            }

            String extension = "";

            int dotIndex = cleanFileName.lastIndexOf(".");
            if (dotIndex >= 0) {
                extension = cleanFileName.substring(dotIndex);
            }

            String normalizedExtension = extension.toLowerCase();
            boolean supportedImageExtension = normalizedExtension.matches("\\.(jpg|jpeg|png|gif|webp|bmp)");
            String contentType = file.getContentType();
            boolean supportedImageContentType = contentType != null && contentType.startsWith("image/");
            if (!supportedImageContentType && !supportedImageExtension) {
                throw new IllegalArgumentException("Yalnız şəkil faylı yükləmək olar.");
            }

            String newFileName = UUID.randomUUID() + extension;

            Path targetLocation = this.fileStorageLocation
                    .resolve(newFileName)
                    .normalize();

            Files.copy(
                    file.getInputStream(),
                    targetLocation,
                    StandardCopyOption.REPLACE_EXISTING
            );

            return "/uploads/" + newFileName;

        } catch (IOException ex) {
            throw new IllegalStateException("Faylı saxlamaq mümkün olmadı.", ex);
        }
    }
}
