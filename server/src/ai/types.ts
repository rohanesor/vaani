/** Provider-agnostic AI contracts. The orchestrator depends on these, never on a vendor SDK. */

export type AIModelTier = 'reasoning' | 'fast';

export interface AIChatMessage { role: 'user' | 'assistant'; content: string }

export interface AICompletionRequest {
  system: string;
  messages: AIChatMessage[];
  tier: AIModelTier;
  maxTokens: number;
  /** When present the provider must return JSON matching this schema. */
  jsonSchema?: Record<string, unknown>;
  signal?: AbortSignal;
  /** Streaming callback. Providers that cannot stream call it once with the full text. */
  onText?: (delta: string) => void;
}

export interface AICompletionResult {
  text: string;
  model: string;
  inputTokens: number;
  outputTokens: number;
  stopReason: string | null;
}

export interface AIProvider {
  readonly name: string;
  isConfigured(): boolean;
  complete(req: AICompletionRequest): Promise<AICompletionResult>;
}

export type AIErrorCode =
  | 'not_configured' | 'disabled' | 'rate_limited' | 'budget_exceeded' | 'timeout'
  | 'cancelled' | 'invalid_response' | 'upstream' | 'invalid_request';

const STATUS: Record<AIErrorCode, number> = {
  not_configured: 503, disabled: 403, rate_limited: 429, budget_exceeded: 429, timeout: 504,
  cancelled: 499, invalid_response: 502, upstream: 502, invalid_request: 400,
};

export class AIError extends Error {
  readonly status: number;
  constructor(public code: AIErrorCode, message: string, public retryable = false) {
    super(message);
    this.status = STATUS[code];
  }
}
