import { useMutation } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage } from '../../shared/ui'
import { useSession } from '../../app/session'

/** Confirms an emailed verification link, or lets a signed-in unverified account ask for a new one. */
export function VerifyEmailPage() {
  const { me, refresh } = useSession()
  const [params] = useSearchParams()
  const token = params.get('token')
  const verify = useMutation({ mutationFn: () => api.post('/auth/email/verify', { token }), onSuccess: refresh })
  const resend = useMutation({ mutationFn: () => api.post('/auth/email/resend') })

  if (verify.isSuccess) {
    return (
      <section className="card narrow">
        <h1>Verify email</h1>
        <p>Your email address is verified.</p>
        <Link to={me ? '/' : '/login'}>Continue</Link>
      </section>
    )
  }
  if (token) {
    return (
      <section className="card narrow">
        <h1>Verify email</h1>
        <ErrorMessage error={verify.error} />
        <button type="button" className="primary" onClick={() => verify.mutate()} disabled={verify.isPending}>
          Confirm my email address
        </button>
      </section>
    )
  }
  return (
    <section className="card narrow">
      <h1>Verify email</h1>
      {me && !me.emailVerified ? (
        <>
          <p>We sent a confirmation link to {me.email}. Open it to continue.</p>
          {resend.isSuccess && <p className="notice">A new link is on its way.</p>}
          <ErrorMessage error={resend.error} />
          <button type="button" onClick={() => resend.mutate()} disabled={resend.isPending}>
            Send a new link
          </button>
        </>
      ) : (
        <Link to="/">Continue</Link>
      )}
    </section>
  )
}
