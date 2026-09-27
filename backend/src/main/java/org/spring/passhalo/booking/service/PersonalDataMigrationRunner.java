package org.spring.passhalo.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.marketing.service.MarketingPiiMigrationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.pii.migration.enabled", havingValue = "true")
public class PersonalDataMigrationRunner implements ApplicationRunner {
    private final BookingPiiMigrationService migrationService;
    private final MarketingPiiMigrationService marketingMigrationService;

    @Override
    public void run(ApplicationArguments args) {
        int migratedRows;
        int batches = 0;
        do {
            migratedRows = migrationService.migrateNextBatch();
            if (migratedRows > 0) {
                batches++;
            }
        } while (migratedRows > 0);
        int migratedSubscribers;
        int marketingBatches = 0;
        do {
            migratedSubscribers = marketingMigrationService.migrateNextBatch();
            if (migratedSubscribers > 0) {
                marketingBatches++;
            }
        } while (migratedSubscribers > 0);
        log.info("PII migration finished; booking batches: {}, marketing batches: {}", batches, marketingBatches);
    }
}
