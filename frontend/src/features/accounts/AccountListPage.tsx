import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, query, type Page } from '../../shared/api'
import type { TemporaryCredential } from '../../shared/types'
import { ErrorMessage, Pagination, QueryState } from '../../shared/ui'
import { useSession } from '../../app/session'
import { CreateAccountForm } from './CreateAccountForm'
import { CredentialNotice } from './CredentialNotice'
import type { Account } from './types'

export function AccountListPage() {
  const { me, activeRole } = useSession()
  const queryClient = useQueryClient()
  const isAdministrator = activeRole === 'ADMINISTRATOR'
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const [credentials, setCredentials] = useState<TemporaryCredential[]>([])
  const [notice, setNotice] = useState<string | null>(null)

  const accounts = useQuery({
    queryKey: ['users', search, page],
    queryFn: () => api.get<Page<Account>>(`/users${query({ search, page })}`),
    placeholderData: keepPreviousData,
  })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['users'] })

  const reset = useMutation({
    mutationFn: (account: Account) => api.post<TemporaryCredential>(`/users/${account.id}/assisted-reset`),
    onSuccess: (credential) => setCredentials([credential]),
  })
  const setStatus = useMutation({
    mutationFn: ({ account, status }: { account: Account; status: Account['status'] }) => api.put<Account>(`/users/${account.id}/status`, { status }),
    onSuccess: refresh,
  })

  return (
    <section>
      <div className="page-header">
        <h1>{isAdministrator ? 'Accounts' : 'Students'}</h1>
        {!creating && (
          <button type="button" className="primary" onClick={() => setCreating(true)}>
            New account
          </button>
        )}
      </div>
      {creating && (
        <CreateAccountForm
          isAdministrator={isAdministrator}
          onCancel={() => setCreating(false)}
          onCreated={(created) => {
            setCreating(false)
            if (created.temporaryCredential) setCredentials([created.temporaryCredential])
            setNotice(created.invitationExpiresAt ? `Invitation created for ${created.account.username}.` : null)
            refresh()
          }}
        />
      )}
      {credentials.length > 0 && <CredentialNotice credentials={credentials} onDismiss={() => setCredentials([])} />}
      {notice && <p role="status">{notice}</p>}
      <ErrorMessage error={reset.error ?? setStatus.error} />
      <label className="search">
        Search
        <input
          type="search"
          value={search}
          onChange={(event) => {
            setSearch(event.target.value)
            setPage(0)
          }}
        />
      </label>
      <QueryState query={accounts} isEmpty={(data) => data.items.length === 0} empty="No accounts match.">
        {(data) => (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Username</th>
                    <th scope="col">Roles</th>
                    <th scope="col">Status</th>
                    <th scope="col">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((account) => (
                    <tr key={account.id}>
                      <td>{account.displayName}</td>
                      <td>{account.username}</td>
                      <td>{account.roles.map((role) => role.toLowerCase()).join(', ')}</td>
                      <td>{account.status === 'ACTIVE' ? 'Active' : 'Disabled'}</td>
                      <td className="actions">
                        {account.id !== me?.id && account.status === 'ACTIVE' && (
                          <button type="button" onClick={() => reset.mutate(account)} aria-label={`Reset password for ${account.username}`}>
                            Reset password
                          </button>
                        )}
                        {isAdministrator && account.id !== me?.id && (
                          <button
                            type="button"
                            onClick={() => setStatus.mutate({ account, status: account.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' })}
                            aria-label={`${account.status === 'ACTIVE' ? 'Disable' : 'Enable'} ${account.username}`}
                          >
                            {account.status === 'ACTIVE' ? 'Disable' : 'Enable'}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />
          </>
        )}
      </QueryState>
    </section>
  )
}
