package org.spring.passhalo.notification.service;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.dto.BookingRequest;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.booking.mapper.BookingMapper;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.notification.repository.BookingConfirmationJobRepository;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "app.mail.booking-retry-initial-delay-ms=3600000")
class BookingConfirmationRetryIntegrationTest {
    @Autowired private BookingMapper bookingMapper;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BookingConfirmationJobRepository jobRepository;
    @Autowired private BookingConfirmationQueueService queueService;
    @Autowired private BookingConfirmationRetryScheduler scheduler;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private EmailService emailService;

    @Test
    void failedDeliveryIsRetriedWithoutStoringPlaintext() {
        String token = "unsubscribe-secret-for-test";
        Fixture fixture = transactions.execute(status -> createFixture(token));
        try {
            var queued = jobRepository.findByBookingId(fixture.bookingId()).orElseThrow();
            assertFalse(queued.getUnsubscribeTokenCiphertext().contains(token));

            doThrow(new IllegalStateException("SMTP unavailable")).doNothing().when(emailService)
                    .sendBookingConfirmation(eq(fixture.eventId()), eq("guest@example.test"), eq("Ada"),
                            eq("Retry test"), any(byte[].class), eq(token));

            scheduler.sendPending();
            var failed = jobRepository.findByBookingId(fixture.bookingId()).orElseThrow();
            assertEquals(1, failed.getAttempts());
            assertTrue(failed.getNextAttemptAt().isAfter(LocalDateTime.now()));

            transactions.executeWithoutResult(status -> {
                var job = jobRepository.findById(failed.getId()).orElseThrow();
                job.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
            });
            scheduler.sendPending();
            scheduler.sendPending();

            assertTrue(jobRepository.findByBookingId(fixture.bookingId()).isEmpty());
            verify(emailService, times(2)).sendBookingConfirmation(eq(fixture.eventId()),
                    eq("guest@example.test"), eq("Ada"), eq("Retry test"), any(byte[].class), eq(token));
        } finally {
            transactions.executeWithoutResult(status -> deleteFixture(fixture));
        }
    }

    @Test
    void cancelledBookingIsNotEmailed() {
        Fixture fixture = transactions.execute(status -> createFixture(null));
        try {
            transactions.executeWithoutResult(status -> {
                Booking booking = bookingRepository.findById(fixture.bookingId()).orElseThrow();
                booking.setBookingStatus(BookingStatus.CANCELLED);
            });

            scheduler.sendPending();

            assertTrue(jobRepository.findByBookingId(fixture.bookingId()).isEmpty());
            org.mockito.Mockito.verifyNoInteractions(emailService);
        } finally {
            transactions.executeWithoutResult(status -> deleteFixture(fixture));
        }
    }

    @Test
    void retriesContinueAfterFifthFailure() {
        Fixture fixture = transactions.execute(status -> createFixture(null));
        try {
            transactions.executeWithoutResult(status -> {
                var job = jobRepository.findByBookingId(fixture.bookingId()).orElseThrow();
                job.setAttempts(4);
            });
            doThrow(new IllegalStateException("SMTP unavailable")).when(emailService)
                    .sendBookingConfirmation(any(), any(), any(), any(), any(), any());

            scheduler.sendPending();

            var pending = jobRepository.findByBookingId(fixture.bookingId()).orElseThrow();
            assertEquals(5, pending.getAttempts());
            assertTrue(pending.getNextAttemptAt().isAfter(LocalDateTime.now().plusMinutes(15)));
            verify(emailService).sendBookingConfirmation(any(), any(), any(), any(), any(), any());
        } finally {
            transactions.executeWithoutResult(status -> deleteFixture(fixture));
        }
    }

    @Test
    void eventClosureRemovesPendingConfirmation() {
        Fixture fixture = transactions.execute(status -> createFixture("unsubscribe-secret-for-test"));
        try {
            transactions.executeWithoutResult(status -> queueService.discardForEvent(fixture.eventId()));

            assertTrue(jobRepository.findByBookingId(fixture.bookingId()).isEmpty());
        } finally {
            transactions.executeWithoutResult(status -> deleteFixture(fixture));
        }
    }

    private Fixture createFixture(String token) {
        User owner = new User();
        owner.setName("Test");
        owner.setSurname("Owner");
        owner.setEmail("retry-owner-" + UUID.randomUUID() + "@example.test");
        owner.setPassword("test-password");
        owner.setRole(Role.ADMIN);
        owner = userRepository.save(owner);

        Event event = new Event();
        event.setName("Retry test");
        event.setDescription("Test");
        event.setLocation("Test venue");
        event.setImageUrl("https://example.test/event.png");
        event.setStartDateTime(LocalDateTime.now().plusDays(1));
        event.setEndDateTime(LocalDateTime.now().plusDays(1).plusHours(3));
        event.setBookingPrice(10.0);
        event.setTotalTickets(10);
        event.setEventState(EventState.WAITING);
        event.setUser(owner);
        event = eventRepository.save(event);

        Booking booking = bookingMapper.toEntity(new BookingRequest(
                "Ada", "Lovelace", "guest@example.test", null, event.getId(), token != null));
        booking.setEvent(event);
        booking = bookingRepository.save(booking);
        queueService.enqueue(booking, token);
        return new Fixture(owner.getId(), event.getId(), booking.getId());
    }

    private void deleteFixture(Fixture fixture) {
        jobRepository.findByBookingId(fixture.bookingId()).ifPresent(jobRepository::delete);
        bookingRepository.deleteById(fixture.bookingId());
        eventRepository.deleteById(fixture.eventId());
        userRepository.deleteById(fixture.ownerId());
    }

    private record Fixture(Long ownerId, Long eventId, Long bookingId) { }
}
