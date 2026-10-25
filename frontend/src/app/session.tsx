import { createContext, useCallback, useContext, useMemo, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate, useLocation } from 'react-router'
import { api, ApiError } from '../shared/api'
import type { Profile, Role } from '../shared/types'
import { Spinner } from '../shared/ui'

type Session = {
  me: Profile | null
  refresh: () => Promise<void>
  signOut: () => Promise<void>
}

const SessionContext = createContext<Session | null>(null)

async function loadProfile(): Promise<Profile | null> {
  try {
    return await api.get<Profile>('/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export function homePath(me: Profile | null): string {
  if (!me) return '/login'
  return me.role === 'TEACHER' ? '/teacher/courses' : '/student'
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const meQuery = useQuery({ queryKey: ['me'], queryFn: loadProfile, retry: false, staleTime: 60_000 })
  const me = meQuery.data ?? null

  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ['me'] })
  }, [queryClient])

  const signOut = useCallback(async () => {
    await api.post('/auth/logout')
    queryClient.setQueryData(['me'], null)
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'me' })
  }, [queryClient])

  const value = useMemo(() => ({ me, refresh, signOut }), [me, refresh, signOut])

  if (meQuery.isPending) return <Spinner />
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession(): Session {
  const session = useContext(SessionContext)
  if (!session) throw new Error('useSession outside SessionProvider')
  return session
}

/**
 * Route guard: requires sign-in, a verified email (the server refuses almost everything else until then) and,
 * optionally, a role or workspace administration.
 */
export function RequireAuth({ role, administrator, children }: { role?: Role; administrator?: boolean; children: ReactNode }) {
  const { me } = useSession()
  const location = useLocation()
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (!me.emailVerified && location.pathname !== '/change-password') return <Navigate to="/verify-email" replace />
  if (role && me.role !== role) return <Navigate to="/" replace />
  if (administrator && !me.capabilities.canAdministerWorkspace) return <Navigate to="/" replace />
  return <>{children}</>
}
