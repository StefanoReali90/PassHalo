package org.spring.passhalo.marketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketingRetentionService {
    private final MarketingRepository marketingRepository;

    @Scheduled(cron = "0 20 3 * * *", zone = "Europe/Rome")
    @Transactional
    public void deleteExpiredSubscribers() {
        long deleted = marketingRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (deleted > 0) log.info("Expired marketing records removed: {}", deleted);
    }
}
