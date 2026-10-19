import { screen, waitFor } from '@testing-library/react'
import axe from 'axe-core'
import { routes } from '../app/routes'
import { mockApi, renderRoutes } from '../test-utils'

const institution = { body: { name: 'School', timezone: 'Asia/Dhaka', logoAssetId: null, certificateVerification: false, verificationShowsStudent: false, revision: 0 } }
const notifications = { body: { items: [], page: 0, size: 20, total: 0 } }
const user = (roles: string[]) => ({ body: { id: 'u1', username: 'user', displayName: 'ব্যবহারকারী', email: null, roles, passwordChangeRequired: false } })

/** Structural WCAG checks (labels, names, landmarks, ARIA). Colour contrast needs a real browser and is excluded. */
async function expectNoViolations() {
  const results = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false }, region: { enabled: false } } })
  expect(results.violations.map((violation) => `${violation.id}: ${violation.nodes.map((node) => node.target.join(' ')).join(', ')}`)).toEqual([])
}

test('sign-in page', async () => {
  mockApi({ 'GET /auth/me': { status: 401, body: {} }, 'GET /setup': { body: { required: false } }, 'GET /institution': institution })
  renderRoutes(routes, '/login')
  await screen.findByRole('heading', { name: 'Sign in' })
  await expectNoViolations()
})

test('student dashboard', async () => {
  mockApi({
    'GET /auth/me': user(['STUDENT']),
    'GET /institution': institution,
    'GET /notifications': notifications,
    'GET /me/learning': {
      body: [
        { enrollmentId: 'e1', className: 'A', courseTitle: 'Physics', edition: 1, enrollmentStatus: 'ACTIVE', classStatus: 'ACTIVE', accessOpen: true, startsAt: null, endsAt: null, summary: { required: 2, completed: 1, lastActivityAt: null } },
      ],
    },
  })
  renderRoutes(routes, '/student')
  await screen.findByRole('heading', { name: 'Physics' })
  await expectNoViolations()
})

test('exam attempt with every question type', async () => {
  const now = new Date().toISOString()
  mockApi({
    'GET /auth/me': user(['STUDENT']),
    'GET /institution': institution,
    'GET /notifications': notifications,
    'GET /attempts/a1': {
      body: {
        id: 'a1',
        assignmentId: 's1',
        enrollmentId: 'e1',
        examTitle: 'Quiz',
        instructions: 'Answer all',
        attemptNumber: 1,
        maxAttempts: 1,
        status: 'IN_PROGRESS',
        startedAt: now,
        expiresAt: new Date(Date.now() + 600_000).toISOString(),
        serverNow: now,
        finalizedAt: null,
        questions: [
          { id: 'q1', position: 0, type: 'SINGLE_CHOICE', prompt: 'Pick', points: 1, options: [{ id: 'o1', label: 'A' }, { id: 'o2', label: 'B' }] },
          { id: 'q2', position: 1, type: 'MULTIPLE_SELECT', prompt: 'Pick many', points: 1, options: [{ id: 'o3', label: 'C' }, { id: 'o4', label: 'D' }] },
          { id: 'q3', position: 2, type: 'TRUE_FALSE', prompt: 'True?', points: 1, options: [{ id: 'o5', label: 'True' }, { id: 'o6', label: 'False' }] },
          { id: 'q4', position: 3, type: 'WRITTEN', prompt: 'Explain', points: 2, options: [] },
        ],
        answers: [],
      },
    },
  })
  renderRoutes(routes, '/student/attempts/a1')
  await screen.findByText('Explain')
  await expectNoViolations()
})

test('teacher account list', async () => {
  mockApi({
    'GET /auth/me': user(['TEACHER']),
    'GET /institution': institution,
    'GET /notifications': notifications,
    'GET /users': { body: { items: [{ id: 's1', username: 'rahim', displayName: 'রহিম', email: null, roles: ['STUDENT'], status: 'ACTIVE', createdAt: '2026-10-18T00:00:00Z', revision: 0 }], page: 0, size: 25, total: 1 } },
  })
  renderRoutes(routes, '/admin/users')
  await screen.findByRole('cell', { name: 'রহিম' })
  await waitFor(() => expect(screen.getByRole('button', { name: 'Reset password for rahim' })).toBeEnabled())
  await expectNoViolations()
})
