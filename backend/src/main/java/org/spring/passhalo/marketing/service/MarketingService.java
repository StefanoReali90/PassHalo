package org.spring.passhalo.marketing.service;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class MarketingService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final MarketingRepository marketingRepository;
    private final PiiCryptoService cryptoService;
    private final BrevoSyncService brevoSyncService;

    @Value("${app.marketing.retention-months:24}")
    private long retentionMonths = 24;

    @Transactional
    public String registerConsent(User owner, Long eventId, String name, String surname, String email) {
        if (retentionMonths < 1) throw new IllegalStateException("Marketing retention must be at least one month");
        String normalizedEmail = email.trim();
        String emailLookupHash = cryptoService.emailLookupHash(email);
        var subscribers = marketingRepository.findAllByOwnerIdAndEmailLookupHash(owner.getId(), emailLookupHash);
        if (subscribers.isEmpty()) {
            subscribers = marketingRepository.findAllByOwnerIdAndEmailIgnoreCase(owner.getId(), normalizedEmail);
        }

        MarketingSubscriber subscriber = subscribers.isEmpty() ? new MarketingSubscriber() : subscribers.getFirst();
        subscriber.setOwner(owner);
        subscriber.setConsentEventId(eventId);
        subscriber.setConsentVersion("owner-email-brevo-v1");
        subscriber.setNameCiphertext(cryptoService.encrypt(name));
        subscriber.setSurnameCiphertext(cryptoService.encrypt(surname));
        subscriber.setEmailCiphertext(cryptoService.encrypt(email));
        subscriber.setEmailLookupHash(emailLookupHash);
        subscriber.setName(null);
        subscriber.setSurname(null);
        subscriber.setEmail(null);
        subscriber.setConsentAt(LocalDateTime.now());
        subscriber.setExpiresAt(LocalDateTime.now().plusMonths(retentionMonths));
        String unsubscribeToken = createUnsubscribeToken();
        subscriber.setUnsubscribeTokenHash(hashToken(unsubscribeToken));
        subscriber.setActive(true);
        marketingRepository.save(subscriber);
        brevoSyncService.queue(owner, emailLookupHash, subscriber.getEmailCiphertext());
        return unsubscribeToken;
    }

    @Transactional
    public void unsubscribe(String token) {
        if (token == null || token.isBlank() || token.length() > 128) return;
        marketingRepository.findByUnsubscribeTokenHash(hashToken(token)).ifPresent(subscriber -> {
            if (subscriber.getOwner() == null) {
                marketingRepository.deleteAllByOwnerIsNullAndEmailLookupHash(subscriber.getEmailLookupHash());
            } else {
                brevoSyncService.queue(subscriber.getOwner(), subscriber.getEmailLookupHash(),
                        subscriber.getEmailCiphertext());
                marketingRepository.deleteAllByOwnerIdAndEmailLookupHash(
                        subscriber.getOwner().getId(), subscriber.getEmailLookupHash());
            }
        });
    }

    private String createUnsubscribeToken() {
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
