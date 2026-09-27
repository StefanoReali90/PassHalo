package org.spring.passhalo.user.config;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventMembershipRepository membershipRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void globalStaffWithEventAdminMembershipCanReadAssignedDashboardAndBookings() throws Exception {
        User owner = saveUser("owner-admin@example.test", Role.ADMIN);
        User collaborator = saveUser("event-admin@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        saveMembership(event, collaborator, EventRole.EVENT_ADMIN, MembershipState.ACTIVE,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        mockMvc.perform(get("/events/{id}/dashboard", event.getId()).with(user(collaborator)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/bookings/events/{eventId}", event.getId()).with(user(collaborator)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/events/my-events").with(user(collaborator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(event.getId()))
                .andExpect(jsonPath("$[0].role").value("EVENT_ADMIN"))
                .andExpect(jsonPath("$[0].owner").value(false));
        mockMvc.perform(get("/events/my-events").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(event.getId()))
                .andExpect(jsonPath("$[0].role").value("EVENT_ADMIN"))
                .andExpect(jsonPath("$[0].owner").value(true));
    }

    @Test
    void eventStaffCannotReadDashboardOrPersonalBookings() throws Exception {
        User owner = saveUser("owner-staff@example.test", Role.ADMIN);
        User collaborator = saveUser("scanner@example.test", Role.STAFF);
        User globalAdminWithStaffMembership = saveUser("admin-scanner@example.test", Role.ADMIN);
        Event event = saveEvent(owner);
        saveMembership(event, collaborator, EventRole.STAFF, MembershipState.ACTIVE,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        saveMembership(event, globalAdminWithStaffMembership, EventRole.STAFF, MembershipState.ACTIVE,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        mockMvc.perform(get("/events/{id}/dashboard", event.getId()).with(user(collaborator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/bookings/events/{eventId}", event.getId()).with(user(collaborator)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/{id}/dashboard", event.getId()).with(user(globalAdminWithStaffMembership)))
                .andExpect(status().isForbidden());
    }

    @Test
    void globalAdminCannotReadAnotherOwnersEventWithoutMembership() throws Exception {
        User owner = saveUser("owner-tenant@example.test", Role.ADMIN);
        User otherAdmin = saveUser("other-tenant@example.test", Role.ADMIN);
        Event event = saveEvent(owner);

        mockMvc.perform(get("/events/{id}/dashboard", event.getId()).with(user(otherAdmin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/bookings/events/{eventId}", event.getId()).with(user(otherAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void globalAdminCannotManageAnotherOwnersEventWithoutMembership() throws Exception {
        User owner = saveUser("owner-edit@example.test", Role.ADMIN);
        User otherAdmin = saveUser("other-edit@example.test", Role.ADMIN);
        Event event = saveEvent(owner);
        String update = """
                {"name":"Changed","description":"Other event","location":"Test venue",
                 "start":"%s","end":"%s","imageUrl":"https://example.test/event.jpg",
                 "totalTickets":100,"normalPrice":15,"bookingPrice":10}
                """.formatted(event.getStartDateTime(), event.getEndDateTime());

        mockMvc.perform(get("/events/my-events").with(user(otherAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(put("/events/{id}", event.getId()).with(user(otherAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/events/{id}", event.getId()).with(user(otherAdmin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/events/{id}/walk-in", event.getId()).with(user(otherAdmin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/events/{id}/close", event.getId()).with(user(otherAdmin)))
                .andExpect(status().isForbidden());
        assertEquals("Test event", event.getName());
        assertEquals(EventState.WAITING, event.getEventState());
    }

    @Test
    void revokedAndExpiredMembershipsCannotReadDashboard() throws Exception {
        User owner = saveUser("owner-expiry@example.test", Role.ADMIN);
        User revoked = saveUser("revoked@example.test", Role.STAFF);
        User expired = saveUser("expired@example.test", Role.STAFF);
        User future = saveUser("future@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        saveMembership(event, revoked, EventRole.EVENT_ADMIN, MembershipState.REVOKED,
                LocalDateTime.now().minusDays(2), null);
        saveMembership(event, expired, EventRole.EVENT_ADMIN, MembershipState.ACTIVE,
                LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));
        saveMembership(event, future, EventRole.EVENT_ADMIN, MembershipState.ACTIVE,
                LocalDateTime.now().plusDays(1), null);

        mockMvc.perform(get("/events/{id}/dashboard", event.getId()).with(user(revoked)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/{id}/dashboard", event.getId()).with(user(expired)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/{id}/dashboard", event.getId()).with(user(future)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/my-events").with(user(expired)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/events/my-events").with(user(future)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void revokingMembershipImmediatelyRemovesOperationalAccess() throws Exception {
        User owner = saveUser("revoke-membership-owner@example.test", Role.ADMIN);
        User staff = saveUser("revoke-membership-staff@example.test", Role.STAFF);
        User otherAdmin = saveUser("revoke-membership-other@example.test", Role.ADMIN);
        Event event = saveEvent(owner);
        EventMembership membership = saveMembership(event, staff, EventRole.STAFF, MembershipState.ACTIVE,
                LocalDateTime.now().minusDays(1), null);

        mockMvc.perform(get("/events/{eventId}/memberships", event.getId()).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].collaboratorId").value(staff.getId()))
                .andExpect(jsonPath("$[0].role").value("STAFF"));
        mockMvc.perform(get("/events/{eventId}/memberships", event.getId()).with(user(staff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/events/{id}/walk-in", event.getId()).with(user(staff)))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/events/{eventId}/memberships/{membershipId}/revoke", event.getId(), membership.getId())
                        .with(user(otherAdmin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/events/{eventId}/memberships/{membershipId}/revoke", event.getId(), membership.getId())
                        .with(user(owner)))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/events/{id}/walk-in", event.getId()).with(user(staff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/my-events").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void staffOperationalAccessEndsAtScheduledEndOrEarlyClosure() throws Exception {
        User owner = saveUser("expiry-operational-owner@example.test", Role.ADMIN);
        User staff = saveUser("expiry-operational-staff@example.test", Role.STAFF);
        Event ongoing = saveEvent(owner);
        Event elapsed = saveEvent(owner);
        Event closed = saveEvent(owner);
        elapsed.setStartDateTime(LocalDateTime.now().minusDays(2));
        elapsed.setEndDateTime(LocalDateTime.now().minusDays(1));
        closed.setEventState(EventState.FINISHED);
        eventRepository.save(elapsed);
        eventRepository.save(closed);
        saveMembership(ongoing, staff, EventRole.STAFF, MembershipState.ACTIVE,
                LocalDateTime.now().minusDays(3), null);
        saveMembership(elapsed, staff, EventRole.STAFF, MembershipState.ACTIVE,
                LocalDateTime.now().minusDays(3), null);
        saveMembership(closed, staff, EventRole.STAFF, MembershipState.ACTIVE,
                LocalDateTime.now().minusDays(3), null);

        mockMvc.perform(patch("/events/{id}/walk-in", ongoing.getId()).with(user(staff)))
                .andExpect(status().isNoContent());
        mockMvc.perform(patch("/events/{id}/walk-in", elapsed.getId()).with(user(staff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/events/{id}/walk-in", closed.getId()).with(user(staff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/events/{id}/walk-in", elapsed.getId()).with(user(owner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/my-events").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ongoing.getId()));
        assertEquals(1, ongoing.getWalkInCount());
        assertEquals(0, elapsed.getWalkInCount());
        assertEquals(0, closed.getWalkInCount());
    }

    @Test
    void scannerCannotValidateAnotherEventsPassAndReceivesNoPersonalData() throws Exception {
        User firstOwner = saveUser("first-checkin-owner@example.test", Role.ADMIN);
        User secondOwner = saveUser("second-checkin-owner@example.test", Role.ADMIN);
        User secondEventStaff = saveUser("second-checkin-staff@example.test", Role.STAFF);
        Event firstEvent = saveEvent(firstOwner);
        Event secondEvent = saveEvent(secondOwner);
        saveMembership(secondEvent, secondEventStaff, EventRole.STAFF, MembershipState.ACTIVE,
                LocalDateTime.now().minusDays(1), null);

        Booking booking = new Booking();
        booking.setEvent(firstEvent);
        booking.setName("Mario");
        booking.setSurname("Rossi");
        booking.setEmail("booking@example.test");
        booking = bookingRepository.save(booking);

        mockMvc.perform(patch("/bookings/check-in/{uuid}", booking.getUuid()).with(user(secondEventStaff)))
                .andExpect(status().isForbidden());
        assertEquals(BookingStatus.CREATED, booking.getBookingStatus());

        mockMvc.perform(patch("/bookings/check-in/{uuid}", booking.getUuid()).with(user(firstOwner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventName").value(firstEvent.getName()))
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.surname").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
        assertEquals(BookingStatus.VALIDATED, booking.getBookingStatus());
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setName("Test");
        user.setSurname("User");
        user.setEmail(email);
        user.setPassword("test-password");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Event saveEvent(User owner) {
        Event event = new Event();
        event.setName("Test event");
        event.setDescription("Event access test");
        event.setLocation("Test venue");
        event.setImageUrl("https://example.test/event.jpg");
        event.setStartDateTime(LocalDateTime.now().plusDays(1));
        event.setEndDateTime(LocalDateTime.now().plusDays(2));
        event.setNormalPrice(15.0);
        event.setBookingPrice(10.0);
        event.setTotalTickets(100);
        event.setEventState(EventState.WAITING);
        event.setUser(owner);
        return eventRepository.save(event);
    }

    private EventMembership saveMembership(Event event, User collaborator, EventRole role, MembershipState state,
                                           LocalDateTime validFrom, LocalDateTime validUntil) {
        EventMembership membership = new EventMembership();
        membership.setEvent(event);
        membership.setCollaborator(collaborator);
        membership.setCreatedBy(event.getUser());
        membership.setRole(role);
        membership.setMembershipState(state);
        membership.setCreatedAt(LocalDateTime.now().minusDays(2));
        membership.setValidFrom(validFrom);
        membership.setValidUntil(validUntil);
        return membershipRepository.save(membership);
    }
}
