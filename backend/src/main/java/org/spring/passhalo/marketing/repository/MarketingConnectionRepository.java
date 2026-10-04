package org.spring.passhalo.marketing.repository;

import org.spring.passhalo.marketing.entity.MarketingConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketingConnectionRepository extends JpaRepository<MarketingConnection, Long> {
    Optional<MarketingConnection> findByOwnerId(Long ownerId);
}
