package org.spring.passhalo.marketing.repository;

import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface MarketingRepository extends JpaRepository<MarketingSubscriber, Long> {
    List<MarketingSubscriber> findAllByEmailLookupHash(String emailLookupHash);
    List<MarketingSubscriber> findAllByEmailIgnoreCase(String email);
    List<MarketingSubscriber> findTop500ByEmailLookupHashIsNullAndEmailIsNotNullOrderByIdAsc();
    java.util.Optional<MarketingSubscriber> findByUnsubscribeTokenHash(String unsubscribeTokenHash);
    long deleteAllByEmailLookupHash(String emailLookupHash);
    long deleteByExpiresAtBefore(LocalDateTime expirationTime);
}
