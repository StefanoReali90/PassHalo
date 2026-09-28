package org.spring.passhalo.user.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.booking.dto.CheckInResponse;
import org.spring.passhalo.user.dto.StaffAccessRequestResponse;
import org.spring.passhalo.user.dto.StaffAccessStatusResponse;
import org.spring.passhalo.user.dto.StaffCheckInRequest;
import org.spring.passhalo.user.dto.StaffCodeResponse;
import org.spring.passhalo.user.dto.SubmitStaffAccessRequest;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.exception.StaffAccessException;
import org.spring.passhalo.user.service.StaffAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class StaffAccessController {
    private static final String COOKIE_NAME = "staff_access";

    @Value("${app.time-zone}")
    private String timeZone;

    private final StaffAccessService staffAccessService;

    @PostMapping("/events/{eventId}/staff-code")
    public ResponseEntity<StaffCodeResponse> generateCode(@PathVariable Long eventId,
                                                           @AuthenticationPrincipal User owner,
                                                           HttpServletRequest request) {
        requireTrustedOrigin(request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(staffAccessService.generateCode(eventId, owner));
    }

    @GetMapping("/events/{eventId}/staff-requests")
    public ResponseEntity<List<StaffAccessRequestResponse>> pendingRequests(@PathVariable Long eventId,
                                                                              @AuthenticationPrincipal User owner) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(staffAccessService.pendingRequests(eventId, owner));
    }

    @PatchMapping("/events/{eventId}/staff-requests/{requestId}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long eventId, @PathVariable Long requestId,
                                         @AuthenticationPrincipal User owner, HttpServletRequest request) {
        requireTrustedOrigin(request);
        staffAccessService.decide(eventId, requestId, owner, true);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/events/{eventId}/staff-requests/{requestId}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long eventId, @PathVariable Long requestId,
                                        @AuthenticationPrincipal User owner, HttpServletRequest request) {
        requireTrustedOrigin(request);
        staffAccessService.decide(eventId, requestId, owner, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/staff-access/requests")
    public ResponseEntity<StaffAccessStatusResponse> submitRequest(@Valid @RequestBody SubmitStaffAccessRequest request,
                                                                     HttpServletRequest httpRequest) {
        requireTrustedOrigin(httpRequest);
        StaffAccessService.BrowserGrant grant = staffAccessService.submitRequest(
                request.code(), cookieValue(httpRequest), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, sessionCookie(grant.secret(), grant.expiresAt(), httpRequest).toString())
                .body(grant.status());
    }

    @GetMapping("/staff-access/status")
    public ResponseEntity<StaffAccessStatusResponse> status(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(staffAccessService.status(cookieValue(request)));
    }

    @PostMapping("/staff-access/check-in")
    public ResponseEntity<CheckInResponse> checkIn(@Valid @RequestBody StaffCheckInRequest request,
                                                    HttpServletRequest httpRequest) {
        requireTrustedOrigin(httpRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(staffAccessService.checkIn(cookieValue(httpRequest), request.uuid()));
    }

    @PostMapping("/staff-access/walk-ins")
    public ResponseEntity<Void> addWalkIn(HttpServletRequest request) {
        requireTrustedOrigin(request);
        staffAccessService.addWalkIn(cookieValue(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/staff-access/walk-ins/decrement")
    public ResponseEntity<Void> removeWalkIn(HttpServletRequest request) {
        requireTrustedOrigin(request);
        staffAccessService.removeWalkIn(cookieValue(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/staff-access/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        requireTrustedOrigin(request);
        staffAccessService.logout(cookieValue(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearCookie(request).toString()).build();
    }

    private String cookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private ResponseCookie sessionCookie(String secret, LocalDateTime expiresAt, HttpServletRequest request) {
        long seconds = Math.max(1, Duration.between(
                LocalDateTime.now(ZoneId.of(timeZone)).atZone(ZoneId.of(timeZone)),
                expiresAt.atZone(ZoneId.of(timeZone))).getSeconds());
        return ResponseCookie.from(COOKIE_NAME, secret).httpOnly(true).secure(request.isSecure())
                .sameSite("Strict").path("/").maxAge(seconds).build();
    }

    private ResponseCookie clearCookie(HttpServletRequest request) {
        return ResponseCookie.from(COOKIE_NAME, "").httpOnly(true).secure(request.isSecure())
                .sameSite("Strict").path("/").maxAge(0).build();
    }

    private void requireTrustedOrigin(HttpServletRequest request) {
        if (!"1".equals(request.getHeader("X-Staff-Action"))) {
            throw new StaffAccessException("Richiesta non consentita", HttpStatus.FORBIDDEN);
        }
        String fetchSite = request.getHeader("Sec-Fetch-Site");
        if ("cross-site".equalsIgnoreCase(fetchSite)) {
            throw new StaffAccessException("Origine della richiesta non consentita", HttpStatus.FORBIDDEN);
        }
        String origin = request.getHeader("Origin");
        if (origin == null) return;
        try {
            URI source = URI.create(origin);
            int sourcePort = source.getPort() < 0 ? ("https".equals(source.getScheme()) ? 443 : 80) : source.getPort();
            int targetPort = request.getServerPort();
            boolean sameOrigin = source.getScheme().equalsIgnoreCase(request.getScheme()) &&
                    source.getHost().equalsIgnoreCase(request.getServerName()) && sourcePort == targetPort;
            boolean localDev = ("localhost".equals(request.getServerName()) || "127.0.0.1".equals(request.getServerName())) &&
                    "http".equals(source.getScheme()) && sourcePort == 5173 &&
                    ("localhost".equals(source.getHost()) || "127.0.0.1".equals(source.getHost()));
            if (sameOrigin || localDev) return;
        } catch (RuntimeException ignored) {
            // Malformed Origin is denied below.
        }
        throw new StaffAccessException("Origine della richiesta non consentita", HttpStatus.FORBIDDEN);
    }
}
