package org.spring.passhalo.marketing.controller;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BrevoConnectionAccessIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @Test
    void onlyAdminCanSeeTheirOwnConnectionStatus() throws Exception {
        User valerio = saveUser("brevo-valerio@example.test", Role.ADMIN);
        User silvio = saveUser("brevo-silvio@example.test", Role.ADMIN);
        User staff = saveUser("brevo-staff@example.test", Role.STAFF);

        mockMvc.perform(get("/marketing/brevo").with(user(valerio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
        mockMvc.perform(get("/marketing/brevo").with(user(silvio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false));
        mockMvc.perform(get("/marketing/brevo").with(user(staff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/marketing/brevo").with(user(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiKey\":\"secret\",\"listId\":1}"))
                .andExpect(status().isForbidden());
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setName("Test");
        user.setSurname("Brevo");
        user.setEmail(email);
        user.setPassword("hashed-test-password");
        user.setRole(role);
        return userRepository.save(user);
    }
}
