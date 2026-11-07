import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { configurationError, supabase } from '../lib/supabase.js'
import { withDeadline } from '../lib/deadline.js'
import { endSession, SESSION_ENDED } from '../lib/session.js'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null)
  const [loading, setLoading] = useState(true)
  const [reason, setReason] = useState(null)
  const blocked = useRef(false)
  const revision = useRef(0)

  useEffect(() => {
    let active = true
    const clear = event => {
      blocked.current = true
      revision.current += 1
      setSession(null)
      setLoading(false)
      setReason(event.detail)
    }
    window.addEventListener(SESSION_ENDED, clear)
    if (!supabase) {
      setLoading(false)
      return () => window.removeEventListener(SESSION_ENDED, clear)
    }
    const initialRevision = revision.current
    const { data: { subscription } } = supabase.auth.onAuthStateChange((event, nextSession) => {
      if (!active) return
      revision.current += 1
      if (blocked.current && nextSession) return
      setSession(nextSession)
      setLoading(false)
      if (event === 'SIGNED_OUT' && !blocked.current) setReason('expired')
    })
    withDeadline(() => supabase.auth.getSession()).then(({ data, error }) => {
      if (active && revision.current === initialRevision && !blocked.current) {
        setSession(error ? null : data.session)
      }
    }).catch(() => {
      if (active && revision.current === initialRevision) setSession(null)
    }).finally(() => {
      if (active) setLoading(false)
    })
    return () => {
      active = false
      subscription.unsubscribe()
      window.removeEventListener(SESSION_ENDED, clear)
    }
  }, [])

  const signIn = useCallback(async (email, password) => {
    if (!supabase) throw new Error('Login unavailable')
    blocked.current = false
    setReason(null)
    try {
      const { data, error } = await withDeadline(async signal => {
        const result = await supabase.auth.signInWithPassword({ email, password })
        if (signal.aborted) {
          void endSession()
          throw new DOMException('Aborted', 'AbortError')
        }
        return result
      })
      if (error || !data?.session) throw new Error('Login failed')
      revision.current += 1
      setSession(data.session)
      return data.session
    } catch (error) {
      // Un resultado tardío de login no puede recuperar una sesión cancelada.
      void endSession()
      throw error
    }
  }, [])

  const signOut = useCallback(() => endSession(), [])

  return (
    <AuthContext.Provider value={{ session, loading, signIn, signOut, reason, configurationError }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth requires AuthProvider')
  return value
}
