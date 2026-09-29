package org.spring.passhalo.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spring.passhalo.user.entity.User;

@Entity
@Table(name = "owner_smtp_settings", uniqueConstraints = @UniqueConstraint(columnNames = "owner_id"))
@Getter
@Setter
@NoArgsConstructor
public class OwnerSmtpSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 253)
    private String host;

    @Column(nullable = false)
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Encryption encryption;

    @Column(nullable = false, length = 320)
    private String username;

    @Column(name = "password_ciphertext", nullable = false, length = 2048)
    private String passwordCiphertext;

    @Column(name = "from_email", nullable = false, length = 320)
    private String fromEmail;

    @Column(name = "from_name", nullable = false, length = 100)
    private String fromName;

    public enum Encryption { STARTTLS, SSL }
}
