import type { TokenPair } from './types';

const BASE: string = (import.meta.env.VITE_API_URL as string | undefined) ?? '';
const KEY = 'gb.tokens';

interface Stored { accessToken: string; refreshToken: string }

export class ApiError extends Error {
  status: number;
  errors?: Record<string, string>;
  constructor(status: number, message: string, errors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.errors = errors;
  }
}

let tokens: Stored | null = null;
try {
  const s = localStorage.getItem(KEY);
  tokens = s ? (JSON.parse(s) as Stored) : null;
} catch {
  tokens = null;
}

let onAuthLost: () => void = () => {};
export function setAuthLostHandler(fn: () => void) { onAuthLost = fn; }

export function saveTokens(t: TokenPair | null) {
  tokens = t ? { accessToken: t.accessToken, refreshToken: t.refreshToken } : null;
  try {
    if (tokens) localStorage.setItem(KEY, JSON.stringify(tokens));
    else localStorage.removeItem(KEY);
  } catch { /* private mode: keep tokens in memory only */ }
}

export const hasTokens = () => tokens !== null;
export const currentRefreshToken = () => tokens?.refreshToken ?? null;

async function parse(res: Response): Promise<unknown> {
  if (res.status === 204) return undefined;
  const text = await res.text();
  if (!text) return undefined;
  try { return JSON.parse(text); } catch { return text; }
}

function toError(res: Response, body: unknown): ApiError {
  const b = (typeof body === 'object' && body !== null ? body : {}) as { detail?: string; title?: string; errors?: Record<string, string> };
  let msg = b.detail || b.title || `Request failed (${res.status})`;
  if (b.errors) msg = Object.entries(b.errors).map(([k, v]) => `${k}: ${v}`).join('; ');
  return new ApiError(res.status, msg, b.errors);
}

let refreshing: Promise<boolean> | null = null;

/** Single-flight refresh: concurrent 401s share one refresh call, because refresh tokens are single-use. */
function refreshTokens(): Promise<boolean> {
  if (!tokens) return Promise.resolve(false);
  if (!refreshing) {
    const rt = tokens.refreshToken;
    refreshing = fetch(`${BASE}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: rt }),
    })
      .then(async (res) => {
        if (!res.ok) return false;
        saveTokens((await res.json()) as TokenPair);
        return true;
      })
      .catch(() => false)
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

export interface Options { method?: string; body?: unknown; query?: Record<string, string | number | boolean | null | undefined>; auth?: boolean }

export async function api<T = void>(path: string, opts: Options = {}): Promise<T> {
  const { method = 'GET', body, query, auth = true } = opts;
  const qs = query
    ? '?' + Object.entries(query).filter(([, v]) => v !== undefined && v !== null && v !== '').map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&')
    : '';
  const send = () => fetch(`${BASE}${path}${qs === '?' ? '' : qs}`, {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(auth && tokens ? { Authorization: `Bearer ${tokens.accessToken}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  let res = await send();
  if (res.status === 401 && auth && tokens) {
    if (await refreshTokens()) res = await send();
    else { saveTokens(null); onAuthLost(); }
  }
  const data = await parse(res);
  if (!res.ok) throw toError(res, data);
  return data as T;
}
