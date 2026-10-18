import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { routes } from './routes'
import { mockApi, renderRoutes } from '../test-utils'

const anonymous = {
  'GET /auth/me': { status: 401, body: { code: 'UNAUTHENTICATED' } },
  'GET /setup': { body: { required: false } },
  'GET /institution': { body: { name: 'Shapla School', timezone: 'Asia/Dhaka', revision: 0 } },
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

test('a temporary password forces the change-password screen', async () => {
  mockApi({
    ...anonymous,
    'GET /auth/me': { body: { id: '1', username: 'student', displayName: 'Student', email: null, roles: ['STUDENT'], passwordChangeRequired: true } },
  })
  renderRoutes(routes, '/admin/settings')
  expect(await screen.findByRole('heading', { name: 'Change password' })).toBeInTheDocument()
  expect(screen.getByText(/temporary password/)).toBeInTheDocument()
})
