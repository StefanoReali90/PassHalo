package org.spring.passhalo.notification.controller;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.notification.service.OwnerSmtpSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/account/smtp")
@RequiredArgsConstructor
public class OwnerSmtpSettingsController {
    private final OwnerSmtpSettingsService service;

    @GetMapping
    public OwnerSmtpSettingsService.Status status(Authentication authentication) {
        return service.status(authentication.getName());
    }

    @PutMapping
    public OwnerSmtpSettingsService.Status save(Authentication authentication,
                                                @RequestBody OwnerSmtpSettingsService.SettingsRequest request) {
        return service.save(authentication.getName(), request);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(Authentication authentication) {
        service.delete(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/test")
    public ResponseEntity<Void> sendTest(Authentication authentication) {
        service.sendTest(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
