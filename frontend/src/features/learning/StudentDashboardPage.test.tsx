import { screen } from '@testing-library/react'
import { routes } from '../../app/routes'
import { mockApi, renderRoutes } from '../../test-utils'
import type { DashboardEntry } from './types'

const student = { id: 's1', username: 'student', displayName: 'রহিম', email: null, roles: ['STUDENT'], passwordChangeRequired: false }
const common = {
  'GET /auth/me': { body: student },
  'GET /institution': { body: { name: 'School', timezone: 'Asia/Dhaka', logoAssetId: null, revision: 0 } },
  'GET /notifications': { body: { items: [], page: 0, size: 20, total: 0 } },
}
const entry = (overrides: Partial<DashboardEntry>): DashboardEntry => ({
  enrollmentId: 'e1',
  className: 'Morning',
  courseTitle: 'Algebra',
  edition: 1,
  enrollmentStatus: 'ACTIVE',
  classStatus: 'ACTIVE',
  accessOpen: true,
  startsAt: null,
  endsAt: null,
  summary: { required: 4, completed: 1, lastActivityAt: null },
  ...overrides,
})

test('shows progress, zero-required wording and inactive explanations', async () => {
  mockApi({
    ...common,
    'GET /me/learning': {
      body: [
        entry({}),
        entry({ enrollmentId: 'e2', courseTitle: 'Reading club', summary: { required: 0, completed: 0, lastActivityAt: null } }),
        entry({ enrollmentId: 'e3', courseTitle: 'History', enrollmentStatus: 'SUSPENDED', accessOpen: false, summary: { required: 2, completed: 2, lastActivityAt: null } }),
      ],
    },
  })
  renderRoutes(routes, '/')
  expect(await screen.findByText('1 of 4 required lessons (25%)')).toBeInTheDocument()
  expect(screen.getByText('No required lessons')).toBeInTheDocument()
  expect(screen.getByText('Your enrollment is suspended. Contact your teacher.')).toBeInTheDocument()
  expect(screen.getAllByRole('link', { name: /Continue/ })).toHaveLength(2)
})

test('shows the empty state', async () => {
  mockApi({ ...common, 'GET /me/learning': { body: [] } })
  renderRoutes(routes, '/student')
  expect(await screen.findByText('You are not enrolled in any classes yet.')).toBeInTheDocument()
})

test('explains why the player is closed', async () => {
  mockApi({ ...common, 'GET /me/enrollments/e1': { status: 403, body: { code: 'ENROLLMENT_INACTIVE', detail: 'This class is not open for learning right now' } } })
  renderRoutes(routes, '/student/enrollments/e1')
  expect(await screen.findByText('This class is not open for learning right now')).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Back to my learning' })).toBeInTheDocument()
})
