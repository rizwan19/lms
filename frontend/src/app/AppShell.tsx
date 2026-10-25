import { NavLink, Outlet, useNavigate } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { api } from '../shared/api'
import type { Profile, Workspace } from '../shared/types'
import { useSession } from './session'
import { NotificationList } from '../features/notifications/NotificationList'

const TEACHER_NAVIGATION = [
  { to: '/teacher/courses', label: 'Courses' },
  { to: '/teacher/classes', label: 'Classes' },
  { to: '/teacher/grading', label: 'Grading' },
]

const ADMINISTRATION_NAVIGATION = [
  { to: '/admin/users', label: 'Accounts' },
  { to: '/admin/settings', label: 'Settings' },
]

const STUDENT_NAVIGATION = [
  { to: '/student', label: 'My learning' },
  { to: '/student/results', label: 'Results' },
  { to: '/student/certificates', label: 'Certificates' },
]

function navigation(me: Profile) {
  if (me.role === 'STUDENT') return STUDENT_NAVIGATION
  return me.capabilities.canAdministerWorkspace ? [...TEACHER_NAVIGATION, ...ADMINISTRATION_NAVIGATION] : TEACHER_NAVIGATION
}

export function AppShell() {
  const { me, signOut } = useSession()
  const navigate = useNavigate()
  const workspace = useQuery({ queryKey: ['workspace'], queryFn: () => api.get<Workspace>('/workspace') })

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
          {me && workspace.data?.logoAssetId && <img src={`/api/v1/media/${workspace.data.logoAssetId}/content`} alt="" />}
          {workspace.data?.name ?? 'Learning Portal'}
        </strong>
        {me?.emailVerified && (
          <nav aria-label="Main">
            {navigation(me).map((item) => (
              <NavLink key={item.to} to={item.to}>
                {item.label}
              </NavLink>
            ))}
          </nav>
        )}
        {me?.emailVerified && <NotificationList />}
        {me && (
          <details className="popover account-menu">
            <summary>{me.displayName}</summary>
            <div className="menu">
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
