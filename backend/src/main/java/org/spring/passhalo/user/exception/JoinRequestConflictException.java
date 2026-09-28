package org.spring.passhalo.user.exception;

import org.springframework.http.HttpStatus;

public class JoinRequestConflictException extends PassHaloException {
    public JoinRequestConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
