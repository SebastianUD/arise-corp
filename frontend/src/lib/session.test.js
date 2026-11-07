import { createClient } from '@supabase/supabase-js'
import { expect, it } from 'vitest'

it('el SDK borra la sesión persistida aunque la revocación remota falle', async () => {
  const storageKey = 'arise-test-auth'
  const client = createClient('https://example.supabase.co', 'sb_publishable_TEST_PUBLIC_KEY', {
    auth: { storageKey, autoRefreshToken: false, detectSessionInUrl: false },
    global: {
      fetch: async input => {
        if (String(input).includes('/logout')) throw new TypeError('offline')
        return new Response(JSON.stringify({
          access_token: 'fake-access-token', refresh_token: 'fake-refresh-token',
          token_type: 'bearer', expires_in: 3600,
          user: { id: 'test-user', email: 'paula@example.test' },
        }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      },
    },
  })
  try {
    const result = await client.auth.signInWithPassword({ email: 'paula@example.test', password: 'fake-password' })
    expect(result.error).toBeNull()
    expect(localStorage.getItem(storageKey)).not.toBeNull()
    const { error } = await client.auth.signOut({ scope: 'local' })
    expect(error).not.toBeNull()
    expect(localStorage.getItem(storageKey)).toBeNull()
    expect((await client.auth.getSession()).data.session).toBeNull()
  } finally {
    await client.auth.stopAutoRefresh()
    localStorage.removeItem(storageKey)
  }
})
