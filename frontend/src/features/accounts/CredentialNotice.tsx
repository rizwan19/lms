import type { TemporaryCredential } from '../../shared/types'
import { csvCell, downloadText } from '../../shared/download'
import { formatDateTime } from '../../shared/ui'

/** Shows one-time credentials once; they are kept only in memory and never re-fetchable. */
export function CredentialNotice({ credentials, onDismiss }: { credentials: TemporaryCredential[]; onDismiss: () => void }) {
  return (
    <div className="notice" role="status">
      <p>
        <strong>Temporary passwords are shown only once.</strong> Share them privately; each must be changed at first sign-in.
      </p>
      <table>
        <thead>
          <tr>
            <th scope="col">Username</th>
            <th scope="col">Temporary password</th>
            <th scope="col">Expires</th>
          </tr>
        </thead>
        <tbody>
          {credentials.map((credential) => (
            <tr key={credential.username}>
              <td>{credential.username}</td>
              <td>
                <code data-testid="temporary-password">{credential.temporaryPassword}</code>
              </td>
              <td>{formatDateTime(credential.expiresAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="actions">
        <button
          type="button"
          onClick={() =>
            downloadText(
              'temporary-credentials.csv',
              ['username,temporary_password,expires_at', ...credentials.map((c) => [c.username, c.temporaryPassword, c.expiresAt].map(csvCell).join(','))].join('\n'),
            )
          }
        >
          Download CSV
        </button>
        <button type="button" onClick={onDismiss}>
          Done
        </button>
      </div>
    </div>
  )
}
