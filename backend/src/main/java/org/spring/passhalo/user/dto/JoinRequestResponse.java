package org.spring.passhalo.user.dto;

import org.spring.passhalo.user.enums.JoinRequestState;

import java.time.LocalDateTime;

public record JoinRequestResponse(
        Long id,
        Long eventId,
        String eventName,
        Long requesterId,
        String requesterName,
        String requesterSurname,
        String requesterEmail,
        JoinRequestState state,
        LocalDateTime createdAt,
        LocalDateTime decidedAt
) {
}
