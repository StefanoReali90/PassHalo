package org.spring.passhalo.booking.dto;

import jakarta.validation.constraints.NotNull;
import org.spring.passhalo.booking.enums.PaymentMethod;

public record PaymentMethodRequest(
        @NotNull(message = "Seleziona Contanti oppure Carta / POS") PaymentMethod paymentMethod
) {
}
