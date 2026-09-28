package org.spring.passhalo.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @NotBlank
        @Size(max = 100)
        String surname,
        @NotBlank
        @Email
        @Size(max = 254)
        String email,
        @Size(max = 40)
        String phone,
        @NotNull
        @Positive
        Long eventId,
        boolean marketingConsent
) {
}
