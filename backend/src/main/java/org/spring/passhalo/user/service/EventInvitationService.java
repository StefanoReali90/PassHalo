package org.spring.passhalo.user.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.exception.EventNotFoundException;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.notification.service.EmailService;
import org.spring.passhalo.user.dto.EventInvitationResponse;
import org.spring.passhalo.user.entity.EventInvitation;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.InviteState;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.exception.InvalidInvitationException;
import org.spring.passhalo.user.exception.InvitationAlreadyExistsException;
import org.spring.passhalo.user.repository.EventInvitationRepository;
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
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EventInvitationService {

    @Value("${app.time-zone}")
    private String timeZone;

    private final EventInvitationRepository eventInvitationRepository;
    private final EventRepository eventRepository;
    private final EmailService emailService;
    private final EventMembershipRepository eventMembershipRepository;
    private final AuthEventService authEventService;
    private final EntityManager entityManager;

    @Transactional
    public EventInvitationResponse createInvitation(Long eventId, String inviteEmail, EventRole role, User admin) throws NoSuchAlgorithmException {

        if (role == null || inviteEmail == null || inviteEmail.isBlank() || admin == null) {
            throw new InvalidInvitationException("Invalid invitation");
        }
        authEventService.checkUserAccess(eventId, admin.getId());
        String cleanedEmail = inviteEmail.trim().toLowerCase(Locale.ROOT);
        Event event = eventRepository.findDistinctById(eventId).orElseThrow(() -> new EventNotFoundException("Event not found"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        requireOpenEvent(event, now);
        boolean invitationAlreadyExists = eventInvitationRepository.existsByEventIdAndRecipientEmailAndInviteStateAndExpiresAtAfter(eventId, cleanedEmail, InviteState.PENDING, now);
        if (invitationAlreadyExists) {
            throw new InvitationAlreadyExistsException("Invitation already exists");
        }

        EventInvitation eventInvitation = new EventInvitation();

        eventInvitation.setEvent(event);
        eventInvitation.setRecipientEmail(cleanedEmail);
        eventInvitation.setCreatedBy(admin);
        eventInvitation.setCreatedAt(now);
        eventInvitation.setExpiresAt(now.plusDays(7));
        eventInvitation.setProposedRole(role);
        eventInvitation.setInviteState(InviteState.PENDING);

        SecureRandom random = new SecureRandom();

        byte[] tokenBytes = new byte[32];

        random.nextBytes(tokenBytes);

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(token.getBytes(StandardCharsets.UTF_8));
        String hashString = Base64.getEncoder().encodeToString(hash);
        eventInvitation.setTokenHash(hashString);
        eventInvitationRepository.save(eventInvitation);
        emailService.sendEmailConfirmation(eventInvitation, token);
        return toResponse(eventInvitation);
    }

    @Transactional(readOnly = true)
    public List<EventInvitationResponse> getPendingInvitations(Long eventId, User admin) {
        authEventService.checkUserAccess(eventId, admin.getId());
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        return eventInvitationRepository.findAllByEventIdAndInviteState(eventId, InviteState.PENDING).stream()
                .filter(invitation -> invitation.getExpiresAt().isAfter(now))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void acceptInvitation(String token, User user) throws NoSuchAlgorithmException {

        if (token == null || token.isBlank() || user == null) {
            throw new InvalidInvitationException("Invalid invitation");
        }


        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] hash = sha256.digest(token.getBytes(StandardCharsets.UTF_8));
        String hashString = Base64.getEncoder().encodeToString(hash);
        EventInvitation invitation = eventInvitationRepository.findByTokenHash(hashString).orElseThrow(() -> new InvalidInvitationException("Not valid token"));
        eventRepository.findDistinctById(invitation.getEvent().getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        entityManager.refresh(invitation);
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        if (invitation.getInviteState() != InviteState.PENDING) {
            throw new InvalidInvitationException("Invitation is not pending");
        }
        if (!invitation.getExpiresAt().isAfter(now)) {
            throw new InvalidInvitationException("Invitation expired");
        }
        requireOpenEvent(invitation.getEvent(), now);

        if (!invitation.getRecipientEmail().trim().equalsIgnoreCase(user.getEmail().trim())) {
            throw new InvalidInvitationException("Invitation does not belong to this user");
        }
        if (invitation.getEvent().getUser().getId().equals(user.getId())) {
            throw new InvalidInvitationException("Event owner cannot accept this invitation");
        }
        Optional<EventMembership> existingMembership = eventMembershipRepository.findByEventIdAndCollaboratorId(invitation.getEvent().getId(), user.getId());

        EventMembership membership;

        if (existingMembership.isEmpty()) {
            membership = new EventMembership();
            membership.setEvent(invitation.getEvent());
            membership.setCollaborator(user);
            membership.setMembershipState(MembershipState.ACTIVE);
            membership.setRole(invitation.getProposedRole());
            membership.setCreatedAt(now);
            membership.setCreatedBy(invitation.getCreatedBy());
            membership.setValidFrom(now);
            membership.setValidUntil(null);
            membership.setRevokedAt(null);

        } else {
            membership = existingMembership.get();
            switch (membership.getMembershipState()) {
                case ACTIVE -> throw new InvalidInvitationException("User is already an active member of this event");
                case REVOKED -> {
                    membership.setMembershipState(MembershipState.ACTIVE);
                    membership.setRole(invitation.getProposedRole());
                    membership.setValidFrom(now);
                    membership.setValidUntil(null);
                    membership.setRevokedAt(null);
                }
                default -> throw new InvalidInvitationException("Invalid membership state");
            }
        }
        eventMembershipRepository.save(membership);
        invitation.setInviteState(InviteState.ACCEPTED);
        invitation.setAcceptedAt(now);
        invitation.setAcceptedBy(user);

        eventInvitationRepository.save(invitation);
    }

    @Transactional
    @Scheduled(cron = "0 0 * * * *", zone = "${app.time-zone}")
    public void expireInvitations() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        List<EventInvitation> expiredInvitations = eventInvitationRepository.findAllByInviteStateAndExpiresAtLessThanEqual(InviteState.PENDING, now);
        for (EventInvitation invitation : expiredInvitations) {
            invitation.setInviteState(InviteState.EXPIRED);
            eventInvitationRepository.save(invitation);
        }
    }

    @Transactional
    public void revokeInvitation(Long invitationId, Long eventId, User admin) {
        authEventService.checkUserAccess(eventId, admin.getId());
        eventRepository.findDistinctById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        EventInvitation invitation = eventInvitationRepository.findByIdAndEventId(invitationId, eventId).orElseThrow(() -> new InvalidInvitationException("Invitation not found"));
        if (invitation.getInviteState() != InviteState.PENDING) {
            throw new InvalidInvitationException("Invitation is not pending");
        }
        invitation.setInviteState(InviteState.REVOKED);
        invitation.setRevokedAt(LocalDateTime.now(ZoneId.of(timeZone)));
        eventInvitationRepository.save(invitation);
    }

    @Transactional
    public void revokePendingForEvent(Long eventId) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        for (EventInvitation invitation : eventInvitationRepository.findAllByEventIdAndInviteState(eventId, InviteState.PENDING)) {
            invitation.setInviteState(InviteState.REVOKED);
            invitation.setRevokedAt(now);
        }
    }

    @Transactional
    public void deleteForEvent(Long eventId) {
        eventInvitationRepository.deleteAll(eventInvitationRepository.findAllByEventId(eventId));
        eventInvitationRepository.flush();
    }

    private void requireOpenEvent(Event event, LocalDateTime now) {
        if (event.getEventState() == EventState.FINISHED || !now.isBefore(event.getEndDateTime())) {
            throw new InvalidInvitationException("L'evento è terminato.");
        }
    }

    private EventInvitationResponse toResponse(EventInvitation invitation) {
        return new EventInvitationResponse(
                invitation.getId(),
                invitation.getEvent().getId(),
                invitation.getRecipientEmail(),
                invitation.getProposedRole(),
                invitation.getInviteState(),
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getRevokedAt()
        );
    }
}
