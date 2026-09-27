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
import org.spring.passhalo.notification.service.EmailService;
import org.spring.passhalo.booking.exception.EventFinishedException;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.service.AuthEventService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final PiiCryptoService cryptoService;
    private final EventRepository eventRepository;
    private final QrCodeService qrCodeService;
    private final EmailService emailService;
    private final MarketingService marketingService;
    private final AuthEventService authEventService;

    @Transactional
    public BookingResponse createBooking(BookingRequest bookingRequest) {
        Booking booking = bookingMapper.toEntity(bookingRequest);
        Event event = eventRepository.findById(bookingRequest.eventId())
                .orElseThrow(() -> new EventNotFoundException("Event not found with id: " + bookingRequest.eventId()));
        if (event.getEventState() == EventState.FINISHED) {
            throw new EventFinishedException("Event is finished and no more bookings are allowed");
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
            unsubscribeToken = marketingService.registerConsent(bookingRequest.name(), bookingRequest.surname(), bookingRequest.email());
        }
        emailService.sendBookingConfirmation(bookingRequest.email(), bookingRequest.name(), event.getName(),
                qrCodeService.createQrCodeBytes(savedBooking.getUuid().toString()), unsubscribeToken);
        log.info("Booking has been created successfully");
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

        log.info("Booking has been cancelled successfully");
    }

    @Transactional
    public CheckInResponse checkInBooking(UUID uuid , User admin) {

        Booking booking = bookingRepository.findForCheckInByUuid(uuid).orElseThrow(() -> new BookingNotFoundException("Booking not found"));
        authEventService.checkStaffAccess(booking.getEvent().getId(), admin.getId());
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
                log.info("Check-in successful");
                return bookingMapper.toCheckInResponse(booking);
            case VALIDATED:
                log.warn("Check-in rejected: pass was already validated");
                throw new AlreadyValidatedException("Booking already validated");


            case CANCELLED:
                log.warn("Check-in rejected");
                throw new AlreadyCanceledException("Booking already canceled");

            default:
                throw new BookingStatusException("Booking status not valid for check-in ");
        }
    }

    @Transactional
    public void anonymizeBookingsByEventId(Long eventId) {
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
        log.info("Booking identifiers replaced after event closure");
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
