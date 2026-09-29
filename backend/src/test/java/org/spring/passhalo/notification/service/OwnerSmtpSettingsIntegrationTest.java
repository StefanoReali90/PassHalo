package org.spring.passhalo.notification.service;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.notification.repository.OwnerSmtpSettingsRepository;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnerSmtpSettingsIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private OwnerSmtpSettingsRepository repository;
    @Autowired private OwnerSmtpSettingsService service;
    @MockitoBean private SmtpHostValidator hostValidator;

    @Test
    void credentialsAreEncryptedAndScopedToTheAuthenticatedOrganizer() throws Exception {
        User first = saveUser("smtp-first@example.test", Role.ADMIN);
        User second = saveUser("smtp-second@example.test", Role.ADMIN);
        User staff = saveUser("smtp-staff@example.test", Role.STAFF);

        mockMvc.perform(put("/account/smtp").with(user(first))
                        .contentType(MediaType.APPLICATION_JSON).content(settings("first-secret", "first@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fromEmail").value("first@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(put("/account/smtp").with(user(second))
                        .contentType(MediaType.APPLICATION_JSON).content(settings("second-secret", "second@example.com")))
                .andExpect(status().isOk());

        assertFalse(repository.findByOwnerId(first.getId()).orElseThrow()
                .getPasswordCiphertext().contains("first-secret"));
        assertNotEquals(repository.findByOwnerId(first.getId()).orElseThrow().getPasswordCiphertext(),
                repository.findByOwnerId(second.getId()).orElseThrow().getPasswordCiphertext());
        mockMvc.perform(get("/account/smtp").with(user(first)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fromEmail").value("first@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get("/account/smtp").with(user(second)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fromEmail").value("second@example.com"));
        mockMvc.perform(get("/account/smtp").with(user(staff))).andExpect(status().isForbidden());
        mockMvc.perform(put("/account/smtp").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON).content(settings("staff-secret", "staff@example.com")))
                .andExpect(status().isForbidden());

        JavaMailSenderImpl firstSender = (JavaMailSenderImpl) service.route(first.getId()).orElseThrow().sender();
        JavaMailSenderImpl secondSender = (JavaMailSenderImpl) service.route(second.getId()).orElseThrow().sender();
        assertEquals("first-secret", firstSender.getPassword());
        assertEquals("second-secret", secondSender.getPassword());

        mockMvc.perform(delete("/account/smtp").with(user(first))).andExpect(status().isNoContent());
        assertTrue(repository.findByOwnerId(first.getId()).isEmpty());
        assertTrue(repository.findByOwnerId(second.getId()).isPresent());
    }

    @Test
    void savingWithoutNewPasswordKeepsExistingSecret() throws Exception {
        User owner = saveUser("smtp-rotate@example.test", Role.ADMIN);
        mockMvc.perform(put("/account/smtp").with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(settings("original-secret", "old@example.com")))
                .andExpect(status().isOk());
        mockMvc.perform(put("/account/smtp").with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(settings("", "new@example.com")))
                .andExpect(status().isOk());
        assertEquals("original-secret", ((JavaMailSenderImpl) service.route(owner.getId())
                .orElseThrow().sender()).getPassword());
        assertEquals("new@example.com", service.status(owner.getEmail()).fromEmail());
    }

    private String settings(String password, String fromEmail) {
        return """
                {"host":"smtp-relay.brevo.com","port":587,"encryption":"STARTTLS",
                 "username":"smtp-login@example.com","password":"%s",
                 "fromEmail":"%s","fromName":"Eventi Test"}
                """.formatted(password, fromEmail);
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setName("Test");
        user.setSurname("User");
        user.setEmail(email);
        user.setPassword("test-password");
        user.setRole(role);
        return userRepository.save(user);
    }
}
