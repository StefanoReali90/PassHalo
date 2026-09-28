package org.spring.passhalo.booking.mapper;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.dto.BookingRequest;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.event.entity.Event;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BookingMapperTest {
    private static final String KEY_ONE = Base64.getEncoder().encodeToString(new byte[]{
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
    });
    private static final String KEY_TWO = Base64.getEncoder().encodeToString(new byte[]{
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
            2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2
    });

    private final PiiCryptoService crypto = new PiiCryptoService(KEY_ONE, KEY_TWO);
    private final BookingMapper mapper = new BookingMapper(crypto);

    @Test
    void newBookingPersistsCiphertextAndLookupHashWithoutPlaintext() {
        BookingRequest request = new BookingRequest("Ada", "Lovelace", "ADA@example.test", null, 5L, false);

        Booking booking = mapper.toEntity(request);

        assertNull(booking.getName());
        assertNull(booking.getSurname());
        assertNull(booking.getEmail());
        assertNull(booking.getPhone());
        assertEquals("Ada", crypto.decrypt(booking.getNameCiphertext()));
        assertEquals("Lovelace", crypto.decrypt(booking.getSurnameCiphertext()));
        assertEquals("ADA@example.test", crypto.decrypt(booking.getEmailCiphertext()));
        assertNull(booking.getPhoneCiphertext());
        assertNull(booking.getConsentAt());
        assertEquals(crypto.emailLookupHash("ada@example.test"), booking.getEmailLookupHash());

        Booking optedIn = mapper.toEntity(
                new BookingRequest("Ada", "Lovelace", "ada@example.test", null, 5L, true));
        assertNotNull(optedIn.getConsentAt());
    }

    @Test
    void bookingResponseDecryptsNewValuesAndReadsLegacyRows() {
        Booking encryptedBooking = mapper.toEntity(
                new BookingRequest("Ada", "Lovelace", "ada@example.test", "123", 5L, false));
        encryptedBooking.setUuid(UUID.randomUUID());
        Event event = new Event();
        event.setId(5L);
        event.setName("Test event");
        encryptedBooking.setEvent(event);
        var response = mapper.toResponse(encryptedBooking, "qr");

        assertEquals("Ada", response.name());
        assertEquals("Lovelace", response.surname());
        assertEquals("ada@example.test", response.email());
        assertEquals("123", response.phone());

        Booking legacyBooking = new Booking();
        legacyBooking.setName("Legacy");
        legacyBooking.setSurname("Guest");
        legacyBooking.setEmail("legacy@example.test");
        legacyBooking.setUuid(UUID.randomUUID());
        legacyBooking.setEvent(event);
        var legacyResponse = mapper.toResponse(legacyBooking, "qr");

        assertEquals("Legacy", legacyResponse.name());
        assertEquals("legacy@example.test", legacyResponse.email());
    }
}
