import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, QueryState, formatDateTime } from '../../shared/ui'
import { useInstitutionTimezone } from '../../shared/useInstitution'
import type { Attempt, StudentExam } from './types'

export function ExamInstructionsPage() {
  const { enrollmentId = '', assignmentId = '' } = useParams()
  const navigate = useNavigate()
  const timeZone = useInstitutionTimezone()
  const exams = useQuery({ queryKey: ['student-exams', enrollmentId], queryFn: () => api.get<StudentExam[]>(`/me/enrollments/${enrollmentId}/exams`) })
  const start = useMutation({
    mutationFn: () => api.post<Attempt>(`/exam-assignments/${assignmentId}/attempts`),
    onSuccess: (attempt) => navigate(`/student/attempts/${attempt.id}`),
  })

  return (
    <QueryState query={exams}>
      {(items) => {
        const exam = items.find((item) => item.assignmentId === assignmentId)
        if (!exam) return <p className="empty">This exam is not available.</p>
        return (
          <section className="card narrow">
            <h1>{exam.title}</h1>
            <ul>
              <li>{exam.questionCount} questions, {exam.maxPoints} points</li>
              <li>{exam.durationMinutes ? `${exam.durationMinutes} minutes once you start` : 'No time limit'}</li>
              {exam.closesAt && <li>Closes {formatDateTime(exam.closesAt, timeZone)}; unsubmitted answers are submitted then</li>}
              <li>
                Attempt {Math.min(exam.attemptsUsed + (exam.activeAttemptId ? 0 : 1), exam.maxAttempts)} of {exam.maxAttempts}
              </li>
              <li>Answers save automatically as you work. Keep this tab open; closing it does not stop the timer.</li>
            </ul>
            <ErrorMessage error={start.error} />
            <div className="actions">
              <button type="button" className="primary" disabled={!exam.canStart || start.isPending} onClick={() => start.mutate()}>
                {exam.activeAttemptId ? 'Resume exam' : 'Start exam'}
              </button>
              <Link to={`/student/enrollments/${enrollmentId}`}>Back to course</Link>
            </div>
          </section>
        )
      }}
    </QueryState>
  )
}
