package az.example.properde.dao.dto.admin;

public class AuthDtos {
    public record LoginRequest(String username, String password) {}
    public record LoginResponse(String token, AdminUser user) {}
    public record AdminUser(Long id, String username, String name, String role) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
}
