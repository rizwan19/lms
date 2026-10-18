import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate, useLocation } from 'react-router'
import { api, ApiError } from '../shared/api'
import type { Me, Role } from '../shared/types'
import { Spinner } from '../shared/ui'

const ROLE_PRIORITY: Role[] = ['ADMINISTRATOR', 'TEACHER', 'STUDENT']
const ROLE_KEY = 'learning.activeRole'

type Session = {
  me: Me | null
  activeRole: Role | null
  setActiveRole: (role: Role) => void
  refresh: () => Promise<void>
  signOut: () => Promise<void>
}

const SessionContext = createContext<Session | null>(null)

async function loadMe(): Promise<Me | null> {
  try {
    return await api.get<Me>('/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

function storedRole(): Role | null {
  try {
    return localStorage.getItem(ROLE_KEY) as Role | null
  } catch {
    return null
  }
}

export function homePath(role: Role | null): string {
  switch (role) {
    case 'ADMINISTRATOR':
      return '/admin/users'
    case 'TEACHER':
      return '/admin/users'
    case 'STUDENT':
      return '/student'
    default:
      return '/login'
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const meQuery = useQuery({ queryKey: ['me'], queryFn: loadMe, retry: false, staleTime: 60_000 })
  const [preferredRole, setPreferredRole] = useState<Role | null>(storedRole)

  const me = meQuery.data ?? null
  const activeRole = me
    ? preferredRole && me.roles.includes(preferredRole)
      ? preferredRole
      : (ROLE_PRIORITY.find((role) => me.roles.includes(role)) ?? null)
    : null

  const setActiveRole = useCallback((role: Role) => {
    setPreferredRole(role)
    try {
      localStorage.setItem(ROLE_KEY, role)
    } catch {
      // Preference only; the server never relies on it.
    }
  }, [])

  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ['me'] })
  }, [queryClient])

  const signOut = useCallback(async () => {
    await api.post('/auth/logout')
    queryClient.setQueryData(['me'], null)
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'me' })
  }, [queryClient])

  const value = useMemo(() => ({ me, activeRole, setActiveRole, refresh, signOut }), [me, activeRole, setActiveRole, refresh, signOut])

  if (meQuery.isPending) return <Spinner />
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>
}

export function useSession(): Session {
  const session = useContext(SessionContext)
  if (!session) throw new Error('useSession outside SessionProvider')
  return session
}

/** Route guard: requires sign-in, a completed password change and (optionally) one of the roles. */
export function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { me } = useSession()
  const location = useLocation()
  if (!me) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (me.passwordChangeRequired && location.pathname !== '/change-password') return <Navigate to="/change-password" replace />
  if (roles && !roles.some((role) => me.roles.includes(role))) return <Navigate to="/" replace />
  return <>{children}</>
}
