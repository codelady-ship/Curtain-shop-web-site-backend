package az.example.properde.controller;

import az.example.properde.dao.dto.admin.AuthDtos.AdminProfileRequest;
import az.example.properde.dao.dto.admin.AuthDtos.ChangePasswordRequest;
import az.example.properde.dao.dto.admin.AuthDtos.ForgotPasswordRequest;
import az.example.properde.dao.dto.admin.AuthDtos.LoginRequest;
import az.example.properde.dao.dto.admin.AuthDtos.LoginResponse;
import az.example.properde.dao.dto.admin.AuthDtos.ResetPasswordRequest;
import az.example.properde.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = adminAuthService.login(request == null ? null : request.username(), request == null ? null : request.password());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.status(401).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/profile")
    public ResponseEntity<?> profile() {
        try {
            return ResponseEntity.ok(adminAuthService.getProfile("admin"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody AdminProfileRequest request) {
        try {
            return ResponseEntity.ok(adminAuthService.updateProfile("admin", request));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            return ResponseEntity.ok(adminAuthService.changePassword("admin", request.currentPassword(), request.newPassword()));
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
