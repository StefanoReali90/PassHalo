package org.spring.passhalo.event.controller;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.booking.enums.PaymentMethod;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentMethodCountsIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private EventRepository events;
    @Autowired private BookingRepository bookings;

    @Test
    void countsPeopleByMethodAndKeepsHistoricalEntriesUnclassified() throws Exception {
        Event event = event();
        User owner = event.getUser();
        event.setWalkInCount(2); // Historical entries, method unknown.
        Booking historical = booking(event);
        historical.setBookingStatus(BookingStatus.VALIDATED);
        Booking cash = booking(event);
        Booking card = booking(event);
        Booking absent = booking(event);
        Booking cancelled = booking(event);
        cancelled.setBookingStatus(BookingStatus.CANCELLED);

        mvc.perform(payment(patch("/bookings/events/{id}/check-in/{uuid}", event.getId(), cash.getUuid()), owner, "CASH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").doesNotExist());
        mvc.perform(payment(patch("/bookings/check-in/{uuid}", card.getUuid()), owner, "CARD"))
                .andExpect(status().isOk());
        mvc.perform(payment(patch("/bookings/check-in/{uuid}", cash.getUuid()), owner, "CARD"))
                .andExpect(status().isConflict());
        assertEquals(PaymentMethod.CASH, cash.getPaymentMethod());
        assertEquals(PaymentMethod.CARD, card.getPaymentMethod());
        assertNull(absent.getPaymentMethod());
        assertNull(historical.getPaymentMethod());

        mvc.perform(payment(patch("/events/{id}/walk-in", event.getId()), owner, "CASH")).andExpect(status().isNoContent());
        // One card transaction covering four people: register four person entries.
        for (int person = 0; person < 4; person++) {
            mvc.perform(payment(patch("/events/{id}/walk-in", event.getId()), owner, "CARD")).andExpect(status().isNoContent());
        }
        mvc.perform(get("/events/{id}/dashboard", event.getId()).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedInCount").value(3))
                .andExpect(jsonPath("$.checkedInCashCount").value(1))
                .andExpect(jsonPath("$.checkedInCardCount").value(1))
                .andExpect(jsonPath("$.checkedInUnrecordedCount").value(1))
                .andExpect(jsonPath("$.walkInCount").value(7))
                .andExpect(jsonPath("$.walkInCashCount").value(1))
                .andExpect(jsonPath("$.walkInCardCount").value(4))
                .andExpect(jsonPath("$.walkInUnrecordedCount").value(2))
                .andExpect(jsonPath("$.totalAttendees").value(10))
                .andExpect(jsonPath("$.totalRevenue").value(135.0));
    }

    @Test
    void missingOrInvalidMethodDoesNotRegisterAnEntry() throws Exception {
        Event event = event();
        Booking booking = booking(event);
        String[] endpoints = {
                "/bookings/check-in/" + booking.getUuid(),
                "/bookings/events/" + event.getId() + "/check-in/" + booking.getUuid(),
                "/events/" + event.getId() + "/walk-in",
                "/events/" + event.getId() + "/walk-in/decrement"
        };
        for (String endpoint : endpoints) {
            mvc.perform(patch(endpoint).with(user(event.getUser()))).andExpect(status().isBadRequest());
            for (String body : new String[]{"{}", "{\"paymentMethod\":null}", "{\"paymentMethod\":\"OTHER\"}"}) {
                mvc.perform(patch(endpoint).with(user(event.getUser())).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
        }
        mvc.perform(post("/staff-access/check-in").header("X-Staff-Action", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"uuid\":\"" + booking.getUuid() + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/staff-access/walk-ins").header("X-Staff-Action", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(BookingStatus.CREATED, booking.getBookingStatus());
        assertNull(booking.getCheckInDateTime());
        assertNull(booking.getPaymentMethod());
        assertEquals(0, event.getWalkInCount());
    }

    @Test
    void correctionChangesOnlyTheChosenMethodAndNeverConsumesHistoricalEntries() throws Exception {
        Event event = event();
        event.setWalkInCount(2);
        User owner = event.getUser();
        mvc.perform(payment(patch("/events/{id}/walk-in", event.getId()), owner, "CASH")).andExpect(status().isNoContent());
        mvc.perform(payment(patch("/events/{id}/walk-in/decrement", event.getId()), owner, "CARD")).andExpect(status().isConflict());
        assertEquals(3, event.getWalkInCount());
        assertEquals(1, event.getWalkInCashCount());
        assertEquals(0, event.getWalkInCardCount());
        mvc.perform(payment(patch("/events/{id}/walk-in/decrement", event.getId()), owner, "CASH")).andExpect(status().isNoContent());
        mvc.perform(payment(patch("/events/{id}/walk-in/decrement", event.getId()), owner, "CASH")).andExpect(status().isConflict());
        assertEquals(2, event.getWalkInCount());
        assertEquals(0, event.getWalkInCashCount());
    }

    @Test
    void closureAndAnonymizationPreservePaymentCounts() throws Exception {
        Event event = event();
        Booking booking = booking(event);
        User owner = event.getUser();
        mvc.perform(payment(patch("/bookings/check-in/{uuid}", booking.getUuid()), owner, "CARD")).andExpect(status().isOk());
        mvc.perform(payment(patch("/events/{id}/walk-in", event.getId()), owner, "CASH")).andExpect(status().isNoContent());
        mvc.perform(patch("/events/{id}/close", event.getId()).with(user(owner))).andExpect(status().isNoContent());
        assertNull(booking.getName());
        assertNull(booking.getEmail());
        assertEquals(PaymentMethod.CARD, booking.getPaymentMethod());
        mvc.perform(get("/events/{id}/dashboard", event.getId()).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedInCardCount").value(1))
                .andExpect(jsonPath("$.walkInCashCount").value(1));
        mvc.perform(payment(patch("/events/{id}/walk-in", event.getId()), owner, "CARD")).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder payment(MockHttpServletRequestBuilder request, User owner, String method) {
        return request.with(user(owner)).contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"" + method + "\"}");
    }

    private Event event() {
        User owner = new User();
        owner.setName("Payment");
        owner.setSurname("Owner");
        owner.setEmail("payments-" + UUID.randomUUID() + "@example.test");
        owner.setPassword("test-password");
        owner.setRole(Role.ADMIN);
        users.save(owner);
        Event event = new Event();
        event.setUser(owner);
        event.setName("Payment counts");
        event.setDescription("Test");
        event.setLocation("Venue");
        event.setImageUrl("https://example.test/image.jpg");
        event.setStartDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).minusHours(1));
        event.setEndDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).plusHours(3));
        event.setNormalPrice(15.0);
        event.setBookingPrice(10.0);
        event.setTotalTickets(100);
        event.setEventState(EventState.IN_PROGRESS);
        return events.save(event);
    }

    private Booking booking(Event event) {
        Booking booking = new Booking();
        booking.setEvent(event);
        booking.setName("Guest");
        booking.setSurname("Test");
        booking.setEmail(UUID.randomUUID() + "@example.test");
        return bookings.save(booking);
    }
}
