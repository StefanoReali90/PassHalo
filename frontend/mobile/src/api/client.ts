const ENV_API_URL = process.env.EXPO_PUBLIC_API_URL?.trim() ?? '';

let apiBaseUrl = ENV_API_URL ? normalizeApiBaseUrl(ENV_API_URL) : '';
let authToken: string | null = null;

interface ApiProblem {
  detail?: string;
  message?: string;
  title?: string;
  errors?: string[];
}

interface ApiOptions extends RequestInit {
  timeoutMs?: number;
}

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status = 0) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export function normalizeApiBaseUrl(value: string) {
  const normalized = value.trim().replace(/\/+$/, '');
  if (!/^https?:\/\//i.test(normalized)) {
    throw new Error('Inserisci un indirizzo completo che inizi con http:// oppure https://.');
  }
  return normalized;
}

export function getDefaultApiBaseUrl() {
  return ENV_API_URL ? normalizeApiBaseUrl(ENV_API_URL) : '';
}

export function configureApi(baseUrl: string, token: string | null) {
  apiBaseUrl = baseUrl ? normalizeApiBaseUrl(baseUrl) : '';
  authToken = token;
}

export function setApiToken(token: string | null) {
  authToken = token;
}

function problemMessage(raw: string, status: number) {
  if (raw) {
    try {
      const problem = JSON.parse(raw) as ApiProblem;
      const details = Array.isArray(problem.errors) && problem.errors.length > 0
        ? ` ${problem.errors.join(' · ')}`
        : '';
      return `${problem.detail ?? problem.message ?? problem.title ?? `Richiesta non riuscita (${status}).`}${details}`;
    } catch {
      // The server did not return a problem-details JSON body.
    }
  }
  return `Richiesta non riuscita (HTTP ${status}).`;
}

export async function apiFetch<T>(endpoint: string, options: ApiOptions = {}): Promise<T> {
  if (!apiBaseUrl) {
    throw new ApiError('Configura prima l’indirizzo del server PassHalo.');
  }

  const { timeoutMs = 15_000, ...requestOptions } = options;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMs);
  const headers = new Headers(requestOptions.headers);
  headers.set('Accept', 'application/json');
  if (requestOptions.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (authToken) {
    headers.set('Authorization', `Bearer ${authToken}`);
  }

  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${endpoint}`, {
      ...requestOptions,
      headers,
      signal: controller.signal,
    });
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') {
      throw new ApiError('Il server sta impiegando troppo tempo a rispondere.');
    }
    throw new ApiError('Server PassHalo non raggiungibile. Controlla l’indirizzo e la connessione.');
  } finally {
    clearTimeout(timeout);
  }

  const raw = await response.text();
  if (!response.ok) {
    throw new ApiError(problemMessage(raw, response.status), response.status);
  }
  if (response.status === 204 || !raw.trim()) return null as T;

  try {
    return JSON.parse(raw) as T;
  } catch {
    throw new ApiError('Il server ha restituito una risposta non valida.', response.status);
  }
}
