package org.spring.passhalo.marketing.service;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;

/** Idempotent, batched backfill for legacy plaintext marketing subscribers. */
@Service
@RequiredArgsConstructor
public class MarketingPiiMigrationService {
    private final MarketingRepository marketingRepository;
    private final PiiCryptoService cryptoService;

    @Value("${app.marketing.retention-months:24}")
    private long retentionMonths = 24;

    @Transactional
    public int migrateNextBatch() {
        if (retentionMonths < 1) throw new IllegalStateException("Marketing retention must be at least one month");
        List<MarketingSubscriber> batch = marketingRepository
                .findTop500ByEmailLookupHashIsNullAndEmailIsNotNullOrderByIdAsc();
        for (MarketingSubscriber subscriber : batch) {
            if (subscriber.getExpiresAt() == null) {
                subscriber.setExpiresAt((subscriber.getConsentAt() == null ? LocalDateTime.now() : subscriber.getConsentAt())
                        .plusMonths(retentionMonths));
            }
            String name = plaintext(subscriber.getNameCiphertext(), subscriber.getName());
            String surname = plaintext(subscriber.getSurnameCiphertext(), subscriber.getSurname());
            String email = plaintext(subscriber.getEmailCiphertext(), subscriber.getEmail());

            subscriber.setNameCiphertext(cryptoService.encrypt(name));
            subscriber.setSurnameCiphertext(cryptoService.encrypt(surname));
            subscriber.setEmailCiphertext(cryptoService.encrypt(email));
            subscriber.setEmailLookupHash(cryptoService.emailLookupHash(email));
            subscriber.setName(null);
            subscriber.setSurname(null);
            subscriber.setEmail(null);
        }
        marketingRepository.saveAll(batch);
        return batch.size();
    }

    private String plaintext(String ciphertext, String legacyPlaintext) {
        if (ciphertext == null) {
            if (legacyPlaintext == null) {
                throw new IllegalStateException("Legacy marketing subscriber is missing a required PII value");
            }
            return legacyPlaintext;
        }
        String decrypted = cryptoService.decrypt(ciphertext);
        if (legacyPlaintext != null && !Objects.equals(decrypted, legacyPlaintext)) {
            throw new IllegalStateException("Encrypted and legacy marketing values do not match");
        }
        return decrypted;
    }
}
