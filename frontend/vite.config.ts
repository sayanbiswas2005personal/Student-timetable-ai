/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * The dev server proxies /api to the Spring Boot backend.
 *
 * That is not just convenience: it makes the browser see a single origin, so the session cookie
 * and CSRF header work with no CORS configuration at all during development. It mirrors how the
 * application is deployed, where the API sits behind the same host as the static files.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const backendPort = env.VITE_BACKEND_PORT ?? '8080'

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: Number(env.VITE_PORT ?? 5173),
      strictPort: true,
      proxy: {
        '/api': {
          target: `http://127.0.0.1:${backendPort}`,
          changeOrigin: false,
        },
        '/actuator': {
          target: `http://127.0.0.1:${backendPort}`,
          changeOrigin: false,
        },
      },
    },
    preview: {
      port: Number(env.VITE_PREVIEW_PORT ?? 4173),
      strictPort: true,
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  }
})