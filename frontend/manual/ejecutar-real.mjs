import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'

// La contraseña solo entra por stdin y se conserva en memoria durante la prueba.
process.stdout.write('Contraseña de las cuentas de demostración (entrada oculta):\n')
process.stdin.setRawMode?.(true)
process.stdin.resume()
process.stdin.setEncoding('utf8')
let secreto = ''
process.stdin.on('data', fragmento => {
  if (fragmento.includes('\u0003')) process.exit(130)
  if (!/[\r\n]/.test(fragmento)) { secreto += fragmento; return }
  secreto += fragmento.replace(/[\r\n]/g, '')
  process.stdin.setRawMode?.(false)
  process.stdin.pause()
  const hijo = spawn(process.execPath, [fileURLToPath(new URL('../node_modules/vitest/vitest.mjs', import.meta.url)),
    'run', '--config', 'manual/vitest.live.config.js', '--reporter=dot'], {
    stdio: ['ignore', 'inherit', 'inherit'], env: { ...process.env, ARISE_QA_PASSWORD: secreto },
  })
  secreto = ''
  hijo.on('exit', codigo => process.exit(codigo ?? 1))
})
