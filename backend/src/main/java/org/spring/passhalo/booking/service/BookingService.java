package org.spring.passhalo.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.spring.passhalo.booking.exception.EventFinishedException;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.service.AuthEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    @Value("${app.time-zone:Europe/Rome}")
    private String timeZone = "Europe/Rome";

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final PiiCryptoService cryptoService;
    private final EventRepository eventRepository;
    private final QrCodeService qrCodeService;
    private final BookingConfirmationQueueService bookingConfirmationQueueService;
    private final MarketingService marketingService;
    private final AuthEventService authEventService;

    @Transactional
    public BookingResponse createBooking(BookingRequest bookingRequest) {
        Booking booking = bookingMapper.toEntity(bookingRequest);
        // Serialize bookings for the same event before checking duplicate emails and capacity.
        Event event = eventRepository.findDistinctById(bookingRequest.eventId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + bookingRequest.eventId()));
        if (event.getEventState() == EventState.FINISHED ||
                !LocalDateTime.now(ZoneId.of(timeZone)).isBefore(event.getEndDateTime())) {
            throw new EventFinishedException("Le prenotazioni per questo evento sono chiuse.");
        }
        String emailLookupHash = cryptoService.emailLookupHash(bookingRequest.email());
        boolean alreadyBooked = bookingRepository.existsByEventIdAndEmailLookupHashAndBookingStatusNot(
                bookingRequest.eventId(), emailLookupHash, BookingStatus.CANCELLED)
                || bookingRepository.existsByEventIdAndEmailIgnoreCaseAndBookingStatusNot(
                bookingRequest.eventId(), bookingRequest.email().trim(), BookingStatus.CANCELLED);
        if (alreadyBooked) {
            throw new AlreadyBookedException("Booking already exists for this event and email");
        }
        if (bookingRepository.countByEventIdAndBookingStatusNot(event.getId(), BookingStatus.CANCELLED) >= event.getTotalTickets()) {
            throw new NoTicketException("No more tickets available for this event");
        }

        booking.setEvent(event);
        Booking savedBooking = bookingRepository.save(booking);
        String qrCode = qrCodeService.createQrCode(savedBooking.getUuid().toString());
        String unsubscribeToken = null;
        if (bookingRequest.marketingConsent()) {
            unsubscribeToken = marketingService.registerConsent(event.getUser(), event.getId(),
                    bookingRequest.name(), bookingRequest.surname(), bookingRequest.email());
        }
        bookingConfirmationQueueService.enqueue(savedBooking, unsubscribeToken);
        log.info("Prenotazione creata eventoId={}", event.getId());
        return bookingMapper.toResponse(savedBooking, qrCode);

    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingByUuid(UUID uuid, User admin) {
        Booking booking = bookingRepository.findByUuid(uuid)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        Event event = booking.getEvent();
        authEventService.checkUserAccess(event.getId(), admin.getId());
        return bookingMapper.toResponse(booking, qrCodeService.createQrCode(booking.getUuid().toString()));
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long bookingId, User admin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id: " + bookingId));
        Event event = booking.getEvent();
        authEventService.checkUserAccess(event.getId(), admin.getId());
        return bookingMapper.toResponse(booking, qrCodeService.createQrCode(booking.getUuid().toString()));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByEventId(Long eventId, User admin) {
        authEventService.checkUserAccess(eventId, admin.getId());
        List<Booking> bookings = bookingRepository.findAllByEventId(eventId);
        return bookings.stream()
                .map(booking -> bookingMapper.toResponse(booking, qrCodeService.createQrCode(booking.getUuid().toString())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByEmail(String email, User admin) {
        String emailLookupHash = cryptoService.emailLookupHash(email);
        List<Booking> bookings = distinctById(
                bookingRepository.findAllByEmailLookupHashAndEvent_User_Id(emailLookupHash, admin.getId()),
                bookingRepository.findAllByEmailIgnoreCaseAndEvent_User_Id(email.trim(), admin.getId()));
        return bookings.stream()
                .map(booking -> bookingMapper.toResponse(booking, qrCodeService.createQrCode(booking.getUuid().toString())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByEventIdAndEmail(Long eventId, String email, User admin) {
        authEventService.checkUserAccess(eventId, admin.getId());
        String emailLookupHash = cryptoService.emailLookupHash(email);
        List<Booking> bookings = distinctById(
                bookingRepository.findAllByEventIdAndEmailLookupHash(eventId, emailLookupHash),
                bookingRepository.findAllByEventIdAndEmailIgnoreCase(eventId, email.trim()));
        return bookings.stream()
                .map(booking -> bookingMapper.toResponse(booking, qrCodeService.createQrCode(booking.getUuid().toString())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings(User admin) {
        List<Booking> bookings = bookingRepository.findAllByEvent_User_Id(admin.getId());
        return bookings.stream()
                .map(booking -> bookingMapper.toResponse(booking, qrCodeService.createQrCode(booking.getUuid().toString())))
                .toList();
    }

    @Transactional
    public void deleteBooking(UUID uuid, User admin) {
        Booking booking = bookingRepository.findForCheckInByUuid(uuid)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        authEventService.checkUserAccess(booking.getEvent().getId(), admin.getId());
        if (booking.getEvent().getEventState() == EventState.FINISHED) {
            throw new EventFinishedException("Event is finished and booking cannot be deleted");
        }
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new AlreadyCanceledException("Booking already canceled");
        }
        if (booking.getBookingStatus() == BookingStatus.VALIDATED) {
            throw new AlreadyValidatedException("Booking already validated and cannot be canceled");
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingConfirmationQueueService.discardForBooking(booking.getId());

        log.info("Prenotazione annullata eventoId={}", booking.getEvent().getId());
    }

    @Transactional
    public CheckInResponse checkInBooking(UUID uuid , User admin) {

        Booking booking = bookingRepository.findForCheckInByUuid(uuid).orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        authEventService.checkStaffAccess(booking.getEvent().getId(), admin.getId());
        return validateCheckIn(booking);
    }

    @Transactional
    public void requestQrResend(Long eventId, UUID uuid, User owner) {
        Event event = eventRepository.findDistinctById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        if (owner == null || !event.getUser().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Only the event owner can resend booking QR codes");
        }
        if (event.getEventState() == EventState.FINISHED ||
                !LocalDateTime.now(ZoneId.of(timeZone)).isBefore(event.getEndDateTime())) {
            throw new EventFinishedException("The event is finished and its QR codes cannot be resent");
        }

        // Lock the booking so simultaneous owner requests cannot insert duplicate confirmation jobs.
        Booking booking = bookingRepository.findForCheckInByUuid(uuid)
                .filter(found -> found.getEvent().getId().equals(eventId))
                .orElseThrow(() -> new BookingNotFoundException("Booking not found for this event"));
        if (booking.getBookingStatus() != BookingStatus.CREATED ||
                booking.getEmailCiphertext() == null || booking.getNameCiphertext() == null) {
            throw new BookingResendUnavailableException("This booking cannot receive a QR confirmation");
        }

        if (bookingConfirmationQueueService.enqueueIfAbsent(booking)) {
            log.info("Reinvio QR accodato eventoId={}", eventId);
        }
    }

    @Transactional
    public CheckInResponse checkInBookingForEvent(UUID uuid, Long eventId, User user) {
        Booking booking = bookingRepository.findForCheckInByUuid(uuid)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        if (!booking.getEvent().getId().equals(eventId)) {
            throw new BookingNotFoundException("Booking not found for this event");
        }
        authEventService.checkStaffAccess(eventId, user.getId());
        return validateCheckIn(booking);
    }

    @Transactional
    public CheckInResponse checkInBookingForEvent(UUID uuid, Long eventId) {
        Booking booking = bookingRepository.findForCheckInByUuid(uuid)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        if (!booking.getEvent().getId().equals(eventId)) {
            throw new BookingNotFoundException("Booking not found");
        }
        return validateCheckIn(booking);
    }

    private CheckInResponse validateCheckIn(Booking booking) {
        if (booking.getEvent() != null && booking.getEvent().getEventState() == EventState.FINISHED) {
            throw new EventFinishedException("Event is finished and check-in is not allowed");
        }

        switch (booking.getBookingStatus()) {
            case CREATED:
                booking.setBookingStatus((BookingStatus.VALIDATED));
                booking.setCheckInDateTime(LocalDateTime.now());
                log.info("Check-in convalidato eventoId={}", booking.getEvent().getId());
                return bookingMapper.toCheckInResponse(booking);
            case VALIDATED:
                log.warn("Check-in rifiutato eventoId={} motivo=gia_convalidato", booking.getEvent().getId());
                throw new AlreadyValidatedException("Booking already validated");


            case CANCELLED:
                log.warn("Check-in rifiutato eventoId={} motivo=prenotazione_annullata", booking.getEvent().getId());
                throw new AlreadyCanceledException("Booking already canceled");

            default:
                throw new BookingStatusException("Booking status not valid for check-in ");
        }
    }

    @Transactional
    public void anonymizeBookingsByEventId(Long eventId) {
        bookingConfirmationQueueService.discardForEvent(eventId);
        List<Booking> bookings = bookingRepository.findAllByEventId(eventId);
        for (Booking booking : bookings) {
            booking.setNameCiphertext(null);
            booking.setSurnameCiphertext(null);
            booking.setEmailCiphertext(null);
            booking.setEmailLookupHash(null);
            booking.setName(null);
            booking.setSurname(null);
            booking.setEmail(null);
            booking.setPhone(null);
            booking.setPhoneCiphertext(null);
            booking.setUuid(UUID.randomUUID());

        }
        log.info("Prenotazioni anonimizzate eventoId={} numero={}", eventId, bookings.size());
    }

    @SafeVarargs
    private final List<Booking> distinctById(List<Booking>... groups) {
        Map<Long, Booking> bookingsById = new LinkedHashMap<>();
        for (List<Booking> group : groups) {
            for (Booking booking : group) {
                bookingsById.putIfAbsent(booking.getId(), booking);
            }
        }
        return List.copyOf(bookingsById.values());
    }



}
