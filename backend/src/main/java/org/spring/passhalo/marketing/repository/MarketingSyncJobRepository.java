package org.spring.passhalo.marketing.repository;

import org.spring.passhalo.marketing.entity.MarketingSyncJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface MarketingSyncJobRepository extends JpaRepository<MarketingSyncJob, Long> {
    Optional<MarketingSyncJob> findByConnectionIdAndEmailLookupHash(Long connectionId, String emailLookupHash);
    @Query("select j from MarketingSyncJob j order by j.updatedAt asc")
    List<MarketingSyncJob> findReady(Pageable pageable);
    long countByConnectionOwnerId(Long ownerId);
}
