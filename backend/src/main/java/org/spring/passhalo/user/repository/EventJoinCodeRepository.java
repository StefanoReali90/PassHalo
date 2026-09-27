package org.spring.passhalo.user.repository;

import org.spring.passhalo.user.entity.EventJoinCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;

public interface EventJoinCodeRepository extends JpaRepository<EventJoinCode, Long> {
    @Lock(PESSIMISTIC_WRITE)
    Optional<EventJoinCode> findByCodeHash(String codeHash);

    List<EventJoinCode> findAllByEventIdAndRevokedAtIsNull(Long eventId);
}
