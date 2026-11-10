export const REQUEST_TIMEOUT = 30_000

export async function withDeadline(operation, milliseconds = REQUEST_TIMEOUT) {
  const controller = new AbortController()
  let timer
  const timeout = new Promise((_, reject) => {
    timer = setTimeout(() => {
      controller.abort()
      reject(new DOMException('Request timed out', 'TimeoutError'))
    }, milliseconds)
  })
  try {
    return await Promise.race([Promise.resolve().then(() => operation(controller.signal)), timeout])
  } finally {
    clearTimeout(timer)
  }
}
