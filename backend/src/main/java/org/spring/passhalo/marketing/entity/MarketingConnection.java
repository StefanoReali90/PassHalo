package org.spring.passhalo.marketing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spring.passhalo.user.entity.User;

@Entity
@Table(name = "marketing_connection", uniqueConstraints = @UniqueConstraint(columnNames = "owner_id"))
@Getter
@Setter
@NoArgsConstructor
public class MarketingConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(name = "credentials_ciphertext", nullable = false, length = 2048)
    private String credentialsCiphertext;

    // Provider-specific identifiers and webhook secret hashes; never raw credentials or tokens.
    @Column(nullable = false, length = 4096)
    private String configuration;
}
