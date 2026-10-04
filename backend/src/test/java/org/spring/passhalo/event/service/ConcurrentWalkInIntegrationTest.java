package org.spring.passhalo.event.service;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.enums.PaymentMethod;
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
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConcurrentWalkInIntegrationTest {
    @Autowired private EventService service;
    @Autowired private EventRepository events;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentPeopleEntriesKeepTotalsAndMethodsConsistent() throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Event fixture = transaction.execute(status -> {
            User owner = new User();
            owner.setName("Concurrent");
            owner.setSurname("Owner");
            owner.setEmail("walk-in-" + UUID.randomUUID() + "@example.test");
            owner.setPassword("test-password");
            owner.setRole(Role.ADMIN);
            users.save(owner);
            Event event = new Event();
            event.setUser(owner);
            event.setName("Concurrent walk-in");
            event.setDescription("Test");
            event.setLocation("Venue");
            event.setImageUrl("https://example.test/image.jpg");
            event.setStartDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).minusHours(1));
            event.setEndDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).plusHours(3));
            event.setNormalPrice(15.0);
            event.setBookingPrice(10.0);
            event.setTotalTickets(100);
            event.setEventState(EventState.IN_PROGRESS);
            return events.saveAndFlush(event);
        });
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<?>>();
            for (int person = 0; person < 20; person++) {
                PaymentMethod method = person % 2 == 0 ? PaymentMethod.CASH : PaymentMethod.CARD;
                results.add(executor.submit(() -> {
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                    service.incrementWalkInCount(fixture.getId(), fixture.getUser(), method);
                }));
            }
            start.countDown();
            for (Future<?> result : results) result.get(15, TimeUnit.SECONDS);
            transaction.execute(status -> {
                Event saved = events.findById(fixture.getId()).orElseThrow();
                assertEquals(20, saved.getWalkInCount());
                assertEquals(10, saved.getWalkInCashCount());
                assertEquals(10, saved.getWalkInCardCount());
                return null;
            });
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            transaction.execute(status -> {
                events.deleteById(fixture.getId());
                users.deleteById(fixture.getUser().getId());
                return null;
            });
        }
    }
}
