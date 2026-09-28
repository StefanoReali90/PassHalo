export type UserRole = 'ADMIN' | 'STAFF';

export interface User {
  name: string;
  surname: string;
  email: string;
  role: UserRole;
}

export interface LoginResponse {
  token: string;
}

export type EventState = 'WAITING' | 'IN_PROGRESS' | 'FINISHED';

export interface EventFaq {
  question: string;
  answer: string;
}

export interface PassHaloEvent {
  id: number;
  name: string;
  startDateTime: string;
  endDateTime: string;
  normalPrice: number;
  bookingPrice: number;
  totalTickets: number;
  description: string;
  imageUrl: string;
  location: string;
  eventState: EventState;
  videoUrl?: string | null;
  faqs?: EventFaq[];
  organizerName: string;
}

export interface MyEvent extends PassHaloEvent {
  role: 'EVENT_ADMIN' | 'STAFF';
  owner: boolean;
}

export interface EventRequest {
  name: string;
  description: string;
  location: string;
  start: string;
  end: string;
  imageUrl: string;
  videoUrl?: string | null;
  faqs?: EventFaq[];
  totalTickets: number;
  normalPrice: number;
  bookingPrice: number;
}

export interface BookingRequest {
  name: string;
  surname: string;
  email: string;
  phone: string;
  eventId: number;
  marketingConsent: boolean;
}

export type BookingStatus = 'CREATED' | 'VALIDATED' | 'CANCELLED';

export interface BookingResponse {
  uuid: string;
  name: string;
  surname: string;
  email: string;
  phone: string | null;
  eventId: number;
  eventName: string;
  bookingStatus: BookingStatus;
  createdAt: string;
  qrCodeBase64: string;
  marketingConsent: boolean;
}

export interface CheckInResponse {
  eventName: string;
}

export interface EventDashboardResponse {
  eventId: number;
  eventName: string;
  totalTickets: number;
  totalBookings: number;
  checkedInCount: number;
  noShowCount: number;
  attendanceRate: number;
  estimatedBookingRevenue: number;
  walkInCount: number;
  totalAttendees: number;
  totalRevenue: number;
}
