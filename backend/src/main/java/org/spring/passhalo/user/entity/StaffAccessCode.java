package org.spring.passhalo.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spring.passhalo.event.entity.Event;

import java.time.LocalDateTime;

@Entity
@Table(name = "staff_access_codes", uniqueConstraints =
        @UniqueConstraint(name = "uk_staff_access_code_hash", columnNames = "code_hash"))
@Getter
@Setter
@NoArgsConstructor
public class StaffAccessCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;
}
