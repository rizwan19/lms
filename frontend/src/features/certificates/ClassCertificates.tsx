import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../shared/api'
import { ErrorMessage, QueryState } from '../../shared/ui'
import { useSession } from '../../app/session'
import { STATUS_LABELS, type Certificate } from './types'

/** Certificates of a class for its teachers: download, revoke with a reason, retry failed renders; reinstate (administrators). */
export function ClassCertificates({ classId }: { classId: string }) {
  const { activeRole } = useSession()
  const queryClient = useQueryClient()
  const [reasons, setReasons] = useState<Record<string, string>>({})
  const certificates = useQuery({ queryKey: ['certificates', classId], queryFn: () => api.get<Certificate[]>(`/certificates?classId=${classId}`) })
  const act = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'revoke' | 'reinstate' | 'retry' }) =>
      api.post(`/certificates/${id}/${action}`, action === 'retry' ? undefined : { reason: reasons[id] ?? '' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['certificates', classId] }),
  })
  return (
    <section className="card" aria-labelledby="class-certificates">
      <h2 id="class-certificates">Certificates</h2>
      <ErrorMessage error={act.error} />
      <QueryState query={certificates} isEmpty={(items) => items.length === 0} empty="No certificates yet.">
        {(items) => (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Student</th>
                  <th scope="col">Status</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {items.map((certificate) => (
                  <tr key={certificate.id}>
                    <td>{certificate.studentName}</td>
                    <td>
                      {STATUS_LABELS[certificate.status]}
                      {certificate.renderError && <small className="error"> {certificate.renderError}</small>}
                      {certificate.revokedReason && <small className="muted"> {certificate.revokedReason}</small>}
                    </td>
                    <td className="actions">
                      {certificate.status === 'ISSUED' && <a href={`/api/v1/certificates/${certificate.id}/download`}>Download</a>}
                      {certificate.status === 'RENDER_FAILED' && (
                        <button type="button" onClick={() => act.mutate({ id: certificate.id, action: 'retry' })}>
                          Retry
                        </button>
                      )}
                      {(certificate.status !== 'REVOKED' || activeRole === 'ADMINISTRATOR') && (
                        <>
                          <input
                            aria-label={`Reason for ${certificate.studentName}`}
                            placeholder="Reason"
                            maxLength={1000}
                            value={reasons[certificate.id] ?? ''}
                            onChange={(event) => setReasons({ ...reasons, [certificate.id]: event.target.value })}
                          />
                          {certificate.status === 'REVOKED' ? (
                            <button type="button" onClick={() => act.mutate({ id: certificate.id, action: 'reinstate' })}>
                              Reinstate
                            </button>
                          ) : (
                            <button type="button" className="danger" onClick={() => act.mutate({ id: certificate.id, action: 'revoke' })}>
                              Revoke
                            </button>
                          )}
                        </>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </QueryState>
    </section>
  )
}
