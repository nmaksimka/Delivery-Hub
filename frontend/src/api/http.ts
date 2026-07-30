import type { ZodType } from 'zod';
import { sessionStore, type Session } from '../auth/sessionStore';
import { sessionFrom, tokenResponseSchema } from './tokens';

type QueryValue = string | number | boolean | undefined;

type RequestOptions<T> = {
  schema: ZodType<T>;
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  query?: Record<string, QueryValue>;
  signal?: AbortSignal;
};

const statusMessages: Record<number, string> = {
  400: 'Проверьте введённые данные',
  401: 'Сессия истекла, войдите заново',
  403: 'Недостаточно прав для этого действия',
  404: 'Ничего не нашлось',
  409: 'Такая запись уже существует',
  429: 'Слишком много запросов, попробуйте чуть позже',
};

export class ApiError extends Error {
  readonly status: number;
  readonly detail: string | null;

  constructor(status: number, message: string, detail: string | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.detail = detail;
  }
}

export function errorMessage(error: unknown, fallback = 'Что-то пошло не так, попробуйте ещё раз') {
  return error instanceof ApiError ? error.message : fallback;
}

function buildUrl(path: string, query?: Record<string, QueryValue>) {
  if (!query) {
    return path;
  }
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined) {
      params.set(key, String(value));
    }
  });
  const search = params.toString();
  return search ? `${path}?${search}` : path;
}

async function readDetail(response: Response): Promise<string | null> {
  try {
    const payload: unknown = await response.json();
    if (payload && typeof payload === 'object') {
      const fields = payload as Record<string, unknown>;
      const detail = fields.detail ?? fields.message ?? fields.error;
      return typeof detail === 'string' ? detail : null;
    }
    return null;
  } catch {
    return null;
  }
}

function messageFor(status: number) {
  if (status >= 500) {
    return 'Сервис временно недоступен, попробуйте позже';
  }
  return statusMessages[status] ?? 'Запрос не выполнен, попробуйте ещё раз';
}

const authPathPrefix = '/api/auth/';

let pendingRefresh: Promise<Session | null> | null = null;

async function exchangeRefreshToken(refreshToken: string): Promise<Session | null> {
  try {
    const response = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) {
      return null;
    }
    const parsed = tokenResponseSchema.safeParse(await response.json());
    return parsed.success ? sessionFrom(parsed.data) : null;
  } catch {
    return null;
  }
}

function refreshSession(expired: Session): Promise<Session | null> {
  const latest = sessionStore.get();
  if (latest && latest.accessToken !== expired.accessToken) {
    return Promise.resolve(latest);
  }
  pendingRefresh ??= exchangeRefreshToken(expired.refreshToken).then((renewed) => {
    pendingRefresh = null;
    if (renewed) {
      sessionStore.set(renewed);
    } else {
      sessionStore.clear();
    }
    return renewed;
  });
  return pendingRefresh;
}

async function send<T>(path: string, options: RequestOptions<T>, session: Session | null) {
  const headers = new Headers({ Accept: 'application/json' });
  if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
  }
  if (session) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
  }
  try {
    return await fetch(buildUrl(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (error) {
    if (options.signal?.aborted) {
      throw error;
    }
    throw new ApiError(0, 'Нет связи с сервером, проверьте интернет');
  }
}

export async function request<T>(path: string, options: RequestOptions<T>): Promise<T> {
  const session = sessionStore.get();
  let response = await send(path, options, session);

  if (response.status === 401 && session && !path.startsWith(authPathPrefix)) {
    const renewed = await refreshSession(session);
    if (renewed) {
      response = await send(path, options, renewed);
      if (response.status === 401) {
        sessionStore.clear();
      }
    }
  }
  if (!response.ok) {
    throw new ApiError(response.status, messageFor(response.status), await readDetail(response));
  }

  const payload: unknown = response.status === 204 ? undefined : await response.json();
  const parsed = options.schema.safeParse(payload);
  if (!parsed.success) {
    throw new ApiError(response.status, 'Сервер вернул неожиданный ответ');
  }
  return parsed.data;
}
