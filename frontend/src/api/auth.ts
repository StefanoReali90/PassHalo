import { apiFetch } from './client';
import type {
    AdminRegisterRequest,
    ChangePasswordRequest,
    ForgotPasswordRequest,
    LoginResponse,
    ResetPasswordRequest,
    User,
} from '../types';

export interface LoginCredentials {
    email: string;
    password: string;
}

export function login(credentials: LoginCredentials): Promise<LoginResponse> {
    return apiFetch<LoginResponse>('/user/login', {
        method: 'POST',
        body: JSON.stringify(credentials),
    });
}

export function getCurrentUser(): Promise<User> {
    return apiFetch<User>('/user/me');
}

export function logout(): Promise<void> {
    return apiFetch<void>('/user/logout', { method: 'POST' });
}

export function registerAdmin(data: AdminRegisterRequest): Promise<User> {
    return apiFetch<User>('/user/register', {
        method: 'POST',
        body: JSON.stringify(data),
    });
}

export function getUsers(): Promise<User[]> {
    return apiFetch<User[]>('/user');
}

export function getUserByEmail(email: string): Promise<User> {
    const query = new URLSearchParams({ email });
    return apiFetch<User>(`/user/search?${query.toString()}`);
}

export function getUserById(userId: number): Promise<User> {
    return apiFetch<User>(`/user/${userId}`);
}

export function deleteUser(userId: number): Promise<void> {
    return apiFetch<void>(`/user/${userId}`, { method: 'DELETE' });
}

export function recoverPassword(data: ForgotPasswordRequest): Promise<void> {
    return apiFetch<void>('/user/recover-password', {
        method: 'POST',
        body: JSON.stringify(data),
    });
}

export function changePassword(userId: number, data: ChangePasswordRequest): Promise<void> {
    return apiFetch<void>(`/user/${userId}/change-password`, {
        method: 'PATCH',
        body: JSON.stringify(data),
    });
}

export function resetPassword(data: ResetPasswordRequest): Promise<void> {
    return apiFetch<void>('/user/reset-password', {
        method: 'PATCH',
        body: JSON.stringify(data),
    });
}
