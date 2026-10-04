package org.spring.passhalo.marketing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.marketing.entity.MarketingConnection;
import org.spring.passhalo.marketing.repository.MarketingConnectionRepository;
import org.spring.passhalo.marketing.repository.MarketingSyncJobRepository;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrevoConnectionServiceTest {
    @Mock private MarketingConnectionRepository connectionRepository;
    @Mock private MarketingSyncJobRepository jobRepository;
    @Mock private MarketingRepository marketingRepository;
    @Mock private UserRepository userRepository;
    @Mock private PiiCryptoService cryptoService;
    @Mock private BrevoApiClient apiClient;
    @Mock private MarketingSyncService syncService;
    @InjectMocks private BrevoConnectionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://passhalo.it");
    }

    @Test
    void connectsOnlyTheAuthenticatedOrganizerAndNeverReturnsTheKey() {
        User owner = owner(1L, "valerio@example.test");
        when(userRepository.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(connectionRepository.findByOwnerId(1L)).thenReturn(Optional.empty());
        when(apiClient.accountOrganizationId("secret-key")).thenReturn("organization-1");
        when(apiClient.createWebhook(eq("secret-key"), eq("https://passhalo.it/api/marketing/brevo/webhook/1"), anyString()))
                .thenReturn(77L);
        when(cryptoService.encrypt("secret-key")).thenReturn("encrypted-key");

        var status = service.connect(owner.getEmail(), "secret-key", 42L);

        assertTrue(status.connected());
        assertEquals(42L, status.listId());
        var captor = ArgumentCaptor.forClass(MarketingConnection.class);
        verify(connectionRepository).saveAndFlush(captor.capture());
        assertEquals(1L, captor.getValue().getOwner().getId());
        assertEquals("encrypted-key", captor.getValue().getCredentialsCiphertext());
        assertEquals("organization-1", BrevoSettings.from(captor.getValue()).organizationId());
        assertNotEquals("secret-key", captor.getValue().getCredentialsCiphertext());
        assertEquals(64, BrevoSettings.from(captor.getValue()).webhookSecretHash().length());
        verify(syncService).queueAllActive(owner);
        verify(apiClient).verifyList("secret-key", 42L);
    }

    @Test
    void webhookDeletesOnlyTheMatchingOwnersConsent() throws Exception {
        User owner = owner(1L, "valerio@example.test");
        when(userRepository.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(connectionRepository.findByOwnerId(1L)).thenReturn(Optional.empty());
        when(apiClient.accountOrganizationId("secret-key")).thenReturn("organization-1");
        when(apiClient.createWebhook(anyString(), anyString(), anyString())).thenReturn(77L);
        when(cryptoService.encrypt(anyString())).thenReturn("encrypted-key");
        service.connect(owner.getEmail(), "secret-key", 42L);
        var connectionCaptor = ArgumentCaptor.forClass(MarketingConnection.class);
        var secretCaptor = ArgumentCaptor.forClass(String.class);
        verify(connectionRepository).saveAndFlush(connectionCaptor.capture());
        verify(apiClient).createWebhook(anyString(), anyString(), secretCaptor.capture());
        when(connectionRepository.findByOwnerId(1L)).thenReturn(Optional.of(connectionCaptor.getValue()));
        connectionCaptor.getValue().setId(10L);
        when(cryptoService.emailLookupHash("shared@example.test")).thenReturn("v1:hash");
        var payload = new ObjectMapper().readTree("{\"event\":\"unsubscribe\",\"email\":\"shared@example.test\",\"list_id\":[42]}");

        service.unsubscribeFromBrevo(1L, secretCaptor.getValue(), payload);

        verify(marketingRepository).deleteAllByOwnerIdAndEmailLookupHash(1L, "v1:hash");
        verify(marketingRepository, never()).deleteAllByEmailLookupHash(anyString());
        assertThrows(ResponseStatusException.class,
                () -> service.unsubscribeFromBrevo(1L, "wrong-secret", payload));
        verify(marketingRepository, times(1)).deleteAllByOwnerIdAndEmailLookupHash(1L, "v1:hash");
        var anotherList = new ObjectMapper().readTree("{\"event\":\"unsubscribe\",\"email\":\"shared@example.test\",\"list_id\":[99]}");
        service.unsubscribeFromBrevo(1L, secretCaptor.getValue(), anotherList);
        verify(marketingRepository, times(1)).deleteAllByOwnerIdAndEmailLookupHash(1L, "v1:hash");
    }

    @Test
    void rotatesExpiredKeyWithoutDroppingPendingContacts() {
        User owner = owner(1L, "valerio@example.test");
        MarketingConnection connection = new MarketingConnection();
        connection.setOwner(owner);
        connection.setProvider(BrevoSettings.PROVIDER);
        connection.setConfiguration(new BrevoSettings(42L, "organization-1", 77L, "a".repeat(64)).serialize());
        when(userRepository.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(connectionRepository.findByOwnerId(1L)).thenReturn(Optional.of(connection));
        when(apiClient.accountOrganizationId("new-key")).thenReturn("organization-1");
        when(apiClient.createWebhook(eq("new-key"), anyString(), anyString())).thenReturn(88L);
        when(cryptoService.encrypt("new-key")).thenReturn("new-encrypted-key");
        when(jobRepository.countByConnectionOwnerId(1L)).thenReturn(3L);

        var status = service.rotateKey(owner.getEmail(), "new-key");

        assertEquals(3L, status.pendingContacts());
        assertEquals("new-encrypted-key", connection.getCredentialsCiphertext());
        assertEquals(88L, BrevoSettings.from(connection).webhookId());
        verify(apiClient).verifyList("new-key", 42L);
        verify(apiClient).deleteWebhook("new-key", 77L);
        verifyNoInteractions(syncService);
    }

    @Test
    void refusesAKeyForADifferentBrevoOrganization() {
        User owner = owner(1L, "valerio@example.test");
        MarketingConnection connection = new MarketingConnection();
        connection.setProvider(BrevoSettings.PROVIDER);
        connection.setConfiguration(new BrevoSettings(42L, "organization-1", 77L, "a".repeat(64)).serialize());
        when(userRepository.findByEmailIgnoreCase(owner.getEmail())).thenReturn(Optional.of(owner));
        when(connectionRepository.findByOwnerId(1L)).thenReturn(Optional.of(connection));
        when(apiClient.accountOrganizationId("other-key")).thenReturn("organization-2");

        assertThrows(ResponseStatusException.class,
                () -> service.rotateKey(owner.getEmail(), "other-key"));

        verify(apiClient, never()).createWebhook(anyString(), anyString(), anyString());
        verify(connectionRepository, never()).saveAndFlush(any());
    }

    private User owner(long id, String email) {
        User owner = new User();
        owner.setId(id);
        owner.setEmail(email);
        owner.setRole(Role.ADMIN);
        return owner;
    }
}
