package org.spring.passhalo.marketing.service;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MarketingExportService {
    private final MarketingRepository repository;
    private final UserRepository userRepository;
    private final PiiCryptoService cryptoService;

    @Transactional(readOnly = true)
    public byte[] exportContacts(String username) {
        var owner = userRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (owner.getRole() != Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        StringBuilder csv = new StringBuilder("\uFEFFemail,first_name,last_name,consent_at,consent_expires_at\r\n");
        for (var subscriber : repository.findAllByOwnerIdAndIsActiveTrueAndExpiresAtAfter(
                owner.getId(), LocalDateTime.now())) {
            // Owner-less legacy consents and inactive/expired contacts cannot enter this query.
            // Encrypted PII is decrypted only for this authenticated organizer's download.
            csv.append(cell(cryptoService.decrypt(subscriber.getEmailCiphertext()))).append(',')
                    .append(cell(cryptoService.decrypt(subscriber.getNameCiphertext()))).append(',')
                    .append(cell(cryptoService.decrypt(subscriber.getSurnameCiphertext()))).append(',')
                    .append(cell(subscriber.getConsentAt().toString())).append(',')
                    .append(cell(subscriber.getExpiresAt().toString())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(String value) {
        if (value == null) value = "";
        String trimmed = value.stripLeading();
        // Quoting does not stop spreadsheet formulas. Neutralize user-provided formulas too.
        if ((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)
                || value.startsWith("\t") || value.startsWith("\r") || value.startsWith("\n")) {
            value = "'" + value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
