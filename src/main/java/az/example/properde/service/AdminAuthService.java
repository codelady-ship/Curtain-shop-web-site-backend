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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {
    private static final String DEFAULT_USERNAME = "admin";
    static final int MIN_BOOTSTRAP_PASSWORD_LENGTH = 12;
    static final int MIN_PASSWORD_LENGTH = 12;
    static final int MAX_PASSWORD_BYTES = 72; // BCrypt limit
    static final int RESET_CODE_DIGITS = 8;
    static final Duration RESET_CODE_TTL = Duration.ofMinutes(15);
    static final Duration RESET_REQUEST_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_RESET_ATTEMPTS = 5;
    static final String FORGOT_PASSWORD_MESSAGE =
            "Məlumatlar admin profilində saxlanılan email və ya telefonla uyğundursa, reset kodu həmin ünvana göndərildi. Kod 15 dəqiqə keçərlidir.";
    private static final String INVALID_RESET_CODE = "Reset kodu yanlışdır və ya vaxtı bitib";
    private static final String INVALID_LOGIN = "Admin giriş məlumatları yanlışdır";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminRepository adminRepository;
    private final JwtTokenService jwtTokenService;
    private final AdminNotificationService adminNotificationService;

    // First-login admin bootstrap (ADMIN_USERNAME / ADMIN_PASSWORD). Used only when that admin
    // row does not exist yet; afterwards the password lives (hashed) in the admins table.
    @Value("${app.admin.username:}")
    private String configuredAdminUsername;

    @Value("${app.admin.password:}")
    private String configuredAdminPassword;

    @Transactional
    public LoginResponse login(String username, String password) {
        String resolvedUsername = StringUtils.hasText(username) ? username.trim() : bootstrapUsername();
        if (!resolvedUsername.equalsIgnoreCase(bootstrapUsername())) {
            throw new IllegalArgumentException(INVALID_LOGIN);
        }
        Admin admin = ensureDefaultAdmin();
        if (!PasswordHashUtil.matches(password, admin.getSalt(), admin.getPasswordHash())) {
            throw new IllegalArgumentException(INVALID_LOGIN);
        }
        if (PasswordHashUtil.needsRehash(admin.getPasswordHash()) && utf8Length(password) <= MAX_PASSWORD_BYTES) {
            // Old salted SHA-256 hash: replace it with BCrypt now that we have the plain password.
            setPasswordHash(admin, password);
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

    /** Authenticated (JWT) endpoint: the only place where the admin's recovery email/phone can change. */
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

    /**
     * Public endpoint. Never writes request data onto the admin and always returns the same generic
     * response. A code is only generated and sent when the admin already has the chosen contact
     * (email or phone) saved in the profile, the request names that same contact, and the previous
     * request is older than the cooldown. The code goes to the saved contact only.
     */
    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        ForgotPasswordResponse generic = new ForgotPasswordResponse(
                FORGOT_PASSWORD_MESSAGE, null, null, null, null, null, null, null, null, null);

        String requestedUsername = request != null && StringUtils.hasText(request.username())
                ? request.username().trim()
                : bootstrapUsername();
        Optional<Admin> found = adminRepository.findByUsernameForUpdate(requestedUsername);
        if (found.isEmpty()) {
            log.warn("Password reset requested for an unknown admin username; ignored.");
            return generic;
        }
        Admin admin = found.get();

        String incomingEmail = request == null ? null : cleanEmail(request.email());
        String incomingPhone = request == null ? null : normalizePhone(request.phone());
        String channel = normalizeChannel(request == null ? null : request.channel(), incomingEmail, incomingPhone);

        String savedTarget = "email".equals(channel) ? cleanEmail(admin.getEmail()) : normalizePhone(admin.getPhone());
        if (!StringUtils.hasText(savedTarget)) {
            log.warn("Password reset requested by {} but the admin has no saved {}; ignored. "
                    + "Save it in the admin profile first.", channel, channel);
            return generic;
        }
        String incomingTarget = "email".equals(channel) ? incomingEmail : incomingPhone;
        if (!savedTarget.equals(incomingTarget)) {
            log.warn("Password reset requested with a {} that does not match the admin profile; ignored.", channel);
            return generic;
        }

        LocalDateTime now = LocalDateTime.now();
        if (admin.getResetRequestedAt() != null && admin.getResetRequestedAt().isAfter(now.minus(RESET_REQUEST_COOLDOWN))) {
            log.warn("Password reset requested again within {} s; ignored.", RESET_REQUEST_COOLDOWN.toSeconds());
            return generic;
        }

        String code = newResetCode();
        DeliveryResult delivery = adminNotificationService.sendResetCode(channel, savedTarget, code);
        if (!delivery.delivered()) {
            log.warn("Password reset code was not delivered by {}: {} {}", channel, delivery.status(), delivery.error());
        }

        admin.setResetCode(code);
        admin.setResetCodeExpiresAt(now.plus(RESET_CODE_TTL));
        admin.setResetCodeUsed(false);
        admin.setResetAttempts(0);
        admin.setResetRequestedAt(now);
        admin.setResetChannel(channel);
        admin.setResetDeliveryStatus(delivery.status());
        admin.setResetDeliveredAt(delivery.delivered() ? LocalDateTime.now() : null);
        admin.setResetDeliveryTarget(delivery.target());
        admin.setResetDeliveryError(delivery.error());
        adminRepository.save(admin);
        return generic;
    }

    /**
     * Public endpoint. The code is single-use, expires after {@link #RESET_CODE_TTL} and is burned after
     * {@link #MAX_RESET_ATTEMPTS} wrong guesses (the failed-attempt counter is committed even though
     * the call fails, hence noRollbackFor).
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public AdminProfileResponse resetPassword(ResetPasswordRequest request) {
        if (request == null || !StringUtils.hasText(request.code())) {
            throw new IllegalArgumentException("Reset kodu daxil edin");
        }
        validateNewPassword(request.newPassword());

        Admin admin = adminRepository.findByUsernameForUpdate(bootstrapUsername())
                .orElseThrow(() -> new IllegalArgumentException(INVALID_RESET_CODE));

        boolean usable = StringUtils.hasText(admin.getResetCode())
                && !Boolean.TRUE.equals(admin.getResetCodeUsed())
                && admin.getResetCodeExpiresAt() != null
                && admin.getResetCodeExpiresAt().isAfter(LocalDateTime.now());
        if (!usable) {
            throw new IllegalArgumentException(INVALID_RESET_CODE);
        }

        if (!constantTimeEquals(admin.getResetCode(), request.code().trim())) {
            int attempts = (admin.getResetAttempts() == null ? 0 : admin.getResetAttempts()) + 1;
            admin.setResetAttempts(attempts);
            if (attempts >= MAX_RESET_ATTEMPTS) {
                admin.setResetCodeUsed(true);
                admin.setResetCode(null);
                log.warn("Password reset code burned after {} wrong attempts.", attempts);
            }
            adminRepository.save(admin);
            throw new IllegalArgumentException(INVALID_RESET_CODE);
        }

        updatePassword(admin, request.newPassword());
        admin.setResetCodeUsed(true);
        admin.setResetCode(null);
        admin.setResetAttempts(0);
        return toProfile(adminRepository.save(admin));
    }

    /**
     * Returns the admin for ADMIN_USERNAME, creating it on first use from ADMIN_PASSWORD.
     * Refuses to create it when ADMIN_PASSWORD is missing or shorter than 12 characters:
     * there is no built-in fallback password.
     */
    @Transactional
    public Admin ensureDefaultAdmin() {
        String username = bootstrapUsername();
        return adminRepository.findByUsernameIgnoreCase(username).orElseGet(() -> {
            String password = configuredAdminPassword == null ? "" : configuredAdminPassword.trim();
            boolean tooLong = utf8Length(password) > MAX_PASSWORD_BYTES;
            if (password.length() < MIN_BOOTSTRAP_PASSWORD_LENGTH || tooLong) {
                log.error("Admin '{}' does not exist and ADMIN_PASSWORD is {}. Set ADMIN_PASSWORD (at least {} characters, "
                                + "at most {} UTF-8 bytes) and restart; the admin account is created on the next admin login.",
                        username, password.isEmpty() ? "not set" : (tooLong ? "too long" : "too short"),
                        MIN_BOOTSTRAP_PASSWORD_LENGTH, MAX_PASSWORD_BYTES);
                throw new IllegalStateException("Admin hesabı yaradılmayıb: serverdə ADMIN_PASSWORD (ən azı "
                        + MIN_BOOTSTRAP_PASSWORD_LENGTH + " simvol, ən çox " + MAX_PASSWORD_BYTES + " bayt) təyin edilməlidir.");
            }
            Admin admin = new Admin();
            admin.setUsername(username);
            admin.setDisplayName("Properde Admin");
            admin.setRole("Baş Administrator");
            admin.setResetCodeUsed(true);
            admin.setResetAttempts(0);
            admin.setResetDeliveryStatus("NONE");
            setPasswordHash(admin, password);
            return adminRepository.save(admin);
        });
    }

    private String bootstrapUsername() {
        return StringUtils.hasText(configuredAdminUsername) ? configuredAdminUsername.trim() : DEFAULT_USERNAME;
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

    private void validateNewPassword(String newPassword) {
        if (!StringUtils.hasText(newPassword) || newPassword.trim().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Yeni şifrə ən azı " + MIN_PASSWORD_LENGTH + " simvol olmalıdır");
        }
        // BCrypt only uses the first 72 bytes; longer passwords are rejected instead of silently truncated.
        if (utf8Length(newPassword.trim()) > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Yeni şifrə ən çox " + MAX_PASSWORD_BYTES + " bayt (UTF-8) ola bilər");
        }
    }

    private static int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private void updatePassword(Admin admin, String newPassword) {
        validateNewPassword(newPassword);
        setPasswordHash(admin, newPassword.trim());
    }

    private void setPasswordHash(Admin admin, String rawPassword) {
        admin.setSalt(PasswordHashUtil.BCRYPT_SALT_MARKER);
        admin.setPasswordHash(PasswordHashUtil.hash(rawPassword));
    }

    private static String newResetCode() {
        int bound = (int) Math.pow(10, RESET_CODE_DIGITS);
        return String.format(Locale.ROOT, "%0" + RESET_CODE_DIGITS + "d", RANDOM.nextInt(bound));
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveUsername(String username) {
        return StringUtils.hasText(username) ? username.trim() : bootstrapUsername();
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
