package org.spring.passhalo.notification.service;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.notification.entity.BookingConfirmationJob;
import org.spring.passhalo.notification.repository.BookingConfirmationJobRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BookingConfirmationQueueService {
    private final BookingConfirmationJobRepository jobRepository;
    private final PiiCryptoService cryptoService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Booking booking, String unsubscribeToken) {
        BookingConfirmationJob job = new BookingConfirmationJob();
        LocalDateTime now = LocalDateTime.now();
        job.setBooking(booking);
        job.setUnsubscribeTokenCiphertext(cryptoService.encrypt(unsubscribeToken));
        job.setCreatedAt(now);
        job.setNextAttemptAt(now);
        jobRepository.save(job);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void discardForBooking(Long bookingId) {
        jobRepository.deleteByBookingId(bookingId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void discardForEvent(Long eventId) {
        jobRepository.deleteByBooking_Event_Id(eventId);
    }
}
