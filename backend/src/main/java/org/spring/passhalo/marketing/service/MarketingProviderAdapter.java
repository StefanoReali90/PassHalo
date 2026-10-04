package org.spring.passhalo.marketing.service;

import org.spring.passhalo.marketing.entity.MarketingConnection;

/** Each supported provider implements its own contact API and configuration format. */
public interface MarketingProviderAdapter {
    String provider();
    void upsertContact(MarketingConnection connection, String email, String name, String surname);
    void removeContact(MarketingConnection connection, String email);
}
