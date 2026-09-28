package org.spring.passhalo.event.exception;

import org.spring.passhalo.user.exception.PassHaloException;
import org.springframework.http.HttpStatus;

public class InvalidCapacityException extends PassHaloException {
    public InvalidCapacityException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
