package org.spring.passhalo.notification.repository;

import org.spring.passhalo.notification.entity.OwnerSmtpSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerSmtpSettingsRepository extends JpaRepository<OwnerSmtpSettings, Long> {
    Optional<OwnerSmtpSettings> findByOwnerId(Long ownerId);
}
