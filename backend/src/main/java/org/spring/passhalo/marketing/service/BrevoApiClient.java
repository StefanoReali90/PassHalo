package org.spring.passhalo.marketing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class BrevoApiClient {
    private static final String BASE = "https://api.brevo.com/v3";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public void verifyList(String apiKey, long listId) {
        request(apiKey, "GET", "/contacts/lists/" + listId, null);
    }

    public String accountOrganizationId(String apiKey) {
        String organizationId = request(apiKey, "GET", "/account", null)
                .path("organization_id").asText("").trim();
        if (organizationId.isEmpty() || organizationId.length() > 100) {
            throw new BrevoApiException("Brevo did not return an organization ID");
        }
        return organizationId;
    }

    public long createWebhook(String apiKey, String callbackUrl, String secret) {
        JsonNode result = request(apiKey, "POST", "/webhooks", Map.of(
                "type", "marketing", "events", List.of("unsubscribed"),
                "description", "PassHalo marketing opt-out",
                "url", callbackUrl,
                "headers", List.of(Map.of("key", "X-PassHalo-Brevo-Secret", "value", secret))));
        long id = result.path("id").asLong();
        if (id <= 0) throw new BrevoApiException("Brevo did not return a webhook ID");
        return id;
    }

    public void deleteWebhook(String apiKey, long webhookId) {
        try {
            request(apiKey, "DELETE", "/webhooks/" + webhookId, null);
        } catch (BrevoNotFoundException ignored) {
            // Already removed in Brevo.
        }
    }

    public void upsertContact(String apiKey, long listId, String email, String name, String surname) {
        // Do not set emailBlacklisted=false: an unsubscribe in Brevo must never be undone here.
        request(apiKey, "POST", "/contacts", Map.of(
                "email", email, "attributes", Map.of("FNAME", name, "LNAME", surname),
                "listIds", List.of(listId), "updateEnabled", true));
    }

    public void removeFromList(String apiKey, long listId, String email) {
        String identifier = URLEncoder.encode(email, StandardCharsets.UTF_8);
        try {
            request(apiKey, "PUT", "/contacts/" + identifier, Map.of("unlinkListIds", List.of(listId)));
        } catch (BrevoNotFoundException ignored) {
            // A contact already removed from Brevo has reached the desired state.
        }
    }

    private JsonNode request(String apiKey, String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("api-key", apiKey)
                    .header("Accept", "application/json");
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            }
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) throw new BrevoNotFoundException();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // Brevo's response body may contain the contact or credential; never log or return it.
                throw new BrevoApiException("Brevo request failed (HTTP " + response.statusCode() + ")");
            }
            return response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
        } catch (IOException exception) {
            throw new BrevoApiException("Brevo is unreachable");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BrevoApiException("Brevo request interrupted");
        }
    }

    public static class BrevoApiException extends RuntimeException {
        public BrevoApiException(String message) { super(message); }
    }

    public static class BrevoNotFoundException extends BrevoApiException {
        public BrevoNotFoundException() { super("Brevo resource not found"); }
    }
}
