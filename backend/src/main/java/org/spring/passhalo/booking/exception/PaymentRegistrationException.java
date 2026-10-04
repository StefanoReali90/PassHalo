package org.spring.passhalo.booking.exception;

import org.spring.passhalo.user.exception.PassHaloException;
import org.springframework.http.HttpStatus;

public class PaymentRegistrationException extends PassHaloException {
    public PaymentRegistrationException(String message, HttpStatus status) {
        super(message, status);
    }
}
