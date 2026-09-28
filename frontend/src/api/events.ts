import { apiFetch } from './client';
import type { Event, EventDashboardResponse, EventRequest, MyEvent } from '../types';

export const EVENT_ACCESS_CHANGED = 'passhalo:event-access-changed';

export function notifyEventAccessChanged() {
    window.dispatchEvent(new Event(EVENT_ACCESS_CHANGED));
}

export function getEvents(): Promise<Event[]> {
    return apiFetch<Event[]>('/events');
}

export function getMyEvents(): Promise<MyEvent[]> {
    return apiFetch<MyEvent[]>('/events/my-events');
}

export function getEventById(eventId: number): Promise<Event> {
    return apiFetch<Event>(`/events/${eventId}`);
}

export function getEventDashboard(eventId: number): Promise<EventDashboardResponse> {
    return apiFetch<EventDashboardResponse>(`/events/${eventId}/dashboard`);
}

export function createEvent(data: EventRequest): Promise<Event> {
    return apiFetch<Event>('/events/', {
        method: 'POST',
        body: JSON.stringify(data),
    });
}

export function updateEvent(eventId: number, data: EventRequest): Promise<Event> {
    return apiFetch<Event>(`/events/${eventId}`, {
        method: 'PUT',
        body: JSON.stringify(data),
    });
}

export function deleteEvent(eventId: number): Promise<void> {
    return apiFetch<void>(`/events/${eventId}`, { method: 'DELETE' });
}

export function incrementWalkInCount(eventId: number): Promise<void> {
    return apiFetch<void>(`/events/${eventId}/walk-in`, { method: 'PATCH' });
}

export function decrementWalkInCount(eventId: number): Promise<void> {
    return apiFetch<void>(`/events/${eventId}/walk-in/decrement`, { method: 'PATCH' });
}

export function closeEvent(eventId: number): Promise<void> {
    return apiFetch<void>(`/events/${eventId}/close`, { method: 'PATCH' });
}
