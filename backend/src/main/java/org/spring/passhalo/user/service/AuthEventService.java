package org.spring.passhalo.user.service;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.exception.AccessDeniedException;
import org.spring.passhalo.event.exception.EventNotFoundException;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AuthEventService {
    @Value("${app.time-zone}")
    private String timezone;
    private final EventRepository eventRepository;

    private final EventMembershipRepository eventMembershipRepository;

    @Transactional(readOnly = true)
    public void checkUserAccess(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        if (event.getUser().getId().equals(userId)) {
            return;
        }
        EventMembership membership = eventMembershipRepository.findByEventIdAndCollaboratorId(eventId, userId)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of the event"));
        if (membership.getMembershipState() != MembershipState.ACTIVE) {
            throw new AccessDeniedException("User is not an active member of the event");
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        if (now.isBefore(membership.getValidFrom())) {
            throw new AccessDeniedException("User's membership is not valid at this time");
        }
        if (membership.getValidUntil() != null) {

            if (membership.getValidUntil().isEqual(now) || membership.getValidUntil().isBefore(now)) {
                throw new AccessDeniedException("User's membership has expired");
            }

        }
        if (membership.getRole() != EventRole.EVENT_ADMIN) {
            throw new AccessDeniedException("User is a staff member and cannot access this event");
        }

    }

    @Transactional(readOnly = true)
    public void checkStaffAccess(Long eventId, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));
        if (event.getEventState() == EventState.FINISHED || !now.isBefore(event.getEndDateTime())) {
            throw new AccessDeniedException("Operational access to this event has expired");
        }
        if (event.getUser().getId().equals(userId)) {
            return;
        }
        EventMembership membership = eventMembershipRepository.findByEventIdAndCollaboratorId(eventId, userId)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of the event"));
        if (membership.getMembershipState() != MembershipState.ACTIVE) {
            throw new AccessDeniedException("User is not an active member of the event");
        }
        if (now.isBefore(membership.getValidFrom())) {
            throw new AccessDeniedException("User's membership is not valid at this time");
        }
        if (membership.getValidUntil() != null) {

            if (membership.getValidUntil().isEqual(now) || membership.getValidUntil().isBefore(now)) {
                throw new AccessDeniedException("User's membership has expired");
            }

        }
        if  (membership.getRole() != EventRole.EVENT_ADMIN && membership.getRole() != EventRole.STAFF) {
            throw new AccessDeniedException("User is not an admin and cannot access this event");
        }
    }
}
