import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// La espera por defecto de 1 s es insuficiente cuando la suite corre en
// paralelo o en un runner de CI con poca CPU.
configure({ asyncUtilTimeout: 5_000 })

// jsdom no implementa la apertura y el cierre de diálogos modales.
HTMLDialogElement.prototype.showModal ??= function () { this.open = true }
HTMLDialogElement.prototype.close ??= function () { this.open = false }

afterEach(cleanup)
