package org.spring.passhalo.marketing.service;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.marketing.entity.MarketingConnection;
import org.spring.passhalo.security.PiiCryptoService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrevoMarketingAdapter implements MarketingProviderAdapter {
    private final BrevoApiClient apiClient;
    private final PiiCryptoService cryptoService;

    @Override
    public String provider() { return BrevoSettings.PROVIDER; }

    @Override
    public void upsertContact(MarketingConnection connection, String email, String name, String surname) {
        apiClient.upsertContact(cryptoService.decrypt(connection.getCredentialsCiphertext()),
                BrevoSettings.from(connection).listId(), email, name, surname);
    }

    @Override
    public void removeContact(MarketingConnection connection, String email) {
        apiClient.removeFromList(cryptoService.decrypt(connection.getCredentialsCiphertext()),
                BrevoSettings.from(connection).listId(), email);
    }
}
