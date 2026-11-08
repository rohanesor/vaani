import type { Request, Response, NextFunction } from 'express';

export class HttpError extends Error {
  constructor(public status: number, public code: string, message: string, public details?: unknown) {
    super(message);
  }
}

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction) {
  if (err instanceof HttpError) {
    res.status(err.status).json({ error: { code: err.code, message: err.message, details: err.details } });
    return;
  }
  if (err && typeof err === 'object' && 'type' in err && (err as { type: string }).type === 'entity.too.large') {
    res.status(413).json({ error: { code: 'payload_too_large', message: 'That upload is too large.' } });
    return;
  }
  console.error('[vaani] unhandled error', err instanceof Error ? err.stack : err);
  res.status(500).json({ error: { code: 'internal', message: 'Something went wrong on our side.' } });
}

/* Minimal validators: small, explicit, no schema library. */
export function reqString(body: unknown, key: string, opts: { min?: number; max?: number; optional?: false } ): string;
export function reqString(body: unknown, key: string, opts: { min?: number; max?: number; optional: true }): string | undefined;
export function reqString(body: unknown, key: string, opts: { min?: number; max?: number; optional?: boolean } = {}): string | undefined {
  const v = (body as Record<string, unknown> | undefined)?.[key];
  if (v === undefined || v === null || v === '') {
    if (opts.optional) return undefined;
    throw new HttpError(400, 'invalid_request', `${key} is required.`);
  }
  if (typeof v !== 'string') throw new HttpError(400, 'invalid_request', `${key} must be text.`);
  const t = v.trim();
  if (opts.min !== undefined && t.length < opts.min) throw new HttpError(400, 'invalid_request', `${key} must be at least ${opts.min} characters.`);
  if (opts.max !== undefined && t.length > opts.max) throw new HttpError(400, 'invalid_request', `${key} must be at most ${opts.max} characters.`);
  return t;
}

export function reqInt(v: unknown, name: string): number {
  const n = typeof v === 'number' ? v : Number.parseInt(String(v), 10);
  if (!Number.isInteger(n) || n <= 0) throw new HttpError(400, 'invalid_request', `${name} must be a positive integer.`);
  return n;
}

export const SUPPORTED_LANGUAGES: Record<string, string> = {
  'en-IN': 'English',
  'hi-IN': 'हिन्दी Hindi',
  'ta-IN': 'தமிழ் Tamil',
  'te-IN': 'తెలుగు Telugu',
  'kn-IN': 'ಕನ್ನಡ Kannada',
  'ml-IN': 'മലയാളം Malayalam',
  'bn-IN': 'বাংলা Bengali',
  'gu-IN': 'ગુજરાતી Gujarati',
  'mr-IN': 'मराठी Marathi',
};

export function reqLanguage(v: unknown, name = 'language'): string {
  if (typeof v !== 'string' || !(v in SUPPORTED_LANGUAGES)) throw new HttpError(400, 'invalid_request', `${name} is not a supported language.`);
  return v;
}
