export type UserRole = 'ADMIN' | 'STAFF';
export type PaymentMethod = 'CASH' | 'CARD';

export interface User {
    name: string;
    surname: string;
    email: string;
    role: UserRole;
}

export interface LoginResponse {
    token: string;
}

export interface AdminRegisterRequest {
    name: string;
    surname: string;
    email: string;
    password: string;
}

export interface ForgotPasswordRequest {
    email: string;
}

export interface ResetPasswordRequest {
    token: string;
    newPassword: string;
    confirmationPassword: string;
}

export interface ChangePasswordRequest {
    oldPassword: string;
    newPassword: string;
    confirmationPassword: string;
}

export type EventState = 'WAITING' | 'IN_PROGRESS' | 'FINISHED';

export interface EventFaq {
    question: string;
    answer: string;
}

export interface Event {
    id: number;
    name: string;
    description: string;
    location: string;
    startDateTime: string;
    endDateTime: string;
    bookingPrice: number;
    normalPrice: number;
    totalTickets: number;
    eventState: EventState;
    imageUrl: string;
    videoUrl?: string | null;
    faqs?: EventFaq[];
    organizerName: string;
}

export type EventRole = 'EVENT_ADMIN' | 'STAFF';
export type MembershipState = 'ACTIVE' | 'REVOKED';
export type InviteState = 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'REVOKED';

export interface MyEvent extends Event {
    role: EventRole;
    owner: boolean;
}

export interface EventInvitationResponse {
    id: number;
    eventId: number;
    recipientEmail: string;
    proposedRole: EventRole;
    state: InviteState;
    createdAt: string;
    expiresAt: string;
    acceptedAt: string | null;
    revokedAt: string | null;
}

export interface EventMembershipResponse {
    id: number;
    eventId: number;
    collaboratorId: number;
    collaboratorName: string;
    collaboratorSurname: string;
    collaboratorEmail: string;
    role: EventRole;
    state: MembershipState;
    validFrom: string;
    validUntil: string | null;
    revokedAt: string | null;
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
    checkedInCashCount: number;
    checkedInCardCount: number;
    checkedInUnrecordedCount: number;
    walkInCashCount: number;
    walkInCardCount: number;
    walkInUnrecordedCount: number;
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
