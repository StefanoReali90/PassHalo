package org.spring.passhalo.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public record EventRequest(
        @NotBlank
        @Size(max = 255)
        String name,
        @NotBlank
        @Size(max = 4000)
        String description,
        @NotBlank
        @Size(max = 255)
        String location,
        @NotNull
        LocalDateTime start,
        @NotNull
        LocalDateTime end,
        @NotBlank
        @Size(max = 2048)
        String imageUrl,
        @NotNull
        @Positive
        Integer totalTickets,
        @NotNull
        @Positive
        Double normalPrice,
        @NotNull
        @Positive
        Double bookingPrice,
        @Size(max=2048)
        String videoUrl,
        @Size(max = 12)
        List<@Valid EventFaqDTO> faqs


) {
}
