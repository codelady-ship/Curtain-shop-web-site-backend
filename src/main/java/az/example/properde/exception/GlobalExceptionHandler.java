package az.example.properde.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error
                -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", "Validasiya xətası",
                "path", request.getRequestURI(),
                "errors", errors
        ));
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            EntityNotFoundException.class,
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", ex.getMessage(),
                "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler({MultipartException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, Object>> handleMultipart(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "message", "Fayl yükləmə xətası",
                "error", ex.getMessage(),
                "path", request.getRequestURI()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled backend error at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "message", "Xəta baş verdi",
                "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                "path", request.getRequestURI()
        ));
    }
}
