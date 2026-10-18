import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../../shared/api'
import type { Institution } from '../../shared/types'
import { ErrorMessage, Field, QueryState, fieldErrors } from '../../shared/ui'
import { TimezoneSelect } from './TimezoneSelect'

export function InstitutionSettingsPage() {
  const queryClient = useQueryClient()
  const institution = useQuery({ queryKey: ['institution'], queryFn: () => api.get<Institution>('/institution') })
  const save = useMutation({
    mutationFn: (body: { name: string; timezone: string; expectedRevision: number }) => api.put<Institution>('/institution', body),
    onSuccess: (updated) => queryClient.setQueryData(['institution'], updated),
  })

  return (
    <section>
      <h1>Institution settings</h1>
      <QueryState query={institution}>
        {(settings) => (
          <form
            key={settings.revision}
            className="card"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              save.mutate({ name: String(form.get('name')), timezone: String(form.get('timezone')), expectedRevision: settings.revision })
            }}
          >
            <Field label="Institution name" name="name" errors={fieldErrors(save.error)}>
              <input id="name" name="name" defaultValue={settings.name} required maxLength={200} />
            </Field>
            <Field label="Timezone" name="timezone" errors={fieldErrors(save.error)} hint="Dates are stored in UTC and shown in this timezone">
              <TimezoneSelect id="timezone" name="timezone" defaultValue={settings.timezone} />
            </Field>
            <ErrorMessage error={save.error} />
            {save.isSuccess && <p role="status">Saved.</p>}
            <button type="submit" className="primary" disabled={save.isPending}>
              Save settings
            </button>
          </form>
        )}
      </QueryState>
    </section>
  )
}
