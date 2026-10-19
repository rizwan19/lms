import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { api, ApiError } from '../../shared/api'
import { Spinner } from '../../shared/ui'

type Verification = { status: 'VALID' | 'REVOKED'; institution: string | null; course: string | null; completedOn: string | null; student: string | null }

/** Public page behind the certificate QR code; shows only what the institution allows. */
export function VerifyCertificatePage() {
  const { token = '' } = useParams()
  const result = useQuery({ queryKey: ['verify', token], queryFn: () => api.get<Verification>(`/public/certificates/${encodeURIComponent(token)}`), retry: false })
  return (
    <section className="card narrow">
      <h1>Certificate verification</h1>
      {result.isPending && <Spinner />}
      {result.error && (
        <p role="status">{result.error instanceof ApiError && result.error.status === 404 ? 'No certificate can be verified with this link.' : 'Verification is unavailable right now.'}</p>
      )}
      {result.data?.status === 'REVOKED' && <p role="status">This certificate has been revoked.</p>}
      {result.data?.status === 'VALID' && (
        <div role="status">
          <p>
            <strong>Valid certificate</strong>
          </p>
          <dl>
            <dt>Institution</dt>
            <dd>{result.data.institution}</dd>
            <dt>Course</dt>
            <dd>{result.data.course}</dd>
            <dt>Completed on</dt>
            <dd>{result.data.completedOn}</dd>
            {result.data.student && (
              <>
                <dt>Student</dt>
                <dd>{result.data.student}</dd>
              </>
            )}
          </dl>
        </div>
      )}
    </section>
  )
}
