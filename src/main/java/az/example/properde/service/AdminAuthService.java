package az.example.properde.service;

import az.example.properde.dao.dto.admin.AuthDtos.AdminUser;
import az.example.properde.dao.dto.admin.AuthDtos.LoginResponse;
import az.example.properde.dao.entity.Admin;
import az.example.properde.repository.AdminRepository;
import az.example.properde.security.JwtTokenService;
import az.example.properde.security.PasswordHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin1978";

    private final AdminRepository adminRepository;
    private final JwtTokenService jwtTokenService;

    @Transactional
    public LoginResponse login(String username, String password) {
        Admin admin = ensureDefaultAdmin();
        String resolvedUsername = StringUtils.hasText(username) ? username.trim() : DEFAULT_USERNAME;
        if (!admin.getUsername().equalsIgnoreCase(resolvedUsername)) {
            throw new IllegalArgumentException("Admin username or password is invalid");
        }
        if (!PasswordHashUtil.matches(password, admin.getSalt(), admin.getPasswordHash())) {
            throw new IllegalArgumentException("Admin username or password is invalid");
        }
        return new LoginResponse(jwtTokenService.issue(admin.getId(), admin.getUsername()), toUser(admin));
    }

    @Transactional
    public AdminUser changePassword(String username, String currentPassword, String newPassword) {
        Admin admin = adminRepository.findByUsernameIgnoreCase(StringUtils.hasText(username) ? username : DEFAULT_USERNAME)
                .orElseGet(this::ensureDefaultAdmin);
        if (!PasswordHashUtil.matches(currentPassword, admin.getSalt(), admin.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is invalid");
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must contain at least 6 characters");
        }
        String salt = PasswordHashUtil.newSalt();
        admin.setSalt(salt);
        admin.setPasswordHash(PasswordHashUtil.hash(newPassword, salt));
        return toUser(adminRepository.save(admin));
    }

    @Transactional
    public Admin ensureDefaultAdmin() {
        return adminRepository.findByUsernameIgnoreCase(DEFAULT_USERNAME).orElseGet(() -> {
            Admin admin = new Admin();
            admin.setUsername(DEFAULT_USERNAME);
            admin.setDisplayName("Properde Admin");
            admin.setRole("Baş Administrator");
            String salt = PasswordHashUtil.newSalt();
            admin.setSalt(salt);
            admin.setPasswordHash(PasswordHashUtil.hash(DEFAULT_PASSWORD, salt));
            return adminRepository.save(admin);
        });
    }

    public AdminUser toUser(Admin admin) {
        return new AdminUser(admin.getId(), admin.getUsername(), admin.getDisplayName(), admin.getRole());
    }
}
