package az.example.properde.service;

import az.example.properde.dao.dto.admin.AuthDtos.AdminProfileRequest;
import az.example.properde.dao.dto.admin.AuthDtos.AdminProfileResponse;
import az.example.properde.dao.dto.admin.AuthDtos.AdminUser;
import az.example.properde.dao.dto.admin.AuthDtos.ForgotPasswordRequest;
import az.example.properde.dao.dto.admin.AuthDtos.ForgotPasswordResponse;
import az.example.properde.dao.dto.admin.AuthDtos.LoginResponse;
import az.example.properde.dao.dto.admin.AuthDtos.ResetPasswordRequest;
import az.example.properde.dao.entity.Admin;
import az.example.properde.repository.AdminRepository;
import az.example.properde.security.JwtTokenService;
import az.example.properde.security.PasswordHashUtil;
import az.example.properde.service.AdminNotificationService.DeliveryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "huseyn1978";
    private static final String LEGACY_DEFAULT_PASSWORD = "admin1978";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminRepository adminRepository;
    private final JwtTokenService jwtTokenService;
    private final AdminNotificationService adminNotificationService;

    @Transactional
    public LoginResponse login(String username, String password) {
        Admin admin = ensureDefaultAdmin();
        String resolvedUsername = StringUtils.hasText(username) ? username.trim() : DEFAULT_USERNAME;
        if (!admin.getUsername().equalsIgnoreCase(resolvedUsername)) {
            throw new IllegalArgumentException("Admin giriş məlumatları yanlışdır");
        }
        if (!PasswordHashUtil.matches(password, admin.getSalt(), admin.getPasswordHash())) {
            boolean legacyDefaultPasswordStored = DEFAULT_USERNAME.equalsIgnoreCase(resolvedUsername)
                    && DEFAULT_PASSWORD.equals(password)
                    && PasswordHashUtil.matches(LEGACY_DEFAULT_PASSWORD, admin.getSalt(), admin.getPasswordHash());
            if (!legacyDefaultPasswordStored) {
                throw new IllegalArgumentException("Admin giriş məlumatları yanlışdır");
            }
            updatePassword(admin, DEFAULT_PASSWORD);
            admin = adminRepository.save(admin);
        }
        return new LoginResponse(jwtTokenService.issue(admin.getId(), admin.getUsername()), toUser(admin));
    }

    @Transactional(readOnly = true)
    public AdminProfileResponse getProfile(String username) {
        Admin admin = adminRepository.findByUsernameIgnoreCase(resolveUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("Admin tapılmadı"));
        return toProfile(admin);
    }

    @Transactional
    public AdminProfileResponse updateProfile(String username, AdminProfileRequest request) {
        Admin admin = adminRepository.findByUsernameIgnoreCase(resolveUsername(username))
                .orElseGet(this::ensureDefaultAdmin);

        if (request != null) {
            if (StringUtils.hasText(request.name())) {
                admin.setDisplayName(request.name().trim());
            }
            admin.setEmail(cleanEmail(request.email()));
            admin.setPhone(normalizePhone(request.phone()));
        }
        return toProfile(adminRepository.save(admin));
    }

    @Transactional
    public AdminProfileResponse changePassword(String username, String currentPassword, String newPassword) {
        Admin admin = adminRepository.findByUsernameIgnoreCase(resolveUsername(username))
                .orElseGet(this::ensureDefaultAdmin);
        if (!PasswordHashUtil.matches(currentPassword, admin.getSalt(), admin.getPasswordHash())) {
            throw new IllegalArgumentException("Hazırkı şifrə yanlışdır");
        }
        updatePassword(admin, newPassword);
        return toProfile(adminRepository.save(admin));
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String resolvedUsername = request != null && StringUtils.hasText(request.username())
                ? request.username().trim()
                : DEFAULT_USERNAME;

        Admin admin = adminRepository.findByUsernameIgnoreCase(resolvedUsername)
                .orElseGet(this::ensureDefaultAdmin);

        String incomingName = request == null ? null : clean(request.name());
        String incomingEmail = request == null ? null : cleanEmail(request.email());
        String incomingPhone = request == null ? null : normalizePhone(request.phone());
        String channel = normalizeChannel(request == null ? null : request.channel(), incomingEmail, incomingPhone);

        if ("email".equals(channel) && !StringUtils.hasText(incomingEmail)) {
            throw new IllegalArgumentException("Email ilə reset üçün admin emailini daxil edin");
        }
        if ("phone".equals(channel) && !StringUtils.hasText(incomingPhone)) {
            throw new IllegalArgumentException("Telefon ilə reset üçün admin telefon nömrəsini daxil edin");
        }

        assertRecoveryIdentity(admin, incomingEmail, incomingPhone);

        if (StringUtils.hasText(incomingName)) {
            admin.setDisplayName(incomingName);
        }
        if (!StringUtils.hasText(admin.getEmail()) && StringUtils.hasText(incomingEmail)) {
            admin.setEmail(incomingEmail);
        }
        if (!StringUtils.hasText(admin.getPhone()) && StringUtils.hasText(incomingPhone)) {
            admin.setPhone(incomingPhone);
        }

        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        LocalDateTime requestedAt = LocalDateTime.now();
        LocalDateTime expiresAt = requestedAt.plusMinutes(15);
        String target = "email".equals(channel) ? incomingEmail : incomingPhone;
        DeliveryResult delivery = adminNotificationService.sendResetCode(channel, target, code);

        admin.setResetCode(code);
        admin.setResetCodeExpiresAt(expiresAt);
        admin.setResetCodeUsed(false);
        admin.setResetRequestedAt(requestedAt);
        admin.setResetChannel(channel);
        admin.setResetDeliveryStatus(delivery.status());
        admin.setResetDeliveredAt(delivery.delivered() ? LocalDateTime.now() : null);
        admin.setResetDeliveryTarget(delivery.target());
        admin.setResetDeliveryError(delivery.error());
        adminRepository.save(admin);

        String message = delivery.delivered()
                ? "Reset kodu seçilən ünvana avtomatik göndərildi. Kod 15 dəqiqə keçərlidir."
                : "Reset kod hazırlandı, amma avtomatik göndəriş tamamlanmadı. SMTP və ya WhatsApp API konfiqurasiyasını yoxlayın.";

        return new ForgotPasswordResponse(
                message,
                channel,
                delivery.delivered(),
                delivery.status(),
                delivery.target(),
                delivery.error(),
                null,
                null,
                null,
                expiresAt
        );
    }

    @Transactional
    public AdminProfileResponse resetPassword(ResetPasswordRequest request) {
        if (request == null || !StringUtils.hasText(request.code())) {
            throw new IllegalArgumentException("Reset kodu daxil edin");
        }
        Admin admin = adminRepository.findFirstByResetCodeIgnoreCaseOrderByResetRequestedAtDesc(request.code().trim())
                .orElseThrow(() -> new IllegalArgumentException("Reset kodu yanlışdır"));

        if (Boolean.TRUE.equals(admin.getResetCodeUsed())) {
            throw new IllegalArgumentException("Reset kodu artıq istifadə edilib");
        }
        if (admin.getResetCodeExpiresAt() == null || admin.getResetCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Reset kodunun vaxtı bitib");
        }

        updatePassword(admin, request.newPassword());
        admin.setResetCodeUsed(true);
        return toProfile(adminRepository.save(admin));
    }

    @Transactional
    public Admin ensureDefaultAdmin() {
        return adminRepository.findByUsernameIgnoreCase(DEFAULT_USERNAME).orElseGet(() -> {
            Admin admin = new Admin();
            admin.setUsername(DEFAULT_USERNAME);
            admin.setDisplayName("Properde Admin");
            admin.setRole("Baş Administrator");
            admin.setResetCodeUsed(true);
            admin.setResetDeliveryStatus("NONE");
            String salt = PasswordHashUtil.newSalt();
            admin.setSalt(salt);
            admin.setPasswordHash(PasswordHashUtil.hash(DEFAULT_PASSWORD, salt));
            return adminRepository.save(admin);
        });
    }

    public AdminUser toUser(Admin admin) {
        return new AdminUser(admin.getId(), admin.getUsername(), admin.getDisplayName(), admin.getRole(), admin.getEmail(), admin.getPhone());
    }

    public AdminProfileResponse toProfile(Admin admin) {
        return new AdminProfileResponse(
                admin.getId(),
                admin.getUsername(),
                admin.getDisplayName(),
                admin.getRole(),
                admin.getEmail(),
                admin.getPhone(),
                admin.getResetCode(),
                admin.getResetCodeExpiresAt(),
                admin.getResetCodeUsed(),
                admin.getResetRequestedAt(),
                admin.getResetChannel(),
                admin.getResetDeliveryStatus(),
                admin.getResetDeliveredAt(),
                admin.getResetDeliveryTarget(),
                admin.getResetDeliveryError()
        );
    }

    private void updatePassword(Admin admin, String newPassword) {
        if (!StringUtils.hasText(newPassword) || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("Yeni şifrə ən azı 6 simvol olmalıdır");
        }
        String salt = PasswordHashUtil.newSalt();
        admin.setSalt(salt);
        admin.setPasswordHash(PasswordHashUtil.hash(newPassword.trim(), salt));
    }

    private void assertRecoveryIdentity(Admin admin, String incomingEmail, String incomingPhone) {
        boolean hasSavedEmail = StringUtils.hasText(admin.getEmail());
        boolean hasSavedPhone = StringUtils.hasText(admin.getPhone());

        if (!hasSavedEmail && !hasSavedPhone) {
            return;
        }

        boolean emailMatches = hasSavedEmail
                && StringUtils.hasText(incomingEmail)
                && admin.getEmail().trim().equalsIgnoreCase(incomingEmail.trim());
        boolean phoneMatches = hasSavedPhone
                && StringUtils.hasText(incomingPhone)
                && normalizePhone(admin.getPhone()).equals(normalizePhone(incomingPhone));

        if (!emailMatches && !phoneMatches) {
            throw new IllegalArgumentException("Daxil edilən email və ya telefon admin profilinə uyğun deyil");
        }
    }

    private String resolveUsername(String username) {
        return StringUtils.hasText(username) ? username.trim() : DEFAULT_USERNAME;
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String cleanEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("0") && digits.length() == 10) {
            return "994" + digits.substring(1);
        }
        return digits;
    }

    private String normalizeChannel(String channel, String email, String phone) {
        String value = StringUtils.hasText(channel) ? channel.trim().toLowerCase(Locale.ROOT) : "";
        if (value.equals("email")) {
            return "email";
        }
        if (value.equals("phone") || value.equals("whatsapp")) {
            return "phone";
        }
        if (StringUtils.hasText(email)) {
            return "email";
        }
        return "phone";
    }
}
