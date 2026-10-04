package org.spring.passhalo.marketing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.spring.passhalo.marketing.entity.MarketingConnection;
import org.spring.passhalo.marketing.repository.MarketingConnectionRepository;
import org.spring.passhalo.marketing.repository.MarketingSyncJobRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(MarketingProviderDispatchIntegrationTest.Adapters.class)
@Transactional
class MarketingProviderDispatchIntegrationTest {
    @TestConfiguration
    static class Adapters {
        @Bean
        MarketingProviderAdapter otherMarketingAdapter() { return mock(MarketingProviderAdapter.class); }
    }

    @Autowired @Qualifier("otherMarketingAdapter") private MarketingProviderAdapter otherAdapter;
    @Autowired private UserRepository users;
    @Autowired private MarketingConnectionRepository connections;
    @Autowired private MarketingSyncJobRepository jobs;
    @Autowired private MarketingService marketing;
    @Autowired private MarketingSyncService sync;
    @Autowired private PiiCryptoService crypto;
    @MockitoBean private BrevoApiClient brevo;

    @BeforeEach
    void setUp() {
        reset(otherAdapter);
        when(otherAdapter.provider()).thenReturn("OTHER_TEST_PROVIDER");
    }

    @Test
    void dispatchesContactCreationAndRevocationToTheConfiguredAdapter() {
        User owner = connection("OTHER_TEST_PROVIDER");
        String token = marketing.registerConsent(owner, 1L, "Ada", "Test", "adapter@example.test");
        sync.flushPending();
        verify(otherAdapter).upsertContact(any(MarketingConnection.class), eq("adapter@example.test"), eq("Ada"), eq("Test"));
        assertEquals(0, jobs.countByConnectionOwnerId(owner.getId()));
        marketing.unsubscribe(token);
        sync.flushPending();
        verify(otherAdapter).removeContact(any(MarketingConnection.class), eq("adapter@example.test"));
        assertEquals(0, jobs.countByConnectionOwnerId(owner.getId()));
        verifyNoInteractions(brevo);
    }

    @Test
    void retainsFailedOperationsUntilTheProviderCanProcessThem() {
        User owner = connection("OTHER_TEST_PROVIDER");
        doThrow(new IllegalStateException("Provider unavailable")).doNothing().when(otherAdapter)
                .upsertContact(any(), anyString(), anyString(), anyString());
        marketing.registerConsent(owner, 1L, "Ada", "Test", "retry@example.test");
        sync.flushPending();
        assertEquals(1, jobs.countByConnectionOwnerId(owner.getId()));
        sync.flushPending();
        assertEquals(0, jobs.countByConnectionOwnerId(owner.getId()));
        verifyNoInteractions(brevo);
    }

    @Test
    void unknownProviderDoesNotSendCredentialsOrContactsToBrevo() {
        User owner = connection("NOT_IMPLEMENTED");
        marketing.registerConsent(owner, 1L, "Ada", "Test", "unknown@example.test");
        sync.flushPending();
        assertEquals(1, jobs.countByConnectionOwnerId(owner.getId()));
        verify(otherAdapter, never()).upsertContact(any(), anyString(), anyString(), anyString());
        verifyNoInteractions(brevo);
    }

    private User connection(String provider) {
        User owner = new User();
        owner.setName("Test"); owner.setSurname("Provider"); owner.setEmail("adapter-" + provider + "@example.test");
        owner.setPassword("test-password"); owner.setRole(Role.ADMIN);
        owner = users.saveAndFlush(owner);
        MarketingConnection connection = new MarketingConnection();
        connection.setOwner(owner);
        connection.setProvider(provider);
        connection.setCredentialsCiphertext(crypto.encrypt("other-provider-secret"));
        // Deliberately no Brevo list/organization/webhook identifiers.
        connection.setConfiguration("{\"audienceId\":\"external-audience-abc\"}");
        connections.saveAndFlush(connection);
        return owner;
    }
}
