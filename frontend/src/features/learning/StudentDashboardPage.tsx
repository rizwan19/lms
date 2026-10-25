import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../../shared/api'
import { QueryState, formatDateTime } from '../../shared/ui'
import { useWorkspaceTimezone } from '../../shared/useWorkspace'
import { ProgressBar } from './ProgressBar'
import type { DashboardEntry } from './types'

function availability(entry: DashboardEntry, timeZone: string): string {
  if (entry.enrollmentStatus === 'SUSPENDED') return 'Your enrollment is suspended. Contact your teacher.'
  if (entry.enrollmentStatus === 'WITHDRAWN') return 'You have left this class.'
  if (entry.classStatus === 'ARCHIVED') return 'This class has ended.'
  if (entry.classStatus === 'DRAFT') return 'This class has not started yet.'
  if (entry.startsAt && Date.parse(entry.startsAt) > Date.now()) return `Opens ${formatDateTime(entry.startsAt, timeZone)}.`
  return 'This class is closed.'
}

export function StudentDashboardPage() {
  const timeZone = useWorkspaceTimezone()
  const learning = useQuery({ queryKey: ['me', 'learning'], queryFn: () => api.get<DashboardEntry[]>('/me/learning') })
  return (
    <section>
      <h1>My learning</h1>
      <QueryState query={learning} isEmpty={(entries) => entries.length === 0} empty="You are not enrolled in any classes yet.">
        {(entries) => (
          <div className="card-grid">
            {entries.map((entry) => (
              <article key={entry.enrollmentId} className="card">
                <h2>{entry.courseTitle}</h2>
                <p className="muted">
                  {entry.className} · edition {entry.edition}
                  {entry.endsAt && <> · closes {formatDateTime(entry.endsAt, timeZone)}</>}
                </p>
                <ProgressBar summary={entry.summary} />
                {entry.summary.lastActivityAt && <p className="muted">Last studied {formatDateTime(entry.summary.lastActivityAt, timeZone)}</p>}
                {entry.accessOpen ? (
                  <Link className="button primary" to={`/student/enrollments/${entry.enrollmentId}`} aria-label={`Continue ${entry.courseTitle}`}>
                    Continue
                  </Link>
                ) : (
                  <p className="notice">{availability(entry, timeZone)}</p>
                )}
              </article>
            ))}
          </div>
        )}
      </QueryState>
    </section>
  )
}
