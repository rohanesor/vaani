/**
 * Server configuration. All secrets are read from the environment only.
 * Secret values are never logged; `describeConfig()` reports presence only.
 */
import crypto from 'node:crypto';

export interface VaaniConfig {
  port: number;
  dbPath: string;
  jwtSecret: string;
  jwtSecretIsEphemeral: boolean;
  corsOrigin: string;
  anthropicApiKey?: string;
  claudeModel: string;
  claudeFastModel: string;
  gnaniApiKey?: string;
  gnaniBaseUrl: string;
  gnaniSttModel: string;
  gnaniTtsModel: string;
  gnaniTtsVoice: string;
  aiRequestsPerMinute: number;
  aiDailyTokenBudget: number;
  aiTimeoutMs: number;
}

function int(name: string, fallback: number): number {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const n = Number.parseInt(raw, 10);
  if (!Number.isFinite(n) || n < 0) throw new Error(`Invalid integer for ${name}`);
  return n;
}

function str(name: string): string | undefined {
  const v = process.env[name]?.trim();
  return v ? v : undefined;
}

export function loadConfig(overrides: Partial<VaaniConfig> = {}): VaaniConfig {
  const jwtSecret = str('VAANI_JWT_SECRET');
  const cfg: VaaniConfig = {
    port: int('PORT', 8787),
    dbPath: str('VAANI_DB_PATH') ?? './data/vaani.db',
    jwtSecret: jwtSecret ?? crypto.randomBytes(32).toString('hex'),
    jwtSecretIsEphemeral: !jwtSecret,
    corsOrigin: str('VAANI_CORS_ORIGIN') ?? 'http://localhost:5173',
    anthropicApiKey: str('ANTHROPIC_API_KEY'),
    claudeModel: str('VAANI_CLAUDE_MODEL') ?? 'claude-sonnet-5-5',
    claudeFastModel: str('VAANI_CLAUDE_FAST_MODEL') ?? 'claude-haiku-5-5',
    gnaniApiKey: str('GNANI_API_KEY'),
    gnaniBaseUrl: str('GNANI_BASE_URL') ?? 'https://api.vachana.ai',
    gnaniSttModel: str('GNANI_STT_MODEL') ?? 'prisma-v3.0',
    gnaniTtsModel: str('GNANI_TTS_MODEL') ?? 'timbre-v2.5',
    gnaniTtsVoice: str('GNANI_TTS_VOICE') ?? 'Kaveri',
    aiRequestsPerMinute: int('VAANI_AI_RPM', 20),
    aiDailyTokenBudget: int('VAANI_AI_DAILY_TOKENS', 200_000),
    aiTimeoutMs: int('VAANI_AI_TIMEOUT_MS', 45_000),
    ...overrides,
  };
  if (overrides.jwtSecret) cfg.jwtSecretIsEphemeral = false;
  if (cfg.jwtSecret.length < 32) throw new Error('VAANI_JWT_SECRET must be at least 32 characters');
  return cfg;
}

/** Safe, secret-free summary for startup logs and the /api/health endpoint. */
export function describeConfig(cfg: VaaniConfig) {
  return {
    claude: { configured: Boolean(cfg.anthropicApiKey), model: cfg.claudeModel, fastModel: cfg.claudeFastModel },
    gnani: { configured: Boolean(cfg.gnaniApiKey), sttModel: cfg.gnaniSttModel, ttsModel: cfg.gnaniTtsModel },
    jwtSecret: cfg.jwtSecretIsEphemeral ? 'ephemeral (set VAANI_JWT_SECRET to keep sessions across restarts)' : 'configured',
  };
}

/**
 * Production configuration validation.
 * Returns a list of error messages; empty list means valid.
 * Does not print secret values in error messages.
 */
export function validateProductionConfig(cfg: VaaniConfig): string[] {
  const errors: string[] = [];
  const isProduction = process.env.NODE_ENV === 'production';

  // Required in production: stable JWT secret
  if (isProduction && cfg.jwtSecretIsEphemeral) {
    errors.push('VAANI_JWT_SECRET is required in production (sessions will not persist across restarts)');
  }

  // Validate CORS origin format
  if (cfg.corsOrigin && !cfg.corsOrigin.startsWith('http://') && !cfg.corsOrigin.startsWith('https://')) {
    errors.push('VAANI_CORS_ORIGIN must be a valid HTTP(S) URL');
  }

  // Production should use HTTPS for CORS origin (warning, not error)
  if (isProduction && cfg.corsOrigin && cfg.corsOrigin.startsWith('http://') && !cfg.corsOrigin.includes('localhost')) {
    errors.push('WARNING: VAANI_CORS_ORIGIN uses HTTP in production; HTTPS is recommended');
  }

  // AI/speech providers are optional - messaging works without them
  // No validation errors for missing Claude/Gnani keys

  return errors;
}
