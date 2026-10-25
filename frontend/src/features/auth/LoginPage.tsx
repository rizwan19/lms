import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, Navigate } from 'react-router'
import { api } from '../../shared/api'
import type { Profile } from '../../shared/types'
import { ErrorMessage, Field } from '../../shared/ui'
import { useSession } from '../../app/session'

export function LoginPage() {
  const { me } = useSession()
  const queryClient = useQueryClient()
  const setup = useQuery({ queryKey: ['setup'], queryFn: () => api.get<boolean>('/setup') })
  const login = useMutation({
    mutationFn: (credentials: { username: string; password: string }) => api.post<Profile>('/auth/login', credentials),
    onSuccess: async (account) => {
      await api.get('/auth/csrf')
      queryClient.setQueryData(['me'], account)
    },
  })

  if (me) return <Navigate to="/" replace />
  if (setup.data) return <Navigate to="/setup" replace />

  return (
    <section className="card narrow">
      <h1>Sign in</h1>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          const form = new FormData(event.currentTarget)
          login.mutate({ username: String(form.get('username')), password: String(form.get('password')) })
        }}
      >
        <Field label="Username or email" name="username">
          <input id="username" name="username" autoComplete="username" required />
        </Field>
        <Field label="Password" name="password">
          <input id="password" name="password" type="password" autoComplete="current-password" required />
        </Field>
        <ErrorMessage error={login.error} />
        <button type="submit" className="primary" disabled={login.isPending}>
          Sign in
        </button>
      </form>
      <p>
        <Link to="/forgot-password">Forgot your password?</Link>
      </p>
    </section>
  )
}
