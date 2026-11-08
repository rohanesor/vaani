/**
 * Password hashing (scrypt) and HS256 session tokens using node:crypto only.
 */
import crypto from 'node:crypto';
import type { Request, Response, NextFunction } from 'express';
import { HttpError } from './http-utils.js';

const SCRYPT_N = 16384;
const KEYLEN = 64;

export function hashPassword(password: string): string {
  const salt = crypto.randomBytes(16);
  const hash = crypto.scryptSync(password, salt, KEYLEN, { N: SCRYPT_N });
  return `scrypt$${SCRYPT_N}$${salt.toString('base64')}$${hash.toString('base64')}`;
}

export function verifyPassword(password: string, stored: string): boolean {
  const [scheme, n, saltB64, hashB64] = stored.split('$');
  if (scheme !== 'scrypt' || !n || !saltB64 || !hashB64) return false;
  const expected = Buffer.from(hashB64, 'base64');
  const actual = crypto.scryptSync(password, Buffer.from(saltB64, 'base64'), expected.length, { N: Number(n) });
  return crypto.timingSafeEqual(expected, actual);
}

const b64url = (b: Buffer | string) => Buffer.from(b).toString('base64url');

export interface TokenClaims { sub: number; iat: number; exp: number; sid?: string }

export function signToken(userId: number, secret: string, ttlSeconds = 60 * 60 * 24 * 14, sessionId?: string): string {
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64url(JSON.stringify({ sub: userId, iat: now, exp: now + ttlSeconds, ...(sessionId ? { sid: sessionId } : {}) }));
  const sig = crypto.createHmac('sha256', secret).update(`${header}.${payload}`).digest('base64url');
  return `${header}.${payload}.${sig}`;
}

export function verifyToken(token: string, secret: string): TokenClaims | null {
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const [h, p, s] = parts;
  const expected = crypto.createHmac('sha256', secret).update(`${h}.${p}`).digest();
  const given = Buffer.from(s, 'base64url');
  if (given.length !== expected.length || !crypto.timingSafeEqual(given, expected)) return null;
  try {
    const header = JSON.parse(Buffer.from(h, 'base64url').toString());
    if (header.alg !== 'HS256') return null;
    const claims = JSON.parse(Buffer.from(p, 'base64url').toString()) as TokenClaims;
    if (typeof claims.sub !== 'number' || claims.exp < Math.floor(Date.now() / 1000)) return null;
    return claims;
  } catch {
    return null;
  }
}

declare module 'express-serve-static-core' {
  interface Request { userId?: number; sessionId?: string }
}

export function requireAuth(secret: string, isSessionActive?: (sessionId: string, userId: number) => boolean) {
  return (req: Request, _res: Response, next: NextFunction) => {
    const header = req.headers.authorization ?? '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : '';
    const claims = token ? verifyToken(token, secret) : null;
    if (!claims || (isSessionActive && (!claims.sid || !isSessionActive(claims.sid, claims.sub))))
      return next(new HttpError(401, 'unauthenticated', 'Sign in to continue.'));
    req.userId = claims.sub;
    req.sessionId = claims.sid;
    next();
  };
}
