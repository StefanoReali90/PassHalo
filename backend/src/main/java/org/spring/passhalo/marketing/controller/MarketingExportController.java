package org.spring.passhalo.marketing.controller;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.marketing.service.MarketingExportService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/marketing")
@RequiredArgsConstructor
public class MarketingExportController {
    private final MarketingExportService exportService;

    @GetMapping("/contacts.csv")
    public ResponseEntity<byte[]> exportContacts(Authentication authentication) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=passhalo-marketing-contacts.csv")
                .cacheControl(CacheControl.noStore())
                .body(exportService.exportContacts(authentication.getName()));
    }
}
