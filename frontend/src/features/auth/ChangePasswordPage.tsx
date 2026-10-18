import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, Field, fieldErrors } from '../../shared/ui'
import { useSession } from '../../app/session'

export function ChangePasswordPage() {
  const { me, refresh } = useSession()
  const navigate = useNavigate()
  const [mismatch, setMismatch] = useState(false)
  const change = useMutation({
    mutationFn: (body: { currentPassword: string; newPassword: string }) => api.put('/auth/me/password', body),
    onSuccess: async () => {
      await refresh()
      navigate('/')
    },
  })
  const errors = fieldErrors(change.error)

  return (
    <section className="card narrow">
      <h1>Change password</h1>
      {me?.passwordChangeRequired && <p className="notice">You signed in with a temporary password. Choose a new password to continue.</p>}
      <form
        onSubmit={(event) => {
          event.preventDefault()
          const form = new FormData(event.currentTarget)
          const newPassword = String(form.get('newPassword'))
          setMismatch(newPassword !== form.get('confirmPassword'))
          if (newPassword === form.get('confirmPassword')) {
            change.mutate({ currentPassword: String(form.get('currentPassword')), newPassword })
          }
        }}
      >
        <Field label="Current password" name="currentPassword" errors={errors}>
          <input id="currentPassword" name="currentPassword" type="password" required autoComplete="current-password" />
        </Field>
        <Field label="New password" name="newPassword" errors={errors} hint="12 to 128 characters">
          <input id="newPassword" name="newPassword" type="password" required minLength={12} maxLength={128} autoComplete="new-password" />
        </Field>
        <Field label="Confirm new password" name="confirmPassword" errors={mismatch ? { confirmPassword: 'Passwords do not match' } : {}}>
          <input id="confirmPassword" name="confirmPassword" type="password" required autoComplete="new-password" />
        </Field>
        <ErrorMessage error={change.error} />
        <button type="submit" className="primary" disabled={change.isPending}>
          Change password
        </button>
      </form>
    </section>
  )
}
