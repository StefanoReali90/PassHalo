import { apiFetch } from './client';
import type { CheckInResponse } from '../types';

const staffActionHeaders = { 'X-Staff-Action': '1' };

export type StaffAccessState = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';

export interface StaffAccessStatus {
    state: StaffAccessState;
    eventId: number;
    eventName: string;
    expiresAt: string;
}

export interface StaffCodeResponse {
    code: string;
    expiresAt: string;
}

export interface StaffRequestResponse {
    id: number;
    state: StaffAccessState;
    createdAt: string;
}

export function createStaffCode(eventId: number): Promise<StaffCodeResponse> {
    return apiFetch<StaffCodeResponse>(`/events/${eventId}/staff-code`, { method: 'POST', headers: staffActionHeaders });
}

export function getStaffRequests(eventId: number): Promise<StaffRequestResponse[]> {
    return apiFetch<StaffRequestResponse[]>(`/events/${eventId}/staff-requests`);
}

export function decideStaffRequest(eventId: number, requestId: number, decision: 'approve' | 'reject'): Promise<void> {
    return apiFetch<void>(`/events/${eventId}/staff-requests/${requestId}/${decision}`, { method: 'PATCH', headers: staffActionHeaders });
}

export function requestStaffAccess(code: string): Promise<StaffAccessStatus> {
    return apiFetch<StaffAccessStatus>('/staff-access/requests', {
        method: 'POST',
        headers: staffActionHeaders,
        body: JSON.stringify({ code }),
    });
}

export function getStaffAccessStatus(): Promise<StaffAccessStatus> {
    return apiFetch<StaffAccessStatus>('/staff-access/status');
}

export function staffCheckIn(uuid: string): Promise<CheckInResponse> {
    return apiFetch<CheckInResponse>('/staff-access/check-in', {
        method: 'POST',
        headers: staffActionHeaders,
        body: JSON.stringify({ uuid }),
    });
}

export function staffAddWalkIn(): Promise<void> {
    return apiFetch<void>('/staff-access/walk-ins', { method: 'POST', headers: staffActionHeaders });
}

export function staffRemoveWalkIn(): Promise<void> {
    return apiFetch<void>('/staff-access/walk-ins/decrement', { method: 'POST', headers: staffActionHeaders });
}

export function staffLogout(): Promise<void> {
    return apiFetch<void>('/staff-access/logout', { method: 'POST', headers: staffActionHeaders });
}
