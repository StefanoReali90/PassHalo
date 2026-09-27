package org.spring.passhalo.marketing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.spring.passhalo.marketing.repository.MarketingRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

@ExtendWith(MockitoExtension.class)
class MarketingPiiMigrationServiceTest {
    @Mock private MarketingRepository marketingRepository;
    @Mock private PiiCryptoService cryptoService;
    @InjectMocks private MarketingPiiMigrationService migrationService;

    @Test
    void encryptsLegacySubscriberAndClearsPlaintext() {
        MarketingSubscriber legacy = new MarketingSubscriber();
        legacy.setName("Ada");
        legacy.setSurname("Lovelace");
        legacy.setEmail("ada@example.test");
        when(marketingRepository.findTop500ByEmailLookupHashIsNullAndEmailIsNotNullOrderByIdAsc())
                .thenReturn(List.of(legacy));
        when(cryptoService.encrypt("Ada")).thenReturn("enc-name");
        when(cryptoService.encrypt("Lovelace")).thenReturn("enc-surname");
        when(cryptoService.encrypt("ada@example.test")).thenReturn("enc-email");
        when(cryptoService.emailLookupHash("ada@example.test")).thenReturn("v1:email-hash");

        assertEquals(1, migrationService.migrateNextBatch());

        assertNull(legacy.getName());
        assertNull(legacy.getSurname());
        assertNull(legacy.getEmail());
        assertEquals("enc-name", legacy.getNameCiphertext());
        assertEquals("enc-surname", legacy.getSurnameCiphertext());
        assertEquals("enc-email", legacy.getEmailCiphertext());
        assertEquals("v1:email-hash", legacy.getEmailLookupHash());
        verify(marketingRepository).saveAll(List.of(legacy));
    }

    @Test
    void preservesLegacyDataWhenARequiredFieldIsMissing() {
        MarketingSubscriber incomplete = new MarketingSubscriber();
        incomplete.setName("Ada");
        when(marketingRepository.findTop500ByEmailLookupHashIsNullAndEmailIsNotNullOrderByIdAsc())
                .thenReturn(List.of(incomplete));

        assertThrows(IllegalStateException.class, migrationService::migrateNextBatch);

        assertEquals("Ada", incomplete.getName());
        assertNull(incomplete.getNameCiphertext());
        verify(marketingRepository, never()).saveAll(anyList());
    }
}
