package org.spring.passhalo.marketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketingRetentionService {
    private final MarketingRepository marketingRepository;
    private final BrevoSyncService brevoSyncService;

    @Scheduled(cron = "0 20 3 * * *", zone = "Europe/Rome")
    @Transactional
    public void deleteExpiredSubscribers() {
        for (MarketingSubscriber subscriber : marketingRepository.findAllByExpiresAtBefore(LocalDateTime.now())) {
            if (subscriber.getOwner() != null) {
                brevoSyncService.queue(subscriber.getOwner(), subscriber.getEmailLookupHash(),
                        subscriber.getEmailCiphertext());
            }
        }
        long deleted = marketingRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) log.info("Consensi marketing scaduti rimossi numero={}", deleted);
    }
}
