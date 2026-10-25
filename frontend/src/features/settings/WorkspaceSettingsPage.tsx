import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../shared/api'
import type { Workspace } from '../../shared/types'
import { ErrorMessage, Field, QueryState, fieldErrors } from '../../shared/ui'
import { TimezoneSelect } from './TimezoneSelect'
import { SystemStatusPanel } from '../operations/SystemStatusPanel'
import { UploadField } from '../materials/UploadField'

export function WorkspaceSettingsPage() {
  const queryClient = useQueryClient()
  const workspace = useQuery({ queryKey: ['workspace'], queryFn: () => api.get<Workspace>('/workspace') })
  const save = useMutation({
    mutationFn: (body: Omit<Workspace, 'state' | 'revision'> & { expectedRevision: number }) => api.put<Workspace>('/workspace', body),
    onSuccess: (updated) => {
      queryClient.setQueryData(['workspace'], updated)
      setLogo(undefined)
    },
  })
  const [logo, setLogo] = useState<string | null | undefined>(undefined)

  return (
    <section>
      <h1>Workspace settings</h1>
      <QueryState query={workspace}>
        {(settings) => (
          <form
            key={settings.revision}
            className="card"
            onSubmit={(event) => {
              event.preventDefault()
              const form = new FormData(event.currentTarget)
              save.mutate({
                name: String(form.get('name')),
                timezone: String(form.get('timezone')),
                logoAssetId: logo === undefined ? settings.logoAssetId : logo,
                certificateVerification: form.get('certificateVerification') === 'on',
                verificationShowsStudent: form.get('verificationShowsStudent') === 'on',
                expectedRevision: settings.revision,
              })
            }}
          >
            <Field label="Workspace name" name="name" errors={fieldErrors(save.error)}>
              <input id="name" name="name" defaultValue={settings.name} required maxLength={200} />
            </Field>
            <Field label="Timezone" name="timezone" errors={fieldErrors(save.error)} hint="Dates are stored in UTC and shown in this timezone">
              <TimezoneSelect id="timezone" name="timezone" defaultValue={settings.timezone} />
            </Field>
            <fieldset>
              <legend>Logo</legend>
              {(logo === undefined ? settings.logoAssetId : logo) ? (
                <div className="actions">
                  <img className="logo-preview" src={`/api/v1/media/${logo ?? settings.logoAssetId}/content`} alt="Current logo" />
                  <button type="button" onClick={() => setLogo(null)}>
                    Remove logo
                  </button>
                </div>
              ) : (
                <p className="muted">No logo.</p>
              )}
              <UploadField id="logo" label="Upload logo (PNG or JPEG, up to 5 MiB)" purpose="BRANDING_IMAGE" onUploaded={(asset) => setLogo(asset.id)} />
            </fieldset>
            <fieldset>
              <legend>Certificate verification</legend>
              <label className="inline">
                <input type="checkbox" name="certificateVerification" defaultChecked={settings.certificateVerification} /> Let anyone with a certificate&apos;s QR code
                check that it is valid
              </label>
              <label className="inline">
                <input type="checkbox" name="verificationShowsStudent" defaultChecked={settings.verificationShowsStudent} /> Show the student&apos;s name on the
                verification page
              </label>
              <small className="muted">Applies to certificates issued after the change.</small>
            </fieldset>
            <ErrorMessage error={save.error} />
            {save.isSuccess && <p role="status">Saved.</p>}
            <button type="submit" className="primary" disabled={save.isPending}>
              Save settings
            </button>
          </form>
        )}
      </QueryState>
      <SystemStatusPanel />
    </section>
  )
}
