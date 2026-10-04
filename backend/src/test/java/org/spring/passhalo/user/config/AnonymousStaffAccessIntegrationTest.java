package org.spring.passhalo.user.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.StaffAccessCodeRepository;
import org.spring.passhalo.user.repository.StaffAccessRequestRepository;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnonymousStaffAccessIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private StaffAccessCodeRepository codeRepository;
    @Autowired private StaffAccessRequestRepository requestRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ownerApprovalGrantsOnlyOneEventWithoutCreatingStaffAccount() throws Exception {
        User owner = saveUser("anonymous-owner@example.test");
        User stranger = saveUser("anonymous-stranger@example.test");
        Event event = saveEvent(owner, "Evento A");
        Event otherEvent = saveEvent(stranger, "Evento B");
        Booking ownBooking = saveBooking(event);
        Booking otherBooking = saveBooking(otherEvent);
        long usersBefore = userRepository.count();

        mockMvc.perform(staffPost("/events/{id}/staff-code", event.getId()).with(user(stranger)))
                .andExpect(status().isForbidden());
        String code = generatedCode(event, owner);
        assertEquals(64, codeRepository.findAllByEventIdAndRevokedAtIsNull(event.getId()).getFirst().getCodeHash().length());
        assertNotEquals(code.replace("-", ""),
                codeRepository.findAllByEventIdAndRevokedAtIsNull(event.getId()).getFirst().getCodeHash());

        mockMvc.perform(get("/staff-access/status")).andExpect(status().isUnauthorized());
        MvcResult submitted = request(code);
        Cookie cookie = submitted.getResponse().getCookie("staff_access");
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.getSecure());
        assertEquals(usersBefore, userRepository.count());
        assertEquals("PENDING", json(submitted).get("state").asText());
        mockMvc.perform(staffPost("/staff-access/check-in").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(ownBooking)))
                .andExpect(status().isForbidden());
        mockMvc.perform(staffPost("/staff-access/walk-ins").cookie(cookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(staffPost("/staff-access/walk-ins/decrement").cookie(cookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/{id}/staff-requests", event.getId()).with(user(stranger)))
                .andExpect(status().isForbidden());
        MvcResult pending = mockMvc.perform(get("/events/{id}/staff-requests", event.getId()).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].state").value("PENDING"))
                .andExpect(jsonPath("$[0].email").doesNotExist()).andReturn();
        long requestId = json(pending).get(0).get("id").asLong();

        mockMvc.perform(staffPatch("/events/{id}/staff-requests/{requestId}/approve", event.getId(), requestId)
                        .with(user(stranger))).andExpect(status().isForbidden());
        mockMvc.perform(staffPatch("/events/{id}/staff-requests/{requestId}/approve", event.getId(), requestId)
                        .with(user(owner))).andExpect(status().isNoContent());
        requestRepository.flush();
        mockMvc.perform(get("/staff-access/status").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("APPROVED"));
        mockMvc.perform(post("/staff-access/walk-ins").cookie(cookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(staffPost("/staff-access/check-in").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(otherBooking)))
                .andExpect(status().isNotFound());
        assertEquals(BookingStatus.CREATED, bookingRepository.findById(otherBooking.getId()).orElseThrow().getBookingStatus());
        mockMvc.perform(staffPost("/staff-access/check-in").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(ownBooking)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eventName").value("Evento A"))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(staffPost("/staff-access/check-in").cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON).content(checkInBody(ownBooking)))
                .andExpect(status().isConflict());
        mockMvc.perform(staffPost("/staff-access/walk-ins").cookie(cookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}")).andExpect(status().isNoContent());
        assertEquals(1, eventRepository.findById(event.getId()).orElseThrow().getWalkInCount());
        assertEquals(1, eventRepository.findById(event.getId()).orElseThrow().getWalkInCashCount());
        assertEquals(org.spring.passhalo.booking.enums.PaymentMethod.CARD, ownBooking.getPaymentMethod());
        assertEquals(0, eventRepository.findById(otherEvent.getId()).orElseThrow().getWalkInCount());
        mockMvc.perform(staffPost("/staff-access/walk-ins/decrement").cookie(cookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isNoContent());
        assertEquals(0, eventRepository.findById(event.getId()).orElseThrow().getWalkInCount());
        mockMvc.perform(get("/events/{id}/dashboard", event.getId()).cookie(cookie))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/bookings/{uuid}", ownBooking.getUuid()).cookie(cookie))
                .andExpect(status().isForbidden());
        MvcResult logout = mockMvc.perform(staffPost("/staff-access/logout").cookie(cookie))
                .andExpect(status().isNoContent()).andReturn();
        assertEquals(0, logout.getResponse().getCookie("staff_access").getMaxAge());
        mockMvc.perform(staffPost("/staff-access/walk-ins").cookie(cookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectionRotationAndClosureImmediatelyRemoveAccess() throws Exception {
        User owner = saveUser("anonymous-rotation-owner@example.test");
        Event event = saveEvent(owner, "Evento rotazione");
        String firstCode = generatedCode(event, owner);
        Cookie rejectedCookie = request(firstCode).getResponse().getCookie("staff_access");
        long rejectedId = requestRepository.findAllByEventIdAndState(
                event.getId(), org.spring.passhalo.user.enums.StaffAccessState.PENDING).getFirst().getId();
        mockMvc.perform(staffPatch("/events/{id}/staff-requests/{requestId}/reject", event.getId(), rejectedId)
                        .with(user(owner))).andExpect(status().isNoContent());
        mockMvc.perform(get("/staff-access/status").cookie(rejectedCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("REJECTED"));
        mockMvc.perform(staffPost("/staff-access/walk-ins").cookie(rejectedCookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());

        Cookie approvedCookie = request(firstCode).getResponse().getCookie("staff_access");
        long approvedId = requestRepository.findAllByEventIdAndState(
                event.getId(), org.spring.passhalo.user.enums.StaffAccessState.PENDING).getFirst().getId();
        mockMvc.perform(staffPatch("/events/{id}/staff-requests/{requestId}/approve", event.getId(), approvedId)
                        .with(user(owner))).andExpect(status().isNoContent());
        requestRepository.flush();
        mockMvc.perform(staffPost("/staff-access/walk-ins").cookie(approvedCookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isNoContent());

        String secondCode = generatedCode(event, owner);
        assertNotEquals(firstCode, secondCode);
        requestRepository.flush();
        mockMvc.perform(get("/staff-access/status").cookie(approvedCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("EXPIRED"));
        mockMvc.perform(staffPost("/staff-access/walk-ins").cookie(approvedCookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(staffPost("/staff-access/requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + firstCode + "\"}"))
                .andExpect(status().isBadRequest());

        Cookie lastCookie = request(secondCode).getResponse().getCookie("staff_access");
        long lastId = requestRepository.findAllByEventIdAndState(
                event.getId(), org.spring.passhalo.user.enums.StaffAccessState.PENDING).getFirst().getId();
        mockMvc.perform(staffPatch("/events/{id}/staff-requests/{requestId}/approve", event.getId(), lastId)
                        .with(user(owner))).andExpect(status().isNoContent());
        requestRepository.flush();
        mockMvc.perform(patch("/events/{id}/close", event.getId()).with(user(owner)))
                .andExpect(status().isNoContent());
        requestRepository.flush();
        mockMvc.perform(get("/staff-access/status").cookie(lastCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("EXPIRED"));
        mockMvc.perform(staffPost("/staff-access/walk-ins").cookie(lastCookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
        assertEquals(EventState.FINISHED, eventRepository.findById(event.getId()).orElseThrow().getEventState());
    }

    @Test
    void endedEventAndCrossSiteRequestCannotUseStaffSession() throws Exception {
        User owner = saveUser("anonymous-ended-owner@example.test");
        Event event = saveEvent(owner, "Evento concluso");
        String code = generatedCode(event, owner);
        mockMvc.perform(staffPost("/staff-access/requests").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isForbidden());
        Cookie cookie = request(code).getResponse().getCookie("staff_access");
        event.setEndDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).minusSeconds(1));
        eventRepository.save(event);
        mockMvc.perform(get("/staff-access/status").cookie(cookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("EXPIRED"));
        mockMvc.perform(staffPost("/staff-access/walk-ins").cookie(cookie).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(staffPost("/events/{id}/staff-code", event.getId()).with(user(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void deletingEventRemovesItsAnonymousCodesAndSessions() throws Exception {
        User owner = saveUser("anonymous-delete-owner@example.test");
        Event event = saveEvent(owner, "Evento da eliminare");
        String code = generatedCode(event, owner);
        request(code);

        mockMvc.perform(delete("/events/{id}", event.getId()).with(user(owner)))
                .andExpect(status().isNoContent());
        eventRepository.flush();
        assertTrue(codeRepository.findAllByEventId(event.getId()).isEmpty());
        assertTrue(requestRepository.findAllByEventId(event.getId()).isEmpty());
        assertTrue(eventRepository.findById(event.getId()).isEmpty());
    }

    private String generatedCode(Event event, User owner) throws Exception {
        MvcResult generated = mockMvc.perform(staffPost("/events/{id}/staff-code", event.getId()).with(user(owner)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.expiresAt").exists()).andReturn();
        return json(generated).get("code").asText();
    }

    private MockHttpServletRequestBuilder staffPost(String path, Object... variables) {
        return post(path, variables).header("X-Staff-Action", "1");
    }

    private MockHttpServletRequestBuilder staffPatch(String path, Object... variables) {
        return patch(path, variables).header("X-Staff-Action", "1");
    }

    private MvcResult request(String code) throws Exception {
        return mockMvc.perform(staffPost("/staff-access/requests")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated()).andReturn();
    }

    private String checkInBody(Booking booking) {
        return "{\"uuid\":\"" + booking.getUuid() + "\",\"paymentMethod\":\"CARD\"}";
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private User saveUser(String email) {
        User user = new User();
        user.setName("Test");
        user.setSurname("Owner");
        user.setEmail(email);
        user.setPassword("password");
        user.setRole(Role.ADMIN);
        return userRepository.save(user);
    }

    private Event saveEvent(User owner, String name) {
        Event event = new Event();
        event.setName(name);
        event.setDescription("Anonymous staff test");
        event.setLocation("Test venue");
        event.setImageUrl("https://example.test/event.jpg");
        event.setStartDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).minusHours(1));
        event.setEndDateTime(LocalDateTime.now(ZoneId.of("Europe/Rome")).plusDays(2));
        event.setNormalPrice(15.0);
        event.setBookingPrice(10.0);
        event.setTotalTickets(100);
        event.setEventState(EventState.IN_PROGRESS);
        event.setUser(owner);
        return eventRepository.save(event);
    }

    private Booking saveBooking(Event event) {
        Booking booking = new Booking();
        booking.setEvent(event);
        booking.setName("Guest");
        booking.setSurname("Private");
        booking.setEmail("guest-" + event.getId() + "@example.test");
        return bookingRepository.saveAndFlush(booking);
    }
}
