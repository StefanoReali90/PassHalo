package org.spring.passhalo.user.service;

import lombok.RequiredArgsConstructor;
import org.spring.passhalo.user.dto.EventMembershipResponse;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.MembershipState;
import org.spring.passhalo.user.exception.InvalidMembershipException;
import org.spring.passhalo.user.mapper.EventMembershipMapper;
import org.spring.passhalo.user.repository.EventMembershipRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventMembershipService {
    private final EventMembershipMapper membershipMapper;

    @Value("${app.time-zone}")
    private String timeZone;

    private final EventMembershipRepository membershipRepository;
    private final AuthEventService authEventService;

    @Transactional(readOnly = true)
    public List<EventMembershipResponse> getMemberships(Long eventId, User admin) {
        authEventService.checkUserAccess(eventId, admin.getId());
        return membershipRepository.findAllByEventId(eventId).stream()
                .map(membershipMapper::toResponse)
                .toList();
    }

    @Transactional
    public void revokeMembership(Long eventId, Long membershipId, User admin) {
        authEventService.checkUserAccess(eventId, admin.getId());
        EventMembership membership = membershipRepository.findByIdAndEventId(membershipId, eventId)
                .orElseThrow(() -> new InvalidMembershipException("Membership not found for this event"));
        if (membership.getMembershipState() != MembershipState.ACTIVE) {
            throw new InvalidMembershipException("Membership is not active");
        }
        membership.setMembershipState(MembershipState.REVOKED);
        membership.setRevokedAt(LocalDateTime.now(ZoneId.of(timeZone)));
        membershipRepository.save(membership);
    }


}
