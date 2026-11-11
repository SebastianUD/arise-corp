import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.js',
    // jsdom se crea una sola vez por worker en lugar de una vez por archivo.
    pool: 'vmThreads',
    // Margen suficiente para equipos y runners de CI con carga alta.
    testTimeout: 15_000,
    hookTimeout: 15_000,
  },
})
