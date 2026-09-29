package org.spring.passhalo.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.booking.service.QrCodeService;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.notification.entity.BookingConfirmationJob;
import org.spring.passhalo.notification.repository.BookingConfirmationJobRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingConfirmationRetryScheduler {
    private static final int BATCH_SIZE = 20;

    private final BookingConfirmationJobRepository jobRepository;
    private final PiiCryptoService cryptoService;
    private final QrCodeService qrCodeService;
    private final EmailService emailService;
    private final TransactionTemplate transactions;

    @Scheduled(fixedDelayString = "${app.mail.booking-retry-interval-ms:15000}",
            initialDelayString = "${app.mail.booking-retry-initial-delay-ms:5000}")
    public void sendPending() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = jobRepository.findReadyIds(now, PageRequest.of(0, BATCH_SIZE));
        for (Long id : ids) {
            Pending pending;
            try {
                pending = transactions.execute(status -> claimAndRead(id));
            } catch (RuntimeException exception) {
                transactions.executeWithoutResult(status -> recordFailure(id, null, exception));
                continue;
            }
            if (pending == null) continue;
            try {
                emailService.sendBookingConfirmation(pending.eventId(), pending.email(), pending.name(),
                        pending.eventName(), qrCodeService.createQrCodeBytes(pending.uuid().toString()),
                        pending.unsubscribeToken());
            } catch (RuntimeException exception) {
                transactions.executeWithoutResult(status -> recordFailure(id, pending.eventId(), exception));
                continue;
            }
            transactions.executeWithoutResult(status -> jobRepository.findById(id).ifPresent(jobRepository::delete));
            log.info("Conferma prenotazione consegnata alla posta eventoId={} jobId={}", pending.eventId(), id);
        }
    }

    private Pending claimAndRead(Long id) {
        LocalDateTime now = LocalDateTime.now();
        if (jobRepository.claim(id, now, now.plusMinutes(2)) != 1) return null;
        BookingConfirmationJob job = jobRepository.findById(id).orElse(null);
        if (job == null) return null;
        Booking booking = job.getBooking();
        if (booking.getBookingStatus() == BookingStatus.CANCELLED ||
                booking.getEvent().getEventState() == EventState.FINISHED ||
                booking.getEmailCiphertext() == null || booking.getNameCiphertext() == null) {
            jobRepository.delete(job);
            log.info("Conferma prenotazione scartata eventoId={} jobId={}", booking.getEvent().getId(), id);
            return null;
        }
        return new Pending(booking.getEvent().getId(), cryptoService.decrypt(booking.getEmailCiphertext()),
                cryptoService.decrypt(booking.getNameCiphertext()), booking.getEvent().getName(), booking.getUuid(),
                cryptoService.decrypt(job.getUnsubscribeTokenCiphertext()));
    }

    private void recordFailure(Long id, Long eventId, RuntimeException exception) {
        jobRepository.findById(id).ifPresent(job -> {
            Long loggedEventId = eventId != null ? eventId : job.getBooking().getEvent().getId();
            int attempts = job.getAttempts() + 1;
            long delayMinutes = Math.min(60, 1L << Math.min(attempts - 1, 6));
            job.setAttempts(attempts);
            job.setNextAttemptAt(LocalDateTime.now().plusMinutes(delayMinutes));
            if (attempts == 5 || attempts % 10 == 0) {
                log.error("Conferma prenotazione ancora in attesa eventoId={} jobId={} tentativi={} prossimoTraMinuti={} errore={}",
                        loggedEventId, id, attempts, delayMinutes, exception.getClass().getSimpleName());
            } else {
                log.warn("Invio conferma prenotazione fallito eventoId={} jobId={} tentativo={} prossimoTraMinuti={} errore={}",
                        loggedEventId, id, attempts, delayMinutes, exception.getClass().getSimpleName());
            }
        });
    }

    private record Pending(Long eventId, String email, String name, String eventName, UUID uuid,
                           String unsubscribeToken) { }
}
