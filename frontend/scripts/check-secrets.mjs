import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { join, relative } from 'node:path'

const root = fileURLToPath(new URL('../', import.meta.url))
const rules = [
  ['clave privada de Supabase', /sb_secret_[A-Za-z0-9_-]{8,}/],
  ['rol privilegiado de Supabase', /service_role/],
  ['configuración privada del backend', /SUPABASE_DB_PASSWORD|JWT_SECRET/],
  ['clave privada PEM', /-----BEGIN (?:EC |RSA |OPENSSH )?PRIVATE KEY-----/],
  ['JWT incrustado', /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/],
]

function files(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name)
    return entry.isDirectory() ? files(path) : [path]
  })
}

if (!existsSync(join(root, 'dist/index.html'))) {
  console.error('Primero ejecuta npm run build para verificar también el bundle.')
  process.exit(1)
}

let failures = 0
const targets = [...files(join(root, 'src')), ...files(join(root, 'dist')), join(root, '.env.example')]
for (const path of targets) {
  const content = readFileSync(path, 'utf8')
  for (const [name, pattern] of rules) {
    if (pattern.test(content)) {
      // Nunca imprimir el valor encontrado.
      console.error(`${relative(root, path)}: posible ${name}`)
      failures += 1
    }
  }
}
if (failures) process.exit(1)
console.log('Verificación aprobada: sin patrones de credenciales privadas en fuente, plantilla y bundle.')
