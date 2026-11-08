import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
// Self-hosted fonts: work offline, and each script's @font-face carries a unicode-range,
// so a browser only downloads the scripts that actually appear on screen.
import '@fontsource/plus-jakarta-sans/400.css';
import '@fontsource/plus-jakarta-sans/500.css';
import '@fontsource/plus-jakarta-sans/600.css';
import '@fontsource/plus-jakarta-sans/700.css';
import '@fontsource-variable/fraunces/opsz.css';
import '@fontsource-variable/fraunces/opsz-italic.css';
import '@fontsource/noto-sans-devanagari/400.css';
import '@fontsource/noto-sans-devanagari/600.css';
import '@fontsource/noto-sans-tamil/400.css';
import '@fontsource/noto-sans-tamil/600.css';
import '@fontsource/noto-sans-telugu/400.css';
import '@fontsource/noto-sans-telugu/600.css';
import '@fontsource/noto-sans-kannada/400.css';
import '@fontsource/noto-sans-kannada/600.css';
import '@fontsource/noto-sans-malayalam/400.css';
import '@fontsource/noto-sans-malayalam/600.css';
import '@fontsource/noto-sans-bengali/400.css';
import '@fontsource/noto-sans-bengali/600.css';
import '@fontsource/noto-sans-gujarati/400.css';
import '@fontsource/noto-sans-gujarati/600.css';
import './styles/tokens.css';
import './styles/app.css';
import App from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
