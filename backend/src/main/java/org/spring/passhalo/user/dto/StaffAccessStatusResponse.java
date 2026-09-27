package org.spring.passhalo.user.dto;

import org.spring.passhalo.user.enums.StaffAccessState;

import java.time.LocalDateTime;

public record StaffAccessStatusResponse(StaffAccessState state, Long eventId, String eventName,
                                        LocalDateTime expiresAt) {
}
