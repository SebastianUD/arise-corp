import { mergeConfig } from 'vitest/config'
import base from '../vite.config.js'

// Solo se ejecuta de forma manual. Escribe en el entorno configurado en .env.
export default mergeConfig(base, { test: {
  include: ['manual/flujo-sga.live.jsx'],
  testTimeout: 180_000,
  hookTimeout: 30_000,
  maxWorkers: 1,
} })
