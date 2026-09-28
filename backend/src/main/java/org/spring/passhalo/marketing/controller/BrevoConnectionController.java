package org.spring.passhalo.marketing.controller;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.marketing.service.BrevoConnectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/marketing/brevo")
@RequiredArgsConstructor
public class BrevoConnectionController {
    private final BrevoConnectionService service;

    @GetMapping
    public BrevoConnectionService.Status status(Authentication authentication) {
        return service.status(authentication.getName());
    }

    @PostMapping
    public BrevoConnectionService.Status connect(Authentication authentication,
                                                  @Valid @RequestBody ConnectRequest request) {
        return service.connect(authentication.getName(), request.apiKey(), request.listId());
    }

    @PutMapping
    public BrevoConnectionService.Status rotateKey(Authentication authentication,
                                                    @Valid @RequestBody RotateKeyRequest request) {
        return service.rotateKey(authentication.getName(), request.apiKey());
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect(Authentication authentication) {
        service.disconnect(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/webhook/{ownerId}")
    public ResponseEntity<Void> webhook(@PathVariable long ownerId,
                                        @RequestHeader(value = "X-PassHalo-Brevo-Secret", required = false) String secret,
                                        @RequestBody JsonNode body) {
        service.unsubscribeFromBrevo(ownerId, secret, body);
        return ResponseEntity.noContent().build();
    }

    public record ConnectRequest(@NotBlank @Size(max = 512) String apiKey,
                                 @NotNull @Positive Long listId) { }

    public record RotateKeyRequest(@NotBlank @Size(max = 512) String apiKey) { }
}
