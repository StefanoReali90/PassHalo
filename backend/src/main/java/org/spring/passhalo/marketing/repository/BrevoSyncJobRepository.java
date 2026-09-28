package org.spring.passhalo.marketing.repository;

import org.spring.passhalo.marketing.entity.BrevoSyncJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface BrevoSyncJobRepository extends JpaRepository<BrevoSyncJob, Long> {
    Optional<BrevoSyncJob> findByOwnerIdAndEmailLookupHash(Long ownerId, String emailLookupHash);
    @Query("select j from BrevoSyncJob j where exists (select c.id from BrevoConnection c where c.owner = j.owner) order by j.updatedAt asc")
    List<BrevoSyncJob> findReady(Pageable pageable);
    long countByOwnerId(Long ownerId);
}
