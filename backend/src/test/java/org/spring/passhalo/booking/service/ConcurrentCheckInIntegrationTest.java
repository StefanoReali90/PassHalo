package org.spring.passhalo.booking.service;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.dto.CheckInResponse;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.booking.exception.AlreadyValidatedException;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ConcurrentCheckInIntegrationTest {
    @Autowired private BookingService bookingService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void twoConcurrentCheckInsValidateTheSameQrOnlyOnce() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Fixture fixture = createFixture(transaction);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstValidated = new CountDownLatch(1);
        CountDownLatch releaseFirstCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);

        try {
            Future<CheckInResponse> first = executor.submit(() -> transaction.execute(status -> {
                CheckInResponse response = bookingService.checkInBooking(fixture.uuid(), fixture.owner());
                firstValidated.countDown();
                try {
                    if (!releaseFirstCommit.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("First check-in did not get permission to commit");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return response;
            }));
            assertTrue(firstValidated.await(10, TimeUnit.SECONDS));

            Future<CheckInResponse> second = executor.submit(() -> {
                secondStarted.countDown();
                return bookingService.checkInBooking(fixture.uuid(), fixture.owner());
            });
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(250, TimeUnit.MILLISECONDS));

            releaseFirstCommit.countDown();
            assertEquals(fixture.eventName(), first.get(10, TimeUnit.SECONDS).eventName());
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));
            assertInstanceOf(AlreadyValidatedException.class, rejected.getCause());

            transaction.execute(status -> {
                Booking saved = bookingRepository.findByUuid(fixture.uuid()).orElseThrow();
                assertEquals(BookingStatus.VALIDATED, saved.getBookingStatus());
                assertNotNull(saved.getCheckInDateTime());
                return null;
            });
        } finally {
            releaseFirstCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            deleteFixture(transaction, fixture);
        }
    }

    private Fixture createFixture(TransactionTemplate transaction) {
        return transaction.execute(status -> {
            User owner = new User();
            owner.setName("Check-in");
            owner.setSurname("Owner");
            owner.setEmail("check-in-owner-" + UUID.randomUUID() + "@example.test");
            owner.setPassword("test-password");
            owner.setRole(Role.ADMIN);
            owner = userRepository.save(owner);

            Event event = new Event();
            event.setName("Concurrent check-in event");
            event.setDescription("Concurrency test");
            event.setLocation("Test venue");
            event.setImageUrl("https://example.test/event.jpg");
            event.setStartDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).minusHours(1));
            event.setEndDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).plusHours(2));
            event.setNormalPrice(15.0);
            event.setBookingPrice(10.0);
            event.setTotalTickets(10);
            event.setEventState(EventState.IN_PROGRESS);
            event.setUser(owner);
            event = eventRepository.save(event);

            Booking booking = new Booking();
            booking.setEvent(event);
            booking.setName("Test");
            booking.setSurname("Guest");
            booking.setEmail("check-in-guest@example.test");
            booking = bookingRepository.saveAndFlush(booking);
            return new Fixture(owner, owner.getId(), event.getId(), event.getName(), booking.getUuid());
        });
    }

    private void deleteFixture(TransactionTemplate transaction, Fixture fixture) {
        transaction.execute(status -> {
            bookingRepository.findByUuid(fixture.uuid()).ifPresent(bookingRepository::delete);
            eventRepository.deleteById(fixture.eventId());
            userRepository.deleteById(fixture.ownerId());
            return null;
        });
    }

    private record Fixture(User owner, Long ownerId, Long eventId, String eventName, UUID uuid) {
    }
}
