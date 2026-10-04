package org.spring.passhalo.marketing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.spring.passhalo.marketing.entity.MarketingConnection;

/** Non-secret Brevo identifiers; API credentials stay in the encrypted generic column. */
public record BrevoSettings(long listId, String organizationId, long webhookId, String webhookSecretHash) {
    public static final String PROVIDER = "BREVO";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static BrevoSettings from(MarketingConnection connection) {
        if (!PROVIDER.equals(connection.getProvider())) {
            throw new IllegalArgumentException("The connection is not a Brevo connection");
        }
        try {
            return MAPPER.readValue(connection.getConfiguration(), BrevoSettings.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid Brevo configuration");
        }
    }

    public String serialize() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot encode Brevo configuration");
        }
    }
}
