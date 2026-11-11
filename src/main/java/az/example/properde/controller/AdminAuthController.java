package az.example.properde.controller;

import az.example.properde.dao.dto.admin.AuthDtos.AdminProfileRequest;
import az.example.properde.dao.dto.admin.AuthDtos.ChangePasswordRequest;
import az.example.properde.dao.dto.admin.AuthDtos.ForgotPasswordRequest;
import az.example.properde.dao.dto.admin.AuthDtos.LoginRequest;
import az.example.properde.dao.dto.admin.AuthDtos.LoginResponse;
import az.example.properde.dao.dto.admin.AuthDtos.ResetPasswordRequest;
import az.example.properde.security.LoginRateLimiter;
import az.example.properde.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AdminAuthService adminAuthService;
    private final LoginRateLimiter loginRateLimiter;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String username = request == null ? null : request.username();
        // getRemoteAddr() is the real client IP behind Render (server.forward-headers-strategy=native).
        String clientIp = httpRequest.getRemoteAddr();
        // Atomic check + record: the attempt counts as a failure unless the login succeeds.
        if (!loginRateLimiter.tryAcquire(clientIp, username)) {
            return ResponseEntity.status(429).body(Map.of("message",
                    "Çox sayda uğursuz giriş cəhdi. " + LoginRateLimiter.WINDOW.toMinutes() + " dəqiqə sonra yenidən cəhd edin."));
        }
        try {
            LoginResponse response = adminAuthService.login(username, request == null ? null : request.password());
            loginRateLimiter.onSuccess(clientIp, username);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> profile() {
        try {
            return ResponseEntity.ok(adminAuthService.getProfile(null));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody AdminProfileRequest request) {
        try {
            return ResponseEntity.ok(adminAuthService.updateProfile(null, request));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            return ResponseEntity.ok(adminAuthService.changePassword(null, request.currentPassword(), request.newPassword()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            return ResponseEntity.ok(adminAuthService.forgotPassword(request));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            return ResponseEntity.ok(adminAuthService.resetPassword(request));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }
}
