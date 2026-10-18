import { useMutation } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, Field, fieldErrors } from '../../shared/ui'

const MODES = {
  invitation: { title: 'Accept invitation', path: '/auth/invitations/accept', action: 'Set password' },
  reset: { title: 'Reset password', path: '/auth/password-reset/confirm', action: 'Reset password' },
} as const

/** Sets a password from an emailed single-use link (invitation or reset). */
export function TokenPasswordPage({ mode }: { mode: keyof typeof MODES }) {
  const [params] = useSearchParams()
  const config = MODES[mode]
  const submit = useMutation({ mutationFn: (password: string) => api.post(config.path, { token: params.get('token') ?? '', password }) })

  if (submit.isSuccess) {
    return (
      <section className="card narrow">
        <h1>{config.title}</h1>
        <p>Your password is set.</p>
        <Link to="/login">Sign in</Link>
      </section>
    )
  }
  return (
    <section className="card narrow">
      <h1>{config.title}</h1>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          submit.mutate(String(new FormData(event.currentTarget).get('password')))
        }}
      >
        <Field label="New password" name="password" errors={fieldErrors(submit.error)} hint="12 to 128 characters">
          <input id="password" name="password" type="password" required minLength={12} maxLength={128} autoComplete="new-password" />
        </Field>
        <ErrorMessage error={submit.error} />
        <button type="submit" className="primary" disabled={submit.isPending}>
          {config.action}
        </button>
      </form>
    </section>
  )
}
