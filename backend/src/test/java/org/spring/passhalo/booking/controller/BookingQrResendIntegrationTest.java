package org.spring.passhalo.booking.controller;

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
import org.spring.passhalo.notification.service.BookingConfirmationQueueService;
import org.spring.passhalo.notification.service.EmailService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.mail.booking-retry-initial-delay-ms=3600000")
@AutoConfigureMockMvc
@Transactional
class BookingQrResendIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingConfirmationJobRepository jobRepository;
    @Autowired private BookingConfirmationQueueService queueService;
    @Autowired private BookingMapper bookingMapper;
    @MockitoBean private EmailService emailService;

    @Test
    void ownerQueuesOneConfirmationEvenAfterRepeatedRequests() throws Exception {
        User owner = saveUser(Role.ADMIN);
        Event event = saveEvent(owner);
        Booking booking = saveBooking(event);

        mockMvc.perform(resend(event, booking).with(user(owner)))
                .andExpect(status().isAccepted());
        mockMvc.perform(resend(event, booking).with(user(owner)))
                .andExpect(status().isAccepted());

        assertEquals(1, jobRepository.findAll().stream()
                .filter(job -> job.getBooking().getId().equals(booking.getId())).count());
        assertNull(jobRepository.findByBookingId(booking.getId()).orElseThrow()
                .getUnsubscribeTokenCiphertext());
    }

    @Test
    void existingConfirmationKeepsItsUnsubscribeToken() throws Exception {
        User owner = saveUser(Role.ADMIN);
        Event event = saveEvent(owner);
        Booking booking = saveBooking(event);
        queueService.enqueue(booking, "unsubscribe-token-for-test");
        String originalCiphertext = jobRepository.findByBookingId(booking.getId()).orElseThrow()
                .getUnsubscribeTokenCiphertext();

        mockMvc.perform(resend(event, booking).with(user(owner)))
                .andExpect(status().isAccepted());

        assertEquals(originalCiphertext, jobRepository.findByBookingId(booking.getId()).orElseThrow()
                .getUnsubscribeTokenCiphertext());
    }

    @Test
    void eventAdminStaffAndOtherOwnerCannotQueueConfirmation() throws Exception {
        User owner = saveUser(Role.ADMIN);
        User eventAdmin = saveUser(Role.ADMIN);
        User staff = saveUser(Role.STAFF);
        User otherOwner = saveUser(Role.ADMIN);
        Event event = saveEvent(owner);
        Event otherEvent = saveEvent(otherOwner);
        Booking booking = saveBooking(event);
        saveMembership(event, eventAdmin, EventRole.EVENT_ADMIN);
        saveMembership(event, staff, EventRole.STAFF);

        mockMvc.perform(resend(event, booking).with(user(eventAdmin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(resend(event, booking).with(user(staff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(resend(event, booking).with(user(otherOwner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(resend(otherEvent, booking).with(user(otherOwner)))
                .andExpect(status().isNotFound());

        assertTrue(jobRepository.findByBookingId(booking.getId()).isEmpty());
    }

    @Test
    void inactiveBookingsAndFinishedEventsCannotBeResent() throws Exception {
        User owner = saveUser(Role.ADMIN);
        Event event = saveEvent(owner);
        Booking booking = saveBooking(event);

        booking.setBookingStatus(BookingStatus.CANCELLED);
        mockMvc.perform(resend(event, booking).with(user(owner)))
                .andExpect(status().isConflict());

        booking.setBookingStatus(BookingStatus.VALIDATED);
        mockMvc.perform(resend(event, booking).with(user(owner)))
                .andExpect(status().isConflict());

        booking.setBookingStatus(BookingStatus.CREATED);
        event.setEventState(EventState.FINISHED);
        mockMvc.perform(resend(event, booking).with(user(owner)))
                .andExpect(status().isConflict());

        assertTrue(jobRepository.findByBookingId(booking.getId()).isEmpty());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resend(Event event, Booking booking) {
        return post("/bookings/events/{eventId}/{uuid}/resend-qr", event.getId(), booking.getUuid());
    }

    private User saveUser(Role role) {
        User user = new User();
        user.setName("Test");
        user.setSurname("User");
        user.setEmail("resend-" + UUID.randomUUID() + "@example.test");
        user.setPassword("test-password");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Event saveEvent(User owner) {
        Event event = new Event();
        event.setName("QR resend test");
        event.setDescription("Test event");
        event.setLocation("Test venue");
        event.setImageUrl("https://example.test/event.png");
        event.setStartDateTime(LocalDateTime.now().plusDays(1));
        event.setEndDateTime(LocalDateTime.now().plusDays(2));
        event.setNormalPrice(15.0);
        event.setBookingPrice(10.0);
        event.setTotalTickets(10);
        event.setEventState(EventState.WAITING);
        event.setUser(owner);
        return eventRepository.save(event);
    }

    private Booking saveBooking(Event event) {
        Booking booking = bookingMapper.toEntity(new BookingRequest(
                "Ada", "Lovelace", "guest@example.test", null, event.getId(), false));
        booking.setEvent(event);
        return bookingRepository.save(booking);
    }

    private void saveMembership(Event event, User collaborator, EventRole role) {
        EventMembership membership = new EventMembership();
        membership.setEvent(event);
        membership.setCollaborator(collaborator);
        membership.setCreatedBy(event.getUser());
        membership.setRole(role);
        membership.setMembershipState(MembershipState.ACTIVE);
        membership.setCreatedAt(LocalDateTime.now().minusDays(1));
        membership.setValidFrom(LocalDateTime.now().minusDays(1));
        membershipRepository.save(membership);
    }
}
