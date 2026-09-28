package org.spring.passhalo.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidInvitationException extends PassHaloException {
    public InvalidInvitationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
