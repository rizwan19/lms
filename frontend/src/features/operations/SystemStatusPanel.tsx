import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, type Page } from '../../shared/api'
import { ErrorMessage, formatDateTime, QueryState } from '../../shared/ui'

type SystemStatus = { emailConfigured: boolean; failedJobs: number }
type Maintenance = { active: boolean; reason: string | null; updatedAt: string }
type FailedJob = { id: string; type: string; attempts: number; lastError: string | null; failedAt: string }

/** Administrator view of delivery health: SMTP configuration and terminally failed jobs. */
export function SystemStatusPanel() {
  const status = useQuery({ queryKey: ['system-status'], queryFn: () => api.get<SystemStatus>('/admin/system-status') })
  const failed = useQuery({
    queryKey: ['failed-jobs'],
    queryFn: () => api.get<Page<FailedJob>>('/admin/failed-jobs'),
    enabled: (status.data?.failedJobs ?? 0) > 0,
  })

  const queryClient = useQueryClient()
  const maintenance = useQuery({ queryKey: ['maintenance'], queryFn: () => api.get<Maintenance>('/admin/maintenance') })
  const toggle = useMutation({
    mutationFn: (active: boolean) => api.put<Maintenance>('/admin/maintenance', { active, reason: active ? 'Manual maintenance' : null }),
    onSuccess: (state) => queryClient.setQueryData(['maintenance'], state),
  })

  return (
    <section className="card" aria-labelledby="system-status">
      <h2 id="system-status">System status</h2>
      {maintenance.data && (
        <p className={maintenance.data.active ? 'notice' : undefined}>
          {maintenance.data.active ? `Maintenance mode is on (${maintenance.data.reason ?? 'no reason'}): changes are paused.` : 'Maintenance mode is off.'}{' '}
          <button type="button" onClick={() => toggle.mutate(!maintenance.data.active)}>
            {maintenance.data.active ? 'End maintenance' : 'Start maintenance'}
          </button>
        </p>
      )}
      <ErrorMessage error={toggle.error} />
      <QueryState query={status}>
        {(data) => (
          <>
            {data.emailConfigured ? (
              <p>Email delivery is configured.</p>
            ) : (
              <p className="notice">Email is not configured. Invitations and reset emails cannot be sent; use temporary passwords and assisted resets.</p>
            )}
            {data.failedJobs === 0 ? (
              <p>No failed background jobs.</p>
            ) : (
              <QueryState query={failed}>
                {(page) => (
                  <div className="table-scroll">
                    <table>
                      <caption>Failed background jobs ({data.failedJobs})</caption>
                      <thead>
                        <tr>
                          <th scope="col">Type</th>
                          <th scope="col">Attempts</th>
                          <th scope="col">Last error</th>
                          <th scope="col">Failed</th>
                        </tr>
                      </thead>
                      <tbody>
                        {page.items.map((job) => (
                          <tr key={job.id}>
                            <td>{job.type}</td>
                            <td>{job.attempts}</td>
                            <td>{job.lastError}</td>
                            <td>{formatDateTime(job.failedAt)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </QueryState>
            )}
          </>
        )}
      </QueryState>
    </section>
  )
}
