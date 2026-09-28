package org.spring.passhalo.user.handler;

import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.user.exception.PassHaloException;
import org.spring.passhalo.user.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(PassHaloException.class)
    public ProblemDetail handlePassHaloException(PassHaloException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                ex.getStatus(),
                ex.getMessage()
        );
        log.warn("Business exception occurred - Status: {}", ex.getStatus());
        problemDetail.setTitle("Errore");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,"Si è verificato un errore interno"
        );
        log.error("Unhandled exception occurred - Type: {}", ex.getClass().getSimpleName());
        problemDetail.setTitle("Errore interno");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Dati di input non validi"
        );
        log.warn("Request validation failed");
        problemDetail.setTitle("Errore di validazione");
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList());

        return ResponseEntity.badRequest().body(problemDetail);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentialsException(BadCredentialsException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "Credenziali non valide"
        );
        log.warn("Authentication failed: invalid credentials attempt");
        problemDetail.setTitle("Errore di autenticazione");
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problemDetail);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Dati di input non validi"
        );
        log.warn("Request JSON parsing failed");
        problemDetail.setTitle("Errore di parsing JSON");
        problemDetail.setProperty("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(problemDetail);
    }

}
