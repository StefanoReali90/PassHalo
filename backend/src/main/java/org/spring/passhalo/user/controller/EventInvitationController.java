package org.spring.passhalo.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.user.dto.AcceptEventInvitationRequest;
import org.spring.passhalo.user.dto.CreateEventInvitationRequest;
import org.spring.passhalo.user.dto.EventInvitationResponse;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.service.EventInvitationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.NoSuchAlgorithmException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class EventInvitationController {

    private final EventInvitationService invitationService;

    @PostMapping("/events/{eventId}/invitations")
    public ResponseEntity<EventInvitationResponse> createInvitation(@PathVariable Long eventId,
                                                                      @Valid @RequestBody CreateEventInvitationRequest request,
                                                                      @AuthenticationPrincipal User user) throws NoSuchAlgorithmException {
        EventInvitationResponse invitation = invitationService.createInvitation(eventId, request.email(), request.role(), user);
        return ResponseEntity.status(HttpStatus.CREATED).body(invitation);
    }

    @GetMapping("/events/{eventId}/invitations")
    public ResponseEntity<List<EventInvitationResponse>> getPendingInvitations(@PathVariable Long eventId,
                                                                                @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(invitationService.getPendingInvitations(eventId, user));
    }

    @DeleteMapping("/events/{eventId}/invitations/{invitationId}")
    public ResponseEntity<Void> revokeInvitation(@PathVariable Long eventId, @PathVariable Long invitationId,
                                                 @AuthenticationPrincipal User user) {
        invitationService.revokeInvitation(invitationId, eventId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<Void> acceptInvitation(@Valid @RequestBody AcceptEventInvitationRequest request,
                                                 @AuthenticationPrincipal User user) throws NoSuchAlgorithmException {
        invitationService.acceptInvitation(request.token(), user);
        return ResponseEntity.noContent().build();
    }
}
