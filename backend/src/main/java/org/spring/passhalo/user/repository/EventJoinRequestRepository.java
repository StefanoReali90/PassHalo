package org.spring.passhalo.user.repository;

import org.spring.passhalo.user.entity.EventJoinRequest;
import org.spring.passhalo.user.enums.JoinRequestState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface EventJoinRequestRepository extends JpaRepository<EventJoinRequest, Long> {
    Optional<EventJoinRequest> findFirstByEventIdAndRequesterIdAndState(Long eventId, Long requesterId,
                                                                        JoinRequestState state);

    @Lock(PESSIMISTIC_WRITE)
    Optional<EventJoinRequest> findByIdAndEventId(Long id, Long eventId);

    List<EventJoinRequest> findAllByEventIdAndStateOrderByCreatedAtAsc(Long eventId, JoinRequestState state);

    List<EventJoinRequest> findAllByRequesterIdOrderByCreatedAtDesc(Long requesterId);
    List<EventJoinRequest> findAllByEventId(Long eventId);

    @Query("select r from EventJoinRequest r where r.state = :state and r.event.endDateTime <= :now")
    List<EventJoinRequest> findAllEndedByState(JoinRequestState state, LocalDateTime now);
}
