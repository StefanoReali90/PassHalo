package org.spring.passhalo.user.repository;

import org.spring.passhalo.user.entity.StaffAccessCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffAccessCodeRepository extends JpaRepository<StaffAccessCode, Long> {
    Optional<StaffAccessCode> findByCodeHash(String codeHash);
    List<StaffAccessCode> findAllByEventId(Long eventId);
    List<StaffAccessCode> findAllByEventIdAndRevokedAtIsNull(Long eventId);
}
