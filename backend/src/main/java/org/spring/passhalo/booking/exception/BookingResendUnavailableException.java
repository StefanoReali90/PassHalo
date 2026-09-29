package org.spring.passhalo.booking.exception;

import org.spring.passhalo.user.exception.PassHaloException;
import org.springframework.http.HttpStatus;

public class BookingResendUnavailableException extends PassHaloException {
    public BookingResendUnavailableException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
