import { Navigate, type RouteObject } from 'react-router'
import type { Role } from '../shared/types'
import { AppShell } from './AppShell'
import { RequireAuth, homePath, useSession } from './session'
import { LoginPage } from '../features/auth/LoginPage'
import { SetupPage } from '../features/auth/SetupPage'
import { ChangePasswordPage } from '../features/auth/ChangePasswordPage'
import { ForgotPasswordPage } from '../features/auth/ForgotPasswordPage'
import { TokenPasswordPage } from '../features/auth/TokenPasswordPage'
import { AccountListPage } from '../features/accounts/AccountListPage'
import { InstitutionSettingsPage } from '../features/settings/InstitutionSettingsPage'
import { CourseListPage } from '../features/courses/CourseListPage'
import { CourseEditorPage } from '../features/courses/CourseEditorPage'
import { CoursePreviewPage } from '../features/courses/CoursePreviewPage'
import { ClassListPage } from '../features/classrooms/ClassListPage'
import { ClassDetailPage } from '../features/classrooms/ClassDetailPage'

function Home() {
  const { activeRole } = useSession()
  return <Navigate to={homePath(activeRole)} replace />
}

const STAFF: Role[] = ['ADMINISTRATOR', 'TEACHER']

export const routes: RouteObject[] = [
  {
    element: <AppShell />,
    children: [
      { path: '/', element: <RequireAuth><Home /></RequireAuth> },
      { path: '/login', element: <LoginPage /> },
      { path: '/setup', element: <SetupPage /> },
      { path: '/forgot-password', element: <ForgotPasswordPage /> },
      { path: '/reset-password', element: <TokenPasswordPage mode="reset" /> },
      { path: '/accept-invitation', element: <TokenPasswordPage mode="invitation" /> },
      { path: '/change-password', element: <RequireAuth><ChangePasswordPage /></RequireAuth> },
      { path: '/admin/users', element: <RequireAuth roles={STAFF}><AccountListPage /></RequireAuth> },
      { path: '/admin/settings', element: <RequireAuth roles={['ADMINISTRATOR']}><InstitutionSettingsPage /></RequireAuth> },
      { path: '/teacher/courses', element: <RequireAuth roles={STAFF}><CourseListPage /></RequireAuth> },
      { path: '/teacher/courses/:id/edit', element: <RequireAuth roles={STAFF}><CourseEditorPage /></RequireAuth> },
      { path: '/teacher/courses/:id/preview', element: <RequireAuth roles={STAFF}><CoursePreviewPage /></RequireAuth> },
      { path: '/teacher/classes', element: <RequireAuth roles={STAFF}><ClassListPage /></RequireAuth> },
      { path: '/teacher/classes/:id', element: <RequireAuth roles={STAFF}><ClassDetailPage /></RequireAuth> },
      { path: '*', element: <p>Page not found.</p> },
    ],
  },
]
