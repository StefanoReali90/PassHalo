package org.spring.passhalo.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.booking.exception.EventFinishedException;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.booking.service.BookingService;
import org.spring.passhalo.event.dto.EventDashboardResponse;
import org.spring.passhalo.event.dto.EventRequest;
import org.spring.passhalo.event.dto.EventResponse;
import org.spring.passhalo.event.dto.MyEventResponse;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.entity.EventFaq;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.exception.AccessDeniedException;
import org.spring.passhalo.event.exception.EventNotFoundException;
import org.spring.passhalo.event.exception.InvalidDateException;
import org.spring.passhalo.event.exception.InvalidPriceException;
import org.spring.passhalo.event.mapper.EventMapper;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.spring.passhalo.user.service.AuthEventService;
import org.spring.passhalo.user.service.EventJoinService;
import org.spring.passhalo.user.service.StaffAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    @Value("${app.time-zone}")
    private String timezone;

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    private final EventMapper eventMapper;
    private final BookingService bookingService;
    private final AuthEventService authEventService;
    private final EventMembershipRepository eventMembershipRepository;
    private final EventJoinService eventJoinService;
    private final StaffAccessService staffAccessService;

    private void validateEventDates(EventRequest event) {
        if (!event.start().isBefore(event.end())) {
            throw new InvalidDateException("Start date must be before end date");
        }
        if (!(event.bookingPrice() < event.normalPrice())) {
            throw new InvalidPriceException("Booking price must be less than normal price");
        }
    }

    @Transactional
    public EventResponse createEvent(EventRequest event, User admin) {
        validateEventDates(event);
        Event newEvent = eventMapper.toEntity(event);
        newEvent.setUser(admin);
        Event savedEvent = eventRepository.save(newEvent);
        log.info("Event created successfully");
        return eventMapper.toResponse(savedEvent);
    }
    @Transactional
    public EventResponse updateEvent(EventRequest event, Long Id, User admin) {
        Event existingEvent = eventRepository.findById(Id).orElseThrow(() -> new EventNotFoundException("Event not found with id: " + Id));
        authEventService.checkUserAccess(Id, admin.getId());
        if(existingEvent.getEventState().equals(EventState.FINISHED)) {
            throw new EventFinishedException("Cannot update a finished event");
        }
        validateEventDates(event);
        existingEvent.setName(event.name());
        existingEvent.setDescription(event.description());
        existingEvent.setLocation(event.location());
        existingEvent.setStartDateTime(event.start());
        existingEvent.setEndDateTime(event.end());
        existingEvent.setImageUrl(event.imageUrl());
        existingEvent.setTotalTickets(event.totalTickets());
        existingEvent.setNormalPrice(event.normalPrice());
        existingEvent.setBookingPrice(event.bookingPrice());
        existingEvent.setVideoUrl(event.videoUrl());
        if (event.faqs() != null) {
            List<EventFaq> eventFaqList = existingEvent.getFaqs();
            eventFaqList.clear();
            for (var faqDTO : event.faqs()) {
                EventFaq faq = new EventFaq();
                faq.setQuestion(faqDTO.question());
                faq.setAnswer(faqDTO.answer());
                eventFaqList.add(faq);
            }
        }
        Event updatedEvent = eventRepository.save(existingEvent);
        return eventMapper.toResponse(updatedEvent);

    }
    @Transactional(readOnly = true)
    public EventResponse getEventById(Long id) {
        Event event = eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException("Event not found with id: " + id));
        return eventMapper.toResponse(event);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> getAllEvents() {
        List<Event> events = eventRepository.findAll();
        return events.stream().map(eventMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<MyEventResponse> getEventsByUser(Long userId) {
        List<Event> ownedEvents = eventRepository.findByUserId(userId);
        List< EventMembership> memberships = eventMembershipRepository.findByCollaboratorIdAndMembershipState(userId, MembershipState.ACTIVE);
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        List<MyEventResponse> results = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        for (Event event : ownedEvents) {
            if (seenIds.add(event.getId())) {
                results.add(new MyEventResponse(eventMapper.toResponse(event), EventRole.EVENT_ADMIN, true));
            }
        }
        for (EventMembership membership : memberships) {
            if (now.isBefore(membership.getValidFrom()) ||
                    (membership.getValidUntil() != null && !now.isBefore(membership.getValidUntil()))) {
                continue;
            }
            Event event = membership.getEvent();
            if (membership.getRole() == EventRole.STAFF &&
                    (event.getEventState() == EventState.FINISHED || !now.isBefore(event.getEndDateTime()))) {
                continue;
            }
            if (seenIds.add(event.getId())) {
                results.add(new MyEventResponse(eventMapper.toResponse(event), membership.getRole(), false));
            }
        }
        return results;
    }

    @Transactional
    public void deleteEventById(Long id, User admin) {
        Event event = eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException("Event not found with id: " + id));
        authEventService.checkUserAccess(id, admin.getId());
        if(event.getEventState().equals(EventState.FINISHED)) {
            throw new EventFinishedException("Cannot delete a finished event");
        }
        staffAccessService.deleteForEvent(id);
        eventRepository.deleteById(id);
    }
    @Transactional
    public void incrementWalkInCount(Long eventId, User admin) {
        Event event = eventRepository.findDistinctById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));
        authEventService.checkStaffAccess(eventId, admin.getId());
        if(event.getEventState().equals(EventState.FINISHED)) {
            throw new EventFinishedException("Cannot register walk-in attendee for a finished event");
        }
        event.setWalkInCount(event.getWalkInCount() + 1);
        log.info("Walk-in attendee registered");
        eventRepository.save(event);
    }

    @Transactional
    public void decrementWalkInCount(Long eventId, User admin) {
        Event event = eventRepository.findDistinctById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));
        authEventService.checkStaffAccess(eventId, admin.getId());
        if(event.getEventState().equals(EventState.FINISHED)) {
            throw new EventFinishedException("Cannot register walk-in attendee for a finished event");
        }
        if (event.getWalkInCount() > 0) {
            event.setWalkInCount(event.getWalkInCount() - 1);
            eventRepository.save(event);
        }
    }

    @Transactional(readOnly = true)
    public EventDashboardResponse getEventDashboardData(Long eventId, User admin) {
        double attendanceRate;
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));
        authEventService.checkUserAccess(eventId, admin.getId());
            long totalBookings = bookingRepository.countByEventIdAndBookingStatusNot(eventId, BookingStatus.CANCELLED);
            long checkedInCount = bookingRepository.countByEventIdAndBookingStatus(eventId, BookingStatus.VALIDATED);
            long noShowCount = totalBookings - checkedInCount;
            if (totalBookings > 0) {
                attendanceRate = (checkedInCount * 100.0) / totalBookings;
            } else {
                attendanceRate = 0;
            }
            double estimatedBookingRenueve = totalBookings * event.getBookingPrice();
            long totalAttendees = checkedInCount + event.getWalkInCount();
            double normalPrice = event.getNormalPrice() != null ? event.getNormalPrice() : 0.0;
            double totalRevenue = (checkedInCount * event.getBookingPrice()) + (event.getWalkInCount() * normalPrice);
            return new EventDashboardResponse(
                    eventId ,
                    event.getName(),
                    event.getTotalTickets(),
                    totalBookings,
                    checkedInCount,
                    noShowCount,
                    attendanceRate,
                    estimatedBookingRenueve,
                    event.getWalkInCount(),
                    totalAttendees,
                    totalRevenue);

    }

    @Transactional
    public void closeEvent(Long eventId, User admin) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new EventNotFoundException("Event not found with id: " + eventId));
        authEventService.checkUserAccess(eventId, admin.getId());
        if (event.getEventState().equals(EventState.FINISHED)) {
            throw new EventFinishedException("Event is already closed with id: " + eventId);
        }
        event.setEventState(EventState.FINISHED);
        eventRepository.save(event);
        eventJoinService.expireRequestsForEvent(eventId);
        staffAccessService.expireForEvent(eventId);
        log.info("Event closed and access credentials expired");
        bookingService.anonymizeBookingsByEventId(eventId);
    }

}
