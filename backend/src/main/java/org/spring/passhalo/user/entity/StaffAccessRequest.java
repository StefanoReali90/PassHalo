package org.spring.passhalo.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.user.enums.StaffAccessState;

import java.time.LocalDateTime;

@Entity
@Table(name = "staff_access_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_staff_access_session_hash", columnNames = "session_hash"),
        indexes = @Index(name = "ix_staff_access_event_state", columnList = "event_id, state"))
@Getter
@Setter
@NoArgsConstructor
public class StaffAccessRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "session_hash", nullable = false, length = 64)
    private String sessionHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StaffAccessState state;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime decidedAt;
}
