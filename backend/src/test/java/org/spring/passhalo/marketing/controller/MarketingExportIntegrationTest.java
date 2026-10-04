package org.spring.passhalo.marketing.controller;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.spring.passhalo.marketing.service.MarketingService;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarketingExportIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private MarketingRepository subscribers;
    @Autowired private MarketingService marketing;
    @Autowired private PiiCryptoService crypto;

    @Test
    void exportsOnlyAuthenticatedOwnersActiveUnexpiredConsents() throws Exception {
        User first = owner("export-first@example.test", Role.ADMIN);
        User other = owner("export-other@example.test", Role.ADMIN);
        subscriber(first, "visible@example.test", "Ada", true, LocalDateTime.now().plusMonths(1));
        subscriber(other, "other@example.test", "Other", true, LocalDateTime.now().plusMonths(1));
        subscriber(first, "inactive@example.test", "Inactive", false, LocalDateTime.now().plusMonths(1));
        subscriber(first, "expired@example.test", "Expired", true, LocalDateTime.now().minusDays(1));
        subscriber(first, "unknown-expiry@example.test", "Unknown", true, null);
        subscriber(null, "legacy@example.test", "Legacy", true, LocalDateTime.now().plusMonths(1));
        var response = mockMvc.perform(get("/marketing/contacts.csv").param("ownerId", other.getId().toString())
                        .with(user(first)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=passhalo-marketing-contacts.csv"))
                .andReturn().getResponse();
        String csv = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFemail,first_name,last_name,consent_at,consent_expires_at\r\n"));
        assertTrue(csv.contains("\"visible@example.test\",\"Ada\",\"Test\""));
        assertFalse(csv.contains("other@example.test"));
        assertFalse(csv.contains("inactive@example.test"));
        assertFalse(csv.contains("expired@example.test"));
        assertFalse(csv.contains("unknown-expiry@example.test"));
        assertFalse(csv.contains("legacy@example.test"));
        assertFalse(csv.contains("ciphertext"));
    }

    @Test
    void revocationImmediatelyExcludesPreviouslyExportedContact() throws Exception {
        User owner = owner("export-revoke@example.test", Role.ADMIN);
        String token = marketing.registerConsent(owner, 1L, "Ada", "Test", "revoked@example.test");
        assertTrue(csv(owner).contains("revoked@example.test"));
        marketing.unsubscribe(token);
        assertFalse(csv(owner).contains("revoked@example.test"));
    }

    @Test
    void csvPreservesUnicodeQuotesAndNewlinesAndNeutralizesSpreadsheetFormulas() throws Exception {
        User owner = owner("export-csv@example.test", Role.ADMIN);
        subscriber(owner, "unicode@example.test", "Nicolò, \"Test\"\nSeconda riga", true, LocalDateTime.now().plusMonths(1));
        subscriber(owner, "formula@example.test", "  =HYPERLINK(\"https://example.test\")", true, LocalDateTime.now().plusMonths(1));
        String csv = csv(owner);
        assertTrue(csv.contains("\"Nicolò, \"\"Test\"\"\nSeconda riga\""));
        assertTrue(csv.contains("\"'  =HYPERLINK(\"\"https://example.test\"\")\""));
    }

    @Test
    void staffAndAnonymousUsersCannotDownloadContacts() throws Exception {
        User staff = owner("export-staff@example.test", Role.STAFF);
        mockMvc.perform(get("/marketing/contacts.csv").with(user(staff))).andExpect(status().isForbidden());
        var anonymous = mockMvc.perform(get("/marketing/contacts.csv")).andReturn().getResponse();
        assertTrue(anonymous.getStatus() == 401 || anonymous.getStatus() == 403);
    }

    private String csv(User owner) throws Exception {
        return new String(mockMvc.perform(get("/marketing/contacts.csv").with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private MarketingSubscriber subscriber(User owner, String email, String name, boolean active, LocalDateTime expiry) {
        MarketingSubscriber subscriber = new MarketingSubscriber();
        subscriber.setOwner(owner);
        subscriber.setEmailLookupHash(crypto.emailLookupHash(email));
        subscriber.setEmailCiphertext(crypto.encrypt(email));
        subscriber.setNameCiphertext(crypto.encrypt(name));
        subscriber.setSurnameCiphertext(crypto.encrypt("Test"));
        subscriber.setActive(active);
        subscriber.setExpiresAt(expiry);
        return subscribers.saveAndFlush(subscriber);
    }

    private User owner(String email, Role role) {
        User owner = new User();
        owner.setName("Test"); owner.setSurname("Export"); owner.setEmail(email);
        owner.setPassword("test-password"); owner.setRole(role);
        return users.saveAndFlush(owner);
    }
}
