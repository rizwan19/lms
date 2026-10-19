import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import { api } from '../../shared/api'
import { ErrorMessage, Field, QueryState, fieldErrors } from '../../shared/ui'
import type { ExamDetail, ExamSummary } from './types'

export function useCourseExams(courseId: string) {
  return useQuery({ queryKey: ['exams', courseId], queryFn: () => api.get<ExamSummary[]>(`/exams?courseId=${courseId}`) })
}

/** Exams belonging to a course, shown on the course editor. */
export function CourseExams({ courseId }: { courseId: string }) {
  const navigate = useNavigate()
  const exams = useCourseExams(courseId)
  const create = useMutation({
    mutationFn: (title: string) => api.post<ExamDetail>('/exams', { courseId, title }),
    onSuccess: (exam) => navigate(`/teacher/exams/${exam.id}/edit`),
  })
  return (
    <section className="card" aria-labelledby="course-exams">
      <h2 id="course-exams">Exams</h2>
      <QueryState query={exams} isEmpty={(items) => items.length === 0} empty="No exams yet.">
        {(items) => (
          <ul className="plain-list">
            {items.map((exam) => (
              <li key={exam.id}>
                <Link to={`/teacher/exams/${exam.id}/edit`}>{exam.title}</Link>{' '}
                <small className="muted">{exam.latestEdition ? `edition ${exam.latestEdition}` : 'not published'}</small>
              </li>
            ))}
          </ul>
        )}
      </QueryState>
      <form
        className="inline-form"
        onSubmit={(event) => {
          event.preventDefault()
          create.mutate(String(new FormData(event.currentTarget).get('examTitle')))
        }}
      >
        <Field label="New exam title" name="examTitle" errors={fieldErrors(create.error)}>
          <input id="examTitle" name="examTitle" required maxLength={200} />
        </Field>
        <button type="submit">Create exam</button>
      </form>
      <ErrorMessage error={create.error} />
    </section>
  )
}
