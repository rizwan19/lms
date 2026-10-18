import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { api, request } from '../../shared/api'
import type { TemporaryCredential } from '../../shared/types'
import { ErrorMessage } from '../../shared/ui'
import { CredentialNotice } from '../accounts/CredentialNotice'

type Outcome = 'NEW_ACCOUNT' | 'ENROLL_EXISTING' | 'INVALID'
type Preview = {
  rows: { line: number; username: string; displayName: string; email: string | null; outcome: Outcome; errors: string[] }[]
  newAccounts: number
  existingStudents: number
  invalidRows: number
  digest: string
}
type ImportResult = { enrolled: number; created: number; invited: number; credentials: TemporaryCredential[] }

const OUTCOME_LABELS: Record<Outcome, string> = { NEW_ACCOUNT: 'New account', ENROLL_EXISTING: 'Existing student', INVALID: 'Needs fixing' }

/** Preview-then-commit CSV import. Nothing changes until every row is valid and the teacher confirms. */
export function RosterImportDialog({ classId, onImported, onClose }: { classId: string; onImported: () => void; onClose: () => void }) {
  const [preview, setPreview] = useState<Preview | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const check = useMutation({
    mutationFn: (file: File) => request<Preview>(`/classes/${classId}/imports/preview`, { method: 'POST', rawBody: file, headers: { 'Content-Type': 'text/csv' } }),
    onSuccess: setPreview,
  })
  const commit = useMutation({
    mutationFn: (checked: Preview) =>
      api.post<ImportResult>(`/classes/${classId}/imports/commit`, {
        digest: checked.digest,
        rows: checked.rows.map(({ username, displayName, email }) => ({ username, displayName, email })),
      }),
    onSuccess: (imported) => {
      setResult(imported)
      onImported()
    },
  })

  if (result) {
    return (
      <div className="card" role="region" aria-label="Import result">
        <p role="status">
          Enrolled {result.enrolled} students ({result.created} new accounts, {result.invited} invited by email).
        </p>
        {result.credentials.length > 0 && <CredentialNotice credentials={result.credentials} onDismiss={onClose} />}
        {result.credentials.length === 0 && (
          <button type="button" onClick={onClose}>
            Close
          </button>
        )}
      </div>
    )
  }

  return (
    <div className="card" role="region" aria-label="Import students">
      <h3>Import students from CSV</h3>
      <p className="muted">
        Columns: <code>username,display_name,email</code>. Email is optional. UTF-8, up to 1,000 rows. Existing accounts are enrolled, never changed.
      </p>
      <label className="field">
        CSV file
        <input
          type="file"
          accept=".csv,text/csv"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) check.mutate(file)
            event.target.value = ''
          }}
        />
      </label>
      <ErrorMessage error={check.error ?? commit.error} />
      {preview && (
        <>
          <p>
            {preview.newAccounts} new, {preview.existingStudents} existing, {preview.invalidRows} to fix.
          </p>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th scope="col">Line</th>
                  <th scope="col">Username</th>
                  <th scope="col">Name</th>
                  <th scope="col">Email</th>
                  <th scope="col">Result</th>
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((row) => (
                  <tr key={row.line} className={row.outcome === 'INVALID' ? 'invalid' : undefined}>
                    <td>{row.line}</td>
                    <td>{row.username}</td>
                    <td>{row.displayName}</td>
                    <td>{row.email ?? '—'}</td>
                    <td>
                      {OUTCOME_LABELS[row.outcome]}
                      {row.errors.length > 0 && <small className="error"> {row.errors.join('; ')}</small>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
      <div className="actions">
        <button type="button" className="primary" disabled={!preview || preview.invalidRows > 0 || preview.rows.length === 0 || commit.isPending} onClick={() => preview && commit.mutate(preview)}>
          Import {preview?.rows.length ?? 0} students
        </button>
        <button type="button" onClick={onClose}>
          Cancel
        </button>
      </div>
    </div>
  )
}
