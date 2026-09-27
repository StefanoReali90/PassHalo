package org.spring.passhalo.user.config;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAccountIsolationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void organizersCanReadTheirOwnProfileButNotGlobalUserDirectoryOrDeleteOthers() throws Exception {
        User first = saveUser("first-organizer@example.test");
        User second = saveUser("second-organizer@example.test");

        mockMvc.perform(get("/user/me").with(user(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(first.getEmail()));
        mockMvc.perform(get("/user").with(user(first))).andExpect(status().isForbidden());
        mockMvc.perform(get("/user/search").param("email", second.getEmail()).with(user(first)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/user/{id}", second.getId()).with(user(first)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/user/{id}", second.getId()).with(user(first)))
                .andExpect(status().isForbidden());
        assertTrue(userRepository.existsById(second.getId()));
    }

    @Test
    void passwordChangeUsesAuthenticatedAccountWithoutAnAccountId() throws Exception {
        User first = saveUser("password-owner@example.test");
        User second = saveUser("password-other@example.test");
        String body = """
                {"oldPassword":"old-password","newPassword":"new-password-123",
                 "confirmationPassword":"new-password-123"}
                """;

        mockMvc.perform(patch("/user/{id}/change-password", second.getId()).with(user(first))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/user/me/change-password").with(user(first))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        assertTrue(passwordEncoder.matches("new-password-123",
                userRepository.findById(first.getId()).orElseThrow().getPassword()));
        assertTrue(passwordEncoder.matches("old-password",
                userRepository.findById(second.getId()).orElseThrow().getPassword()));
    }

    private User saveUser(String email) {
        User user = new User();
        user.setName("Test");
        user.setSurname("Organizer");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("old-password"));
        user.setRole(Role.ADMIN);
        return userRepository.save(user);
    }
}
