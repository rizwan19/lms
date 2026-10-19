import { useQuery } from '@tanstack/react-query'
import { api } from '../../shared/api'
import { QueryState } from '../../shared/ui'
import type { LessonSummary } from './types'

type Completion = {
  status: 'NOT_ELIGIBLE' | 'ACHIEVED' | 'INVALIDATED'
  lessons: LessonSummary
  exams: { assignmentId: string; title: string; publishedPass: boolean; pendingGrading: boolean }[]
}

function examState(exam: Completion['exams'][number]): string {
  if (exam.publishedPass) return 'passed'
  if (exam.pendingGrading) return 'waiting for grading'
  return 'not passed yet'
}

/** Explains exactly what is still needed to complete the course. */
export function CompletionRequirements({ enrollmentId }: { enrollmentId: string }) {
  const completion = useQuery({ queryKey: ['completion', enrollmentId], queryFn: () => api.get<Completion>(`/me/enrollments/${enrollmentId}/completion`) })
  return (
    <section className="card" aria-labelledby="completion">
      <h2 id="completion">Course completion</h2>
      <QueryState query={completion}>
        {(state) => (
          <>
            <p role="status">
              {state.status === 'ACHIEVED' && 'Completed. Well done!'}
              {state.status === 'INVALIDATED' && 'A grade correction means a requirement is no longer met.'}
              {state.status === 'NOT_ELIGIBLE' && 'Not completed yet.'}
            </p>
            <ul>
              <li>
                {state.lessons.required === 0
                  ? 'No required lessons'
                  : `Required lessons: ${state.lessons.completed} of ${state.lessons.required} ${state.lessons.completed >= state.lessons.required ? '✓' : ''}`}
              </li>
              {state.exams.map((exam) => (
                <li key={exam.assignmentId}>
                  {exam.title}: {examState(exam)} {exam.publishedPass && '✓'}
                </li>
              ))}
            </ul>
          </>
        )}
      </QueryState>
    </section>
  )
}
