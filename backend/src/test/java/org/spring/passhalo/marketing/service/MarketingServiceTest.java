package org.spring.passhalo.marketing.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.spring.passhalo.user.entity.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.anyString;

@ExtendWith(MockitoExtension.class)
class MarketingServiceTest {
    @Mock private MarketingRepository marketingRepository;
    @Mock private PiiCryptoService cryptoService;
    @Mock private BrevoSyncService brevoSyncService;
    @InjectMocks private MarketingService marketingService;

    private User owner() {
        User owner = new User();
        owner.setId(1L);
        return owner;
    }

    @Test
    void storesNewMarketingConsentWithoutPlaintext() {
        when(cryptoService.emailLookupHash("ada@example.test")).thenReturn("v1:email-hash");
        when(marketingRepository.findAllByOwnerIdAndEmailLookupHash(1L, "v1:email-hash")).thenReturn(List.of());
        when(marketingRepository.findAllByOwnerIdAndEmailIgnoreCase(1L, "ada@example.test")).thenReturn(List.of());
        when(cryptoService.encrypt("Ada")).thenReturn("enc-name");
        when(cryptoService.encrypt("Lovelace")).thenReturn("enc-surname");
        when(cryptoService.encrypt("ada@example.test")).thenReturn("enc-email");

        String token = marketingService.registerConsent(owner(), 10L, "Ada", "Lovelace", "ada@example.test");
        assertNotNull(token);
        assertEquals(43, token.length());

        var subscriberCaptor = org.mockito.ArgumentCaptor.forClass(MarketingSubscriber.class);
        verify(marketingRepository).save(subscriberCaptor.capture());
        MarketingSubscriber saved = subscriberCaptor.getValue();
        assertNull(saved.getName());
        assertNull(saved.getSurname());
        assertNull(saved.getEmail());
        assertEquals("enc-name", saved.getNameCiphertext());
        assertEquals("enc-surname", saved.getSurnameCiphertext());
        assertEquals("enc-email", saved.getEmailCiphertext());
        assertEquals("v1:email-hash", saved.getEmailLookupHash());
        assertTrue(saved.isActive());
        assertEquals(1L, saved.getOwner().getId());
        assertEquals(10L, saved.getConsentEventId());
        assertEquals("owner-email-brevo-v1", saved.getConsentVersion());
        assertNotNull(saved.getConsentAt());
        assertNotNull(saved.getExpiresAt());
        assertNotNull(saved.getUnsubscribeTokenHash());
        assertEquals(64, saved.getUnsubscribeTokenHash().length());
        org.junit.jupiter.api.Assertions.assertNotEquals(token, saved.getUnsubscribeTokenHash());
    }

    @Test
    void unsubscribeDeletesEveryMarketingRecordForThatEmail() {
        MarketingSubscriber first = new MarketingSubscriber();
        first.setEmailLookupHash("v1:shared-hash");
        when(marketingRepository.findByUnsubscribeTokenHash(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.of(first));
        first.setOwner(owner());

        marketingService.unsubscribe("opaque-random-token");

        org.mockito.Mockito.verify(marketingRepository).deleteAllByOwnerIdAndEmailLookupHash(1L, "v1:shared-hash");
    }

    @Test
    void unknownOrBlankUnsubscribeTokensAreSafeAndDoNothing() {
        marketingService.unsubscribe(" ");
        org.mockito.Mockito.verify(marketingRepository, never()).findByUnsubscribeTokenHash(anyString());
    }

    @Test
    void reactivatesLegacySubscriberAndMovesItsPersonalDataToCiphertext() {
        MarketingSubscriber legacy = new MarketingSubscriber();
        legacy.setName("Ada");
        legacy.setSurname("Lovelace");
        legacy.setEmail("ADA@example.test");
        legacy.setActive(false);
        when(cryptoService.emailLookupHash("ADA@example.test")).thenReturn("v1:email-hash");
        legacy.setOwner(owner());
        when(marketingRepository.findAllByOwnerIdAndEmailLookupHash(1L, "v1:email-hash")).thenReturn(List.of());
        when(marketingRepository.findAllByOwnerIdAndEmailIgnoreCase(1L, "ADA@example.test")).thenReturn(List.of(legacy));
        when(cryptoService.encrypt("Ada")).thenReturn("enc-name");
        when(cryptoService.encrypt("Lovelace")).thenReturn("enc-surname");
        when(cryptoService.encrypt("ADA@example.test")).thenReturn("enc-email");

        marketingService.registerConsent(owner(), 10L, "Ada", "Lovelace", "ADA@example.test");

        assertNull(legacy.getName());
        assertNull(legacy.getSurname());
        assertNull(legacy.getEmail());
        assertEquals("enc-email", legacy.getEmailCiphertext());
        assertTrue(legacy.isActive());
        assertNotNull(legacy.getConsentAt());
        verify(marketingRepository).save(legacy);
    }
}
