package org.spring.passhalo.user.repository;

import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.enums.MembershipState;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

@Repository
public interface EventMembershipRepository extends JpaRepository<EventMembership, Long> {
    Optional<EventMembership> findByEventIdAndCollaboratorId(Long eventId, Long collaboratorId);

    @Lock(PESSIMISTIC_WRITE)
    Optional<EventMembership> findByIdAndEventId(Long id, Long eventId);

    List<EventMembership> findAllByEventId(Long eventId);

    List<EventMembership> findByEventIdAndMembershipState(Long eventId, MembershipState membershipState);

    List<EventMembership> findByCollaboratorIdAndMembershipState(Long collaboratorId, MembershipState membershipState);

}
