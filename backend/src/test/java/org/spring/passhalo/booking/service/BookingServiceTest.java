package org.spring.passhalo.booking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.booking.dto.BookingRequest;
import org.spring.passhalo.booking.dto.BookingResponse;
import org.spring.passhalo.booking.dto.CheckInResponse;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.booking.exception.*;
import org.spring.passhalo.booking.mapper.BookingMapper;
import org.spring.passhalo.booking.repository.BookingRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.exception.AccessDeniedException;
import org.spring.passhalo.event.exception.EventNotFoundException;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.marketing.service.MarketingService;
import org.spring.passhalo.notification.service.BookingConfirmationQueueService;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.service.AuthEventService;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {

    @InjectMocks
    private BookingService bookingService;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PiiCryptoService cryptoService;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private QrCodeService qrCodeService;

    @Mock
    private BookingConfirmationQueueService bookingConfirmationQueueService;

    @Mock
    private MarketingService marketingService;

    @Mock
    private AuthEventService authEventService;

    @Test
    void testCheckIn() {
        UUID bookingId = UUID.randomUUID();
        User user = new User();
        user.setId(7L);
        Event event = new Event();
        event.setId(11L);
        event.setEventState(EventState.WAITING);
        Booking booking = new Booking();
        CheckInResponse expectedResponse = new CheckInResponse("Concerto");
        booking.setUuid(bookingId);
        booking.setBookingStatus(BookingStatus.CREATED);
        booking.setCheckInDateTime(null);
        booking.setEvent(event);
        when(bookingRepository.findForCheckInByUuid(bookingId)).thenReturn(Optional.of(booking));
        when(bookingMapper.toCheckInResponse(booking)).thenReturn(expectedResponse);
        CheckInResponse response = bookingService.checkInBooking(bookingId, user, org.spring.passhalo.booking.enums.PaymentMethod.CASH);
        assertEquals(expectedResponse, response);
        assertNotNull(response);
        assertEquals("Concerto", response.eventName());
        assertEquals(BookingStatus.VALIDATED, booking.getBookingStatus());
        assertNotNull(booking.getCheckInDateTime());
        verify(bookingRepository, times(1)).findForCheckInByUuid(bookingId);
        verify(authEventService).checkStaffAccess(event.getId(), user.getId());

    }

    @Test
    void checkIn_WhenAccessDenied_DoesNotValidateBooking() {
        UUID bookingId = UUID.randomUUID();
        User user = new User();
        user.setId(7L);
        Event event = new Event();
        event.setId(11L);
        event.setEventState(EventState.WAITING);
        Booking booking = new Booking();
        booking.setUuid(bookingId);
        booking.setBookingStatus(BookingStatus.CREATED);
        booking.setEvent(event);
        when(bookingRepository.findForCheckInByUuid(bookingId)).thenReturn(Optional.of(booking));
        doThrow(new AccessDeniedException("Access denied"))
                .when(authEventService).checkStaffAccess(event.getId(), user.getId());

        assertThrows(AccessDeniedException.class, () -> bookingService.checkInBooking(bookingId, user, org.spring.passhalo.booking.enums.PaymentMethod.CASH));

        assertEquals(BookingStatus.CREATED, booking.getBookingStatus());
        assertNull(booking.getCheckInDateTime());
        verifyNoInteractions(bookingMapper);
    }

    @Test
    void testCheckIn_WhenAlreadyValidated_ShouldThrowException() {
        Event event = new Event();
        User user = new User();
        event.setId(11L);
        user.setId(7L);
        event.setEventState(EventState.WAITING);
        UUID bookingId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setUuid(bookingId);
        booking.setBookingStatus(BookingStatus.VALIDATED);
        booking.setEvent(event);
        when(bookingRepository.findForCheckInByUuid(bookingId)).thenReturn(Optional.of(booking));
        assertThrows(AlreadyValidatedException.class, () -> bookingService.checkInBooking(bookingId, user, org.spring.passhalo.booking.enums.PaymentMethod.CASH));
        verify(bookingRepository, times(1)).findForCheckInByUuid(bookingId);
        verify(authEventService).checkStaffAccess(event.getId(), user.getId());
    }

    @Test
    void checkin_bookingNotFound_ShouldThrowException() {
        UUID uuid = UUID.randomUUID();
        User user = new User();
        user.setId(7L);
        when(bookingRepository.findForCheckInByUuid(uuid)).thenReturn(Optional.empty());
        assertThrows(BookingNotFoundException.class, () -> bookingService.checkInBooking(uuid, user, org.spring.passhalo.booking.enums.PaymentMethod.CASH));
        verify(bookingRepository, times(1)).findForCheckInByUuid(uuid);
        verifyNoInteractions(authEventService);
    }

    @Test
    void checkin_bookingCanceled_ShouldThrowException() {
        Event event = new Event();
        User user = new User();
        event.setId(11L);
        user.setId(7L);
        event.setEventState(EventState.WAITING);
        UUID uuid = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setUuid(uuid);
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setEvent(event);
        when(bookingRepository.findForCheckInByUuid(uuid)).thenReturn(Optional.of(booking));
        assertThrows(AlreadyCanceledException.class, () -> bookingService.checkInBooking(uuid, user, org.spring.passhalo.booking.enums.PaymentMethod.CASH));
        verify(bookingRepository, times(1)).findForCheckInByUuid(uuid);
        verify(authEventService).checkStaffAccess(event.getId(), user.getId());
    }

    @Test
    void createBooking() {
        BookingRequest request = new BookingRequest("Mario", "Rossi", "mario.rossi@example.com", "1234567890", 1L, true);
        Booking booking = new Booking();
        booking.setUuid(UUID.randomUUID());
        booking.setName("Mario");
        booking.setSurname("Rossi");
        booking.setEmail("mario.rossi@example.com");
        booking.setPhone("1234567890");
        booking.setEvent(new Event());
        booking.setBookingStatus(BookingStatus.CREATED);
        BookingResponse expectedResponse = mock(BookingResponse.class);
        Event event = new Event();
        event.setId(1L);
        event.setName("Concerto");
        event.setTotalTickets(300);
        event.setEndDateTime(LocalDateTime.now().plusDays(1));
        when(bookingMapper.toEntity(request)).thenReturn(new Booking());
        when(eventRepository.findDistinctById(1L)).thenReturn(Optional.of(event));
        when(cryptoService.emailLookupHash("mario.rossi@example.com")).thenReturn("v1:lookup-hash");
        when(bookingRepository.existsByEventIdAndEmailLookupHashAndBookingStatusNot(1L, "v1:lookup-hash", BookingStatus.CANCELLED)).thenReturn(false);
        when(bookingRepository.existsByEventIdAndEmailIgnoreCaseAndBookingStatusNot(1L, "mario.rossi@example.com", BookingStatus.CANCELLED)).thenReturn(false);
        when(bookingRepository.countByEventIdAndBookingStatusNot(1L, BookingStatus.CANCELLED)).thenReturn(0L);
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking);
        when(qrCodeService.createQrCode(anyString())).thenReturn("mock-qr-base64");
        when(bookingMapper.toResponse(eq(booking), anyString())).thenReturn(expectedResponse);
        BookingResponse response = bookingService.createBooking(request);
        assertNotNull(response);
        assertEquals(expectedResponse, response);
        verify(bookingRepository, times(1)).save(any(Booking.class));
        verify(bookingConfirmationQueueService).enqueue(eq(booking), any());
        verify(marketingService, times(1)).registerConsent(any(), any(), any(), any(), any());

    }

    @Test
    void createBooking_WhenEventNotFound_ShouldThrowException() {
        BookingRequest request = new BookingRequest("Mario", "Rossi", "mario.rossi@example.com", "1234567890", 1L, true);
        when(bookingMapper.toEntity(request)).thenReturn(new Booking());
        when(eventRepository.findDistinctById(1L)).thenReturn(Optional.empty());
        assertThrows(EventNotFoundException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void createBooking_WhenAlreadyBooked_ShouldThrowException() {
        BookingRequest request = new BookingRequest("Mario", "Rossi", "mario.rossi@example.com", "1234567890", 1L, true);
        when(bookingMapper.toEntity(request)).thenReturn(new Booking());
        Event event = new Event();
        event.setEndDateTime(LocalDateTime.now().plusDays(1));
        when(eventRepository.findDistinctById(1L)).thenReturn(Optional.of(event));
        when(cryptoService.emailLookupHash("mario.rossi@example.com")).thenReturn("v1:lookup-hash");
        when(bookingRepository.existsByEventIdAndEmailLookupHashAndBookingStatusNot(1L, "v1:lookup-hash", BookingStatus.CANCELLED)).thenReturn(true);
        assertThrows(AlreadyBookedException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void createBooking_WhenNoTicketsAvailable_ShouldThrowException() {
        BookingRequest request = new BookingRequest("Mario", "Rossi", "mario.rossi@example.com", "1234567890", 1L, true);
        Event event = new Event();
        event.setId(1L);
        event.setTotalTickets(100);
        event.setEndDateTime(LocalDateTime.now().plusDays(1));
        when(bookingMapper.toEntity(request)).thenReturn(new Booking());
        when(eventRepository.findDistinctById(1L)).thenReturn(Optional.of(event));
        when(cryptoService.emailLookupHash("mario.rossi@example.com")).thenReturn("v1:lookup-hash");
        when(bookingRepository.existsByEventIdAndEmailLookupHashAndBookingStatusNot(1L, "v1:lookup-hash", BookingStatus.CANCELLED)).thenReturn(false);
        when(bookingRepository.existsByEventIdAndEmailIgnoreCaseAndBookingStatusNot(1L, "mario.rossi@example.com", BookingStatus.CANCELLED)).thenReturn(false);
        when(bookingRepository.countByEventIdAndBookingStatusNot(1L, BookingStatus.CANCELLED)).thenReturn(300L);
        assertThrows(NoTicketException.class, () -> bookingService.createBooking(request));
    }

    @Test
    void deleteBooking() {
        UUID bookingUuid = UUID.randomUUID();
        Booking booking = new Booking();
        User user = new User();
        user.setId(1L);
        Event event = new Event();
        event.setId(11L);
        event.setUser(user);
        booking.setEvent(event);
        booking.setId(8L);
        booking.setBookingStatus(BookingStatus.CREATED);
        booking.setUuid(bookingUuid);
        when(bookingRepository.findForCheckInByUuid(booking.getUuid())).thenReturn(Optional.of(booking));
        bookingService.deleteBooking(booking.getUuid(), user);
        assertEquals(BookingStatus.CANCELLED, booking.getBookingStatus());
        verify(bookingRepository, times(1)).findForCheckInByUuid(booking.getUuid());
        verify(authEventService).checkUserAccess(event.getId(), user.getId());
        verify(bookingConfirmationQueueService).discardForBooking(booking.getId());
    }

    @Test
    void createBooking_WhenEventHasEnded_ShouldThrowException() {
        BookingRequest request = new BookingRequest("Mario", "Rossi", "mario@example.test", null, 1L, false);
        Event event = new Event();
        event.setEndDateTime(LocalDateTime.now().minusMinutes(1));
        when(bookingMapper.toEntity(request)).thenReturn(new Booking());
        when(eventRepository.findDistinctById(1L)).thenReturn(Optional.of(event));

        assertThrows(EventFinishedException.class, () -> bookingService.createBooking(request));
        verifyNoInteractions(cryptoService, bookingConfirmationQueueService);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void deleteBooking_WhenAccessDenied_DoesNotCancelBooking() {
        UUID uuid = UUID.randomUUID();
        User outsider = new User();
        outsider.setId(7L);
        Event event = new Event();
        event.setId(11L);
        Booking booking = new Booking();
        booking.setUuid(uuid);
        booking.setEvent(event);
        booking.setBookingStatus(BookingStatus.CREATED);
        when(bookingRepository.findForCheckInByUuid(uuid)).thenReturn(Optional.of(booking));
        doThrow(new AccessDeniedException("denied"))
                .when(authEventService).checkUserAccess(event.getId(), outsider.getId());

        assertThrows(AccessDeniedException.class, () -> bookingService.deleteBooking(uuid, outsider));

        assertEquals(BookingStatus.CREATED, booking.getBookingStatus());
    }

    @Test
    void bookingDetailsAreNotMappedBeforeEventAccessCheck() {
        UUID uuid = UUID.randomUUID();
        User outsider = new User();
        outsider.setId(7L);
        Event event = new Event();
        event.setId(11L);
        Booking booking = new Booking();
        booking.setUuid(uuid);
        booking.setEvent(event);
        when(bookingRepository.findByUuid(uuid)).thenReturn(Optional.of(booking));
        doThrow(new AccessDeniedException("denied"))
                .when(authEventService).checkUserAccess(event.getId(), outsider.getId());

        assertThrows(AccessDeniedException.class, () -> bookingService.getBookingByUuid(uuid, outsider));

        verifyNoInteractions(bookingMapper, qrCodeService);
    }

    @Test
    void bookingSearchDoesNotReadPersonalDataWhenAccessIsDenied() {
        User outsider = new User();
        outsider.setId(7L);
        doThrow(new AccessDeniedException("denied"))
                .when(authEventService).checkUserAccess(11L, outsider.getId());

        assertThrows(AccessDeniedException.class,
                () -> bookingService.getBookingsByEventIdAndEmail(11L, "guest@example.test", outsider));

        verify(bookingRepository, never()).findAllByEventIdAndEmailIgnoreCase(anyLong(), anyString());
        verify(bookingRepository, never()).findAllByEventIdAndEmailLookupHash(anyLong(), anyString());
        verifyNoInteractions(cryptoService);
    }

    @Test
    void anonymizeBooking() {
        Long eventId = 1L;
        Booking booking = new Booking();
        booking.setBookingStatus(BookingStatus.CREATED);
        booking.setUuid(UUID.randomUUID());
        UUID originalUuid = booking.getUuid();
        when(bookingRepository.findAllByEventId(eventId)).thenReturn(List.of(booking));
        bookingService.anonymizeBookingsByEventId(eventId);
        assertNull(booking.getNameCiphertext());
        assertNull(booking.getSurnameCiphertext());
        assertNull(booking.getEmailCiphertext());
        assertNull(booking.getEmailLookupHash());
        assertNull(booking.getName());
        assertNull(booking.getSurname());
        assertNull(booking.getEmail());
        assertNotEquals(originalUuid, booking.getUuid());
        assertNull(booking.getPhone());
        verify(bookingRepository, times(1)).findAllByEventId(eventId);
    }

    @Test
    void testCheckIn_WhenEventFinished_ShouldThrowException() {
        UUID bookingId = UUID.randomUUID();
        User user  = new User();
        user.setId(7L);
        Event event = new Event();
        event.setId(11L);
        event.setEventState(EventState.FINISHED);
        Booking booking = new Booking();
        booking.setUuid(bookingId);
        booking.setBookingStatus(BookingStatus.CREATED);
        booking.setCheckInDateTime(null);
        booking.setEvent(event);
        when(bookingRepository.findForCheckInByUuid(bookingId)).thenReturn(Optional.of(booking));
        assertThrows(EventFinishedException.class, () -> bookingService.checkInBooking(bookingId, user, org.spring.passhalo.booking.enums.PaymentMethod.CASH));
        verify(bookingRepository, times(1)).findForCheckInByUuid(bookingId);
        verify(authEventService).checkStaffAccess(event.getId(), user.getId());
    }
}

