/// <reference types="vitest/config" />
import { tanstackRouter } from '@tanstack/router-plugin/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 3000 と 8080 は他のアプリが使っていることがあるので避ける。
// フロントエンドは Vite の既定の 5173、バックエンドは 18080(backend の application.yml)。
const backendPort = 18080

export default defineConfig({
  // The router plugin must come before the React plugin.
  plugins: [tanstackRouter({ target: 'react', autoCodeSplitting: true }), react()],
  server: {
    port: 5173,
    strictPort: true,
    // Same-origin in development, so the backend needs no CORS setup.
    proxy: { '/api': `http://localhost:${backendPort}` },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
  },
})
