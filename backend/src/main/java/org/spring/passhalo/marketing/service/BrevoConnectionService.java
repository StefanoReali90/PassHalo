package org.spring.passhalo.marketing.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.marketing.entity.MarketingConnection;
import org.spring.passhalo.marketing.repository.MarketingConnectionRepository;
import org.spring.passhalo.marketing.repository.MarketingSyncJobRepository;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class BrevoConnectionService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MarketingConnectionRepository connectionRepository;
    private final MarketingSyncJobRepository jobRepository;
    private final MarketingRepository marketingRepository;
    private final UserRepository userRepository;
    private final PiiCryptoService cryptoService;
    private final BrevoApiClient apiClient;
    private final MarketingSyncService syncService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Transactional(readOnly = true)
    public Status status(String username) {
        User owner = owner(username);
        return connectionRepository.findByOwnerId(owner.getId())
                .filter(connection -> BrevoSettings.PROVIDER.equals(connection.getProvider()))
                .map(connection -> new Status(true, BrevoSettings.from(connection).listId(),
                        jobRepository.countByConnectionOwnerId(owner.getId())))
                .orElseGet(() -> new Status(false, null, 0));
    }

    @Transactional
    public Status connect(String username, String rawApiKey, long listId) {
        User owner = owner(username);
        if (connectionRepository.findByOwnerId(owner.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un provider marketing è già collegato");
        }
        String apiKey = validatedApiKey(rawApiKey);
        if (listId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista Brevo non valida");
        if (!frontendBaseUrl.startsWith("https://")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Per Brevo serve un indirizzo HTTPS pubblico");
        }
        final String organizationId;
        try {
            organizationId = apiClient.accountOrganizationId(apiKey);
            apiClient.verifyList(apiKey, listId);
        } catch (BrevoApiClient.BrevoApiException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chiave Brevo o lista non valida");
        }
        String webhookSecret = newWebhookSecret();
        final long webhookId;
        try {
            webhookId = apiClient.createWebhook(apiKey, webhookUrl(owner), webhookSecret);
        } catch (BrevoApiClient.BrevoApiException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Impossibile configurare la disiscrizione Brevo");
        }
        MarketingConnection connection = new MarketingConnection();
        connection.setOwner(owner);
        connection.setProvider(BrevoSettings.PROVIDER);
        connection.setCredentialsCiphertext(cryptoService.encrypt(apiKey));
        connection.setConfiguration(new BrevoSettings(listId, organizationId, webhookId, sha256(webhookSecret)).serialize());
        connectionRepository.saveAndFlush(connection);
        syncService.queueAllActive(owner);
        return new Status(true, listId, jobRepository.countByConnectionOwnerId(owner.getId()));
    }

    @Transactional
    public Status rotateKey(String username, String rawApiKey) {
        User owner = owner(username);
        MarketingConnection connection = connectionRepository.findByOwnerId(owner.getId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Brevo non è collegato"));
        BrevoSettings settings = settings(connection);
        String apiKey = validatedApiKey(rawApiKey);
        try {
            if (!settings.organizationId().equals(apiClient.accountOrganizationId(apiKey))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La nuova chiave deve appartenere allo stesso account Brevo");
            }
            apiClient.verifyList(apiKey, settings.listId());
        } catch (BrevoApiClient.BrevoApiException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nuova chiave Brevo non valida");
        }
        String webhookSecret = newWebhookSecret();
        final long newWebhookId;
        try {
            newWebhookId = apiClient.createWebhook(apiKey, webhookUrl(owner), webhookSecret);
        } catch (BrevoApiClient.BrevoApiException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Impossibile aggiornare il webhook Brevo");
        }
        long oldWebhookId = settings.webhookId();
        connection.setCredentialsCiphertext(cryptoService.encrypt(apiKey));
        connection.setConfiguration(new BrevoSettings(settings.listId(), settings.organizationId(),
                newWebhookId, sha256(webhookSecret)).serialize());
        connectionRepository.saveAndFlush(connection);
        try {
            apiClient.deleteWebhook(apiKey, oldWebhookId);
        } catch (BrevoApiClient.BrevoApiException ignored) {
            // The old webhook can only reach an invalid secret; the new webhook is active.
        }
        return new Status(true, settings.listId(), jobRepository.countByConnectionOwnerId(owner.getId()));
    }

    @Transactional
    public void disconnect(String username) {
        User owner = owner(username);
        MarketingConnection connection = connectionRepository.findByOwnerId(owner.getId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Brevo non è collegato"));
        if (jobRepository.countByConnectionOwnerId(owner.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendi la sincronizzazione dei contatti in sospeso");
        }
        BrevoSettings settings = settings(connection);
        String apiKey = cryptoService.decrypt(connection.getCredentialsCiphertext());
        try {
            for (var subscriber : marketingRepository.findAllByOwnerIdAndIsActiveTrueAndExpiresAtAfter(
                    owner.getId(), LocalDateTime.now())) {
                apiClient.removeFromList(apiKey, settings.listId(),
                        cryptoService.decrypt(subscriber.getEmailCiphertext()));
            }
            apiClient.deleteWebhook(apiKey, settings.webhookId());
        } catch (BrevoApiClient.BrevoApiException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Impossibile scollegare Brevo in sicurezza");
        }
        connectionRepository.delete(connection);
    }

    @Transactional
    public void unsubscribeFromBrevo(long ownerId, String secret, JsonNode body) {
        MarketingConnection connection = connectionRepository.findByOwnerId(ownerId).orElse(null);
        if (connection == null || !BrevoSettings.PROVIDER.equals(connection.getProvider())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        BrevoSettings settings = BrevoSettings.from(connection);
        if (secret == null || !MessageDigest.isEqual(
                settings.webhookSecretHash().getBytes(StandardCharsets.US_ASCII),
                sha256(secret).getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String event = body.path("event").asText();
        if (!event.equals("unsubscribe")) return;
        String email = body.path("email").asText("");
        if (email.isBlank()) return;
        JsonNode lists = body.path("list_id");
        if (!lists.isArray() || lists.isEmpty()) return;
        boolean matchingList = false;
        for (JsonNode list : lists) {
            if (list.asLong() == settings.listId()) matchingList = true;
        }
        if (!matchingList) return;
        String hash = cryptoService.emailLookupHash(email);
        marketingRepository.deleteAllByOwnerIdAndEmailLookupHash(ownerId, hash);
        jobRepository.findByConnectionIdAndEmailLookupHash(connection.getId(), hash).ifPresent(jobRepository::delete);
    }

    private static BrevoSettings settings(MarketingConnection connection) {
        if (!BrevoSettings.PROVIDER.equals(connection.getProvider())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Il provider collegato non è Brevo");
        }
        return BrevoSettings.from(connection);
    }

    private User owner(String username) {
        User user = userRepository.findByEmailIgnoreCase(username).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getRole() != Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }

    private static String validatedApiKey(String rawApiKey) {
        String apiKey = rawApiKey.trim();
        if (apiKey.isEmpty() || apiKey.indexOf('\r') >= 0 || apiKey.indexOf('\n') >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chiave Brevo non valida");
        }
        return apiKey;
    }

    private static String newWebhookSecret() {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String webhookUrl(User owner) {
        return frontendBaseUrl.replaceAll("/+$", "")
                + "/api/marketing/brevo/webhook/" + owner.getId();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Status(boolean connected, Long listId, long pendingContacts) { }
}
