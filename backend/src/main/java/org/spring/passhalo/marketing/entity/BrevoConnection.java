package org.spring.passhalo.marketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spring.passhalo.user.entity.User;

@Entity
@Table(name = "brevo_connection", uniqueConstraints = @UniqueConstraint(columnNames = "owner_id"))
@Getter
@Setter
@NoArgsConstructor
public class BrevoConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "api_key_ciphertext", nullable = false, length = 2048)
    private String apiKeyCiphertext;

    @Column(name = "list_id", nullable = false)
    private Long listId;

    @Column(name = "organization_id", nullable = false, length = 100)
    private String organizationId;

    @Column(name = "webhook_id", nullable = false)
    private Long webhookId;

    @Column(name = "webhook_secret_hash", nullable = false, length = 64)
    private String webhookSecretHash;
}
