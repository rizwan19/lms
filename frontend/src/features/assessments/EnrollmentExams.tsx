import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../../shared/api'
import { QueryState, formatDateTime } from '../../shared/ui'
import { useWorkspaceTimezone } from '../../shared/useWorkspace'
import type { StudentExam } from './types'

/** Exams of one enrollment, shown in the course player. */
export function EnrollmentExams({ enrollmentId }: { enrollmentId: string }) {
  const timeZone = useWorkspaceTimezone()
  const exams = useQuery({ queryKey: ['student-exams', enrollmentId], queryFn: () => api.get<StudentExam[]>(`/me/enrollments/${enrollmentId}/exams`) })
  return (
    <section className="card" aria-labelledby="student-exams">
      <h2 id="student-exams">Exams</h2>
      <QueryState query={exams} isEmpty={(items) => items.length === 0} empty="No exams in this class.">
        {(items) => (
          <ul className="plain-list">
            {items.map((exam) => (
              <li key={exam.assignmentId}>
                <strong>{exam.title}</strong> {exam.required ? '(required)' : '(practice)'}
                <br />
                <small className="muted">
                  {exam.closesAt ? `Closes ${formatDateTime(exam.closesAt, timeZone)} · ` : ''}
                  {exam.durationMinutes ? `${exam.durationMinutes} minutes` : 'untimed'} · attempts {exam.attemptsUsed}/{exam.maxAttempts}
                </small>
                {exam.canStart && (
                  <Link className="button primary" to={`/student/enrollments/${enrollmentId}/exams/${exam.assignmentId}`}>
                    {exam.activeAttemptId ? `Resume ${exam.title}` : `Open ${exam.title}`}
                  </Link>
                )}
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </section>
  )
}
