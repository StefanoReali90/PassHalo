package org.spring.passhalo.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.MembershipState;

import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints  = @UniqueConstraint(name="uk_event_membership_event_collaborator", columnNames = {"event_id", "collaborator_id"}))
@Getter
@Setter
@NoArgsConstructor
public class EventMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipState membershipState;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime validFrom;

    @Column
    private LocalDateTime validUntil;


    @Column
    private LocalDateTime revokedAt;

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "collaborator_id", nullable = false)
    private User collaborator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;
}
