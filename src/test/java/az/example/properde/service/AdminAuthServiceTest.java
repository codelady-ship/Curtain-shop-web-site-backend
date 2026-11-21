package az.example.properde.service;

import az.example.properde.dao.dto.admin.AuthDtos.ForgotPasswordRequest;
import az.example.properde.dao.dto.admin.AuthDtos.ForgotPasswordResponse;
import az.example.properde.dao.dto.admin.AuthDtos.ResetPasswordRequest;
import az.example.properde.dao.entity.Admin;
import az.example.properde.repository.AdminRepository;
import az.example.properde.security.JwtTokenService;
import az.example.properde.security.PasswordHashUtil;
import az.example.properde.service.AdminNotificationService.DeliveryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests (no database) for the admin bootstrap and password-reset rules. */
class AdminAuthServiceTest {
    private AdminRepository repo;
    private AdminNotificationService notifications;
    private AdminAuthService service;

    @BeforeEach
    void setUp() {
        repo = mock(AdminRepository.class);
        notifications = mock(AdminNotificationService.class);
        service = new AdminAuthService(repo, new JwtTokenService("unit-test-secret-0123456789-abcdefghij", 3600), notifications);
        ReflectionTestUtils.setField(service, "configuredAdminUsername", "admin");
        ReflectionTestUtils.setField(service, "configuredAdminPassword", "");
        when(repo.save(any(Admin.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Admin admin(String email, String phone) {
        Admin admin = new Admin();
        admin.setUsername("admin");
        admin.setSalt(PasswordHashUtil.BCRYPT_SALT_MARKER);
        admin.setPasswordHash(PasswordHashUtil.hash("existing-password"));
        admin.setEmail(email);
        admin.setPhone(phone);
        when(repo.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));
        when(repo.findByUsernameForUpdate("admin")).thenReturn(Optional.of(admin));
        return admin;
    }

    @Test
    void loginReplacesLegacySha256HashWithBcrypt() throws Exception {
        Admin admin = admin(null, null);
        admin.setSalt("abc123");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        admin.setPasswordHash(HexFormat.of().formatHex(
                digest.digest("abc123:old-legacy-password".getBytes(StandardCharsets.UTF_8))));

        assertNotNull(service.login("admin", "old-legacy-password").token());
        assertTrue(admin.getPasswordHash().startsWith("$2"));
        assertEquals(PasswordHashUtil.BCRYPT_SALT_MARKER, admin.getSalt());
        assertNotNull(service.login("admin", "old-legacy-password").token());
        assertThrows(IllegalArgumentException.class, () -> service.login("admin", "wrong-password"));
    }

    /** The demo row exactly as Liquibase 9.1 seeds it (hash read from the changelog). */
    private Admin seededDemoAdmin() throws Exception {
        String changelog;
        try (var in = getClass().getResourceAsStream("/db/changelog/9.1-demo-admin.yml")) {
            changelog = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        java.util.regex.Matcher hash = java.util.regex.Pattern.compile("'(\\$2a\\$10\\$[^']{53})'").matcher(changelog);
        assertTrue(hash.find(), "BCrypt hash in 9.1-demo-admin.yml");
        Admin demo = new Admin();
        demo.setId(2L);
        demo.setUsername("demo");
        demo.setSalt(PasswordHashUtil.BCRYPT_SALT_MARKER);
        demo.setPasswordHash(hash.group(1));
        demo.setDemo(true);
        when(repo.findByUsernameIgnoreCase("demo")).thenReturn(Optional.of(demo));
        return demo;
    }

    @Test
    void seededDemoAdminLogsInWithTheDemoRole() throws Exception {
        seededDemoAdmin();
        JwtTokenService jwt = new JwtTokenService("unit-test-secret-0123456789-abcdefghij", 3600);

        var response = service.login("demo", "demo1234");
        assertTrue(response.user().demo());
        assertTrue(jwt.parse(response.token()).demo());
        assertThrows(IllegalArgumentException.class, () -> service.login("demo", "wrong-password"));
        verify(repo, never()).save(any(Admin.class));
    }

    @Test
    void demoLoginCanBeTurnedOff() throws Exception {
        seededDemoAdmin();
        ReflectionTestUtils.setField(service, "demoAdminEnabled", false);
        assertThrows(IllegalArgumentException.class, () -> service.login("demo", "demo1234"));
    }

    @Test
    void otherUsernamesThanTheBootstrapAdminNeedTheDemoFlag() {
        Admin other = new Admin();
        other.setUsername("someone");
        other.setSalt(PasswordHashUtil.BCRYPT_SALT_MARKER);
        other.setPasswordHash(PasswordHashUtil.hash("someone-password"));
        when(repo.findByUsernameIgnoreCase("someone")).thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class, () -> service.login("someone", "someone-password"));
    }

    @Test
    void realAdminStillGetsAFullAdminToken() {
        admin(null, null).setId(1L);
        JwtTokenService jwt = new JwtTokenService("unit-test-secret-0123456789-abcdefghij", 3600);
        var response = service.login("admin", "existing-password");
        assertFalse(response.user().demo());
        assertFalse(jwt.parse(response.token()).demo());
    }

    @Test
    void newPasswordNeedsTwelveCharacters() {
        admin(null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(null, "existing-password", "short-pass1"));
        service.changePassword(null, "existing-password", "long-enough-pass");
    }

    @Test
    void passwordsOver72Utf8BytesAreRejected() {
        admin(null, null);
        String seventyFourBytes = "ə".repeat(37); // 37 characters, 74 UTF-8 bytes
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(null, "existing-password", seventyFourBytes));
        assertThrows(IllegalArgumentException.class,
                () -> service.changePassword(null, "existing-password", "x".repeat(73)));
        service.changePassword(null, "existing-password", "x".repeat(72));

        when(repo.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(service, "configuredAdminPassword", seventyFourBytes);
        assertThrows(IllegalStateException.class, () -> service.ensureDefaultAdmin());
    }

    @Test
    void bootstrapRefusesMissingOrShortAdminPassword() {
        when(repo.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.ensureDefaultAdmin());
        ReflectionTestUtils.setField(service, "configuredAdminPassword", "short-pass1");
        assertThrows(IllegalStateException.class, () -> service.ensureDefaultAdmin());
        assertThrows(IllegalStateException.class, () -> service.login("admin", "anything"));
        verify(repo, never()).save(any());
    }

    @Test
    void bootstrapAcceptsRenderGeneratedPassword() {
        // render.yaml uses ADMIN_PASSWORD generateValue: true -> a random base64 value (about 44 chars, may contain + / =).
        String generated = "q8Zr+3kP/0vWb9LxT2mNcY7eHf4sJ1aQ6uRgDiE5oB0=";
        when(repo.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(service, "configuredAdminPassword", generated);
        Admin created = service.ensureDefaultAdmin();
        assertTrue(PasswordHashUtil.matches(generated, created.getSalt(), created.getPasswordHash()));
    }

    @Test
    void bootstrapCreatesAdminFromLongAdminPassword() {
        when(repo.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
        ReflectionTestUtils.setField(service, "configuredAdminPassword", "a-long-password-123");
        Admin created = service.ensureDefaultAdmin();
        assertTrue(PasswordHashUtil.matches("a-long-password-123", created.getSalt(), created.getPasswordHash()));
    }

    @Test
    void forgotPasswordWithoutSavedContactChangesNothing() {
        Admin admin = admin(null, null);
        ForgotPasswordResponse response = service.forgotPassword(
                new ForgotPasswordRequest("admin", "Attacker", "attacker@example.com", "0501112233", "email"));
        assertEquals(AdminAuthService.FORGOT_PASSWORD_MESSAGE, response.message());
        assertNull(admin.getEmail());
        assertNull(admin.getPhone());
        assertNull(admin.getResetCode());
        verify(repo, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void forgotPasswordWithWrongContactChangesNothing() {
        Admin admin = admin("owner@example.com", null);
        ForgotPasswordResponse response = service.forgotPassword(
                new ForgotPasswordRequest("admin", null, "attacker@example.com", null, "email"));
        assertEquals(AdminAuthService.FORGOT_PASSWORD_MESSAGE, response.message());
        assertEquals("owner@example.com", admin.getEmail());
        verify(repo, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void forgotPasswordSendsCodeOnlyToSavedContactAndHasCooldown() {
        Admin admin = admin("owner@example.com", null);
        when(notifications.sendResetCode(anyString(), anyString(), anyString()))
                .thenReturn(DeliveryResult.sent("email", "owner@example.com"));

        ForgotPasswordResponse response = service.forgotPassword(
                new ForgotPasswordRequest("admin", null, "Owner@Example.com", null, "email"));
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(notifications).sendResetCode(eq("email"), eq("owner@example.com"), code.capture());
        assertTrue(code.getValue().matches("[0-9]{8}"));
        assertEquals(code.getValue(), admin.getResetCode());
        assertNull(response.code());
        assertNull(response.deliveryTarget());

        service.forgotPassword(new ForgotPasswordRequest("admin", null, "owner@example.com", null, "email"));
        verify(notifications, times(1)).sendResetCode(anyString(), anyString(), anyString());
    }

    @Test
    void resetCodeIsSingleUse() {
        Admin admin = admin("owner@example.com", null);
        admin.setResetCode("12345678");
        admin.setResetCodeUsed(false);
        admin.setResetAttempts(0);
        admin.setResetCodeExpiresAt(LocalDateTime.now().plusMinutes(10));

        service.resetPassword(new ResetPasswordRequest("12345678", "new-password-1"));
        assertTrue(PasswordHashUtil.matches("new-password-1", admin.getSalt(), admin.getPasswordHash()));
        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(new ResetPasswordRequest("12345678", "another-password")));
    }

    @Test
    void expiredResetCodeIsRejected() {
        Admin admin = admin("owner@example.com", null);
        admin.setResetCode("12345678");
        admin.setResetCodeUsed(false);
        admin.setResetCodeExpiresAt(LocalDateTime.now().minusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(new ResetPasswordRequest("12345678", "new-password-1")));
        assertTrue(PasswordHashUtil.matches("existing-password", admin.getSalt(), admin.getPasswordHash()));
    }

    @Test
    void resetCodeIsBurnedAfterMaxWrongAttempts() {
        Admin admin = admin("owner@example.com", null);
        admin.setResetCode("12345678");
        admin.setResetCodeUsed(false);
        admin.setResetAttempts(0);
        admin.setResetCodeExpiresAt(LocalDateTime.now().plusMinutes(10));

        for (int i = 0; i < AdminAuthService.MAX_RESET_ATTEMPTS; i++) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.resetPassword(new ResetPasswordRequest("00000000", "new-password-1")));
        }
        assertTrue(admin.getResetCodeUsed());
        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(new ResetPasswordRequest("12345678", "new-password-1")));
        assertTrue(PasswordHashUtil.matches("existing-password", admin.getSalt(), admin.getPasswordHash()));
    }
}
