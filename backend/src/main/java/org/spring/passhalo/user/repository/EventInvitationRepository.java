package org.spring.passhalo.user.repository;

import org.spring.passhalo.user.entity.EventInvitation;
import org.spring.passhalo.user.enums.InviteState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

@Repository
public interface EventInvitationRepository extends JpaRepository<EventInvitation, Long> {
    Optional<EventInvitation> findByTokenHash(String tokenHash);
    boolean existsByEventIdAndRecipientEmailAndInviteStateAndExpiresAtAfter(Long eventId, String recipientEmail, InviteState inviteState, LocalDateTime now);
    @Lock(PESSIMISTIC_WRITE)
    Optional<EventInvitation> findByIdAndEventId(Long id, Long eventId);
    List<EventInvitation> findAllByEventIdAndInviteState(Long eventId, InviteState inviteState);
    List<EventInvitation> findAllByEventId(Long eventId);
    @Lock(PESSIMISTIC_WRITE)
    List<EventInvitation> findAllByInviteStateAndExpiresAtLessThanEqual(InviteState inviteState, LocalDateTime expiresAt);

}
