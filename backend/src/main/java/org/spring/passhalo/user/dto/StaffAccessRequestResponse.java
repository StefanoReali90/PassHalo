package org.spring.passhalo.user.dto;

import org.spring.passhalo.user.enums.StaffAccessState;

import java.time.LocalDateTime;

public record StaffAccessRequestResponse(Long id, StaffAccessState state, LocalDateTime createdAt) {
}
