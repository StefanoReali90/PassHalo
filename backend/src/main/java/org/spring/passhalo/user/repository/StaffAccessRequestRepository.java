package org.spring.passhalo.user.repository;

import org.spring.passhalo.user.entity.StaffAccessRequest;
import org.spring.passhalo.user.enums.StaffAccessState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface StaffAccessRequestRepository extends JpaRepository<StaffAccessRequest, Long> {
    Optional<StaffAccessRequest> findBySessionHash(String sessionHash);
    List<StaffAccessRequest> findAllByEventId(Long eventId);

    @Lock(PESSIMISTIC_WRITE)
    Optional<StaffAccessRequest> findByIdAndEventId(Long id, Long eventId);

    List<StaffAccessRequest> findAllByEventIdAndState(Long eventId, StaffAccessState state);
    List<StaffAccessRequest> findAllByEventIdAndStateIn(Long eventId, List<StaffAccessState> states);
}
