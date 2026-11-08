/**
 * Claude implementation of AIProvider. The API key is read server-side only.
 * Retries for 408/409/429/5xx are delegated to the SDK (maxRetries).
 */
import Anthropic from '@anthropic-ai/sdk';
import { AIError, type AICompletionRequest, type AICompletionResult, type AIProvider } from './types.js';

export interface ClaudeProviderOptions {
  apiKey?: string;
  model: string;
  fastModel: string;
  timeoutMs: number;
  maxRetries?: number;
  /** Injected for tests. */
  client?: Pick<Anthropic, 'messages'>;
}

export class ClaudeProvider implements AIProvider {
  readonly name = 'claude';
  private client?: Pick<Anthropic, 'messages'>;

  constructor(private opts: ClaudeProviderOptions) {
    this.client = opts.client ?? (opts.apiKey
      ? new Anthropic({ apiKey: opts.apiKey, timeout: opts.timeoutMs, maxRetries: opts.maxRetries ?? 2 })
      : undefined);
  }

  isConfigured() { return Boolean(this.client); }

  async complete(req: AICompletionRequest): Promise<AICompletionResult> {
    if (!this.client) throw new AIError('not_configured', 'Claude is not configured on this server (ANTHROPIC_API_KEY is missing).');
    const model = req.tier === 'fast' ? this.opts.fastModel : this.opts.model;
    const params = {
      model,
      max_tokens: req.maxTokens,
      system: req.system,
      messages: req.messages,
      ...(req.jsonSchema ? { output_config: { format: { type: 'json_schema' as const, schema: req.jsonSchema } } } : {}),
    };
    try {
      if (req.onText) {
        const stream = this.client.messages.stream(params, { signal: req.signal });
        stream.on('text', (delta: string) => req.onText!(delta));
        const msg = await stream.finalMessage();
        return this.toResult(msg, model);
      }
      const msg = await this.client.messages.create(params, { signal: req.signal });
      return this.toResult(msg as Anthropic.Message, model);
    } catch (e) {
      throw mapError(e, req.signal);
    }
  }

  private toResult(msg: Anthropic.Message, model: string): AICompletionResult {
    const text = msg.content.filter((b): b is Anthropic.TextBlock => b.type === 'text').map((b) => b.text).join('');
    return { text, model: msg.model ?? model, inputTokens: msg.usage?.input_tokens ?? 0, outputTokens: msg.usage?.output_tokens ?? 0, stopReason: msg.stop_reason ?? null };
  }
}

export function mapError(e: unknown, signal?: AbortSignal): AIError {
  if (e instanceof AIError) return e;
  if (signal?.aborted || e instanceof Anthropic.APIUserAbortError) return new AIError('cancelled', 'Request cancelled.');
  if (e instanceof Anthropic.APIConnectionTimeoutError) return new AIError('timeout', 'Claude took too long to respond.', true);
  if (e instanceof Anthropic.RateLimitError) return new AIError('rate_limited', 'Claude is rate-limiting requests right now. Try again shortly.', true);
  if (e instanceof Anthropic.AuthenticationError || e instanceof Anthropic.PermissionDeniedError)
    return new AIError('not_configured', 'The server’s Claude credentials were rejected.');
  if (e instanceof Anthropic.BadRequestError) return new AIError('invalid_request', 'Claude rejected the request.');
  if (e instanceof Anthropic.APIConnectionError) return new AIError('upstream', 'Could not reach Claude.', true);
  if (e instanceof Anthropic.APIError) return new AIError('upstream', `Claude returned an error (${e.status ?? 'unknown'}).`, true);
  return new AIError('upstream', 'Unexpected AI provider error.', true);
}
