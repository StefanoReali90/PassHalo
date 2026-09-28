package org.spring.passhalo.booking.service;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.dto.BookingRequest;
import org.spring.passhalo.booking.exception.NoTicketException;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.notification.service.EmailService;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ConcurrentBookingIntegrationTest {
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private EmailService emailService;

    @Test
    void twoCustomersCannotReserveTheLastSeat() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = transaction.execute(status -> createFixture());
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstReserved = new CountDownLatch(1);
        CountDownLatch releaseFirstCommit = new CountDownLatch(1);

        try {
            var first = executor.submit(() -> transaction.execute(status -> {
                bookingService.createBooking(request(fixture.eventId(), "first@example.test"));
                firstReserved.countDown();
                try {
                    if (!releaseFirstCommit.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("First reservation did not commit in time");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));
            assertTrue(firstReserved.await(10, TimeUnit.SECONDS));

            var second = executor.submit(() ->
                    bookingService.createBooking(request(fixture.eventId(), "second@example.test")));
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));

            releaseFirstCommit.countDown();
            first.get(10, TimeUnit.SECONDS);
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));
            assertInstanceOf(NoTicketException.class, rejected.getCause());
            assertEquals(1, bookingRepository.countByEventId(fixture.eventId()));
        } finally {
            releaseFirstCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            transaction.execute(status -> {
                bookingRepository.deleteAll(bookingRepository.findAllByEventId(fixture.eventId()));
                bookingRepository.flush();
                eventRepository.deleteById(fixture.eventId());
                userRepository.deleteById(fixture.ownerId());
                return null;
            });
        }
    }

    private BookingRequest request(Long eventId, String email) {
        return new BookingRequest("Test", "Guest", email, null, eventId, false);
    }

    private Fixture createFixture() {
        User owner = new User();
        owner.setName("Test");
        owner.setSurname("Owner");
        owner.setEmail("booking-owner-" + UUID.randomUUID() + "@example.test");
        owner.setPassword("test-password");
        owner.setRole(Role.ADMIN);
        owner = userRepository.save(owner);

        Event event = new Event();
        event.setName("Last seat event");
        event.setDescription("Concurrency test");
        event.setLocation("Test venue");
        event.setImageUrl("https://example.test/event.jpg");
        event.setStartDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).plusHours(1));
        event.setEndDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).plusHours(3));
        event.setNormalPrice(15.0);
        event.setBookingPrice(10.0);
        event.setTotalTickets(1);
        event.setEventState(EventState.WAITING);
        event.setUser(owner);
        event = eventRepository.save(event);
        return new Fixture(owner.getId(), event.getId());
    }

    private record Fixture(Long ownerId, Long eventId) {}
}
