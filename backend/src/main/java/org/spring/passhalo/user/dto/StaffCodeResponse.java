package org.spring.passhalo.user.dto;

import java.time.LocalDateTime;

public record StaffCodeResponse(String code, LocalDateTime expiresAt) {
}
