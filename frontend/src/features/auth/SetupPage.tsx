import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../../shared/api'
import type { Profile, RegistrationReceipt } from '../../shared/types'
import { ErrorMessage, Field, fieldErrors } from '../../shared/ui'
import { TimezoneSelect } from '../settings/TimezoneSelect'

type SetupForm = Record<'workspaceName' | 'timezone' | 'username' | 'displayName' | 'email' | 'password', string>

export function SetupPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const setup = useMutation({
    mutationFn: async (form: SetupForm) => {
      await api.post<RegistrationReceipt>('/setup', form)
      return api.post<Profile>('/auth/login', { username: form.username, password: form.password })
    },
    onSuccess: async (account) => {
      await api.get('/auth/csrf')
      queryClient.setQueryData(['me'], account)
      await queryClient.invalidateQueries({ queryKey: ['workspace'] })
      navigate('/')
    },
  })
  const errors = fieldErrors(setup.error)

  return (
    <section className="card narrow">
      <h1>Set up your learning portal</h1>
      <form
        onSubmit={(event) => {
          event.preventDefault()
          setup.mutate(Object.fromEntries(new FormData(event.currentTarget)) as SetupForm)
        }}
      >
        <Field label="Workspace name" name="workspaceName" errors={errors}>
          <input id="workspaceName" name="workspaceName" required maxLength={200} />
        </Field>
        <Field label="Timezone" name="timezone" errors={errors}>
          <TimezoneSelect id="timezone" name="timezone" />
        </Field>
        <Field label="Administrator username" name="username" errors={errors}>
          <input id="username" name="username" required autoComplete="username" />
        </Field>
        <Field label="Display name" name="displayName" errors={errors}>
          <input id="displayName" name="displayName" required maxLength={150} />
        </Field>
        <Field label="Email" name="email" errors={errors}>
          <input id="email" name="email" type="email" required autoComplete="email" />
        </Field>
        <Field label="Password" name="password" errors={errors} hint="12 to 128 characters">
          <input id="password" name="password" type="password" required minLength={12} maxLength={128} autoComplete="new-password" />
        </Field>
        <ErrorMessage error={setup.error} />
        <button type="submit" className="primary" disabled={setup.isPending}>
          Create administrator
        </button>
      </form>
    </section>
  )
}
