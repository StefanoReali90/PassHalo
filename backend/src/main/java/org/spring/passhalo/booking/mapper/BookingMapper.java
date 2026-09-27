package org.spring.passhalo.booking.mapper;

import org.spring.passhalo.booking.dto.BookingRequest;
import org.spring.passhalo.booking.dto.BookingResponse;
import org.spring.passhalo.booking.dto.CheckInResponse;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.security.PiiCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

@Component
@RequiredArgsConstructor
public class BookingMapper {
    private final PiiCryptoService cryptoService;

    public Booking toEntity(BookingRequest bookingRequest) {
        Booking booking = new Booking();
        booking.setNameCiphertext(cryptoService.encrypt(bookingRequest.name()));
        booking.setSurnameCiphertext(cryptoService.encrypt(bookingRequest.surname()));
        booking.setEmailCiphertext(cryptoService.encrypt(bookingRequest.email()));
        booking.setPhoneCiphertext(cryptoService.encrypt(bookingRequest.phone()));
        booking.setEmailLookupHash(cryptoService.emailLookupHash(bookingRequest.email()));
        booking.setMarketingConsent(bookingRequest.marketingConsent());
        booking.setConsentAt(bookingRequest.marketingConsent() ? new Timestamp(System.currentTimeMillis()) : null);
        return booking;

    }

    public BookingResponse toResponse(Booking booking, String qrCodeBase64) {
        BookingResponse bookingResponse = new BookingResponse(booking.getUuid(),
                decryptOrLegacy(booking.getNameCiphertext(), booking.getName()),
                decryptOrLegacy(booking.getSurnameCiphertext(), booking.getSurname()),
                decryptOrLegacy(booking.getEmailCiphertext(), booking.getEmail()),
                decryptOrLegacy(booking.getPhoneCiphertext(), booking.getPhone()),
                booking.getEvent().getId(),
                booking.getEvent().getName(),
                booking.getBookingStatus(),
                booking.getCreatedAt(),
                qrCodeBase64,
                booking.getMarketingConsent());
        ;
        return bookingResponse;
    }

    private String decryptOrLegacy(String ciphertext, String legacyPlaintext) {
        return ciphertext == null ? legacyPlaintext : cryptoService.decrypt(ciphertext);
    }

    public CheckInResponse toCheckInResponse(Booking booking) {
        return new CheckInResponse(booking.getEvent().getName());
    }
}
