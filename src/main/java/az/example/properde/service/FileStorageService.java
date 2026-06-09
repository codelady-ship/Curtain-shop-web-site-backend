package az.example.properde.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;

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

            String extension = extensionFromName(cleanFileName);

            String normalizedExtension = extension.toLowerCase(Locale.ROOT);
            boolean supportedImageExtension = isSupportedImageExtension(normalizedExtension);
            String contentType = file.getContentType();
            boolean supportedImageContentType = isSupportedContentType(contentType);
            if (!supportedImageContentType || !supportedImageExtension) {
                throw new IllegalArgumentException("Yalnız jpg, png, gif, webp və bmp şəkil faylları yükləmək olar.");
            }

            String finalExtension = supportedImageExtension ? normalizedExtension : extensionFromContentType(contentType);
            return saveBytes(file.getBytes(), finalExtension);

        } catch (IOException ex) {
            throw new IllegalStateException("Faylı saxlamaq mümkün olmadı.", ex);
        }
    }

    public String saveDataUrl(String dataUrl) {
        if (!StringUtils.hasText(dataUrl)) {
            throw new IllegalArgumentException("Şəkil datası boş ola bilməz.");
        }

        String trimmed = dataUrl.trim();
        int commaIndex = trimmed.indexOf(',');
        if (!trimmed.startsWith("data:image/") || commaIndex < 0) {
            throw new IllegalArgumentException("Düzgün base64 şəkil datası göndərin.");
        }

        String header = trimmed.substring(0, commaIndex).toLowerCase(Locale.ROOT);
        if (!header.contains(";base64")) {
            throw new IllegalArgumentException("Şəkil base64 formatında olmalıdır.");
        }

        String extension = extensionFromDataUrlHeader(header);
        byte[] bytes = Base64.getDecoder().decode(trimmed.substring(commaIndex + 1));
        try {
            return saveBytes(bytes, extension);
        } catch (IOException ex) {
            throw new IllegalStateException("Faylı saxlamaq mümkün olmadı.", ex);
        }
    }


    public void deletePublicFile(String publicUrl) {
        if (!StringUtils.hasText(publicUrl)) {
            return;
        }
        String normalized = publicUrl.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("data:")) {
            return;
        }
        normalized = normalized.replace('\\', '/');
        int uploadsIndex = normalized.indexOf("/uploads/");
        if (uploadsIndex >= 0) {
            normalized = normalized.substring(uploadsIndex + "/uploads/".length());
        } else if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        } else if (normalized.startsWith("/uploads/")) {
            normalized = normalized.substring("/uploads/".length());
        } else {
            return;
        }
        String cleanFileName = StringUtils.cleanPath(normalized);
        if (!StringUtils.hasText(cleanFileName) || cleanFileName.contains("..") || cleanFileName.contains("/")) {
            return;
        }
        try {
            Files.deleteIfExists(this.fileStorageLocation.resolve(cleanFileName).normalize());
        } catch (IOException ignored) {
            // File cleanup should not fail the main update transaction.
        }
    }

    private String saveBytes(byte[] bytes, String extension) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Fayl boş ola bilməz.");
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Şəkil maksimum 5MB ola bilər.");
        }

        String normalizedExtension = StringUtils.hasText(extension) ? extension.toLowerCase(Locale.ROOT) : ".webp";
        if (!isSupportedImageExtension(normalizedExtension)) {
            normalizedExtension = ".webp";
        }

        String newFileName = UUID.randomUUID() + normalizedExtension;
        Path targetLocation = this.fileStorageLocation.resolve(newFileName).normalize();
        Files.write(targetLocation, bytes);
        return "/uploads/" + newFileName;
    }

    private String extensionFromName(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");
        return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
    }

    private boolean isSupportedImageExtension(String extension) {
        return extension != null && extension.matches("\\.(jpg|jpeg|png|gif|webp|bmp)");
    }


    private boolean isSupportedContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return false;
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg", "image/png", "image/gif", "image/bmp", "image/webp" -> true;
            default -> false;
        };
    }

    private String extensionFromContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return ".webp";
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/bmp" -> ".bmp";
            case "image/webp" -> ".webp";
            default -> ".webp";
        };
    }

    private String extensionFromDataUrlHeader(String header) {
        if (header.contains("image/jpeg") || header.contains("image/jpg")) return ".jpg";
        if (header.contains("image/png")) return ".png";
        if (header.contains("image/gif")) return ".gif";
        if (header.contains("image/bmp")) return ".bmp";
        return ".webp";
    }
}
