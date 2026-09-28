package org.spring.passhalo.user.controller;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.user.dto.EventMembershipResponse;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.service.EventMembershipService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/events/{eventId}/memberships")
@RequiredArgsConstructor
public class EventMembershipController {

    private final EventMembershipService membershipService;

    @GetMapping
    public ResponseEntity<List<EventMembershipResponse>> getMemberships(@PathVariable Long eventId,
                                                                         @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(membershipService.getMemberships(eventId, user));
    }

    @PatchMapping("/{membershipId}/revoke")
    public ResponseEntity<Void> revokeMembership(@PathVariable Long eventId, @PathVariable Long membershipId,
                                                 @AuthenticationPrincipal User user) {
        membershipService.revokeMembership(eventId, membershipId, user);
        return ResponseEntity.noContent().build();
    }
}
