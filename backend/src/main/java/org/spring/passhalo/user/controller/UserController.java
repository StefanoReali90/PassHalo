package org.spring.passhalo.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.user.dto.*;
import org.spring.passhalo.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @PostMapping(path = "/register", consumes = "application/json", produces = "application/json")
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody AdminRegistrationRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(path = "/recover-password", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Void> recoverPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.recoverPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(path = "/me/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
                                               Authentication authentication) {
        userService.changePassword(changePasswordRequest, authentication.getName());
        return ResponseEntity.noContent().build();
    }
    @PatchMapping(path = "/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        userService.resetPassword(resetPasswordRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/login", consumes = "application/json", produces = "application/json")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        ResponseCookie cookie = ResponseCookie.from("jwt", response.token())
                .httpOnly(true)
                .path("/")
                .maxAge(24 * 60 * 60) // 1 day
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response);
    }

    @PostMapping(path="/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    @GetMapping(path="/me", produces = "application/json")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        UserResponse response = userService.getUserByEmail(email);
        return ResponseEntity.ok(response);
    }
}
