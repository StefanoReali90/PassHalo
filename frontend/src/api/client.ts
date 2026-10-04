const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
export const AUTH_EXPIRED_EVENT = 'passhalo:auth-expired';

interface ApiProblem {
    detail?: string;
    message?: string;
    title?: string;
    errors?: string[];
}

interface ApiRequestOptions extends RequestInit {
    timeoutMs?: number;
    responseType?: 'json' | 'blob';
}

export class ApiError extends Error {
    readonly status: number;
    readonly problem: ApiProblem | null;

    constructor(message: string, status: number, problem: ApiProblem | null = null) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.problem = problem;
    }
}

export class NetworkError extends Error {
    constructor(message: string) {
        super(message);
        this.name = 'NetworkError';
    }
}

export function isApiError(error: unknown): error is ApiError {
    return error instanceof ApiError;
}

function emitExpiredSession(endpoint: string, status: number) {
    const sessionProbe = endpoint === '/user/me';
    const loginAttempt = endpoint === '/user/login';
    const anonymousStaffSession = endpoint.startsWith('/staff-access/');
    if (status === 401 && !sessionProbe && !loginAttempt && !anonymousStaffSession) {
        window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
    }
}

function parseProblem(raw: string): ApiProblem | null {
    if (!raw) return null;
    try {
        return JSON.parse(raw) as ApiProblem;
    } catch {
        return null;
    }
}

export async function apiFetch<T>(endpoint: string, options: ApiRequestOptions = {}): Promise<T> {
    const { timeoutMs = 15_000, signal: externalSignal, responseType = 'json', ...requestOptions } = options;
    const controller = new AbortController();
    let timedOut = false;
    const forwardAbort = () => controller.abort(externalSignal?.reason);
    externalSignal?.addEventListener('abort', forwardAbort, { once: true });
    const timeout = window.setTimeout(() => {
        timedOut = true;
        controller.abort();
    }, timeoutMs);

    const headers = new Headers(requestOptions.headers);
    headers.set('Accept', responseType === 'blob' ? 'text/csv' : 'application/json');
    if (requestOptions.body && !(requestOptions.body instanceof FormData) && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
    }

    let response: Response;
    try {
        response = await fetch(`${API_BASE_URL}${endpoint}`, {
            ...requestOptions,
            headers,
            credentials: 'include',
            signal: controller.signal,
        });
    } catch (error) {
        if (timedOut) throw new NetworkError('Il server sta impiegando troppo tempo a rispondere. Riprova.');
        if (externalSignal?.aborted) throw error;
        throw new NetworkError('PassHalo non riesce a raggiungere il server. Controlla la connessione e riprova.');
    } finally {
        window.clearTimeout(timeout);
        externalSignal?.removeEventListener('abort', forwardAbort);
    }

    if (!response.ok) {
        const rawError = await response.text();
        const problem = parseProblem(rawError);
        const validationDetails = Array.isArray(problem?.errors) && problem.errors.length > 0
            ? ` ${problem.errors.join(' · ')}`
            : '';
        const message = `${problem?.detail
            || problem?.message
            || problem?.title
            || `Richiesta non riuscita (HTTP ${response.status}).`}${validationDetails}`;
        emitExpiredSession(endpoint, response.status);
        throw new ApiError(message, response.status, problem);
    }

    if (response.status === 204) return null as T;

    if (responseType === 'blob') return await response.blob() as T;

    const body = await response.text();
    if (!body.trim()) return null as T;
    try {
        return JSON.parse(body) as T;
    } catch {
        throw new ApiError('Il server ha restituito una risposta non valida.', response.status);
    }
}
