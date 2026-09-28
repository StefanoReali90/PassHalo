package org.spring.passhalo.user.exception;

import org.springframework.http.HttpStatus;

public class InvitationAlreadyExistsException extends PassHaloException {
    public InvitationAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
