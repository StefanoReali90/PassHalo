package org.spring.passhalo.marketing.service;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.marketing.entity.MarketingConnection;
import org.spring.passhalo.marketing.repository.MarketingConnectionRepository;
import org.spring.passhalo.marketing.repository.MarketingSyncJobRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class BrevoSyncIntegrationTest {
    @Autowired private UserRepository userRepository;
    @Autowired private MarketingConnectionRepository connectionRepository;
    @Autowired private MarketingSyncJobRepository jobRepository;
    @Autowired private MarketingService marketingService;
    @Autowired private MarketingSyncService syncService;
    @Autowired private PiiCryptoService cryptoService;
    @MockitoBean private BrevoApiClient apiClient;

    @Test
    void consentAndRevocationSynchronizeOnlyTheOwnersBrevoList() {
        User owner = new User();
        owner.setName("Valerio");
        owner.setSurname("Test");
        owner.setEmail("brevo-sync-valerio@example.test");
        owner.setPassword("hashed-test-password");
        owner.setRole(Role.ADMIN);
        owner = userRepository.saveAndFlush(owner);

        MarketingConnection connection = new MarketingConnection();
        connection.setOwner(owner);
        connection.setCredentialsCiphertext(cryptoService.encrypt("test-brevo-key"));
        connection.setProvider(BrevoSettings.PROVIDER);
        connection.setConfiguration(new BrevoSettings(42L, "organization-1", 77L, "a".repeat(64)).serialize());
        connectionRepository.saveAndFlush(connection);

        String token = marketingService.registerConsent(owner, 123L, "Ada", "Lovelace", "ada@example.test");
        assertEquals(1, jobRepository.countByConnectionOwnerId(owner.getId()));
        syncService.flushPending();
        verify(apiClient).upsertContact("test-brevo-key", 42L, "ada@example.test", "Ada", "Lovelace");
        assertEquals(0, jobRepository.countByConnectionOwnerId(owner.getId()));

        marketingService.unsubscribe(token);
        assertEquals(1, jobRepository.countByConnectionOwnerId(owner.getId()));
        syncService.flushPending();
        verify(apiClient).removeFromList("test-brevo-key", 42L, "ada@example.test");
        assertEquals(0, jobRepository.countByConnectionOwnerId(owner.getId()));
    }
}
