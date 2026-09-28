package org.spring.passhalo.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.InviteState;

import java.time.LocalDateTime;

@Entity
@Table(name= "event_invitations", uniqueConstraints = @UniqueConstraint(name="uk_event_invitation_token_hash", columnNames = {"token_hash"}))
@Getter
@Setter
@NoArgsConstructor
public class EventInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch= FetchType.LAZY)
    @JoinColumn(name = "event_id",nullable = false)
    private Event event;

    @Column(nullable = false, length = 254, name = "recipient_email")
    private String recipientEmail;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EventRole proposedRole;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InviteState inviteState = InviteState.PENDING;
    @Column(nullable = false, length = 64, name = "token_hash")
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column
    private LocalDateTime acceptedAt;

    @Column
    private LocalDateTime revokedAt;

    @ManyToOne(fetch= FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @ManyToOne(fetch= FetchType.LAZY)
    @JoinColumn(name = "accepted_by_id")
    private User acceptedBy;



}
