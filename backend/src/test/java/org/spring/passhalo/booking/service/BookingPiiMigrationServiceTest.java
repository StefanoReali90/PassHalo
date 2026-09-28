package org.spring.passhalo.booking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.security.PiiCryptoService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingPiiMigrationServiceTest {
    @Mock private BookingRepository bookingRepository;
    @Mock private PiiCryptoService cryptoService;
    @InjectMocks private BookingPiiMigrationService migrationService;

    @Test
    void encryptsLegacyValuesAndClearsPlaintextOnlyAfterPreparingAllFields() {
        Booking legacy = new Booking();
        legacy.setName("Ada");
        legacy.setSurname("Lovelace");
        legacy.setEmail("ADA@example.test");
        legacy.setPhone(null);
        when(bookingRepository.findTop500ByEmailLookupHashIsNullAndEmailIsNotNullOrderByIdAsc())
                .thenReturn(List.of(legacy));
        when(cryptoService.encrypt("Ada")).thenReturn("enc-name");
        when(cryptoService.encrypt("Lovelace")).thenReturn("enc-surname");
        when(cryptoService.encrypt("ADA@example.test")).thenReturn("enc-email");
        when(cryptoService.emailLookupHash("ADA@example.test")).thenReturn("v1:email-hash");

        int migrated = migrationService.migrateNextBatch();

        assertEquals(1, migrated);
        assertEquals("enc-name", legacy.getNameCiphertext());
        assertEquals("enc-surname", legacy.getSurnameCiphertext());
        assertEquals("enc-email", legacy.getEmailCiphertext());
        assertEquals("v1:email-hash", legacy.getEmailLookupHash());
        assertNull(legacy.getName());
        assertNull(legacy.getSurname());
        assertNull(legacy.getEmail());
        assertNull(legacy.getPhone());
        verify(bookingRepository).saveAll(List.of(legacy));
    }

    @Test
    void leavesLegacyPlaintextUntouchedWhenARequiredValueIsMissing() {
        Booking incomplete = new Booking();
        incomplete.setName("Ada");
        incomplete.setSurname("Lovelace");
        when(bookingRepository.findTop500ByEmailLookupHashIsNullAndEmailIsNotNullOrderByIdAsc())
                .thenReturn(List.of(incomplete));

        assertThrows(IllegalStateException.class, migrationService::migrateNextBatch);

        assertEquals("Ada", incomplete.getName());
        assertNull(incomplete.getNameCiphertext());
        verify(bookingRepository, never()).saveAll(anyList());
    }
}
