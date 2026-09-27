package org.spring.passhalo.user.service;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.exception.AccessDeniedException;
import org.spring.passhalo.event.exception.EventNotFoundException;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.dto.JoinCodeResponse;
import org.spring.passhalo.user.dto.JoinRequestResponse;
import org.spring.passhalo.user.entity.EventJoinCode;
import org.spring.passhalo.user.entity.EventJoinRequest;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.JoinRequestState;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.exception.InvalidJoinCodeException;
import org.spring.passhalo.user.exception.JoinRequestConflictException;
import org.spring.passhalo.user.repository.EventJoinCodeRepository;
import org.spring.passhalo.user.repository.EventJoinRequestRepository;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventJoinService {
    private static final String CODE_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 16;

    @Value("${app.time-zone}")
    private String timeZone;

    private final EventRepository eventRepository;
    private final EventJoinCodeRepository joinCodeRepository;
    private final EventJoinRequestRepository joinRequestRepository;
    private final EventMembershipRepository membershipRepository;

    @Transactional
    public JoinCodeResponse generateCode(Long eventId, User owner) {
        Event event = eventRepository.findDistinctById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        requireOwner(event, owner);
        LocalDateTime now = now();
        requireOpen(event, now);

        for (EventJoinCode previous : joinCodeRepository.findAllByEventIdAndRevokedAtIsNull(eventId)) {
            previous.setRevokedAt(now);
        }

        String code = newCode();
        EventJoinCode joinCode = new EventJoinCode();
        joinCode.setEvent(event);
        joinCode.setCodeHash(hash(code));
        joinCode.setCreatedAt(now);
        joinCode.setExpiresAt(now.plusDays(7).isBefore(event.getEndDateTime())
                ? now.plusDays(7) : event.getEndDateTime());
        joinCode.setCreatedBy(owner);
        joinCodeRepository.save(joinCode);
        return new JoinCodeResponse(formatCode(code), joinCode.getExpiresAt());
    }

    @Transactional
    public JoinRequestResponse submitRequest(String suppliedCode, User requester) {
        String code = normalizeCode(suppliedCode);
        EventJoinCode joinCode = joinCodeRepository.findByCodeHash(hash(code))
                .orElseThrow(InvalidJoinCodeException::new);
        LocalDateTime now = now();
        if (joinCode.getRevokedAt() != null || !now.isBefore(joinCode.getExpiresAt())) {
            throw new InvalidJoinCodeException();
        }
        Event event = joinCode.getEvent();
        if (event.getEventState() == EventState.FINISHED || !now.isBefore(event.getEndDateTime())) {
            throw new InvalidJoinCodeException();
        }
        if (event.getUser().getId().equals(requester.getId())) {
            throw new JoinRequestConflictException("Event owner cannot request staff access");
        }
        Optional<EventMembership> membership = membershipRepository.findByEventIdAndCollaboratorId(
                event.getId(), requester.getId());
        if (membership.isPresent() && membership.get().getMembershipState() == MembershipState.ACTIVE &&
                (membership.get().getValidUntil() == null || now.isBefore(membership.get().getValidUntil()))) {
            throw new JoinRequestConflictException("User already has access to this event");
        }
        if (joinRequestRepository.findFirstByEventIdAndRequesterIdAndState(
                event.getId(), requester.getId(), JoinRequestState.PENDING).isPresent()) {
            throw new JoinRequestConflictException("A request is already pending for this event");
        }

        EventJoinRequest request = new EventJoinRequest();
        request.setEvent(event);
        request.setRequester(requester);
        request.setState(JoinRequestState.PENDING);
        request.setCreatedAt(now);
        return toResponse(joinRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> pendingRequests(Long eventId, User owner) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        requireOwner(event, owner);
        if (event.getEventState() == EventState.FINISHED || !now().isBefore(event.getEndDateTime())) {
            return List.of();
        }
        return joinRequestRepository.findAllByEventIdAndStateOrderByCreatedAtAsc(eventId, JoinRequestState.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<JoinRequestResponse> myRequests(User requester) {
        return joinRequestRepository.findAllByRequesterIdOrderByCreatedAtDesc(requester.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public JoinRequestResponse approveRequest(Long eventId, Long requestId, User owner) {
        Event event = eventRepository.findDistinctById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        requireOwner(event, owner);
        LocalDateTime now = now();
        requireOpen(event, now);
        EventJoinRequest request = pendingRequest(eventId, requestId);

        EventMembership membership = membershipRepository.findByEventIdAndCollaboratorId(
                eventId, request.getRequester().getId()).orElseGet(EventMembership::new);
        if (membership.getId() != null && membership.getMembershipState() == MembershipState.ACTIVE &&
                (membership.getValidUntil() == null || now.isBefore(membership.getValidUntil()))) {
            throw new JoinRequestConflictException("User already has access to this event");
        }
        if (membership.getId() == null) {
            membership.setEvent(event);
            membership.setCollaborator(request.getRequester());
            membership.setCreatedAt(now);
            membership.setCreatedBy(owner);
        }
        membership.setRole(EventRole.STAFF);
        membership.setMembershipState(MembershipState.ACTIVE);
        membership.setValidFrom(now);
        membership.setValidUntil(null);
        membership.setRevokedAt(null);
        membershipRepository.save(membership);

        request.setState(JoinRequestState.APPROVED);
        request.setDecidedAt(now);
        request.setDecidedBy(owner);
        return toResponse(request);
    }

    @Transactional
    public JoinRequestResponse rejectRequest(Long eventId, Long requestId, User owner) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        requireOwner(event, owner);
        EventJoinRequest request = pendingRequest(eventId, requestId);
        request.setState(JoinRequestState.REJECTED);
        request.setDecidedAt(now());
        request.setDecidedBy(owner);
        return toResponse(request);
    }

    @Transactional
    public void expireRequestsForEvent(Long eventId) {
        LocalDateTime now = now();
        for (EventJoinRequest request : joinRequestRepository.findAllByEventIdAndStateOrderByCreatedAtAsc(
                eventId, JoinRequestState.PENDING)) {
            request.setState(JoinRequestState.EXPIRED);
            request.setDecidedAt(now);
        }
        for (EventJoinCode code : joinCodeRepository.findAllByEventIdAndRevokedAtIsNull(eventId)) {
            code.setRevokedAt(now);
        }
    }

    @Transactional
    @Scheduled(cron = "0 0 * * * *", zone = "${app.time-zone}")
    public void expireEndedRequests() {
        LocalDateTime now = now();
        for (EventJoinRequest request : joinRequestRepository.findAllEndedByState(JoinRequestState.PENDING, now)) {
            request.setState(JoinRequestState.EXPIRED);
            request.setDecidedAt(now);
        }
    }

    private EventJoinRequest pendingRequest(Long eventId, Long requestId) {
        EventJoinRequest request = joinRequestRepository.findByIdAndEventId(requestId, eventId)
                .orElseThrow(() -> new JoinRequestConflictException("Join request not found"));
        if (request.getState() != JoinRequestState.PENDING) {
            throw new JoinRequestConflictException("Join request has already been decided");
        }
        return request;
    }

    private void requireOwner(Event event, User user) {
        if (user == null || !event.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Only the event owner can manage staff requests");
        }
    }

    private void requireOpen(Event event, LocalDateTime now) {
        if (event.getEventState() == EventState.FINISHED || !now.isBefore(event.getEndDateTime())) {
            throw new JoinRequestConflictException("Event has finished");
        }
    }

    private String normalizeCode(String suppliedCode) {
        if (suppliedCode == null) {
            throw new InvalidJoinCodeException();
        }
        String code = suppliedCode.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != CODE_LENGTH) {
            throw new InvalidJoinCodeException();
        }
        for (int i = 0; i < code.length(); i++) {
            if (CODE_ALPHABET.indexOf(code.charAt(i)) < 0) {
                throw new InvalidJoinCodeException();
            }
        }
        return code;
    }

    private String newCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    private String formatCode(String code) {
        return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" +
                code.substring(8, 12) + "-" + code.substring(12);
    }

    private String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of(timeZone));
    }

    private JoinRequestResponse toResponse(EventJoinRequest request) {
        User requester = request.getRequester();
        return new JoinRequestResponse(
                request.getId(), request.getEvent().getId(), request.getEvent().getName(),
                requester.getId(), requester.getName(), requester.getSurname(), requester.getEmail(),
                request.getState(), request.getCreatedAt(), request.getDecidedAt());
    }
}
