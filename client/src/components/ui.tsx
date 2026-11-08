import { createContext, useCallback, useContext, useEffect, useRef, useState, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { LoaderCircle, X, CircleAlert, Info, Check } from 'lucide-react';
import { initials } from '../lib/format';

/** VAANI mark: a speech bubble whose voice bars form a “V”. */
export function Logo({ size = 28, withWord = false }: { size?: number; withWord?: boolean }) {
  return (
    <span className="logo">
      <svg width={size} height={size} viewBox="0 0 40 40" role="img" aria-label={withWord ? undefined : 'VAANI'} aria-hidden={withWord ? 'true' : undefined}>
        <path d="M20 3c9.4 0 17 6.9 17 15.4 0 8.5-7.6 15.4-17 15.4-1.7 0-3.3-.2-4.8-.6L7.5 37l1.3-7.1C5.2 27.1 3 22.9 3 18.4 3 9.9 10.6 3 20 3Z" fill="var(--color-brand-primary)" />
        <g stroke="var(--color-on-brand)" strokeWidth="2.6" strokeLinecap="round">
          <path d="M11.5 14.5v3" /><path d="M15.75 15v6.5" /><path d="M20 16.5v8" /><path d="M24.25 15v6.5" /><path d="M28.5 14.5v3" />
        </g>
      </svg>
      {withWord && <span className="logo-word">vaani</span>}
    </span>
  );
}

/**
 * The AI mark — a point with an orbit: “something is listening with you”.
 * Used instead of sparkles, and only where AI acts or speaks.
 */
export function AIMark({ size = 16, className = '' }: { size?: number; className?: string }) {
  return (
    <svg className={`ai-mark ${className}`} width={size} height={size} viewBox="0 0 16 16" aria-hidden="true">
      <circle cx="8" cy="8" r="2.4" fill="currentColor" />
      <path d="M8 2.2a5.8 5.8 0 1 1-5.6 4.3" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

/** The voice mark — three bars, used for voice affordances and voice activity. */
export function VoiceMark({ size = 16, live = false }: { size?: number; live?: boolean }) {
  return (
    <svg className={`voice-mark${live ? ' live' : ''}`} width={size} height={size} viewBox="0 0 16 16" aria-hidden="true">
      <rect x="2" y="5" width="2.4" height="6" rx="1.2" fill="currentColor" />
      <rect x="6.8" y="2" width="2.4" height="12" rx="1.2" fill="currentColor" />
      <rect x="11.6" y="4" width="2.4" height="8" rx="1.2" fill="currentColor" />
    </svg>
  );
}

const SCRIPT_GLYPH: Record<string, string> = { en: 'En', hi: 'हि', ta: 'த', te: 'తె', kn: 'ಕ', ml: 'മ', bn: 'বা', gu: 'ગુ', mr: 'म' };
/** Tiny script glyph for a language (e.g. “த” for Tamil). Subtle on purpose. */
export function LangGlyph({ code, title }: { code: string; title: string }) {
  const base = code.split('-')[0];
  return <span className="lang-glyph" lang={base} title={title} aria-label={title}>{SCRIPT_GLYPH[base] ?? base.toUpperCase()}</span>;
}

export function Avatar({ name, hue, size = 40, online, group }: { name: string; hue: number; size?: number; online?: boolean; group?: boolean }) {
  return (
    <span className={`avatar${group ? ' avatar-group' : ''}`} style={{ width: size, height: size, fontSize: Math.round(size * 0.38), ['--hue' as string]: hue }} aria-hidden="true">
      {initials(name)}
      {online && <span className="presence" />}
    </span>
  );
}

type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'ai' | 'voice'; size?: 'sm' | 'md'; loading?: boolean; icon?: ReactNode };
export function Button({ variant = 'secondary', size = 'md', loading, icon, children, className = '', disabled, type = 'button', ...rest }: BtnProps) {
  return (
    <button type={type} className={`btn btn-${variant} btn-${size} ${className}`} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading ? <LoaderCircle className="spin" size={16} aria-hidden="true" /> : icon}
      {children && <span>{children}</span>}
    </button>
  );
}

export function IconButton({ label, children, className = '', tone, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string; tone?: 'ai' | 'voice' | 'brand' }) {
  return <button type="button" className={`icon-btn ${tone ? `tone-${tone}` : ''} ${className}`} aria-label={label} title={label} {...rest}>{children}</button>;
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return <span className="spinner" role="status"><LoaderCircle className="spin" size={18} aria-hidden="true" /><span className="sr-only">{label}</span></span>;
}

export function EmptyState({ icon, title, children, action }: { icon?: ReactNode; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      {icon && <div className="empty-icon" aria-hidden="true">{icon}</div>}
      <h3>{title}</h3>
      {children && <p>{children}</p>}
      {action}
    </div>
  );
}

export function Banner({ tone = 'info', children, action }: { tone?: 'info' | 'warning' | 'error' | 'success'; children: ReactNode; action?: ReactNode }) {
  const Icon = tone === 'error' || tone === 'warning' ? CircleAlert : tone === 'success' ? Check : Info;
  return (
    <div className={`banner banner-${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon size={16} aria-hidden="true" />
      <div className="banner-body">{children}</div>
      {action}
    </div>
  );
}

/** Small-caps label used across VAANI for section and annotation headings. */
export const Label = ({ children, tone, as: As = 'span' }: { children: ReactNode; tone?: 'ai' | 'voice' | 'brand'; as?: 'span' | 'h2' | 'h3' }) =>
  <As className={`label${tone ? ` label-${tone}` : ''}`}>{children}</As>;

/** Accessible modal on <dialog>: focus trap, Esc, scrim click. Right drawer on wide screens, bottom sheet on phones. */
export function Sheet({ open, onClose, title, subtitle, children, footer, eyebrow }: {
  open: boolean; onClose: () => void; title: string; subtitle?: ReactNode; children: ReactNode; footer?: ReactNode; eyebrow?: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current; if (!d) return;
    if (open && !d.open) d.showModal();
    if (!open && d.open) d.close();
  }, [open]);
  return (
    <dialog ref={ref} className="sheet" onClose={onClose} onCancel={(e) => { e.preventDefault(); onClose(); }}
      onClick={(e) => { if (e.target === ref.current) onClose(); }} aria-labelledby="sheet-title">
      {open && (
        <div className="sheet-inner">
          <header className="sheet-head">
            <div className="sheet-titles">
              {eyebrow}
              <h2 id="sheet-title">{title}</h2>
              {subtitle && <div className="sheet-sub">{subtitle}</div>}
            </div>
            <IconButton label="Close" onClick={onClose}><X size={18} /></IconButton>
          </header>
          <div className="sheet-body">{children}</div>
          {footer && <footer className="sheet-foot">{footer}</footer>}
        </div>
      )}
    </dialog>
  );
}

/* ---------------- toasts ---------------- */
interface Toast { id: number; text: string; tone: 'info' | 'error' | 'success' }
const ToastCtx = createContext<(text: string, tone?: Toast['tone']) => void>(() => {});
export const useToast = () => useContext(ToastCtx);
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const push = useCallback((text: string, tone: Toast['tone'] = 'info') => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t.slice(-2), { id, text, tone }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4200);
  }, []);
  return (
    <ToastCtx.Provider value={push}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((t) => <div key={t.id} className={`toast toast-${t.tone}`} role={t.tone === 'error' ? 'alert' : 'status'}>{t.text}</div>)}
      </div>
    </ToastCtx.Provider>
  );
}

export function Segmented<T extends string>({ value, onChange, options, label }: { value: T; onChange: (v: T) => void; options: { value: T; label: string; icon?: ReactNode }[]; label: string }) {
  return (
    <div className="segmented" role="radiogroup" aria-label={label}>
      {options.map((o) => (
        <button key={o.value} type="button" role="radio" aria-checked={value === o.value} className={value === o.value ? 'on' : ''} onClick={() => onChange(o.value)}>
          {o.icon}{o.label}
        </button>
      ))}
    </div>
  );
}

export function Toggle({ checked, onChange, label, description, id, disabled }: { checked: boolean; onChange: (v: boolean) => void; label: string; description?: ReactNode; id: string; disabled?: boolean }) {
  return (
    <div className={`toggle-row${disabled ? ' disabled' : ''}`}>
      <label htmlFor={id} className="toggle-text"><span className="toggle-label">{label}</span>{description && <span className="toggle-desc">{description}</span>}</label>
      <input id={id} type="checkbox" role="switch" className="switch" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
    </div>
  );
}
