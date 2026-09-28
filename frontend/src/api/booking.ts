import { apiFetch } from './client';
import type { BookingRequest, BookingResponse, CheckInResponse } from '../types';

const segment = (value: string) => encodeURIComponent(value.trim());

export function createBooking(data: BookingRequest): Promise<BookingResponse> {
    return apiFetch<BookingResponse>('/bookings/', {
        method: 'POST',
        body: JSON.stringify(data),
    });
}

export function getBookingByUUID(uuid: string): Promise<BookingResponse> {
    return apiFetch<BookingResponse>(`/bookings/${segment(uuid)}`);
}

export function getBookingsByEventId(eventId: number): Promise<BookingResponse[]> {
    return apiFetch<BookingResponse[]>(`/bookings/events/${eventId}`);
}

export function getBookingsByEmail(email: string): Promise<BookingResponse[]> {
    return apiFetch<BookingResponse[]>(`/bookings/email/${segment(email)}`);
}

export function getBookingsByEventAndEmail(eventId: number, email: string): Promise<BookingResponse[]> {
    return apiFetch<BookingResponse[]>(`/bookings/event/${eventId}/email/${segment(email)}`);
}

export function getBookingById(bookingId: number): Promise<BookingResponse> {
    return apiFetch<BookingResponse>(`/bookings/bookingId/${bookingId}`);
}

export function getBookings(): Promise<BookingResponse[]> {
    return apiFetch<BookingResponse[]>('/bookings/');
}

export function checkInBooking(uuid: string, eventId: number): Promise<CheckInResponse> {
    return apiFetch<CheckInResponse>(`/bookings/events/${eventId}/check-in/${segment(uuid)}`, { method: 'PATCH' });
}

export function cancelBooking(uuid: string): Promise<void> {
    return apiFetch<void>(`/bookings/${segment(uuid)}`, { method: 'DELETE' });
}
