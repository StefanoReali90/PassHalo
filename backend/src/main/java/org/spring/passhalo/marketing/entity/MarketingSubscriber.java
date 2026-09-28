package org.spring.passhalo.marketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import org.spring.passhalo.user.entity.User;

@Entity
@Table(indexes = {
        @Index(name = "ix_marketing_email_lookup_hash", columnList = "email_lookup_hash"),
        @Index(name = "ix_marketing_expires_at", columnList = "expires_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_marketing_owner_email", columnNames = {"owner_id", "email_lookup_hash"}))
@Setter
@Getter
@NoArgsConstructor
public class MarketingSubscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Null only for legacy consents whose organizer cannot be established reliably.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "consent_event_id")
    private Long consentEventId;

    @Column(name = "consent_version", length = 40)
    private String consentVersion;

    // Legacy plaintext columns are nullable so the controlled backfill can clear them.
    @Column(nullable = true)
    private String name;

    @Column(nullable = true)
    private String surname;

    @Column(nullable = true)
    private String email;

    @Column(name = "name_ciphertext", length = 1024)
    private String nameCiphertext;

    @Column(name = "surname_ciphertext", length = 1024)
    private String surnameCiphertext;

    @Column(name = "email_ciphertext", length = 1024)
    private String emailCiphertext;

    @Column(name = "email_lookup_hash", length = 67)
    private String emailLookupHash;

    @Column(name = "unsubscribe_token_hash", length = 64)
    private String unsubscribeTokenHash;

    @Column(nullable = false)
    private LocalDateTime consentAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean isActive = true;

}
