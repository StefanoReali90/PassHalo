package org.spring.passhalo.event.controller;

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
class EventCreationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;

    @Test
    void savesLongDescriptionAndImageUrlWithinEditorLimits() throws Exception {
        User owner = saveOwner();
        String description = "D".repeat(350);
        String imageUrl = "https://example.test/" + "x".repeat(330);

        mockMvc.perform(post("/events/").with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson(description, imageUrl)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(description))
                .andExpect(jsonPath("$.imageUrl").value(imageUrl));

        mockMvc.perform(get("/events/"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsDescriptionBeyondEditorLimitBeforeDatabaseInsert() throws Exception {
        User owner = saveOwner();

        mockMvc.perform(post("/events/").with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("D".repeat(4001), "https://example.test/image.jpg")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void allowsTheSamePriceForBookingAndAtTheDoor() throws Exception {
        User owner = saveOwner();

        mockMvc.perform(post("/events/").with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Same price event", "https://example.test/image.jpg", 10.0, 10.0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.normalPrice").value(10.0))
                .andExpect(jsonPath("$.bookingPrice").value(10.0));
    }

    private User saveOwner() {
        User owner = new User();
        owner.setName("Event");
        owner.setSurname("Owner");
        owner.setEmail("event-owner@example.test");
        owner.setPassword("unused-test-password");
        owner.setRole(Role.ADMIN);
        return userRepository.saveAndFlush(owner);
    }

    private String eventJson(String description, String imageUrl) {
        return eventJson(description, imageUrl, 15.0, 10.0);
    }

    private String eventJson(String description, String imageUrl, double normalPrice, double bookingPrice) {
        return """
                {
                  "name": "Long content event",
                  "description": "%s",
                  "location": "Venue",
                  "start": "2030-01-01T20:00:00",
                  "end": "2030-01-02T02:00:00",
                  "imageUrl": "%s",
                  "totalTickets": 100,
                  "normalPrice": %s,
                  "bookingPrice": %s
                }
                """.formatted(description, imageUrl, normalPrice, bookingPrice);
    }
}
