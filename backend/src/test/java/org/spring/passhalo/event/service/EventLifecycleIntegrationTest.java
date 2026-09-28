package org.spring.passhalo.event.service;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.EventInvitation;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.InviteState;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.EventInvitationRepository;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
class EventLifecycleIntegrationTest {
    @Autowired private EventLifecycleScheduler scheduler;
    @Autowired private EventService eventService;
    @Autowired private EventRepository eventRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private EventInvitationRepository invitationRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void endedEventIsClosedAndBookingDataIsAnonymized() {
        User owner = saveUser("owner");
        Event event = saveEvent(owner, true);
        Booking booking = new Booking();
        booking.setEvent(event);
        booking.setName("Ada");
        booking.setSurname("Rossi");
        booking.setEmail("ada@example.test");
        booking = bookingRepository.save(booking);
        UUID originalUuid = booking.getUuid();

        EventInvitation invitation = saveInvitation(event, owner);
        scheduler.closeEndedEvents();

        assertEquals(EventState.FINISHED, event.getEventState());
        assertNull(booking.getName());
        assertNull(booking.getEmail());
        assertNotEquals(originalUuid, booking.getUuid());
        assertEquals(InviteState.REVOKED, invitation.getInviteState());
    }

    @Test
    void emptyEventCanBeDeletedWithItsCollaborationsAndInvitations() {
        User owner = saveUser("delete-owner");
        User collaborator = saveUser("delete-collaborator");
        Event event = saveEvent(owner, false);
        saveInvitation(event, owner);

        EventMembership membership = new EventMembership();
        membership.setEvent(event);
        membership.setCollaborator(collaborator);
        membership.setCreatedBy(owner);
        membership.setRole(EventRole.STAFF);
        membership.setMembershipState(MembershipState.ACTIVE);
        membership.setCreatedAt(LocalDateTime.now());
        membership.setValidFrom(LocalDateTime.now());
        membershipRepository.save(membership);

        eventService.deleteEventById(event.getId(), owner);
        eventRepository.flush();

        assertFalse(eventRepository.existsById(event.getId()));
        assertEquals(0, invitationRepository.findAllByEventId(event.getId()).size());
        assertEquals(0, membershipRepository.findAllByEventId(event.getId()).size());
    }

    private User saveUser(String prefix) {
        User user = new User();
        user.setName("Test");
        user.setSurname("User");
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.test");
        user.setPassword("test-password");
        user.setRole(Role.ADMIN);
        return userRepository.save(user);
    }

    private Event saveEvent(User owner, boolean ended) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Europe/Rome"));
        Event event = new Event();
        event.setName("Lifecycle test");
        event.setDescription("Lifecycle test");
        event.setLocation("Test venue");
        event.setImageUrl("https://example.test/event.jpg");
        event.setStartDateTime(ended ? now.minusHours(2) : now.plusHours(1));
        event.setEndDateTime(ended ? now.minusMinutes(1) : now.plusHours(3));
        event.setNormalPrice(15.0);
        event.setBookingPrice(10.0);
        event.setTotalTickets(10);
        event.setEventState(EventState.WAITING);
        event.setUser(owner);
        return eventRepository.save(event);
    }

    private EventInvitation saveInvitation(Event event, User owner) {
        EventInvitation invitation = new EventInvitation();
        invitation.setEvent(event);
        invitation.setRecipientEmail("guest@example.test");
        invitation.setProposedRole(EventRole.STAFF);
        invitation.setInviteState(InviteState.PENDING);
        invitation.setTokenHash(UUID.randomUUID().toString());
        invitation.setCreatedAt(LocalDateTime.now(ZoneId.of("Europe/Rome")));
        invitation.setExpiresAt(LocalDateTime.now(ZoneId.of("Europe/Rome")).plusDays(1));
        invitation.setCreatedBy(owner);
        return invitationRepository.save(invitation);
    }
}
