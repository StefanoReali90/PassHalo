package org.spring.passhalo.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.spring.passhalo.user.enums.EventRole;

public record CreateEventInvitationRequest(
        @NotBlank @Email String email,
        @NotNull EventRole role
) {
}
