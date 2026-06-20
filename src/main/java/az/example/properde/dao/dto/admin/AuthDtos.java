package az.example.properde.dao.dto.admin;

import java.time.LocalDateTime;

public class AuthDtos {
    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, AdminUser user) {}
    public record AdminUser(Long id, String username, String name, String role, String email, String phone) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
    public record AdminProfileRequest(String name, String email, String phone) {}
    public record AdminProfileResponse(
            Long id,
            String username,
            String name,
            String role,
            String email,
            String phone,
            String resetCode,
            LocalDateTime resetCodeExpiresAt,
            Boolean resetCodeUsed,
            LocalDateTime resetRequestedAt,
            String resetChannel,
            String resetDeliveryStatus,
            LocalDateTime resetDeliveredAt,
            String resetDeliveryTarget,
            String resetDeliveryError
    ) {}
    public record ForgotPasswordRequest(String username, String name, String email, String phone, String channel) {}
    public record ForgotPasswordResponse(
            String message,
            String channel,
            Boolean delivered,
            String deliveryStatus,
            String deliveryTarget,
            String deliveryError,
            String code,
            String link,
            String mailtoLink,
            LocalDateTime expiresAt
    ) {}
    public record ResetPasswordRequest(String code, String newPassword) {}
}
