package org.spring.passhalo.event.dto;

import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.user.enums.EventRole;

import java.time.LocalDateTime;
import java.util.List;

public record MyEventResponse(
        Long id,
        String name,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        Double normalPrice,
        Double bookingPrice,
        int totalTickets,
        String description,
        String imageUrl,
        String location,
        EventState eventState,
        String videoUrl,
        List<EventFaqDTO> faqs,
        EventRole role,
        boolean owner
) {
    public MyEventResponse(EventResponse event, EventRole role, boolean owner) {
        this(event.id(), event.name(), event.startDateTime(), event.endDateTime(),
                event.normalPrice(), event.bookingPrice(), event.totalTickets(),
                event.description(), event.imageUrl(), event.location(),
                event.eventState(), event.videoUrl(), event.faqs(), role, owner);
    }
}
