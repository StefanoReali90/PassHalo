package org.spring.passhalo.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.exception.AccessDeniedException;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.dto.JoinCodeResponse;
import org.spring.passhalo.user.dto.JoinRequestResponse;
import org.spring.passhalo.user.entity.EventJoinCode;
import org.spring.passhalo.user.entity.EventJoinRequest;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.JoinRequestState;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.exception.InvalidJoinCodeException;
import org.spring.passhalo.user.exception.JoinRequestConflictException;
import org.spring.passhalo.user.repository.EventJoinCodeRepository;
import org.spring.passhalo.user.repository.EventJoinRequestRepository;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventJoinServiceTest {
    private static final String CODE = "1234-5678-9ABC-DEFG";

    @Mock private EventRepository eventRepository;
    @Mock private EventJoinCodeRepository joinCodeRepository;
    @Mock private EventJoinRequestRepository joinRequestRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @InjectMocks private EventJoinService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "timeZone", "Europe/Rome");
    }

    @Test
    void onlyOwnerCanGenerateCodeWithoutChangingExistingCode() {
        Event event = event();
        when(eventRepository.findDistinctById(event.getId())).thenReturn(Optional.of(event));

        assertThrows(AccessDeniedException.class, () -> service.generateCode(event.getId(), requester()));

        verifyNoInteractions(joinCodeRepository);
    }

    @Test
    void generatedCodeIsStoredOnlyAsHashAndRotationRevokesPreviousCode() throws Exception {
        Event event = event();
        EventJoinCode previous = activeCode(event);
        when(eventRepository.findDistinctById(event.getId())).thenReturn(Optional.of(event));
        when(joinCodeRepository.findAllByEventIdAndRevokedAtIsNull(event.getId()))
                .thenReturn(List.of(previous));

        JoinCodeResponse response = service.generateCode(event.getId(), event.getUser());

        ArgumentCaptor<EventJoinCode> saved = ArgumentCaptor.forClass(EventJoinCode.class);
        verify(joinCodeRepository).save(saved.capture());
        assertNotNull(previous.getRevokedAt());
        assertTrue(response.code().matches("[1-9A-HJ-NP-Z]{4}(-[1-9A-HJ-NP-Z]{4}){3}"));
        assertEquals(event.getEndDateTime(), response.expiresAt());
        String normalized = response.code().replace("-", "");
        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        assertEquals(expectedHash, saved.getValue().getCodeHash());
        assertNotEquals(normalized, saved.getValue().getCodeHash());
        assertEquals(event, saved.getValue().getEvent());
    }

    @Test
    void codeForDistantEventExpiresAfterSevenDays() {
        Event event = event();
        event.setEndDateTime(now().plusDays(20));
        when(eventRepository.findDistinctById(event.getId())).thenReturn(Optional.of(event));

        JoinCodeResponse response = service.generateCode(event.getId(), event.getUser());

        ArgumentCaptor<EventJoinCode> saved = ArgumentCaptor.forClass(EventJoinCode.class);
        verify(joinCodeRepository).save(saved.capture());
        assertEquals(saved.getValue().getCreatedAt().plusDays(7), response.expiresAt());
    }

    @Test
    void malformedCodeNeverReachesPersistence() {
        User staff = requester();

        assertThrows(InvalidJoinCodeException.class, () -> service.submitRequest("123", staff));
        assertThrows(InvalidJoinCodeException.class, () -> service.submitRequest("OOOO-OOOO-OOOO-OOOO", staff));
        assertThrows(InvalidJoinCodeException.class, () -> service.submitRequest(null, staff));

        verifyNoInteractions(joinCodeRepository, joinRequestRepository, membershipRepository);
    }

    @Test
    void revokedOrExpiredCodeCannotCreateRequest() {
        Event event = event();
        EventJoinCode code = activeCode(event);
        code.setRevokedAt(now().minusMinutes(1));
        when(joinCodeRepository.findByCodeHash(anyString())).thenReturn(Optional.of(code));
        assertThrows(InvalidJoinCodeException.class, () -> service.submitRequest(CODE, requester()));

        code.setRevokedAt(null);
        code.setExpiresAt(now().minusMinutes(1));
        assertThrows(InvalidJoinCodeException.class, () -> service.submitRequest(CODE, requester()));

        verifyNoInteractions(joinRequestRepository, membershipRepository);
    }

    @Test
    void validCodeCannotRequestAccessAfterEventEnds() {
        Event event = event();
        when(joinCodeRepository.findByCodeHash(anyString())).thenReturn(Optional.of(activeCode(event)));

        event.setEventState(EventState.FINISHED);
        assertThrows(InvalidJoinCodeException.class, () -> service.submitRequest(CODE, requester()));
        event.setEventState(EventState.WAITING);
        event.setEndDateTime(now().minusMinutes(1));
        assertThrows(InvalidJoinCodeException.class, () -> service.submitRequest(CODE, requester()));

        verifyNoInteractions(joinRequestRepository, membershipRepository);
    }

    @Test
    void submittingCodeCreatesPendingRequestWithoutMembershipAndNormalizesInput() throws Exception {
        Event event = event();
        User staff = requester();
        when(joinCodeRepository.findByCodeHash(anyString())).thenReturn(Optional.of(activeCode(event)));
        when(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), staff.getId()))
                .thenReturn(Optional.empty());
        when(joinRequestRepository.save(any(EventJoinRequest.class))).thenAnswer(invocation -> {
            EventJoinRequest request = invocation.getArgument(0);
            request.setId(50L);
            return request;
        });

        JoinRequestResponse response = service.submitRequest(" 1234-5678-9abc-defg ", staff);

        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("123456789ABCDEFG".getBytes(StandardCharsets.UTF_8)));
        verify(joinCodeRepository).findByCodeHash(expectedHash);
        assertEquals(JoinRequestState.PENDING, response.state());
        assertEquals(event.getId(), response.eventId());
        assertEquals(staff.getId(), response.requesterId());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void ownerOrExistingActiveMemberCannotSubmitRequest() {
        Event event = event();
        EventJoinCode code = activeCode(event);
        when(joinCodeRepository.findByCodeHash(anyString())).thenReturn(Optional.of(code));

        assertThrows(JoinRequestConflictException.class,
                () -> service.submitRequest(CODE, event.getUser()));

        User staff = requester();
        EventMembership membership = new EventMembership();
        membership.setMembershipState(MembershipState.ACTIVE);
        when(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), staff.getId()))
                .thenReturn(Optional.of(membership));
        assertThrows(JoinRequestConflictException.class,
                () -> service.submitRequest(CODE, staff));

        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void duplicatePendingRequestDoesNotCreateAnotherOne() {
        Event event = event();
        User staff = requester();
        when(joinCodeRepository.findByCodeHash(anyString())).thenReturn(Optional.of(activeCode(event)));
        when(joinRequestRepository.findFirstByEventIdAndRequesterIdAndState(
                event.getId(), staff.getId(), JoinRequestState.PENDING))
                .thenReturn(Optional.of(pendingRequest(event, staff)));

        assertThrows(JoinRequestConflictException.class, () -> service.submitRequest(CODE, staff));

        verify(joinRequestRepository, never()).save(any());
    }

    @Test
    void approvalCreatesStaffMembershipOnlyForPendingRequest() {
        Event event = event();
        User staff = requester();
        EventJoinRequest request = pendingRequest(event, staff);
        when(eventRepository.findDistinctById(event.getId())).thenReturn(Optional.of(event));
        when(joinRequestRepository.findByIdAndEventId(request.getId(), event.getId()))
                .thenReturn(Optional.of(request));

        JoinRequestResponse response = service.approveRequest(event.getId(), request.getId(), event.getUser());

        ArgumentCaptor<EventMembership> saved = ArgumentCaptor.forClass(EventMembership.class);
        verify(membershipRepository).save(saved.capture());
        EventMembership membership = saved.getValue();
        assertEquals(EventRole.STAFF, membership.getRole());
        assertEquals(MembershipState.ACTIVE, membership.getMembershipState());
        assertEquals(event, membership.getEvent());
        assertEquals(staff, membership.getCollaborator());
        assertEquals(event.getUser(), membership.getCreatedBy());
        assertNotNull(membership.getValidFrom());
        assertNull(membership.getValidUntil());
        assertEquals(JoinRequestState.APPROVED, response.state());
        assertEquals(event.getUser(), request.getDecidedBy());
    }

    @Test
    void approvalReactivatesRevokedMembershipInsteadOfCreatingDuplicate() {
        Event event = event();
        User staff = requester();
        EventJoinRequest request = pendingRequest(event, staff);
        EventMembership membership = new EventMembership();
        membership.setId(80L);
        membership.setMembershipState(MembershipState.REVOKED);
        membership.setRole(EventRole.EVENT_ADMIN);
        membership.setRevokedAt(now().minusDays(1));
        membership.setValidUntil(now().minusDays(1));
        when(eventRepository.findDistinctById(event.getId())).thenReturn(Optional.of(event));
        when(joinRequestRepository.findByIdAndEventId(request.getId(), event.getId()))
                .thenReturn(Optional.of(request));
        when(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), staff.getId()))
                .thenReturn(Optional.of(membership));

        service.approveRequest(event.getId(), request.getId(), event.getUser());

        verify(membershipRepository).save(membership);
        assertEquals(80L, membership.getId());
        assertEquals(MembershipState.ACTIVE, membership.getMembershipState());
        assertEquals(EventRole.STAFF, membership.getRole());
        assertNull(membership.getRevokedAt());
        assertNull(membership.getValidUntil());
    }

    @Test
    void approvalRenewsMembershipWhoseValidityEndedEarlier() {
        Event event = event();
        User staff = requester();
        EventJoinRequest request = pendingRequest(event, staff);
        EventMembership membership = new EventMembership();
        membership.setId(80L);
        membership.setMembershipState(MembershipState.ACTIVE);
        membership.setValidUntil(now().minusMinutes(1));
        when(eventRepository.findDistinctById(event.getId())).thenReturn(Optional.of(event));
        when(joinRequestRepository.findByIdAndEventId(request.getId(), event.getId()))
                .thenReturn(Optional.of(request));
        when(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), staff.getId()))
                .thenReturn(Optional.of(membership));

        service.approveRequest(event.getId(), request.getId(), event.getUser());

        verify(membershipRepository).save(membership);
        assertEquals(MembershipState.ACTIVE, membership.getMembershipState());
        assertNull(membership.getValidUntil());
        assertEquals(JoinRequestState.APPROVED, request.getState());
    }

    @Test
    void approvalOfActiveMembershipIsRejectedWithoutDecidingRequest() {
        Event event = event();
        User staff = requester();
        EventJoinRequest request = pendingRequest(event, staff);
        EventMembership membership = new EventMembership();
        membership.setId(80L);
        membership.setMembershipState(MembershipState.ACTIVE);
        when(eventRepository.findDistinctById(event.getId())).thenReturn(Optional.of(event));
        when(joinRequestRepository.findByIdAndEventId(request.getId(), event.getId()))
                .thenReturn(Optional.of(request));
        when(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), staff.getId()))
                .thenReturn(Optional.of(membership));

        assertThrows(JoinRequestConflictException.class,
                () -> service.approveRequest(event.getId(), request.getId(), event.getUser()));

        assertEquals(JoinRequestState.PENDING, request.getState());
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void otherOwnerAndFinishedEventCannotApproveRequest() {
        Event event = event();
        when(eventRepository.findDistinctById(event.getId())).thenReturn(Optional.of(event));

        assertThrows(AccessDeniedException.class,
                () -> service.approveRequest(event.getId(), 70L, requester()));
        event.setEventState(EventState.FINISHED);
        assertThrows(JoinRequestConflictException.class,
                () -> service.approveRequest(event.getId(), 70L, event.getUser()));

        verifyNoInteractions(joinRequestRepository, membershipRepository);
    }

    @Test
    void rejectionRecordsDecisionWithoutCreatingMembership() {
        Event event = event();
        EventJoinRequest request = pendingRequest(event, requester());
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(joinRequestRepository.findByIdAndEventId(request.getId(), event.getId()))
                .thenReturn(Optional.of(request));

        JoinRequestResponse response = service.rejectRequest(event.getId(), request.getId(), event.getUser());

        assertEquals(JoinRequestState.REJECTED, response.state());
        assertNotNull(request.getDecidedAt());
        assertEquals(event.getUser(), request.getDecidedBy());
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void closingEventExpiresPendingRequestsAndRevokesCodes() {
        Event event = event();
        EventJoinRequest pending = pendingRequest(event, requester());
        EventJoinCode code = activeCode(event);
        when(joinRequestRepository.findAllByEventIdAndStateOrderByCreatedAtAsc(event.getId(), JoinRequestState.PENDING))
                .thenReturn(List.of(pending));
        when(joinCodeRepository.findAllByEventIdAndRevokedAtIsNull(event.getId()))
                .thenReturn(List.of(code));

        service.expireRequestsForEvent(event.getId());

        assertEquals(JoinRequestState.EXPIRED, pending.getState());
        assertNotNull(pending.getDecidedAt());
        assertNotNull(code.getRevokedAt());
        assertFalse(code.getRevokedAt().isBefore(pending.getDecidedAt()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Europe/Rome"));
    }

    private Event event() {
        User owner = new User();
        owner.setId(1L);
        owner.setEmail("owner@example.test");
        Event event = new Event();
        event.setId(10L);
        event.setName("Test event");
        event.setUser(owner);
        event.setEventState(EventState.WAITING);
        event.setEndDateTime(now().plusDays(2));
        return event;
    }

    private User requester() {
        User staff = new User();
        staff.setId(2L);
        staff.setName("Ada");
        staff.setSurname("Rossi");
        staff.setEmail("ada@example.test");
        return staff;
    }

    private EventJoinCode activeCode(Event event) {
        EventJoinCode code = new EventJoinCode();
        code.setEvent(event);
        code.setExpiresAt(now().plusDays(1));
        return code;
    }

    private EventJoinRequest pendingRequest(Event event, User staff) {
        EventJoinRequest request = new EventJoinRequest();
        request.setId(70L);
        request.setEvent(event);
        request.setRequester(staff);
        request.setState(JoinRequestState.PENDING);
        request.setCreatedAt(now().minusMinutes(5));
        return request;
    }
}
