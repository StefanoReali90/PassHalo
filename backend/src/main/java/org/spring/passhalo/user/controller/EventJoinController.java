package org.spring.passhalo.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.user.dto.JoinCodeResponse;
import org.spring.passhalo.user.dto.JoinRequestResponse;
import org.spring.passhalo.user.dto.SubmitJoinRequest;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.service.EventJoinService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventJoinController {
    private final EventJoinService joinService;

    @PostMapping("/events/{eventId}/join-code")
    public ResponseEntity<JoinCodeResponse> generateCode(@PathVariable Long eventId,
                                                         @AuthenticationPrincipal User owner) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(joinService.generateCode(eventId, owner));
    }

    @PostMapping("/join-requests")
    public ResponseEntity<JoinRequestResponse> submitRequest(@Valid @RequestBody SubmitJoinRequest request,
                                                             @AuthenticationPrincipal User staff) {
        return ResponseEntity.status(HttpStatus.CREATED).body(joinService.submitRequest(request.code(), staff));
    }

    @GetMapping("/events/{eventId}/join-requests")
    public ResponseEntity<List<JoinRequestResponse>> pendingRequests(@PathVariable Long eventId,
                                                                      @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(joinService.pendingRequests(eventId, owner));
    }

    @GetMapping("/join-requests/my")
    public ResponseEntity<List<JoinRequestResponse>> myRequests(@AuthenticationPrincipal User staff) {
        return ResponseEntity.ok(joinService.myRequests(staff));
    }

    @PatchMapping("/events/{eventId}/join-requests/{requestId}/approve")
    public ResponseEntity<JoinRequestResponse> approveRequest(@PathVariable Long eventId,
                                                               @PathVariable Long requestId,
                                                               @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(joinService.approveRequest(eventId, requestId, owner));
    }

    @PatchMapping("/events/{eventId}/join-requests/{requestId}/reject")
    public ResponseEntity<JoinRequestResponse> rejectRequest(@PathVariable Long eventId,
                                                              @PathVariable Long requestId,
                                                              @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok(joinService.rejectRequest(eventId, requestId, owner));
    }
}
