import { useState, type FormEvent } from 'react';
import { Languages, ShieldCheck, ArrowRight } from 'lucide-react';
import { AIMark, Banner, Button, Logo, VoiceMark } from '../components/ui';
import { useSession } from '../state/session';
import { ApiError } from '../lib/api';
import { LANGUAGES } from '../lib/format';

export function Splash() {
  const { status, error, retry, logout } = useSession();
  return (
    <main className="splash" aria-busy={status === 'loading'}>
      <Logo size={64} />
      <p className="splash-word">VAANI</p>
      {status === 'loading' && <div className="splash-bar" role="progressbar" aria-label="Starting VAANI" />}
      {error && <p className="splash-note" role="status">{error}</p>}
      {status === 'error' && (
        <div className="row gap-2">
          <Button variant="primary" onClick={retry}>Try again</Button>
          <Button variant="ghost" onClick={logout}>Sign out</Button>
        </div>
      )}
    </main>
  );
}

export function AuthScreen() {
  const { login, register } = useSession();
  const [mode, setMode] = useState<'login' | 'register' | 'recover'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [lang, setLang] = useState('en-IN');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<{ message: string; field?: string } | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setErr(null);
    if (mode === 'register' && password.length < 8) { setErr({ message: 'Password must be at least 8 characters.', field: 'password' }); return; }
    setBusy(true);
    try {
      if (mode === 'login') await login(email, password);
      else await register({ email, password, displayName: name, preferredLanguage: lang });
    } catch (e) {
      const a = e as ApiError;
      setErr({ message: a.message, field: a.details?.field });
    } finally { setBusy(false); }
  }

  return (
    <main className="auth">
      <section className="auth-hero" aria-hidden="true">
        <Logo size={30} withWord />
        <h1 className="display">Every language.<br />One conversation.</h1>
        {/* Illustration of the product idea (sample text, not user data): the original stays; VAANI annotates it. */}
        <div className="hero-sample">
          <div className="hs-turn"><span className="hs-speaker">Meena</span><p lang="ta">நாளைக்கு நாலு மணிக்கு பேசலாமா?</p>
            <div className="hs-gloss"><span className="label label-ai"><AIMark size={11} /> Tamil → English · VAANI</span><p>Shall we talk tomorrow at four?</p></div></div>
          <div className="hs-turn self"><span className="hs-speaker">You</span><div className="hs-voice"><VoiceMark size={14} /><span className="hs-bars" /><span>0:06</span></div>
            <p className="hs-trans">“Yes — I’ll bring the slides.”</p></div>
        </div>
        <ul className="hero-points">
          <li><Languages size={16} /> Read anyone, in your language — originals stay</li>
          <li><VoiceMark size={16} /> Speak; VAANI transcribes</li>
          <li><AIMark size={16} /> AI drafts. You decide what’s sent.</li>
        </ul>
      </section>
      <section className="auth-card">
        <div className="auth-mobile-logo"><Logo size={36} withWord /></div>
        {mode === 'recover' ? (
          <div className="stack gap-4">
            <h2 className="title">Reset your password</h2>
            <Banner tone="info">Password reset by email isn’t available yet — this server doesn’t send email. Ask the person who runs your VAANI server to reset your account.</Banner>
            <Button variant="secondary" onClick={() => setMode('login')}>Back to sign in</Button>
          </div>
        ) : (
          <form className="stack gap-4" onSubmit={submit} noValidate>
            <div>
              <h2 className="title">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h2>
              <p className="muted">{mode === 'login' ? 'Sign in to continue your conversations.' : 'It takes less than a minute.'}</p>
            </div>
            {err && !err.field && <Banner tone="error">{err.message}</Banner>}
            {mode === 'register' && (
              <div className="field">
                <label htmlFor="name">Your name</label>
                <input id="name" autoComplete="name" required maxLength={60} value={name} onChange={(e) => setName(e.target.value)} />
              </div>
            )}
            <div className="field">
              <label htmlFor="email">Email</label>
              <input id="email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)}
                aria-invalid={err?.field === 'email' || undefined} aria-describedby={err?.field === 'email' ? 'email-err' : undefined} />
              {err?.field === 'email' && <p id="email-err" className="field-error">{err.message}</p>}
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <input id="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'register' ? 8 : undefined}
                value={password} onChange={(e) => setPassword(e.target.value)} aria-invalid={err?.field === 'password' || undefined}
                aria-describedby={err?.field === 'password' ? 'pw-err' : mode === 'register' ? 'pw-hint' : undefined} />
              {err?.field === 'password' ? <p id="pw-err" className="field-error">{err.message}</p> : mode === 'register' && <p id="pw-hint" className="field-hint">At least 8 characters.</p>}
            </div>
            {mode === 'register' && (
              <div className="field">
                <label htmlFor="lang">Language you read best</label>
                <select id="lang" value={lang} onChange={(e) => setLang(e.target.value)}>
                  {LANGUAGES.map((l) => <option key={l.code} value={l.code}>{l.native}{l.native !== l.english ? ` — ${l.english}` : ''}</option>)}
                </select>
                <p className="field-hint">VAANI translates messages into this language when you ask.</p>
              </div>
            )}
            <Button type="submit" variant="primary" loading={busy} icon={!busy && <ArrowRight size={16} />}>{mode === 'login' ? 'Sign in' : 'Create account'}</Button>
            <div className="auth-switch">
              {mode === 'login' ? (
                <>
                  <button type="button" className="link" onClick={() => { setMode('register'); setErr(null); }}>Create an account</button>
                  <button type="button" className="link" onClick={() => { setMode('recover'); setErr(null); }}>Forgot password?</button>
                </>
              ) : <button type="button" className="link" onClick={() => { setMode('login'); setErr(null); }}>I already have an account</button>}
            </div>
          </form>
        )}
      </section>
    </main>
  );
}

const STEPS = [
  { icon: <Languages size={28} />, title: 'Talk in your language', body: 'Write and speak however you’re comfortable — Tamil, Hindi, Hinglish, anything. Tap Translate on any message to read it in your language. The original always stays.' },
  { icon: <VoiceMark size={28} />, title: 'Voice that becomes text', body: 'Send voice notes. When transcription is switched on, VAANI turns them into searchable text and can spot plans like “tomorrow at four”.' },
  { icon: <AIMark size={28} />, title: 'AI that stays in its lane', body: 'Ask for a reply, a rewrite or a summary right inside the chat. VAANI shows what it read and never sends anything without you.' },
];

export function Onboarding() {
  const { user, settings, updateSettings, updateProfile } = useSession();
  const [step, setStep] = useState(0);
  const [lang, setLang] = useState(user?.preferredLanguage ?? 'en-IN');
  const [ai, setAi] = useState(settings?.aiEnabled ?? true);
  const [busy, setBusy] = useState(false);
  const last = step === STEPS.length;

  async function finish() {
    setBusy(true);
    try {
      if (lang !== user?.preferredLanguage) await updateProfile({ preferredLanguage: lang });
      await updateSettings({ aiEnabled: ai, onboarded: true });
    } finally { setBusy(false); }
  }

  return (
    <main className="onboard">
      <div className="onboard-card">
        <div className="onboard-top">
          <Logo size={28} withWord />
          <button className="link" onClick={finish} disabled={busy}>Skip</button>
        </div>
        {!last ? (
          <div className="onboard-step" key={step}>
            <div className="onboard-icon" aria-hidden="true">{STEPS[step].icon}</div>
            <h1 className="title">{STEPS[step].title}</h1>
            <p className="muted">{STEPS[step].body}</p>
          </div>
        ) : (
          <div className="onboard-step stack gap-4">
            <div className="onboard-icon" aria-hidden="true"><ShieldCheck size={28} /></div>
            <h1 className="title">Your preferences</h1>
            <div className="field">
              <label htmlFor="ob-lang">Translate messages into</label>
              <select id="ob-lang" value={lang} onChange={(e) => setLang(e.target.value)}>
                {LANGUAGES.map((l) => <option key={l.code} value={l.code}>{l.native}{l.native !== l.english ? ` — ${l.english}` : ''}</option>)}
              </select>
            </div>
            <label className="check-card">
              <input type="checkbox" checked={ai} onChange={(e) => setAi(e.target.checked)} />
              <span><strong>Turn on AI assistance</strong><br /><span className="muted">When you ask, VAANI sends the relevant recent messages of that chat to Claude (Anthropic). Phone numbers and emails are hidden first. You can change this any time in Settings → Privacy.</span></span>
            </label>
          </div>
        )}
        <div className="onboard-foot">
          <div className="dots" aria-label={`Step ${step + 1} of ${STEPS.length + 1}`}>
            {[...STEPS, null].map((_, i) => <span key={i} className={i === step ? 'on' : ''} />)}
          </div>
          {last ? <Button variant="primary" loading={busy} onClick={finish}>Start talking</Button>
            : <Button variant="primary" onClick={() => setStep(step + 1)} icon={<ArrowRight size={16} />}>Next</Button>}
        </div>
      </div>
    </main>
  );
}
