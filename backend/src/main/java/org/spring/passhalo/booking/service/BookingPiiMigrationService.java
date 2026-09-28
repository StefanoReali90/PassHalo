package org.spring.passhalo.booking.service;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/** Idempotent, batched backfill for legacy plaintext booking rows. */
@Service
@RequiredArgsConstructor
public class BookingPiiMigrationService {
    private final BookingRepository bookingRepository;
    private final PiiCryptoService cryptoService;

    @Transactional
    public int migrateNextBatch() {
        List<Booking> batch = bookingRepository.findTop500ByEmailLookupHashIsNullAndEmailIsNotNullOrderByIdAsc();
        for (Booking booking : batch) {
            String name = plaintext(booking.getNameCiphertext(), booking.getName(), true);
            String surname = plaintext(booking.getSurnameCiphertext(), booking.getSurname(), true);
            String email = plaintext(booking.getEmailCiphertext(), booking.getEmail(), true);
            String phone = plaintext(booking.getPhoneCiphertext(), booking.getPhone(), false);

            booking.setNameCiphertext(cryptoService.encrypt(name));
            booking.setSurnameCiphertext(cryptoService.encrypt(surname));
            booking.setEmailCiphertext(cryptoService.encrypt(email));
            booking.setPhoneCiphertext(cryptoService.encrypt(phone));
            booking.setEmailLookupHash(cryptoService.emailLookupHash(email));

            // Clear plaintext only after every encrypted value and the lookup hash are ready.
            booking.setName(null);
            booking.setSurname(null);
            booking.setEmail(null);
            booking.setPhone(null);
            if (!Boolean.TRUE.equals(booking.getMarketingConsent())) {
                booking.setConsentAt(null);
            }
        }
        bookingRepository.saveAll(batch);
        return batch.size();
    }

    private String plaintext(String ciphertext, String legacyPlaintext, boolean required) {
        if (ciphertext == null) {
            if (required && legacyPlaintext == null) {
                throw new IllegalStateException("Legacy booking row is missing a required PII value");
            }
            return legacyPlaintext;
        }
        String decrypted = cryptoService.decrypt(ciphertext);
        if (legacyPlaintext != null && !Objects.equals(decrypted, legacyPlaintext)) {
            throw new IllegalStateException("Encrypted and legacy booking values do not match");
        }
        return decrypted;
    }
}
