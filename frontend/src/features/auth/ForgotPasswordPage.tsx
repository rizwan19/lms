import { useMutation } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, Field } from '../../shared/ui'

export function ForgotPasswordPage() {
  const request = useMutation({ mutationFn: (usernameOrEmail: string) => api.post('/auth/password-reset/request', { usernameOrEmail }) })
  return (
    <section className="card narrow">
      <h1>Forgot password</h1>
      {request.isSuccess ? (
        <p role="status">If an account with an email address matches, a reset link is on its way. No email? Ask your teacher or administrator for an assisted reset.</p>
      ) : (
        <form
          onSubmit={(event) => {
            event.preventDefault()
            request.mutate(String(new FormData(event.currentTarget).get('usernameOrEmail')))
          }}
        >
          <Field label="Username or email" name="usernameOrEmail">
            <input id="usernameOrEmail" name="usernameOrEmail" required />
          </Field>
          <ErrorMessage error={request.error} />
          <button type="submit" className="primary" disabled={request.isPending}>
            Send reset link
          </button>
        </form>
      )}
      <p>
        <Link to="/login">Back to sign in</Link>
      </p>
    </section>
  )
}
