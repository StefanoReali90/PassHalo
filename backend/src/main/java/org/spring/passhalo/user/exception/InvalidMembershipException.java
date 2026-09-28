package org.spring.passhalo.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidMembershipException extends PassHaloException {
    public InvalidMembershipException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
