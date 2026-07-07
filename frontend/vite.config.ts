import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Proxies API calls to the Spring Boot backend during local dev, so the
    // frontend can call same-origin '/api/...' paths with no CORS needed.
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
})
