import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { routes } from './routes'
import { mockApi, renderRoutes } from '../test-utils'

const anonymous = {
  'GET /auth/me': { status: 401, body: { code: 'UNAUTHENTICATED' } },
  'GET /setup': { body: false },
  'GET /workspace': { body: { name: 'Shapla School', timezone: 'Asia/Dhaka', revision: 0 } },
}

test('anonymous visitors are sent to sign in', async () => {
  mockApi(anonymous)
  renderRoutes(routes, '/admin/users')
  expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  expect(await screen.findByText('Shapla School')).toBeInTheDocument()
})

test('a failed sign-in shows the server message', async () => {
  mockApi({
    ...anonymous,
    'POST /auth/login': { status: 401, body: { code: 'INVALID_CREDENTIALS', detail: 'Invalid username or password' } },
  })
  renderRoutes(routes, '/login')
  await userEvent.type(await screen.findByLabelText('Username or email'), 'someone')
  await userEvent.type(screen.getByLabelText('Password'), 'wrong password')
  await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
})

test('an unverified email forces the verify-email screen', async () => {
  mockApi({
    ...anonymous,
    'GET /auth/me': {
      body: {
        id: '1',
        username: 'student',
        displayName: 'Student',
        email: 'student@example.test',
        role: 'STUDENT',
        emailVerified: false,
        teacherStatus: null,
        capabilities: { canAdministerWorkspace: false, canPublishCourses: false, canLearn: false },
      },
    },
  })
  renderRoutes(routes, '/admin/settings')
  expect(await screen.findByRole('heading', { name: 'Verify email' })).toBeInTheDocument()
  expect(screen.getByText(/student@example.test/)).toBeInTheDocument()
})
