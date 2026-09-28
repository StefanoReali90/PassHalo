package org.spring.passhalo.marketing.repository;

import org.spring.passhalo.marketing.entity.BrevoConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BrevoConnectionRepository extends JpaRepository<BrevoConnection, Long> {
    Optional<BrevoConnection> findByOwnerId(Long ownerId);
}
