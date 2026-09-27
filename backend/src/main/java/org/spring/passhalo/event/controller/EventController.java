package org.spring.passhalo.event.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.event.dto.EventDashboardResponse;
import org.spring.passhalo.event.dto.EventRequest;
import org.spring.passhalo.event.dto.EventResponse;
import org.spring.passhalo.event.dto.MyEventResponse;
import org.spring.passhalo.event.service.EventService;
import org.spring.passhalo.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;


    @PostMapping(path = "/", consumes = "application/json", produces = "application/json")
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody EventRequest eventRequest, @AuthenticationPrincipal User admin) {
        EventResponse eventResponse = eventService.createEvent(eventRequest, admin);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventResponse);
    }

    @PutMapping(path = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable Long id, @Valid @RequestBody EventRequest eventRequest, @AuthenticationPrincipal User admin) {
        EventResponse eventResponse = eventService.updateEvent(eventRequest, id, admin);
        return ResponseEntity.status(HttpStatus.OK).body(eventResponse);


    }

    @GetMapping(path = "/{id}", produces = "application/json")
    public ResponseEntity<EventResponse> getEventById(@PathVariable Long id) {
        EventResponse eventResponse = eventService.getEventById(id);
        return ResponseEntity.ok(eventResponse);
    }

    @GetMapping(path = {"", "/"}, produces = "application/json")
    public ResponseEntity<List<EventResponse>> getAllEvents() {
        List<EventResponse> eventResponses = eventService.getAllEvents();
        return ResponseEntity.ok(eventResponses);
    }

    @GetMapping(path = "/my-events", produces = "application/json")
    public ResponseEntity<List<MyEventResponse>> getEventsByUserId(@AuthenticationPrincipal User admin) {
        List<MyEventResponse> eventResponses = eventService.getEventsByUser(admin.getId());
        return ResponseEntity.ok(eventResponses);

    }

    @GetMapping(path="/{id}/dashboard", produces = "application/json")
    public ResponseEntity <EventDashboardResponse> getEventDashboard(@PathVariable Long id, @AuthenticationPrincipal User admin) {
        EventDashboardResponse dashboardResponse = eventService.getEventDashboardData(id, admin);
        return ResponseEntity.ok(dashboardResponse);
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id, @AuthenticationPrincipal User admin) {
        eventService.deleteEventById(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(path = "/{id}/walk-in")
    public ResponseEntity<Void> incrementWalkInCount(@PathVariable Long id, @AuthenticationPrincipal User admin) {
        eventService.incrementWalkInCount(id,admin);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(path = "/{id}/walk-in/decrement")
    public ResponseEntity<Void> decrementWalkInCount(@PathVariable Long id, @AuthenticationPrincipal User admin) {
        eventService.decrementWalkInCount(id, admin);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping(path="/{id}/close", produces = "application/json")
    public ResponseEntity<Void> closeEvent(@PathVariable Long id, @AuthenticationPrincipal User admin) {
        eventService.closeEvent(id, admin);
        return ResponseEntity.noContent().build();
    }
}
