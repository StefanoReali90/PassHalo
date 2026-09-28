package org.spring.passhalo.user.dto;

import java.time.LocalDateTime;

public record JoinCodeResponse(String code, LocalDateTime expiresAt) {
}
