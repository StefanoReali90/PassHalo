package org.spring.passhalo.user.dto;

import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.MembershipState;

import java.time.LocalDateTime;

public record EventMembershipResponse(
        Long id,
        Long eventId,
        Long collaboratorId,
        String collaboratorName,
        String collaboratorSurname,
        String collaboratorEmail,
        EventRole role,
        MembershipState state,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        LocalDateTime revokedAt
) {
}
