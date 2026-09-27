package org.spring.passhalo.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.exception.AccessDeniedException;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthEventServiceTest {
    @Mock private EventRepository eventRepository;
    @Mock private EventMembershipRepository membershipRepository;
    @InjectMocks private AuthEventService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "timezone", "Europe/Rome");
    }

    @Test
    void operationalAccessExpiresAtEventEndEvenForOwner() {
        Event event = event();
        event.setEndDateTime(now().minusSeconds(1));
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThrows(AccessDeniedException.class,
                () -> service.checkStaffAccess(event.getId(), event.getUser().getId()));

        verifyNoInteractions(membershipRepository);
    }

    @Test
    void earlyClosureBlocksScannerEvenBeforeScheduledEnd() {
        Event event = event();
        event.setEventState(EventState.FINISHED);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThrows(AccessDeniedException.class, () -> service.checkStaffAccess(event.getId(), 2L));

        verifyNoInteractions(membershipRepository);
    }

    @Test
    void activeStaffCanOperateOnlyOnAssignedEvent() {
        Event event = event();
        EventMembership membership = membership(EventRole.STAFF);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), 2L))
                .thenReturn(Optional.of(membership));

        assertDoesNotThrow(() -> service.checkStaffAccess(event.getId(), 2L));
        assertThrows(AccessDeniedException.class, () -> service.checkStaffAccess(event.getId(), 3L));
    }

    @Test
    void futureExpiredAndRevokedMembershipsCannotOperate() {
        Event event = event();
        EventMembership membership = membership(EventRole.STAFF);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), 2L))
                .thenReturn(Optional.of(membership));

        membership.setValidFrom(now().plusDays(1));
        assertThrows(AccessDeniedException.class, () -> service.checkStaffAccess(event.getId(), 2L));
        membership.setValidFrom(now().minusDays(1));
        membership.setValidUntil(now().minusSeconds(1));
        assertThrows(AccessDeniedException.class, () -> service.checkStaffAccess(event.getId(), 2L));
        membership.setValidUntil(null);
        membership.setMembershipState(MembershipState.REVOKED);
        assertThrows(AccessDeniedException.class, () -> service.checkStaffAccess(event.getId(), 2L));
    }

    @Test
    void globalAdminRoleDoesNotReplaceEventAdminMembership() {
        Event event = event();
        EventMembership membership = membership(EventRole.STAFF);
        membership.getCollaborator().setRole(Role.ADMIN);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(membershipRepository.findByEventIdAndCollaboratorId(event.getId(), 2L))
                .thenReturn(Optional.of(membership));

        assertThrows(AccessDeniedException.class, () -> service.checkUserAccess(event.getId(), 2L));
        membership.setRole(EventRole.EVENT_ADMIN);
        assertDoesNotThrow(() -> service.checkUserAccess(event.getId(), 2L));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Europe/Rome"));
    }

    private Event event() {
        User owner = new User();
        owner.setId(1L);
        Event event = new Event();
        event.setId(10L);
        event.setUser(owner);
        event.setEventState(EventState.WAITING);
        event.setEndDateTime(now().plusDays(1));
        return event;
    }

    private EventMembership membership(EventRole role) {
        User collaborator = new User();
        collaborator.setId(2L);
        EventMembership membership = new EventMembership();
        membership.setCollaborator(collaborator);
        membership.setRole(role);
        membership.setMembershipState(MembershipState.ACTIVE);
        membership.setValidFrom(now().minusDays(1));
        membership.setValidUntil(now().plusDays(1));
        return membership;
    }
}
