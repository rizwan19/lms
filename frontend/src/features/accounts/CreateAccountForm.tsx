import { useMutation } from '@tanstack/react-query'
import { api } from '../../shared/api'
import type { Role } from '../../shared/types'
import { ErrorMessage, Field, fieldErrors } from '../../shared/ui'
import type { CreatedAccount } from './types'

const ALL_ROLES: Role[] = ['STUDENT', 'TEACHER', 'ADMINISTRATOR']

export function CreateAccountForm({ isAdministrator, onCreated, onCancel }: { isAdministrator: boolean; onCreated: (created: CreatedAccount) => void; onCancel: () => void }) {
  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) => api.post<CreatedAccount>('/users', body),
    onSuccess: onCreated,
  })
  const errors = fieldErrors(create.error)
  const roles = isAdministrator ? ALL_ROLES : (['STUDENT'] as Role[])

  return (
    <form
      className="card"
      aria-label="Create account"
      onSubmit={(event) => {
        event.preventDefault()
        const form = new FormData(event.currentTarget)
        create.mutate({
          username: form.get('username'),
          displayName: form.get('displayName'),
          email: form.get('email') || null,
          roles: isAdministrator ? form.getAll('roles') : ['STUDENT'],
          delivery: form.get('delivery'),
        })
      }}
    >
      <h2>New account</h2>
      <Field label="Username" name="username" errors={errors} hint="3-64 letters, digits, dots, underscores or hyphens">
        <input id="username" name="username" required autoComplete="off" />
      </Field>
      <Field label="Display name" name="displayName" errors={errors}>
        <input id="displayName" name="displayName" required maxLength={150} />
      </Field>
      <Field label="Email" name="email" errors={errors} hint="Optional for students; required for staff">
        <input id="email" name="email" type="email" autoComplete="off" />
      </Field>
      {isAdministrator && (
        <fieldset>
          <legend>Roles</legend>
          {roles.map((role) => (
            <label key={role} className="inline">
              <input type="checkbox" name="roles" value={role} defaultChecked={role === 'STUDENT'} /> {role.toLowerCase()}
            </label>
          ))}
          {errors.roles && <small className="error">{errors.roles}</small>}
        </fieldset>
      )}
      <fieldset>
        <legend>First sign-in</legend>
        <label className="inline">
          <input type="radio" name="delivery" value="TEMPORARY_PASSWORD" defaultChecked /> Show a temporary password
        </label>
        <label className="inline">
          <input type="radio" name="delivery" value="INVITATION" /> Email an invitation
        </label>
      </fieldset>
      <ErrorMessage error={create.error} />
      <div className="actions">
        <button type="submit" className="primary" disabled={create.isPending}>
          Create account
        </button>
        <button type="button" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  )
}
