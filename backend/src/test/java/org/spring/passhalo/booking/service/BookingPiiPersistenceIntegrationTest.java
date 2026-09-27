package org.spring.passhalo.booking.service;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.dto.BookingRequest;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.mapper.BookingMapper;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.marketing.service.MarketingService;
import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.spring.passhalo.marketing.service.MarketingRetentionService;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingPiiPersistenceIntegrationTest {
    @Autowired private BookingMapper bookingMapper;
    @Autowired private PiiCryptoService cryptoService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MarketingService marketingService;
    @Autowired private MarketingRepository marketingRepository;
    @Autowired private MarketingRetentionService marketingRetentionService;
    @Autowired private MockMvc mockMvc;

    @Test
    void newBookingPersistsNoPlaintextAndCanStillBeReadByAuthorizedApplicationCode() {
        Event event = saveEvent();
        Booking booking = bookingMapper.toEntity(new BookingRequest(
                "Ada", "Lovelace", "ada@example.test", "123456", event.getId(), false));
        booking.setEvent(event);
        Booking saved = bookingRepository.saveAndFlush(booking);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, surname, email, phone, name_ciphertext, surname_ciphertext, email_ciphertext, " +
                        "phone_ciphertext, email_lookup_hash, consent_at FROM booking WHERE id = ?", saved.getId());

        assertNull(row.get("NAME"));
        assertNull(row.get("SURNAME"));
        assertNull(row.get("EMAIL"));
        assertNull(row.get("PHONE"));
        assertNotNull(row.get("NAME_CIPHERTEXT"));
        assertNotNull(row.get("SURNAME_CIPHERTEXT"));
        assertNotNull(row.get("EMAIL_CIPHERTEXT"));
        assertNotNull(row.get("PHONE_CIPHERTEXT"));
        assertNotEquals("ada@example.test", row.get("EMAIL_CIPHERTEXT"));
        assertEquals(cryptoService.emailLookupHash("ada@example.test"), row.get("EMAIL_LOOKUP_HASH"));
        assertNull(row.get("CONSENT_AT"));

        var response = bookingMapper.toResponse(saved, "qr");
        assertEquals("Ada", response.name());
        assertEquals("Lovelace", response.surname());
        assertEquals("ada@example.test", response.email());
        assertEquals("123456", response.phone());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void marketingConsentPersistsNoPlaintextAndKeepsOnlyAnEmailLookupHash() throws Exception {
        String unsubscribeToken = marketingService.registerConsent("Ada", "Lovelace", "ada-marketing@example.test");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, surname, email, name_ciphertext, surname_ciphertext, email_ciphertext, " +
                        "email_lookup_hash FROM marketing_subscriber WHERE email_lookup_hash = ?",
                cryptoService.emailLookupHash("ada-marketing@example.test"));

        assertNull(row.get("NAME"));
        assertNull(row.get("SURNAME"));
        assertNull(row.get("EMAIL"));
        assertNotNull(row.get("NAME_CIPHERTEXT"));
        assertNotNull(row.get("SURNAME_CIPHERTEXT"));
        assertNotNull(row.get("EMAIL_CIPHERTEXT"));
        assertNotEquals("ada-marketing@example.test", row.get("EMAIL_CIPHERTEXT"));

        mockMvc.perform(post("/marketing/unsubscribe")
                        .contentType("application/json")
                        .content("{\"token\":\"" + unsubscribeToken + "\"}"))
                .andExpect(status().isNoContent());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM marketing_subscriber WHERE email_lookup_hash = ?",
                Integer.class, cryptoService.emailLookupHash("ada-marketing@example.test")));
    }

    @Test
    void dailyRetentionRemovesExpiredMarketingRowsAndKeepsCurrentConsent() {
        MarketingSubscriber expired = subscriberWithExpiry(LocalDateTime.now().minusDays(1), "expired-hash");
        MarketingSubscriber current = subscriberWithExpiry(LocalDateTime.now().plusDays(1), "current-hash");
        expired = marketingRepository.saveAndFlush(expired);
        current = marketingRepository.saveAndFlush(current);
        Long expiredId = expired.getId();
        Long currentId = current.getId();

        marketingRetentionService.deleteExpiredSubscribers();

        assertEquals(false, marketingRepository.existsById(expiredId));
        assertEquals(true, marketingRepository.existsById(currentId));
    }

    private MarketingSubscriber subscriberWithExpiry(LocalDateTime expiry, String hash) {
        MarketingSubscriber subscriber = new MarketingSubscriber();
        subscriber.setEmailLookupHash(hash);
        subscriber.setEmailCiphertext("encrypted-email");
        subscriber.setConsentAt(LocalDateTime.now().minusMonths(1));
        subscriber.setExpiresAt(expiry);
        subscriber.setActive(true);
        return subscriber;
    }

    private Event saveEvent() {
        User owner = new User();
        owner.setName("Event");
        owner.setSurname("Owner");
        owner.setEmail("privacy-test-" + System.nanoTime() + "@example.test");
        owner.setPassword("hashed-test-password");
        owner.setRole(Role.ADMIN);
        owner = userRepository.save(owner);

        Event event = new Event();
        event.setName("Privacy integration test");
        event.setStartDateTime(LocalDateTime.now().plusDays(1));
        event.setEndDateTime(LocalDateTime.now().plusDays(1).plusHours(3));
        event.setBookingPrice(10.0);
        event.setTotalTickets(100);
        event.setEventState(EventState.WAITING);
        event.setDescription("Test");
        event.setImageUrl("https://example.test/event.png");
        event.setLocation("Test venue");
        event.setUser(owner);
        return eventRepository.save(event);
    }
}
