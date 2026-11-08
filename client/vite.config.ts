import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Dev: the API + WebSocket run on :8787 and are proxied, so the browser talks to one origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8787',
      '/ws': { target: 'ws://localhost:8787', ws: true },
    },
  },
});
