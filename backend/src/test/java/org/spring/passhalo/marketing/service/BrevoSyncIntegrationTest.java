package org.spring.passhalo.marketing.service;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.marketing.entity.BrevoConnection;
import org.spring.passhalo.marketing.repository.BrevoConnectionRepository;
import org.spring.passhalo.marketing.repository.BrevoSyncJobRepository;
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
    @Autowired private BrevoConnectionRepository connectionRepository;
    @Autowired private BrevoSyncJobRepository jobRepository;
    @Autowired private MarketingService marketingService;
    @Autowired private BrevoSyncService syncService;
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

        BrevoConnection connection = new BrevoConnection();
        connection.setOwner(owner);
        connection.setApiKeyCiphertext(cryptoService.encrypt("test-brevo-key"));
        connection.setListId(42L);
        connection.setOrganizationId("organization-1");
        connection.setWebhookId(77L);
        connection.setWebhookSecretHash("a".repeat(64));
        connectionRepository.saveAndFlush(connection);

        String token = marketingService.registerConsent(owner, 123L, "Ada", "Lovelace", "ada@example.test");
        assertEquals(1, jobRepository.countByOwnerId(owner.getId()));
        syncService.flushPending();
        verify(apiClient).upsertContact("test-brevo-key", 42L, "ada@example.test", "Ada", "Lovelace");
        assertEquals(0, jobRepository.countByOwnerId(owner.getId()));

        marketingService.unsubscribe(token);
        assertEquals(1, jobRepository.countByOwnerId(owner.getId()));
        syncService.flushPending();
        verify(apiClient).removeFromList("test-brevo-key", 42L, "ada@example.test");
        assertEquals(0, jobRepository.countByOwnerId(owner.getId()));
    }
}
