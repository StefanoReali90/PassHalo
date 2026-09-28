package org.spring.passhalo.user.dto;

import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.InviteState;

import java.time.LocalDateTime;

public record EventInvitationResponse(
        Long id,
        Long eventId,
        String recipientEmail,
        EventRole proposedRole,
        InviteState state,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime acceptedAt,
        LocalDateTime revokedAt
) {
}
