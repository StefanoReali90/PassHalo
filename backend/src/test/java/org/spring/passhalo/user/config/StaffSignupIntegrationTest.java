package org.spring.passhalo.user.config;

import org.junit.jupiter.api.Test;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StaffSignupIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Test
    void staffCannotCreateAnAccount() throws Exception {
        mockMvc.perform(post("/user/staff-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"surname\":\"Rossi\",\"email\":\"ADA@Example.Test\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/user/staff-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"surname\":\"Rossi\",\"email\":\"ada@example.test\",\"password\":\"test-password-123\"}"))
                .andExpect(status().isForbidden());
        assertTrue(userRepository.findByEmail("ada@example.test").isEmpty());
    }
}
