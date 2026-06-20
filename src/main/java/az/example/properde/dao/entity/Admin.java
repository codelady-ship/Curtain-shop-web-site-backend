package az.example.properde.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "admins")
@Getter
@Setter
public class Admin extends BaseEntity {
    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(name = "salt", nullable = false, length = 64)
    private String salt;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "reset_code", length = 20)
    private String resetCode;

    @Column(name = "reset_code_expires_at")
    private LocalDateTime resetCodeExpiresAt;

    @Column(name = "reset_code_used")
    private Boolean resetCodeUsed = true;

    @Column(name = "reset_requested_at")
    private LocalDateTime resetRequestedAt;

    @Column(name = "reset_channel", length = 30)
    private String resetChannel;

    @Column(name = "reset_delivery_status", length = 40)
    private String resetDeliveryStatus;

    @Column(name = "reset_delivered_at")
    private LocalDateTime resetDeliveredAt;

    @Column(name = "reset_delivery_target", length = 180)
    private String resetDeliveryTarget;

    @Column(name = "reset_delivery_error", length = 500)
    private String resetDeliveryError;

    @Column(name = "role", length = 80)
    private String role = "Baş Administrator";
}
