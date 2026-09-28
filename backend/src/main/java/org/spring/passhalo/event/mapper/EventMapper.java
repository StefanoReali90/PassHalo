package org.spring.passhalo.event.mapper;

import org.spring.passhalo.event.dto.EventFaqDTO;
import org.spring.passhalo.event.dto.EventRequest;
import org.spring.passhalo.event.dto.EventResponse;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.entity.EventFaq;
import org.spring.passhalo.event.enums.EventState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EventMapper {

    public Event toEntity(EventRequest eventRequest) {
        Event event = new Event();
        event.setName(eventRequest.name());
        event.setDescription(eventRequest.description());
        event.setLocation(eventRequest.location());
        event.setStartDateTime(eventRequest.start());
        event.setEndDateTime(eventRequest.end());
        event.setImageUrl(eventRequest.imageUrl());
        event.setTotalTickets(eventRequest.totalTickets());
        event.setNormalPrice(eventRequest.normalPrice());
        event.setBookingPrice(eventRequest.bookingPrice());
        event.setEventState(EventState.WAITING);
        event.setVideoUrl(eventRequest.videoUrl());
        List<EventFaq> eventFaqList = new ArrayList<>();
        if (eventRequest.faqs() != null) {
            for (var faqDTO : eventRequest.faqs()) {
                EventFaq faq = new EventFaq();
                faq.setQuestion(faqDTO.question());
                faq.setAnswer(faqDTO.answer());
                eventFaqList.add(faq);
            }
        }
        event.setFaqs(eventFaqList);
        return event;
    }

    public EventResponse toResponse(Event event) {
        List<EventFaqDTO> faqs = new ArrayList<>();
        if (event.getFaqs() != null) {
            for (var faq : event.getFaqs()) {
                EventFaqDTO faqDTO = new EventFaqDTO(faq.getQuestion(), faq.getAnswer());
                faqs.add(faqDTO);
            }
        }
        return new EventResponse(
                event.getId(),
                event.getName(),
               event.getStartDateTime(),
                event.getEndDateTime(),
                event.getNormalPrice(),
                event.getBookingPrice(),
                event.getTotalTickets(),
                event.getDescription(),
                event.getImageUrl(),
                event.getLocation(),
                event.getEventState(),
                event.getVideoUrl(),
                faqs,
                event.getUser().getName() + " " + event.getUser().getSurname()

        );
    }
}
