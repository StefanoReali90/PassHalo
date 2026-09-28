package org.spring.passhalo.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.event.exception.AccessDeniedException;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.exception.InvalidMembershipException;
import org.spring.passhalo.user.mapper.EventMembershipMapper;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class EventMembershipServiceTest {
    @Mock private EventMembershipMapper membershipMapper;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private AuthEventService authEventService;
    @InjectMocks private EventMembershipService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "timeZone", "Europe/Rome");
    }

    @Test
    void revocationImmediatelyMarksActiveMembershipAndRecordsTime() {
        User owner = owner();
        EventMembership membership = new EventMembership();
        membership.setMembershipState(MembershipState.ACTIVE);
        when(membershipRepository.findByIdAndEventId(7L, 10L)).thenReturn(Optional.of(membership));

        service.revokeMembership(10L, 7L, owner);

        verify(authEventService).checkUserAccess(10L, owner.getId());
        verify(membershipRepository).save(membership);
        assertEquals(MembershipState.REVOKED, membership.getMembershipState());
        assertNotNull(membership.getRevokedAt());
    }

    @Test
    void revokedMembershipCannotBeRevokedAgain() {
        EventMembership membership = new EventMembership();
        membership.setMembershipState(MembershipState.REVOKED);
        when(membershipRepository.findByIdAndEventId(7L, 10L)).thenReturn(Optional.of(membership));

        assertThrows(InvalidMembershipException.class,
                () -> service.revokeMembership(10L, 7L, owner()));

        verify(membershipRepository, never()).save(any());
    }

    @Test
    void unauthorizedUserCannotLookUpOrRevokeMembership() {
        User outsider = owner();
        doThrow(new AccessDeniedException("denied"))
                .when(authEventService).checkUserAccess(10L, outsider.getId());

        assertThrows(AccessDeniedException.class,
                () -> service.revokeMembership(10L, 7L, outsider));

        verifyNoInteractions(membershipRepository);
    }

    private User owner() {
        User user = new User();
        user.setId(1L);
        return user;
    }
}
