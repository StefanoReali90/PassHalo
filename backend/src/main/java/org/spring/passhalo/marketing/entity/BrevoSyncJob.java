package org.spring.passhalo.marketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spring.passhalo.user.entity.User;

@Entity
@Table(name = "brevo_sync_job", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "email_lookup_hash"}))
@Getter
@Setter
@NoArgsConstructor
public class BrevoSyncJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "email_lookup_hash", nullable = false, length = 67)
    private String emailLookupHash;

    @Column(name = "email_ciphertext", nullable = false, length = 1024)
    private String emailCiphertext;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    @Column(nullable = false)
    private long revision;
}
