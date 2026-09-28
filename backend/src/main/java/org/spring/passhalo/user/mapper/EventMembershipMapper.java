package org.spring.passhalo.user.mapper;

import org.spring.passhalo.user.dto.EventMembershipResponse;
import org.spring.passhalo.user.entity.EventMembership;
import org.spring.passhalo.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class EventMembershipMapper {


    public EventMembershipResponse toResponse(EventMembership membership) {
        User collaborator = membership.getCollaborator();
        return new EventMembershipResponse(
                membership.getId(),
                membership.getEvent().getId(),
                collaborator.getId(),
                collaborator.getName(),
                collaborator.getSurname(),
                collaborator.getEmail(),
                membership.getRole(),
                membership.getMembershipState(),
                membership.getValidFrom(),
                membership.getValidUntil(),
                membership.getRevokedAt()
        );
    }
}
