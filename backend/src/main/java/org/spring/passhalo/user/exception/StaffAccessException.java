package org.spring.passhalo.user.exception;

import org.springframework.http.HttpStatus;

public class StaffAccessException extends PassHaloException {
    public StaffAccessException(String message, HttpStatus status) {
        super(message, status);
    }
}
