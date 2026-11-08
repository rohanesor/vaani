export const LANGUAGES: { code: string; native: string; english: string }[] = [
  { code: 'en-IN', native: 'English', english: 'English' },
  { code: 'hi-IN', native: 'हिन्दी', english: 'Hindi' },
  { code: 'ta-IN', native: 'தமிழ்', english: 'Tamil' },
  { code: 'te-IN', native: 'తెలుగు', english: 'Telugu' },
  { code: 'kn-IN', native: 'ಕನ್ನಡ', english: 'Kannada' },
  { code: 'ml-IN', native: 'മലയാളം', english: 'Malayalam' },
  { code: 'bn-IN', native: 'বাংলা', english: 'Bengali' },
  { code: 'gu-IN', native: 'ગુજરાતી', english: 'Gujarati' },
  { code: 'mr-IN', native: 'मराठी', english: 'Marathi' },
];
export const langLabel = (code: string | null | undefined) => {
  if (!code) return 'Unknown';
  const l = LANGUAGES.find((x) => x.code === code || x.code.startsWith(code.split('-')[0] + '-'));
  return l ? l.english : code;
};

export const clock = (iso: string) => new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });

export function dayLabel(iso: string) {
  const d = new Date(iso); const now = new Date();
  const start = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((start(now) - start(d)) / 86_400_000);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  if (diff < 7) return d.toLocaleDateString([], { weekday: 'long' });
  return d.toLocaleDateString([], { day: 'numeric', month: 'short', year: d.getFullYear() === now.getFullYear() ? undefined : 'numeric' });
}

export function listTime(iso: string) {
  const d = new Date(iso); const now = new Date();
  if (d.toDateString() === now.toDateString()) return clock(iso);
  const diff = (now.getTime() - d.getTime()) / 86_400_000;
  if (diff < 6) return d.toLocaleDateString([], { weekday: 'short' });
  return d.toLocaleDateString([], { day: 'numeric', month: 'short' });
}

export const duration = (ms: number) => { const s = Math.max(0, Math.round(ms / 1000)); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`; };

export const initials = (name: string) => name.trim().split(/\s+/).slice(0, 2).map((p) => [...p][0] ?? '').join('').toUpperCase();

export const uid = () => (crypto.randomUUID?.() ?? `${Date.now()}-${Math.random()}`).slice(0, 36);

export const timezone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata';
