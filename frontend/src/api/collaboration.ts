import { apiFetch } from './client';
import type {
    EventInvitationResponse,
    EventMembershipResponse,
    EventRole,
} from '../types';

export function createInvitation(eventId: number, email: string, role: EventRole): Promise<EventInvitationResponse> {
    return apiFetch<EventInvitationResponse>(`/events/${eventId}/invitations`, {
        method: 'POST',
        body: JSON.stringify({ email, role }),
    });
}

export function getPendingInvitations(eventId: number): Promise<EventInvitationResponse[]> {
    return apiFetch<EventInvitationResponse[]>(`/events/${eventId}/invitations`);
}

export function revokeInvitation(eventId: number, invitationId: number): Promise<void> {
    return apiFetch<void>(`/events/${eventId}/invitations/${invitationId}`, { method: 'DELETE' });
}

export function acceptInvitation(token: string): Promise<void> {
    return apiFetch<void>('/invitations/accept', {
        method: 'POST',
        body: JSON.stringify({ token }),
    });
}

export function getMemberships(eventId: number): Promise<EventMembershipResponse[]> {
    return apiFetch<EventMembershipResponse[]>(`/events/${eventId}/memberships`);
}

export function revokeMembership(eventId: number, membershipId: number): Promise<void> {
    return apiFetch<void>(`/events/${eventId}/memberships/${membershipId}/revoke`, { method: 'PATCH' });
}
