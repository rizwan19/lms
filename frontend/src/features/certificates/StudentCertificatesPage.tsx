import { useQuery } from '@tanstack/react-query'
import { api } from '../../shared/api'
import { QueryState } from '../../shared/ui'
import { STATUS_LABELS, type Certificate } from './types'

export function StudentCertificatesPage() {
  const certificates = useQuery({ queryKey: ['me', 'certificates'], queryFn: () => api.get<Certificate[]>('/me/certificates') })
  return (
    <section>
      <h1>My certificates</h1>
      <QueryState query={certificates} isEmpty={(items) => items.length === 0} empty="Complete a course to receive its certificate.">
        {(items) => (
          <ul className="card-grid">
            {items.map((certificate) => (
              <li key={certificate.id} className="card">
                <h2>{certificate.courseTitle}</h2>
                <p className="muted">
                  Edition {certificate.edition} · completed {certificate.completedOn}
                </p>
                <p>{STATUS_LABELS[certificate.status]}</p>
                {certificate.status === 'RENDER_FAILED' && <p className="error">{certificate.renderError}</p>}
                {certificate.status === 'REVOKED' && <p className="muted">{certificate.revokedReason}</p>}
                {certificate.status === 'ISSUED' && (
                  <a className="button primary" href={`/api/v1/certificates/${certificate.id}/download`}>
                    Download PDF
                  </a>
                )}
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </section>
  )
}
