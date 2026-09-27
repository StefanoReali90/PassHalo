package org.spring.passhalo.event.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.booking.exception.EventFinishedException;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.booking.service.BookingService;
import org.spring.passhalo.event.dto.EventRequest;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.exception.AccessDeniedException;
import org.spring.passhalo.event.exception.InvalidDateException;
import org.spring.passhalo.event.exception.InvalidPriceException;
import org.spring.passhalo.event.mapper.EventMapper;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.spring.passhalo.user.service.AuthEventService;
import org.spring.passhalo.user.service.EventJoinService;
import org.spring.passhalo.user.service.StaffAccessService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {
    @Mock private EventRepository eventRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private EventMapper eventMapper;
    @Mock private BookingService bookingService;
    @Mock private AuthEventService authEventService;
    @Mock private EventMembershipRepository membershipRepository;
    @Mock private EventJoinService eventJoinService;
    @Mock private StaffAccessService staffAccessService;
    @InjectMocks private EventService service;

    @Test
    void eventMustEndAfterItStarts() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);

        assertThrows(InvalidDateException.class,
                () -> service.createEvent(request(start, start, 10.0, 15.0), owner()));
        assertThrows(InvalidDateException.class,
                () -> service.createEvent(request(start, start.minusHours(1), 10.0, 15.0), owner()));

        verifyNoInteractions(eventMapper, eventRepository);
    }

    @Test
    void reducedPriceMustBeLowerThanNormalPrice() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);

        assertThrows(InvalidPriceException.class,
                () -> service.createEvent(request(start, start.plusHours(4), 15.0, 15.0), owner()));

        verifyNoInteractions(eventMapper, eventRepository);
    }

    @Test
    void closingEventExpiresRequestsAndAnonymizesBookings() {
        User owner = owner();
        Event event = event(owner);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        service.closeEvent(event.getId(), owner);

        verify(authEventService).checkUserAccess(event.getId(), owner.getId());
        verify(eventRepository).save(event);
        verify(eventJoinService).expireRequestsForEvent(event.getId());
        verify(staffAccessService).expireForEvent(event.getId());
        verify(bookingService).anonymizeBookingsByEventId(event.getId());
        assertEquals(EventState.FINISHED, event.getEventState());
    }

    @Test
    void unauthorizedUserCannotCloseOrCleanUpEvent() {
        User owner = owner();
        Event event = event(owner);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        doThrow(new AccessDeniedException("denied"))
                .when(authEventService).checkUserAccess(event.getId(), owner.getId());

        assertThrows(AccessDeniedException.class, () -> service.closeEvent(event.getId(), owner));
        assertEquals(EventState.WAITING, event.getEventState());
        verifyNoInteractions(eventJoinService, bookingService);
    }

    @Test
    void alreadyClosedEventCannotBeClosedTwice() {
        User owner = owner();
        Event event = event(owner);
        event.setEventState(EventState.FINISHED);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThrows(EventFinishedException.class, () -> service.closeEvent(event.getId(), owner));

        verifyNoInteractions(eventJoinService, bookingService);
    }

    private User owner() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    private Event event(User owner) {
        Event event = new Event();
        event.setId(10L);
        event.setUser(owner);
        event.setEventState(EventState.WAITING);
        return event;
    }

    private EventRequest request(LocalDateTime start, LocalDateTime end,
                                 double bookingPrice, double normalPrice) {
        return new EventRequest("Test event", "Description", "Venue", start, end,
                "https://example.test/image.jpg", 100, normalPrice, bookingPrice, null, null);
    }
}
