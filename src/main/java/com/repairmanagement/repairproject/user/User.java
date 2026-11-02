package com.repairmanagement.repairproject.user;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", nullable = false, length = 190, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "phone", length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "platform_role", nullable = false, length = 30)
    private PlatformRole platformRole = PlatformRole.USER;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected User() {}

    public User(String fullName, String email, String passwordHash, String phone) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.phone = phone;
    }

    public User(String fullName, String email, String passwordHash, String phone,
                PlatformRole platformRole) {
        this(fullName, email, passwordHash, phone);
        if (platformRole != PlatformRole.OWNER && platformRole != PlatformRole.CLIENT) {
            throw new IllegalArgumentException("Invalid public account type");
        }
        this.platformRole = platformRole;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getPhone() { return phone; }
    public PlatformRole getPlatformRole() { return platformRole; }
    public boolean isActive() { return active; }
}
