package org.spring.passhalo.event.exception;

import org.spring.passhalo.user.exception.PassHaloException;
import org.springframework.http.HttpStatus;

public class EventDeletionException extends PassHaloException {
    public EventDeletionException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
