package org.spring.passhalo.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.EventJoinCode;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.EventJoinCodeRepository;
import org.spring.passhalo.user.repository.EventJoinRequestRepository;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.spring.passhalo.user.repository.UserRepository;
import org.spring.passhalo.user.service.EventJoinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventJoinFlowIntegrationTest {
    @Autowired private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private UserRepository userRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private EventJoinCodeRepository joinCodeRepository;
    @Autowired private EventJoinRequestRepository joinRequestRepository;
    @Autowired private EventMembershipRepository membershipRepository;
    @Autowired private EventJoinService joinService;

    @Test
    void codeCreatesPendingNotificationAndOnlyOwnerApprovalGrantsStaffAccess() throws Exception {
        User owner = saveUser("join-owner@example.test", Role.ADMIN);
        User otherOwner = saveUser("join-other-owner@example.test", Role.ADMIN);
        User staff = saveUser("join-staff@example.test", Role.STAFF);
        Event event = saveEvent(owner);

        MvcResult generated = mockMvc.perform(post("/events/{eventId}/join-code", event.getId()).with(user(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn();
        String code = json(generated, "code");
        assertEquals(19, code.length());
        EventJoinCode storedCode = joinCodeRepository.findAllByEventIdAndRevokedAtIsNull(event.getId()).getFirst();
        assertNotEquals(code.replace("-", ""), storedCode.getCodeHash());
        assertEquals(64, storedCode.getCodeHash().length());

        mockMvc.perform(post("/join-requests").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PENDING"));
        assertTrue(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), staff.getId()).isEmpty());
        mockMvc.perform(patch("/events/{eventId}/walk-in", event.getId()).with(user(staff)).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/events/{eventId}/join-requests", event.getId()).with(user(staff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/{eventId}/join-requests", event.getId()).with(user(otherOwner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/events/{eventId}/join-requests", event.getId()).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requesterEmail").value(staff.getEmail()))
                .andExpect(jsonPath("$[0].state").value("PENDING"));
        Long requestId = joinRequestRepository.findAllByEventIdAndStateOrderByCreatedAtAsc(
                event.getId(), org.spring.passhalo.user.enums.JoinRequestState.PENDING).getFirst().getId();

        mockMvc.perform(patch("/events/{eventId}/join-requests/{requestId}/approve", event.getId(), requestId)
                        .with(user(otherOwner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/events/{eventId}/join-requests/{requestId}/approve", event.getId(), requestId)
                        .with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("APPROVED"));
        EventMembership membership = membershipRepository.findByEventIdAndCollaboratorId(
                event.getId(), staff.getId()).orElseThrow();
        assertEquals(EventRole.STAFF, membership.getRole());
        assertEquals(MembershipState.ACTIVE, membership.getMembershipState());
        assertNotNull(membership.getValidFrom());
        mockMvc.perform(patch("/events/{eventId}/walk-in", event.getId()).with(user(staff)).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/join-requests/my").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("APPROVED"));
        mockMvc.perform(patch("/events/{eventId}/join-requests/{requestId}/approve", event.getId(), requestId)
                        .with(user(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    void rotatingOrExpiringCodeBlocksNewRequests() throws Exception {
        User owner = saveUser("rotate-owner@example.test", Role.ADMIN);
        User staff = saveUser("rotate-staff@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        String first = generateCode(event, owner);
        String second = generateCode(event, owner);

        mockMvc.perform(post("/join-requests").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + first + "\"}"))
                .andExpect(status().isBadRequest());
        EventJoinCode current = joinCodeRepository.findAllByEventIdAndRevokedAtIsNull(event.getId()).getFirst();
        current.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        joinCodeRepository.save(current);
        mockMvc.perform(post("/join-requests").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + second + "\"}"))
                .andExpect(status().isBadRequest());
        assertTrue(joinRequestRepository.findAllByRequesterIdOrderByCreatedAtDesc(staff.getId()).isEmpty());
    }

    @Test
    void rejectedStaffCanAskAgainAndRevokedMembershipCanBeReactivated() throws Exception {
        User owner = saveUser("rejoin-owner@example.test", Role.ADMIN);
        User staff = saveUser("rejoin-staff@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        String code = generateCode(event, owner);

        submit(code, staff).andExpect(status().isCreated());
        Long firstId = joinRequestRepository.findAllByRequesterIdOrderByCreatedAtDesc(staff.getId()).getFirst().getId();
        submit(code, staff).andExpect(status().isConflict());
        mockMvc.perform(patch("/events/{eventId}/join-requests/{id}/reject", event.getId(), firstId)
                        .with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("REJECTED"));
        assertTrue(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), staff.getId()).isEmpty());

        submit(code, staff).andExpect(status().isCreated());
        Long secondId = joinRequestRepository.findAllByRequesterIdOrderByCreatedAtDesc(staff.getId()).getFirst().getId();
        mockMvc.perform(patch("/events/{eventId}/join-requests/{id}/approve", event.getId(), secondId)
                        .with(user(owner)))
                .andExpect(status().isOk());
        EventMembership membership = membershipRepository.findByEventIdAndCollaboratorId(
                event.getId(), staff.getId()).orElseThrow();
        Long membershipId = membership.getId();
        membership.setMembershipState(MembershipState.REVOKED);
        membership.setRevokedAt(LocalDateTime.now());
        membershipRepository.save(membership);

        submit(code, staff).andExpect(status().isCreated());
        Long thirdId = joinRequestRepository.findAllByRequesterIdOrderByCreatedAtDesc(staff.getId()).getFirst().getId();
        mockMvc.perform(patch("/events/{eventId}/join-requests/{id}/approve", event.getId(), thirdId)
                        .with(user(owner)))
                .andExpect(status().isOk());
        EventMembership reactivated = membershipRepository.findByEventIdAndCollaboratorId(
                event.getId(), staff.getId()).orElseThrow();
        assertEquals(membershipId, reactivated.getId());
        assertEquals(MembershipState.ACTIVE, reactivated.getMembershipState());
    }

    @Test
    void finishedEventCannotIssueCodeAcceptRequestsOrGrantAccess() throws Exception {
        User owner = saveUser("finished-join-owner@example.test", Role.ADMIN);
        User staff = saveUser("finished-join-staff@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        String code = generateCode(event, owner);
        submit(code, staff).andExpect(status().isCreated());
        Long requestId = joinRequestRepository.findAllByRequesterIdOrderByCreatedAtDesc(staff.getId()).getFirst().getId();
        mockMvc.perform(patch("/events/{eventId}/close", event.getId()).with(user(owner)))
                .andExpect(status().isNoContent());
        assertEquals(org.spring.passhalo.user.enums.JoinRequestState.EXPIRED,
                joinRequestRepository.findById(requestId).orElseThrow().getState());

        mockMvc.perform(post("/events/{eventId}/join-code", event.getId()).with(user(owner)))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/events/{eventId}/join-requests/{id}/approve", event.getId(), requestId)
                        .with(user(owner)))
                .andExpect(status().isConflict());
        submit(code, staff).andExpect(status().isBadRequest());
        assertTrue(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), staff.getId()).isEmpty());
    }

    @Test
    void scheduledEndExpiresPendingRequestsWithoutGrantingMembership() throws Exception {
        User owner = saveUser("scheduled-join-owner@example.test", Role.ADMIN);
        User staff = saveUser("scheduled-join-staff@example.test", Role.STAFF);
        Event event = saveEvent(owner);
        String code = generateCode(event, owner);
        submit(code, staff).andExpect(status().isCreated());
        Long requestId = joinRequestRepository.findAllByRequesterIdOrderByCreatedAtDesc(staff.getId()).getFirst().getId();
        event.setStartDateTime(LocalDateTime.now().minusDays(2));
        event.setEndDateTime(LocalDateTime.now().minusDays(1));
        eventRepository.save(event);

        joinService.expireEndedRequests();

        assertEquals(org.spring.passhalo.user.enums.JoinRequestState.EXPIRED,
                joinRequestRepository.findById(requestId).orElseThrow().getState());
        assertTrue(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), staff.getId()).isEmpty());
        mockMvc.perform(get("/events/{eventId}/join-requests", event.getId()).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private String generateCode(Event event, User owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/events/{eventId}/join-code", event.getId()).with(user(owner)))
                .andExpect(status().isCreated()).andReturn();
        return json(result, "code");
    }

    private org.springframework.test.web.servlet.ResultActions submit(String code, User staff) throws Exception {
        return mockMvc.perform(post("/join-requests").with(user(staff))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private String json(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asText();
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
        event.setDescription("Join flow test");
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
}
