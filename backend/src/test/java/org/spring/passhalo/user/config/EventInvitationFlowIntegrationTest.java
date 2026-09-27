package org.spring.passhalo.user.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.notification.service.EmailService;
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
import org.spring.passhalo.user.service.EventInvitationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventInvitationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventInvitationRepository invitationRepository;

    @Autowired
    private EventMembershipRepository membershipRepository;

    @Autowired
    private EventInvitationService invitationService;

    @MockitoBean
    private EmailService emailService;

    @Test
    void eventAdminCanInviteAndMatchingAccountCanAcceptOnlyOnce() throws Exception {
        User owner = saveUser("invitation-owner@example.test", Role.ADMIN);
        User eventAdmin = saveUser("invitation-admin@example.test", Role.STAFF);
        User invitee = saveUser("invitee@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        saveMembership(event, eventAdmin, EventRole.EVENT_ADMIN);

        mockMvc.perform(post("/events/{eventId}/invitations", event.getId())
                        .with(user(eventAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"INVITEE@example.test\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientEmail").value("invitee@example.test"))
                .andExpect(jsonPath("$.proposedRole").value("STAFF"))
                .andExpect(jsonPath("$.tokenHash").doesNotExist());

        EventInvitation invitation = invitationRepository.findAllByEventIdAndInviteState(event.getId(), InviteState.PENDING).getFirst();
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailConfirmation(any(EventInvitation.class), token.capture());
        assertNotEquals(token.getValue(), invitation.getTokenHash());

        mockMvc.perform(post("/invitations/accept")
                        .with(user(invitee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isNoContent());

        EventMembership membership = membershipRepository.findByEventIdAndCollaboratorId(event.getId(), invitee.getId()).orElseThrow();
        assertEquals(MembershipState.ACTIVE, membership.getMembershipState());
        assertEquals(EventRole.STAFF, membership.getRole());
        assertEquals(InviteState.ACCEPTED, invitation.getInviteState());

        mockMvc.perform(post("/invitations/accept")
                        .with(user(invitee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void staffCannotManageInvitationsAndRevokedTokenCannotBeAccepted() throws Exception {
        User owner = saveUser("revoke-owner@example.test", Role.ADMIN);
        User scanner = saveUser("revoke-scanner@example.test", Role.STAFF);
        User invitee = saveUser("revoke-invitee@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        saveMembership(event, scanner, EventRole.STAFF);

        mockMvc.perform(post("/events/{eventId}/invitations", event.getId())
                        .with(user(scanner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"revoke-invitee@example.test\",\"role\":\"STAFF\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/{eventId}/invitations", event.getId()).with(user(scanner)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/events/{eventId}/invitations", event.getId())
                        .with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"revoke-invitee@example.test\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated());

        EventInvitation invitation = invitationRepository.findAllByEventIdAndInviteState(event.getId(), InviteState.PENDING).getFirst();
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailConfirmation(any(EventInvitation.class), token.capture());

        mockMvc.perform(delete("/events/{eventId}/invitations/{invitationId}", event.getId(), invitation.getId())
                        .with(user(owner)))
                .andExpect(status().isNoContent());
        assertEquals(InviteState.REVOKED, invitation.getInviteState());

        mockMvc.perform(post("/invitations/accept")
                        .with(user(invitee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), invitee.getId()).isEmpty());
    }

    @Test
    void scheduledExpirationChangesPendingState() throws Exception {
        User owner = saveUser("expiry-owner@example.test", Role.ADMIN);
        User invitee = saveUser("expiry-invitee@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        EventInvitation invitation = new EventInvitation();
        invitation.setEvent(event);
        invitation.setRecipientEmail(invitee.getEmail());
        invitation.setProposedRole(EventRole.STAFF);
        invitation.setInviteState(InviteState.PENDING);
        invitation.setTokenHash("expired-invitation-hash");
        invitation.setCreatedAt(LocalDateTime.now().minusDays(8));
        invitation.setExpiresAt(LocalDateTime.now().minusDays(1));
        invitation.setCreatedBy(owner);
        invitationRepository.save(invitation);

        invitationService.expireInvitations();

        assertEquals(InviteState.EXPIRED, invitation.getInviteState());
        mockMvc.perform(get("/events/{eventId}/invitations", event.getId()).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void acceptingReinvitationReactivatesExistingMembership() throws Exception {
        User owner = saveUser("reinvite-owner@example.test", Role.ADMIN);
        User invitee = saveUser("reinvite-user@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        EventMembership membership = saveMembership(event, invitee, EventRole.STAFF);
        membership.setMembershipState(MembershipState.REVOKED);
        membership.setRevokedAt(LocalDateTime.now().minusHours(1));
        membershipRepository.save(membership);
        Long originalMembershipId = membership.getId();

        mockMvc.perform(post("/events/{eventId}/invitations", event.getId())
                        .with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"reinvite-user@example.test\",\"role\":\"EVENT_ADMIN\"}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailConfirmation(any(EventInvitation.class), token.capture());

        mockMvc.perform(post("/invitations/accept")
                        .with(user(invitee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isNoContent());

        EventMembership reactivated = membershipRepository.findByEventIdAndCollaboratorId(event.getId(), invitee.getId()).orElseThrow();
        assertEquals(originalMembershipId, reactivated.getId());
        assertEquals(MembershipState.ACTIVE, reactivated.getMembershipState());
        assertEquals(EventRole.EVENT_ADMIN, reactivated.getRole());
        assertNull(reactivated.getRevokedAt());
    }

    @Test
    void onlyTheAccountWithTheInvitedEmailCanAccept() throws Exception {
        User owner = saveUser("email-owner@example.test", Role.ADMIN);
        User wrongUser = saveUser("wrong-account@example.test", Role.STAFF);
        Event event = saveEvent(owner);

        mockMvc.perform(post("/events/{eventId}/invitations", event.getId())
                        .with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"right-account@example.test\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailConfirmation(any(EventInvitation.class), token.capture());

        mockMvc.perform(post("/invitations/accept")
                        .with(user(wrongUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), wrongUser.getId()).isEmpty());
    }

    @Test
    void expiredTokenCannotCreateMembership() throws Exception {
        User owner = saveUser("token-expiry-owner@example.test", Role.ADMIN);
        User invitee = saveUser("token-expiry-user@example.test", Role.STAFF);
        Event event = saveEvent(owner);

        mockMvc.perform(post("/events/{eventId}/invitations", event.getId())
                        .with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"token-expiry-user@example.test\",\"role\":\"STAFF\"}"))
                .andExpect(status().isCreated());
        EventInvitation invitation = invitationRepository.findAllByEventIdAndInviteState(event.getId(), InviteState.PENDING).getFirst();
        invitation.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        invitationRepository.save(invitation);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailConfirmation(any(EventInvitation.class), token.capture());

        mockMvc.perform(post("/invitations/accept")
                        .with(user(invitee))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token.getValue() + "\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), invitee.getId()).isEmpty());
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
        event.setDescription("Invitation test");
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

    private EventMembership saveMembership(Event event, User collaborator, EventRole role) {
        EventMembership membership = new EventMembership();
        membership.setEvent(event);
        membership.setCollaborator(collaborator);
        membership.setCreatedBy(event.getUser());
        membership.setRole(role);
        membership.setMembershipState(MembershipState.ACTIVE);
        membership.setCreatedAt(LocalDateTime.now().minusDays(1));
        membership.setValidFrom(LocalDateTime.now().minusDays(1));
        return membershipRepository.save(membership);
    }
}
