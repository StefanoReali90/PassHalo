package org.spring.passhalo.booking.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.booking.dto.BookingRequest;
import org.spring.passhalo.booking.dto.BookingResponse;
import org.spring.passhalo.booking.dto.CheckInResponse;
import org.spring.passhalo.booking.service.BookingService;
import org.spring.passhalo.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping(path = "/", consumes = "application/json", produces = "application/json")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest bookingRequest) {
        BookingResponse bookingResponse = bookingService.createBooking(bookingRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingResponse);
    }

    @GetMapping(path = "/{uuid}", produces = "application/json")
    public ResponseEntity<BookingResponse> getBookingByUuid(@PathVariable UUID uuid, @AuthenticationPrincipal User admin) {
        BookingResponse bookingResponse = bookingService.getBookingByUuid(uuid, admin);
        return ResponseEntity.ok(bookingResponse);
    }

    @GetMapping(path = "/events/{eventId}", produces = "application/json")
    public ResponseEntity<List<BookingResponse>> getBookingByEventId(@PathVariable Long eventId, @AuthenticationPrincipal User admin) {
        List<BookingResponse> bookingResponse = bookingService.getBookingsByEventId(eventId,admin);
        return ResponseEntity.ok(bookingResponse);
    }

    @GetMapping(path = "/email/{email}", produces = "application/json")
    public ResponseEntity<List<BookingResponse>> getBookingByEmail(@PathVariable String email, @AuthenticationPrincipal User admin) {
        List<BookingResponse> bookingResponse = bookingService.getBookingsByEmail(email,admin);
        return ResponseEntity.ok(bookingResponse);
    }

    @GetMapping(path = "/event/{eventId}/email/{email}", produces = "application/json")
    public ResponseEntity<List<BookingResponse>> getBookingByEventIdAndEmail(@PathVariable Long eventId, @PathVariable String email, @AuthenticationPrincipal User admin) {
        List<BookingResponse> bookingResponse = bookingService.getBookingsByEventIdAndEmail(eventId, email,admin);
        return ResponseEntity.ok(bookingResponse);
    }

    @GetMapping(path = "/bookingId/{bookingId}", produces = "application/json")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable Long bookingId, @AuthenticationPrincipal User admin) {
        BookingResponse bookingResponse = bookingService.getBookingById(bookingId, admin);
        return ResponseEntity.ok(bookingResponse);
    }

    @GetMapping(path="/", produces = "application/json")
    public ResponseEntity<List<BookingResponse>> getAllBookings(@AuthenticationPrincipal User admin) {
        List<BookingResponse> bookingResponses = bookingService.getAllBookings(admin);
        return ResponseEntity.ok(bookingResponses);
    }

    @PatchMapping(path = "/check-in/{uuid}", produces = "application/json")
    public ResponseEntity<CheckInResponse> checkInBooking(@PathVariable UUID uuid, @AuthenticationPrincipal User admin) {
        CheckInResponse checkInResponse = bookingService.checkInBooking(uuid, admin);
        return ResponseEntity.ok(checkInResponse);
    }

    @PatchMapping(path = "/events/{eventId}/check-in/{uuid}", produces = "application/json")
    public ResponseEntity<CheckInResponse> checkInBookingForEvent(@PathVariable Long eventId,
                                                                   @PathVariable UUID uuid,
                                                                   @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(bookingService.checkInBookingForEvent(uuid, eventId, user));
    }



    @DeleteMapping(path = "/{uuid}", produces = "application/json")
    public ResponseEntity<Void> cancelBooking(@PathVariable UUID uuid, @AuthenticationPrincipal User admin) {
        bookingService.deleteBooking(uuid,admin);
        return ResponseEntity.noContent().build();
    }
}
