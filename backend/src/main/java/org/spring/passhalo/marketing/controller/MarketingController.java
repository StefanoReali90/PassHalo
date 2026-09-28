package org.spring.passhalo.marketing.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.marketing.service.MarketingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/marketing")
@RequiredArgsConstructor
public class MarketingController {
    private final MarketingService marketingService;

    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        marketingService.unsubscribe(request.token());
        return ResponseEntity.noContent().build();
    }

    public record UnsubscribeRequest(@NotBlank @Size(max = 128) String token) { }
}
