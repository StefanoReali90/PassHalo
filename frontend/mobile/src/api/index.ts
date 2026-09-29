import { apiFetch } from './client';
import type {
  BookingRequest,
  BookingResponse,
  CheckInResponse,
  PassHaloEvent,
  EventDashboardResponse,
  EventRequest,
  LoginResponse,
  MyEvent,
  User,
} from '../types';

const segment = (value: string) => encodeURIComponent(value.trim());

export interface BrevoStatus {
  connected: boolean;
  listId: number | null;
  pendingContacts: number;
}

export const api = {
  brevoStatus() {
    return apiFetch<BrevoStatus>('/marketing/brevo');
  },

  connectBrevo(apiKey: string, listId: number) {
    return apiFetch<BrevoStatus>('/marketing/brevo', {
      method: 'POST',
      body: JSON.stringify({ apiKey, listId }),
      timeoutMs: 25_000,
    });
  },

  rotateBrevoKey(apiKey: string) {
    return apiFetch<BrevoStatus>('/marketing/brevo', {
      method: 'PUT',
      body: JSON.stringify({ apiKey }),
      timeoutMs: 25_000,
    });
  },

  disconnectBrevo() {
    return apiFetch<void>('/marketing/brevo', { method: 'DELETE', timeoutMs: 60_000 });
  },

  login(email: string, password: string) {
    return apiFetch<LoginResponse>('/user/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
  },

  registerAdmin(data: { name: string; surname: string; email: string; password: string }) {
    return apiFetch<User>('/user/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  currentUser() {
    return apiFetch<User>('/user/me');
  },

  events() {
    return apiFetch<PassHaloEvent[]>('/events');
  },

  myEvents() {
    return apiFetch<MyEvent[]>('/events/my-events');
  },

  createEvent(data: EventRequest) {
    return apiFetch<PassHaloEvent>('/events/', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  updateEvent(eventId: number, data: EventRequest) {
    return apiFetch<PassHaloEvent>(`/events/${eventId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  },

  deleteEvent(eventId: number) {
    return apiFetch<void>(`/events/${eventId}`, { method: 'DELETE' });
  },

  dashboard(eventId: number) {
    return apiFetch<EventDashboardResponse>(`/events/${eventId}/dashboard`);
  },

  createBooking(data: BookingRequest) {
    return apiFetch<BookingResponse>('/bookings/', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  resendBookingQr(eventId: number, uuid: string) {
    return apiFetch<void>(`/bookings/events/${eventId}/${segment(uuid)}/resend-qr`, { method: 'POST' });
  },

  async bookings() {
    const events = (await api.myEvents()).filter((event) => event.role === 'EVENT_ADMIN');
    const groups = await Promise.all(events.map((event) =>
      apiFetch<BookingResponse[]>(`/bookings/events/${event.id}`),
    ));
    return { bookings: groups.flat(), ownerEventIds: events.filter((event) => event.owner && event.eventState !== 'FINISHED').map((event) => event.id) };
  },

  cancelBooking(uuid: string) {
    return apiFetch<void>(`/bookings/${segment(uuid)}`, { method: 'DELETE' });
  },

  checkIn(uuid: string, eventId: number) {
    return apiFetch<CheckInResponse>(`/bookings/events/${eventId}/check-in/${segment(uuid)}`, { method: 'PATCH' });
  },

  incrementWalkIn(eventId: number) {
    return apiFetch<void>(`/events/${eventId}/walk-in`, { method: 'PATCH' });
  },

  decrementWalkIn(eventId: number) {
    return apiFetch<void>(`/events/${eventId}/walk-in/decrement`, { method: 'PATCH' });
  },

  closeEvent(eventId: number) {
    return apiFetch<void>(`/events/${eventId}/close`, { method: 'PATCH' });
  },
};
