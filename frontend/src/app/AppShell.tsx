import { NavLink, Outlet, useNavigate } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '../shared/api'
import type { Institution, Role } from '../shared/types'
import { homePath, useSession } from './session'
import { NotificationList } from '../features/notifications/NotificationList'

const ROLE_LABELS: Record<Role, string> = { ADMINISTRATOR: 'Administrator', TEACHER: 'Teacher', STUDENT: 'Student' }

const NAVIGATION: Record<Role, { to: string; label: string }[]> = {
  ADMINISTRATOR: [
    { to: '/admin/users', label: 'Accounts' },
    { to: '/admin/settings', label: 'Settings' },
  ],
  TEACHER: [{ to: '/admin/users', label: 'Students' }],
  STUDENT: [{ to: '/student', label: 'My learning' }],
}

export function AppShell() {
  const { me, activeRole, setActiveRole, signOut } = useSession()
  const navigate = useNavigate()
  const institution = useQuery({ queryKey: ['institution'], queryFn: () => api.get<Institution>('/institution') })

  async function handleSignOut() {
    await signOut()
    navigate('/login')
  }

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="topbar">
        <strong className="brand">
          {me && institution.data?.logoAssetId && <img src={`/api/v1/media/${institution.data.logoAssetId}/content`} alt="" />}
          {institution.data?.name ?? 'Learning Portal'}
        </strong>
        {me && activeRole && !me.passwordChangeRequired && (
          <nav aria-label="Main">
            {NAVIGATION[activeRole].map((item) => (
              <NavLink key={item.to} to={item.to}>
                {item.label}
              </NavLink>
            ))}
          </nav>
        )}
        {me && !me.passwordChangeRequired && <NotificationList />}
        {me && (
          <details className="popover account-menu">
            <summary>{me.displayName}</summary>
            <div className="menu">
              {me.roles.length > 1 && (
                <label>
                  View as
                  <select
                    value={activeRole ?? ''}
                    onChange={(event) => {
                      const role = event.target.value as Role
                      setActiveRole(role)
                      navigate(homePath(role))
                    }}
                  >
                    {me.roles.map((role) => (
                      <option key={role} value={role}>
                        {ROLE_LABELS[role]}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              <NavLink to="/change-password">Change password</NavLink>
              <button type="button" onClick={handleSignOut}>
                Sign out
              </button>
            </div>
          </details>
        )}
      </header>
      <main id="main" className="page">
        <Outlet />
      </main>
    </div>
  )
}
