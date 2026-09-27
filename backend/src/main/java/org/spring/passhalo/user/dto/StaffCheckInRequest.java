package org.spring.passhalo.user.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StaffCheckInRequest(@NotNull UUID uuid) {
}
