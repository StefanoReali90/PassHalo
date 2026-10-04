package org.spring.passhalo.user.dto;

import jakarta.validation.constraints.NotNull;
import org.spring.passhalo.booking.enums.PaymentMethod;

import java.util.UUID;

public record StaffCheckInRequest(@NotNull UUID uuid,
                                 @NotNull(message = "Seleziona Contanti oppure Carta / POS") PaymentMethod paymentMethod) {
}
