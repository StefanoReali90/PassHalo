package org.spring.passhalo.user.exception;

import org.springframework.http.HttpStatus;

public class InvalidJoinCodeException extends PassHaloException {
    public InvalidJoinCodeException() {
        super("Invalid or expired event code", HttpStatus.BAD_REQUEST);
    }
}
